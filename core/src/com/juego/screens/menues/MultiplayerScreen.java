package com.juego.screens.menues;

import java.io.IOException;
import java.net.BindException;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Array;
import com.juego.bots.BotDifficulty;
import com.juego.core.Assets;
import com.juego.core.Core;
import com.juego.core.GameClock;
import com.juego.core.GameSession;
import com.juego.net.GameClient;
import com.juego.net.GameServer;
import com.juego.net.Messages;
import com.juego.net.Protocol;
import com.juego.screens.GameScreen;
import com.juego.utils.Config;

/**
 * Starts a match. "Un jugador" runs a private server with bots inside the
 * game, "Crear partida" runs one others can join (with or without bots), and
 * "Unirse a partida" connects to a host or a dedicated server by IP. Either
 * way, the match starts once the client is in.
 */
public class MultiplayerScreen extends MenuScreen {
	private final GameSession.Kind kind;
	private final TextField nameField, hostField, portField;
	private final Slider botsSlider;
	private final Label botsValue;
	private final SelectBox<String> difficultyBox;
	private final TextButton startButton;
	private final Label statusLabel;
	/** Owned by this screen until the match starts, then handed to the GameSession. */
	private GameClient client;
	private GameClock clock;
	private boolean serverStarted;

	public MultiplayerScreen(Core game, GameSession.Kind kind) {
		super(game);
		this.kind = kind;
		boolean singleplayer = kind == GameSession.Kind.SINGLEPLAYER, hosting = kind == GameSession.Kind.HOST;

		Image background = new Image(loadTexture("mainMenu/background.png"));
		background.setFillParent(true);
		stage.addActor(background);

		nameField = new TextField(Config.playerName, Assets.skin);
		nameField.setMaxLength(Protocol.MAX_NAME_LENGTH);
		hostField = new TextField("127.0.0.1", Assets.skin);
		portField = new TextField(Integer.toString(Protocol.DEFAULT_PORT), Assets.skin);
		portField.setTextFieldFilter(new TextField.TextFieldFilter.DigitsOnlyFilter());
		botsSlider = new Slider(singleplayer ? 1 : 0, GameServer.MAX_BOTS, 1, false, Assets.skin);
		botsSlider.setValue(singleplayer ? Config.singleplayerBots : Config.hostBots);
		botsValue = new Label("", Assets.skin);
		difficultyBox = new SelectBox<>(Assets.skin);
		Array<String> difficulties = new Array<>();
		for (BotDifficulty d : BotDifficulty.values())
			difficulties.add(d.label);
		difficultyBox.setItems(difficulties);
		difficultyBox.setSelectedIndex(Config.botDifficulty.ordinal());
		startButton = new TextButton(singleplayer ? "Jugar" : hosting ? "Crear" : "Conectar", Assets.skin);
		TextButton backButton = new TextButton("Volver", Assets.skin);
		statusLabel = new Label("", Assets.skin);
		statusLabel.setWrap(true);

		Table table = new Table();
		table.setFillParent(true);
		Label title = new Label(singleplayer ? "Un jugador" : hosting ? "Crear partida" : "Unirse a partida",
				Assets.skin);
		title.setFontScale(2f);
		table.add(title).colspan(3).padBottom(30).row();
		row(table, "Nombre", nameField, null);
		if (kind == GameSession.Kind.JOIN)
			row(table, "IP del servidor", hostField, null);
		if (!singleplayer)
			row(table, "Puerto UDP", portField, null);
		if (kind != GameSession.Kind.JOIN) {
			row(table, "Bots", botsSlider, botsValue);
			row(table, "Dificultad", difficultyBox, null);
		}
		table.add().height(10).row();
		table.add(startButton).colspan(3).width(256).height(56).padBottom(10).row();
		table.add(backButton).colspan(3).width(256).height(56).padBottom(20).row();
		table.add(statusLabel).colspan(3).width(560).row();
		String hintText = singleplayer
				? "Deathmatch contra bots. Durante la partida, la consola (tecla a la izquierda del 1) agrega o saca bots y cambia la dificultad."
				: hosting ? "Deathmatch hasta " + Protocol.MAX_PLAYERS
						+ " personas, mas los bots. Los demas se unen con tu IP (se muestra al empezar)." : null;
		if (hintText != null) {
			Label hint = new Label(hintText, Assets.skin);
			hint.setWrap(true);
			hint.setColor(Color.LIGHT_GRAY);
			table.add(hint).colspan(3).width(560).padTop(10);
		}
		stage.addActor(table);
		stage.setKeyboardFocus(nameField);
		showBots();

		botsSlider.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				showBots();
			}
		});
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

	private static void row(Table table, String name, Actor widget, Label value) {
		table.add(new Label(name, Assets.skin)).right().padRight(12);
		table.add(widget).width(280).height(36).padBottom(10);
		if (value != null)
			table.add(value).width(40).padLeft(10).padBottom(10).left();
		else
			table.add().width(40);
		table.row();
	}

	private void showBots() {
		int bots = (int) botsSlider.getValue();
		botsValue.setText(bots);
		difficultyBox.setDisabled(bots == 0);
	}

	private void start() {
		if (client != null)
			return; // already connecting
		int port = 0;
		if (kind != GameSession.Kind.SINGLEPLAYER) {
			try {
				port = Integer.parseInt(portField.getText());
				if (port < 1 || port > 65535)
					throw new NumberFormatException();
			} catch (NumberFormatException e) {
				showError("El puerto tiene que ser un numero entre 1 y 65535");
				return;
			}
		}
		String name = Messages.sanitizeName(nameField.getText());
		String host = kind == GameSession.Kind.JOIN ? hostField.getText().trim() : "127.0.0.1";
		int bots = (int) botsSlider.getValue();
		BotDifficulty difficulty = BotDifficulty.values()[difficultyBox.getSelectedIndex()];
		clock = new GameClock();
		try {
			if (kind != GameSession.Kind.JOIN) {
				GameServer.Config config = new GameServer.Config();
				config.port = port; // 0 in singleplayer: any free port, only reachable from this computer
				config.localOnly = kind == GameSession.Kind.SINGLEPLAYER;
				config.bots = bots;
				config.botDifficulty = difficulty;
				game.startHostedServer(config, clock);
				serverStarted = true;
				port = game.getHostedPort();
			}
			client = new GameClient(clock);
			client.connect(host, port, name);
			Config.playerName = name;
			if (kind == GameSession.Kind.SINGLEPLAYER)
				Config.singleplayerBots = bots;
			else if (kind == GameSession.Kind.HOST)
				Config.hostBots = bots;
			if (kind != GameSession.Kind.JOIN)
				Config.botDifficulty = difficulty;
			showStatus(kind == GameSession.Kind.JOIN ? "Conectando a " + host + ":" + port + "..."
					: bots > 0 ? "Preparando la partida y los bots..." : "Creando partida...");
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
				game.setScreen(new GameScreen(game, new GameSession(client, clock, kind)));
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
		Config.save();
		super.dispose();
	}
}
