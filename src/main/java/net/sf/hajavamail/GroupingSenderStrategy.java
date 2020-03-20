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

import java.util.Collection;
import java.util.Iterator;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;

/**
 * Sender strategy implementation that groups the target addresses before sending.
 * 
 * @author  Paul Ferraro
 * @version $Revision: 1.3 $
 * @since   1.0
 */
public abstract class GroupingSenderStrategy extends SimpleSenderStrategy
{
	/**
	 * @see net.sf.hajavamail.SenderStrategy#send(net.sf.hajavamail.Sender, javax.mail.Message, javax.mail.Address[])
	 */
	public void send(Sender sender, Message message, Address[] addresses) throws MessagingException
	{
		if (addresses.length > 1)
		{
			Iterator addressGroups = this.groupAddresses(addresses).iterator();
			
			while (addressGroups.hasNext())
			{
				Address[] addressGroup = (Address[]) addressGroups.next();
				
				sender.send(message, addressGroup);
			}
		}
		else
		{
			sender.send(message, addresses);
		}
	}
	
	/**
	 * Organizes the specified addresses into groups.
	 * @param addresses all recipients of the message to be sent
	 * @return a collection of javax.mail.Address[]
	 */
	protected abstract Collection groupAddresses(Address[] addresses);
}
