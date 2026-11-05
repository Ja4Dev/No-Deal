package com.juego.graphics;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.Pool;

/**
 * Bullet tracers, barely there on purpose: a short, faint 1 px streak that
 * flies from the gun to where the bullet stopped, fading from its tail to its
 * head. It is drawn with depth testing, so walls hide it.
 */
public class Tracers implements Disposable {
	/** World units per second (a character is 6 units tall). Fast, but slow enough to catch a glimpse. */
	static final float SPEED = 260f;
	/** Length of the streak. */
	static final float LENGTH = 3.5f;
	private static final Color HEAD = new Color(1f, 0.93f, 0.72f, 0.45f);
	private static final Color TAIL = new Color(1f, 0.93f, 0.72f, 0f);
	private static final int MAX_TRACERS = 64;

	private static final class Tracer {
		final Vector3 start = new Vector3(), direction = new Vector3();
		float length, travelled;
	}

	private final PerspectiveCamera camera;
	private final ShapeRenderer shapes = new ShapeRenderer();
	private final Array<Tracer> active = new Array<>(false, MAX_TRACERS);
	private final Pool<Tracer> pool = new Pool<Tracer>() {
		@Override
		protected Tracer newObject() {
			return new Tracer();
		}
	};
	private final Vector3 head = new Vector3(), tail = new Vector3();

	public Tracers(PerspectiveCamera camera) {
		this.camera = camera;
	}

	/** A bullet flying from {@code from} to {@code to}. */
	public void add(Vector3 from, Vector3 to) {
		float length = from.dst(to);
		if (length < LENGTH)
			return; // point blank: nothing to see
		if (active.size >= MAX_TRACERS)
			pool.free(active.removeIndex(0));
		Tracer t = pool.obtain();
		t.start.set(from);
		t.direction.set(to).sub(from).scl(1 / length);
		t.length = length;
		t.travelled = 0;
		active.add(t);
	}

	public int size() {
		return active.size;
	}

	/** Moves the tracers and draws them. Call after the world, before the first-person gun. */
	public void render(float delta) {
		for (int i = active.size - 1; i >= 0; i--) {
			Tracer t = active.get(i);
			t.travelled += SPEED * delta;
			if (t.travelled - LENGTH >= t.length) {
				pool.free(active.removeIndex(i));
			}
		}
		if (active.size == 0)
			return;
		Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
		Gdx.gl.glDepthMask(false);
		Gdx.gl.glEnable(GL20.GL_BLEND);
		Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE); // additive: a glint, never a dark line
		shapes.setProjectionMatrix(camera.combined);
		shapes.begin(ShapeRenderer.ShapeType.Line);
		for (Tracer t : active) {
			float headAt = Math.min(t.travelled, t.length);
			float tailAt = Math.max(0, t.travelled - LENGTH);
			head.set(t.direction).scl(headAt).add(t.start);
			tail.set(t.direction).scl(tailAt).add(t.start);
			shapes.line(tail.x, tail.y, tail.z, head.x, head.y, head.z, TAIL, HEAD);
		}
		shapes.end();
		Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
		Gdx.gl.glDisable(GL20.GL_BLEND);
		Gdx.gl.glDepthMask(true);
	}

	@Override
	public void dispose() {
		shapes.dispose();
	}
}
