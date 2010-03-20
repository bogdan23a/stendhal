/***************************************************************************
 *                    (C) Copyright 2007-2010 - Stendhal                   *
 ***************************************************************************
 ***************************************************************************
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *                                                                         *
 ***************************************************************************
 */
package games.stendhal.server.entity.mapstuff.sign;

import games.stendhal.server.core.engine.dbcommand.ReadCharactersFromHallOfFameCommand;
import games.stendhal.server.core.events.TurnListener;
import games.stendhal.server.core.events.TurnNotifier;

import java.util.Iterator;
import java.util.List;

import marauroa.server.db.command.DBCommand;
import marauroa.server.db.command.DBCommandQueue;
import marauroa.server.db.command.ResultHandle;

/**
 * loads the content of a sign from the hall of fame. 
 *
 * @author hendrik
 */
public class SignFromHallOfFameLoader implements TurnListener {
	private Sign sign;
	private String introduction;
	private ResultHandle handle;
	private boolean lineBreaks;
	

	/**
	 * creates a new SignFromHallOfFameLoader
	 *
	 * @param sign the sign to modify
	 * @param introduction introduction for the sign
	 * @param fametype type of fame
	 * @param max maximum number of returned characters
	 * @param ascending sort ascending or descending
	 * @param lineBreaks should each player be put on a line by itself?
	 */
	public SignFromHallOfFameLoader(Sign sign, String introduction, String fametype, int max, boolean ascending, boolean lineBreaks) {
		this.sign = sign;
		this.introduction = introduction;
		this.handle = new ResultHandle();
		this.lineBreaks = lineBreaks;
		DBCommand command = new ReadCharactersFromHallOfFameCommand(fametype, max, ascending);
		DBCommandQueue.get().enqueueAndAwaitResult(command, handle);
	}


	public void onTurnReached(int currentTurn) {
		// if there is no result, wait some more
		List<ReadCharactersFromHallOfFameCommand> list = DBCommandQueue.get().getResults(ReadCharactersFromHallOfFameCommand.class, handle);
		if (list.isEmpty()) {
			TurnNotifier.get().notifyInTurns(0, this);
			return;
		}

		// update the sign
		ReadCharactersFromHallOfFameCommand command = list.get(0);
		List<String> players = command.getNames();

		if (lineBreaks) {
			StringBuilder builder = new StringBuilder();
			Iterator<String> it = players.iterator();
				while (it.hasNext()) {
				builder.append(it.next());
				if (it.hasNext()) {
					builder.append("\n");
				}
			}
			sign.setText(introduction + builder.toString());
		} else {
			sign.setText(introduction + players);
		}
		sign.notifyWorldAboutChanges();
	}

}
