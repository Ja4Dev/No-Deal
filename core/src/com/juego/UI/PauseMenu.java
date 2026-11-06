package com.juego.UI;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.juego.components.PlayerComponent;
import com.juego.core.Assets;
import com.juego.core.Core;
import com.juego.core.GameWorld;
import com.juego.screens.menues.ConfigMenuScreen;
import com.juego.screens.menues.MenuScreen;
import com.juego.screens.menues.SettingsFlow;

/**
 * The in-game pause menu. Its "Opciones" button opens the same settings
 * pages as the main menu (sound, mouse, video, controls, crosshair), drawn over
 * the match; the pause key or "Volver" goes back to the pause buttons.
 */
public class PauseMenu extends Menu {
	private static final float BUTTON_W = 192, BUTTON_H = 48, BUTTON_STEP = 60;

	private Image backgroundImage, titleImage;
	private TextButton resumeButton, scoreButton, settingsButton, menuButton;
	private final PlayerComponent playerComponent;
	private final GameWorld gameWorld;

	/** The settings page open over the pause menu, or null. */
	private MenuScreen page;
	/** Page to show from the next frame: pages are swapped from their own click listeners. */
	private MenuScreen pendingPage;
	private boolean closePending;

	private final SettingsFlow settingsFlow = new SettingsFlow() {
		@Override
		public void open(MenuScreen next) {
			if (pendingPage != null && pendingPage != next)
				pendingPage.dispose();
			pendingPage = next;
			closePending = false;
		}

		@Override
		public void close() {
			closePending = true;
		}
	};

	public PauseMenu(PlayerComponent playerComponent, GameWorld gameWorld) {
		this.playerComponent = playerComponent;
		this.gameWorld = gameWorld;
		setWidgets();
		configureWidgets();
		setListeners();
		Gdx.input.setInputProcessor(stage);
	}

	private void setListeners() {
		resumeButton.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				playerComponent.pause = false;
			}
		});
		scoreButton.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				playerComponent.scoreboard = true;
			}
		});
		settingsButton.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				settingsFlow.open(new ConfigMenuScreen(gameWorld.game, settingsFlow));
			}
		});
		menuButton.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				gameWorld.game.leaveMatch(null);
			}
		});
	}

	private void configureWidgets() {
		backgroundImage.setSize(Core.VIRTUAL_WIDTH / 2 - backgroundImage.getWidth(), Core.VIRTUAL_HEIGHT / 1.5f);
		backgroundImage.setPosition(Core.VIRTUAL_WIDTH / 2 - backgroundImage.getWidth() / 2, Core.VIRTUAL_HEIGHT / 2 - backgroundImage.getHeight() / 2);
		titleImage.setSize(titleImage.getWidth() * 1.25f, titleImage.getHeight() * 1.25f);
		titleImage.setPosition(Core.VIRTUAL_WIDTH / 2 - titleImage.getWidth() / 2, Core.VIRTUAL_HEIGHT / 1.4f - titleImage.getHeight() / 2);
		stage.addActor(backgroundImage);
		stage.addActor(titleImage);
		TextButton[] buttons = { resumeButton, scoreButton, settingsButton, menuButton };
		for (int i = 0; i < buttons.length; i++) {
			buttons[i].setSize(BUTTON_W, BUTTON_H);
			buttons[i].setPosition(Core.VIRTUAL_WIDTH / 2 - BUTTON_W / 2, Core.VIRTUAL_HEIGHT / 2 - i * BUTTON_STEP);
			stage.addActor(buttons[i]);
		}
	}

	private void setWidgets() {
		backgroundImage = new Image(loadTexture("pauseMenu/background.png"));
		titleImage = new Image(loadTexture("pauseMenu/title.png"));
		resumeButton = new TextButton("Reanudar", Assets.skin);
		scoreButton = new TextButton("Puntuacion", Assets.skin);
		settingsButton = new TextButton("Opciones", Assets.skin);
		menuButton = new TextButton("Menu Principal", Assets.skin);
	}

	/** Hidden behind the scoreboard, the buttons must not react to clicks. */
	public void setInteractive(boolean interactive) {
		stage.getRoot().setTouchable(interactive ? Touchable.enabled : Touchable.disabled);
	}

	/** True while a settings page is shown. */
	public boolean isShowingSettings() {
		return page != null || pendingPage != null;
	}

	/**
	 * The pause key was pressed: inside the settings it goes back to the pause
	 * buttons (or belongs to the controls page, while it waits for a key).
	 *
	 * @return true if the key was used here, so the game must not unpause
	 */
	public boolean consumePauseKey() {
		if (!isShowingSettings())
			return false;
		if (page == null || !page.capturesKeys())
			settingsFlow.close();
		return true;
	}

	public void update(float delta) {
		applyPendingPage();
		if (!playerComponent.pause) {
			closePage();
			return;
		}
		if (page != null) {
			page.render(delta);
		} else {
			stage.act(delta);
			stage.draw();
		}
	}

	private void applyPendingPage() {
		if (closePending) {
			closePending = false;
			if (pendingPage != null)
				pendingPage.dispose();
			pendingPage = null;
			closePage();
		}
		if (pendingPage != null) {
			if (page != null)
				page.dispose();
			page = pendingPage;
			pendingPage = null;
			page.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		}
	}

	/** Back to the pause buttons. Disposing the page saves the settings. */
	private void closePage() {
		if (page == null)
			return;
		page.dispose();
		page = null;
		Gdx.input.setInputProcessor(stage);
	}

	@Override
	public void resize(int width, int height) {
		stage.getViewport().update(width, height);
		if (page != null)
			page.resize(width, height);
	}

	@Override
	public void dispose() {
		if (pendingPage != null)
			pendingPage.dispose();
		if (page != null)
			page.dispose();
		super.dispose();
	}
}
