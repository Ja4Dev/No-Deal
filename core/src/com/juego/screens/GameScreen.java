package com.juego.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.juego.core.Core;
import com.juego.core.GameWorld;
import com.juego.screens.menues.MainMenuScreen;

public class GameScreen implements Screen {
	
	private Core game;
	private GameWorld gameWorld;
	
	
	
	public GameScreen(Core game) {
		this.game = game;
		
		gameWorld = new GameWorld(game);
		Gdx.input.setCursorCatched(true);
		}

	@Override
	public void show() {
		// TODO Auto-generated method stub

	}

	@Override
	public void render(float delta) {
		gameWorld.render(delta);
	}

	@Override
	public void resize(int width, int height) {
		gameWorld.resize(width, height);
	}

	@Override
	public void pause() {
		// TODO Auto-generated method stub

	}

	@Override
	public void resume() {
		// TODO Auto-generated method stub

	}

	@Override
	public void hide() {
		// TODO Auto-generated method stub

	}

	@Override
	public void dispose() {
		 gameWorld.dispose();

	}

}
