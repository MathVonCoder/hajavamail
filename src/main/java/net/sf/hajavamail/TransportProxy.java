/*
 * HA-JavaMail: High-Availability JavaMail
 * Copyright (C) 2004 Paul Ferraro
 * 
 * This library is free software; you can redistribute it and/or modify it 
 * under the terms of the GNU Lesser General Public License as published by the 
 * Free Software Foundation; either version 2.1 of the License, or (at your 
 * option) any later version.
 * 
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License 
 * for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation, 
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 * 
 * Contact: ferraro@users.sourceforge.net
 */
package net.sf.hajavamail;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Provider;
import javax.mail.SendFailedException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.URLName;
import javax.mail.event.TransportListener;
import javax.mail.internet.InternetAddress;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @author  Paul Ferraro
 * @version $Revision: 1.4 $
 * @since   1.0
 */
public class TransportProxy extends Transport
{
	public static final String POOL_SIZE = "mail.transport.pool-size";
	public static final String SENDER_STRATEGY = "mail.transport.sender-strategy";
	public static final String CONNECT_RETRY_PERIOD = "mail.transport.connect-retry-period";
	public static final String CONNECT_TIMEOUT = "mail.transport.connect-timeout";
	
	private static final String DEFAULT_SENDER_STRATEGY = SimpleSenderStrategy.class.getName();
	private static final String DEFAULT_TRANSPORT_PROTOCOL = "smtp";
	private static final int DEFAULT_CONNECT_RETRY_PERIOD = 60;
	private static final int DEFAULT_POOL_SIZE = 1;
	private static final int DEFAULT_CONNECT_TIMEOUT = 0;
	
	static Log log = LogFactory.getLog(TransportProxy.class);

	long connectRetryPeriod;
	private TransportConnector[] connectors;
	private MessageSender[] senders;
	private final List idleSenderList = new LinkedList();
	private final List idleConnectorList = new LinkedList();
	private long connectTimeout;
	private SenderStrategy senderStrategy = new SimpleSenderStrategy();
	
	/**
	 * Constructs a new TransportProxy.
	 * @param session
	 * @param url
	 * @throws javax.mail.MessagingException
	 */
	public TransportProxy(Session session, URLName url) throws MessagingException
	{
		super(session, url);

		Properties properties = session.getProperties();
		
		int poolSize = Integer.parseInt(properties.getProperty(POOL_SIZE, Integer.toString(DEFAULT_POOL_SIZE)));
		this.connectRetryPeriod = 1000 * Integer.parseInt(properties.getProperty(CONNECT_RETRY_PERIOD, Integer.toString(DEFAULT_CONNECT_RETRY_PERIOD)));
		this.connectTimeout = 1000 * Integer.parseInt(properties.getProperty(CONNECT_TIMEOUT, Integer.toString(DEFAULT_CONNECT_TIMEOUT)));
		
		String protocol = properties.getProperty("mail.transport.protocol", DEFAULT_TRANSPORT_PROTOCOL);
		String hostProperty = "mail." + protocol + ".host";
		String host = properties.getProperty(hostProperty);
		
		if ((host == null) || (host.length() == 0))
		{
			hostProperty = "mail.host";
			host = properties.getProperty(hostProperty);
		}
		
		if ((host == null) || (host.length() == 0))
		{
			throw new MessagingException("No transport host specified.");
		}
		
		String senderStrategyClassName = properties.getProperty(SENDER_STRATEGY, DEFAULT_SENDER_STRATEGY);
		
		try
		{
			Class senderStrategyClass = Class.forName(senderStrategyClassName);
			Object lSenderStrategy = senderStrategyClass.newInstance();
			
			if (!SenderStrategy.class.isInstance(lSenderStrategy))
			{
				throw new MessagingException("Sender strategry " + senderStrategyClassName + " does not implement " + SenderStrategy.class.getName());
			}
			
			this.senderStrategy = (SenderStrategy) lSenderStrategy;
		}
		catch (ClassNotFoundException | IllegalAccessException e)
		{
			throw new MessagingException("Invalid sender strategy: " + senderStrategyClassName, e);
		}
		catch (InstantiationException e)
		{
			throw new MessagingException("Failed to create sender strategy: " + senderStrategyClassName, e);
		}
		
		Provider provider = null;
		Provider[] providers = session.getProviders();
		
		for (int i = 0; i < providers.length; ++i)
		{
			if (providers[i].getType().equals(Provider.Type.TRANSPORT))
			{
				if (providers[i].getProtocol().equals(url.getProtocol()))
				{
					if (!providers[i].getClassName().equals(this.getClass().getName()))
					{
						provider = providers[i];
						break;
					}
				}
			}
		}
		
		if (provider == null)
		{
			throw new MessagingException("Could not find an appropriate " + url.getProtocol() + " provider.");
		}
		
		String[] hosts = host.split(",");
		
		int size = poolSize * hosts.length;
		this.connectors = new TransportConnector[size];
		this.senders = new MessageSender[size];
		
		for (int i = 0; i < size; ++i)
		{
			Transport transport = this.session.getTransport(provider);
			Integer index = i;
			
			this.connectors[i] = new TransportConnector(transport, index);
			this.senders[i] = new MessageSender(index);
			this.releaseSender(this.senders[i]);
		}
	}
	
