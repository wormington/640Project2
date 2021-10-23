package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.MACAddress;

/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device
{	
	/** Routing table for the router */
	private RouteTable routeTable;
	
	/** ARP cache for the router */
	private ArpCache arpCache;
	
	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Router(String host, DumpFile logfile)
	{
		super(host,logfile);
		this.routeTable = new RouteTable();
		this.arpCache = new ArpCache();
	}
	
	/**
	 * @return routing table for the router
	 */
	public RouteTable getRouteTable()
	{ return this.routeTable; }
	
	/**
	 * Load a new routing table from a file.
	 * @param routeTableFile the name of the file containing the routing table
	 */
	public void loadRouteTable(String routeTableFile)
	{
		if (!routeTable.load(routeTableFile, this))
		{
			System.err.println("Error setting up routing table from file "
					+ routeTableFile);
			System.exit(1);
		}
		
		System.out.println("Loaded static route table");
		System.out.println("-------------------------------------------------");
		System.out.print(this.routeTable.toString());
		System.out.println("-------------------------------------------------");
	}
	
	/**
	 * Load a new ARP cache from a file.
	 * @param arpCacheFile the name of the file containing the ARP cache
	 */
	public void loadArpCache(String arpCacheFile)
	{
		if (!arpCache.load(arpCacheFile))
		{
			System.err.println("Error setting up ARP cache from file "
					+ arpCacheFile);
			System.exit(1);
		}
		
		System.out.println("Loaded static ARP cache");
		System.out.println("----------------------------------");
		System.out.print(this.arpCache.toString());
		System.out.println("----------------------------------");
	}

	/**
	 * Handle an Ethernet packet received on a specific interface.
	 * @param etherPacket the Ethernet packet that was received
	 * @param inIface the interface on which the packet was received
	 */
	public void handlePacket(Ethernet etherPacket, Iface inIface)
	{
		System.out.println("*** -> Received packet: " +
                etherPacket.toString().replace("\n", "\n\t"));
		
		/********************************************************************/
		/* TODO: Handle packets                                             */
		
		// Check for IPv4 packet. Drop if not IPv4.
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4)
		{
			return;
		}
		
		// Get IPv4 packet from Ethernet packet.
		IPv4 ipv4Packet = (IPv4) etherPacket.getPayload();
		
		// Get checksum from packet and check. Set new checksum.
		short tempChk = ipv4Packet.getChecksum();
		ipv4Packet.resetChecksum();
		ipv4Packet.serialize();
		short newChk = ipv4Packet.getChecksum();
		
		// If checksums are different, drop packet.
		if (tempChk != newChk)
		{
			return;
		}
		
		// Update ttl.
		int tempTtl = Byte.toUnsignedInt(ipv4Packet.getTtl());
		byte newTtl = (new Integer(tempTtl - 1)).byteValue();
		ipv4Packet.setTtl(newTtl);
		
		// If ttl == 0, drop packet.
		if (ipv4Packet.getTtl() == 0)
		{
			return;
		}
		
		// Set checksum after ttl update
		ipv4Packet.resetChecksum();
		ipv4Packet.serialize();
		
		// If packet destination IP matches interface IP, drop packet.
		for (Iface i : getInterfaces().values())
		{
			if (i.getIpAddress() == ipv4Packet.getDestinationAddress())
			{
				return;
			}
		}
		
		// Find route
		int destIp = ipv4Packet.getDestinationAddress();
		RouteEntry route = routeTable.lookup(destIp);
		
		//Test if route exists 
		if (null == route) {
			// Drop, no match
			return;
		}
		
		// Check Gateway Address of RouteEntry for use in ArpTable lookup.
		ArpEntry finalArp = null;
		int gatewayIp = route.getGatewayAddress();
		
		// If the Gateway Address != 0.0.0.0, use it do ArpTable lookup.
		// Otherwise use the destination ip in the ArpTable lookup.
		if (gatewayIp != 0)
		{
			finalArp = arpCache.lookup(gatewayIp);
		}
		else
		{
			finalArp = arpCache.lookup(destIp);
		}
				
		
		if (null == finalArp) 
		{
			//Drop null
			return;
		}
		
		MACAddress finalMac = finalArp.getMac();
		
		// Update ethernet header
		etherPacket.setSourceMACAddress(route.getInterface().getMacAddress().toString());
		etherPacket.setDestinationMACAddress(finalMac.toString());
		
		// Send packet
		sendPacket(etherPacket, route.getInterface());
		
		// TODO: Perform lookups and send packet to dest.
		/********************************************************************/
	}
}
