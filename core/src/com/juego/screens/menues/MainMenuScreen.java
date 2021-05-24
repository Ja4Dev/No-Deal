package com.juego.screens.menues;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.juego.UI.Menu;
import com.juego.core.Assets;
import com.juego.core.Core;
import com.juego.screens.GameScreen;
import com.juego.utils.MusicPlayer;

public class MainMenuScreen extends Menu implements Screen {
	Core game;
	Image backgroundImage, titleImage;
	TextButton playButton, configButton, creditsButton, quitButton;
	Label musicPlayer;
	TextButton musicPause, musicNext, musicPrev;

	public MainMenuScreen(Core game) {
		this.game = game;
		setWidgets();
		configureWidgers();
		setListeners();
		Gdx.input.setInputProcessor(stage);
	}

	private void setListeners() {
		playButton.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				game.setScreen(new GameScreen(game));
				game.musicPlayer.paused = true;
				game.musicPlayer.music[game.musicPlayer.getTemaActual()].stop();
			}
		});
		configButton.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				game.setScreen(new ConfigMenuScreen(game));
			}
		});
		creditsButton.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				game.setScreen(new CreditsScreen(game));
			}
		});
		quitButton.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				Gdx.app.exit();
			}
		});
		musicPause.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				game.musicPlayer.paused = !game.musicPlayer.paused;
			}
		});
		musicNext.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				game.musicPlayer.nextSong();
			}
		});
		musicPrev.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				game.musicPlayer.prevSong();
			}
		});

	}

	private void configureWidgers() {
		backgroundImage.setSize(Core.VIRTUAL_WIDTH, Core.VIRTUAL_HEIGHT);
		titleImage.setPosition(Core.VIRTUAL_WIDTH / 2 - titleImage.getWidth() / 2, Core.VIRTUAL_HEIGHT / 1.25f);
		playButton.setSize(256, 64);
		playButton.setPosition(Core.VIRTUAL_WIDTH / 2 - playButton.getWidth() / 2, Core.VIRTUAL_HEIGHT / 2 - 0);
		configButton.setSize(256, 64);
		configButton.setPosition(Core.VIRTUAL_WIDTH / 2 - playButton.getWidth() / 2, Core.VIRTUAL_HEIGHT / 2 - 70);
		creditsButton.setSize(256, 64);
		creditsButton.setPosition(Core.VIRTUAL_WIDTH / 2 - playButton.getWidth() / 2, Core.VIRTUAL_HEIGHT / 2 - 140);
		quitButton.setSize(256, 64);
		quitButton.setPosition(Core.VIRTUAL_WIDTH / 2 - playButton.getWidth() / 2, Core.VIRTUAL_HEIGHT / 2 - 210);
		musicPlayer.setFontScale(1.25f);
		musicPlayer.setPosition(36, 47);
		musicPrev.setSize(32, 32);
		musicPrev.setPosition(10, 10);
		musicPause.setSize(48, 32);
		musicPause.setPosition(musicPrev.getX() + 37, 10);
		musicNext.setSize(32, 32);
		musicNext.setPosition(musicPause.getX() + 52, 10);
		stage.addActor(backgroundImage);
		stage.addActor(titleImage);
		stage.addActor(playButton);
		stage.addActor(configButton);
		stage.addActor(creditsButton);
		stage.addActor(quitButton);
		stage.addActor(musicPlayer);
		stage.addActor(musicPrev);
		stage.addActor(musicPause);
		stage.addActor(musicNext);
	}

	private void setWidgets() {
		backgroundImage = new Image(new Texture(Gdx.files.internal("mainMenu/background.png")));
		titleImage = new Image(new Texture(Gdx.files.internal("mainMenu/title.png")));
		playButton = new TextButton("Jugar Demo", Assets.skin);
		configButton = new TextButton("Opciones", Assets.skin);
		creditsButton = new TextButton("Creditos", Assets.skin);
		quitButton = new TextButton("Salir", Assets.skin);
		musicPlayer = new Label("Musica", Assets.skin);
		musicPrev = new TextButton("<<", Assets.skin);
		musicPause = new TextButton("Pausa", Assets.skin);
		musicNext = new TextButton(">>", Assets.skin);
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
