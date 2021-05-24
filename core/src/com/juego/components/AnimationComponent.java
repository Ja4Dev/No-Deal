package com.juego.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.utils.AnimationController;

public class AnimationComponent implements Component {
	private AnimationController animationController;
	public AnimationComponent(ModelInstance instance) {
		animationController = new
		AnimationController(instance);
		animationController.allowSameAnimation = true;
	}
	public void animate(final String id, final int loops, final int speed) {
		animationController.animate(id, loops, speed, null, 0);
	}
	public void update(float delta) {
		animationController.update(delta);
	}
}