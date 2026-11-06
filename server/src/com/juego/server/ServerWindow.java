package com.juego.server;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import com.badlogic.gdx.utils.Array;
import com.juego.bots.BotDifficulty;
import com.juego.net.GameServer;
import com.juego.net.GameServer.EventType;
import com.juego.net.GameServer.PlayerInfo;

/**
 * Console-style window for the dedicated server: choose the port and match
 * rules, start and stop the server, see the addresses to join, the players and
 * a colored log of what happens (joins, kills, matches). There is also a
 * command line at the bottom.
 *
 * Everything here runs on the Swing thread; the server runs on the libGDX
 * thread inside {@link ServerHost}, and the two only talk through
 * {@link ServerHost#post} and the {@link ServerHost.View} callbacks.
 */
public class ServerWindow implements ServerHost.View {
	private static final int MAX_LOG_LINES = 2000;

	private static final Color BACKGROUND = new Color(0x12, 0x14, 0x16);
	private static final Color PANEL = new Color(0x1b, 0x1e, 0x21);
	private static final Color FIELD = new Color(0x26, 0x2a, 0x2e);
	private static final Color BORDER = new Color(0x33, 0x38, 0x3d);
	private static final Color TEXT = new Color(0xd6, 0xd6, 0xd6);
	private static final Color DIM = new Color(0x80, 0x86, 0x8c);
	private static final Color GREEN = new Color(0x5f, 0xd0, 0x6e);
	private static final Color RED = new Color(0xff, 0x6b, 0x5e);
	private static final Color YELLOW = new Color(0xf0, 0xc0, 0x4a);
	private static final Color CYAN = new Color(0x5c, 0xc8, 0xe6);
	private static final Color ORANGE = new Color(0xff, 0x9f, 0x43);
	private static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 13);
	private static final Font MONO_BOLD = MONO.deriveFont(Font.BOLD);

	private static final String PREF_PORT = "port", PREF_PLAYERS = "maxPlayers", PREF_TIME = "timeLimit",
			PREF_FRAGS = "fragLimit", PREF_BOTS = "bots", PREF_BOT_DIFFICULTY = "botDifficulty";

	private final ServerHost host;
	private final Preferences prefs = Preferences.userRoot().node("nodeal/server");
	private final JFrame frame = new JFrame("No Deal - Servidor");
	private final JSpinner portField, playersField, timeField, fragsField, botsField;
	private final JComboBox<String> difficultyField = new JComboBox<>();
	private final JButton startButton = button("Iniciar servidor");
	private final JButton copyButton = button("Copiar IP");
	private final JButton publicIpButton = button("Ver IP pública");
	private final JLabel stateLabel = label("", MONO_BOLD, DIM);
	private final JLabel addressLabel = label("", MONO, TEXT);
	private final JLabel matchLabel = label(" ", MONO_BOLD, TEXT);
	private final JTextPane log = new JTextPane();
	private final Map<EventType, SimpleAttributeSet> styles = new EnumMap<>(EventType.class);
	private final SimpleAttributeSet timeStyle = style(DIM);
	private final PlayersModel players = new PlayersModel();
	private final JTextField commandField = new JTextField();

	private ServerHost.Status status;
	/** Start/stop requests sent to the host; until it handled them all, the buttons wait. */
	private long requests;
	private boolean waiting;

	/** Opens the window and the server loop. With {@code autoStart} the server starts with {@code config}. */
	public static void open(GameServer.Config config, boolean autoStart) {
		SwingUtilities.invokeLater(() -> {
			ServerWindow window = new ServerWindow(config, autoStart);
			window.frame.setVisible(true);
			window.commandField.requestFocusInWindow();
		});
	}

	/**
	 * Builds the window and starts the server loop. Settings passed on the
	 * command line win over the ones saved last time.
	 */
	ServerWindow(GameServer.Config config, boolean optionsGiven) {
		if (!optionsGiven) {
			config.port = prefs.getInt(PREF_PORT, config.port);
			config.maxPlayers = prefs.getInt(PREF_PLAYERS, config.maxPlayers);
			config.timeLimitMinutes = prefs.getInt(PREF_TIME, config.timeLimitMinutes);
			config.fragLimit = prefs.getInt(PREF_FRAGS, config.fragLimit);
			config.bots = prefs.getInt(PREF_BOTS, config.bots);
			BotDifficulty difficulty = BotDifficulty.parse(prefs.get(PREF_BOT_DIFFICULTY, ""));
			if (difficulty != null)
				config.botDifficulty = difficulty;
			if (DedicatedServer.validate(config) != null)
				config = new GameServer.Config(); // corrupt preferences: back to defaults
		}
		portField = spinner(config.port, 1, 65535, 6);
		playersField = spinner(config.maxPlayers, 1, 32, 3);
		timeField = spinner(config.timeLimitMinutes, 1, 120, 4);
		fragsField = spinner(config.fragLimit, 0, 999, 4);
		botsField = spinner(config.bots, 0, GameServer.MAX_BOTS, 3);
		for (BotDifficulty d : BotDifficulty.values())
			difficultyField.addItem(d.label);
		difficultyField.setSelectedIndex(config.botDifficulty.ordinal());
		difficultyField.setFont(MONO);
		difficultyField.setBackground(FIELD);
		difficultyField.setForeground(TEXT);

		styles.put(EventType.INFO, style(TEXT));
		styles.put(EventType.JOIN, style(GREEN));
		styles.put(EventType.LEAVE, style(YELLOW));
		styles.put(EventType.KILL, style(ORANGE));
		styles.put(EventType.MATCH, style(CYAN));
		styles.put(EventType.WARNING, style(RED));

		buildLayout();
		host = new ServerHost(this);
		host.setOnDisposed(() -> SwingUtilities.invokeLater(() -> {
			frame.dispose();
			System.exit(0);
		}));
		DedicatedServer.launch(host, this);

		append(EventType.INFO, "No Deal - servidor dedicado. Elegí el puerto y las reglas, y tocá \"Iniciar servidor\".");
		append(EventType.INFO, "Escribí 'ayuda' abajo para ver los comandos.");
		if (optionsGiven)
			start();
	}

	// ---- layout ----

	private void buildLayout() {
		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				quit();
			}
		});

		JPanel settings = row();
		settings.add(caption("Puerto UDP"));
		settings.add(portField);
		settings.add(Box.createHorizontalStrut(10));
		settings.add(caption("Jugadores"));
		settings.add(playersField);
		settings.add(Box.createHorizontalStrut(10));
		settings.add(caption("Minutos"));
		settings.add(timeField);
		settings.add(Box.createHorizontalStrut(10));
		settings.add(caption("Bajas para ganar (0 = sin límite)"));
		settings.add(fragsField);
		settings.add(Box.createHorizontalStrut(14));
		settings.add(startButton);
		startButton.addActionListener(e -> {
			if (status != null && status.running)
				stop();
			else
				start();
		});

		JPanel botsRow = row();
		botsRow.add(caption("Bots"));
		botsRow.add(botsField);
		botsRow.add(Box.createHorizontalStrut(10));
		botsRow.add(caption("Dificultad"));
		botsRow.add(difficultyField);
		botsRow.add(Box.createHorizontalStrut(10));
		botsRow.add(caption("(además de los jugadores; en partida: bot agregar, bot quitar, bot dificultad)"));

		JPanel addressRow = row();
		addressRow.add(stateLabel);
		addressRow.add(Box.createHorizontalStrut(12));
		addressRow.add(addressLabel);
		addressRow.add(Box.createHorizontalStrut(12));
		addressRow.add(copyButton);
		addressRow.add(publicIpButton);
		copyButton.addActionListener(e -> copyAddress());
		publicIpButton.addActionListener(e -> lookUpPublicIp());

		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		top.setBackground(PANEL);
		top.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));
		top.add(settings);
		top.add(botsRow);
		top.add(addressRow);

		log.setEditable(false);
		log.setBackground(BACKGROUND);
		log.setForeground(TEXT);
		log.setFont(MONO);
		log.setMargin(new java.awt.Insets(8, 10, 8, 10));
		JScrollPane logScroll = scroll(log);

		JTable table = new JTable(players);
		table.setFont(MONO);
		table.setRowHeight(22);
		table.setBackground(BACKGROUND);
		table.setForeground(TEXT);
		table.setGridColor(BORDER);
		table.setShowVerticalLines(false);
		table.setSelectionBackground(FIELD);
		table.setSelectionForeground(TEXT);
		table.setFillsViewportHeight(true);
		table.getTableHeader().setBackground(PANEL);
		table.getTableHeader().setBorder(null);
		table.getTableHeader().setReorderingAllowed(false);
		table.getTableHeader().setDefaultRenderer(new HeaderRenderer());
		int[] widths = { 30, 100, 64, 80, 64, 74 }; // the name column takes whatever space is left
		for (int i = 0; i < widths.length; i++) {
			table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
			if (i != 1)
				table.getColumnModel().getColumn(i).setMinWidth(widths[i]);
		}
		table.setDefaultRenderer(Object.class, new PlayerCellRenderer());

		JPanel side = new JPanel(new BorderLayout());
		side.setBackground(PANEL);
		matchLabel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
		side.add(matchLabel, BorderLayout.NORTH);
		side.add(scroll(table), BorderLayout.CENTER);

		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, logScroll, side);
		split.setResizeWeight(1);
		split.setDividerSize(4);
		split.setBorder(null);
		split.setBackground(BORDER);

		JPanel commandRow = new JPanel(new BorderLayout());
		commandRow.setBackground(PANEL);
		commandRow.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER),
				BorderFactory.createEmptyBorder(6, 10, 6, 10)));
		commandRow.add(label("> ", MONO_BOLD, GREEN), BorderLayout.WEST);
		commandField.setFont(MONO);
		commandField.setBackground(PANEL);
		commandField.setForeground(TEXT);
		commandField.setCaretColor(GREEN);
		commandField.setBorder(null);
		commandField.addActionListener(e -> {
			String command = commandField.getText().trim();
			commandField.setText("");
			if (!command.isEmpty())
				execute(command);
		});
		commandRow.add(commandField, BorderLayout.CENTER);

		JPanel content = new JPanel(new BorderLayout());
		content.setBackground(BACKGROUND);
		content.add(top, BorderLayout.NORTH);
		content.add(split, BorderLayout.CENTER);
		content.add(commandRow, BorderLayout.SOUTH);
		frame.setContentPane(content);
		frame.setMinimumSize(new Dimension(820, 420));
		frame.setSize(1040, 600);
		frame.setLocationRelativeTo(null);
		split.setDividerLocation(1040 - 430);
		showStatus(new ServerHost.Status(null, new Array<>(), 0));
	}

	private static JPanel row() {
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
		row.setBackground(PANEL);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		return row;
	}

	private static JLabel label(String text, Font font, Color color) {
		JLabel label = new JLabel(text);
		label.setFont(font);
		label.setForeground(color);
		return label;
	}

	private static JLabel caption(String text) {
		return label(text, MONO, DIM);
	}

	private static JButton button(String text) {
		JButton button = new JButton(text);
		button.setFont(MONO_BOLD);
		button.setBackground(FIELD);
		button.setForeground(TEXT);
		button.setFocusPainted(false);
		button.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER),
				BorderFactory.createEmptyBorder(4, 10, 4, 10)));
		return button;
	}

	private static JSpinner spinner(int value, int min, int max, int columns) {
		JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, min, max, 1));
		spinner.setEditor(new JSpinner.NumberEditor(spinner, "#")); // no thousands separator in the port
		JTextField text = ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
		text.setColumns(columns);
		text.setFont(MONO);
		text.setBackground(FIELD);
		text.setForeground(TEXT);
		text.setCaretColor(TEXT);
		text.setDisabledTextColor(DIM);
		spinner.setBorder(BorderFactory.createLineBorder(BORDER));
		return spinner;
	}

	private static JScrollPane scroll(JComponent view) {
		JScrollPane scroll = new JScrollPane(view);
		// Not null: a JTable replaces a null border with the look and feel's light one.
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.getViewport().setBackground(BACKGROUND);
		return scroll;
	}

	private static SimpleAttributeSet style(Color color) {
		SimpleAttributeSet style = new SimpleAttributeSet();
		StyleConstants.setForeground(style, color);
		return style;
	}

	// ---- actions ----

	private void start() {
		GameServer.Config config = new GameServer.Config();
		config.port = (Integer) portField.getValue();
		config.maxPlayers = (Integer) playersField.getValue();
		config.timeLimitMinutes = (Integer) timeField.getValue();
		config.fragLimit = (Integer) fragsField.getValue();
		config.bots = (Integer) botsField.getValue();
		config.botDifficulty = BotDifficulty.values()[difficultyField.getSelectedIndex()];
		prefs.putInt(PREF_BOTS, config.bots);
		prefs.put(PREF_BOT_DIFFICULTY, config.botDifficulty.id());
		prefs.putInt(PREF_PORT, config.port);
		prefs.putInt(PREF_PLAYERS, config.maxPlayers);
		prefs.putInt(PREF_TIME, config.timeLimitMinutes);
		prefs.putInt(PREF_FRAGS, config.fragLimit);
		requests++;
		waiting = true;
		showStatus(status);
		host.start(config);
	}

	private void stop() {
		requests++;
		waiting = true;
		showStatus(status);
		host.stop();
	}

	private void quit() {
		frame.setTitle("No Deal - Servidor (cerrando...)");
		host.quit(); // the host stops the server, then closes the window (setOnDisposed)
	}

	private void copyAddress() {
		if (status == null || !status.running)
			return;
		String address = (status.addresses.size > 0 ? status.addresses.first() : "127.0.0.1") + ":" + status.port;
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(address), null);
		append(EventType.INFO, "Copiado: " + address);
	}

	/** Asks a public service which IP this network has on the Internet. Only when the button is pressed. */
	private void lookUpPublicIp() {
		publicIpButton.setEnabled(false);
		append(EventType.INFO, "Consultando tu IP pública (api.ipify.org)...");
		Thread thread = new Thread(() -> {
			String message;
			EventType type = EventType.INFO;
			try {
				HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
				HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.ipify.org"))
						.timeout(Duration.ofSeconds(5)).build();
				String ip = client.send(request, HttpResponse.BodyHandlers.ofString()).body().trim();
				int port = (Integer) portField.getValue();
				message = "Tu IP pública es " + ip + ". Para jugar por Internet, redirigí el puerto UDP " + port
						+ " del router a esta PC y compartí " + ip + ":" + port + ".";
			} catch (Exception e) {
				type = EventType.WARNING;
				message = "No se pudo consultar la IP pública: " + e.getMessage();
			}
			EventType finalType = type;
			String finalMessage = message;
			SwingUtilities.invokeLater(() -> {
				append(finalType, finalMessage);
				publicIpButton.setEnabled(true);
			});
		}, "PublicIp");
		thread.setDaemon(true);
		thread.start();
	}

	private void execute(String line) {
		append(EventType.INFO, "> " + line);
		String command = line.toLowerCase();
		switch (command) {
		case "ayuda":
		case "help":
			append(EventType.INFO, "Comandos: iniciar, detener, estado, ip, limpiar, salir");
			for (String help : GameServer.COMMANDS_HELP.split("\n"))
				if (!help.startsWith("estado"))
					append(EventType.INFO, help);
			break;
		case "iniciar":
		case "start":
			if (status != null && status.running)
				append(EventType.WARNING, "El servidor ya está en marcha.");
			else
				start();
			break;
		case "detener":
		case "stop":
			if (status == null || !status.running)
				append(EventType.WARNING, "El servidor no está en marcha.");
			else
				stop();
			break;
		case "estado":
		case "status":
		case "jugadores":
		case "players":
			printStatus();
			break;
		case "ip":
			if (status != null && status.running)
				for (String ip : status.addresses)
					append(EventType.INFO, "En tu red: " + ip + ":" + status.port);
			lookUpPublicIp();
			break;
		case "limpiar":
		case "clear":
		case "cls":
			log.setText("");
			break;
		case "salir":
		case "quit":
		case "exit":
			quit();
			break;
		default:
			// Match commands (bots) run on the server thread.
			host.post(() -> {
				GameServer server = host.server();
				String reply = server == null ? null : server.command(line);
				if (reply != null)
					onEvent(EventType.INFO, reply);
				else if (server == null && line.toLowerCase().startsWith("bot"))
					onEvent(EventType.WARNING, "Primero iniciá el servidor.");
				else
					onEvent(EventType.WARNING, "Comando desconocido '" + line + "'. Escribí 'ayuda'.");
			});
		}
	}

	private void printStatus() {
		if (status == null || !status.running) {
			append(EventType.INFO, "Servidor detenido.");
			return;
		}
		append(EventType.INFO, status.people + "/" + status.maxPlayers + " jugadores"
				+ (status.bots > 0 ? " y " + status.bots + " bots" : "") + " en el puerto "
				+ status.port + ". " + DedicatedServer.describeMatch(status) + ".");
		for (PlayerInfo p : status.players)
			append(EventType.INFO, String.format("  #%d %-16s %s  bajas %d / muertes %d / asistencias %d  %s", p.id,
					p.name, p.alive ? p.health + " hp" : "muerto", p.kills, p.deaths, p.assists, p.address));
	}

	// ---- log ----

	private void append(EventType type, String message) {
		StyledDocument doc = log.getStyledDocument();
		try {
			doc.insertString(doc.getLength(), LocalTime.now().format(DedicatedServer.CLOCK) + "  ", timeStyle);
			doc.insertString(doc.getLength(), message + "\n", styles.get(type));
			Element root = doc.getDefaultRootElement();
			if (root.getElementCount() > MAX_LOG_LINES)
				doc.remove(0, root.getElement(root.getElementCount() - MAX_LOG_LINES - 1).getEndOffset());
		} catch (BadLocationException e) {
			throw new IllegalStateException(e);
		}
		log.setCaretPosition(doc.getLength());
	}

	private void showStatus(ServerHost.Status status) {
		this.status = status;
		boolean running = status != null && status.running;
		for (JSpinner field : new JSpinner[] { portField, playersField, timeField, fragsField, botsField })
			field.setEnabled(!running && !waiting);
		difficultyField.setEnabled(!running && !waiting);
		startButton.setEnabled(!waiting);
		startButton.setText(running ? "Detener servidor" : "Iniciar servidor");
		copyButton.setEnabled(running);
		if (running) {
			stateLabel.setText("● EN LÍNEA");
			stateLabel.setForeground(GREEN);
			StringBuilder sb = new StringBuilder("Para unirse: ");
			if (status.addresses.size == 0)
				sb.append("127.0.0.1:").append(status.port).append(" (solo esta PC)");
			for (int i = 0; i < status.addresses.size; i++)
				sb.append(i > 0 ? "   " : "").append(status.addresses.get(i)).append(':').append(status.port);
			addressLabel.setText(sb.toString());
			matchLabel.setText(status.people + "/" + status.maxPlayers + " jugadores"
					+ (status.bots > 0 ? " + " + status.bots + " bots" : "") + " · " + DedicatedServer.describeMatch(status));
		} else {
			stateLabel.setText(waiting ? "● ..." : "● DETENIDO");
			stateLabel.setForeground(waiting ? YELLOW : DIM);
			addressLabel.setText("Elegí el puerto y tocá \"Iniciar servidor\"");
			matchLabel.setText("Sin partida");
		}
		players.set(running ? status.players : new Array<>());
	}

	// ---- ServerHost.View (server thread) ----

	@Override
	public void onEvent(EventType type, String message) {
		DedicatedServer.print(type, message); // a copy in the terminal, if there is one
		SwingUtilities.invokeLater(() -> append(type, message));
	}

	@Override
	public void onStatus(ServerHost.Status status) {
		SwingUtilities.invokeLater(() -> {
			waiting = status.handledRequests < requests;
			showStatus(status);
		});
	}

	// ---- players table ----

	private static final class PlayersModel extends AbstractTableModel {
		private static final String[] COLUMNS = { "#", "Jugador", "Bajas", "Muertes", "Asist.", "Estado" };
		private Array<PlayerInfo> rows = new Array<>();

		void set(Array<PlayerInfo> rows) {
			boolean sameSize = rows.size == this.rows.size;
			this.rows = rows;
			if (sameSize)
				fireTableRowsUpdated(0, rows.size - 1); // keeps the selection
			else
				fireTableDataChanged();
		}

		PlayerInfo get(int row) {
			return rows.get(row);
		}

		@Override
		public int getRowCount() {
			return rows.size;
		}

		@Override
		public int getColumnCount() {
			return COLUMNS.length;
		}

		@Override
		public String getColumnName(int column) {
			return COLUMNS[column];
		}

		@Override
		public Object getValueAt(int row, int column) {
			PlayerInfo p = rows.get(row);
			switch (column) {
			case 0:
				return p.id;
			case 1:
				return p.name;
			case 2:
				return p.kills;
			case 3:
				return p.deaths;
			case 4:
				return p.assists;
			default:
				return p.alive ? p.health + " hp" : "muerto";
			}
		}
	}

	private static final class HeaderRenderer extends DefaultTableCellRenderer {
		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus,
				int row, int column) {
			super.getTableCellRendererComponent(table, value, false, false, row, column);
			setFont(MONO_BOLD);
			setBackground(PANEL);
			setForeground(DIM);
			setHorizontalAlignment(column == 1 ? LEFT : CENTER);
			setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
					BorderFactory.createEmptyBorder(4, 6, 4, 6)));
			return this;
		}
	}

	private final class PlayerCellRenderer extends DefaultTableCellRenderer {
		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus,
				int row, int column) {
			super.getTableCellRendererComponent(table, value, selected, false, row, column);
			setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
			setHorizontalAlignment(column == 1 ? LEFT : CENTER);
			PlayerInfo p = players.get(row);
			setForeground(column == 5 && !p.alive ? RED : column == 2 ? GREEN : TEXT);
			setToolTipText(p.address);
			return this;
		}
	}
}
