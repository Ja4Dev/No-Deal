package com.juego.net;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;

import com.badlogic.gdx.utils.Array;

public final class NetUtils {
	private NetUtils() {
	}

	/** This machine's LAN IPv4 addresses, to tell other players where to connect. */
	public static Array<String> lanAddresses() {
		Array<String> result = new Array<>();
		try {
			for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
				if (!nic.isUp() || nic.isLoopback() || nic.isVirtual())
					continue;
				for (InetAddress address : Collections.list(nic.getInetAddresses())) {
					if (address instanceof Inet4Address && address.isSiteLocalAddress())
						result.add(address.getHostAddress());
				}
			}
		} catch (SocketException e) {
			NetLog.error("Net", "Could not list network interfaces: " + e.getMessage());
		}
		return result;
	}

	/**
	 * "192.168.1.20:51234", without the leading slash of
	 * InetSocketAddress.toString(); a Bluetooth link shows the device name.
	 */
	public static String format(InetSocketAddress address) {
		if (address.isUnresolved() && address.getHostString().contains(": "))
			return address.getHostString(); // "Bluetooth: Pixel 6"
		return (address.getAddress() != null ? address.getAddress().getHostAddress() : address.getHostString())
				+ ":" + address.getPort();
	}
}
