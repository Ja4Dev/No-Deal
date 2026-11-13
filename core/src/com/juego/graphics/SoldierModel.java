package com.juego.graphics;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.CylinderShapeBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.SphereShapeBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.juego.utils.CharacterShape;

/**
 * The players' character: a low-poly soldier (boots, knee pads, tactical vest
 * with pouches, shoulder pads, helmet and goggles, and a rifle held at the
 * shoulder), built in code so there is no model file to load. It fits the
 * character's size: 6 units tall, centered on the capsule's center, facing +Z
 * like a player with yaw 0.
 * <p>
 * Its parts are separate nodes so it can be animated (see {@link Pose}): the
 * legs swing at the hips, and the arms, rifle and head follow where the player
 * aims. The vest, shoulder and knee pads use the material {@link #TEAM}, which
 * each player's copy paints in its own color.
 */
public final class SoldierModel {
	/** Material tinted per player (vest, pads). */
	public static final String TEAM = "team";

	/** Distinct, slightly muted colors, one per player id. */
	private static final Color[] TEAM_COLORS = { new Color(0.62f, 0.16f, 0.14f, 1), new Color(0.16f, 0.32f, 0.62f, 1),
			new Color(0.75f, 0.6f, 0.15f, 1), new Color(0.2f, 0.5f, 0.25f, 1), new Color(0.75f, 0.38f, 0.12f, 1),
			new Color(0.45f, 0.22f, 0.6f, 1), new Color(0.15f, 0.55f, 0.6f, 1), new Color(0.7f, 0.7f, 0.68f, 1) };

	// Joint positions (relative to the capsule's center).
	private static final float HIP_Y = -0.55f, HIP_X = 0.42f, NECK_Y = 1.6f, SHOULDER_Y = 1.25f;

	private static final long ATTRIBUTES = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal;

	private SoldierModel() {
	}

	public static Model build() {
		Material team = material(TEAM, 0.5f, 0.5f, 0.5f, 8);
		Material cloth = material("cloth", 0.27f, 0.29f, 0.25f, 2);
		Material dark = material("dark", 0.09f, 0.09f, 0.1f, 16);
		Material skin = material("skin", 0.78f, 0.6f, 0.47f, 4);
		Material helmet = material("helmet", 0.24f, 0.27f, 0.2f, 10);
		Material lens = material("lens", 0.05f, 0.08f, 0.1f, 60);

		ModelBuilder mb = new ModelBuilder();
		mb.begin();
		leg(mb, "legL", HIP_X, cloth, team, dark);
		leg(mb, "legR", -HIP_X, cloth, team, dark);

		node(mb, "body", 0, 0, 0);
		MeshPartBuilder b = mb.part("body-cloth", GL20.GL_TRIANGLES, ATTRIBUTES, cloth);
		box(b, 0, 0.55f, 0, 1.7f, 1.8f, 0.9f); // torso
		b = mb.part("body-team", GL20.GL_TRIANGLES, ATTRIBUTES, team);
		box(b, 0, 0.62f, 0.47f, 1.62f, 1.35f, 0.18f); // vest, front
		box(b, 0, 0.62f, -0.47f, 1.62f, 1.35f, 0.14f); // and back
		box(b, 0.98f, 1.28f, 0, 0.42f, 0.34f, 0.78f); // shoulder pads
		box(b, -0.98f, 1.28f, 0, 0.42f, 0.34f, 0.78f);
		b = mb.part("body-dark", GL20.GL_TRIANGLES, ATTRIBUTES, dark);
		box(b, 0, -0.42f, 0, 1.52f, 0.36f, 0.86f); // belt
		for (float x : new float[] { -0.5f, 0, 0.5f })
			box(b, x, 0.12f, 0.6f, 0.34f, 0.32f, 0.14f); // pouches
		box(b, -0.62f, -0.42f, 0.25f, 0.3f, 0.42f, 0.3f); // holster
		b = mb.part("body-skin", GL20.GL_TRIANGLES, ATTRIBUTES, skin);
		cylinder(b, 0, 1.55f, 0, 0.42f, 0.32f); // neck

		node(mb, "head", 0, NECK_Y, 0);
		b = mb.part("head-skin", GL20.GL_TRIANGLES, ATTRIBUTES, skin);
		sphere(b, 0, 0.5f, 0.03f, 0.82f, 0.95f, 0.86f);
		b = mb.part("head-helmet", GL20.GL_TRIANGLES, ATTRIBUTES, helmet);
		sphere(b, 0, 0.82f, -0.02f, 1.08f, 0.72f, 1.12f); // shell
		box(b, 0, 0.6f, 0.04f, 1.1f, 0.1f, 1.16f); // rim
		b = mb.part("head-lens", GL20.GL_TRIANGLES, ATTRIBUTES, lens);
		box(b, 0, 0.52f, 0.4f, 0.7f, 0.18f, 0.12f); // goggles

		// Arms and rifle turn together around the shoulders, with the aim.
		node(mb, "aim", 0, SHOULDER_Y, 0);
		b = mb.part("aim-cloth", GL20.GL_TRIANGLES, ATTRIBUTES, cloth);
		Vector3 rightShoulder = new Vector3(-0.98f, 0, 0), rightElbow = new Vector3(-0.92f, -0.72f, 0.38f),
				rightHand = new Vector3(-0.42f, -0.36f, 0.78f);
		Vector3 leftShoulder = new Vector3(0.98f, 0, 0), leftElbow = new Vector3(0.62f, -0.62f, 0.78f),
				leftHand = new Vector3(-0.28f, -0.24f, 1.45f);
		limb(b, rightShoulder, rightElbow, 0.42f);
		limb(b, rightElbow, rightHand, 0.36f);
		limb(b, leftShoulder, leftElbow, 0.42f);
		limb(b, leftElbow, leftHand, 0.36f);
		b = mb.part("aim-dark", GL20.GL_TRIANGLES, ATTRIBUTES, dark);
		box(b, rightHand.x, rightHand.y, rightHand.z, 0.26f, 0.26f, 0.26f); // gloves
		box(b, leftHand.x, leftHand.y, leftHand.z, 0.26f, 0.26f, 0.26f);
		float gx = -0.36f;
		box(b, gx, -0.12f, 0.95f, 0.2f, 0.3f, 1.3f); // receiver
		box(b, gx, -0.06f, 1.95f, 0.09f, 0.09f, 0.9f); // barrel
		box(b, gx, -0.1f, 1.5f, 0.18f, 0.2f, 0.55f); // handguard
		box(b, gx, -0.42f, 1.1f, 0.14f, 0.42f, 0.22f); // magazine
		box(b, gx, -0.2f, 0.22f, 0.14f, 0.26f, 0.55f); // stock
		box(b, gx, 0.07f, 0.95f, 0.07f, 0.09f, 0.32f); // sight
		return mb.end();
	}

