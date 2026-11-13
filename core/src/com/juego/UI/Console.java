package com.juego.UI;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Array;
import com.juego.core.Assets;
import com.juego.core.Core;
import com.juego.utils.Platform;
import com.juego.utils.Text;

/**
 * The in-game console: a line to type commands at the top of the screen and
 * the last answers above it. Whoever runs the server (singleplayer or "Crear
 * partida") uses it to add and remove bots or change their difficulty
 * mid-match.
 */
public class Console extends Menu {
	/** Runs a typed line and returns what to show (may be several lines). */
	public interface Executor {
		String execute(String line);
	}

	private static final int MAX_LINES = 12;

	private final Executor executor;
	private final TextField input;
	private final Label log;
	private final Array<String> lines = new Array<>();
	private final Array<String> history = new Array<>();
	private int historyIndex;
	private boolean open;
	/** The Cerrar button was tapped; the HUD closes the console on its next update. */
	private boolean closeRequested;

	public Console(Executor executor) {
		this.executor = executor;
		input = new TextField("", Assets.skin);
		// The console key itself (` or º) must not end up in the line.
		input.setTextFieldFilter((field, c) -> c != '`' && c != 'º' && c != 'ª' && c != '\\');
		input.setTextFieldListener((field, c) -> {
			if (c == '\r' || c == '\n')
				submit();
		});
		log = new Label("", Assets.skin);
		log.setWrap(true);
		log.setAlignment(com.badlogic.gdx.utils.Align.bottomLeft);

		Drawable background = Assets.skin.newDrawable("white", new Color(0, 0, 0, 0.72f));
		Table panel = new Table();
		panel.setBackground(background);
		panel.pad(10);
		// Phones have no Esc key (and iPhones no back button): the console closes with a button.
		TextButton close = new TextButton(Text.get("close"), Assets.skin);
		close.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				closeRequested = true;
			}
		});
		panel.add(log).colspan(2).growX().left().bottom().height(MAX_LINES * 20).row();
		panel.add(input).growX().height(32).padTop(6);
		panel.add(close).width(110).height(32).padTop(6).padLeft(8);
		panel.setBounds(0, Core.VIRTUAL_HEIGHT - 320, Core.VIRTUAL_WIDTH, 320);
		stage.addActor(panel);
		print(Text.get("console.welcome", Platform.touch ? Text.get("close") : "Esc"));
	}

	public boolean isOpen() {
		return open;
	}

	/** Shows the console and takes the keyboard. */
	public void open() {
		open = true;
		closeRequested = false;
		input.setText("");
		Gdx.input.setInputProcessor(stage);
		stage.setKeyboardFocus(input);
		if (Platform.touch)
			Gdx.input.setOnscreenKeyboardVisible(true);
	}

	/** True once after the Cerrar button is tapped. */
	public boolean consumeCloseRequest() {
		boolean requested = closeRequested;
		closeRequested = false;
		return requested;
	}

	/** Hides it; the caller gives the keyboard back to whoever had it. */
	public void close() {
		open = false;
		stage.setKeyboardFocus(null);
		if (Platform.touch)
			Gdx.input.setOnscreenKeyboardVisible(false);
	}

	/** Up and down arrows walk through the commands typed before. */
	private void browseHistory() {
		int step = Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.UP) ? -1
				: Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.DOWN) ? 1 : 0;
		if (step == 0 || history.size == 0)
			return;
		historyIndex = Math.max(0, Math.min(history.size, historyIndex + step));
		input.setText(historyIndex < history.size ? history.get(historyIndex) : "");
		input.setCursorPosition(input.getText().length());
	}

	private void submit() {
		String line = input.getText().trim();
		input.setText("");
		if (line.isEmpty())
			return;
		history.add(line);
		historyIndex = history.size;
		print("> " + line);
		if (line.equalsIgnoreCase("limpiar") || line.equalsIgnoreCase("clear")) {
			lines.clear();
			log.setText("");
			return;
		}
		String reply = executor.execute(line);
		if (reply != null)
			print(reply);
	}

	public void print(String text) {
		for (String line : text.split("\n"))
			lines.add(line);
		while (lines.size > MAX_LINES)
			lines.removeIndex(0);
		log.setText(String.join("\n", lines));
	}

	public void draw(float delta) {
		if (!open)
			return;
		browseHistory();
		stage.getViewport().apply();
		stage.act(delta);
		stage.draw();
	}
}
