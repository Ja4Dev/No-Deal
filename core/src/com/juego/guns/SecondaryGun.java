package com.juego.guns;

import com.badlogic.gdx.audio.Sound;

public abstract class SecondaryGun extends Gun{

	public SecondaryGun(int damage, int magazine, int magazineSize, boolean auto, float range, int delay, Sound sound, boolean sight, String modelName, float recoil) {
		super(damage, magazine, magazineSize, auto, range, delay, sound, sight, modelName, recoil);
		// TODO Auto-generated constructor stub
	}

}
