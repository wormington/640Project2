package edu.wisc.cs.sdn.vnet.sw;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.MACAddress;

import java.util.ArrayList;
import java.util.HashMap;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

/**
 * @author Aaron Gember-Jacobson
 */
public class Switch extends Device
{	
	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Switch(String host, DumpFile logfile)
	{
		super(host,logfile);
		
		// Field init
		expiryLookup = new HashMap<MACAddress, Long>();
		ifaceLookup = new HashMap<MACAddress, Iface>();

		// Thread init and start
		expCheck = new Thread(new ExpirationCheck());
		expCheck.start();
	}

	/**
	 * Handle an Ethernet packet received on a specific interface.
	 * @param etherPacket the Ethernet packet that was received
	 * @param inIface the interface on which the packet was received
	 */
	public void handlePacket(Ethernet etherPacket, Iface inIface)
	{
		System.out.println("*** -> Received packet: " + etherPacket.toString().replace("\n", "\n\t"));

		/********************************************************************/

		/* ====== Record and update MAC and Iface entries ====== */

		// Remove an entry if the source MAC Address of the new packet does not
		// match the current MAC address associated with inIface.
		// Holds the lock on ifaceLookup.
		synchronized (ifaceLookup)
		{
			synchronized(expiryLookup)
			{
				// Update source MAC entry and set timer for lease
				expiryLookup.put(etherPacket.getSourceMAC(), System.currentTimeMillis() + LEASE_TIME);
				ifaceLookup.put(etherPacket.getSourceMAC(), inIface);
	
				/* ====== Send or broadcast packet ====== */
	
				// Check for an entry in the lookup table with the same MAC as our destination.
				// If entry is found, send packet there.
				if (ifaceLookup.get(etherPacket.getDestinationMAC()) != null)
				{
					sendPacket(etherPacket, ifaceLookup.get(etherPacket.getDestinationMAC()));
				}
				else
				{
					// If no matching destination, broadcast packet.
					for (Iface tempIface : getInterfaces().values())
					{
						if (!tempIface.equals(inIface))
							sendPacket(etherPacket, tempIface);
					}
				}
			}
		}
		/********************************************************************/
	}

	private class ExpirationCheck implements Runnable
	{
		public void run()
		{
			/* ====== Remove expired MAC entries ====== */
			while (true)
			{
				// Holds the lock on ifaceLookup, prevents interference from handlePacket()
				synchronized (ifaceLookup)
				{
					synchronized(expiryLookup)
					{
						// Make sure lists are not empty before checking
						if (!ifaceLookup.isEmpty() && !expiryLookup.isEmpty())
						{
							ArrayList<MACAddress> temp = new ArrayList<MACAddress>();
							// Iterate through the list and find expired leases
							for (MACAddress i : ifaceLookup.keySet())
							{
								// Remove any entry if expired
								if (expiryLookup.get(i) <= System.currentTimeMillis())
								{
									temp.add(i);
								}
							}
							
							if (!temp.isEmpty())
							{
								for (MACAddress j : temp)
								{
									expiryLookup.remove(j);
									ifaceLookup.remove(j);
								}
							}
						}
					}
				}

				// Put the thread to sleep. Otherwise this thread will hog execution time.
				// Specifications say that removing an entry within 1 second of its expiration
				// is okay, so we only sleep for a second.
				try
				{
					Thread.sleep(1000);
				} catch (InterruptedException e)
				{
					System.out.println(e.getMessage());
					System.exit(1);
				}
			}
		}
	}
	
	private Thread expCheck;
	private HashMap<MACAddress, Long> expiryLookup;
	private HashMap<MACAddress, Iface> ifaceLookup;
	private static final long LEASE_TIME = 15000; // milliseconds
}
