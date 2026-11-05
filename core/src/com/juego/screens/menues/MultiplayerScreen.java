package com.juego.screens.menues;

import java.io.IOException;
import java.net.BindException;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.juego.core.Assets;
import com.juego.core.Core;
import com.juego.core.GameSession;
import com.juego.net.GameClient;
import com.juego.net.Messages;
import com.juego.net.Protocol;
import com.juego.screens.GameScreen;
import com.juego.utils.Config;

/**
 * "Crear partida" (host: starts a server inside this game and joins it) and
 * "Unirse a partida" (join a host or a dedicated server by IP).
 */
public class MultiplayerScreen extends MenuScreen {
	private final boolean hosting;
	private final TextField nameField, hostField, portField;
	private final TextButton startButton;
	private final Label statusLabel;
	/** Owned by this screen until the match starts, then handed to the GameSession. */
	private GameClient client;
	private boolean serverStarted;

	public MultiplayerScreen(Core game, boolean hosting) {
		super(game);
		this.hosting = hosting;

		Image background = new Image(loadTexture("mainMenu/background.png"));
		background.setFillParent(true);
		stage.addActor(background);

		nameField = new TextField(Config.playerName, Assets.skin);
		nameField.setMaxLength(Protocol.MAX_NAME_LENGTH);
		hostField = new TextField("127.0.0.1", Assets.skin);
		portField = new TextField(Integer.toString(Protocol.DEFAULT_PORT), Assets.skin);
		portField.setTextFieldFilter(new TextField.TextFieldFilter.DigitsOnlyFilter());
		startButton = new TextButton(hosting ? "Crear" : "Conectar", Assets.skin);
		TextButton backButton = new TextButton("Volver", Assets.skin);
		statusLabel = new Label("", Assets.skin);
		statusLabel.setWrap(true);

		Table table = new Table();
		table.setFillParent(true);
		Label title = new Label(hosting ? "Crear partida" : "Unirse a partida", Assets.skin);
		title.setFontScale(2f);
		table.add(title).colspan(2).padBottom(30).row();
		table.add(new Label("Nombre", Assets.skin)).right().padRight(12);
		table.add(nameField).width(280).height(36).padBottom(10).row();
		if (!hosting) {
			table.add(new Label("IP del servidor", Assets.skin)).right().padRight(12);
			table.add(hostField).width(280).height(36).padBottom(10).row();
		}
		table.add(new Label("Puerto UDP", Assets.skin)).right().padRight(12);
		table.add(portField).width(280).height(36).padBottom(20).row();
		table.add(startButton).colspan(2).width(256).height(56).padBottom(10).row();
		table.add(backButton).colspan(2).width(256).height(56).padBottom(20).row();
		table.add(statusLabel).colspan(2).width(560).row();
		if (hosting) {
			Label hint = new Label("Deathmatch hasta " + Protocol.MAX_PLAYERS
					+ " jugadores. Los demas se unen con tu IP (se muestra al empezar).", Assets.skin);
			hint.setWrap(true);
			hint.setColor(Color.LIGHT_GRAY);
			table.add(hint).colspan(2).width(560).padTop(10);
		}
		stage.addActor(table);
		stage.setKeyboardFocus(nameField);

		startButton.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				start();
			}
		});
		backButton.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				game.setScreen(new MainMenuScreen(game));
			}
		});
	}

	private void start() {
		if (client != null)
			return; // already connecting
		int port;
		try {
			port = Integer.parseInt(portField.getText());
			if (port < 1 || port > 65535)
				throw new NumberFormatException();
		} catch (NumberFormatException e) {
			showError("El puerto tiene que ser un numero entre 1 y 65535");
			return;
		}
		String name = Messages.sanitizeName(nameField.getText());
		String host = hosting ? "127.0.0.1" : hostField.getText().trim();
		try {
			if (hosting) {
				game.startHostedServer(port);
				serverStarted = true;
			}
			client = new GameClient(System::currentTimeMillis);
			client.connect(host, port, name);
			Config.playerName = name;
			showStatus(hosting ? "Creando partida..." : "Conectando a " + host + ":" + port + "...");
			startButton.setDisabled(true);
		} catch (BindException e) {
			showError("No se pudo abrir el puerto " + port + ": ya esta en uso");
			cleanUp();
		} catch (IOException | IllegalArgumentException e) {
			showError(e.getMessage() != null ? e.getMessage() : e.toString());
			cleanUp();
		}
	}

	@Override
	public void render(float delta) {
		if (client != null) {
			client.update();
			switch (client.getStatus()) {
			case CONNECTED:
				game.musicPlayer.stop();
				game.setScreen(new GameScreen(game, GameSession.multiplayer(client, hosting)));
				client = null; // the session owns it now
				serverStarted = false; // and the match will stop the server when it ends
				break;
			case REJECTED:
			case DISCONNECTED:
				showError(client.getFailureReason());
				cleanUp();
				break;
			default:
				break;
			}
		}
		super.render(delta);
	}

	private void showStatus(String text) {
		statusLabel.setColor(Color.WHITE);
		statusLabel.setText(text);
	}

	private void showError(String text) {
		statusLabel.setColor(1f, 0.4f, 0.4f, 1f);
		statusLabel.setText(text);
	}

	private void cleanUp() {
		if (client != null) {
			client.dispose();
			client = null;
		}
		if (serverStarted) {
			game.stopHostedServer();
			serverStarted = false;
		}
		startButton.setDisabled(false);
	}

	@Override
	public void dispose() {
		cleanUp();
		super.dispose();
	}
}
