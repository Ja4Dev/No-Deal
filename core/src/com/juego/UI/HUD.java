package com.juego.UI;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.juego.guns.Gun;

public class HUD extends Menu {
	private final BitmapFont font;
	private final Label healthLabel;
	private final Label ammoLabel;
	private final Label timeLabel;

	public HUD(int health, int ammo) {
		Image healthWidget = new Image(loadTexture("HUD/HealthWidget.png"));
		healthWidget.setPosition(10, 11);
		Image ammoWidget = new Image(loadTexture("HUD/AmmoWidget.png"));
		ammoWidget.setPosition(stage.getWidth() - 150, 11);
		Image crosshair = new Image(loadTexture("HUD/Crosshair.png"));
		crosshair.setPosition(stage.getWidth() / 2 - crosshair.getWidth() / 2,
				stage.getHeight() / 2 - crosshair.getHeight() / 2);
		Image timerWidget = new Image(loadTexture("HUD/TimerWidget.png"));
		timerWidget.setPosition(stage.getWidth() / 2 - timerWidget.getWidth() / 2, stage.getHeight() - 60);

		// One font shared by every label; each label scales it on its own so the
		// sizes don't overwrite each other.
		font = new BitmapFont();
		Label.LabelStyle style = new Label.LabelStyle(font, Color.GREEN);
		healthLabel = new Label(Integer.toString(health), style);
		healthLabel.setFontScale(2f);
		healthLabel.setPosition(62.5f, 27.5f);
		ammoLabel = new Label(Integer.toString(ammo), style);
		ammoLabel.setFontScale(2f);
		ammoLabel.setPosition(stage.getWidth() - 97.5f, 27.5f);
		timeLabel = new Label("15:00", style);
		timeLabel.setFontScale(2f);
		timeLabel.setPosition(stage.getWidth() / 2 - timeLabel.getPrefWidth() / 5, stage.getHeight() - 46);

		stage.addActor(healthWidget);
		stage.addActor(ammoWidget);
		stage.addActor(crosshair);
		stage.addActor(timerWidget);
		stage.addActor(healthLabel);
		stage.addActor(ammoLabel);
		stage.addActor(timeLabel);
	}

	/** Primary gun: shows magazine and reserve ammo. */
	public void update(int health, int ammo, Gun gun) {
		healthLabel.setText(health);
		ammoLabel.setText(gun.magazine + "/" + ammo);
		draw();
	}

	/** Secondary gun: infinite reserve, shows only the magazine. */
	public void update(int health, Gun gun) {
		healthLabel.setText(health);
		ammoLabel.setText(gun.magazine);
		draw();
	}

	private void draw() {
		stage.getViewport().apply();
		stage.draw();
	}

	@Override
	public void dispose() {
		super.dispose();
		font.dispose();
	}
}
