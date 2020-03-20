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

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;

/**
 * To configure a specific sender strategy use the {@link net.sf.hajavamail.TransportProxy#SENDER_STRATEGY} session property.
 * 
 * @author  Paul Ferraro
 * @version $Revision: 1.3 $
 * @since   1.0
 */
public interface SenderStrategy
{
	/**
	 * Send the specified message to the specified addresses using the specified sender.
	 * @param sender object responsible for actually sending the message.
	 * @param message JavaMail message to send
	 * @param addresses an array of Addresses to which to sending the message
	 * @throws MessagingException if message cannot be sent
	 */
	public void send(Sender sender, Message message, Address[] addresses) throws MessagingException;
}