	/**
	 * Creates and starts a new connector thread for each underlying transport.
	 * This method returns after the first successful transport connection is made.
	 * @param hostList
	 * @param port
	 * @param user
	 * @param password
	 * @return 
	 * @see javax.mail.Service#protocolConnect(java.lang.String, int, java.lang.String, java.lang.String)
	 * @throws MessagingException if no transports were connected within the timeout configured via the {@link net.sf.hajavamail.TransportProxy#CONNECT_TIMEOUT} session property.
	 */
	@Override
	protected boolean protocolConnect(String hostList, int port, String user, String password) throws MessagingException
	{
		String[] hosts = hostList.split(",");
		
		for (int i = 0; i < this.connectors.length; ++i)
		{
			String host = hosts[i % hosts.length];
			
			URLName lurl = new URLName(this.connectors[i].getTransport().getURLName().getProtocol(), host, port, this.connectors[i].getTransport().getURLName().getFile(), user, password);
			
			this.connectors[i].connect(lurl);
		}

		boolean connectFailed = false;
		
		synchronized (this.idleConnectorList)
		{
			if (this.idleConnectorList.isEmpty())
			{
				try
				{
					// Wait until the first transport is available, or until connect timeout
					this.idleConnectorList.wait(this.connectTimeout);
				}
				catch (InterruptedException e)
				{
					// Do nothing
				}
				
				// If there are no active transports, then timeout was exceeded.
				if (this.idleConnectorList.isEmpty())
				{
					connectFailed = true;
				}
			}
		}

		if (connectFailed)
		{
			this.close();
			
			throw new MessagingException("Connect timeout (" + this.connectTimeout + " ms) exceeded.");
		}
		
		return true;
	}
	
	/**
	 * Performs simple message validation before sending using the sender strategy configured via the {@link net.sf.hajavamail.TransportProxy#SENDER_STRATEGY} session property.
	 * @param message
	 * @param addresses
	 * @throws javax.mail.MessagingException
	 * @see javax.mail.Transport#sendMessage(javax.mail.Message, javax.mail.Address[])
	 */
	@Override
	public void sendMessage(Message message, Address[] addresses) throws MessagingException
	{
		if (!this.isConnected())
		{
			throw new MessagingException("Transport not connected");
		}
		
		if ((addresses == null) || (addresses.length == 0))
		{
			// Nobody will recieve this message
			return;
		}
		
		if (message.getSubject() == null)
		{
			message.setSubject("");
		}

		Address[] recipients = message.getAllRecipients();
		
		if ((recipients == null) || (recipients.length == 0))
		{
			throw new MessagingException("Message contains no recipients.");
		}
		
		try
		{
			if (message.getContent() == null)
			{
				message.setText("");
			}
		}
		catch (IOException e)
		{
			throw new MessagingException("Failed to get message content", e);
		}
		
		this.senderStrategy.send(this.acquireSender(), message, addresses);
	}
	
