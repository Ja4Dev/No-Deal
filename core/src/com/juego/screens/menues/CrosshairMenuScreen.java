package com.juego.screens.menues;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.HdpiUtils;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Array;
import com.juego.UI.Crosshair;
import com.juego.core.Assets;
import com.juego.core.Core;
import com.juego.utils.Config;
import com.juego.utils.CrosshairSettings;
import com.juego.utils.Text;

/**
 * Crosshair settings with a live preview, drawn over a light and a dark
 * background to check it is visible on both. Saved when leaving the screen.
 */
public class CrosshairMenuScreen extends MenuScreen {
	/** Preview area, in stage coordinates. */
	private static final float PREVIEW_X = 760, PREVIEW_Y = 150, PREVIEW_W = 400, PREVIEW_H = 400;

	private final CrosshairSettings settings = Config.crosshair;
	private final Crosshair crosshair = new Crosshair();
	private final ShapeRenderer shapes = new ShapeRenderer();
	private final Matrix4 projection = new Matrix4();
	private final Vector2 corner = new Vector2(), opposite = new Vector2();
	private float time;

	private final SelectBox<String> style, color;
	private final Slider size, thickness, gap, opacity;
	private final Label sizeValue, thicknessValue, gapValue, opacityValue;
	private final CheckBox outline, dynamicMove, dynamicFire, followRecoil;

	/** Opened from the main menu. */
	public CrosshairMenuScreen(Core game) {
		this(game, SettingsFlow.mainMenu(game));
	}

