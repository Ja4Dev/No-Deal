package com.juego.UI;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.juego.components.PlayerComponent;
import com.juego.core.Assets;
import com.juego.core.Core;
import com.juego.core.GameWorld;
import com.juego.screens.GameScreen;
import com.juego.screens.menues.ConfigMenuScreen;
import com.juego.screens.menues.CreditsScreen;
import com.juego.screens.menues.MainMenuScreen;

public class PauseMenu extends Menu{
	Image backgroundImage, titleImage;
	TextButton resumeButton, scoreButton, menuButton;
	private PlayerComponent playerComponent;
	private GameWorld gameWorld;
	
	
	
	public PauseMenu(PlayerComponent playerComponent, GameWorld gameWorld) {
		this.playerComponent = playerComponent;
		this.gameWorld = gameWorld;
		setWidgets();
		configureWidgers();
		setListeners();
		Gdx.input.setInputProcessor(stage);
	}
	
	private void setListeners() {
		resumeButton.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				playerComponent.pause = false;
			}
		});
		scoreButton.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				playerComponent.scoreboard = true;
			}
		});
		menuButton.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				gameWorld.game.musicPlayer.paused = false;
				gameWorld.game.setScreen(new MainMenuScreen(gameWorld.game));
			}
		});

	}
	
	private void configureWidgers() {
		backgroundImage.setSize(Core.VIRTUAL_WIDTH / 2 - backgroundImage.getWidth() / 1, Core.VIRTUAL_HEIGHT / 1.5f);
		backgroundImage.setPosition(Core.VIRTUAL_WIDTH / 2 - backgroundImage.getWidth() / 2, Core.VIRTUAL_HEIGHT / 2 - backgroundImage.getHeight() / 2);
		titleImage.setSize(titleImage.getWidth() * 1.25f, titleImage.getHeight() * 1.25f);
		titleImage.setPosition(Core.VIRTUAL_WIDTH / 2 - titleImage.getWidth() / 2, Core.VIRTUAL_HEIGHT / 1.4f - titleImage.getHeight() / 2);
		resumeButton.setSize(192, 48);
		resumeButton.setPosition(Core.VIRTUAL_WIDTH / 2 - resumeButton.getWidth() / 2, Core.VIRTUAL_HEIGHT / 2);
		scoreButton.setSize(192, 48);
		scoreButton.setPosition(Core.VIRTUAL_WIDTH / 2 - resumeButton.getWidth() / 2, Core.VIRTUAL_HEIGHT / 2 - 60);
		menuButton.setSize(192, 48);
		menuButton.setPosition(Core.VIRTUAL_WIDTH / 2 - resumeButton.getWidth() / 2, Core.VIRTUAL_HEIGHT / 2 - 120);
		stage.addActor(backgroundImage);
		stage.addActor(titleImage);
		stage.addActor(resumeButton);
		stage.addActor(scoreButton);
		stage.addActor(menuButton);

	}

	private void setWidgets() {
		backgroundImage = new Image(new Texture(Gdx.files.internal("pauseMenu/background.png")));
		titleImage = new Image(new Texture(Gdx.files.internal("pauseMenu/title.png")));
		resumeButton = new TextButton("Reanudar", Assets.skin);
		scoreButton = new TextButton("Puntuacion", Assets.skin);
		menuButton = new TextButton("Menu Principal", Assets.skin);
	}

	public void update(float delta) {
		if(playerComponent.pause) {
		stage.act(delta);
		stage.draw();
		}
	}

	public void resize(int width, int height) {
		stage.getViewport().update(width, height);		
	}

}
