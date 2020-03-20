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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.mail.Address;
import javax.mail.internet.InternetAddress;

/**
 * Grouping sender strategy implementation that groups target addresses by host.
 * 
 * @author  Paul Ferraro
 * @version $Revision: 1.3 $
 * @since   1.0
 */
public class HostGroupingSenderStrategy extends GroupingSenderStrategy
{
	/**
	 * @see net.sf.hajavamail.GroupingSenderStrategy#groupAddresses(javax.mail.Address[])
	 */
	protected Collection groupAddresses(Address[] addresses)
	{
		Map addressListMap = new HashMap();

		for (int i = 0; i < addresses.length; ++i)
		{
			InternetAddress internetAddress = (InternetAddress) addresses[i];
			String address = internetAddress.getAddress();
			String host = address.substring(address.indexOf("@") + 1).toLowerCase();
			
			List addressList = (List) addressListMap.get(host);
			
			if (addressList == null)
			{
				addressList = new LinkedList();
				addressListMap.put(host, addressList);
			}
			
			addressList.add(addresses[i]);
		}
		
		List addressGroupList = new ArrayList(addressListMap.size());
		Iterator addressLists = addressListMap.values().iterator();
		
		while (addressLists.hasNext())
		{
			List addressList = (List) addressLists.next();
			
			addressGroupList.add(addressList.toArray(new Address[addressList.size()]));
		}
		
		return addressGroupList;
	}
}
