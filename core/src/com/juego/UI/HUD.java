package com.juego.UI;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.juego.guns.Gun;

public class HUD extends Menu{
	private BitmapFont font;
	private Label healthLabel;
	private Label ammoLabel;
	private Label timeLabel;
	private Image healthWidget;
	private Image ammoWidget;
	private Image crosshair;
	private Image timerWidget;
	
	public HUD(int health, int ammo) {
	    healthWidget = new Image(new Texture(Gdx.files.internal("HUD/HealthWidget.png")));
	    healthWidget.setPosition(10, 11);
	    ammoWidget = new Image(new Texture(Gdx.files.internal("HUD/AmmoWidget.png")));
	    ammoWidget.setPosition(stage.getWidth() - 150, 11);
	    crosshair = new Image(new Texture(Gdx.files.internal("HUD/crosshair.png")));
	    crosshair.setScale(1);
	    crosshair.setPosition(stage.getWidth() / 2 - crosshair.getWidth() / 2, stage.getHeight() / 2 - crosshair.getHeight() / 2);
	    timerWidget = new Image(new Texture(Gdx.files.internal("HUD/TimerWidget.png")));
	    timerWidget.setPosition(stage.getWidth() / 2 - timerWidget.getWidth() / 2, stage.getHeight() - 60);
	    font = new BitmapFont();
	    healthLabel = new Label(Integer.toString(health), new Label.LabelStyle(font, Color.GREEN));
	    font.getData().setScale(3);
	    healthLabel.setPosition(62.5f, 27.5f);
	    ammoLabel = new Label(Integer.toString(ammo), new Label.LabelStyle(font, Color.GREEN));
	    font.getData().setScale(2.5f);
	    ammoLabel.setPosition(stage.getWidth()-97.5f, 11f);
	    timeLabel = new Label("15:00", new Label.LabelStyle(font, Color.GREEN));
	    font.getData().setScale(2);
	    timeLabel.setPosition(stage.getWidth() / 2 - timeLabel.getWidth() / 5, stage.getHeight() - 60);
	    stage.addActor(healthWidget);
	    stage.addActor(ammoWidget);
	    stage.addActor(crosshair);
	    stage.addActor(timerWidget);
	    stage.addActor(healthLabel);
	    stage.addActor(ammoLabel);
	    stage.addActor(timeLabel);
	}
	
	public void update(int health, int ammo, Gun gun, boolean paused) {
	      stage.draw();
	      healthLabel.setText(health);
	      ammoLabel.setText(gun.magazine + "/" + ammo);
	}
	
	public void update(int health, Gun gun, boolean paused) {
	      stage.draw();
	      healthLabel.setText(health);
	      ammoLabel.setText(gun.magazine);
	      
	}
}
