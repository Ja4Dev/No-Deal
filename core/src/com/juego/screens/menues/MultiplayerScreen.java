package com.juego.screens.menues;

import java.io.IOException;
import java.net.BindException;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.List;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
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
import com.juego.net.Bluetooth;
import com.juego.net.GameClient;
import com.juego.net.GameServer;
import com.juego.net.LinkTransport;
import com.juego.net.Messages;
import com.juego.net.Protocol;
import com.juego.screens.GameScreen;
import com.juego.utils.Config;
import com.juego.utils.Platform;

/**
 * Starts a match. "Un jugador" runs a private server with bots inside the
 * game, "Crear partida" runs one others can join (with or without bots), and
 * "Unirse a partida" connects to a host or a dedicated server by IP. Either
 * way, the match starts once the client is in.
 * <p>
 * On phones with Bluetooth, hosting and joining can also go over Bluetooth:
 * the host's phone listens (and still takes Wi-Fi players), and joining shows
 * the phones nearby instead of asking for an IP.
 */
public class MultiplayerScreen extends MenuScreen {
	private static final int WIFI = 0, BLUETOOTH = 1;

	private final GameSession.Kind kind;
	private final TextField nameField, hostField, portField;
	private final Slider botsSlider;
	private final Label botsValue;
	private final SelectBox<String> difficultyBox;
	private final TextButton startButton, backButton;
	private final Label statusLabel;
	private final Table table = new Table();
	/** Wi-Fi or Bluetooth; null where there is no Bluetooth (and in singleplayer). */
	private final SelectBox<String> networkBox;
	/** Phones to join over Bluetooth, in the order of {@link #devices}. */
	private final List<String> deviceList;
	private final ScrollPane deviceScroll;
	private final TextButton scanButton;
	private final Array<Bluetooth.Device> devices = new Array<>();
	private boolean scanning;
	/** Asking for Bluetooth permissions, or connecting: one thing at a time. */
	private boolean busy;
	private boolean disposed;
	/** Listening for phones while the hosted server starts; the server owns it once it has it. */
	private LinkTransport bluetoothHost;
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
		backButton = new TextButton("Volver", Assets.skin);
		statusLabel = new Label("", Assets.skin);
		statusLabel.setWrap(true);

		if (!singleplayer && game.hasBluetooth()) {
			networkBox = new SelectBox<>(Assets.skin);
			networkBox.setItems("Wi-Fi", "Bluetooth");
			deviceList = new List<>(Assets.skin);
			deviceScroll = new ScrollPane(deviceList, Assets.skin);
			deviceScroll.setFadeScrollBars(false);
			scanButton = new TextButton("Buscar", Assets.skin);
		} else {
			networkBox = null;
			deviceList = null;
			deviceScroll = null;
			scanButton = null;
		}

