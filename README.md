# No-Deal
A 3D First Person Shooter game template built with LibGDX, Ashley (ECS) and Bullet physics.

> Originally written in 2020 with LibGDX 1.9.10 and Java 8. It has since been updated to **LibGDX 1.13.1, Java 17, Gradle 8 and the LWJGL3 desktop backend**.

## Building and running
Requirements: **JDK 17** or newer (a JRE is not enough).

```bash
./gradlew desktop:run    # run the game (use gradlew.bat on Windows cmd)
./gradlew desktop:dist   # build a runnable jar in desktop/build/libs/
./gradlew test           # run the unit tests (no window needed)
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
| `RenderSystem` | Draws the world, then the gun view model with its own camera |
| `BulletSystem` | Steps the Bullet physics world at a fixed 60 Hz |
| `PlayerSystem` | Mouse look, WASD movement, jumping, respawning |
| `WeaponSystem` | Hitscan shooting, headshots, reloading, switching, zoom |
| `EnemySystem` | Spawns enemies and makes them chase the player |
| `StatusSystem` | Removes dead entities and frees their physics objects |
| `HudSystem` | HUD, pause menu and scoreboard toggle |

The three player systems (`PlayerSystem`, `WeaponSystem`, `HudSystem`) don't call each other: they share state through `PlayerComponent` (movement flags, pending recoil, pause). Guns are data: each one is defined in [`core/assets/data/guns.json`](core/assets/data/guns.json) (damage, magazine, reserve, fire rate, recoil, model, sound...), so adding a gun needs no code. All assets go through a reference-counted `AssetManager` (`Assets.acquire` / `Assets.release`).

## What I did
The most important things I've made for the project are:
  - A basic player movement and mouse looking mechanism.
  - The essentials physics for walking, running and jumping.
  - A raycasting shooting mechanism including the desviations for jumping or moving when shooting the gun.
  - A versatile and simple 3D Models importing for all the project entities, including the textures and animations.
  - A main menu music player with all its needed user controls to improve the user experience.
  - A bot spawning and player chasing system.
  - An entity system to create character, guns, maps, props and any other interactive object in the game.
  - A basic set of menues, including settings, credits and pause.
  - A fully functional HUD.

## How I did it?
<p>I based my project on the "Building a 3D Game with LibGDX" book by Sebastian Di Giussepe, but I needed to make some changes to make that works in this LibGDX version, like use the Y axis to the gravity instead of the Z axis, and some other changes to make fix the errors and complete the book tutorial, like making my own HUD system instead of the described on the book.</p>

## What I couldn't do
Between the things that I couldn't add to the projects are:
  - The networking.
  - The directional sound.
  - The player's model.
  - The timer.
  - The scoreboard.
  - The skyboxes.
  - The raycasting shoots decals.
  - The settings options saving.
  - The team selection menues.
  - The gun dropping and picking system.

## Want to help me?
You can give the project a star, or simply share this to anyone you know is working on a Java 3D Game Project.

<br>
Thank you all for reading this and showing your interest in my project! Hope you liked it or found it useful. 

