package com.juego.net;

import java.io.Closeable;
import java.net.InetSocketAddress;

/**
 * How the game's packets travel: UDP over Wi-Fi ({@link NetSocket}) or
 * Bluetooth links ({@link LinkTransport}). The server and the client only
 * send and receive packets to and from addresses, so the same match works
 * over any of them.
 * <p>
 * Addresses identify the other side: a real IP and port for UDP, an
 * unresolved address naming the device for a Bluetooth link.
 */
public interface Transport extends Closeable {
	/** Next received packet, or null if there is none. Never blocks: called on the game thread. */
	NetSocket.Datagram poll();

	/** Sends a packet. Best effort, like UDP: the game's own reliable channel resends what matters. */
	void send(InetSocketAddress to, byte[] data);

	/** Whether this transport is the way to reach that address. */
	boolean reaches(InetSocketAddress address);

	@Override
	void close();
}