		table.setFillParent(true);
		stage.addActor(table);
		buildTable();
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
		setBackButton(backButton);
		backButton.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				game.setScreen(new MainMenuScreen(game));
			}
		});
		if (networkBox != null) {
			networkBox.addListener(new ChangeListener() {
				@Override
				public void changed(ChangeEvent event, Actor actor) {
					networkChanged();
				}
			});
			scanButton.addListener(new ClickListener() {
				@Override
				public void clicked(InputEvent event, float x, float y) {
					scan();
				}
			});
		}
	}

	private boolean overBluetooth() {
		return networkBox != null && networkBox.getSelectedIndex() == BLUETOOTH;
	}

	/** Lays the form out for the kind of match and the network chosen. */
	private void buildTable() {
		boolean singleplayer = kind == GameSession.Kind.SINGLEPLAYER, hosting = kind == GameSession.Kind.HOST;
		boolean joining = kind == GameSession.Kind.JOIN, bluetooth = overBluetooth();
		table.clearChildren();
		Label title = new Label(singleplayer ? "Un jugador" : hosting ? "Crear partida" : "Unirse a partida",
				Assets.skin);
		title.setFontScale(2f);
		table.add(title).colspan(3).padBottom(24).row();
		row(table, "Nombre", nameField, null);
		if (networkBox != null)
			row(table, "Conexion", networkBox, null);
		if (joining && bluetooth) {
			table.add(new Label("Telefono", Assets.skin)).right().top().padRight(12);
			table.add(deviceScroll).width(280).height(130).padBottom(10);
			table.add(scanButton).width(90).height(36).padLeft(10).top();
			table.row();
		} else {
			if (joining)
				row(table, "IP del servidor", hostField, null);
			if (!singleplayer)
				row(table, "Puerto UDP", portField, null);
		}
		if (!joining) {
			row(table, "Bots", botsSlider, botsValue);
			row(table, "Dificultad", difficultyBox, null);
		}
		table.add().height(10).row();
		table.add(startButton).colspan(3).width(256).height(56).padBottom(10).row();
		table.add(backButton).colspan(3).width(256).height(56).padBottom(20).row();
		table.add(statusLabel).colspan(3).width(560).row();
		String hintText;
		if (singleplayer)
			hintText = "Deathmatch contra bots. Durante la partida, la consola ("
					+ (Platform.touch ? "boton >_ arriba a la derecha" : "tecla a la izquierda del 1")
					+ ") agrega o saca bots y cambia la dificultad.";
		else if (hosting && bluetooth)
			hintText = "Deathmatch hasta " + Protocol.MAX_PLAYERS + " personas, mas los bots. Los demas eligen \""
					+ game.bluetooth.localName() + "\" en Unirse a partida, con Conexion: Bluetooth"
					+ " (por Wi-Fi tambien se pueden unir con tu IP).";
		else if (hosting)
			hintText = "Deathmatch hasta " + Protocol.MAX_PLAYERS
					+ " personas, mas los bots. Los demas se unen con tu IP (se muestra al empezar).";
		else if (bluetooth)
			hintText = "Elegi el telefono de quien creo la partida. Si no aparece, que toque Crear otra vez:"
					+ " su telefono queda visible unos minutos.";
		else
			hintText = null;
		if (hintText != null) {
			Label hint = new Label(hintText, Assets.skin);
			hint.setWrap(true);
			hint.setColor(Color.LIGHT_GRAY);
			table.add(hint).colspan(3).width(560).padTop(6);
		}
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

	private void networkChanged() {
		buildTable();
		statusLabel.setText("");
		if (kind == GameSession.Kind.JOIN) {
			if (overBluetooth())
				scan();
			else
				stopScan();
		}
	}

	// ---- Bluetooth ----

	/** Gets Bluetooth ready, then lists the paired phones and looks for nearby ones. */
	private void scan() {
		if (busy)
			return;
		busy = true;
		showStatus("Preparando Bluetooth...");
		game.bluetooth.prepare(false, new Bluetooth.Callback<Void>() {
			@Override
			public void ready(Void result) {
				busy = false;
				if (disposed || !overBluetooth())
					return;
				stopScan();
				devices.clear();
				deviceList.clearItems();
				scanning = true;
				showStatus("Buscando telefonos cerca...");
				game.bluetooth.startScan(device -> {
					if (disposed || devices.contains(device, false))
						return;
					devices.add(device);
					Array<String> items = new Array<>();
					for (Bluetooth.Device d : devices)
						items.add(d.name + (d.paired ? " (vinculado)" : ""));
					int selected = deviceList.getSelectedIndex();
					deviceList.setItems(items);
					deviceList.setSelectedIndex(Math.max(0, selected));
				});
			}

			@Override
			public void failed(String reason) {
				busy = false;
				if (!disposed)
					showError(reason);
			}
		});
	}

	private void stopScan() {
		if (scanning) {
			game.bluetooth.stopScan();
			scanning = false;
		}
	}

	/** Joins the phone chosen in the list. */
	private void joinOverBluetooth(final String name) {
		int index = deviceList.getSelectedIndex();
		if (index < 0 || index >= devices.size) {
			showError("Elegi el telefono de quien creo la partida (toca Buscar si no aparece).");
			return;
		}
		final Bluetooth.Device device = devices.get(index);
		stopScan();
		busy = true;
		startButton.setDisabled(true);
		showStatus("Conectando por Bluetooth con " + device.name + "...");
		game.bluetooth.connect(device, new Bluetooth.Callback<Bluetooth.Joined>() {
			@Override
			public void ready(Bluetooth.Joined joined) {
				busy = false;
				if (disposed) {
					joined.transport.close();
					return;
				}
				clock = new GameClock();
				client = new GameClient(clock);
				client.connect(joined.transport, joined.host, name);
				Config.playerName = name;
				showStatus("Entrando a la partida de " + device.name + "...");
			}

			@Override
			public void failed(String reason) {
				busy = false;
				if (disposed)
					return;
				showError(reason);
				startButton.setDisabled(false);
			}
		});
	}

	/** Asks for Bluetooth (and visibility), starts listening, then creates the match as usual. */
	private void hostOverBluetooth() {
		busy = true;
		startButton.setDisabled(true);
		showStatus("Preparando Bluetooth...");
		game.bluetooth.prepare(true, new Bluetooth.Callback<Void>() {
			@Override
			public void ready(Void result) {
				busy = false;
				if (disposed)
					return;
				try {
					bluetoothHost = game.bluetooth.host();
				} catch (IOException e) {
					showError("No se pudo abrir el Bluetooth: " + e.getMessage());
					startButton.setDisabled(false);
					return;
				}
				buildTable(); // the hint now knows this phone's Bluetooth name
				startOverNetwork();
			}

			@Override
			public void failed(String reason) {
				busy = false;
				if (disposed)
					return;
				showError(reason);
				startButton.setDisabled(false);
			}
		});
	}

	// ---- Starting ----

	private void start() {
		if (client != null || busy)
			return; // already connecting
		if (overBluetooth()) {
			if (kind == GameSession.Kind.JOIN)
				joinOverBluetooth(Messages.sanitizeName(nameField.getText()));
			else
				hostOverBluetooth();
			return;
		}
		startOverNetwork();
	}

	private void startOverNetwork() {
		int port = 0;
		if (kind != GameSession.Kind.SINGLEPLAYER) {
			try {
				port = Integer.parseInt(portField.getText());
				if (port < 1 || port > 65535)
					throw new NumberFormatException();
			} catch (NumberFormatException e) {
				showError("El puerto tiene que ser un numero entre 1 y 65535");
				cleanUp();
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
				if (bluetoothHost != null) {
					game.getHostedServer().addTransport(bluetoothHost); // phones over Bluetooth join it too
					bluetoothHost = null;
				}
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
			client.dispose(); // also closes a Bluetooth link to the host
			client = null;
		}
		if (serverStarted) {
			game.stopHostedServer(); // also stops listening on Bluetooth
			serverStarted = false;
		}
		if (bluetoothHost != null) {
			bluetoothHost.close();
			bluetoothHost = null;
		}
		startButton.setDisabled(false);
	}

	@Override
	public void dispose() {
		disposed = true;
		stopScan();
		cleanUp();
		Config.save();
		super.dispose();
	}
}
