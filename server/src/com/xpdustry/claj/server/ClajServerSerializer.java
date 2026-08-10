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
  static {
    // Set wrapper serializer
    ConnectionPayloadPacket.serializer = new ConnectionPayloadPacket.Serializer() {
      @Override
      public void read(ConnectionPayloadPacket packet, ByteBufferInput read) {
        packet.raw = raw.get().read(read.buffer);
      }
      @Override
      public void write(ConnectionPayloadPacket packet, ByteBufferOutput write) {
        packet.raw.write(write);
      }
    };
  }

  protected static final ThreadLocal<ByteBufferInput> read = Threads.local(ByteBufferInput::new);
  protected static final ThreadLocal<ByteBufferOutput> write = Threads.local(ByteBufferOutput::new);
  protected static final ThreadLocal<RawPacket> raw = Threads.local(RawPacket::new);

  /**
   * As there is only one serializer for CLaJ servers, the fast path can be used.
   * <p>
   * Remember to disable it, if using the serializer elsewhere.
   * Indeed, the fast path is only efficient if a single thread uses it frequently.
    */
  public static boolean FAST_THREAD_LOCAL = true;

  public NetworkSpeed networkSpeed, packetCounter;
  public boolean decodeClaj = true;

  public ClajServerSerializer() {}
  public ClajServerSerializer(NetworkSpeed networkSpeed, NetworkSpeed packetCounter) {
    this.networkSpeed = networkSpeed;
    this.packetCounter = packetCounter;
  }

  @Override
  public Object read(ByteBuffer buffer) {
    if (networkSpeed != null) networkSpeed.downloadMark(buffer.remaining());
    if (packetCounter != null) packetCounter.downloadMark();
    return switch (buffer.get()) {
      case ClajNet.frameworkId -> readFramework(buffer);
      case ClajNet.oldId -> readString(buffer);
      case ClajNet.id -> decodeClaj ? readClaj(buffer) : readRaw(buffer);
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
    Packet packet = ClajNet.newLocalPacket(buffer.get(), FAST_THREAD_LOCAL);
    if (!packet.allow(true))
      throw new ArcNetException("Invalid packet type for endpoint: " + packet.getClass().getName());
    ByteBufferInput in = read.get();
    in.buffer = buffer;
    packet.read(in);
    return packet;
  }

  public RawPacket readRaw(ByteBuffer buffer) {
    buffer.position(buffer.position()-1);
    return raw.get().read(buffer);
  }

  @Override
  public void write(ByteBuffer buffer, Object object) {
    int lastPos = networkSpeed != null ? buffer.position() : 0;
    switch (object) {
      case ByteBuffer buff -> buffer.put(buff);
      case FrameworkMessage framework -> writeFramework(buffer.put(ClajNet.frameworkId), framework);
      case String str -> writeString(buffer.put(ClajNet.oldId), str);
      case RawPacket raw -> raw.write(buffer);
      case Packet packet -> writeClaj(buffer.put(ClajNet.id), packet);
      default -> throw new ArcNetException("Unknown packet type: " + object.getClass().getName());
    }
    if (networkSpeed != null) networkSpeed.uploadMark(buffer.position() - lastPos);
    if (packetCounter != null) packetCounter.uploadMark();
  }

  public void writeClaj(ByteBuffer buffer, Packet packet) {
    buffer.put(ClajNet.getId(packet));
    ByteBufferOutput out = write.get();
    out.buffer = buffer;
    packet.write(out);
  }

  public void writeString(ByteBuffer buffer, String str) {
    ByteBufferOutput out = write.get();
    out.buffer = buffer;
    Strings.writeUTF(out, str);
  }
}