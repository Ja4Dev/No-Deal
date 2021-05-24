package com.juego.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.juego.systems.BulletSystem;

public class StatusComponent implements Component{
public boolean alive;
private int health = 50;
public StatusComponent(){
alive = true;
}
public void getDamage(int damage) {
	health -= damage;
	if (health <= 0) {
		die();
	}
}
public void getDamageHead(int damage) {
	health -= damage * 3;
		System.out.println("me diste cabeza");
	if (health <= 0) {
		die();
	}
}
private void die() {
	health = 0;
	alive = false;
}

}