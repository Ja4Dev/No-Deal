package com.juego.UI;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.LongArray;
import com.juego.guns.Gun;

public class HUD extends Menu {
	private final BitmapFont font;
	private final Label healthLabel;
	private final Label ammoLabel;
	private final Label timeLabel;
	private final Label feedLabel;
	private final Label centerLabel;
	private static final long FEED_DURATION_MS = 6000;
	private static final int FEED_LINES = 5;
	private final Array<String> feedMessages = new Array<>();
	private final LongArray feedTimes = new LongArray();

	public HUD() {
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
		healthLabel = new Label("100", style); // initial text sizes the label (layout)
		healthLabel.setFontScale(2f);
		healthLabel.setPosition(62.5f, 27.5f);
		ammoLabel = new Label("70", style);
		ammoLabel.setFontScale(2f);
		ammoLabel.setPosition(stage.getWidth() - 97.5f, 27.5f);
		timeLabel = new Label("15:00", style);
		timeLabel.setFontScale(2f);
		timeLabel.setPosition(stage.getWidth() / 2 - timeLabel.getPrefWidth() / 5, stage.getHeight() - 46);

		feedLabel = new Label("", new Label.LabelStyle(font, Color.WHITE));
		feedLabel.setFontScale(1.25f);
		centerLabel = new Label("", new Label.LabelStyle(font, Color.RED));
		centerLabel.setFontScale(2.5f);
		centerLabel.setAlignment(Align.center);

		stage.addActor(healthWidget);
		stage.addActor(ammoWidget);
		stage.addActor(crosshair);
		stage.addActor(timerWidget);
		stage.addActor(healthLabel);
		stage.addActor(ammoLabel);
		stage.addActor(timeLabel);
		stage.addActor(feedLabel);
		stage.addActor(centerLabel);
	}

	/** Adds a line to the kill feed (top right). Lines fade out after a few seconds. */
	public void addFeedMessage(String message) {
		feedMessages.add(message);
		feedTimes.add(System.currentTimeMillis());
		while (feedMessages.size > FEED_LINES) {
			feedMessages.removeIndex(0);
			feedTimes.removeIndex(0);
		}
	}

	/** Text of the clock widget at the top (match time left). */
	public void setClock(String text) {
		timeLabel.setText(text);
	}

	/** Big centered message, e.g. "Te eliminó X". Null or empty hides it. */
	public void setCenterMessage(String message) {
		centerLabel.setText(message == null ? "" : message);
	}

	private void layoutMessages() {
		long now = System.currentTimeMillis();
		while (feedTimes.size > 0 && now - feedTimes.first() > FEED_DURATION_MS) {
			feedMessages.removeIndex(0);
			feedTimes.removeIndex(0);
		}
		feedLabel.setText(String.join("\n", feedMessages));
		feedLabel.pack();
		feedLabel.setPosition(stage.getWidth() - 12, stage.getHeight() - 12, Align.topRight);
		centerLabel.pack();
		centerLabel.setPosition(stage.getWidth() / 2, stage.getHeight() * 0.8f, Align.center);
	}

	public void update(int health, Gun gun) {
		healthLabel.setText(health);
		if (gun.hasInfiniteReserve())
			ammoLabel.setText(gun.getMagazine());
		else
			ammoLabel.setText(gun.getMagazine() + "/" + gun.getReserve());
		draw();
	}

	private void draw() {
		layoutMessages();
		stage.getViewport().apply();
		stage.draw();
	}

	@Override
	public void dispose() {
		super.dispose();
		font.dispose();
	}
}
