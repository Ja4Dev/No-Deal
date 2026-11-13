package com.juego.UI;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.LongArray;
import com.juego.guns.Gun;
import com.juego.match.Team;
import com.juego.utils.Text;
import com.juego.utils.Config;
import com.juego.utils.Display;
import com.juego.utils.Platform;

public class HUD extends Menu {
	private final BitmapFont font;
	/** For texts with colors ([#ff6152]...[]): the kill feed and the team scores. */
	private final BitmapFont markupFont;
	/** Team match: the score under the clock, and our team above the health. */
	private final Label teamScoreLabel, myTeamLabel;
	private final Image healthWidget, ammoWidget, timerWidget;
	private final Label healthLabel;
	private final Label ammoLabel;
	private final Label timeLabel;
	private final Label feedLabel;
	private final Label centerLabel;
	private final Label promptLabel;
	/** cl_showfps (top left) and cl_showspeed (under the crosshair): hidden when empty. */
	private final Label statsLabel, speedLabel, jumpLabel;
	private final Crosshair crosshair = new Crosshair();
	private boolean crosshairVisible = true;
	/** Extra gap of the dynamic crosshair, in pixels: target and what is drawn (eased towards it). */
	private float crosshairSpread, shownSpread;
	/** Offset from the center of the screen where the crosshair is drawn (following the recoil), in pixels. */
	private float crosshairX, crosshairY;
	// Damage feedback: red edges that flash on every hit (and stay faintly when
	// health is low), and an arc around the crosshair pointing where the hit came from.
	private final Texture vignetteTexture, indicatorTexture;
	private final Image vignette, indicator;
	private float flash, indicatorAlpha;
	private static final float FLASH_FADE_PER_SECOND = 1.6f;
	private static final float INDICATOR_FADE_PER_SECOND = 0.9f;
	private static final int LOW_HEALTH = 35;
	private static final long FEED_DURATION_MS = 6000;
	private static final int FEED_LINES = 5;
	private final Array<String> feedMessages = new Array<>();
	private final LongArray feedTimes = new LongArray();

	public HUD() {
		super(true); // by the edges of the screen, whatever its shape (see layout)
		healthWidget = new Image(loadTexture("HUD/HealthWidget.png"));
		healthWidget.setPosition(10, 11);
		ammoWidget = new Image(loadTexture("HUD/AmmoWidget.png"));
		timerWidget = new Image(loadTexture("HUD/TimerWidget.png"));

		// One font shared by every label; each label scales it on its own so the
		// sizes don't overwrite each other.
		font = new BitmapFont();
		Label.LabelStyle style = new Label.LabelStyle(font, Color.GREEN);
		healthLabel = new Label("100", style); // initial text sizes the label (layout)
		healthLabel.setFontScale(2f);
		healthLabel.setPosition(62.5f, 27.5f);
		ammoLabel = new Label("70", style);
		ammoLabel.setFontScale(2f);
		timeLabel = new Label("15:00", style);
		timeLabel.setFontScale(2f);

		markupFont = new BitmapFont();
		markupFont.getData().markupEnabled = true;
		feedLabel = new Label("", new Label.LabelStyle(markupFont, Color.WHITE));
		feedLabel.setFontScale(1.25f);
		teamScoreLabel = new Label("", new Label.LabelStyle(markupFont, Color.WHITE));
		teamScoreLabel.setFontScale(1.6f);
		teamScoreLabel.setAlignment(Align.center);
		myTeamLabel = new Label("", new Label.LabelStyle(markupFont, Color.WHITE));
		myTeamLabel.setFontScale(1.2f);
		centerLabel = new Label("", new Label.LabelStyle(font, Color.RED));
		centerLabel.setFontScale(2.5f);
		centerLabel.setAlignment(Align.center);
		promptLabel = new Label("", new Label.LabelStyle(font, Color.WHITE));
		promptLabel.setFontScale(1.4f);
		promptLabel.setAlignment(Align.center);
		statsLabel = new Label("", new Label.LabelStyle(font, Color.WHITE));
		statsLabel.setFontScale(1.2f);
		speedLabel = new Label("", new Label.LabelStyle(font, Color.WHITE));
		speedLabel.setFontScale(1.9f);
		speedLabel.setAlignment(Align.center);
		jumpLabel = new Label("", new Label.LabelStyle(font, Color.WHITE));
		jumpLabel.setFontScale(1.3f);
		jumpLabel.setAlignment(Align.center);

		vignetteTexture = new Texture(createVignette(256));
		vignetteTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
		vignette = new Image(vignetteTexture);
		vignette.setColor(1, 1, 1, 0);
		vignette.setTouchable(Touchable.disabled);
		indicatorTexture = new Texture(createIndicator(256));
		indicatorTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
		indicator = new Image(indicatorTexture);
		indicator.setSize(340, 340); // a ring well clear of the crosshair
		indicator.setOrigin(Align.center);
		indicator.setColor(1, 1, 1, 0);
		indicator.setTouchable(Touchable.disabled);

		stage.addActor(vignette); // first: under everything else
		stage.addActor(indicator);
		stage.addActor(healthWidget);
		stage.addActor(ammoWidget);
		stage.addActor(timerWidget);
		stage.addActor(healthLabel);
		stage.addActor(ammoLabel);
		stage.addActor(timeLabel);
		stage.addActor(feedLabel);
		stage.addActor(teamScoreLabel);
		stage.addActor(myTeamLabel);
		stage.addActor(centerLabel);
		stage.addActor(promptLabel);
		stage.addActor(statsLabel);
		stage.addActor(speedLabel);
		stage.addActor(jumpLabel);
		layout();
	}

