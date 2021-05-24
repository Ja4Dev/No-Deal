package com.juego.UI;

import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.juego.core.Core;

public abstract class Menu {
	protected Stage stage;
	
	public Menu() {
		stage = new Stage(new FitViewport(Core.VIRTUAL_WIDTH, Core.VIRTUAL_HEIGHT));
	}
	
}
