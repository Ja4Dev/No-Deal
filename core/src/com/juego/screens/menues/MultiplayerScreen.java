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
import com.juego.match.GameMode;
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
import com.juego.utils.Text;

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
	private final Slider botsSlider, timeSlider, fragSlider;
	private final Label botsValue, timeValue, fragValue;
	private final SelectBox<String> difficultyBox;
	/** Everyone against everyone, or teams (singleplayer and hosting). */
	private final SelectBox<String> modeBox;
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
		timeSlider = new Slider(1, Config.MAX_TIME_LIMIT_MINUTES, 1, false, Assets.skin);
		timeSlider.setValue(Config.timeLimitMinutes);
		timeValue = new Label("", Assets.skin);
		fragSlider = new Slider(0, Config.MAX_FRAG_LIMIT, 1, false, Assets.skin);
		fragSlider.setValue(Config.fragLimit);
		fragValue = new Label("", Assets.skin);
		difficultyBox = new SelectBox<>(Assets.skin);
		Array<String> difficulties = new Array<>();
		for (BotDifficulty d : BotDifficulty.values())
			difficulties.add(d.label());
		difficultyBox.setItems(difficulties);
		difficultyBox.setSelectedIndex(Config.botDifficulty.ordinal());
		modeBox = new SelectBox<>(Assets.skin);
		Array<String> modes = new Array<>();
		for (GameMode mode : GameMode.values())
			modes.add(mode.label());
		modeBox.setItems(modes);
		modeBox.setSelectedIndex(Config.gameMode.ordinal());
		startButton = new TextButton(Text.get(singleplayer ? "mp.play" : hosting ? "mp.create" : "mp.connect"), Assets.skin);
		backButton = new TextButton(Text.get("back"), Assets.skin);
		statusLabel = new Label("", Assets.skin);
		statusLabel.setWrap(true);

		if (!singleplayer && game.hasBluetooth()) {
			networkBox = new SelectBox<>(Assets.skin);
			networkBox.setItems("Wi-Fi", "Bluetooth");
			deviceList = new List<>(Assets.skin);
			deviceScroll = new ScrollPane(deviceList, Assets.skin);
			deviceScroll.setFadeScrollBars(false);
			scanButton = new TextButton(Text.get("mp.search"), Assets.skin);
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
		showLimits();

		botsSlider.addListener(new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				showBots();
			}
		});
		ChangeListener limits = new ChangeListener() {
			@Override
			public void changed(ChangeEvent event, Actor actor) {
				showLimits();
			}
		};
		timeSlider.addListener(limits);
		fragSlider.addListener(limits);
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
		Label title = new Label(Text.get(singleplayer ? "menu.singleplayer" : hosting ? "menu.host" : "menu.join"),
				Assets.skin);
		title.setFontScale(2f);
		table.add(title).colspan(3).padBottom(16).row();
		row(table, Text.get("mp.name"), nameField, null);
		if (networkBox != null)
			row(table, Text.get("mp.connection"), networkBox, null);
		if (joining && bluetooth) {
			table.add(new Label(Text.get("mp.phone"), Assets.skin)).right().top().padRight(12);
			table.add(deviceScroll).width(280).height(130).padBottom(10);
			table.add(scanButton).width(90).height(36).padLeft(10).top();
			table.row();
		} else {
			if (joining)
				row(table, Text.get("mp.serverIp"), hostField, null);
			if (!singleplayer)
				row(table, Text.get("mp.port"), portField, null);
		}
		if (!joining) {
			row(table, Text.get("mp.mode"), modeBox, null);
			row(table, Text.get("mp.bots"), botsSlider, botsValue);
			row(table, Text.get("mp.difficulty"), difficultyBox, null);
			row(table, Text.get("mp.duration"), timeSlider, timeValue);
			row(table, Text.get("mp.fragLimit"), fragSlider, fragValue);
		}
		table.add().height(10).row();
		table.add(startButton).colspan(3).width(256).height(56).padBottom(10).row();
		table.add(backButton).colspan(3).width(256).height(56).padBottom(20).row();
		table.add(statusLabel).colspan(3).width(560).row();
		String hintText;
		if (singleplayer)
			hintText = Text.get("mp.hint.singleplayer",
					Text.get(Platform.touch ? "mp.consoleWhere.touch" : "mp.consoleWhere.key"));
		else if (hosting && bluetooth)
			hintText = Text.get("mp.hint.hostBluetooth", Protocol.MAX_PLAYERS, game.bluetooth.localName());
		else if (hosting)
			hintText = Text.get("mp.hint.host", Protocol.MAX_PLAYERS);
		else if (bluetooth)
			hintText = Text.get("mp.hint.joinBluetooth");
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
		table.add(widget).width(280).height(34).padBottom(8);
		if (value != null)
			table.add(value).width(100).padLeft(10).padBottom(8).left();
		else
			table.add().width(100);
		table.row();
	}

	private void showLimits() {
		timeValue.setText(Text.get("mp.minutes", (int) timeSlider.getValue()));
		int frags = (int) fragSlider.getValue();
		fragValue.setText(frags == 0 ? Text.get("mp.noLimit") : Integer.toString(frags));
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
		showStatus(Text.get("mp.preparingBluetooth"));
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
				showStatus(Text.get("mp.scanning"));
				game.bluetooth.startScan(device -> {
					if (disposed || devices.contains(device, false))
						return;
					devices.add(device);
					Array<String> items = new Array<>();
					for (Bluetooth.Device d : devices)
						items.add(d.name + (d.paired ? Text.get("mp.paired") : ""));
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
			showError(Text.get("mp.choosePhone"));
			return;
		}
		final Bluetooth.Device device = devices.get(index);
		stopScan();
		busy = true;
		startButton.setDisabled(true);
		showStatus(Text.get("mp.connectingBluetooth", device.name));
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
				showStatus(Text.get("mp.enteringMatch", device.name));
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
		showStatus(Text.get("mp.preparingBluetooth"));
		game.bluetooth.prepare(true, new Bluetooth.Callback<Void>() {
			@Override
			public void ready(Void result) {
				busy = false;
				if (disposed)
					return;
				try {
					bluetoothHost = game.bluetooth.host();
				} catch (IOException e) {
					showError(Text.get("mp.bluetoothFailed", e.getMessage()));
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
				showError(Text.get("mp.badPort"));
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
				config.timeLimitMinutes = (int) timeSlider.getValue();
				config.fragLimit = (int) fragSlider.getValue();
				config.mode = GameMode.values()[modeBox.getSelectedIndex()];
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
			if (kind != GameSession.Kind.JOIN) {
				Config.botDifficulty = difficulty;
				Config.timeLimitMinutes = (int) timeSlider.getValue();
				Config.fragLimit = (int) fragSlider.getValue();
				Config.gameMode = GameMode.values()[modeBox.getSelectedIndex()];
			}
			showStatus(kind == GameSession.Kind.JOIN ? Text.get("mp.connectingTo", host, port)
					: Text.get(bots > 0 ? "mp.preparingBots" : "mp.creating"));
			startButton.setDisabled(true);
		} catch (BindException e) {
			showError(Text.get("mp.portInUse", port));
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