	private static void leg(ModelBuilder mb, String id, float x, Material cloth, Material team, Material dark) {
		node(mb, id, x, HIP_Y, 0);
		MeshPartBuilder b = mb.part(id + "-cloth", GL20.GL_TRIANGLES, ATTRIBUTES, cloth);
		box(b, 0, -1.02f, 0, 0.6f, 2.04f, 0.64f);
		b = mb.part(id + "-team", GL20.GL_TRIANGLES, ATTRIBUTES, team);
		box(b, 0, -1.12f, 0.35f, 0.5f, 0.44f, 0.12f); // knee pad
		b = mb.part(id + "-dark", GL20.GL_TRIANGLES, ATTRIBUTES, dark);
		box(b, 0, -2.24f, 0.12f, 0.68f, 0.42f, 1f); // boot
	}

	private static Node node(ModelBuilder mb, String id, float x, float y, float z) {
		Node node = mb.node();
		node.id = id;
		node.translation.set(x, y, z);
		return node;
	}

	private static Material material(String id, float r, float g, float b, float shininess) {
		return new Material(id, ColorAttribute.createDiffuse(r, g, b, 1),
				ColorAttribute.createSpecular(0.15f, 0.15f, 0.15f, 1), FloatAttribute.createShininess(shininess));
	}

	private static final Matrix4 transform = new Matrix4();

	private static void box(MeshPartBuilder b, float x, float y, float z, float w, float h, float d) {
		BoxShapeBuilder.build(b, x, y, z, w, h, d);
	}

	private static void sphere(MeshPartBuilder b, float x, float y, float z, float w, float h, float d) {
		b.setVertexTransform(transform.setToTranslation(x, y, z));
		SphereShapeBuilder.build(b, w, h, d, 14, 10);
		b.setVertexTransform(null);
	}

	private static void cylinder(MeshPartBuilder b, float x, float y, float z, float diameter, float height) {
		b.setVertexTransform(transform.setToTranslation(x, y, z));
		CylinderShapeBuilder.build(b, diameter, height, diameter, 10);
		b.setVertexTransform(null);
	}

