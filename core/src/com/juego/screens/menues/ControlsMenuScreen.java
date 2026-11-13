package com.juego.screens.menues;

import java.util.EnumMap;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.juego.core.Assets;
import com.juego.core.Core;
import com.juego.utils.Config;
import com.juego.utils.Controls;
import com.juego.utils.Controls.Action;
import com.juego.utils.Controls.Binding;
import com.juego.utils.Text;

/**
 * Rebinds controls: click an action, then press the key or mouse button to
 * use for it (Escape cancels). Taking a binding another action had swaps them.
 */
public class ControlsMenuScreen extends MenuScreen {
	private final EnumMap<Action, TextButton> buttons = new EnumMap<>(Action.class);
	private final Label hint;
	/** The action waiting for a key/button, or null. */
	private Action waiting;

	/** Frame in which the last key/button was captured (that press must not also pause or unpause). */
	private long captureFrame = -10;

	public ControlsMenuScreen(final Core game, final SettingsFlow flow) {
		super(game);
		Image background = new Image(loadTexture("controlsMenu/background.png"));
		background.setSize(Core.VIRTUAL_WIDTH, Core.VIRTUAL_HEIGHT);
		Image title = new Image(loadTexture(Text.image("controlsMenu/title.png")));
		title.setSize(title.getWidth() * 0.85f, title.getHeight() * 0.85f);
		title.setPosition(Core.VIRTUAL_WIDTH / 2 - title.getWidth() / 2, Core.VIRTUAL_HEIGHT / 1.14f);
		stage.addActor(background);
		stage.addActor(title);

		Table grid = new Table();
		Action[] actions = Action.values();
		int half = (actions.length + 1) / 2;
		for (int i = 0; i < half; i++) {
			addCell(grid, actions[i]);
			if (i + half < actions.length) {
				grid.add().width(40);
				addCell(grid, actions[i + half]);
			}
			grid.row().padBottom(8);
		}

		hint = new Label(Text.get("controls.hint"), Assets.skin);
		hint.setColor(Color.LIGHT_GRAY);
		TextButton defaults = new TextButton(Text.get("defaults"), Assets.skin);
		TextButton back = new TextButton(Text.get("back"), Assets.skin);

		Table table = new Table();
		table.setFillParent(true);
		table.top().padTop(160);
		table.add(grid).colspan(2).padBottom(20).row();
		table.add(hint).colspan(2).padBottom(24).row();
		table.add(defaults).width(192).height(48).padRight(16);
		table.add(back).width(192).height(48);
		stage.addActor(table);

		defaults.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				Controls.resetDefaults();
				waiting = null;
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
		// Capture listener: sees the key/click before any button does, so the click that
		// picks a mouse binding doesn't also press whatever button is under the cursor.
		stage.addCaptureListener(new InputListener() {
			@Override
			public boolean keyDown(InputEvent event, int keycode) {
				if (waiting == null)
					return false;
				if (keycode != Input.Keys.ESCAPE)
					Controls.set(waiting, Binding.key(keycode));
				finishCapture(event);
				return true;
			}

			@Override
			public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
				if (waiting == null)
					return false;
				Controls.set(waiting, Binding.mouse(button));
				finishCapture(event);
				return true;
			}
		});
		refresh();
	}

	private void addCell(Table grid, final Action action) {
		TextButton button = new TextButton("", Assets.skin);
		buttons.put(action, button);
		grid.add(new Label(action.label(), Assets.skin)).right().padRight(12);
		grid.add(button).width(170).height(36);
		button.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				waiting = action;
				refresh();
			}
		});
	}

	private void finishCapture(InputEvent event) {
		waiting = null;
		captureFrame = Gdx.graphics.getFrameId();
		event.stop();
		event.cancel();
		refresh();
	}

	@Override
	public boolean capturesKeys() {
		return waiting != null || Gdx.graphics.getFrameId() - captureFrame <= 1;
	}

	private void refresh() {
		for (Action action : Action.values()) {
			TextButton button = buttons.get(action);
			button.setText(action == waiting ? Text.get("controls.waiting") : Controls.get(action).displayName());
		}
	}

	@Override
	public void dispose() {
		Config.save();
		super.dispose();
	}
}
