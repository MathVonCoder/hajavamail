/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.hajavamail;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.event.TransportListener;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author mathieu
 */
public class TransportProxyTest {

	public TransportProxyTest() {
	}

	@Before
	public void setUp() {
	}

	@Test
	public void testSendManyMessages() throws Exception {
		java.util.Properties properties = new java.util.Properties();
		properties.setProperty("mail.transport.protocol", "smtp");
		properties.setProperty("mail.host", "localhost");
		properties.setProperty("mail.transport.pool-size", "10");

		javax.mail.Session session = javax.mail.Session.getInstance(properties);
		javax.mail.Transport transport = session.getTransport();

		long startTime = System.currentTimeMillis();

		transport.connect();

		javax.mail.Address address = new javax.mail.internet.InternetAddress("test@adelya.com");

		for (int i = 1; i <= 25 ; ++i) {
			javax.mail.Message message = new javax.mail.internet.MimeMessage(session);
			message.setRecipient(javax.mail.Message.RecipientType.TO, address);
			message.setSubject("Test #" + i);
			message.setText("");

			transport.sendMessage(message, message.getAllRecipients());
		}

		transport.close();

		long endTime = System.currentTimeMillis();
		System.out.println("Total execution time = " + (startTime - endTime) + " ms");
	}
/*
	@Test
	public void testSendMessage() throws Exception {
		System.out.println("sendMessage");
		Message message = null;
		Address[] addresses = null;
		TransportProxy instance = null;
		instance.sendMessage(message, addresses);
		fail("The test case is a prototype.");
	}

	@Test
	public void testAcquireSender() {
		System.out.println("acquireSender");
		TransportProxy instance = null;
		TransportProxy.MessageSender expResult = null;
		TransportProxy.MessageSender result = instance.acquireSender();
		assertEquals(expResult, result);
		fail("The test case is a prototype.");
	}

	@org.junit.Test
	public void testReleaseSender() {
		System.out.println("releaseSender");
		TransportProxy.MessageSender sender = null;
		TransportProxy instance = null;
		instance.releaseSender(sender);
		fail("The test case is a prototype.");
	}

	@org.junit.Test
	public void testAcquireConnector() {
		System.out.println("acquireConnector");
		TransportProxy instance = null;
		TransportProxy.TransportConnector expResult = null;
		TransportProxy.TransportConnector result = instance.acquireConnector();
		assertEquals(expResult, result);
		fail("The test case is a prototype.");
	}

	@org.junit.Test
	public void testReleaseConnector() {
		System.out.println("releaseConnector");
		TransportProxy.TransportConnector connector = null;
		TransportProxy instance = null;
		instance.releaseConnector(connector);
		fail("The test case is a prototype.");
	}

	@org.junit.Test
	public void testAddTransportListener() {
		System.out.println("addTransportListener");
		TransportListener listener = null;
		TransportProxy instance = null;
		instance.addTransportListener(listener);
		fail("The test case is a prototype.");
	}

	@org.junit.Test
	public void testRemoveTransportListener() {
		System.out.println("removeTransportListener");
		TransportListener listener = null;
		TransportProxy instance = null;
		instance.removeTransportListener(listener);
		fail("The test case is a prototype.");
	}

	@org.junit.Test
	public void testClose() throws Exception {
		System.out.println("close");
		TransportProxy instance = null;
		instance.close();
		fail("The test case is a prototype.");
	}
*/
}
