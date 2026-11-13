package com.juego.utils;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Every text the player reads, in Spanish and English: {@code Text.get("menu.options")}.
 * Texts with values use {0}, {1}...: {@code Text.get("feed.joined", name)}.
 * <p>
 * Both languages sit side by side here, in code rather than in files, so the
 * server, the tests and every platform have them without loading anything. A
 * missing key shows as the key itself, so it is easy to spot.
 * <p>
 * The language starts as the system's (Spanish if it is Spanish, English
 * otherwise) until the player picks one in Opciones (see {@link Config}).
 */
public final class Text {
	public enum Language {
		/** The menu font has no accents: "Espanol". */
		ES("es", "Espanol"), EN("en", "English");

		public final String id, label;

		Language(String id, String label) {
			this.id = id;
			this.label = label;
		}

		public static Language parse(String id) {
			for (Language language : values())
				if (language.id.equalsIgnoreCase(id))
					return language;
			return null;
		}
	}

	private static final Map<String, String[]> TEXTS = new HashMap<>();
	private static Language language = system();

	private Text() {
	}

	/** Spanish on a Spanish system, English on any other. */
	public static Language system() {
		return "es".equals(Locale.getDefault().getLanguage()) ? Language.ES : Language.EN;
	}

	public static Language language() {
		return language;
	}

	public static void setLanguage(Language language) {
		Text.language = language;
	}

	/** The text in the current language, with {0}, {1}... replaced by the values. */
	public static String get(String key, Object... values) {
		String[] texts = TEXTS.get(key);
		String text = texts == null ? key : texts[language.ordinal()];
		for (int i = 0; i < values.length; i++)
			text = text.replace("{" + i + "}", String.valueOf(values[i]));
		return text;
	}

	/** Whether there is a text for that key (for texts built from names, like the controls). */
	public static boolean has(String key) {
		return TEXTS.containsKey(key);
	}

	/**
	 * The image to load for this language: menu titles and the credits have
	 * words in them, so English has its own copy ("title.png" -> "title_en.png").
	 */
	public static String image(String path) {
		if (language == Language.ES || !LOCALIZED_IMAGES.containsKey(path))
			return path;
		return LOCALIZED_IMAGES.get(path);
	}

	private static final Map<String, String> LOCALIZED_IMAGES = new HashMap<>();

	private static void localizedImage(String path) {
		LOCALIZED_IMAGES.put(path, path.replace(".png", "_en.png"));
	}

	private static void add(String key, String es, String en) {
		if (TEXTS.put(key, new String[] { es, en }) != null)
			throw new IllegalStateException("Text key repeated: " + key);
	}

