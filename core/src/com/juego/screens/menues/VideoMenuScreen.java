package com.juego.screens.menues;

import com.badlogic.gdx.graphics.Color;
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
import com.juego.utils.Video;

/**
 * Video settings. Display mode, aspect ratio and resolution change when
 * Aplicar is pressed (so browsing the lists doesn't flash the screen); the
 * rest at once, also mid-match. Scaling (stretched or black bars) is for a
 * resolution of another shape than the monitor's, in fullscreen. Antialiasing needs the game to restart. On
 * phones only the field of view and the shadows apply.
 */
public class VideoMenuScreen extends MenuScreen {
	/** FPS limits offered (0: vertical sync, the screen's own rate). */
	private static final int[] FPS_LIMITS = { 0, 60, 120, 144, 165, 240, 300, 500, 1000 };

	private final SelectBox<String> mode, aspect, resolution, scaling, fps, shadows, msaa;
	private final Slider fov;
	private final Label fovValue;
	private Array<Video.Resolution> listed = new Array<>();
	private final Array<Video.Resolution> screenModes;
	private final Video.Resolution nativeResolution;
	/** The FPS limit values in the list (the console's fps_max can set one that isn't offered). */
	private final Array<Integer> fpsValues = new Array<>();

	public VideoMenuScreen(final Core game, final SettingsFlow flow) {
		super(game);
		Image background = new Image(loadTexture("configMenu/background.png"));
		background.setSize(Core.VIRTUAL_WIDTH, Core.VIRTUAL_HEIGHT);
		Image title = new Image(loadTexture(Text.image("configMenu/title.png")));
		title.setSize(title.getWidth() * 0.85f, title.getHeight() * 0.85f);
		title.setPosition(Core.VIRTUAL_WIDTH / 2 - title.getWidth() / 2, Core.VIRTUAL_HEIGHT / 1.14f);
		stage.addActor(background);
		stage.addActor(title);

		boolean desktop = Platform.desktop;
		screenModes = desktop ? Video.screenResolutions() : new Array<Video.Resolution>();
		nativeResolution = desktop ? Video.nativeResolution() : new Video.Resolution(0, 0);

		mode = select();
		Array<String> modes = new Array<>();
		for (Video.Mode m : Video.Mode.values())
			modes.add(m.label());
		mode.setItems(modes);
		mode.setSelectedIndex(Config.videoMode.ordinal());

		aspect = select();
		Array<String> aspects = new Array<>();
		for (Video.Aspect a : Video.Aspect.values())
			aspects.add(a.label());
		aspect.setItems(aspects);
		Video.Resolution current = desktop ? Video.chosenResolution() : nativeResolution;
		aspect.setSelectedIndex(Video.Aspect.of(current.width, current.height).ordinal());

		resolution = select();
		resolution.setMaxListCount(10);
		scaling = select();
		fillResolutions(current);

		Array<String> scalings = new Array<>();
		for (Video.Scaling s : Video.Scaling.values())
			scalings.add(s.label());
		scaling.setItems(scalings);
		scaling.setSelectedIndex(Config.scaling.ordinal());

		fps = select();
		Array<String> fpsItems = new Array<>();
		for (int limit : FPS_LIMITS)
			addFps(fpsItems, limit);
		if (!fpsValues.contains(Config.fpsMax, false))
			addFps(fpsItems, Config.fpsMax);
		fps.setItems(fpsItems);
		fps.setSelectedIndex(fpsValues.indexOf(Config.fpsMax, false));

		fov = new Slider(Video.MIN_FOV, Video.MAX_FOV, 1, false, Assets.skin);
		fov.setValue(Config.fov);
		fovValue = new Label(Integer.toString(Config.fov), Assets.skin);

		shadows = select();
		Array<String> shadowItems = new Array<>();
		for (Video.Shadows s : Video.Shadows.values())
			shadowItems.add(s.label());
		shadows.setItems(shadowItems);
		shadows.setSelectedIndex(Config.shadows.ordinal());

		msaa = select();
		Array<String> msaaItems = new Array<>();
		int msaaIndex = 0;
		for (int i = 0; i < Video.MSAA.length; i++) {
			int samples = Video.MSAA[i];
			msaaItems.add(Text.get("video.restart", samples == 0 ? Text.get("video.msaa.off") : samples + "x"));
			if (samples == Config.msaa)
				msaaIndex = i;
		}
		msaa.setItems(msaaItems);
		msaa.setSelectedIndex(msaaIndex);

		TextButton apply = new TextButton(Text.get("video.apply"), Assets.skin);
		TextButton back = new TextButton(Text.get("back"), Assets.skin);

		Table table = new Table();
		table.setFillParent(true);
		table.top().padTop(150);
		if (desktop) {
			row(table, Text.get("video.mode"), mode);
			row(table, Text.get("video.aspect"), aspect);
			row(table, Text.get("video.resolution"), resolution);
			row(table, Text.get("video.scaling"), scaling);
			row(table, Text.get("video.fps"), fps);
		}
		table.add(new Label(Text.get("video.fov"), Assets.skin)).right().padRight(14);
		table.add(fov).width(300);
		table.add(fovValue).width(50).padLeft(14).left();
		table.row().padBottom(8);
		row(table, Text.get("video.shadows"), shadows);
		if (desktop)
			row(table, Text.get("video.msaa"), msaa);
		Table buttons = new Table();
		if (desktop) {
			Label hint = new Label(Text.get("video.applyHint"), Assets.skin);
			hint.setColor(Color.LIGHT_GRAY);
			table.add(hint).colspan(3).padTop(6).padBottom(16).row();
			buttons.add(apply).width(192).height(48).padRight(16);
		} else {
			table.add().height(24).colspan(3).row();
		}
		buttons.add(back).width(192).height(48);
		table.add(buttons).colspan(3).row();
		stage.addActor(table);

		mode.addListener(changed(() -> fillResolutions(selectedResolution())));
		aspect.addListener(changed(() -> fillResolutions(selectedResolution())));
		scaling.addListener(changed(() -> {
			Config.scaling = Video.Scaling.values()[scaling.getSelectedIndex()];
			Video.applyScaling();
		}));
		fps.addListener(changed(() -> {
			Config.fpsMax = fpsValues.get(fps.getSelectedIndex());
			Config.applyFpsMax();
		}));
		fov.addListener(changed(() -> {
			Config.fov = Math.round(fov.getValue());
			fovValue.setText(Config.fov);
		}));
		shadows.addListener(changed(() -> Config.shadows = Video.Shadows.values()[shadows.getSelectedIndex()]));
		msaa.addListener(changed(() -> Config.msaa = Video.MSAA[msaa.getSelectedIndex()]));
		apply.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				Config.videoMode = Video.Mode.values()[mode.getSelectedIndex()];
				Video.Resolution r = selectedResolution();
				if (r != null) {
					Config.screenWidth = r.width;
					Config.screenHeight = r.height;
				}
				Video.apply();
				Config.save();
			}
		});
		setBackButton(back);
		back.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				flow.open(new ConfigMenuScreen(game, flow));
			}
		});
	}

	private void addFps(Array<String> items, int limit) {
		fpsValues.add(limit);
		items.add(limit == 0 ? Text.get("video.fps.vsync") : Integer.toString(limit));
	}

	/** The resolutions of the chosen mode and aspect ratio, keeping {@code keep} selected if it is one. */
	private void fillResolutions(Video.Resolution keep) {
		Video.Mode m = Video.Mode.values()[mode.getSelectedIndex()];
		Video.Aspect a = Video.Aspect.values()[aspect.getSelectedIndex()];
		listed = Video.resolutions(m, a, screenModes, nativeResolution);
		if (listed.isEmpty()) // nothing of that shape on this screen: show them all
			listed = Video.resolutions(m, Video.Aspect.ALL, screenModes, nativeResolution);
		Array<String> items = new Array<>();
		for (Video.Resolution r : listed)
			items.add(r.toString());
		resolution.setItems(items);
		int index = keep == null ? -1 : listed.indexOf(keep, false);
		if (index >= 0)
			resolution.setSelectedIndex(index);
		scaling.setDisabled(m == Video.Mode.WINDOWED); // a window is the resolution's own shape
	}

	private Video.Resolution selectedResolution() {
		int index = resolution.getSelectedIndex();
		return index >= 0 && index < listed.size ? listed.get(index) : null;
	}

	private static SelectBox<String> select() {
		return new SelectBox<>(Assets.skin);
	}

	private static void row(Table table, String name, SelectBox<String> box) {
		table.add(new Label(name, Assets.skin)).right().padRight(14);
		table.add(box).width(300).height(32);
		table.add().width(50);
		table.row().padBottom(8);
	}

	private static ChangeListener changed(Runnable action) {
		return new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				action.run();
			}
		};
	}

	@Override
	public void dispose() {
		Config.save();
		super.dispose();
	}
}