	MessageSender acquireSender()
	{
		Integer index = null;
		
		synchronized (this.idleSenderList)
		{
			while (this.idleSenderList.isEmpty())
			{
				try
				{
					this.idleSenderList.wait();
				}
				catch (InterruptedException e)
				{
					// Do nothing
				}
			}
			
			index = (Integer) this.idleSenderList.remove(0);
		}
		
		return this.senders[index];
	}
	
	void releaseSender(MessageSender sender)
	{
		synchronized (this.idleSenderList)
		{
			this.idleSenderList.add(sender.getIndex());
			
			this.idleSenderList.notify();
		}
	}
	
	TransportConnector acquireConnector()
	{
		Integer index = null;
		
		synchronized (this.idleConnectorList)
		{
			while (this.idleConnectorList.isEmpty())
			{
				try
				{
					this.idleConnectorList.wait();
				}
				catch (InterruptedException e)
				{
					// Do nothing
				}
			}
			
			index = (Integer) this.idleConnectorList.remove(0);
		}
		
		return this.connectors[index];
	}
	
	void releaseConnector(TransportConnector connector)
	{
		synchronized (this.idleConnectorList)
		{
			this.idleConnectorList.add(connector.getIndex());
			
			this.idleConnectorList.notify();
		}
	}

	/**
	 * @param listener
	 * @see javax.mail.Transport#addTransportListener(javax.mail.event.TransportListener)
	 */
	@Override
	public void addTransportListener(TransportListener listener)
	{
		for (int i = 0; i < this.connectors.length; ++i)
		{
			this.connectors[i].getTransport().addTransportListener(listener);
		}
	}

	/**
	 * @param listener
	 * @see javax.mail.Transport#removeTransportListener(javax.mail.event.TransportListener)
	 */
	@Override
	public void removeTransportListener(TransportListener listener)
	{
		for (int i = 0; i < this.connectors.length; ++i)
		{
			this.connectors[i].getTransport().removeTransportListener(listener);
		}
	}
	
	/**
	 * Closes the Transport proxy.  Implementation is as follows:
	 * <ol>
	 *  <li>Waits until until all sender threads have completed.</li>
	 * 	<li>Interrupts the execution of any active connector threads</li>
	 * 	<li>Closes the underlying transports</li>
	 *  <li>Calls <code>javax.mail.Service.close()</code></li>
	 * </ol>
	 * @throws javax.mail.MessagingException
	 * @see javax.mail.Service#close()
	 */
	@Override
	public void close() throws MessagingException
	{
		synchronized (this.idleSenderList)
		{
			log.info("Waiting for active senders to complete...");
			while (this.idleSenderList.size() != this.senders.length)
			{
				try
				{
					this.idleSenderList.wait();
				}
				catch (InterruptedException e)
				{
					// Ignore
				}
			}
		}

		for (int i = 0; i < this.connectors.length; ++i)
		{
			TransportConnector connector = this.connectors[i];

			connector.interrupt();
		}
		
		synchronized (this.idleConnectorList)
		{
			log.info("Waiting for active connectors to stop...");
			while (this.idleConnectorList.size() != this.connectors.length)
			{
				try
				{
					this.idleConnectorList.wait();
				}
				catch (InterruptedException e)
				{
					// Ignore
				}
			}

			for (int i = 0; i < this.connectors.length; ++i)
			{
				TransportConnector connector = this.connectors[i];
				Transport transport = connector.getTransport();
				
				if (transport.isConnected())
				{
					URLName turl = transport.getURLName();
					
					try
					{
						transport.close();
						
						log.info("Successfully closed " + turl.getProtocol() + " connection to " + turl.getHost());
					}
					catch (MessagingException e)
					{
						log.warn("Failed to close " + turl.getProtocol() + " connection to " + turl.getHost());
					}
				}
			}
			
			this.idleConnectorList.clear();
		}
		
		super.close();
	}
	
