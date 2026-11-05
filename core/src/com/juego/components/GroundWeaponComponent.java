package com.juego.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.juego.guns.GroundWeapons;

/**
 * A gun lying on the ground (or flying there after being thrown). The model is
 * a first-person view model, built pointing down -Z with +Y up, so it is
 * scaled to {@code worldLength}, rolled onto its side and spun to its yaw.
 */
public class GroundWeaponComponent implements Component {
	/** How long a thrown gun takes to land. */
	public static final long THROW_MS = 450;
	private static final float ARC_HEIGHT = 1.2f;

	public final GroundWeapons.Weapon weapon;
	/** When the throw started (wall clock, for the animation only). */
	public final long thrownAtMs;
	private final float scale, restHeight;
	private final Vector3 modelCenter;
	private final Vector3 position = new Vector3();
	private boolean landed;

	/**
	 * @param modelCenter    center of the model's bounding box
	 * @param modelLength    its length along Z
	 * @param modelThickness its width along X, which ends up vertical once it lies on its side
	 */
	public GroundWeaponComponent(GroundWeapons.Weapon weapon, long nowMs, Vector3 modelCenter, float modelLength,
			float modelThickness) {
		this.weapon = weapon;
		this.thrownAtMs = weapon.from.epsilonEquals(weapon.position, 0.01f) ? nowMs - THROW_MS : nowMs;
		this.modelCenter = new Vector3(modelCenter);
		this.scale = weapon.def.worldLength / Math.max(0.001f, modelLength);
		this.restHeight = modelThickness * scale / 2 + 0.05f;
	}

	/** Sets the model transform for this moment. @return false once it has landed and won't move again */
	public boolean update(Matrix4 transform, long nowMs) {
		if (landed)
			return false;
		float t = Math.min(1f, (nowMs - thrownAtMs) / (float) THROW_MS);
		position.set(weapon.from).lerp(weapon.position, Interpolation.pow2Out.apply(t));
		position.y += restHeight * t + ARC_HEIGHT * 4 * t * (1 - t);
		float roll = 90 * t;
		float spin = 200 * (1 - t); // it turns a bit while flying
		transform.idt().translate(position).rotate(Vector3.Y, weapon.yaw + spin).rotate(Vector3.Z, roll)
				.scale(scale, scale, scale).translate(-modelCenter.x, -modelCenter.y, -modelCenter.z);
		landed = t >= 1f;
		return true;
	}
}
