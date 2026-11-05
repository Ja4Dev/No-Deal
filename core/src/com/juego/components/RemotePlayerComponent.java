package com.juego.components;

import com.badlogic.ashley.core.Component;

/** Another player in an online match. Its position comes from server snapshots, not from physics. */
public class RemotePlayerComponent implements Component {
	public final int playerId;

	public RemotePlayerComponent(int playerId) {
		this.playerId = playerId;
	}
}
