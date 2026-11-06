package com.juego.screens.menues;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.juego.core.Assets;
import com.juego.core.Core;
import com.juego.utils.Config;

/** Sound, mouse and video settings. Saved when leaving the screen. */
public class ConfigMenuScreen extends MenuScreen {

	/** Opened from the main menu. */
	public ConfigMenuScreen(Core game) {
		this(game, SettingsFlow.mainMenu(game));
	}

	public ConfigMenuScreen(final Core game, final SettingsFlow flow) {
		super(game);
		Image background = new Image(loadTexture("configMenu/background.png"));
		background.setSize(Core.VIRTUAL_WIDTH, Core.VIRTUAL_HEIGHT);
		Image title = new Image(loadTexture("configMenu/title.png"));
		title.setSize(title.getWidth() * 0.85f, title.getHeight() * 0.85f);
		title.setPosition(Core.VIRTUAL_WIDTH / 2 - title.getWidth() / 2, Core.VIRTUAL_HEIGHT / 1.14f);
		stage.addActor(background);
		stage.addActor(title);

		final Slider music = new Slider(0, 100, 1, false, Assets.skin);
		final Slider effects = new Slider(0, 100, 1, false, Assets.skin);
		final Slider sensitivity = new Slider(Config.MIN_SENSITIVITY, Config.MAX_SENSITIVITY, 0.1f, false, Assets.skin);
		final Label musicValue = new Label("", Assets.skin);
		final Label effectsValue = new Label("", Assets.skin);
		final Label sensitivityValue = new Label("", Assets.skin);
		final CheckBox fullscreen = new CheckBox(" Pantalla Completa", Assets.skin);
		TextButton controls = new TextButton("Controles", Assets.skin);
		TextButton crosshair = new TextButton("Mira", Assets.skin);
		TextButton back = new TextButton("Volver", Assets.skin);

		music.setValue(Config.musicVolume);
		effects.setValue(Config.fxVolume);
		sensitivity.setValue(Config.sensitivity);
		fullscreen.setChecked(Config.fullscreen);
		musicValue.setText((int) Config.musicVolume);
		effectsValue.setText((int) Config.fxVolume);
		sensitivityValue.setText(String.format("%.1f", Config.sensitivity));

		Table table = new Table();
		table.setFillParent(true);
		table.top().padTop(150);
		section(table, "Sonido");
		row(table, "Musica", music, musicValue);
		row(table, "Efectos", effects, effectsValue);
		section(table, "Mouse");
		row(table, "Sensibilidad", sensitivity, sensitivityValue);
		section(table, "Video");
		table.add(fullscreen).colspan(3).padBottom(24).row();
		Table screens = new Table();
		screens.add(controls).width(192).height(48).padRight(16);
		screens.add(crosshair).width(192).height(48);
		table.add(screens).colspan(3).padBottom(12).row();
		table.add(back).colspan(3).width(192).height(48).row();
		stage.addActor(table);

		music.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				Config.musicVolume = music.getValue();
				musicValue.setText((int) Config.musicVolume);
			}
		});
		effects.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				Config.fxVolume = effects.getValue();
				effectsValue.setText((int) Config.fxVolume);
			}
		});
		sensitivity.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				Config.sensitivity = sensitivity.getValue();
				sensitivityValue.setText(String.format("%.1f", Config.sensitivity));
			}
		});
		fullscreen.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				Config.fullscreen = fullscreen.isChecked();
				if (Config.fullscreen)
					Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
				else
					Gdx.graphics.setWindowedMode(Core.VIRTUAL_WIDTH, Core.VIRTUAL_HEIGHT);
			}
		});
		controls.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				flow.open(new ControlsMenuScreen(game, flow));
			}
		});
		crosshair.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				flow.open(new CrosshairMenuScreen(game, flow));
			}
		});
		back.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				flow.close();
			}
		});
	}

	private static void section(Table table, String name) {
		Label label = new Label(name, Assets.skin);
		label.setFontScale(1.5f);
		table.add(label).colspan(3).padTop(10).padBottom(8).row();
	}

	private static void row(Table table, String name, Slider slider, Label value) {
		table.add(new Label(name, Assets.skin)).right().padRight(14);
		table.add(slider).width(260);
		table.add(value).width(50).padLeft(14).left();
		table.row().padBottom(6);
	}

	@Override
	public void dispose() {
		Config.save();
		super.dispose();
	}
}
