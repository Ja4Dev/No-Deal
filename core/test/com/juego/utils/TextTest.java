package com.juego.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.juego.UI.TouchControls;
import com.juego.bots.BotDifficulty;
import com.juego.utils.Controls.Action;

class TextTest {
	private final Text.Language before = Text.language();

	@AfterEach
	void restore() {
		Text.setLanguage(before);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, String[]> all() throws Exception {
		Field field = Text.class.getDeclaredField("TEXTS");
		field.setAccessible(true);
		return (Map<String, String[]>) field.get(null);
	}

	private static Set<String> matches(String regex, String text) {
		Set<String> found = new TreeSet<>();
		Matcher m = Pattern.compile(regex).matcher(text);
		while (m.find())
			found.add(m.group());
		return found;
	}

	/** A translation that lost a {0} or a %d would show a wrong message or crash String.format. */
	@Test
	void everyTextHasBothLanguagesWithTheSameBlanks() throws Exception {
		for (Map.Entry<String, String[]> e : all().entrySet()) {
			String es = e.getValue()[0], en = e.getValue()[1];
			assertFalse(es.isEmpty() || en.isEmpty(), e.getKey() + " is empty in a language");
			assertEquals(matches("\\{\\d+\\}", es), matches("\\{\\d+\\}", en), e.getKey() + ": different {n}");
			assertEquals(matches("%-?\\d*[a-z]", es).size(), matches("%-?\\d*[a-z]", en).size(),
					e.getKey() + ": different format specifiers");
		}
	}

	/** The menu font has no accents, so menu texts must not use them (in-game labels can). */
	@Test
	void menuTextsHaveNoAccents() throws Exception {
		for (Map.Entry<String, String[]> e : all().entrySet()) {
			String key = e.getKey();
			boolean menu = key.startsWith("menu.") || key.startsWith("options.") || key.startsWith("mp.")
					|| key.startsWith("crosshair.") || key.startsWith("controls.") || key.startsWith("action.")
					|| key.startsWith("pause.") || key.startsWith("difficulty.");
			if (!menu)
				continue;
			for (String text : e.getValue())
				if (!text.matches("[\\x20-\\x7E\\n]*"))
					fail(key + " has a character the menu font can't draw: " + text);
		}
	}

	@Test
	void valuesFillTheBlanks() {
		Text.setLanguage(Text.Language.ES);
		assertEquals("Ana se unió", Text.get("feed.joined", "Ana"));
		Text.setLanguage(Text.Language.EN);
		assertEquals("Ana joined", Text.get("feed.joined", "Ana"));
		assertEquals("Bot difficulty: Hard. Options: easy, medium, hard, expert",
				Text.get("server.difficultyNow", BotDifficulty.DIFICIL.label(), BotDifficulty.choices()));
	}

	@Test
	void aMissingKeyShowsItself() {
		assertEquals("no.such.key", Text.get("no.such.key"));
	}

	@Test
	void everyNameBuiltFromCodeHasATranslation() {
		for (Action action : Action.values())
			assertTrue(Text.has("action." + action.name()), action.name());
		for (BotDifficulty d : BotDifficulty.values())
			assertTrue(Text.has("difficulty." + d.name()), d.name());
		for (CrosshairSettings.Style s : CrosshairSettings.Style.values())
			assertTrue(Text.has("crosshair.style." + s.name()), s.name());
		assertEquals(6, CrosshairSettings.colorNames().length);
		for (String color : CrosshairSettings.colorNames())
			assertFalse(color.startsWith("crosshair."), color);
		for (TouchControls.Button b : TouchControls.Button.values())
			assertFalse(b.label().startsWith("touch."), b.name());
	}

	@Test
	void englishImagesOnlyInEnglish() {
		Text.setLanguage(Text.Language.ES);
		assertEquals("pauseMenu/title.png", Text.image("pauseMenu/title.png"));
		Text.setLanguage(Text.Language.EN);
		assertEquals("pauseMenu/title_en.png", Text.image("pauseMenu/title.png"));
		assertEquals("mainMenu/title.png", Text.image("mainMenu/title.png"), "no words in it: same image");
	}

	@Test
	void bothLanguagesParse() {
		assertEquals(Text.Language.EN, Text.Language.parse("en"));
		assertEquals(Text.Language.ES, Text.Language.parse("ES"));
		assertEquals(null, Text.Language.parse(""));
		assertNotEquals(Text.get("menu.options"), null);
	}
}