	/** What sits by the right and top edges, or in the middle, follows the screen's size. */
	@Override
	protected void layout() {
		float width = stage.getWidth(), height = stage.getHeight();
		ammoWidget.setPosition(width - 150, 11);
		ammoLabel.setPosition(width - 97.5f, 27.5f);
		timerWidget.setPosition(width / 2 - timerWidget.getWidth() / 2, height - 60);
		timeLabel.setPosition(width / 2 - timeLabel.getPrefWidth() / 5, height - 46);
		vignette.setSize(width, height);
		indicator.setPosition(width / 2, height / 2, Align.center);
	}

	/** The FPS and ping line (cl_showfps); null hides it. */
	public void setStats(String text) {
		statsLabel.setText(text == null ? "" : text);
	}

	/**
	 * The speedometer (cl_showspeed): the speed now, and under it the speed of
	 * the last jump, green if it gained on the hop before, red if it lost.
	 * Null hides it.
	 *
	 * @param gain positive, negative, or 0 (white: the first hop, or not hopping)
	 */
	public void setSpeed(String speed, String jump, float gain) {
		speedLabel.setText(speed == null ? "" : speed);
		jumpLabel.setText(speed == null || jump == null ? "" : jump);
		jumpLabel.setColor(gain > 0 ? Color.GREEN : gain < 0 ? Color.SALMON : Color.WHITE);
	}

