package com.juego.bullet;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.physics.bullet.linearmath.btMotionState;

public class MotionState extends btMotionState {
	
	private final Matrix4 transform;
	
	
	public MotionState(final Matrix4 transform) {
		this.transform = transform;
	}
	
	
	@Override
	public void getWorldTransform(final Matrix4 worldTrans) {
		worldTrans.set(transform);
	}
	
	
	@Override
	public void setWorldTransform(final Matrix4 worldTrans) {
		transform.set(worldTrans);
	}
}