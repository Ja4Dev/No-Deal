package com.juego.screens.menues;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.juego.UI.Menu;
import com.juego.core.Assets;
import com.juego.core.Core;
import com.juego.utils.Config;

public class ConfigMenuScreen extends Menu implements Screen {

	private Core game;
	Image backgroundImage, titleImage;
	TextButton backButton, controlsButton;
	CheckBox fullscreen;
	Slider musicVolumeSlider, fxVolumeSlider;
	Label sound, musicVolume, fxVolume, musicVolumeValue, fxVolumeValue, video;

	public ConfigMenuScreen(Core game) {
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
				game.setScreen(new MainMenuScreen(game));
			}
		});
		fullscreen.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				if(fullscreen.isChecked()) {
				Config.fullscreen = true;
				getGraphs().setFullscreenMode(Gdx.graphics.getDisplayMode());
				} else getGraphs().setWindowedMode(Core.VIRTUAL_WIDTH, Core.VIRTUAL_HEIGHT);
			}
		});
		musicVolumeSlider.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				Config.musicVolume = musicVolumeSlider.getValue();
				musicVolumeValue.setText((int) Config.musicVolume);
			}
		});
		fxVolumeSlider.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				Config.fxVolume = fxVolumeSlider.getValue();
				fxVolumeValue.setText((int) Config.fxVolume);
			}
		});
		controlsButton.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				game.setScreen(new ControlsMenuScreen(game));
			}
		});

	}

	private void configureWidgers() {
		
		backgroundImage.setSize(Core.VIRTUAL_WIDTH, Core.VIRTUAL_HEIGHT);
		titleImage.setSize(titleImage.getWidth() * 0.85f, titleImage.getHeight() * 0.85f);
		titleImage.setPosition(Core.VIRTUAL_WIDTH / 2 - titleImage.getWidth() / 2, Core.VIRTUAL_HEIGHT / 1.14f);
		backButton.setSize(192, 48);
		backButton.setPosition(Core.VIRTUAL_WIDTH / 2 - backButton.getWidth() / 2, Core.VIRTUAL_HEIGHT / 2 - 250);
		sound.setFontScale(1.5f);
		sound.setPosition(Core.VIRTUAL_WIDTH / 2 - sound.getWidth() / 1.375f, titleImage.getY() - 75);
		musicVolume.setPosition(Core.VIRTUAL_WIDTH / 2 - musicVolume.getWidth() / 2, sound.getY() - 40);
		musicVolumeSlider.setPosition(Core.VIRTUAL_WIDTH / 2 - musicVolumeSlider.getWidth() / 2, musicVolume.getY() - 20);
		musicVolumeSlider.setValue(Config.musicVolume);
		musicVolumeValue.setPosition(Core.VIRTUAL_WIDTH / 2 - musicVolumeSlider.getX() + 650, musicVolume.getY() - 10);
		musicVolumeValue.setText((int) Config.musicVolume);
		fxVolume.setPosition(Core.VIRTUAL_WIDTH / 2 - fxVolume.getWidth() / 2, musicVolumeSlider.getY() - 40);
		fxVolumeSlider.setPosition(Core.VIRTUAL_WIDTH / 2 - fxVolumeSlider.getWidth() / 2, fxVolume.getY() - 20);
		fxVolumeSlider.setValue(Config.fxVolume);
		fxVolumeValue.setPosition(Core.VIRTUAL_WIDTH / 2 - fxVolumeSlider.getX() + 650, fxVolume.getY() - 10);
		fxVolumeValue.setText((int) Config.fxVolume);
		video.setFontScale(1.5f);
		video.setPosition(Core.VIRTUAL_WIDTH / 2 - video.getWidth() / 1.375f, fxVolumeSlider.getY() - 100);
		fullscreen.setPosition(Core.VIRTUAL_WIDTH / 2 - fullscreen.getWidth() / 2, video.getY() - 40);
		if(Config.fullscreen) fullscreen.setChecked(true);
		controlsButton.setSize(192, 48);
		controlsButton.setPosition(Core.VIRTUAL_WIDTH / 2 - controlsButton.getWidth() / 2, fullscreen.getY() - 80);
		stage.addActor(backgroundImage);
		stage.addActor(titleImage);
		stage.addActor(backButton);
		stage.addActor(sound);
		stage.addActor(musicVolume);
		stage.addActor(musicVolumeSlider);
		stage.addActor(musicVolumeValue);
		stage.addActor(fxVolume);
		stage.addActor(fxVolumeSlider);
		stage.addActor(fxVolumeValue);
		stage.addActor(video);
		stage.addActor(fullscreen);
//		stage.addActor(controlsButton);
	}

	private void setWidgets() {
		backgroundImage = new Image(new Texture(Gdx.files.internal("configMenu/background.png")));
		titleImage = new Image(new Texture(Gdx.files.internal("configMenu/title.png")));
		backButton = new TextButton("Volver", Assets.skin);	
		sound = new Label("Sonido", Assets.skin);
		musicVolume = new Label("Musica", Assets.skin);
		musicVolumeSlider = new Slider(0, 100, 1, false, Assets.skin);
		musicVolumeValue = new Label("", Assets.skin);
		fxVolume = new Label("Efectos", Assets.skin);
		fxVolumeSlider = new Slider (0, 100, 1, false, Assets.skin);
		fxVolumeValue = new Label("", Assets.skin);
		video = new Label("Video", Assets.skin);
		fullscreen = new CheckBox("Pantalla Completa", Assets.skin);
		controlsButton = new TextButton("Controles", Assets.skin);
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
	
	public Graphics getGraphs() {
		return Gdx.app.getGraphics();
	}
	

}