	/** A box from one joint to the next, {@code thickness} wide. */
	private static void limb(MeshPartBuilder b, Vector3 from, Vector3 to, float thickness) {
		Vector3 direction = new Vector3(to).sub(from);
		float length = direction.len();
		Quaternion rotation = new Quaternion().setFromCross(Vector3.Y, direction.nor());
		b.setVertexTransform(transform.set(new Vector3(from).add(to).scl(0.5f), rotation));
		BoxShapeBuilder.build(b, 0, 0, 0, thickness, length + thickness * 0.5f, thickness);
		b.setVertexTransform(null);
	}

	/** Team match: Red's and Blue's uniforms (the first two colors). */
	public static void paint(ModelInstance instance, com.juego.match.Team team) {
		paint(instance, team == com.juego.match.Team.BLUE ? 1 : 0);
	}

	/** Paints a player's copy of the model in that player's color. */
	public static void paint(ModelInstance instance, int playerId) {
		Material team = instance.getMaterial(TEAM);
		if (team != null)
			((ColorAttribute) team.get(ColorAttribute.Diffuse)).color.set(TEAM_COLORS[Math.floorMod(playerId,
					TEAM_COLORS.length)]);
	}

	/**
	 * Animates one copy of the model: a walk cycle as fast as the character
	 * moves, and the aim (arms, rifle and head) following the view's pitch.
	 */
	public static class Pose {
		/** Distance covered by one step (half a walk cycle), as for the footsteps. */
		private static final float STRIDE = 4.2f;
		private static final float LEG_SWING = 32f, BOB = 0.1f, MAX_AIM_PITCH = 60f;
		/** From the hip to the sole: the model is 6 tall, centered, so the feet are 3 below the center. */
		private static final float LEG_LENGTH = HIP_Y + 3f;
		/** How far each leg turns (one forward, one back) when kneeling. */
		private static final float KNEEL_ANGLE = 60f;

		private final ModelInstance instance;
		private final Node legL, legR, body, head, aim;
		private float phase, amount, crouch;
		private final Quaternion rotation = new Quaternion();

		public Pose(ModelInstance instance) {
			this.instance = instance;
			legL = instance.getNode("legL");
			legR = instance.getNode("legR");
			body = instance.getNode("body");
			head = instance.getNode("head");
			aim = instance.getNode("aim");
		}

		/**
		 * @param speed    horizontal speed, units per second (running is 12)
		 * @param pitch    where they look, degrees (positive is down)
		 * @param crouched crouching: the body goes down by {@link CharacterShape#CROUCH_DROP}, like the
		 *                 hitbox, kneeling (one leg forward, one back)
		 */
		public void update(float speed, float pitch, boolean crouched, float delta) {
			float target = MathUtils.clamp(speed / 12f, 0, 1);
			amount += (target - amount) * Math.min(1, delta * 8); // ease in and out of walking
			crouch += ((crouched ? 1 : 0) - crouch) * Math.min(1, delta * 12);
			phase = (phase + speed * delta / STRIDE * MathUtils.PI) % MathUtils.PI2;
			float swing = MathUtils.sin(phase) * LEG_SWING * amount * (1 - crouch * 0.7f);
			float drop = CharacterShape.CROUCH_DROP * crouch;
			// The legs have no knees: kneeling, they open and get shorter so the feet stay on the floor.
			float kneel = KNEEL_ANGLE * crouch;
			float hipHeight = LEG_LENGTH - drop;
			// (a little shorter still: turned, the corners of the boots reach lower than the leg's end)
			float legScale = Math.min(1, hipHeight * (1 - 0.18f * crouch) / (LEG_LENGTH * MathUtils.cosDeg(kneel)));
			legL.translation.set(HIP_X, HIP_Y - drop, 0);
			legR.translation.set(-HIP_X, HIP_Y - drop, 0);
			legL.rotation.set(rotation.setEulerAngles(0, swing - kneel, 0));
			legR.rotation.set(rotation.setEulerAngles(0, -swing + kneel, 0));
			legL.scale.set(1, legScale, 1);
			legR.scale.set(1, legScale, 1);
			float bob = -Math.abs(MathUtils.cos(phase)) * BOB * amount - drop;
			body.translation.set(0, bob, 0);
			head.translation.set(0, NECK_Y + bob, 0);
			aim.translation.set(0, SHOULDER_Y + bob, 0);
			float aimPitch = MathUtils.clamp(pitch, -MAX_AIM_PITCH, MAX_AIM_PITCH);
			aim.rotation.set(rotation.setEulerAngles(0, aimPitch, 0));
			head.rotation.set(rotation.setEulerAngles(0, aimPitch * 0.6f, 0));
			instance.calculateTransforms();
		}
	}
}
