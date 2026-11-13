package com.juego.screens.menues;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Array;
import com.juego.core.Assets;
import com.juego.core.Core;
import com.juego.utils.Config;
import com.juego.utils.Platform;
import com.juego.utils.Text;

/** Language, sound and mouse settings, and the way to the video, controls and crosshair ones. Saved when leaving. */
public class ConfigMenuScreen extends MenuScreen {

	/** Opened from the main menu. */
	public ConfigMenuScreen(Core game) {
		this(game, SettingsFlow.mainMenu(game));
	}

	public ConfigMenuScreen(final Core game, final SettingsFlow flow) {
		super(game);
		Image background = new Image(loadTexture("configMenu/background.png"));
		background.setSize(Core.VIRTUAL_WIDTH, Core.VIRTUAL_HEIGHT);
		Image title = new Image(loadTexture(Text.image("configMenu/title.png")));
		title.setSize(title.getWidth() * 0.85f, title.getHeight() * 0.85f);
		title.setPosition(Core.VIRTUAL_WIDTH / 2 - title.getWidth() / 2, Core.VIRTUAL_HEIGHT / 1.14f);
		stage.addActor(background);
		stage.addActor(title);

		final Slider music = new Slider(0, 100, 1, false, Assets.skin);
		final Slider effects = new Slider(0, 100, 1, false, Assets.skin);
		// On a touch screen the sensitivity is how fast dragging a finger turns the view.
		final boolean touch = Platform.touch;
		final Slider sensitivity = touch
				? new Slider(Config.MIN_TOUCH_SENSITIVITY, Config.MAX_TOUCH_SENSITIVITY, 0.1f, false, Assets.skin)
				: new Slider(Config.MIN_SENSITIVITY, Config.MAX_SENSITIVITY, 0.1f, false, Assets.skin);
		final Label musicValue = new Label("", Assets.skin);
		final Label effectsValue = new Label("", Assets.skin);
		final Label sensitivityValue = new Label("", Assets.skin);
		TextButton video = new TextButton(Text.get("options.video"), Assets.skin);
		TextButton controls = new TextButton(Text.get("options.controls"), Assets.skin);
		TextButton crosshair = new TextButton(Text.get("options.crosshair"), Assets.skin);
		TextButton back = new TextButton(Text.get("back"), Assets.skin);

		music.setValue(Config.musicVolume);
		effects.setValue(Config.fxVolume);
		sensitivity.setValue(touch ? Config.touchSensitivity : Config.sensitivity);
		musicValue.setText((int) Config.musicVolume);
		effectsValue.setText((int) Config.fxVolume);
		sensitivityValue.setText(String.format("%.1f", sensitivity.getValue()));

		Table table = new Table();
		table.setFillParent(true);
		table.top().padTop(150);
		// The language first: someone who can't read the other one has to find it.
		final SelectBox<String> language = new SelectBox<>(Assets.skin);
		Array<String> languages = new Array<>();
		for (Text.Language l : Text.Language.values())
			languages.add(l.label);
		language.setItems(languages);
		language.setSelectedIndex(Text.language().ordinal());
		table.add(new Label(Text.get("options.language"), Assets.skin)).right().padRight(14);
		table.add(language).width(260).height(32);
		table.add().width(50);
		table.row().padBottom(6);
		language.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				Text.Language chosen = Text.Language.values()[language.getSelectedIndex()];
				if (chosen == Text.language())
					return;
				Config.language = chosen;
				Text.setLanguage(chosen);
				flow.open(new ConfigMenuScreen(game, flow)); // this page again, in the new language
			}
		});
		section(table, Text.get("options.sound"));
		row(table, Text.get("options.music"), music, musicValue);
		row(table, Text.get("options.effects"), effects, effectsValue);
		section(table, Text.get(touch ? "options.touch" : "options.mouse"));
		row(table, Text.get("options.sensitivity"), sensitivity, sensitivityValue);
		table.add().height(24).colspan(3).row();
		Table screens = new Table();
		screens.add(video).width(192).height(48).padRight(16);
		if (!touch) // key bindings: the touch controls are fixed
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
				if (touch)
					Config.touchSensitivity = sensitivity.getValue();
				else
					Config.sensitivity = sensitivity.getValue();
				sensitivityValue.setText(String.format("%.1f", sensitivity.getValue()));
			}
		});
		video.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				flow.open(new VideoMenuScreen(game, flow));
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
		setBackButton(back);
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