	public CrosshairMenuScreen(final Core game, final SettingsFlow flow) {
		super(game);
		Image background = new Image(loadTexture("configMenu/background.png"));
		background.setSize(Core.VIRTUAL_WIDTH, Core.VIRTUAL_HEIGHT);
		stage.addActor(background);

		Array<String> styles = new Array<>();
		for (CrosshairSettings.Style s : CrosshairSettings.Style.values())
			styles.add(s.label());
		style = new SelectBox<>(Assets.skin);
		style.setItems(styles);
		color = new SelectBox<>(Assets.skin);
		color.setItems(CrosshairSettings.colorNames());
		size = new Slider(CrosshairSettings.MIN_SIZE, CrosshairSettings.MAX_SIZE, 1, false, Assets.skin);
		thickness = new Slider(CrosshairSettings.MIN_THICKNESS, CrosshairSettings.MAX_THICKNESS, 1, false, Assets.skin);
		gap = new Slider(CrosshairSettings.MIN_GAP, CrosshairSettings.MAX_GAP, 1, false, Assets.skin);
		opacity = new Slider(CrosshairSettings.MIN_OPACITY * 100, 100, 5, false, Assets.skin);
		sizeValue = new Label("", Assets.skin);
		thicknessValue = new Label("", Assets.skin);
		gapValue = new Label("", Assets.skin);
		opacityValue = new Label("", Assets.skin);
		outline = new CheckBox(Text.get("crosshair.outline"), Assets.skin);
		dynamicMove = new CheckBox(Text.get("crosshair.dynamicMove"), Assets.skin);
		dynamicFire = new CheckBox(Text.get("crosshair.dynamicFire"), Assets.skin);
		followRecoil = new CheckBox(Text.get("crosshair.followRecoil"), Assets.skin);
		TextButton defaults = new TextButton(Text.get("defaults"), Assets.skin);
		TextButton back = new TextButton(Text.get("back"), Assets.skin);

		Label title = new Label(Text.get("crosshair.title"), Assets.skin);
		title.setFontScale(2f);
		Table table = new Table();
		table.setPosition(100, 70);
		table.setSize(600, 560);
		table.top().left();
		table.add(title).colspan(3).left().padBottom(24).row();
		row(table, Text.get("crosshair.style"), style, null);
		row(table, Text.get("crosshair.color"), color, null);
		row(table, Text.get("crosshair.size"), size, sizeValue);
		row(table, Text.get("crosshair.thickness"), thickness, thicknessValue);
		row(table, Text.get("crosshair.gap"), gap, gapValue);
		row(table, Text.get("crosshair.opacity"), opacity, opacityValue);
		table.add(outline).colspan(3).left().padTop(8).row();
		table.add(dynamicMove).colspan(3).left().padTop(8).row();
		table.add(dynamicFire).colspan(3).left().padTop(8).row();
		table.add(followRecoil).colspan(3).left().padTop(8).padBottom(24).row();
		Table buttons = new Table();
		buttons.add(defaults).width(192).height(48).padRight(16);
		buttons.add(back).width(192).height(48);
		table.add(buttons).colspan(3).left().row();
		stage.addActor(table);

		Label preview = new Label(Text.get("crosshair.preview"), Assets.skin);
		preview.setPosition(PREVIEW_X, PREVIEW_Y + PREVIEW_H + 10);
		stage.addActor(preview);

		refresh();
		ChangeListener changed = new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				settings.style = CrosshairSettings.Style.values()[style.getSelectedIndex()];
				settings.color = color.getSelectedIndex();
				settings.size = (int) size.getValue();
				settings.thickness = (int) thickness.getValue();
				settings.gap = (int) gap.getValue();
				settings.opacity = opacity.getValue() / 100f;
				settings.outline = outline.isChecked();
				settings.dynamicMove = dynamicMove.isChecked();
				settings.dynamicFire = dynamicFire.isChecked();
				settings.followRecoil = followRecoil.isChecked();
				refreshValues();
			}
		};
		for (Actor actor : new Actor[] { style, color, size, thickness, gap, opacity, outline, dynamicMove, dynamicFire,
				followRecoil })
			actor.addListener(changed);
		defaults.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				settings.resetDefaults();
				refresh();
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

	private static void row(Table table, String name, Actor widget, Label value) {
		table.add(new Label(name, Assets.skin)).left().padRight(14);
		table.add(widget).width(260).left();
		if (value != null)
			table.add(value).width(50).padLeft(14).left();
		else
			table.add();
		table.row().padBottom(8);
	}

	/** Puts the saved settings in the widgets. */
	private void refresh() {
		style.setSelectedIndex(settings.style.ordinal());
		color.setSelectedIndex(settings.color);
		size.setValue(settings.size);
		thickness.setValue(settings.thickness);
		gap.setValue(settings.gap);
		opacity.setValue(Math.round(settings.opacity * 100));
		outline.setChecked(settings.outline);
		dynamicMove.setChecked(settings.dynamicMove);
		dynamicFire.setChecked(settings.dynamicFire);
		followRecoil.setChecked(settings.followRecoil);
		refreshValues();
	}

	private void refreshValues() {
		sizeValue.setText(settings.size);
		thicknessValue.setText(settings.thickness);
		gapValue.setText(settings.gap);
		opacityValue.setText(Math.round(settings.opacity * 100) + "%");
	}

	@Override
	public void render(float delta) {
		super.render(delta);
		time += delta;
		// Preview area in window pixels (y up).
		stage.getViewport().project(corner.set(PREVIEW_X, PREVIEW_Y));
		stage.getViewport().project(opposite.set(PREVIEW_X + PREVIEW_W, PREVIEW_Y + PREVIEW_H));
		float x = corner.x, y = corner.y, w = opposite.x - corner.x, h = opposite.y - corner.y;

		int width = Gdx.graphics.getWidth(), height = Gdx.graphics.getHeight();
		HdpiUtils.glViewport(0, 0, width, height);
		shapes.setProjectionMatrix(projection.setToOrtho2D(0, 0, width, height));
		Gdx.gl.glEnable(GL20.GL_BLEND);
		shapes.begin(ShapeRenderer.ShapeType.Filled);
		shapes.setColor(new Color(0.62f, 0.72f, 0.8f, 1)); // sky
		shapes.rect(x, y + h / 2, w / 2, h / 2);
		shapes.setColor(new Color(0.78f, 0.74f, 0.66f, 1)); // light wall
		shapes.rect(x + w / 2, y + h / 2, w / 2, h / 2);
		shapes.setColor(new Color(0.2f, 0.2f, 0.22f, 1)); // shadow
		shapes.rect(x, y, w / 2, h / 2);
		shapes.setColor(new Color(0.35f, 0.3f, 0.26f, 1)); // floor
		shapes.rect(x + w / 2, y, w / 2, h / 2);
		shapes.end();

		// A loop of 4 seconds: running (2 s), then a spray (2 s), to show each option at work.
		float phase = time % 4f, scale = Crosshair.scale();
		float running = phase < 2 ? MathUtils.sin(phase / 2 * MathUtils.PI) : 0;
		float spraying = phase >= 2 ? MathUtils.sin((phase - 2) / 2 * MathUtils.PI) : 0;
		float spread = ((settings.dynamicMove ? running * 22f : 0) + (settings.dynamicFire ? spraying * 16f : 0)) * scale;
		float climb = settings.followRecoil ? spraying * 28f * scale : 0;
		crosshair.draw(settings, x + w / 2, y + h / 2 + climb, spread);
		stage.getViewport().apply();
	}

	@Override
	public void dispose() {
		Config.save();
		crosshair.dispose();
		shapes.dispose();
		super.dispose();
	}
}
