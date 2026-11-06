package com.juego.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectMap;
import com.juego.components.ModelComponent;
import com.juego.components.PlayerComponent;
import com.juego.components.RemotePlayerComponent;
import com.juego.core.Assets;
import com.juego.core.GameWorld;
import com.juego.utils.Config;
import com.juego.utils.Footsteps;
import com.juego.utils.SpatialAudio;

/**
 * Footstep sounds: the local player's when running, and everyone else's
 * (people and bots) from where they are, panned and fading with distance,
 * so you hear someone running around the corner. Walking with Shift is
 * silent, as in CS.
 */
public class FootstepSystem extends EntitySystem implements EntityListener, Disposable {
	private static final String[] SOUNDS = { "sounds/footsteps/concrete-0.wav", "sounds/footsteps/concrete-1.wav",
			"sounds/footsteps/concrete-2.wav", "sounds/footsteps/concrete-3.wav", "sounds/footsteps/concrete-4.wav" };
	private static final float OWN_VOLUME = 0.35f, OTHERS_VOLUME = 0.9f, LANDING_BOOST = 1.6f;
	/** Others are heard from closer than gunshots. */
	private static final float OTHERS_RANGE_SCALE = 2.2f;

	private final GameWorld gameWorld;
	private final PerspectiveCamera camera;
	private final Array<Sound> sounds = new Array<>();
	private int lastSound = -1;
	private PlayerComponent player;
	private final Footsteps own = new Footsteps();
	private ImmutableArray<Entity> remotes;
	private final ComponentMapper<ModelComponent> mm = ComponentMapper.getFor(ModelComponent.class);
	private final ObjectMap<Entity, Tracker> trackers = new ObjectMap<>();
	private final Vector3 position = new Vector3(), scaled = new Vector3();

	/** What we know of another player's movement, from where their model is drawn each frame. */
	private static class Tracker {
		final Footsteps steps = new Footsteps();
		final Vector3 last = new Vector3();
		boolean known;
		float speed;
	}

	public FootstepSystem(GameWorld gameWorld, PerspectiveCamera camera) {
		this.gameWorld = gameWorld;
		this.camera = camera;
		for (String path : SOUNDS)
			sounds.add(Assets.acquire(path, Sound.class));
	}

	@Override
	public void addedToEngine(Engine engine) {
		remotes = engine.getEntitiesFor(Family.all(RemotePlayerComponent.class, ModelComponent.class).get());
		engine.addEntityListener(Family.all(PlayerComponent.class).get(), this);
		engine.addEntityListener(Family.all(RemotePlayerComponent.class).get(), this);
	}

	@Override
	public void entityAdded(Entity entity) {
		if (gameWorld.isLocalPlayer(entity))
			player = entity.getComponent(PlayerComponent.class);
	}

	@Override
	public void entityRemoved(Entity entity) {
		trackers.remove(entity);
		if (entity.getComponent(PlayerComponent.class) == player)
			player = null;
	}

	@Override
	public void update(float delta) {
		if (delta <= 0)
			return;
		if (player != null && !player.pause && !player.isDead()) {
			float speed = (float) Math.sqrt(player.velocity.x * player.velocity.x + player.velocity.z * player.velocity.z);
			Footsteps.Step step = own.update(speed, player.onGround, delta);
			if (step != Footsteps.Step.NONE)
				play(OWN_VOLUME * (step == Footsteps.Step.LANDING ? LANDING_BOOST : 1), 0);
		}
		for (int i = 0; i < remotes.size(); i++)
			updateRemote(remotes.get(i), delta);
	}

	private void updateRemote(Entity entity, float delta) {
		ModelComponent model = mm.get(entity);
		Tracker tracker = trackers.get(entity);
		if (tracker == null)
			trackers.put(entity, tracker = new Tracker());
		if (!model.visible) {
			tracker.known = false;
			return;
		}
		model.instance.transform.getTranslation(position);
		if (!tracker.known || tracker.last.dst2(position) > 100) { // first sight, or a respawn
			tracker.known = true;
			tracker.last.set(position);
			return;
		}
		float dx = position.x - tracker.last.x, dz = position.z - tracker.last.z;
		float dy = Math.abs(position.y - tracker.last.y);
		// Snapshots arrive 20 times a second: smooth the speed so it doesn't flicker.
		float speed = (float) Math.sqrt(dx * dx + dz * dz) / delta;
		tracker.speed += (speed - tracker.speed) * Math.min(1, delta * 10);
		// Remote players have no "on the ground" flag: moving up or down fast means jumping or falling.
		boolean onGround = dy / delta < 4f;
		tracker.last.set(position);
		Footsteps.Step step = tracker.steps.update(tracker.speed, onGround, delta);
		if (step == Footsteps.Step.NONE)
			return;
		// Footsteps carry less far than gunshots: as if the source were farther away.
		scaled.set(position).sub(camera.position).scl(OTHERS_RANGE_SCALE).add(camera.position);
		float volume = OTHERS_VOLUME * SpatialAudio.volume(camera.position, scaled)
				* (step == Footsteps.Step.LANDING ? LANDING_BOOST : 1);
		if (volume > 0.01f)
			play(volume, SpatialAudio.pan(camera.position, camera.direction, camera.up, position));
	}

	/** A random footstep, never the same one twice in a row, with a slightly different pitch each time. */
	private void play(float volume, float pan) {
		int index = MathUtils.random(sounds.size - 2);
		if (index >= lastSound)
			index++;
		lastSound = index;
		sounds.get(index).play(Math.min(1, volume) * Config.fxVolume / 100, MathUtils.random(0.92f, 1.08f), pan);
	}

	@Override
	public void dispose() {
		for (String path : SOUNDS)
			Assets.release(path);
		sounds.clear();
	}
}
