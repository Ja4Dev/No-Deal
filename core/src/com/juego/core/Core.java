package com.juego.core;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.juego.screens.GameScreen;
import com.juego.screens.menues.MainMenuScreen;
import com.juego.utils.MusicPlayer;

public class Core extends ApplicationAdapter {
	public static int VIRTUAL_WIDTH = 1280;
	public static int VIRTUAL_HEIGHT = 720;
	Screen screen;
	boolean introShowed = false;
	public MusicPlayer musicPlayer;
	
	@Override
	public void create() {
		new Assets();
		Gdx.input.setCatchBackKey(true);
		musicPlayer = new MusicPlayer();
		setScreen(new MainMenuScreen(this));
	}
	
	
	@Override
	public void render() {
		Gdx.gl.glClearColor(0, 0, 0, 1);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
		if(!introShowed) {
			System.out.println("TO DO: reproducir video de la empresa.");
			introShowed = true;
		}
		screen.render(Gdx.graphics.getDeltaTime());
//		System.out.println(Gdx.graphics.getFramesPerSecond()); 
		musicPlayer.update();
	}
	
	
	@Override
	public void resize(int width, int height) {
		screen.resize(width, height);
	}
	
	
	public void setScreen(Screen screen) {
		if (this.screen != null) {
			this.screen.hide();
			this.screen.dispose();
		}
		this.screen = screen;
		if (this.screen != null) {
			this.screen.show();
			this.screen.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		}
	}
	
	public void dispose() {
		Assets.dispose();
	}

}
