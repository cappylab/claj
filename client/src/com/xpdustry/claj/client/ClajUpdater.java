/**
 * This file is part of CLaJ. The system that allows you to play with your friends,
 * just by creating a room, copying the link and sending it to your friends.
 * Copyright (c) 2026  Xpdustry
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.xpdustry.claj.client;

import arc.Events;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.mod.Mods;

import com.xpdustry.claj.client.util.VersionChecker;


public class ClajUpdater {
  /** Schedules an update check for 2 secs after the client has finished loading. */
  public static void schedule() {
    Events.on(EventType.ClientLoadEvent.class, _ ->
      Timer.schedule(ClajUpdater::checkForUpdate, 2)
    );
  }

  public static void checkForUpdate() {
    Mods.ModMeta meta = Main.getMeta();
    VersionChecker.checkAsyncFor(meta, s -> {
      if (!(s instanceof VersionChecker.UpdateState.Outdated)) return;
      Vars.ui.showCustomConfirm(
        "@claj.update.title", "@claj.update.text", "@claj.update.confirm", "@claj.update.ignore",
        () -> {
          // Open the mods menu, like that this will ask for a restart when closed
          Vars.ui.mods.show();
          Vars.ui.mods.githubImportMod(meta.repo, meta.java);
        },
        () -> {});
    });
  }
}
