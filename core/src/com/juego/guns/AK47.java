package com.juego.guns;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;

public class AK47 extends PrimaryGun{

	private static int damage = 30;
	private static int magazine = 30;
	private static int magazineSize = 30;
	private static boolean auto = true;
	private static float range = 50f;
	private static int delay = 100;
	private static Sound sound = Gdx.audio.newSound(Gdx.files.internal("sounds/AK47-1Shot.mp3"));
	private static boolean sight = false;
	private static String modelName = "Ak47.g3dj";
	private static float recoil = 2;
	
	
	public AK47() {
		super(damage, magazine, magazineSize, auto, range, delay, sound, sight, modelName, recoil);
		super.modelPos[0] = 2;
		super.modelPos[1] = -0.75f;
		super.modelPos[2] = -4;
	}

}
