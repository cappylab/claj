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

package com.xpdustry.claj.common.net;

import java.nio.ByteBuffer;

import arc.net.*;
import arc.util.pooling.*;


/** Poolable class to delay packet decoding and event running to another thread. */
public class ListenerEvent implements Runnable, Pool.Poolable {
  protected static final Pool<ListenerEvent> pool = Pools.get(ListenerEvent.class, ListenerEvent::new, 4096);
  protected static final ByteBuffer empty = ByteBuffer.allocate(0);

  /** -1: invalid, 0: connected, 1: disconnected, 2: received raw, 3: received, 4: idle. */
  protected byte type;
  protected Connection connection;
  protected DcReason reason;
  protected ByteBuffer buffer = empty;
  protected Object object;
  protected NetListener listener;
  protected NetSerializer serializer;
  protected boolean reset;

  @Override
  public void run() {
    if (reset) throw new RuntimeException("Event is resets");
    try {
      switch (type) {
        case 0 -> listener.connected(connection);
        case 1 -> listener.disconnected(connection, reason);
        case 2 -> listener.received(connection, serializer.read(buffer));
        case 3 -> listener.received(connection, object);
        case 4 -> listener.idle(connection);
        default -> throw new RuntimeException("Invalid event type: " + type);
      }
    } finally {
      synchronized (pool) {
        pool.free(this);
      }
    }
  }

  @Override
  public void reset() {
    reset = true;
    type = -1;
    connection = null;
    reason = null;
    buffer.clear();
    object = null;
    listener = null;
    serializer = null;
  }

  protected static ListenerEvent get(int type, Connection connection, DcReason reason, ByteBuffer buffer,
                                     Object object, NetListener listener, NetSerializer serializer) {
    ListenerEvent o;
    synchronized (pool) {
      o = pool.obtain();
    }
    o.reset = false;
    o.type = (byte)type;
    o.connection = connection;
    o.reason = reason;
    if (buffer != null) {
      if (buffer.capacity() <= o.buffer.capacity()) o.buffer.clear();
      else o.buffer = ByteBuffer.allocate(buffer.capacity());
      o.buffer.put(buffer).flip();
    }
    o.object = object;
    o.listener = listener;
    o.serializer = serializer;
    return o;
  }

  public static ListenerEvent ofConnected(Connection connection, NetListener listener) {
    return get(0, connection, null, null, null, listener, null);
  }

  public static ListenerEvent ofDisconnected(Connection connection, NetListener listener, DcReason reason) {
    return get(1, connection, reason, null, null, listener, null);
  }

  public static ListenerEvent ofReceived(Connection connection, NetListener listener, ByteBuffer buffer,
                                         NetSerializer serializer) {
    return get(2, connection, null, buffer, null, listener, serializer);
  }

  public static ListenerEvent ofReceived(Connection connection, NetListener listener, Object object) {
    return get(3, connection, null, null, object, listener, null);
  }

  public static ListenerEvent ofIdle(Connection connection, NetListener listener) {
    return get(4, connection, null, null, null, listener, null);
  }
}
