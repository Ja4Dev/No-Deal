package com.juego.guns;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;

public class Magnum extends SecondaryGun{
	private static int damage = 40;
	private static int magazine = 6;
	private static int magazineSize = 6;
	private static boolean auto = false;
	private static float range = 25f;
	private static int delay = 500;
	private static Sound sound = Gdx.audio.newSound(Gdx.files.internal("sounds/Magnum-1Shot.mp3"));
	private static boolean sight = false;
	private static String modelName = "Magnum.g3dj";
	private static float recoil = 5;
	
	
	public Magnum() {
		super(damage, magazine, magazineSize, auto, range, delay, sound, sight, modelName, recoil);
		super.modelPos[0] = 2.75f;
		super.modelPos[1] = -1.5f;
		super.modelPos[2] = -4;
	}

}
