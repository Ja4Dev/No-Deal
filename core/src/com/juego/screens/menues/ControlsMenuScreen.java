package com.juego.screens.menues;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.juego.UI.Menu;
import com.juego.core.Assets;
import com.juego.core.Core;
import com.juego.utils.Config;

public class ControlsMenuScreen extends Menu implements Screen {

	private Core game;
	Image backgroundImage, titleImage;
	TextButton backButton;
	TextButton forwardButton,backwardButton, leftButton, rightButton, jumpButton, walkButton, reloadButton, changeGunButton, scoreboardsButton, fireButton, zoomButton, pauseButton;
	Label forward,backward, left, right, jump, walk, reload, changeGun, scoreboards, fire, zoom, pause;
	
	public ControlsMenuScreen(Core game) {
		this.game = game;
		setWidgets();
		configureWidgers();
		setListeners();
		Gdx.input.setInputProcessor(stage);
	}

	private void setListeners() {
		backButton.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				game.setScreen(new ConfigMenuScreen(game));
			}
		});
		
	}

	private void configureWidgers() {
		backgroundImage.setSize(Core.VIRTUAL_WIDTH, Core.VIRTUAL_HEIGHT);
		titleImage.setSize(titleImage.getWidth() * 0.85f, titleImage.getHeight() * 0.85f);
		titleImage.setPosition(Core.VIRTUAL_WIDTH / 2 - titleImage.getWidth() / 2, Core.VIRTUAL_HEIGHT / 1.14f);
		backButton.setSize(192, 48);
		backButton.setPosition(Core.VIRTUAL_WIDTH / 2 - backButton.getWidth() / 2, Core.VIRTUAL_HEIGHT / 2 - 250);
	}

	private void setWidgets() {
		backgroundImage = new Image(new Texture(Gdx.files.internal("controlsMenu/background.png")));
		titleImage = new Image(new Texture(Gdx.files.internal("controlsMenu/title.png")));
		backButton = new TextButton("Volver", Assets.skin);
		stage.addActor(backgroundImage);
		stage.addActor(titleImage);
		stage.addActor(backButton);
	}

	@Override
	public void render(float delta) {
	stage.act(delta);
	stage.draw();
	}
	
	@Override
	public void resize(int width, int height) {
	stage.getViewport().update(width, height);
	}

	@Override
	public void dispose() {
		stage.dispose();
	}

	@Override
	public void show() {
		// TODO Auto-generated method stub
		
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

}