	static {
		localizedImage("configMenu/title.png");
		localizedImage("controlsMenu/title.png");
		localizedImage("creditsMenu/title.png");
		localizedImage("creditsMenu/credits.png");
		localizedImage("pauseMenu/title.png");

		// ---- common ----
		add("back", "Volver", "Back");
		add("defaults", "Restaurar", "Reset");
		add("accept", "Aceptar", "OK");
		add("cancel", "Cancelar", "Cancel");
		add("close", "Cerrar", "Close");
		add("player.default", "Jugador", "Player");
		add("you", "Vos", "You");

		// ---- main menu ----
		add("menu.singleplayer", "Un jugador", "Singleplayer");
		add("menu.host", "Crear partida", "Host game");
		add("menu.join", "Unirse a partida", "Join game");
		add("menu.options", "Opciones", "Options");
		add("menu.credits", "Creditos", "Credits");
		add("menu.quit", "Salir", "Quit");
		add("menu.music", "Musica", "Music");
		add("menu.musicNow", "Musica: {0}", "Music: {0}");
		add("menu.musicPause", "Pausa", "Pause");

		// ---- options ----
		add("options.fullscreen", " Pantalla Completa", " Fullscreen");
		add("options.controls", "Controles", "Controls");
		add("options.crosshair", "Mira", "Crosshair");
		add("options.sound", "Sonido", "Sound");
		add("options.music", "Musica", "Music");
		add("options.effects", "Efectos", "Effects");
		add("options.touch", "Pantalla tactil", "Touch screen");
		add("options.mouse", "Mouse", "Mouse");
		add("options.sensitivity", "Sensibilidad", "Sensitivity");
		add("options.video", "Video", "Video");
		add("options.language", "Idioma", "Language");

		// ---- controls ----
		add("controls.hint", "Hace clic en una accion y despues presiona la tecla o boton que quieras usar.",
				"Click an action, then press the key or button you want for it.");
		add("controls.waiting", "Presiona...", "Press...");
		add("action.FORWARD", "Adelante", "Forward");
		add("action.BACKWARD", "Atras", "Back");
		add("action.LEFT", "Izquierda", "Left");
		add("action.RIGHT", "Derecha", "Right");
		add("action.JUMP", "Saltar", "Jump");
		add("action.WALK", "Caminar", "Walk");
		add("action.CROUCH", "Agacharse", "Crouch");
		add("action.FIRE", "Disparar", "Fire");
		add("action.AIM", "Apuntar", "Aim");
		add("action.RELOAD", "Recargar", "Reload");
		add("action.SWITCH_GUN", "Cambiar arma", "Switch gun");
		add("action.DROP", "Soltar / agarrar arma", "Drop / pick up gun");
		add("action.SCOREBOARD", "Puntuacion", "Scoreboard");
		add("action.PAUSE", "Pausa", "Pause");
		add("action.CONSOLE", "Consola", "Console");
		add("action.KNIFE", "Cuchillo", "Knife");
		add("gun.knife", "Cuchillo", "Knife");
		add("key.grave", "Izq. del 1", "Left of 1");
		add("key.other", "Tecla {0}", "Key {0}");
		add("mouse.left", "Clic izquierdo", "Left click");
		add("mouse.right", "Clic derecho", "Right click");
		add("mouse.middle", "Clic medio", "Middle click");
		add("mouse.other", "Boton {0}", "Button {0}");

		// ---- on-screen buttons (phones) ----
		add("touch.FIRE", "FUEGO", "FIRE");
		add("touch.AIM", "MIRA", "AIM");
		add("touch.JUMP", "SALTO", "JUMP");
		add("touch.CROUCH", "AGACHAR", "CROUCH");
		add("touch.SWITCH_GUN", "ARMA", "SWITCH");
		add("touch.RELOAD", "RECARGA", "RELOAD");
		add("touch.DROP", "SOLTAR\nAGARRAR", "DROP\nPICK UP");
		add("touch.SCOREBOARD", "PTS", "SCORE");

		// ---- crosshair ----
		add("crosshair.title", "Mira", "Crosshair");
		add("crosshair.style", "Estilo", "Style");
		add("crosshair.color", "Color", "Color");
		add("crosshair.size", "Largo", "Length");
		add("crosshair.thickness", "Grosor", "Thickness");
		add("crosshair.gap", "Separacion", "Gap");
		add("crosshair.opacity", "Opacidad", "Opacity");
		add("crosshair.preview", "Vista previa", "Preview");
		add("crosshair.outline", " Borde negro", " Black outline");
		add("crosshair.dynamicMove", " Se abre al moverte y saltar", " Opens when moving and jumping");
		add("crosshair.dynamicFire", " Se abre al disparar seguido", " Opens when spraying");
		add("crosshair.followRecoil", " Sigue el retroceso (marca donde va la bala)",
				" Follows the recoil (marks where the bullet goes)");
		add("crosshair.style.CROSS", "Clasica", "Classic");
		add("crosshair.style.CROSS_DOT", "Clasica con punto", "Classic with dot");
		add("crosshair.style.DOT", "Solo punto", "Dot only");
		add("crosshair.style.T", "Forma de T", "T shape");
		add("crosshair.color.0", "Verde", "Green");
		add("crosshair.color.1", "Amarillo", "Yellow");
		add("crosshair.color.2", "Cian", "Cyan");
		add("crosshair.color.3", "Blanco", "White");
		add("crosshair.color.4", "Rojo", "Red");
		add("crosshair.color.5", "Magenta", "Magenta");

		// ---- bots ----
		add("difficulty.FACIL", "Facil", "Easy");
		add("difficulty.MEDIO", "Medio", "Medium");
		add("difficulty.DIFICIL", "Dificil", "Hard");
		add("difficulty.MUY_DIFICIL", "Muy dificil", "Expert");
		add("difficulty.choices", "facil, medio, dificil, muy_dificil", "easy, medium, hard, expert");

		// ---- starting a match ----
		add("mp.play", "Jugar", "Play");
		add("mp.create", "Crear", "Create");
		add("mp.connect", "Conectar", "Connect");
		add("mp.search", "Buscar", "Search");
		add("mp.name", "Nombre", "Name");
		add("mp.connection", "Conexion", "Connection");
		add("mp.phone", "Telefono", "Phone");
		add("mp.serverIp", "IP del servidor", "Server IP");
		add("mp.port", "Puerto UDP", "UDP port");
		add("mp.bots", "Bots", "Bots");
		add("mp.difficulty", "Dificultad", "Difficulty");
		add("mp.duration", "Duracion", "Length");
		add("mp.fragLimit", "Bajas para ganar", "Kills to win");
		add("mp.minutes", "{0} min", "{0} min");
		add("mp.noLimit", "Sin limite", "No limit");
		add("mp.consoleWhere.touch", "boton >_ arriba a la derecha", ">_ button, top right");
		add("mp.consoleWhere.key", "tecla a la izquierda del 1", "the key left of 1");
		add("mp.hint.singleplayer",
				"Deathmatch contra bots. Durante la partida, la consola ({0}) agrega o saca bots y cambia la dificultad.",
				"Deathmatch against bots. During the match, the console ({0}) adds or removes bots and changes their difficulty.");
		add("mp.hint.host", "Deathmatch hasta {0} personas, mas los bots. Los demas se unen con tu IP (se muestra al empezar).",
				"Deathmatch for up to {0} people, plus bots. The others join with your IP (shown when the match starts).");
		add("mp.hint.hostBluetooth",
				"Deathmatch hasta {0} personas, mas los bots. Los demas eligen \"{1}\" en Unirse a partida, con Conexion: Bluetooth (por Wi-Fi tambien se pueden unir con tu IP).",
				"Deathmatch for up to {0} people, plus bots. The others pick \"{1}\" in Join game, with Connection: Bluetooth (over Wi-Fi they can also join with your IP).");
		add("mp.hint.joinBluetooth",
				"Elegi el telefono de quien creo la partida. Si no aparece, que toque Crear otra vez: su telefono queda visible unos minutos.",
				"Pick the phone of whoever created the match. If it isn't listed, they should tap Create again: their phone stays visible for a few minutes.");
		add("mp.preparingBluetooth", "Preparando Bluetooth...", "Getting Bluetooth ready...");
		add("mp.scanning", "Buscando telefonos cerca...", "Looking for phones nearby...");
		add("mp.paired", " (vinculado)", " (paired)");
		add("mp.choosePhone", "Elegi el telefono de quien creo la partida (toca Buscar si no aparece).",
				"Pick the phone of whoever created the match (tap Search if it isn't listed).");
		add("mp.connectingBluetooth", "Conectando por Bluetooth con {0}...", "Connecting over Bluetooth to {0}...");
		add("mp.enteringMatch", "Entrando a la partida de {0}...", "Joining {0}'s match...");
		add("mp.bluetoothFailed", "No se pudo abrir el Bluetooth: {0}", "Couldn't open Bluetooth: {0}");
		add("mp.badPort", "El puerto tiene que ser un numero entre 1 y 65535", "The port must be a number between 1 and 65535");
		add("mp.connectingTo", "Conectando a {0}:{1}...", "Connecting to {0}:{1}...");
		add("mp.preparingBots", "Preparando la partida y los bots...", "Getting the match and the bots ready...");
		add("mp.creating", "Creando partida...", "Creating the match...");
		add("mp.portInUse", "No se pudo abrir el puerto {0}: ya esta en uso", "Couldn't open port {0}: it is already in use");

		// ---- Bluetooth (Android) ----
		add("bt.thisPhone", "este telefono", "this phone");
		add("bt.needNearby",
				"Para jugar por Bluetooth hace falta el permiso de dispositivos cercanos. Podes darlo en Ajustes > Apps > No Deal > Permisos.",
				"Playing over Bluetooth needs the nearby devices permission. You can grant it in Settings > Apps > No Deal > Permissions.");
		add("bt.needLocation",
				"Para buscar telefonos por Bluetooth, Android pide el permiso de ubicacion. Podes darlo en Ajustes > Apps > No Deal > Permisos.",
				"To look for phones over Bluetooth, Android asks for the location permission. You can grant it in Settings > Apps > No Deal > Permissions.");
		add("bt.denied", "Android no dejo usar el Bluetooth: {0}", "Android didn't allow using Bluetooth: {0}");
		add("bt.turnOn", "Para jugar por Bluetooth hay que prenderlo.", "Bluetooth has to be on to play over it.");
		add("bt.connectFailed",
				"No se pudo conectar con {0}. Fijate que haya creado la partida con Conexion: Bluetooth y que siga en el juego.",
				"Couldn't connect to {0}. Check that they created the match with Connection: Bluetooth and are still in the game.");

		// ---- pause menu ----
		add("pause.resume", "Reanudar", "Resume");
		add("pause.scoreboard", "Puntuacion", "Scoreboard");
		add("pause.options", "Opciones", "Options");
		add("pause.rename", "Cambiar nombre", "Change name");
		add("pause.mainMenu", "Menu Principal", "Main Menu");
		add("pause.newName", "Nuevo nombre", "New name");

		// ---- HUD, scoreboard, death screen ----
		add("hud.end", "Fin", "End");
		add("hud.tapBack", "Toca para volver", "Tap to go back");
		add("hud.clickBack", "Clic para volver", "Click to go back");
		add("hud.nextMatchIn", "Nueva partida en {0} s", "New match in {0} s");
		add("hud.timeLeft", "Quedan {0}", "{0} left");
		add("prompt.pickUp", "[{0}] Agarrar {1}", "[{0}] Pick up {1}");
		add("prompt.swap", "[{0}] Cambiar {1} por {2}", "[{0}] Swap {1} for {2}");
		add("score.player", "Jugador", "Player");
		add("score.kills", "Bajas", "Kills");
		add("score.deaths", "Muertes", "Deaths");
		add("score.assists", "Asist.", "Assists");
		add("score.renames", "Cambios de nombre", "Name changes");
		add("match.singleplayer", "Un jugador contra bots", "Singleplayer against bots");
		add("match.deathmatch", "Deathmatch", "Deathmatch");
		add("match.fragLimit", "{0}: gana el primero en llegar a {1} bajas", "{0}: first to {1} kills wins");
		add("death.respawnIn", "Reviviendo en {0}s", "Respawning in {0}s");
		add("death.respawning", "Reviviendo...", "Respawning...");
		add("death.killedBy", "Te eliminó", "Killed by");
		add("death.headshot", "  -  a la cabeza", "  -  headshot");
		add("death.stats", "Bajas {0}      Muertes {1}      Asistencias {2}", "Kills {0}      Deaths {1}      Assists {2}");
		add("death.fell", "Te caíste del mapa", "You fell off the map");
		add("death.fallDamage", "Te mató la caída", "The fall killed you");
		add("death.died", "Moriste", "You died");

		// ---- feed and messages during a match ----
		add("feed.consoleHint", "Escribi en la consola ({0}) 'bot agregar' o 'bot dificultad'",
				"Type in the console ({0}) 'bot add' or 'bot difficulty'");
		add("feed.consoleButton", "boton {0}", "button: {0}");
		add("feed.consoleKey", "tecla {0}", "key: {0}");
		add("feed.hosted", "Partida creada (puerto {0})", "Match created (port {0})");
		add("feed.hostedAt", "Partida creada: {0}", "Match created: {0}");
		add("feed.connected", "Conectado a la partida", "Connected to the match");
		add("feed.joined", "{0} se unió", "{0} joined");
		add("feed.left", "{0} salió", "{0} left");
		add("feed.fell", "{0} se cayó del mapa", "{0} fell off the map");
		add("feed.fallDied", "{0} murió por la caída", "{0} died from the fall");
		add("feed.headshot", ", a la cabeza", ", headshot");
		add("feed.newMatch", "Nueva partida", "New match");
		add("feed.renamedMe", "Ahora te llamás {0}", "You are now {0}");
		add("feed.renamed", "{0} ahora se llama {1}", "{0} is now {1}");
		add("center.matchOver", "Fin de la partida", "Match over");
		add("center.youWon", "¡Ganaste la partida!", "You won the match!");
		add("center.winner", "Ganó {0}", "{0} won");

		// ---- console ----
		add("console.welcome", "Consola. Escribi 'ayuda' para ver los comandos; {0} para cerrar.",
				"Console. Type 'help' to see the commands; {0} to close.");
		add("console.help", "Comandos:\nnombre <nuevo>  cambia tu nombre\n"
				+ "cl_showfps [0|1]  muestra los FPS y el ping\n"
				+ "cl_showspeed [0|1]  muestra tu velocidad y la de cada salto\n"
				+ "fps_max [valor]  limita los FPS (0 = los de la pantalla)\n{0}\nlimpiar  borra la consola",
				"Commands:\nname <new>  changes your name\n"
						+ "cl_showfps [0|1]  shows the FPS and the ping\n"
						+ "cl_showspeed [0|1]  shows your speed and that of each jump\n"
						+ "fps_max [value]  caps the FPS (0 = the screen's refresh rate)\n{0}\nclear  clears the console");
		add("console.hostOnly", "{0} lo cambia quien creo la partida. Ahora: {1}",
				"{0} is changed by whoever created the match. Now: {1}");
		add("hud.fps", "{0} FPS", "{0} FPS");
		add("hud.ping", "ping {0} ms", "ping {0} ms");
		add("hud.jump", "salto {0}", "jump {0}");
		add("console.renameUsage", "Uso: nombre <nuevo nombre> (hasta {0} letras)", "Usage: name <new name> (up to {0} letters)");
		add("console.renamed", "Ahora te llamas {0}.", "You are now {0}.");
		add("console.notHost", "Los comandos los usa quien creo la partida (o la consola del servidor dedicado).",
				"Commands are for whoever created the match (or the dedicated server's console).");
		add("console.unknown", "No conozco '{0}'. Escribi 'ayuda'.", "Unknown command '{0}'. Type 'help'.");

		// ---- connecting ----
		add("net.unknownHost", "No se encontro el host '{0}'", "Host '{0}' not found");
		add("net.noResponse", "No se pudo conectar: el servidor no responde", "Couldn't connect: the server doesn't answer");
		add("net.lost", "Se perdio la conexion con el servidor", "Lost the connection to the server");
		add("net.serverClosed", "El servidor cerro la partida", "The server closed the match");

		// ---- server (its events, console replies and the reasons it gives) ----
		add("server.playing", "jugando, quedan {0}", "playing, {0} left");
		add("server.between", "entre partidas, la proxima en {0}", "between matches, the next one in {0}");
		add("server.playerLine", "  #%d %-16s %s  bajas %d / muertes %d / asistencias %d  %s%n",
				"  #%d %-16s %s  kills %d / deaths %d / assists %d  %s%n");
		add("server.dead", "muerto", "dead");
		add("server.nobody", "  (nadie)\n", "  (nobody)\n");
		add("server.timedOut", "{0} se desconectó (sin respuesta)", "{0} disconnected (no response)");
		add("server.left", "{0} salió de la partida", "{0} left the match");
		add("server.badVersion", "Version incompatible (servidor v{0}, cliente v{1})",
				"Incompatible version (server v{0}, client v{1})");
		add("server.full", "Servidor lleno ({0} jugadores)", "Server full ({0} players)");
		add("server.rejected", "Rechazado {0}: {1}", "Rejected {0}: {1}");
		add("server.joined", "{0} entró a la partida (#{1}, {2})", "{0} joined the match (#{1}, {2})");
		add("server.renamed", "{0} ahora se llama {1}", "{0} is now {1}");
		add("server.botJoined", "{0} entró a la partida (bot {1})", "{0} joined the match ({1} bot)");
		add("server.botLeft", "{0} salió de la partida (bot)", "{0} left the match (bot)");
		add("server.botDifficulty", "Dificultad de los bots: {0}", "Bot difficulty: {0}");
		add("server.commandsHelp", "bot agregar [cantidad] [dificultad]  agrega bots\n"
				+ "bot quitar [nombre | cantidad | todos]  saca bots\n"
				+ "bot dificultad <dificultad>  cambia la dificultad de todos los bots\n"
				+ "bot lista  muestra los bots\n"
				+ "  dificultades: {0}\n"
				+ "  (tambien bot_add, bot_kick y bot_difficulty, como en CS)\n"
				+ "estado  jugadores y tiempo de la partida\n"
				+ "sv_gravity [valor]  gravedad (800 es la normal, como en CS)\n"
				+ "sv_airaccelerate [valor]  control en el aire (10 normal; 100 para bunnyhop)\n"
				+ "sv_autobunnyhopping [0|1]  mantener el salto vuelve a saltar al caer\n"
				+ "sv_maxspeed [valor]  velocidad al correr (250 normal, como en CS)\n"
				+ "sv_maxvelocity [valor]  velocidad maxima, aun con bunnyhop (750 normal)\n"
				+ "sv_falldamage [0|1]  las caidas fuertes lastiman",
				"bot add [count] [difficulty]  adds bots\n"
						+ "bot kick [name | count | all]  removes bots\n"
						+ "bot difficulty <difficulty>  changes the difficulty of every bot\n"
						+ "bot list  shows the bots\n"
						+ "  difficulties: {0}\n"
						+ "  (also bot_add, bot_kick and bot_difficulty, as in CS)\n"
						+ "status  players and match time\n"
						+ "sv_gravity [value]  gravity (800 is normal, as in CS)\n"
						+ "sv_airaccelerate [value]  control in the air (10 is normal; 100 for bunnyhop)\n"
						+ "sv_autobunnyhopping [0|1]  holding jump jumps again on landing\n"
						+ "sv_maxspeed [value]  running speed (250 is normal, as in CS)\n"
						+ "sv_maxvelocity [value]  top speed, even bunnyhopping (750 is normal)\n"
						+ "sv_falldamage [0|1]  hard falls hurt");
		add("server.status", "{0}/{1} jugadores y {2} bots, {3}", "{0}/{1} players and {2} bots, {3}");
		add("server.difficultyNow", "Dificultad de los bots: {0}. Opciones: {1}", "Bot difficulty: {0}. Options: {1}");
		add("server.unknownDifficulty", "No conozco la dificultad '{0}'. Opciones: {1}", "Unknown difficulty '{0}'. Options: {1}");
		add("server.allBotsNow", "Ahora todos los bots juegan en {0}.", "All bots now play on {0}.");
		add("server.botList", "{0} bots (los nuevos entran en {1})", "{0} bots (new ones join on {1})");
		add("server.botLine", "%n  %-12s %s  bajas %d / muertes %d", "%n  %-12s %s  kills %d / deaths %d");
		add("server.unknownBotCommand", "No conozco 'bot {0}'. Comandos:\n{1}", "Unknown command 'bot {0}'. Commands:\n{1}");
		add("server.noMoreBots", "No entran mas bots (maximo {0}, y {1} en total).",
				"No room for more bots (at most {0}, and {1} in total).");
		add("server.botAdded", "Entro {0} ({1}).", "{0} joined ({1}).");
		add("server.botsAdded", "Entraron {0} ({1}).", "{0} joined ({1}).");
		add("server.noBots", "No hay bots.", "There are no bots.");
		add("server.noBotNamed", "No hay ningun bot llamado '{0}'. Escribi 'bot lista' para verlos.",
				"There is no bot called '{0}'. Type 'bot list' to see them.");
		add("server.botsRemoved", "Salio {0}.", "{0} left.");
		add("server.matchOver", "Fin de la partida ({0}). {1}", "Match over ({0}). {1}");
		add("server.byFragLimit", "límite de bajas", "kill limit");
		add("server.byTime", "tiempo", "time");
		add("server.noKills", "Nadie hizo bajas.", "Nobody got a kill.");
		add("server.winner", "Ganó {0} con {1} bajas.", "{0} won with {1} kills.");
		add("server.newMatch", "Empieza una nueva partida", "A new match begins");
		add("server.fell", "{0} se cayó del mapa", "{0} fell off the map");
		add("server.fallDied", "{0} murió por la caída", "{0} died from the fall");
		add("server.kill", "{0} mató a {1} con {2}{3}", "{0} killed {1} with {2}{3}");
		add("server.headshot", " (headshot)", " (headshot)");
		add("server.cvar", "{0} = {1}", "{0} = {1}");
		add("server.cvarChanged", "{0} ahora es {1}", "{0} is now {1}");
		add("server.cvarRange", "{0} tiene que estar entre {1} y {2}", "{0} must be between {1} and {2}");
		add("server.cvarNotNumber", "{0}: '{1}' no es un numero", "{0}: '{1}' isn't a number");
		add("server.cvarUnknown", "No conozco {0}", "Unknown variable {0}");
		add("feed.movement", "El servidor cambio {0} a {1}", "The server set {0} to {1}");

		// ---- dedicated server (its window and its terminal) ----
		add("ds.portFailed", "No se pudo abrir el puerto {0}: {1}. Probá con otro puerto.",
				"Couldn't open port {0}: {1}. Try another port.");
		add("ds.started", "Servidor iniciado en el puerto UDP {0} ({1} jugadores, {2} min{3}){4}",
				"Server started on UDP port {0} ({1} players, {2} min{3}){4}");
		add("ds.startedFrags", " o {0} bajas", " or {0} kills");
		add("ds.startedBots", ", con {0} bots ({1})", ", with {0} bots ({1})");
		add("ds.lanAddress", "En tu red se unen con {0}", "On your network they join with {0}");
		add("ds.noLan", "No se encontró ninguna red local: solo se puede jugar en esta PC ({0})",
				"No local network found: only this PC can play ({0})");
		add("ds.stopped", "Servidor detenido", "Server stopped");
		add("ds.title", "No Deal - Servidor", "No Deal - Server");
		add("ds.titleClosing", "No Deal - Servidor (cerrando...)", "No Deal - Server (closing...)");
		add("ds.start", "Iniciar servidor", "Start server");
		add("ds.stop", "Detener servidor", "Stop server");
		add("ds.copyIp", "Copiar IP", "Copy IP");
		add("ds.publicIp", "Ver IP pública", "Show public IP");
		add("ds.welcome", "No Deal - servidor dedicado. Elegí el puerto y las reglas, y tocá \"Iniciar servidor\".",
				"No Deal - dedicated server. Choose the port and the rules, and press \"Start server\".");
		add("ds.typeHelp", "Escribí 'ayuda' abajo para ver los comandos.", "Type 'help' below to see the commands.");
		add("ds.port", "Puerto UDP", "UDP port");
		add("ds.players", "Jugadores", "Players");
		add("ds.minutes", "Minutos", "Minutes");
		add("ds.fragLimit", "Bajas para ganar (0 = sin límite)", "Kills to win (0 = no limit)");
		add("ds.bots", "Bots", "Bots");
		add("ds.difficulty", "Dificultad", "Difficulty");
		add("ds.botsHint", "(además de los jugadores; en partida: bot agregar, bot quitar, bot dificultad)",
				"(besides the players; during the match: bot add, bot kick, bot difficulty)");
		add("ds.copied", "Copiado: {0}", "Copied: {0}");
		add("ds.askingPublicIp", "Consultando tu IP pública (api.ipify.org)...", "Looking up your public IP (api.ipify.org)...");
		add("ds.publicIpIs",
				"Tu IP pública es {0}. Para jugar por Internet, redirigí el puerto UDP {1} del router a esta PC y compartí {0}:{1}.",
				"Your public IP is {0}. To play over the Internet, forward UDP port {1} on the router to this PC and share {0}:{1}.");
		add("ds.publicIpFailed", "No se pudo consultar la IP pública: {0}", "Couldn't look up the public IP: {0}");
		add("ds.commands", "Comandos: iniciar, detener, estado, ip, limpiar, salir",
				"Commands: start, stop, status, ip, clear, quit");
		add("ds.alreadyRunning", "El servidor ya está en marcha.", "The server is already running.");
		add("ds.notRunning", "El servidor no está en marcha.", "The server isn't running.");
		add("ds.onYourNetwork", "En tu red: {0}", "On your network: {0}");
		add("ds.startFirst", "Primero iniciá el servidor.", "Start the server first.");
		add("ds.unknownCommand", "Comando desconocido '{0}'. Escribí 'ayuda'.", "Unknown command '{0}'. Type 'help'.");
		add("ds.stoppedDot", "Servidor detenido.", "Server stopped.");
		add("ds.status", "{0}/{1} jugadores{2} en el puerto {3}. {4}.", "{0}/{1} players{2} on port {3}. {4}.");
		add("ds.statusBots", " y {0} bots", " and {0} bots");
		add("ds.playerLine", "  #%d %-16s %s  bajas %d / muertes %d / asistencias %d  %s",
				"  #%d %-16s %s  kills %d / deaths %d / assists %d  %s");
		add("ds.dead", "muerto", "dead");
		add("ds.online", "● EN LÍNEA", "● ONLINE");
		add("ds.offline", "● DETENIDO", "● STOPPED");
		add("ds.toJoin", "Para unirse: ", "To join: ");
		add("ds.onlyThisPc", " (solo esta PC)", " (this PC only)");
		add("ds.playersCount", "{0}/{1} jugadores", "{0}/{1} players");
		add("ds.choosePort", "Elegí el puerto y tocá \"Iniciar servidor\"", "Choose the port and press \"Start server\"");
		add("ds.noMatch", "Sin partida", "No match");
		add("ds.col.player", "Jugador", "Player");
		add("ds.col.kills", "Bajas", "Kills");
		add("ds.col.deaths", "Muertes", "Deaths");
		add("ds.col.assists", "Asist.", "Assists");
		add("ds.col.state", "Estado", "State");
		add("ds.playing", "Jugando, quedan {0}", "Playing, {0} left");
		add("ds.between", "Entre partidas, la proxima en {0}", "Between matches, the next one in {0}");
		add("ds.terminalStatus", "{0}/{1} jugadores y {2} bots en el puerto {3}, {4}",
				"{0}/{1} players and {2} bots on port {3}, {4}");
		add("ds.terminalCommands", "Comandos: estado (lista los jugadores), salir (avisa a los jugadores y cierra)",
				"Commands: status (list players), quit (notify players and stop)");
		add("ds.terminalUnknown", "Comando desconocido '{0}'. Escribí 'ayuda'.", "Unknown command '{0}'. Type 'help'.");
		add("ds.terminalHelp", "Escribí 'ayuda' para ver los comandos.", "Type 'help' for commands.");
		add("ds.usage", "Uso: java -jar NoDeal-server.jar [--port {0}] [--max-players {1}] [--time-limit {2}] "
				+ "[--frag-limit {3}] [--bots 0] [--bot-difficulty medio] [--gravity 800] [--airaccelerate 10] "
				+ "[--autobunnyhop] [+sv_maxspeed 250 ...] [--lang es|en] [--nogui]\n"
				+ "  --time-limit  duración de la partida en minutos\n"
				+ "  --frag-limit  bajas que ganan antes de tiempo (0 = se juega todo el tiempo)\n"
				+ "  --bots        bots en la partida (0 a {4}), además de los jugadores\n"
				+ "  --bot-difficulty  {5}\n"
				+ "  --gravity, --airaccelerate, --autobunnyhop  como sv_gravity, sv_airaccelerate y sv_autobunnyhopping\n"
				+ "  +sv_...       cualquier variable sv_ de la consola, como en CS: +sv_falldamage 0\n"
				+ "  --lang        idioma de los mensajes (si no, el del juego o el del sistema)\n"
				+ "  --nogui       usar la terminal en vez de abrir la ventana del servidor",
				"Usage: java -jar NoDeal-server.jar [--port {0}] [--max-players {1}] [--time-limit {2}] "
						+ "[--frag-limit {3}] [--bots 0] [--bot-difficulty medium] [--gravity 800] [--airaccelerate 10] "
						+ "[--autobunnyhop] [+sv_maxspeed 250 ...] [--lang es|en] [--nogui]\n"
						+ "  --time-limit  match length in minutes\n"
						+ "  --frag-limit  kills that win the match early (0 = play the full time)\n"
						+ "  --bots        bots in the match (0 to {4}), besides the players\n"
						+ "  --bot-difficulty  {5}\n"
						+ "  --gravity, --airaccelerate, --autobunnyhop  like sv_gravity, sv_airaccelerate and sv_autobunnyhopping\n"
						+ "  +sv_...       any sv_ console variable, as in CS: +sv_falldamage 0\n"
						+ "  --lang        language of the messages (otherwise the game's, or the system's)\n"
						+ "  --nogui       run in the terminal instead of opening the server window");
		add("ds.title.usage", "Servidor dedicado de No Deal (protocolo v{0})", "No Deal dedicated server (protocol v{0})");
		add("ds.unknownOption", "Opción desconocida {0}", "Unknown option {0}");
		add("ds.unknownDifficulty", "Dificultad de bots desconocida {0} ({1})", "Unknown bot difficulty {0} ({1})");
		add("ds.unknownLanguage", "Idioma desconocido {0} (es, en)", "Unknown language {0} (es, en)");
		add("ds.badPort", "--port tiene que estar entre 1 y 65535", "--port must be between 1 and 65535");
		add("ds.badPlayers", "--max-players tiene que estar entre 1 y 32", "--max-players must be between 1 and 32");
		add("ds.badTime", "--time-limit tiene que estar entre 1 y 120 minutos", "--time-limit must be between 1 and 120 minutes");
		add("ds.badFrags", "--frag-limit tiene que estar entre 0 (sin límite) y 999", "--frag-limit must be between 0 (none) and 999");
		add("ds.badBots", "--bots tiene que estar entre 0 y {0}", "--bots must be between 0 and {0}");
	}
}
