package com.juego.net;

/**
 * Constants of the No Deal network protocol (UDP).
 *
 * <pre>
 * datagram := magic:u16  ack:i32  count:u8  message*
 * message  := flags:u8  [id:i32 if reliable]  type:u8  length:u16  payload
 * </pre>
 *
 * {@code ack} is cumulative: every reliable message with an id up to it has
 * been delivered. See {@link Connection} for the reliability rules.
 */
public final class Protocol {
	public static final short MAGIC = 0x4E44; // "ND"
	/** Bumped on any incompatible change; checked in HELLO. */
	public static final int VERSION = 1;
	public static final int DEFAULT_PORT = 25559;
	public static final int MAX_PLAYERS = 4;
	/** Keeps datagrams under the typical internet MTU to avoid IP fragmentation. */
	public static final int MAX_DATAGRAM = 1200;
	public static final int MAX_NAME_LENGTH = 16;

	public static final long CONNECT_TIMEOUT_MS = 5000;
	public static final long TIMEOUT_MS = 5000;
	public static final long RESEND_MS = 100;
	public static final long STATE_INTERVAL_MS = 1000 / 30;
	public static final long SNAPSHOT_INTERVAL_MS = 1000 / 20;
	public static final long RESPAWN_DELAY_MS = 3000;
	/** Remote players are drawn this far in the past, so there are always two snapshots to blend. */
	public static final long INTERPOLATION_DELAY_MS = 100;

	// Fixed loadout for multiplayer: slot 0 = primary, slot 1 = secondary.
	public static final String[] LOADOUT = { "m4a1", "magnum" };

	// Message types.
	public static final byte HELLO = 1;
	public static final byte WELCOME = 2;
	public static final byte REJECT = 3;
	public static final byte PLAYER_JOINED = 4;
	public static final byte PLAYER_LEFT = 5;
	public static final byte PLAYER_STATE = 6;
	public static final byte FIRE = 7;
	public static final byte SNAPSHOT = 8;
	public static final byte KILL = 9;
	public static final byte RESPAWN = 10;
	public static final byte SET_POSITION = 11;
	public static final byte DISCONNECT = 12;

	/** Player id used as "killer" when a player dies by falling out of the map. */
	public static final int WORLD = 0;

	private Protocol() {
	}
}
