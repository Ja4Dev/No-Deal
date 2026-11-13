package com.juego.net;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A UDP socket with a background receiver thread.
 * <p>
 * The thread only blocks on {@code receive()} and queues raw datagrams; all
 * parsing and game logic happens on the caller's thread via {@link #poll()}.
 * That keeps the game single-threaded (Bullet's JNI wrappers are not
 * thread-safe) while never stalling a frame waiting for the network.
 */
public class NetSocket implements Transport {
	private static final int MAX_QUEUED = 4096;

	public static class Datagram {
		public final InetSocketAddress from;
		public final byte[] data;

		Datagram(InetSocketAddress from, byte[] data) {
			this.from = from;
			this.data = data;
		}
	}

	private final DatagramSocket socket;
	private final ConcurrentLinkedQueue<Datagram> inbox = new ConcurrentLinkedQueue<>();
	private final AtomicInteger queued = new AtomicInteger();
	private final Thread receiver;
	private volatile boolean running = true;

	/** @param port local port to bind, or 0 for any free port (clients). */
	public NetSocket(int port) throws SocketException {
		this(new DatagramSocket(port));
	}

	/**
	 * @param localOnly only this computer can reach the socket (a private
	 *                  singleplayer server), not the network
	 */
	public NetSocket(int port, boolean localOnly) throws SocketException {
		this(localOnly ? new DatagramSocket(new InetSocketAddress(LOOPBACK, port)) : new DatagramSocket(port));
	}

	/**
	 * 127.0.0.1, where the game connects to its own private server. Not
	 * InetAddress.getLoopbackAddress(): on Android that is the IPv6 ::1, which
	 * never receives what is sent to 127.0.0.1.
	 */
	static final InetAddress LOOPBACK;

	static {
		try {
			LOOPBACK = InetAddress.getByAddress(new byte[] { 127, 0, 0, 1 });
		} catch (java.net.UnknownHostException e) {
			throw new ExceptionInInitializerError(e); // can't happen: 4 bytes is a valid IPv4 address
		}
	}

	private NetSocket(DatagramSocket socket) {
		this.socket = socket;
		receiver = new Thread(this::receiveLoop, "NetSocket-" + socket.getLocalPort());
		receiver.setDaemon(true);
		receiver.start();
	}

	private void receiveLoop() {
		byte[] buffer = new byte[Protocol.MAX_DATAGRAM * 2];
		DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
		while (running) {
			try {
				packet.setLength(buffer.length);
				socket.receive(packet);
				if (queued.get() >= MAX_QUEUED)
					continue; // the game thread is not keeping up: drop rather than grow forever
				inbox.add(new Datagram((InetSocketAddress) packet.getSocketAddress(),
						Arrays.copyOf(packet.getData(), packet.getLength())));
				queued.incrementAndGet();
			} catch (IOException e) {
				if (running)
					NetLog.error("Net", "receive failed: " + e.getMessage());
			}
		}
	}

	/** Next received datagram, or null if there is none. Never blocks. */
	@Override
	public Datagram poll() {
		Datagram d = inbox.poll();
		if (d != null)
			queued.decrementAndGet();
		return d;
	}

	@Override
	public boolean reaches(InetSocketAddress address) {
		return !address.isUnresolved(); // an IP address, not a Bluetooth device
	}

	@Override
	public void send(InetSocketAddress to, byte[] data) {
		try {
			socket.send(new DatagramPacket(data, data.length, to));
		} catch (IOException e) {
			// UDP is best-effort: the reliable channel resends what matters.
			NetLog.error("Net", "send to " + to + " failed: " + e.getMessage());
		}
	}

	public int getLocalPort() {
		return socket.getLocalPort();
	}

	@Override
	public void close() {
		running = false;
		socket.close(); // unblocks receive()
	}
}
