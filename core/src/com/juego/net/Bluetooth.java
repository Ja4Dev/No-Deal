package com.juego.net;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * Multiplayer over Bluetooth, for phones without a shared Wi-Fi. The game
 * only needs the links: the match runs over them exactly like over UDP (see
 * {@link LinkTransport}). Each platform that has Bluetooth implements this
 * (Android: Bluetooth Classic RFCOMM); elsewhere there is none and the menus
 * don't offer it.
 * <p>
 * The host's phone listens; the others find it in a list (phones paired
 * before, and nearby ones while it is visible) and connect to it. Callbacks
 * run on the game thread.
 */
public interface Bluetooth {

	/** A phone that can be joined: paired before, or found nearby. */
	final class Device {
		public final String name;
		/** The hardware address, which tells devices with the same name apart. */
		public final String address;
		public final boolean paired;

		public Device(String name, String address, boolean paired) {
			this.name = name;
			this.address = address;
			this.paired = paired;
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Device && ((Device) o).address.equals(address);
		}

		@Override
		public int hashCode() {
			return address.hashCode();
		}
	}

	/** The outcome of something that takes time (permissions, turning Bluetooth on, connecting). */
	interface Callback<T> {
		void ready(T result);

		/** @param reason what happened and what to do, for the player */
		void failed(String reason);
	}

	interface DeviceListener {
		void found(Device device);
	}

	/** A connection to the host: the transport and the host's address in it, for {@link GameClient#connect}. */
	final class Joined {
		public final LinkTransport transport;
		public final InetSocketAddress host;

		public Joined(LinkTransport transport, InetSocketAddress host) {
			this.transport = transport;
			this.host = host;
		}
	}

	/** False when this device has no Bluetooth at all. */
	boolean isSupported();

	/**
	 * Gets Bluetooth ready, asking the player for what is missing: its
	 * permissions, turning it on and, when hosting, making this phone visible
	 * to others for a few minutes.
	 */
	void prepare(boolean hosting, Callback<Void> done);

	/** This phone's Bluetooth name, the one the others see in their list. */
	String localName();

	/**
	 * Starts accepting phones; each one that connects becomes a link of the
	 * returned transport, until it is closed (then it stops listening too).
	 */
	LinkTransport host() throws IOException;

	/** Lists the phones paired before, then looks for others nearby, until {@link #stopScan}. */
	void startScan(DeviceListener listener);

	void stopScan();

	/** Connects to a host's phone in the background. */
	void connect(Device device, Callback<Joined> done);
}
