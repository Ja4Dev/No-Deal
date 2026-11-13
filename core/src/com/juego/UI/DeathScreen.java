package com.juego.UI;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.juego.utils.Text;

/**
 * What you see while dead, over the spectator view: who killed you, with what,
 * their kills, deaths and assists, and how long until you're back.
 */
public class DeathScreen extends Menu {
	private static final Color KILLER = new Color(1f, 0.45f, 0.3f, 1), DIM = new Color(0.75f, 0.75f, 0.75f, 1);

	private final BitmapFont font = new BitmapFont();
	private final Texture panelTexture;
	private final Table panel = new Table();
	private final Label countdown, title, name, weapon, stats;

	public DeathScreen() {
		Pixmap pixel = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
		pixel.setColor(0, 0, 0, 0.65f);
		pixel.fill();
		panelTexture = new Texture(pixel);
		pixel.dispose();

		countdown = label(Color.WHITE, 2f);
		title = label(DIM, 1.3f);
		name = label(KILLER, 2.4f);
		weapon = label(DIM, 1.3f);
		stats = label(Color.WHITE, 1.5f);

		panel.setBackground(new TextureRegionDrawable(panelTexture));
		panel.pad(14, 30, 16, 30);
		panel.add(title).row();
		panel.add(name).padTop(2).row();
		panel.add(weapon).padTop(2).row();
		panel.add(stats).padTop(10);
		stage.addActor(panel);
		stage.addActor(countdown);
	}

	private Label label(Color color, float scale) {
		Label label = new Label("", new Label.LabelStyle(font, color));
		label.setFontScale(scale);
		label.setAlignment(Align.center);
		return label;
	}

	/**
	 * @param killer   killer's name, or null if nobody killed us
	 * @param fell     we fell out of the map
	 * @param gun      what they used ("" if unknown)
	 * @param respawnMs time until we're back
	 */
	public void update(String killer, boolean fell, String gun, boolean headshot, int kills, int deaths, int assists,
			long respawnMs) {
		countdown.setText(respawnMs > 0 ? Text.get("death.respawnIn", (respawnMs + 999) / 1000)
				: Text.get("death.respawning"));
		if (killer != null) {
			title.setText(Text.get("death.killedBy"));
			name.setText(killer);
			weapon.setText(gun.isEmpty() ? "" : gun + (headshot ? Text.get("death.headshot") : ""));
			stats.setText(Text.get("death.stats", kills, deaths, assists));
		} else {
			title.setText("");
			name.setText(Text.get(fell ? "death.fell" : "death.died"));
			weapon.setText("");
			stats.setText("");
		}
		weapon.setVisible(!weapon.getText().isEmpty());
		stats.setVisible(!stats.getText().isEmpty());
		panel.pack();
		panel.setPosition(stage.getWidth() / 2, 110, Align.bottom);
		countdown.pack();
		countdown.setPosition(stage.getWidth() / 2, stage.getHeight() * 0.78f, Align.center);
	}

	public void draw() {
		stage.getViewport().apply();
		stage.draw();
	}

	@Override
	public void dispose() {
		super.dispose();
		font.dispose();
		panelTexture.dispose();
	}
}
