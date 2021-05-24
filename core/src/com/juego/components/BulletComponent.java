package com.juego.components;

import com.badlogic.gdx.physics.bullet.collision.btCollisionObject;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.juego.bullet.MotionState;
import com.badlogic.ashley.core.Component;

public class BulletComponent implements Component{
	
	public MotionState motionState;
	public btRigidBody.btRigidBodyConstructionInfo bodyInfo;
	public btCollisionObject body;
	
	
}

