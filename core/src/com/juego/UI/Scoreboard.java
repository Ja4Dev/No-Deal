package com.juego.UI;

import java.util.Comparator;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.juego.utils.Text;

/** Score table shown while holding the scoreboard key, from the pause menu, and between matches. */
public class Scoreboard extends Menu {

	public static class Row {
		public String name;
		public int kills, deaths, assists;
		public boolean local;

		public Row set(String name, int kills, int deaths, int assists, boolean local) {
			this.name = name;
			this.kills = kills;
			this.deaths = deaths;
			this.assists = assists;
			this.local = local;
			return this;
		}
	}

	/** Most kills first; ties go to fewer deaths, then alphabetical. */
	public static final Comparator<Row> RANKING = (a, b) -> a.kills != b.kills ? b.kills - a.kills
			: a.deaths != b.deaths ? a.deaths - b.deaths : a.name.compareToIgnoreCase(b.name);

	private static final Color HEADER = new Color(0.75f, 0.75f, 0.75f, 1);
	private static final Color LOCAL = new Color(1f, 0.85f, 0.2f, 1);
	private static final Color RENAMED = new Color(0.55f, 0.85f, 1f, 1);

	// The HUD's built-in font covers Latin-1, so player names with accents render fine.
	private final BitmapFont font = new BitmapFont();
	private final Texture panel;
	private final Table table = new Table();
	/** Hangs from the panel's top-right corner: who changed their name in this match. */
	private final Table renamesTag = new Table();
	private final Label.LabelStyle style = new Label.LabelStyle(font, Color.WHITE);

	public Scoreboard() {
		super(true); // centered on the whole screen, whatever its shape
		Pixmap pixel = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
		pixel.setColor(0, 0, 0, 0.7f);
		pixel.fill();
		panel = new Texture(pixel);
		pixel.dispose();
		table.setBackground(new TextureRegionDrawable(panel));
		table.pad(20);
		stage.addActor(table);
		renamesTag.setBackground(new TextureRegionDrawable(panel));
		renamesTag.pad(6, 12, 6, 12);
		stage.addActor(renamesTag);
	}

	/**
	 * Rebuilds the table. Rows are sorted in place.
	 *
	 * @param renames "Old -> New" for names changed in this match, shown in the corner (may be empty)
	 */
	public void update(String title, Array<Row> rows, String footer, Array<String> renames) {
		rows.sort(RANKING);
		table.clearChildren();
		table.add(label(title, Color.WHITE, 1.6f)).colspan(5).padBottom(14).row();
		table.add(label("#", HEADER, 1.2f)).width(40);
		table.add(label(Text.get("score.player"), HEADER, 1.2f)).width(260).left();
		table.add(label(Text.get("score.kills"), HEADER, 1.2f)).width(90);
		table.add(label(Text.get("score.deaths"), HEADER, 1.2f)).width(90);
		table.add(label(Text.get("score.assists"), HEADER, 1.2f)).width(90);
		table.row().padBottom(6);
		for (int i = 0; i < rows.size; i++) {
			Row row = rows.get(i);
			Color color = row.local ? LOCAL : Color.WHITE;
			table.add(label(Integer.toString(i + 1), color, 1.3f));
			table.add(label(row.name, color, 1.3f)).left();
			table.add(label(Integer.toString(row.kills), color, 1.3f));
			table.add(label(Integer.toString(row.deaths), color, 1.3f));
			table.add(label(Integer.toString(row.assists), color, 1.3f));
			table.row().padBottom(4);
		}
		if (footer != null && !footer.isEmpty())
			table.add(label(footer, HEADER, 1.2f)).colspan(5).padTop(14);
		table.pack();
		table.setPosition(stage.getWidth() / 2, stage.getHeight() / 2, Align.center);

		renamesTag.clearChildren();
		renamesTag.setVisible(renames.size > 0);
		if (renames.size > 0) {
			renamesTag.add(label(Text.get("score.renames"), HEADER, 1f)).right().row();
			for (String rename : renames)
				renamesTag.add(label(rename.replace(" -> ", "  >  "), RENAMED, 1.05f)).right().row();
			renamesTag.pack();
			// Just above the panel, aligned with its right edge.
			renamesTag.setPosition(table.getX() + table.getWidth(), table.getY() + table.getHeight() + 4,
					Align.bottomRight);
		}
	}

	private Label label(String text, Color color, float scale) {
		Label label = new Label(text, style);
		label.setColor(color);
		label.setFontScale(scale);
		label.setAlignment(Align.center);
		return label;
	}

	public void draw() {
		stage.getViewport().apply();
		stage.draw();
	}

	@Override
	public void dispose() {
		super.dispose();
		panel.dispose();
		font.dispose();
	}
}
