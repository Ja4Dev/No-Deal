package com.juego.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.ClosestRayResultCallback;
import com.badlogic.gdx.physics.bullet.collision.btBroadphaseProxy;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.juego.components.GroundWeaponComponent;
import com.juego.components.ModelComponent;
import com.juego.components.PlayerComponent;
import com.juego.core.Assets;
import com.juego.core.GameWorld;
import com.juego.guns.GroundWeapons;
import com.juego.guns.Gun;
import com.juego.guns.GunDefinition;
import com.juego.net.GameClient;
import com.juego.utils.Controls;
import com.juego.utils.Controls.Action;

/**
 * Guns on the ground: dropping with the {@link Action#DROP} key (G by
 * default), picking up the gun you look at with the same key (swapping it for
 * the one in that slot), and picking up guns you walk over when that slot is
 * empty.
 * <p>
 * The server owns the {@link GroundWeapons}: this system only mirrors what
 * the server says, and pressing G sends a request.
 */
public class GroundWeaponSystem extends EntitySystem implements EntityListener, GroundWeapons.Listener, Disposable {
	/** A request is not repeated for this long while waiting for the server's answer. */
	private static final long REQUEST_COOLDOWN_MS = 400;

	private final GameWorld gameWorld;
	private final PerspectiveCamera camera;
	private final GroundWeapons weapons;
	private final IntMap<Entity> entities = new IntMap<>();
	private final ComponentMapper<GroundWeaponComponent> gm = ComponentMapper.getFor(GroundWeaponComponent.class);
	private final ComponentMapper<ModelComponent> mm = ComponentMapper.getFor(ModelComponent.class);
	private ImmutableArray<Entity> flying;
	private Engine engine;
	private Entity player;
	private PlayerComponent playerComponent;

	private final boolean[] freeSlots = new boolean[GunDefinition.Slot.values().length];
	private final Vector3 center = new Vector3();
	private int lastRequestWeapon = -1;
	private long lastRequestMs;

	public GroundWeaponSystem(GameWorld gameWorld, PerspectiveCamera camera) {
		this.gameWorld = gameWorld;
		this.camera = camera;
		weapons = new GroundWeapons(null, this);
	}

	@Override
	public void addedToEngine(Engine engine) {
		this.engine = engine;
		flying = engine.getEntitiesFor(Family.all(GroundWeaponComponent.class, ModelComponent.class).get());
		engine.addEntityListener(Family.all(PlayerComponent.class).get(), this);
	}

	@Override
	public void entityAdded(Entity entity) {
		if (!gameWorld.isLocalPlayer(entity))
			return;
		player = entity;
		playerComponent = entity.getComponent(PlayerComponent.class);
	}

	@Override
	public void entityRemoved(Entity entity) {
		if (entity == player)
			player = null;
	}

	private int localId() {
		return gameWorld.session.client.getLocalId();
	}

	@Override
	public void update(float delta) {
		long wallClock = System.currentTimeMillis();
		for (int i = 0; i < flying.size(); i++) {
			Entity e = flying.get(i);
			gm.get(e).update(mm.get(e).instance.transform, wallClock);
		}
		if (player == null)
			return;
		long now = wallClock; // the guns' timestamps are wall clock too (see onServerSpawn)

		String prompt = null;
		if (playerComponent.controlsPlayer() && !playerComponent.isDead() && gameWorld.canFight()) {
			gameWorld.playerSystem().getPosition(center);
			GroundWeapons.Weapon aimed = weapons.findAimed(center, camera.position, camera.direction);
			if (aimed != null)
				prompt = describePickup(aimed);
			if (Controls.isJustPressed(Action.DROP)) {
				if (aimed != null)
					pickUp(aimed, now);
				else
					dropSelected(now);
			} else {
				for (int i = 0; i < freeSlots.length; i++)
					freeSlots[i] = playerComponent.guns[i] == null;
				GroundWeapons.Weapon walkedOver = weapons.findAutoPickup(center, freeSlots, localId(), now);
				if (walkedOver != null)
					pickUp(walkedOver, now);
			}
		}
		if (gameWorld.getHud() != null)
			gameWorld.getHud().setPrompt(prompt);
	}

	private String describePickup(GroundWeapons.Weapon weapon) {
		String key = Controls.get(Action.DROP).displayName();
		Gun current = playerComponent.guns[weapon.def.slot.ordinal()];
		if (current == null)
			return "[" + key + "] Agarrar " + weapon.def.name;
		return "[" + key + "] Cambiar " + current.def.name + " por " + weapon.def.name;
	}

	/** Takes a gun from the ground; the gun in that slot, if any, is thrown in its place. */
	private void pickUp(GroundWeapons.Weapon weapon, long now) {
		int slot = weapon.def.slot.ordinal();
		Gun replaced = playerComponent.guns[slot];
		if (mayRequest(weapon.id))
			gameWorld.network.requestPickup(weapon.id, replaced);
	}

	/** Throws the gun in hand, unless it is the last one. */
	private void dropSelected(long now) {
		Gun gun = playerComponent.selectedGun();
		if (gun == null || playerComponent.gunCount() < 2)
			return;
		if (mayRequest(-2 - playerComponent.selectedSlot))
			gameWorld.network.requestDrop(playerComponent.selectedSlot, gun);
	}

	/** Don't flood the server with the same request every frame while its answer is on the way. */
	private boolean mayRequest(int what) {
		long now = System.currentTimeMillis();
		if (what == lastRequestWeapon && now - lastRequestMs < REQUEST_COOLDOWN_MS)
			return false;
		lastRequestWeapon = what;
		lastRequestMs = now;
		return true;
	}

	// ---- what the server says ----

	public void onServerSpawn(GameClient.Event e) {
		GunDefinition def;
		try {
			def = Assets.armory.get(e.gun);
		} catch (GdxRuntimeException unknownGun) {
			return; // a server with guns this game doesn't have
		}
		weapons.addRemote(e.weaponId, def, e.magazine, e.reserve, e.from, e.position, e.yaw, e.playerId,
				System.currentTimeMillis());
		// Our own throw, confirmed: the gun leaves our hands now.
		boolean thrown = !e.from.epsilonEquals(e.position, 0.01f);
		int slot = def.slot.ordinal();
		if (e.playerId == localId() && thrown && playerComponent != null && !playerComponent.isDead()
				&& playerComponent.guns[slot] != null && playerComponent.guns[slot].def == def)
			playerComponent.removeGun(slot);
	}

	public void onServerRemove(GameClient.Event e) {
		GroundWeapons.Weapon w = weapons.get(e.weaponId);
		weapons.removeRemote(e.weaponId, e.playerId);
		if (w != null && e.playerId == localId() && playerComponent != null && !playerComponent.isDead())
			playerComponent.equip(new Gun(w.def, w.magazine, w.reserve));
	}

	// ---- GroundWeapons.Listener ----

	@Override
	public void spawned(GroundWeapons.Weapon weapon) {
		Entity entity = gameWorld.entityFactory.createGroundWeapon(weapon, System.currentTimeMillis());
		entities.put(weapon.id, entity);
		engine.addEntity(entity);
	}

	@Override
	public void removed(GroundWeapons.Weapon weapon, int pickerId) {
		Entity entity = entities.remove(weapon.id);
		if (entity != null)
			engine.removeEntity(entity);
	}

	public int groundCount() {
		return weapons.size();
	}

	@Override
	public void dispose() {
	}
}
