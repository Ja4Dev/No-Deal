package com.juego.graphics;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.DepthTestAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.utils.Disposable;

/**
 * A sky seen through the level's openings: a large sphere around the camera,
 * viewed from inside, textured with a generated vertical gradient. It is drawn
 * first and never writes depth, so the level always covers it.
 */
public class SkyDome implements Disposable {
	private static final float RADIUS = 2000f;
	private static final int GRADIENT_HEIGHT = 256;

	private final Texture texture;
	private final Model model;
	private final ModelInstance instance;

	public SkyDome() {
		texture = createGradient();
		Material material = new Material(TextureAttribute.createDiffuse(texture),
				IntAttribute.createCullFace(GL20.GL_FRONT), // we are inside the sphere
				new DepthTestAttribute(GL20.GL_LEQUAL, false));
		model = new ModelBuilder().createSphere(RADIUS * 2, RADIUS * 2, RADIUS * 2, 32, 16, material,
				VertexAttributes.Usage.Position | VertexAttributes.Usage.TextureCoordinates);
		instance = new ModelInstance(model);
	}

	/** Deep blue at the zenith, pale near the horizon, hazy grey below it. */
	private static Texture createGradient() {
		Pixmap pixmap = new Pixmap(1, GRADIENT_HEIGHT, Pixmap.Format.RGBA8888);
		for (int y = 0; y < GRADIENT_HEIGHT; y++) {
			float v = y / (float) (GRADIENT_HEIGHT - 1); // 0 = top of the sphere, 1 = bottom
			float r, g, b;
			if (v < 0.5f) {
				float t = (float) Math.pow(v / 0.5f, 1.6); // zenith -> horizon
				r = lerp(0.16f, 0.72f, t);
				g = lerp(0.34f, 0.84f, t);
				b = lerp(0.72f, 0.95f, t);
			} else {
				float t = Math.min(1, (v - 0.5f) / 0.15f); // horizon -> ground haze
				r = lerp(0.72f, 0.55f, t);
				g = lerp(0.84f, 0.58f, t);
				b = lerp(0.95f, 0.62f, t);
			}
			pixmap.setColor(r, g, b, 1);
			pixmap.drawPixel(0, y);
		}
		Texture texture = new Texture(pixmap);
		texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
		pixmap.dispose();
		return texture;
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	/** Draws the sky centered on the camera. Call inside an active ModelBatch, before the level. */
	public void render(ModelBatch batch, Camera camera) {
		instance.transform.setToTranslation(camera.position);
		batch.render(instance); // no environment: the sky is unlit
	}

	@Override
	public void dispose() {
		model.dispose();
		texture.dispose();
	}
}
