package com.juego.net;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.juego.guns.Armory;

/**
 * Packets over stream links, the way Bluetooth RFCOMM sockets carry them.
 * TCP sockets on localhost stand in for the Bluetooth sockets: both are
 * reliable byte streams, which is all LinkTransport sees.
 */
class LinkTransportTest {
	private static final File ASSETS = new File("assets");
	private static final Vector3 SPAWN_A = new Vector3(3, 4, 2);
	private static final Vector3 SPAWN_B = new Vector3(-6, 4, -4);

	@TempDir
	static Path tmp;
	private final List<AutoCloseable> toClose = new ArrayList<>();

	@BeforeAll
	static void init() throws IOException {
		Bullet.init();
		Files.writeString(tmp.resolve("Test.json"), "{model: maps/Bank.g3dj, spawnPoints: [[" + SPAWN_A.x + ","
				+ SPAWN_A.y + "," + SPAWN_A.z + "],[" + SPAWN_B.x + "," + SPAWN_B.y + "," + SPAWN_B.z + "]]}");
	}

	@AfterEach
	void closeAll() throws Exception {
		for (int i = toClose.size() - 1; i >= 0; i--)
			toClose.get(i).close();
	}

	/** A connected pair of sockets: [0] is the host's end, [1] the joining phone's. */
	private Socket[] socketPair() throws IOException {
		try (ServerSocket listener = new ServerSocket(0, 1, NetSocket.LOOPBACK)) {
			Socket joining = new Socket(NetSocket.LOOPBACK, listener.getLocalPort());
			Socket host = listener.accept();
			toClose.add(joining);
			toClose.add(host);
			return new Socket[] { host, joining };
		}
	}

	private InetSocketAddress link(LinkTransport transport, String name, Socket socket) throws IOException {
		return transport.addLink(name, socket.getInputStream(), socket.getOutputStream(), socket);
	}

	private static NetSocket.Datagram receive(Transport transport) throws InterruptedException {
		for (int i = 0; i < 300; i++) {
			NetSocket.Datagram d = transport.poll();
			if (d != null)
				return d;
			Thread.sleep(5);
		}
		return null;
	}

	private static void waitFor(String what, BooleanSupplier condition) throws InterruptedException {
		for (int i = 0; i < 300; i++) {
			if (condition.getAsBoolean())
				return;
			Thread.sleep(5);
		}
		fail("timed out waiting for: " + what);
	}

	@Test
	void packetsKeepTheirBoundariesBothWays() throws Exception {
		Socket[] pair = socketPair();
		LinkTransport host = new LinkTransport("Bluetooth"), phone = new LinkTransport("Bluetooth");
		toClose.add(host);
		toClose.add(phone);
		InetSocketAddress phoneAtHost = link(host, "Pixel", pair[0]);
		InetSocketAddress hostAtPhone = link(phone, "Moto", pair[1]);
		assertEquals("Bluetooth: Pixel", NetUtils.format(phoneAtHost));
		assertTrue(host.reaches(phoneAtHost));
		assertFalse(host.reaches(new InetSocketAddress("127.0.0.1", 25559)));

		byte[] small = { 7 }, big = new byte[Protocol.MAX_DATAGRAM];
		for (int i = 0; i < big.length; i++)
			big[i] = (byte) i;
		phone.send(hostAtPhone, small);
		phone.send(hostAtPhone, big);
		NetSocket.Datagram first = receive(host), second = receive(host);
		assertNotNull(first);
		assertNotNull(second);
		assertArrayEquals(small, first.data, "one packet, not merged with the next");
		assertArrayEquals(big, second.data);
		assertEquals(phoneAtHost, first.from, "the host knows who sent it");

		host.send(phoneAtHost, new byte[] { 1, 2 });
		NetSocket.Datagram reply = receive(phone);
		assertNotNull(reply);
		assertArrayEquals(new byte[] { 1, 2 }, reply.data);
		assertEquals(hostAtPhone, reply.from);
	}

	@Test
	void everyPhoneGetsItsOwnAddress() throws Exception {
		LinkTransport host = new LinkTransport("Bluetooth");
		toClose.add(host);
		// Two phones with the same name are still two players.
		InetSocketAddress a = link(host, "Galaxy", socketPair()[0]);
		InetSocketAddress b = link(host, "Galaxy", socketPair()[0]);
		assertNotEquals(a, b);
		assertEquals(2, host.linkCount());
	}

