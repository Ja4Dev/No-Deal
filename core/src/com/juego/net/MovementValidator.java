package com.juego.net;

import com.badlogic.gdx.math.Vector3;
import com.juego.utils.FallDamage;
import com.juego.utils.PlayerMovement;

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
	/** Margin over the fastest legal speed. */
	public static final float SPEED_MARGIN = 1.12f;
	/** Normal top speed (bunnyhopping can reach PlayerMovement.MAX_SPEED) plus margin. */
	public static final float MAX_HORIZONTAL_SPEED = PlayerMovement.MAX_SPEED * SPEED_MARGIN;
	/** Falling, up to the character controller's limit, plus margin. */
	public static final float MAX_FALL_SPEED = FallDamage.MAX_FALL_SPEED * 1.1f;
	/** Going up: jumping, or climbing stairs at full speed, plus margin. */
	public static final float MAX_RISE_SPEED = 30f;
	public static final float SLACK = 0.5f;
	public static final long MAX_CLOCK_AHEAD_MS = 250;
	/**
	 * After the server lowers the speed limit, players keep the old one this
	 * long: they move by the old rules until the change reaches them.
	 */
	public static final long LIMIT_GRACE_MS = 1500;

	private final Vector3 lastPosition = new Vector3();
	private boolean hasBase;
	private long lastClientMs, clientBaseMs, serverBaseMs, resetServerMs;
	private float maxHorizontalSpeed = MAX_HORIZONTAL_SPEED, maxRiseSpeed = MAX_RISE_SPEED;
	/** A lower limit that applies from {@link #lowerAtMs} on (Long.MAX_VALUE: none). */
	private float lowerHorizontal, lowerRise;
	private long lowerAtMs = Long.MAX_VALUE;

	/**
	 * Follows the server's sv_maxspeed / sv_maxvelocity. A higher limit applies
	 * right away, a lower one after {@link #LIMIT_GRACE_MS}.
	 *
	 * @param fastest the fastest legal horizontal speed (see MovementSettings.fastestSpeed)
	 * @param run     the running speed (climbing stairs goes up about as fast)
	 */
	public void setLimits(float fastest, float run, long serverNowMs) {
		float horizontal = fastest * SPEED_MARGIN, rise = Math.max(MAX_RISE_SPEED, run * 1.5f);
		applyLowering(serverNowMs);
		if (horizontal >= maxHorizontalSpeed && rise >= maxRiseSpeed) {
			maxHorizontalSpeed = horizontal;
			maxRiseSpeed = rise;
			lowerAtMs = Long.MAX_VALUE;
			return;
		}
		maxHorizontalSpeed = Math.max(maxHorizontalSpeed, horizontal);
		maxRiseSpeed = Math.max(maxRiseSpeed, rise);
		lowerHorizontal = horizontal;
		lowerRise = rise;
		lowerAtMs = serverNowMs + LIMIT_GRACE_MS;
	}

	private void applyLowering(long serverNowMs) {
		if (serverNowMs >= lowerAtMs) {
			maxHorizontalSpeed = lowerHorizontal;
			maxRiseSpeed = lowerRise;
			lowerAtMs = Long.MAX_VALUE;
		}
	}

	/** The horizontal speed allowed now, margin included. */
	public float getMaxHorizontalSpeed() {
		return maxHorizontalSpeed;
	}

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
		applyLowering(serverNowMs);
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
		float rise = position.y - lastPosition.y;
		if (horizontal > maxHorizontalSpeed * elapsed + SLACK || rise > maxRiseSpeed * elapsed + SLACK
				|| -rise > MAX_FALL_SPEED * elapsed + SLACK)
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
