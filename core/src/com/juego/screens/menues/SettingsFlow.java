package com.juego.screens.menues;

import com.juego.core.Core;

/**
 * Where the settings pages (Opciones, Controles, Mira) are shown: as full
 * screens from the main menu, or over the running match from the pause menu.
 * The pages only navigate through this, so the same pages work in both places.
 */
public interface SettingsFlow {
	/** Shows this settings page instead of the current one. */
	void open(MenuScreen page);

	/** Leaves the settings: back to the main menu, or to the pause menu. */
	void close();

	/** The settings opened from the main menu, as full screens. */
	static SettingsFlow mainMenu(final Core game) {
		return new SettingsFlow() {
			@Override
			public void open(MenuScreen page) {
				game.setScreen(page);
			}

			@Override
			public void close() {
				game.setScreen(new MainMenuScreen(game));
			}
		};
	}
}
