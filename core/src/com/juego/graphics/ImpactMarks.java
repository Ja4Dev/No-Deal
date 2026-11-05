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

/**
 * Bullet holes left on walls and floors. A fixed pool of decals is reused in
 * a ring (the oldest mark is recycled first) and each mark fades out at the
 * end of its life. The texture is generated at startup, so there is no asset.
 */
public class ImpactMarks implements Disposable {
	private static final int MAX_MARKS = 64;
	private static final long LIFETIME_MS = 20_000;
	private static final long FADE_MS = 4_000;
	private static final float SIZE = 0.6f;
	/** Lifts the mark off the surface so it doesn't flicker (z-fighting). */
	private static final float SURFACE_OFFSET = 0.02f;
	private static final int TEXTURE_SIZE = 32;

	private final Texture texture;
	private final TextureRegion region;
	private final DecalBatch batch;
	private final Decal[] decals = new Decal[MAX_MARKS];
	private final long[] bornMs = new long[MAX_MARKS];
	private int next;
	private final Vector3 tmp = new Vector3();

	public ImpactMarks(Camera camera) {
		texture = createTexture();
		region = new TextureRegion(texture);
		batch = new DecalBatch(new CameraGroupStrategy(camera));
	}

	/** A dark, slightly irregular hole with a soft scorched edge. */
	private static Texture createTexture() {
		Pixmap pixmap = new Pixmap(TEXTURE_SIZE, TEXTURE_SIZE, Pixmap.Format.RGBA8888);
		pixmap.setBlending(Pixmap.Blending.None);
		float half = TEXTURE_SIZE / 2f;
		for (int y = 0; y < TEXTURE_SIZE; y++) {
			for (int x = 0; x < TEXTURE_SIZE; x++) {
				float dx = (x + 0.5f - half) / half, dy = (y + 0.5f - half) / half;
				float angle = MathUtils.atan2(dy, dx);
				// A wobbly radius makes the edge look chipped instead of a perfect circle.
				float r = (float) Math.sqrt(dx * dx + dy * dy) * (1 + 0.12f * MathUtils.sin(angle * 5));
				float alpha, shade;
				if (r < 0.32f) {
					alpha = 0.95f; // the hole
					shade = 0.04f;
				} else if (r < 1f) {
					float t = (r - 0.32f) / 0.68f; // scorch fading outwards
					alpha = 0.75f * (1 - t) * (1 - t);
					shade = 0.12f + 0.1f * t;
				} else {
					alpha = 0;
					shade = 0;
				}
				pixmap.setColor(shade, shade * 0.95f, shade * 0.9f, alpha);
				pixmap.drawPixel(x, y);
			}
		}
		Texture texture = new Texture(pixmap, true);
		texture.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
		pixmap.dispose();
		return texture;
	}

	/** Adds a mark at {@code point} on a surface facing {@code normal}. */
	public void add(Vector3 point, Vector3 normal) {
		Decal decal = decals[next];
		if (decal == null)
			decal = decals[next] = Decal.newDecal(SIZE, SIZE, region, true);
		decal.setPosition(tmp.set(normal).nor().scl(SURFACE_OFFSET).add(point));
		// Any up vector not parallel to the normal works; a random spin hides the repetition.
		Vector3 up = Math.abs(normal.y) > 0.9f ? Vector3.Z : Vector3.Y;
		decal.setRotation(normal, up);
		decal.rotateZ(MathUtils.random(360f));
		decal.setColor(1, 1, 1, 1);
		bornMs[next] = System.currentTimeMillis();
		next = (next + 1) % MAX_MARKS;
	}

	public void render() {
		long now = System.currentTimeMillis();
		boolean any = false;
		for (int i = 0; i < MAX_MARKS; i++) {
			if (decals[i] == null)
				continue;
			long age = now - bornMs[i];
			if (age >= LIFETIME_MS)
				continue;
			float alpha = age > LIFETIME_MS - FADE_MS ? (LIFETIME_MS - age) / (float) FADE_MS : 1;
			decals[i].setColor(1, 1, 1, alpha);
			batch.add(decals[i]);
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
