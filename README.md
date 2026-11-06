# No-Deal
A 3D First Person Shooter game template built with LibGDX, Ashley (ECS) and Bullet physics.

> Originally written in 2020 with LibGDX 1.9.10 and Java 8. It has since been updated to **LibGDX 1.13.1, Java 17, Gradle 8 and the LWJGL3 desktop backend**.

## Building and running
Requirements: **JDK 17** or newer (a JRE is not enough).

```bash
./gradlew desktop:run    # run the game (use gradlew.bat on Windows cmd)
./gradlew desktop:dist   # build a runnable jar in desktop/build/libs/
./gradlew test           # run the unit and network tests (no window needed)
./gradlew server:run     # run a dedicated server (see Multiplayer)
./gradlew server:dist    # build the dedicated server jar in server/build/libs/
```

Run from the project root: the `run` task sets `core/assets` as the working directory so assets resolve correctly.

## Description
<p>No Deal was an attempt to develop a simple first person shooter game I carried out as a final academic project for my technical high scool, in the year 2020.
Unfortunately, the project couldn't end as I wanted, but I think I got a really good basic template to everyone who want to create a Java 3D First Person game.</p>

**IMPORTANT ADVICE:** The menues of the game and some commentary lines in the code are writed on Spanish, but fortunately all the code is fully writen in English.


