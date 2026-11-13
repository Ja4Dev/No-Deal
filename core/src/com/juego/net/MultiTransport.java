package com.juego.net;

import java.net.InetSocketAddress;

import com.badlogic.gdx.utils.Array;

/**
 * Several transports as one: a host hearing its own client over UDP and the
 * other phones over Bluetooth. Packets go out through the transport that
 * reaches the address.
 */
public class MultiTransport implements Transport {
	private final Array<Transport> transports = new Array<>();

	public MultiTransport(Transport first) {
		transports.add(first);
	}

	public void add(Transport transport) {
		transports.add(transport);
	}

	@Override
	public NetSocket.Datagram poll() {
		for (int i = 0; i < transports.size; i++) {
			NetSocket.Datagram d = transports.get(i).poll();
			if (d != null)
				return d;
		}
		return null;
	}

	@Override
	public void send(InetSocketAddress to, byte[] data) {
		for (int i = 0; i < transports.size; i++) {
			if (transports.get(i).reaches(to)) {
				transports.get(i).send(to, data);
				return;
			}
		}
	}

	@Override
	public boolean reaches(InetSocketAddress address) {
		for (int i = 0; i < transports.size; i++)
			if (transports.get(i).reaches(address))
				return true;
		return false;
	}

	@Override
	public void close() {
		for (int i = 0; i < transports.size; i++)
			transports.get(i).close();
	}
}
