package com.juego.net;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.InetSocketAddress;

import org.junit.jupiter.api.Test;

class NetSocketTest {
	/**
	 * The game connects to its private (singleplayer) server at "127.0.0.1".
	 * On Android InetAddress.getLoopbackAddress() is ::1, and a server bound
	 * there never heard the client: it must be bound to IPv4 127.0.0.1.
	 */
	@Test
	void privateServerListensOnIpv4Loopback() throws Exception {
		assertEquals("127.0.0.1", NetSocket.LOOPBACK.getHostAddress());
		try (NetSocket server = new NetSocket(0, true); NetSocket client = new NetSocket(0)) {
			byte[] hello = { 1, 2, 3 };
			client.send(new InetSocketAddress("127.0.0.1", server.getLocalPort()), hello);
			NetSocket.Datagram received = null;
			for (int i = 0; i < 200 && received == null; i++) {
				received = server.poll();
				if (received == null)
					Thread.sleep(10);
			}
			assertNotNull(received, "the private server receives what is sent to 127.0.0.1");
			assertArrayEquals(hello, received.data);
		}
	}
}
