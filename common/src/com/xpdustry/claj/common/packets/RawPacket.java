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

package com.xpdustry.claj.common.packets;

import java.nio.ByteBuffer;

import arc.util.io.ByteBufferInput;
import arc.util.io.ByteBufferOutput;
import com.xpdustry.claj.common.util.ByteBufferPool;


/**
 * Wrapper for {@link ByteBuffer} that implements {@link Packet}. <br>
 * This is only needed due to compatibility with receivers.
 */
public class RawPacket implements Packet {
  protected ByteBuffer data;
  protected boolean pooled;
  /** Auto-{@link #free} {@link #data} after {@link #write} call. */
  public boolean autoFree = true;

  public RawPacket() {}
  public RawPacket(ByteBuffer buffer) { this(buffer, false); }
  /** if {@code pooled}, {@link #free()} must be called after use, {@link #data} will be cleared. */
  public RawPacket(ByteBuffer buffer, boolean pooled) {
    this.pooled = pooled;
    this.data = copyRemaining(buffer, pooled);
  }
  public RawPacket(ByteBufferInput read) { this(read, false); }
  /** if {@code pooled}, {@link #free()} must be called after use, {@link #data} will be cleared. */
  public RawPacket(ByteBufferInput read, boolean pooled) {
    this.pooled = pooled;
    this.data = copyRemaining(read, pooled);
  }

  @Override
  public void read(ByteBufferInput read) {
    if (data == null) data = copyRemaining(read);
    else ((ByteBuffer) data.clear()).put(read.buffer).flip();
  }

  @Override
  public void write(ByteBufferOutput write) {
    // As we have a GC, this not very important if we miss some buffer to release
    // E.g. when the socket become closed before writing into
    if (autoFree) {
      try { write(data, write); }
      finally { free(); }
    } else write(data, write);
  }

  public ByteBuffer data() {
    return data;
  }

  public boolean pooled() {
    return pooled;
  }

  public void free() {
    if (pooled && data != null) ByteBufferPool.get().release(data);
    data = null;
    pooled = false;
  }

  // Helpers

  public RawPacket copy() {
    return new RawPacket(data, pooled);
  }

  public static ByteBuffer copyRemaining(ByteBufferInput in) { return copyRemaining(in.buffer); }
  public static ByteBuffer copyRemaining(ByteBufferInput in, boolean pooled) {
    return copyRemaining(in.buffer, pooled);
  }
  public static ByteBuffer copyRemaining(ByteBuffer src) { return copyRemaining(src, false); }
  /** if {@code pooled}, {@link ByteBufferPool#free()} must be called after use. */
  public static ByteBuffer copyRemaining(ByteBuffer src, boolean pooled) {
    int len = src.remaining();
    ByteBuffer data = pooled ? ByteBufferPool.get().obtain(len) : ByteBuffer.allocate(len);
    data.put(src).flip();
    return data;
  }

  public static ByteBuffer read(ByteBufferInput read, int length) { return read(read, length, false); }
  public static ByteBuffer read(ByteBufferInput read, int length, boolean pooled) {
    byte[] data = new byte[length];
    read.readFully(data);
    return pooled ? ByteBufferPool.get().obtain(length).put(data) : ByteBuffer.wrap(data);
  }

  /** Suppresses {@code src} reading. Optimized for backed array buffers. */
  public static void write(ByteBuffer src, ByteBufferOutput write) {
    if (src == null) return;
    if (src.hasArray()) {
      write.write(src.array(), src.arrayOffset() + src.position(), src.remaining());
    } else {
      // Not safe to write buffer directly
      //TODO: optimize
      int pos = src.position();
      byte[] bytes = new byte[src.remaining()];
      src.get(bytes);
      src.position(pos);
      write.write(bytes);
    }
  }
}