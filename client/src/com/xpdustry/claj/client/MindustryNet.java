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

import mindustry.net.Net;
import mindustry.net.NetConnection;

import com.xpdustry.claj.api.Claj;
import com.xpdustry.claj.api.ClajProxy;


/** Modified {@link Net} that handles CLaJ broadcast system. */
public class MindustryNet extends Net {
  public MindustryNet() {
    super(MindustryClajProvider.mindustryProvider);
  }

  @Override
  public void send(Object object, boolean reliable) {
    MindustryClajProxy proxy = getProxy();
    if (proxy == null) super.send(object, reliable);
    else broadcast(proxy, null, null, object, reliable);
  }

  // V8 specific. Use specific bulk send methods when dropping V7 support.
  //@Override
  public void send(Object object, Iterable<NetConnection> connections, boolean reliable) {
    MindustryClajProxy proxy = getProxy();
    if (proxy == null) {
      //super.send(object, connections, reliable);
      for (NetConnection con : connections) con.send(object, reliable);
    } else broadcast(proxy, connections, null, object, reliable);
  }

  @Override
  public void sendExcept(NetConnection except, Object object, boolean reliable) {
    MindustryClajProxy proxy = getProxy();
    // Cannot exclude a CLaJ connection from broadcast
    if (proxy == null || proxy.getConnection(except) != null) super.sendExcept(except, object, reliable);
    else broadcast(proxy, null, except, object, reliable);
  }

  public MindustryClajProxy getProxy() {
    if (!server()) return null;
    ClajProxy proxy = Claj.get().proxies.get();
    return proxy.roomCreated() && proxy instanceof MindustryClajProxy mproxy ? mproxy : null;
  }

  public void broadcast(MindustryClajProxy proxy, Iterable<NetConnection> connections, NetConnection except,
                        Object object, boolean reliable) {
    for (NetConnection con : connections == null ? getConnections() : connections) {
      if (con == except || proxy.getConnection(con) != null) continue;
      con.send(object, reliable);
    }
    proxy.broadcast(object, reliable);
  }
}
