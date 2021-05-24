package com.juego.UI;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.juego.components.PlayerComponent;
import com.juego.systems.MatchSystem;

public class ScoreboardMenu extends Menu{
	private BitmapFont font;
	private Label label;
	private Image background;
	private Image resumeButton;
	private Image exitButton;
	private Image scoreButton;
	private Image menuButton;
	private PlayerComponent playerComponent;
	
	public ScoreboardMenu (PlayerComponent playerComponent, MatchSystem matchSystem) {
		
	}
}
