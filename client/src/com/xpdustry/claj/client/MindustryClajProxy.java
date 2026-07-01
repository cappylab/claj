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

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.NoSuchElementException;

import arc.Core;
import arc.net.Connection;
import arc.net.DcReason;
import arc.util.Structs;

import mindustry.Vars;
import mindustry.net.*;
import mindustry.net.Packets.KickReason;

import com.xpdustry.claj.api.ClajProvider;
import com.xpdustry.claj.api.ClajProxy;
import com.xpdustry.claj.api.net.VirtualConnection;


public class MindustryClajProxy extends ClajProxy {
  //TODO: still useful?
/*
  /** No-op rate-keeper to prevent the local mindustry server from life blacklisting the claj server. *\/
  private static final Ratekeeper noopRate = new Ratekeeper() {
    @Override
    public boolean allow(long spacing, int cap) {
      return true;
    }
  };
*/



  public MindustryClajProxy(ClajProvider provider) {
    super(provider);

    // Try to fix some issues with entities not loading when receiving world
    forceTcp = true;


/* Deprecated, i think...
    // Modify listener to set the noop rate
    receiver.handle(ConnectionJoinPacket.class, p -> {
      NetConnection net = toMindustryConnection(getConnection(p.conID));
      if (net == null) return;
      // Change the packet rate and chat rate to a no-op version to avoid a potential life blacklisting
      net.packetRate = noopRate;
      net.chatRate = noopRate;
    });
*/
  }

  public static boolean isMindustryConnection(Connection con) {
    return con.getArbitraryData() instanceof NetConnection;
  }

  public static NetConnection toMindustryConnection(Connection con) {
    return con != null && con.getArbitraryData() instanceof NetConnection nc ? nc : null;
  }

  static final Field connectionField;
  static {
    Field f = null;
    try {
      Class<?> clazz = Structs.find(ArcNetProvider.class.getDeclaredClasses(),
                                    c -> "ArcConnection".equals(c.getSimpleName()));
      if (clazz != null) f = clazz.getDeclaredField("connection");
    } catch (Exception _) {}
    connectionField = f;
  }

  /** Really difficult to convert as ArcConnection is package-private. Reflection is used. */
  public static VirtualConnection toVirtualConnection(NetConnection con) {
    if (connectionField == null) return null;
    try { return connectionField.get(con) instanceof VirtualConnection vcon ? vcon : null; }
    catch (Exception e) { return null; }
  }

  public Iterable<NetConnection> getMindustryConnections() {
    return () -> new Iterator<>() {
      NetConnection next;
      int index;

      @Override
      public boolean hasNext() {
        if (next != null) return true;
        while (index < getInternalConnections().size) {
          next = toMindustryConnection(getInternalConnections().get(index));
          if (next != null) return true;
          index++;
        }
        return false;
      }

      @Override
      public NetConnection next() {
        if (!hasNext()) throw new NoSuchElementException();
        NetConnection value = next;
        next = null;
        return value;
      }
    };
  }

  public int getMindustryConnectionsSize() {
    return getInternalConnections().count(MindustryClajProxy::isMindustryConnection);
  }

  /**
   * We cannot easily convert to VirtualConnection as ArcConnection is package-private.
   * So we'll need to use the reverse path
   */
  public VirtualConnection getConnection(NetConnection con) {
    //VirtualConnection vcon = toVirtualConnection(con);
    //return vcon != null && getConnection(vcon.getID()) == vcon ? vcon : null;
    return getInternalConnections().find(c -> {
      NetConnection nc = toMindustryConnection(c);
      return nc != null && nc == con;
    });
  }

  public void kickAllConnections(KickReason reason) {
    // No way to broadcast kick here
    for (NetConnection con : getMindustryConnections()) con.kick(reason);
  }

  @Override
  public void closeAllConnections(DcReason reason) {
    // Kick players before, if we can
    if (isConnected()) kickAllConnections(KickReason.serverClose);
    super.closeAllConnections(reason);
  }

  public Host getState() {
    // Not very efficient
    Host host = NetworkIO.readServerData(0, "localhost", NetworkIO.writeServerData());
    host.port = Core.settings.getInt("port", Vars.port);
    return host;
  }
}