## Code structure
The game uses an Entity Component System ([Ashley](https://github.com/libgdx/ashley)): components only hold data, and each system owns one responsibility.

| System | Responsibility |
| --- | --- |
| `RenderSystem` | Draws the world, then the gun view model with its own camera. Runs after every other system, so the frame shows this frame's movement and aim |
| `BulletSystem` | Steps the Bullet physics world at a fixed 60 Hz; the camera is interpolated between steps, so it moves smoothly on faster monitors |
| `NetworkSystem` | Syncs the match with the server, moves the other players |
| `PlayerSystem` | Mouse look, CS-style movement (acceleration, friction, air control), jumping, respawning |
| `WeaponSystem` | Hitscan shooting with spray recoil, headshots, reloading, switching, zoom, bullet tracers, view model bob and kick |
| `GroundWeaponSystem` | Guns on the ground: dropping and picking up (the server decides) |
| `FootstepSystem` | Footsteps: yours when running, and everyone else's, panned and fading with distance |
| `DeathCamSystem` | While dead, a spectator camera that follows whoever killed you |
| `HudSystem` | HUD, configurable crosshair, pause menu, console, scoreboard toggle, damage feedback, death screen |

Every match, singleplayer included, is run by a `GameServer`: *Un jugador* starts a private one inside the game with bots, so bots, guns, kills and respawns work the same way alone and online.

The three player systems (`PlayerSystem`, `WeaponSystem`, `HudSystem`) don't call each other: they share state through `PlayerComponent` (movement, how recoil pushes the view, pause). Getting hit flashes the screen edges red, an arc around the crosshair points to where the shot came from, and the edges stay faintly red while health is low. Every setting (sound, mouse, video, controls, crosshair) can be changed from the main menu or mid-match from the pause menu: the same pages are reused in both places through `SettingsFlow`. Settings and key bindings are saved with libGDX `Preferences`, and any action can be bound to a key or a mouse button (`Controls`). Guns are data: each one is defined in [`core/assets/data/guns.json`](core/assets/data/guns.json) (damage, magazine, reserve, fire rate, spray pattern, inaccuracy, model, sound...), so adding a gun needs no code. All assets go through a reference-counted `AssetManager` (`Assets.acquire` / `Assets.release`).

### How it feels
- **Recoil like CS 1.6.** Each gun has a fixed spray pattern (`spray` in `guns.json`): the first bullet goes where you aim, and holding the trigger the next ones climb and then wander left and right, the same way every time, so you can learn to pull the mouse against it. The view climbs with the pattern and comes back down when you let go, and each shot jolts it a little; bullets follow both, so the crosshair always shows where the next bullet goes (and the tracer ends there). On top of that there is a random inaccuracy: tiny when standing still or walking, large when running or jumping. The rules live in `Recoil`, which is pure logic and unit-tested.
- **Movement.** Quake/CS-style acceleration and friction (`PlayerMovement`): full speed in about 0.2 s, a full stop in about 0.25 s, momentum kept in the air with a little air control. The eyes are near the top of the head, and the mouse uses CS's scale (0.022 degrees per count), so a CS sensitivity feels the same here.
- **Crosshair.** Style (classic, with a dot, dot only, T), length, thickness, gap, color, opacity and an outline, with a live preview in Settings > Mira. It can be dynamic: it opens up with the gun's real inaccuracy while moving and spraying.
- **The soldiers.** Players and bots are low-poly soldiers built in code (`SoldierModel`, no model file): boots, knee pads, a tactical vest with pouches, shoulder pads, a helmet with goggles, and a rifle at the shoulder. Each player's vest and pads have their own color. The legs walk as fast as the player moves, and the arms, rifle and head follow where they aim. Bullets hit a capsule that matches the model (`CharacterShape.HITBOX_RADIUS`), slimmer than the one used for walking, so a shot that looks like a miss is a miss.
- **Changing your name mid-match.** From the pause menu (*Cambiar nombre*) or the console (`nombre <nuevo>`). Everyone sees it in the kill feed, and the scoreboard shows the match's name changes in its top-right corner.
- **Damage and distance.** A bullet always reaches what it is aimed at, across the whole level, but it hurts less the farther it flies, as in CS: each gun keeps a fraction of its damage every 50 units (`rangeModifier` in `guns.json`: 85% for the M4, 90% for the AK-47, 75% for the magnum). An M4 body shot does 34 point blank and about 23 across the Bank. Headshots do 4 times the damage, so one is lethal up to about 85 units.
- **Hits you can see and hear.** When a bullet hits someone, everyone near enough sees blood (a puff and drops out of the wound, a spray out the other side, and a stain on the wall behind) and hears a thud from the body; far away it is silent. The server announces each hit (`HIT` message) with where it landed and the bullet's direction.
- **Dying.** The camera leaves your body and follows whoever killed you, from behind (`DeathCamSystem`), while a panel shows their name, the gun, whether it was a headshot, and their kills, deaths and assists, with a "Reviviendo en 3s" countdown. Assists go to anyone who did 40 or more damage to that life without finishing it, like in CS; they are also on the scoreboard and the server window.
- **Footsteps.** Running makes noise, one step every stride; walking with Shift and being in the air are silent, and landing from a jump is louder (`Footsteps`, unit-tested). You hear everyone else's steps too, from where they are, so someone running around the corner gives themselves away.

## Bots
Computer-controlled players that look for you and shoot, with four difficulties: **fácil, medio, difícil and muy difícil**.

- **Singleplayer** (*Un jugador*): choose how many bots (1 to 16) and their difficulty. It's a deathmatch against them; the pause menu freezes the whole match, bots included.
- **Hosting** (*Crear partida*) and the **dedicated server** can add bots too (0 to 16, besides the people).
- **During the match**, the console changes them: press the key left of the 1 (rebindable) if you are running the match, or type in the dedicated server's window or terminal:

  | Command | What it does |
  | --- | --- |
  | `bot agregar [cantidad] [dificultad]` | adds bots, e.g. `bot agregar 3 dificil` |
  | `bot quitar [nombre \| cantidad \| todos]` | takes bots out |
  | `bot dificultad <dificultad>` | changes the difficulty of every bot |
  | `bot lista` | lists them |

  The CS names work too: `bot_add`, `bot_kick`, `bot_difficulty`.

How they work:
- **Same rules as people.** A bot (`Bot`) walks with the same physics body (`CharacterBody`) and movement code as the player, and shoots with the same gun data: fire rate, magazine and reload, spray pattern and inaccuracy. If it runs while shooting, it misses like a person would. Its shots go through the same server-side hit detection.
- **Human limits, by difficulty** (`BotDifficulty`): reaction time (0.75 s down to 0.17 s), how fast and how precisely it turns its aim, how well it pulls down the spray, how often it goes for the head, its field of view, and how it moves in a fight. Easy bots run and gun, medium ones stop to shoot, and the hard ones strafe while they shoot. They only shoot what they can see, notice whoever shoots them even from behind, and walk towards gunfire they hear.
- **Navigation without waypoints.** When the first bot joins, `NavGrid` samples the level's collision mesh on a 2-unit grid: every floor with room for a standing character becomes a node, linked to its neighbours when a character can walk between them. Building it takes about 0.4 s for the Bank. Bots find their way with A* and then cut corners where the straight line is clear. If they get stuck, they find another way.
- **Tested.** `BotTest` runs bots on the real level: paths between every pair of spawns, a bot crossing the whole map to find and shoot a target, and the four difficulties against the same target. At about 40 units easy bots hit 54% of their shots and very hard ones 93%, mostly to the head, and reaction times drop from 0.76 s to 0.18 s. `BotServerTest` adds bots by command to a real server and checks that a client sees them move, hunt it down and kill it.

## Multiplayer
Deathmatch for up to 4 players (plus bots) over LAN, with a hand-written UDP protocol (no networking library).

- **Host a match**: *Crear partida* in the main menu. The server runs inside your game and the other players join with your IP, which is shown in the kill feed when the match starts.
- **Join a match**: *Unirse a partida*, enter the host's IP and port (default UDP `25559`).
- **Dedicated server**: the same server without the game or a GPU, for a machine that only hosts. Double-click `NoDeal-server-1.0.jar` to open the server window: pick the port and match rules, start and stop the server, copy the address to join, look up your public IP, and watch the players and a colored log of joins, kills and matches. It also has a command line (`ayuda`, `estado`, `ip`, `limpiar`, `salir`...). Settings are remembered between runs.

  Started with options, it starts the server right away. With `--nogui`, or on a machine without a display, it runs in the terminal instead:
  ```bash
  java -jar NoDeal-server-1.0.jar --port 25559 --max-players 4 --time-limit 10 --frag-limit 20 --bots 4 --bot-difficulty dificil [--nogui]
  ```
  Terminal commands: `status` (match clock and players), `quit` (notify everyone and stop), and the [bot commands](#bots).
- **Guns on the ground**: press **G** (rebindable) to drop the gun in hand, or to pick up the gun you are looking at, which swaps it for the one in that slot. Walking over a gun picks it up if that slot is empty. Dead players drop the gun they were holding, and the level has AK-47 spawners that refill 30 seconds after someone takes the gun. Dropped guns keep their ammo and disappear after a minute.
- **Match rules**: a match lasts 10 minutes or until someone reaches 20 kills. Then the scoreboard shows the winner for 10 seconds, scores reset and everyone respawns. In singleplayer the clock stops while the game is paused.

### How it works
```
   client (game)                                server (in-game host or dedicated)
 ┌───────────────┐   PLAYER_STATE 30 Hz (unreliable)   ┌──────────────────────────┐
 │ NetworkSystem │ ──────────────────────────────────► │ GameServer               │
 │   GameClient  │   FIRE (reliable)                   │  - MovementValidator     │
 │               │ ──────────────────────────────────► │  - hitscan vs. level +   │
 │               │   SNAPSHOT 20 Hz (unreliable)       │    player hitboxes       │
 │               │ ◄────────────────────────────────── │  - health, kills, deaths │
 │               │   JOIN/LEAVE/KILL/RESPAWN (reliable)│  - respawns              │
 └───────────────┘ ◄────────────────────────────────── └──────────────────────────┘
```
- **Who decides what.** Each client simulates its own movement, so it feels instant, and the server checks every reported position against the maximum speed. The client's clock may not run ahead of the server's, so a client can't claim extra time to move faster. Everything that decides the match is server-authoritative: every shot is re-traced on the server against the level and the other players' hitboxes, and only the server changes health, kills, deaths and respawns.
- **Two channels over one UDP socket** (`Connection`). Frequent state travels *unreliably*, because only the newest copy matters. Events travel on a *reliable, ordered* channel: each message has an id and is re-sent until the peer acknowledges it with a cumulative ack carried on every datagram. The receiver buffers out-of-order messages and delivers each one exactly once, in order. `ConnectionTest` checks this over a simulated link that loses 30% of the packets, duplicates 10% and shuffles the rest.
- **Threads.** Each socket has a receiver thread that only queues raw datagrams. Everything else runs on the game/server thread, because Bullet's JNI wrappers are not thread-safe.
- **Guns on the ground** follow the same rule: clients only *ask* to drop or pick up a gun, and the server decides, then tells everyone where it landed or who took it. The same `GroundWeapons` class runs on the server and as the client's mirror of the server. The server also knows each player's guns, so a client can't fire a gun it doesn't have.
- **Smooth remote players.** Other players are drawn 100 ms in the past, blending between the two snapshots around that moment.
- **Headless-friendly collision.** `MapCollision` builds the level's triangle mesh straight from the `.g3dj` vertex data. It never creates a GL `Model`, so the dedicated server runs without OpenGL. Spawn points for each level live in `core/assets/data/maps/<level>.json`.

Known limitations: there is no lag compensation (you aim at where other players were about 100 ms ago, which is fine on LAN), the server does not check movement against walls, and players can walk through each other (bots bump into other bots, not into people).

## What I did
The most important things I've made for the project are:
  - A basic player movement and mouse looking mechanism.
  - The essentials physics for walking, running and jumping.
  - A raycasting shooting mechanism including the desviations for jumping or moving when shooting the gun.
  - A versatile and simple 3D Models importing for all the project entities, including the textures and animations.
  - A main menu music player with all its needed user controls to improve the user experience.
  - A bot spawning and player chasing system. *(Rewritten in 2026: bots that navigate the level, aim and shoot, with four difficulties; see [Bots](#bots).)*
  - An entity system to create character, guns, maps, props and any other interactive object in the game.
  - A basic set of menues, including settings, credits and pause.
  - A fully functional HUD.

## How I did it?
<p>I based my project on the "Building a 3D Game with LibGDX" book by Sebastian Di Giussepe, but I needed to make some changes to make that works in this LibGDX version, like use the Y axis to the gravity instead of the Z axis, and some other changes to make fix the errors and complete the book tutorial, like making my own HUD system instead of the described on the book.</p>

## What I couldn't do
Between the things that I couldn't add to the projects are:
  - The networking. *(Added in 2026: see [Multiplayer](#multiplayer).)*
  - The directional sound. *(Added in 2026: other players' shots are panned and attenuated by distance.)*
  - The player's model. *(Added in 2026: animated low-poly soldiers, see [How it feels](#how-it-feels).)*
  - The timer. *(Added in 2026: timed matches, see Multiplayer.)*
  - The scoreboard. *(Added in 2026: hold Tab.)*
  - The skyboxes. *(Added in 2026: a generated sky dome, visible through the openings in the roof.)*
  - The raycasting shoots decals. *(Added in 2026: bullet holes that fade out, and faint tracers.)*
  - The settings options saving. *(Added in 2026, along with rebindable controls, mouse sensitivity and a configurable crosshair.)*
  - The team selection menues.
  - The gun dropping and picking system. *(Added in 2026: press G, see Multiplayer.)*

## Credits
- Footstep and hit sounds: [Impact Sounds](https://kenney.nl/assets/impact-sounds) by Kenney (CC0), converted to mono WAV so they can be panned.

## Want to help me?
You can give the project a star, or simply share this to anyone you know is working on a Java 3D Game Project.

<br>
Thank you all for reading this and showing your interest in my project! Hope you liked it or found it useful. 