	@Test
	void aPhoneThatDisconnectsIsDropped() throws Exception {
		Socket[] pair = socketPair();
		LinkTransport host = new LinkTransport("Bluetooth");
		toClose.add(host);
		InetSocketAddress phone = link(host, "Pixel", pair[0]);
		pair[1].close(); // the phone walks away
		waitFor("the link closes", () -> host.linkCount() == 0);
		assertFalse(host.reaches(phone));
		host.send(phone, new byte[] { 1 }); // nothing happens, no exception
		assertNull(host.poll());
	}

	@Test
	void garbageClosesTheLink() throws Exception {
		Socket[] pair = socketPair();
		LinkTransport host = new LinkTransport("Bluetooth");
		toClose.add(host);
		link(host, "Raro", pair[0]);
		pair[1].getOutputStream().write(new byte[] { 0, 0 }); // a packet of length 0
		waitFor("the link closes", () -> host.linkCount() == 0);
	}

	@Test
	void sendingNeverBlocksTheGame() throws Exception {
		Socket[] pair = socketPair();
		LinkTransport host = new LinkTransport("Bluetooth");
		toClose.add(host);
		InetSocketAddress phone = link(host, "Lento", pair[0]);
		// The phone never reads: the socket buffers fill up, then the queue, then packets are dropped.
		long start = System.nanoTime();
		byte[] data = new byte[1000];
		for (int i = 0; i < 20_000; i++)
			host.send(phone, data);
		assertTrue((System.nanoTime() - start) / 1_000_000 < 2000, "20000 sends returned right away");
	}

	/**
	 * A whole match: the host plays on its own phone (UDP on localhost, as
	 * now) and a second phone joins over a link. They see each other and a
	 * shot from the linked phone hurts the host.
	 */
	@Test
	void aMatchOverALink() throws Exception {
		GameServer.Config config = new GameServer.Config();
		config.port = 0;
		config.maxPlayers = 2;
		config.map = "Test";
		Armory armory = Armory.load(new FileHandle(new File(ASSETS, Armory.GUNS_FILE)));
		GameServer server = new GameServer(config,
				path -> path.startsWith("data/maps/") && tmp.resolve(path.substring(10)).toFile().exists()
						? new FileHandle(tmp.resolve(path.substring(10)).toFile())
						: new FileHandle(new File(ASSETS, path)),
				armory, System::currentTimeMillis);
		toClose.add(server::dispose);
		LinkTransport bluetooth = new LinkTransport("Bluetooth");
		server.addTransport(bluetooth);

		GameClient host = new GameClient(System::currentTimeMillis);
		toClose.add(host::dispose);
		host.connect("127.0.0.1", server.getPort(), "Ana");

		Socket[] pair = socketPair();
		link(bluetooth, "Pixel de Beto", pair[0]); // the host accepts the phone...
		LinkTransport phoneSide = new LinkTransport("Bluetooth");
		InetSocketAddress hostAddress = link(phoneSide, "Moto de Ana", pair[1]); // ...which connected to it
		GameClient phone = new GameClient(System::currentTimeMillis);
		toClose.add(phone::dispose);
		phone.connect(phoneSide, hostAddress, "Beto");

		GameClient[] clients = { host, phone };
		pump(server, clients, "both connected and see each other",
				() -> host.getStatus() == GameClient.Status.CONNECTED && phone.getStatus() == GameClient.Status.CONNECTED
						&& host.getPlayer(phone.getLocalId()) != null && phone.getPlayer(host.getLocalId()) != null);
		assertEquals("Beto", host.getPlayer(phone.getLocalId()).name);
		assertTrue(server.describePlayers().contains("Bluetooth: Pixel de Beto"), server.describePlayers());

		Vector3 from = phone.getSpawn(), to = host.getSpawn();
		phone.sendFire(0, from, new Vector3(to).add(0, -1, 0).sub(from));
		pump(server, clients, "the host is hit", () -> host.getPlayer(host.getLocalId()).health < 100);
	}

	private static void pump(GameServer server, GameClient[] clients, String what, BooleanSupplier condition)
			throws InterruptedException {
		long deadline = System.currentTimeMillis() + 5000;
		while (System.currentTimeMillis() < deadline) {
			server.update();
			for (GameClient c : clients) {
				c.update();
				while (c.pollEvent() != null) {
					// not needed here
				}
			}
			if (condition.getAsBoolean())
				return;
			Thread.sleep(2);
		}
		fail("timed out waiting for: " + what);
	}
}