	/** Red, transparent in the middle and stronger towards the edges and corners. */
	private static Pixmap createVignette(int size) {
		Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
		Color c = new Color();
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				float dx = (x + 0.5f) / size * 2 - 1, dy = (y + 0.5f) / size * 2 - 1;
				float d = (float) Math.sqrt(dx * dx + dy * dy) / 1.414f; // 0 center, 1 corner
				float a = MathUtils.clamp((d - 0.35f) / 0.65f, 0, 1);
				pixmap.drawPixel(x, y, Color.rgba8888(c.set(0.75f, 0.02f, 0.02f, a * a * 0.9f)));
			}
		}
		return pixmap;
	}

	/** A short red arc at the top of a ring; rotated, it points anywhere around the crosshair. */
	private static Pixmap createIndicator(int size) {
		Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
		Color c = new Color();
		float outer = size / 2f - 2, inner = outer - 14, halfSpan = 26; // degrees
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				float dx = x + 0.5f - size / 2f, dy = size / 2f - (y + 0.5f); // y up
				float r = (float) Math.sqrt(dx * dx + dy * dy);
				float angle = Math.abs(MathUtils.atan2(dx, dy) * MathUtils.radiansToDegrees); // 0 = up
				if (r < inner - 1 || r > outer + 1 || angle > halfSpan + 1)
					continue;
				float edge = Math.min(Math.min(r - inner + 1, outer + 1 - r), (halfSpan + 1 - angle) * 0.6f);
				float a = MathUtils.clamp(edge, 0, 1) * (1 - angle / (halfSpan + 1) * 0.6f);
				pixmap.drawPixel(x, y, Color.rgba8888(c.set(1f, 0.15f, 0.1f, a)));
			}
		}
		return pixmap;
	}

	/**
	 * Shows a hit: the screen edges flash red, stronger for bigger hits.
	 *
	 * @param direction where the hit came from, in degrees clockwise from straight ahead, or NaN if unknown
	 */
	public void showDamage(int amount, float direction) {
		flash = Math.min(1f, flash + 0.35f + amount / 70f);
		if (!Float.isNaN(direction)) {
			indicatorAlpha = 1f;
			indicator.setRotation(-direction); // scene2d rotates counterclockwise
		}
	}

	/**
	 * @param visible       false hides it (no gun in hand, dead)
	 * @param spreadPixels  how far the dynamic crosshair opens (0 when it isn't dynamic)
	 * @param offsetX       where it is drawn, right of the center of the screen (following the recoil)
	 * @param offsetY       and above the center
	 */
	public void setCrosshair(boolean visible, float spreadPixels, float offsetX, float offsetY) {
		crosshairVisible = visible;
		crosshairSpread = spreadPixels;
		crosshairX = offsetX;
		crosshairY = offsetY;
	}

	/** Hint at the bottom of the screen, e.g. "[G] Agarrar AK-47". Null hides it. */
	public void setPrompt(String prompt) {
		promptLabel.setText(prompt == null ? "" : prompt);
	}

	/**
	 * Team match: the score under the clock (Red's left, Blue's right) and our
	 * team above the health. {@code mine} NONE hides them (deathmatch).
	 */
	public void setTeams(Team mine, int red, int blue) {
		if (!mine.isTeam()) {
			teamScoreLabel.setText("");
			myTeamLabel.setText("");
			return;
		}
		teamScoreLabel.setText("[#" + Team.RED.hex + "]" + Team.RED.label() + "  " + red + "[]     [#" + Team.BLUE.hex
				+ "]" + blue + "  " + Team.BLUE.label() + "[]");
		myTeamLabel.setText("[#" + mine.hex + "]" + Text.get("hud.team", mine.label()) + "[]");
	}

	/** Adds a line to the kill feed (top right): may have colors, so a "[" must come as "[[". Lines fade out. */
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

	/** Health, ammo and prompts are hidden while watching someone else (dead). */
	public void setPlayerWidgetsVisible(boolean visible) {
		healthWidget.setVisible(visible);
		ammoWidget.setVisible(visible);
		healthLabel.setVisible(visible);
		myTeamLabel.setVisible(visible);
		ammoLabel.setVisible(visible);
		promptLabel.setVisible(visible);
		speedLabel.setVisible(visible);
		jumpLabel.setVisible(visible);
	}

	/** Big centered message, e.g. "¡Ganaste la partida!". Null or empty hides it. */
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
		teamScoreLabel.pack();
		teamScoreLabel.setPosition(stage.getWidth() / 2, stage.getHeight() - 66, Align.top);
		myTeamLabel.pack();
		myTeamLabel.setPosition(12, 72, Align.bottomLeft);
		// On a touch screen the top right corner has the pause, scoreboard and console buttons.
		if (Platform.touch)
			feedLabel.setPosition(12, stage.getHeight() - 12, Align.topLeft);
		else
			feedLabel.setPosition(stage.getWidth() - 12, stage.getHeight() - 12, Align.topRight);
		centerLabel.pack();
		centerLabel.setPosition(stage.getWidth() / 2, stage.getHeight() * 0.8f, Align.center);
		promptLabel.pack();
		promptLabel.setPosition(stage.getWidth() / 2, stage.getHeight() * 0.3f, Align.center);
		statsLabel.pack();
		// Phones have the kill feed in the top left: the numbers go under the clock instead.
		if (Platform.touch)
			statsLabel.setPosition(stage.getWidth() / 2, stage.getHeight() - 76, Align.top);
		else
			statsLabel.setPosition(12, stage.getHeight() - 12, Align.topLeft);
		speedLabel.pack();
		speedLabel.setPosition(stage.getWidth() / 2, stage.getHeight() * 0.22f, Align.center);
		jumpLabel.pack();
		jumpLabel.setPosition(stage.getWidth() / 2, stage.getHeight() * 0.22f - 30, Align.center);
	}

	private void updateDamageEffects(int health) {
		float delta = Math.min(Gdx.graphics.getDeltaTime(), 0.1f);
		flash = Math.max(0, flash - FLASH_FADE_PER_SECOND * delta);
		indicatorAlpha = Math.max(0, indicatorAlpha - INDICATOR_FADE_PER_SECOND * delta);
		// Low health keeps a faint red edge, a little stronger the closer to dying.
		float lowHealth = health > 0 && health < LOW_HEALTH ? 0.25f + 0.25f * (1 - health / (float) LOW_HEALTH) : 0;
		vignette.getColor().a = Math.min(1f, Math.max(flash, lowHealth));
		indicator.getColor().a = indicatorAlpha;
	}

	/** @param gun the gun in hand; null only while dead after dropping it */
	public void update(int health, Gun gun) {
		healthLabel.setText(health);
		updateDamageEffects(health);
		if (gun == null || gun.def.isMelee())
			ammoLabel.setText("-");
		else if (gun.hasInfiniteReserve())
			ammoLabel.setText(gun.getMagazine());
		else
			ammoLabel.setText(gun.getMagazine() + "/" + gun.getReserve());
		draw();
	}

	private void draw() {
		layoutMessages();
		stage.getViewport().apply();
		stage.draw();
		drawCrosshair();
		stage.getViewport().apply(); // the crosshair draws over the whole window
	}

	private void drawCrosshair() {
		float delta = Math.min(Gdx.graphics.getDeltaTime(), 0.1f);
		float target = Math.min(crosshairSpread, Display.height() * 0.12f);
		// Opens fast, closes a little slower, like CS.
		float rate = target > shownSpread ? 30f : 12f;
		shownSpread += (target - shownSpread) * Math.min(1f, delta * rate);
		if (crosshairVisible)
			crosshair.draw(Config.crosshair, Display.width() / 2f + crosshairX,
					Display.height() / 2f + crosshairY, shownSpread);
	}

	@Override
	public void dispose() {
		super.dispose();
		font.dispose();
		markupFont.dispose();
		vignetteTexture.dispose();
		indicatorTexture.dispose();
		crosshair.dispose();
	}
}
