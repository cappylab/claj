/**
 * This file is part of CLaJ. The system that allows you to play with your friends,
 * just by creating a room, copying the link and sending it to your friends.
 * Copyright (c) 2025-2026  Xpdustry
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

package com.xpdustry.claj.common.packets;

import java.nio.ByteBuffer;

import arc.util.Threads;
import arc.util.io.ByteBufferInput;
import arc.util.io.ByteBufferOutput;

import com.xpdustry.claj.common.util.ByteBufferPool;


/**
 * Bufferize the data in {@link #READ} at {@link #read(ByteBufferInput)} call. <br>
 * Real reading will be done at {@link #handled()} call.
 */
public abstract class DelayedPacket implements Packet {
  private static final ByteBuffer EMPTY = ByteBuffer.allocate(0);
  private static final ThreadLocal<ByteBufferInput> READ = Threads.local(() -> new ByteBufferInput(EMPTY));

  private ByteBuffer DATA = EMPTY;

  @Override
  public final void read(ByteBufferInput read) {
    DATA = ByteBufferPool.get().obtain(read.buffer.remaining());
    DATA.put(read.buffer).flip();
  }

  @Override
  public final void handled() {
    if (DATA == EMPTY) return; // avoid double reading
    ByteBufferInput read = READ.get();
    try {
      read.buffer = DATA;
      readImpl(read);
    } finally {
      ByteBufferPool.get().release(DATA);
      read.buffer = DATA = EMPTY;
    }
  }

  protected abstract void readImpl(ByteBufferInput read);
  @Override
  public abstract void write(ByteBufferOutput write);
}
