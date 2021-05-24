package com.juego.guns;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;

public class M4A1 extends PrimaryGun{

	private static int damage = 34;
	private static int magazine = 30;
	private static int magazineSize = 30;
	private static boolean auto = true;
	private static float range = 50f;
	private static int delay = 150;
	private static Sound sound = Gdx.audio.newSound(Gdx.files.internal("sounds/M4A1-1Shot.mp3"));
	private static boolean sight = true;
	private static String modelName = "M4A1.g3dj";
	private static float recoil = 1;
	
	
	public M4A1() {
		super(damage, magazine, magazineSize, auto, range, delay, sound, sight, modelName, recoil);
		super.modelPos[0] = 2.5f;
		super.modelPos[1] = -2f;
		super.modelPos[2] = -2.5f;
	}

}
