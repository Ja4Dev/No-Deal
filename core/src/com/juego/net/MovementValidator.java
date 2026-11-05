package com.juego.net;

import com.badlogic.gdx.math.Vector3;

/**
 * Server-side sanity check of client-reported movement ("trust, but verify").
 * Clients simulate their own movement for responsiveness; the server rejects
 * positions that couldn't have been reached in the elapsed time.
 * <p>
 * Elapsed time is taken from the client's own timestamps, so packets that
 * arrive bunched together after network jitter are judged by when they were
 * sent. To stop a client from simply claiming that more time has passed (a
 * speed hack), its clock may never run ahead of the server's by more than
 * {@link #MAX_CLOCK_AHEAD_MS}.
 */
public class MovementValidator {
	/** Run speed (10) plus margin. */
	public static final float MAX_HORIZONTAL_SPEED = 12.5f;
	/** Jumping (15) and falling (up to Bullet's default fall speed of 55) plus margin. */
	public static final float MAX_VERTICAL_SPEED = 60f;
	public static final float SLACK = 0.5f;
	public static final long MAX_CLOCK_AHEAD_MS = 250;

	private final Vector3 lastPosition = new Vector3();
	private boolean hasBase;
	private long lastClientMs, clientBaseMs, serverBaseMs, resetServerMs;

	/** Starts over from a position the server chose (spawn, respawn or correction). */
	public void reset(Vector3 position, long serverNowMs) {
		lastPosition.set(position);
		resetServerMs = serverNowMs;
		hasBase = false;
	}

	public Vector3 getLastPosition() {
		return lastPosition;
	}

	/** @return true and remember the position if it is plausible; false to reject it. */
	public boolean accept(Vector3 position, long clientMs, long serverNowMs) {
		float elapsed;
		if (!hasBase) {
			// First state after a reset: the client's clock isn't known yet, so allow
			// whatever movement fits in the server time since the reset.
			elapsed = Math.max(0, serverNowMs - resetServerMs) / 1000f;
		} else {
			if (clientMs < lastClientMs)
				return false;
			if ((clientMs - clientBaseMs) - (serverNowMs - serverBaseMs) > MAX_CLOCK_AHEAD_MS)
				return false; // client clock running fast
			elapsed = (clientMs - lastClientMs) / 1000f;
		}
		float dx = position.x - lastPosition.x, dz = position.z - lastPosition.z;
		float horizontal = (float) Math.sqrt(dx * dx + dz * dz);
		float vertical = Math.abs(position.y - lastPosition.y);
		if (horizontal > MAX_HORIZONTAL_SPEED * elapsed + SLACK || vertical > MAX_VERTICAL_SPEED * elapsed + SLACK)
			return false;
		if (!hasBase) {
			hasBase = true;
			clientBaseMs = clientMs;
			serverBaseMs = serverNowMs;
		}
		lastClientMs = clientMs;
		lastPosition.set(position);
		return true;
	}
}
