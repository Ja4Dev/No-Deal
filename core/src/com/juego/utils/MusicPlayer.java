package com.juego.utils;

import java.util.Random;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;

public class MusicPlayer {
	private Random random;
	private int temaActual;
	public Music music[] = { Gdx.audio.newMusic(Gdx.files.internal("mainMenu/musicPlayer/Katyusha - Orchestral Cover.mp3")),
			 	 Gdx.audio.newMusic(Gdx.files.internal("mainMenu/musicPlayer/Bella Ciao - Fonola Band.mp3")),
				 Gdx.audio.newMusic(Gdx.files.internal("mainMenu/musicPlayer/Avenida de las Camelias - Orquesta Sanmartiniana.mp3")),
				 Gdx.audio.newMusic(Gdx.files.internal("mainMenu/musicPlayer/Cara al Sol - La Falange.mp3")),
				 Gdx.audio.newMusic(Gdx.files.internal("mainMenu/musicPlayer/Pa Vikingtog - Panzer SS Wiking.mp3")),
 				 };
	public boolean paused = false;
	
	public MusicPlayer() {
		random = new Random();
		temaActual = random.nextInt(music.length);
		music[getTemaActual()].play();
		music[getTemaActual()].setVolume(Config.musicVolume/100);
	}
	
	public void update() {
		if (paused) {
			music[getTemaActual()].pause();
		} else music[getTemaActual()].play();
		if (!music[getTemaActual()].isPlaying() && !paused) {
			nextSong();
		}
		music[getTemaActual()].setVolume(Config.musicVolume/100);
	}

	public int getTemaActual() {
		return temaActual;
	}
	public void nextSong() {
		music[getTemaActual()].stop();
		if(temaActual < music.length - 1) {
		temaActual++;
		} else {
			temaActual = 0;
		}
		music[getTemaActual()].play();
	}
	public void prevSong() {
		music[getTemaActual()].stop();
		if(temaActual > 0) {
			temaActual--;
			} else {
				temaActual = music.length - 1;
			}
		music[getTemaActual()].play();
	}
	
	
}