	/**
	 * Closes the transpory proxy, if not closed already.
	 * @throws java.lang.Throwable
	 * @see java.lang.Object#finalize()
	 */
	@Override
	protected void finalize() throws Throwable
	{
		if (this.isConnected())
		{
			this.close();
		}
		
		super.finalize();
	}
	
	/**
	 * Asynchronously (re)connect a transport and make it available.
	 */
	private class TransportConnector implements Runnable
	{
		private Transport transport;
		private URLName url;
		private Integer index;
		private Thread thread;
		
		public TransportConnector(Transport transport, Integer index)
		{
			this.transport = transport;
			this.index = index;
		}
		
		public Transport getTransport()
		{
			return this.transport;
		}
		
		public Integer getIndex()
		{
			return this.index;
		}
		
		public void connect(URLName url)
		{
			this.url = url;
			
			this.reconnect();
		}
		
		public void reconnect()
		{
			this.thread = new Thread(this);
			this.thread.start();
		}

		public void interrupt()
		{
			this.thread.interrupt();
		}
		
		@Override
		public void run()
		{
			if (this.transport.isConnected())
			{
				try
				{
					this.transport.close();
				}
				catch (MessagingException e)
				{
					log.info("Failed to close " + this.url.getProtocol() + " connection to " + this.url.getHost(), e);
				}
			}
			
			while (!this.transport.isConnected() && !Thread.interrupted())
			{
				try
				{
					this.transport.connect(this.url.getHost(), this.url.getPort(), this.url.getUsername(), this.url.getPassword());
					
					log.info("Successfully opened " + this.url.getProtocol() + " connection to " + this.url.getHost());
				}
				catch (MessagingException e)
				{
					log.warn("Failed to connect transport", e);
					
					try
					{
						// Try again after a delay
						Thread.sleep(TransportProxy.this.connectRetryPeriod);
					}
					catch (InterruptedException ie)
					{
						this.interrupt();
					}
				}
			}

			// Release connector back to the pool
			TransportProxy.this.releaseConnector(this);
		}
	}

	/**
	 * Asynchronously send a message to a set of addresses via a transport.
	 */
	private class MessageSender implements Sender, Runnable
	{
		private Integer index;
		private Message message;
		private Address[] addresses;
		
		public MessageSender(Integer index)
		{
			this.index = index;
		}
		
		public Integer getIndex()
		{
			return this.index;
		}
		
		@Override
		public void send(Message message, Address[] addresses)
		{
			this.message = message;
			this.addresses = addresses;
			
			new Thread(this).start();
		}
		
		@Override
		public void run()
		{
			TransportConnector connector = null;
			
			while (connector == null)
			{
				connector = TransportProxy.this.acquireConnector();
				
				URLName url = connector.getTransport().getURLName();
				
				try
				{
					connector.getTransport().sendMessage(this.message, this.addresses);
				}
				catch (SendFailedException e)
				{
					log.error("Failed to send message to invalid addresses: " + InternetAddress.toString(this.addresses), e);
				}
				catch (MessagingException e)
				{
					log.debug(url.getProtocol() + " connection to " + url.getHost() + " is dead.", e);
					
					// Transport connection is dead
					connector.reconnect();
					
					connector = null;
				}
				catch (Throwable e)
				{
					log.error("Unexpected failure while sending message via " + url.getHost(), e);
				}
			}
			
			// Release connector back to the pool
			TransportProxy.this.releaseConnector(connector);
			
			// Release sender back to the pool
			TransportProxy.this.releaseSender(this);
		}
	}
}
