package com.juego.utils;

import java.util.Random;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;

/**
 * Main menu music player. Tracks are listed in {@value #PLAYLIST} (directories
 * can't be listed inside a jar), one per line:
 *
 * <pre>
 * file.ogg | Title - Author (License)
 * </pre>
 *
 * Blank lines and lines starting with '#' are ignored. Only the current track
 * is loaded (streamed); the others are opened when they start playing.
 */
public class MusicPlayer {
	private static final String FOLDER = "mainMenu/musicPlayer/";
	public static final String PLAYLIST = FOLDER + "playlist.txt";

	private final Array<String> files = new Array<>();
	private final Array<String> titles = new Array<>();
	private Music current;
	private int temaActual;
	private boolean finished = false;
	public boolean paused = false;

	public MusicPlayer() {
		loadPlaylist();
		if (hasTracks()) {
			temaActual = new Random().nextInt(files.size);
			open(temaActual);
		}
	}

	private void loadPlaylist() {
		FileHandle playlist = Gdx.files.internal(PLAYLIST);
		if (!playlist.exists())
			return;
		for (String line : playlist.readString("UTF-8").split("\\r?\\n")) {
			line = line.trim();
			if (line.isEmpty() || line.startsWith("#"))
				continue;
			String[] parts = line.split("\\|", 2);
			String file = parts[0].trim();
			if (!Gdx.files.internal(FOLDER + file).exists()) {
				Gdx.app.error("MusicPlayer", "Missing track listed in playlist: " + file);
				continue;
			}
			files.add(file);
			titles.add(parts.length > 1 ? parts[1].trim() : file);
		}
	}

	public boolean hasTracks() {
		return files.size > 0;
	}

	public String getCurrentTitle() {
		return hasTracks() ? titles.get(temaActual) : "";
	}

	public int getTemaActual() {
		return temaActual;
	}

	public void update() {
		if (current == null)
			return;
		if (finished) {
			nextSong();
			return;
		}
		current.setVolume(Config.musicVolume / 100);
		if (paused) {
			current.pause();
		} else if (!current.isPlaying()) {
			current.play();
		}
	}

	/** Stops playback completely (e.g. when a match starts). */
	public void stop() {
		paused = true;
		if (current != null)
			current.stop();
	}

	public void nextSong() {
		if (hasTracks())
			open((temaActual + 1) % files.size);
	}

	public void prevSong() {
		if (hasTracks())
			open((temaActual - 1 + files.size) % files.size);
	}

	private void open(int index) {
		if (current != null)
			current.dispose();
		temaActual = index;
		finished = false;
		current = Gdx.audio.newMusic(Gdx.files.internal(FOLDER + files.get(index)));
		// Only flag it here: switching tracks inside the callback would dispose the
		// stream that is currently notifying us.
		current.setOnCompletionListener(music -> finished = true);
		current.setVolume(Config.musicVolume / 100);
		if (!paused)
			current.play();
	}

	public void dispose() {
		if (current != null) {
			current.dispose();
			current = null;
		}
	}
}
