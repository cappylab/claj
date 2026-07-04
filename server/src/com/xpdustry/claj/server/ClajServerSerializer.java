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

package com.xpdustry.claj.server;

import java.nio.ByteBuffer;

import arc.net.ArcNetException;
import arc.net.FrameworkMessage;
import arc.net.NetSerializer;
import arc.util.Threads;
import arc.util.io.ByteBufferInput;
import arc.util.io.ByteBufferOutput;

import com.xpdustry.claj.common.ClajNet;
import com.xpdustry.claj.common.net.FrameworkSerializer;
import com.xpdustry.claj.common.packets.*;
import com.xpdustry.claj.common.util.Strings;
import com.xpdustry.claj.server.util.NetworkSpeed;


public class ClajServerSerializer implements NetSerializer, FrameworkSerializer {
  public static boolean POOLED_BUFFERS = true;
  /** This includes {@link ConnectionPayloadPacket} and {@link RawPacket}. */
  public static boolean REUSE_RAW_PACKETS = false;


  private static final RawPacket rpi = new RawPacket();
  private static final ConnectionPayloadPacket cppi = new ConnectionPayloadPacket();
  private static final byte cpp = ClajNet.getId(ConnectionPayloadPacket.class);


  static {
    // Set wrapper serializer
    ConnectionPayloadPacket.serializer = new ConnectionPayloadPacket.Serializer() {
      @Override
      public void read(ConnectionPayloadPacket packet, ByteBufferInput read) {
        if (!REUSE_RAW_PACKETS) packet.raw = new RawPacket(read, POOLED_BUFFERS);
      }
      @Override
      public void write(ConnectionPayloadPacket packet, ByteBufferOutput write) {
        packet.raw.write(write);
      }
    };
  }


  protected final ThreadLocal<ByteBufferInput> read = Threads.local(ByteBufferInput::new);
  protected final ThreadLocal<ByteBufferOutput> write = Threads.local(ByteBufferOutput::new);
  protected final NetworkSpeed networkSpeed;

  /** @param networkSpeed is for debugging, sets to null to disable it */
  public ClajServerSerializer(NetworkSpeed networkSpeed) {
    this.networkSpeed = networkSpeed;
  }

  @Override
  public Object read(ByteBuffer buffer) {
    if (networkSpeed != null) networkSpeed.downloadMark(buffer.remaining());
    return switch (buffer.get()) {
      case ClajNet.frameworkId -> readFramework(buffer);
      case ClajNet.oldId -> readString(buffer);
      case ClajNet.id -> readClaj(buffer);
      // Non-claj packets are saved as raw buffer, to avoid re-serialization
      default -> readRaw(buffer);
    };
  }

  /** Note that an empty string is always returned. */
  public String readString(ByteBuffer buffer) {
    // We don't care of the data, it's just for compatibility reasons
    buffer.position(buffer.limit());
    return "";
  }

  public Packet readClaj(ByteBuffer buffer) {
    byte id = buffer.get();
    if (REUSE_RAW_PACKETS && id == cpp) {
      cppi.raw = rpi.set(buffer, false);
      return cppi;
    }

    Packet packet = ClajNet.newPacket(id);
    if(!packet.allow(true)) throw new ArcNetException("Invalid packet type for endpoint: " + packet.getClass());
    ByteBufferInput in = read.get();
    in.buffer = buffer;
    packet.read(in);
    return packet;
  }

  public RawPacket readRaw(ByteBuffer buffer) {
    buffer.position(buffer.position()-1);
    return REUSE_RAW_PACKETS ? rpi.set(buffer, false) : new RawPacket(buffer, POOLED_BUFFERS);
  }

  @Override
  public void write(ByteBuffer buffer, Object object) {
    int lastPos = 0;
    if (networkSpeed != null) lastPos = buffer.position();
    switch (object) {
      case ByteBuffer buff -> buffer.put(buff);
      case FrameworkMessage framework -> writeFramework(buffer.put(ClajNet.frameworkId), framework);
      case String str -> writeString(buffer, str);
      case Packet packet -> writeClaj(buffer, packet);
      default -> throw new ArcNetException("Unknown packet type: " + object.getClass().getName());
    }
    if (networkSpeed != null) networkSpeed.uploadMark(buffer.position() - lastPos);
  }

  public void writeClaj(ByteBuffer buffer, Packet packet) {
    if (!(packet instanceof RawPacket)) buffer.put(ClajNet.id).put(ClajNet.getId(packet));
    ByteBufferOutput out = write.get();
    out.buffer = buffer;
    packet.write(out);
  }

  public void writeString(ByteBuffer buffer, String str) {
    ByteBufferOutput out = write.get();
    out.buffer = buffer;
    buffer.put(ClajNet.oldId);
    Strings.writeUTF(out, str);
  }
}