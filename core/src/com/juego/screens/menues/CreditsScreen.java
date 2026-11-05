package com.juego.screens.menues;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.juego.UI.Menu;
import com.juego.core.Assets;
import com.juego.core.Core;
import com.juego.screens.GameScreen;

public class CreditsScreen extends MenuScreen {
	
	Image backgroundImage, titleImage, creditsImage;
	TextButton backButton;
	
	public CreditsScreen(Core game) {
		super(game);
		setWidgets();
		configureWidgers();
		setListeners();
	}

	private void setListeners() {
		backButton.addListener(new ClickListener() {
			@Override
			public void clicked(InputEvent event, float x, float y) {
				game.setScreen(new MainMenuScreen(game));
			}
		});

	}

	private void configureWidgers() {
		
		backgroundImage.setSize(Core.VIRTUAL_WIDTH, Core.VIRTUAL_HEIGHT);
		titleImage.setSize(titleImage.getWidth() * 0.75f, titleImage.getHeight() * 0.75f);
		titleImage.setPosition(Core.VIRTUAL_WIDTH / 2 - titleImage.getWidth() / 2, Core.VIRTUAL_HEIGHT / 1.125f);
		creditsImage.setPosition(Core.VIRTUAL_WIDTH / 2 - creditsImage.getWidth() / 2, Core.VIRTUAL_HEIGHT / 2 - creditsImage.getHeight() / 2);
		backButton.setSize(128, 32);
		backButton.setPosition(Core.VIRTUAL_WIDTH / 2 - backButton.getWidth() / 2, Core.VIRTUAL_HEIGHT / 2 - 250);
			
		stage.addActor(backgroundImage);
		stage.addActor(titleImage);
		stage.addActor(creditsImage);
		stage.addActor(backButton);
	}

	private void setWidgets() {
		backgroundImage = new Image(loadTexture("creditsMenu/background.png"));
		titleImage = new Image(loadTexture("creditsMenu/title.png"));
		creditsImage = new Image(loadTexture("creditsMenu/credits.png"));
		backButton = new TextButton("Volver", Assets.skin);			
		
	}

	

}
