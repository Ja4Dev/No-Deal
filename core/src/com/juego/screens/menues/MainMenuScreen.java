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
import com.juego.core.GameSession;
import com.juego.utils.MusicPlayer;

public class MainMenuScreen extends MenuScreen {
	Image backgroundImage, titleImage;
	TextButton playButton, hostButton, joinButton, configButton, creditsButton, quitButton;
	Label musicPlayer, messageLabel;
	TextButton musicPause, musicNext, musicPrev;
	/** Shown above the buttons, e.g. why an online match ended. */
	private final String message;

	public MainMenuScreen(Core game) {
		this(game, null);
	}

	public MainMenuScreen(Core game, String message) {
		super(game);
		this.message = message;
		setWidgets();
		configureWidgers();
		setListeners();
	}

	private void setListeners() {
		playButton.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				game.setScreen(new MultiplayerScreen(game, GameSession.Kind.SINGLEPLAYER));
			}
		});
		hostButton.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				game.setScreen(new MultiplayerScreen(game, GameSession.Kind.HOST));
			}
		});
		joinButton.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				game.setScreen(new MultiplayerScreen(game, GameSession.Kind.JOIN));
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
		TextButton[] buttons = { playButton, hostButton, joinButton, configButton, creditsButton, quitButton };
		float top = Core.VIRTUAL_HEIGHT / 2 + 100;
		for (int i = 0; i < buttons.length; i++) {
			buttons[i].setSize(256, 56);
			buttons[i].setPosition(Core.VIRTUAL_WIDTH / 2 - 128, top - i * 64);
			stage.addActor(buttons[i]);
		}
		if (message != null && !message.isEmpty()) {
			messageLabel.setColor(1f, 0.35f, 0.35f, 1f);
			messageLabel.pack();
			messageLabel.setPosition(Core.VIRTUAL_WIDTH / 2 - messageLabel.getWidth() / 2, top + 70);
		}
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
		backgroundImage.toBack();
		if (message != null && !message.isEmpty())
			stage.addActor(messageLabel);
		// The music controls only make sense when the playlist has tracks.
		if (game.musicPlayer.hasTracks()) {
			stage.addActor(musicPlayer);
			stage.addActor(musicPrev);
			stage.addActor(musicPause);
			stage.addActor(musicNext);
		}
	}

	private void setWidgets() {
		backgroundImage = new Image(loadTexture("mainMenu/background.png"));
		titleImage = new Image(loadTexture("mainMenu/title.png"));
		playButton = new TextButton("Un jugador", Assets.skin);
		hostButton = new TextButton("Crear partida", Assets.skin);
		joinButton = new TextButton("Unirse a partida", Assets.skin);
		messageLabel = new Label(message == null ? "" : message, Assets.skin);
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
	musicPlayer.setText("Musica: " + game.musicPlayer.getCurrentTitle());
	stage.act(delta);
	stage.draw();
	}
	
	

}
