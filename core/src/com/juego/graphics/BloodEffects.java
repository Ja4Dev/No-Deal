package com.juego.graphics;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g3d.decals.CameraGroupStrategy;
import com.badlogic.gdx.graphics.g3d.decals.Decal;
import com.badlogic.gdx.graphics.g3d.decals.DecalBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.juego.utils.CharacterShape;

/**
 * Blood when a bullet hits someone, so the shooter can tell they hit: a puff
 * of red mist and drops where the bullet went in, and a bigger spray out the
 * other side; the drops fall with gravity. Headshots spray more. Camera-facing quads
 * from a fixed pool (the oldest is reused first); the texture is generated, so
 * there is no asset.
 */
public class BloodEffects implements Disposable {
	private static final int MAX_PARTICLES = 256;
	private static final float GRAVITY = 26f;
	private static final int TEXTURE_SIZE = 32;

	private final Texture texture;
	private final DecalBatch batch;
	private final Camera camera;
	private final Decal[] decals = new Decal[MAX_PARTICLES];
	private final Vector3[] position = new Vector3[MAX_PARTICLES], velocity = new Vector3[MAX_PARTICLES];
	private final float[] age = new float[MAX_PARTICLES], life = new float[MAX_PARTICLES];
	private final float[] startSize = new float[MAX_PARTICLES], endSize = new float[MAX_PARTICLES];
	private final float[] red = new float[MAX_PARTICLES], alpha = new float[MAX_PARTICLES];
	private final boolean[] mist = new boolean[MAX_PARTICLES];
	private int next;
	private final Vector3 tmp = new Vector3(), tmp2 = new Vector3(), entry = new Vector3(), exit = new Vector3();
	/** About how far a bullet travels through a body (the capsule is 4 wide). */
	private static final float BODY_THICKNESS = 2 * CharacterShape.RADIUS;

	public BloodEffects(Camera camera) {
		this.camera = camera;
		texture = createTexture();
		TextureRegion region = new TextureRegion(texture);
		batch = new DecalBatch(new CameraGroupStrategy(camera));
		for (int i = 0; i < MAX_PARTICLES; i++) {
			decals[i] = Decal.newDecal(1, 1, region, true);
			position[i] = new Vector3();
			velocity[i] = new Vector3();
			age[i] = life[i] = 1; // dead
		}
	}

	/** A soft white blob, tinted red per particle. */
	private static Texture createTexture() {
		Pixmap pixmap = new Pixmap(TEXTURE_SIZE, TEXTURE_SIZE, Pixmap.Format.RGBA8888);
		pixmap.setBlending(Pixmap.Blending.None);
		float half = TEXTURE_SIZE / 2f;
		for (int y = 0; y < TEXTURE_SIZE; y++) {
			for (int x = 0; x < TEXTURE_SIZE; x++) {
				float dx = (x + 0.5f - half) / half, dy = (y + 0.5f - half) / half;
				float r = (float) Math.sqrt(dx * dx + dy * dy);
				float a = MathUtils.clamp(1 - r, 0, 1);
				pixmap.setColor(1, 1, 1, a * a * (3 - 2 * a)); // smoothstep edge
				pixmap.drawPixel(x, y);
			}
		}
		Texture texture = new Texture(pixmap);
		texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
		pixmap.dispose();
		return texture;
	}

	/**
	 * A hit at {@code at} (on the body's surface) by a bullet travelling along
	 * {@code direction} (normalized): a splash out of the wound, back towards
	 * the shooter so they see it, and a spray out the other side of the body.
	 */
	public void spray(Vector3 at, Vector3 direction, boolean headshot) {
		float scale = headshot ? 1.4f : 1f;
		// Entry: a puff and drops thrown back and sideways, in front of the body.
		entry.set(direction).scl(-0.4f).add(at);
		mist(entry, tmp2.set(direction).scl(-1.5f), 0.9f, 2.8f * scale);
		for (int i = 0; i < (headshot ? 16 : 10); i++)
			droplet(entry, tmp2.set(direction).scl(-MathUtils.random(1f, 3f))
					.add(randomUnit().scl(MathUtils.random(2f, 4.5f))).add(0, MathUtils.random(1f, 3f), 0));
		// Exit: through the body, a bigger spray the way the bullet was going.
		exit.set(direction).scl(BODY_THICKNESS).add(at);
		mist(exit, tmp2.set(direction).scl(2.5f), 1.2f, 3.8f * scale);
		for (int i = 0; i < (headshot ? 20 : 12); i++)
			droplet(exit, tmp2.set(direction).scl(MathUtils.random(3f, 10f))
					.add(randomUnit().scl(MathUtils.random(1f, 4f))).add(0, MathUtils.random(1f, 3f), 0));
	}

	private void mist(Vector3 at, Vector3 velocity, float from, float to) {
		int p = spawn(at, 0.4f + MathUtils.random(0.15f));
		this.velocity[p].set(velocity).add(randomUnit().scl(0.5f)).add(0, 0.4f, 0);
		startSize[p] = from;
		endSize[p] = to;
		red[p] = 0.5f;
		alpha[p] = 0.9f;
		mist[p] = true;
	}

	private void droplet(Vector3 at, Vector3 velocity) {
		int p = spawn(at, MathUtils.random(0.35f, 0.7f));
		this.velocity[p].set(velocity);
		startSize[p] = MathUtils.random(0.25f, 0.5f);
		endSize[p] = startSize[p] * 0.6f;
		red[p] = MathUtils.random(0.5f, 0.75f);
		alpha[p] = 0.95f;
		mist[p] = false;
	}

	private int spawn(Vector3 at, float lifetime) {
		int p = next;
		next = (next + 1) % MAX_PARTICLES;
		position[p].set(at);
		age[p] = 0;
		life[p] = lifetime;
		return p;
	}

	private Vector3 randomUnit() {
		return tmp.setToRandomDirection();
	}

	public void render(float delta) {
		boolean any = false;
		for (int i = 0; i < MAX_PARTICLES; i++) {
			if (age[i] >= life[i])
				continue;
			age[i] += delta;
			if (age[i] >= life[i])
				continue;
			float t = age[i] / life[i];
			if (mist[i]) {
				velocity[i].scl((float) Math.exp(-4 * delta)); // the mist slows down and hangs in the air
			} else {
				velocity[i].y -= GRAVITY * delta;
			}
			position[i].mulAdd(velocity[i], delta);
			float size = MathUtils.lerp(startSize[i], endSize[i], t);
			Decal d = decals[i];
			d.setPosition(position[i]);
			d.setDimensions(size, size);
			d.lookAt(camera.position, camera.up);
			d.setColor(red[i], 0, 0, alpha[i] * (1 - t * t));
			batch.add(d);
			any = true;
		}
		if (any)
			batch.flush();
	}

	@Override
	public void dispose() {
		batch.dispose();
		texture.dispose();
	}
}
