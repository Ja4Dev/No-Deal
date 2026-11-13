package com.juego.UI;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.utils.Align;
import com.juego.components.PlayerComponent;
import com.juego.net.GameClient;
import com.juego.net.Protocol;
import com.juego.utils.Config;
import com.juego.core.Assets;
import com.juego.core.Core;
import com.juego.core.GameWorld;
import com.juego.screens.menues.ConfigMenuScreen;
import com.juego.screens.menues.MenuScreen;
import com.juego.screens.menues.SettingsFlow;
import com.juego.utils.Text;

/**
 * The in-game pause menu. Its "Opciones" button opens the same settings
 * pages as the main menu (sound, mouse, video, controls, crosshair), drawn over
 * the match; the pause key or "Volver" goes back to the pause buttons.
 */
public class PauseMenu extends Menu {
	private static final float BUTTON_W = 192, BUTTON_H = 48, BUTTON_STEP = 60;

	private Image backgroundImage, titleImage;
	private TextButton resumeButton, scoreButton, settingsButton, nameButton, menuButton;
	/** "Cambiar nombre": a text field with Aceptar and Cancelar, over the pause buttons. */
	private Table namePanel;
	private TextField nameField;
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
		nameButton.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				showNamePanel(true);
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
		TextButton[] buttons = { resumeButton, scoreButton, nameButton, settingsButton, menuButton };
		for (int i = 0; i < buttons.length; i++) {
			buttons[i].setSize(BUTTON_W, BUTTON_H);
			buttons[i].setPosition(Core.VIRTUAL_WIDTH / 2 - BUTTON_W / 2,
					Core.VIRTUAL_HEIGHT / 2 + BUTTON_STEP - i * BUTTON_STEP);
			stage.addActor(buttons[i]);
		}
		buildNamePanel();
	}

	private void setWidgets() {
		backgroundImage = new Image(loadTexture("pauseMenu/background.png"));
		titleImage = new Image(loadTexture(Text.image("pauseMenu/title.png")));
		resumeButton = new TextButton(Text.get("pause.resume"), Assets.skin);
		scoreButton = new TextButton(Text.get("pause.scoreboard"), Assets.skin);
		settingsButton = new TextButton(Text.get("pause.options"), Assets.skin);
		nameButton = new TextButton(Text.get("pause.rename"), Assets.skin);
		menuButton = new TextButton(Text.get("pause.mainMenu"), Assets.skin);
	}

	/** Gives the keyboard and mouse back to the pause menu (after the console closes). */
	public void takeInput() {
		Gdx.input.setInputProcessor(stage); // the console only opens outside the pause menu
	}

	private void buildNamePanel() {
		nameField = new TextField("", Assets.skin);
		nameField.setMaxLength(Protocol.MAX_NAME_LENGTH);
		nameField.setTextFieldListener((field, c) -> {
			if (c == '\r' || c == '\n')
				acceptName();
		});
		TextButton accept = new TextButton(Text.get("accept"), Assets.skin), cancel = new TextButton(Text.get("cancel"), Assets.skin);
		accept.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				acceptName();
			}
		});
		cancel.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				showNamePanel(false);
			}
		});
		namePanel = new Table();
		namePanel.setBackground(Assets.skin.newDrawable("white", new Color(0.05f, 0.08f, 0.05f, 0.95f)));
		namePanel.pad(18);
		namePanel.add(new Label(Text.get("pause.newName"), Assets.skin)).colspan(2).padBottom(8).row();
		namePanel.add(nameField).colspan(2).width(280).height(36).padBottom(12).row();
		namePanel.add(accept).width(134).height(44).padRight(12);
		namePanel.add(cancel).width(134).height(44);
		namePanel.pack();
		namePanel.setPosition(Core.VIRTUAL_WIDTH / 2, Core.VIRTUAL_HEIGHT / 2, Align.center);
		namePanel.setVisible(false);
		stage.addActor(namePanel);
	}

	private void showNamePanel(boolean show) {
		namePanel.setVisible(show);
		for (TextButton b : new TextButton[] { resumeButton, scoreButton, nameButton, settingsButton, menuButton })
			b.setVisible(!show);
		if (show) {
			GameClient.PlayerInfo me = gameWorld.session.client.getPlayer(gameWorld.session.client.getLocalId());
			nameField.setText(me != null ? me.name : Config.playerName);
			nameField.selectAll();
			stage.setKeyboardFocus(nameField);
		} else {
			stage.setKeyboardFocus(null);
		}
	}

	private void acceptName() {
		String typed = nameField.getText().trim();
		if (!typed.isEmpty())
			gameWorld.network.rename(typed);
		showNamePanel(false);
	}

	/** Whether the name panel has the keyboard (the pause key must close it, not unpause). */
	public boolean isEditingName() {
		return namePanel.isVisible();
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
		if (isEditingName()) {
			showNamePanel(false);
			return true;
		}
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
			if (namePanel.isVisible())
				showNamePanel(false);
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
			page.resize(com.juego.utils.Display.width(), com.juego.utils.Display.height());
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
