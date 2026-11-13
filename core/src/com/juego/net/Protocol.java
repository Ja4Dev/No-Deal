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
	public static final int VERSION = 6;
	public static final int DEFAULT_PORT = 25559;
	public static final int MAX_PLAYERS = 4;
	/** Keeps datagrams under the typical internet MTU to avoid IP fragmentation. */
	public static final int MAX_DATAGRAM = 1200;
	public static final int MAX_NAME_LENGTH = 16;
	/** Most players (people and bots) a match can have: all of them must fit in one snapshot datagram. */
	public static final int MAX_MATCH_PLAYERS = 36;

	public static final long CONNECT_TIMEOUT_MS = 5000;
	public static final long TIMEOUT_MS = 5000;
	/**
	 * How long a player who just joined may stay silent: the game freezes
	 * while it loads the level, which on a slow phone takes longer than
	 * {@link #TIMEOUT_MS}. It ends with the player's first state.
	 */
	public static final long LOADING_TIMEOUT_MS = 30000;
	public static final long RESEND_MS = 100;
	public static final long STATE_INTERVAL_MS = 1000 / 30;
	public static final long SNAPSHOT_INTERVAL_MS = 1000 / 20;
	public static final long RESPAWN_DELAY_MS = 3000;
	// Default match rules (the dedicated server can override them).
	public static final int DEFAULT_TIME_LIMIT_MINUTES = 10;
	public static final int DEFAULT_FRAG_LIMIT = 20;
	public static final long INTERMISSION_MS = 10_000;

	/** Remote players are drawn this far in the past, so there are always two snapshots to blend. */
	public static final long INTERPOLATION_DELAY_MS = 100;

	/** What every player spawns with: slot 0 = primary, slot 1 = secondary. */
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
	public static final byte MATCH_END = 13;
	/** Someone fired: lets the other clients play the shot with directional sound. Unreliable. */
	public static final byte SHOT = 14;
	/** Client asks to throw the gun in a slot. Reliable. */
	public static final byte DROP_WEAPON = 15;
	/** Client asks to take a gun from the ground. Reliable. */
	public static final byte PICKUP_WEAPON = 16;
	/** A gun appeared on the ground (thrown, dropped on death, or from a spawner). Reliable. */
	public static final byte WEAPON_SPAWN = 17;
	/** A gun left the ground (taken, despawned or reset). Reliable. */
	public static final byte WEAPON_REMOVE = 18;
	/** Sent to a player who got hit, with where the shot came from (for the HUD). Unreliable. */
	public static final byte DAMAGE = 19;
	/** A shot hit a player: where, for blood and the hit sound. Sent to everyone. Unreliable. */
	public static final byte HIT = 20;
	/** Client asks to be called something else. Reliable. */
	public static final byte RENAME = 21;
	/** Someone changed their name. Reliable. */
	public static final byte PLAYER_RENAMED = 22;
	/** A player can change their name at most this often. */
	public static final long RENAME_COOLDOWN_MS = 2000;
	/** Doing more than this damage makes an assist if someone else finishes the victim off (as in CS). */
	public static final int ASSIST_DAMAGE = 40;
	/** Headshots do this many times the damage, as in CS: one is lethal with any gun up to ~85 units. */
	public static final int HEADSHOT_MULTIPLIER = 4;

	/** Player id used as "killer" when a player dies by falling out of the map. */
	public static final int WORLD = 0;

	private Protocol() {
	}
}
