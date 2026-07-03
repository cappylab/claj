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

package com.xpdustry.claj.common;

import arc.func.Prov;
import arc.net.ArcNetException;
import arc.struct.*;
import arc.util.Threads;
import arc.util.pooling.Pool;

import com.xpdustry.claj.common.packets.Packet;
import com.xpdustry.claj.common.util.FastThreadLocal;


@SuppressWarnings("unchecked")
public class ClajNet {
  /** Identifier for framework messages. */
  public static final byte frameworkId = -2;
  /** Old CLaJ id. */
  public static final byte oldId = -3;
  /** Identifier for CLaJ packets. */
  public static final byte id = -4;

  /** Maximum number of packet that can be registered. */
  public static final int MAX_PACKETS = 1<<Byte.SIZE;

  protected static final ObjectIntMap<Class<?>> packetIds = new ObjectIntMap<>(16);
  protected static final IntMap<Prov<?>> packets = new IntMap<>(16);
  protected static final IntMap<ThreadLocal<?>> packetLocals = new IntMap<>(8);
  protected static final ObjectMap<Class<?>, ThreadLocal<?>> classPacketLocals = new ObjectMap<>(8);
  protected static final IntMap<Pool<?>> packetPools = new IntMap<>(8);
  protected static final ObjectMap<Class<?>, Pool<?>> classPacketPools = new ObjectMap<>(8);

  /**
   * Registers a new packet type for serialization. Ignores if already registered.
   * @throws IllegalArgumentException if no id is available for this packet. ({@code 256} packets max)
   */
  public static <T extends Packet> void register(Prov<T> cons) {
    Class<?> type = cons.get().getClass();
    if (packetIds.containsKey(type)) return;
    int id = packetIds.size;
    if (id >= MAX_PACKETS) throw new IllegalArgumentException("Packets limit reached");
    packetIds.put(type, id);
    packets.put(id, cons);
  }

  public static byte getId(Packet packet) { return getId(packet.getClass()); }
  public static byte getId(Class<? extends Packet> packet) {
    int id = packetIds.get(packet, -1);
    if(id == -1) throw new ArcNetException("Unknown packet type: " + packet);
    return (byte)id;
  }

  protected static Prov<?> getPacket(byte id) {
    Prov<?> packet = packets.get(id);
    if (packet == null) throw new ArcNetException("Unknown packet id: " + id);
    return packet;
  }

  public static <T extends Packet> T newPacket(byte id) {
    return (T)getPacket(id).get();
  }

  public static <T extends Packet> T newLocalPacket(byte id) { return newLocalPacket(id, true); }
  /**
   * For use with read, if packets are processed on the same thread.
   * <p>
   * The {@code fast} argument determines whether to use an implementation with a same thread use (MRU) fast path.
   * Default is {@code true}. <br>
   * You would set it to {@code false} only if you think the packet is likely to be frequently used
   * by multiple threads. As the fast path is only faster when a single thread is requesting it intensively. <br>
   * The argument is only taken into account during the first call for a given packet.
   */
  public static <T extends Packet> T newLocalPacket(byte id, boolean fast) {
    ThreadLocal<?> local = packetLocals.get(id);
    if (local == null) local = registerLocal(id, null, fast);
    return (T)local.get();
  }

  public static <T extends Packet> T newLocalPacket(Class<T> packet) { return newLocalPacket(packet, true); }
  /**
   * For use with send, as packets are serialized in-place.
   * <p>
   * The {@code fast} argument determines whether to use an implementation with a same thread use (MRU) fast path.
   * Default is {@code true}. <br>
   * You would set it to {@code false} only if you think the packet is likely to be frequently used
   * by multiple threads. As the fast path is only faster when a single thread is requesting it intensively. <br>
   * The argument is only taken into account during the first call for a given packet.
   */
  public static <T extends Packet> T newLocalPacket(Class<T> packet, boolean fast) {
    ThreadLocal<?> local = classPacketLocals.get(packet);
    if (local == null) local = registerLocal(getId(packet), packet, fast);
    return (T)local.get();
  }

  protected static ThreadLocal<?> registerLocal(byte id, Class<?> packet, boolean fast) {
    synchronized (packetLocals) {
      synchronized (classPacketLocals) {
        ThreadLocal<?> local;
        if ((local = packetLocals.get(id)) == null) { // safe check
          Prov<?> prov = getPacket(id);
          local = fast ? FastThreadLocal.with(prov): Threads.local(prov);
          packetLocals.put(id, local);
          classPacketLocals.put(packet == null ? prov.get().getClass() : packet, local);
        }
        return local;
      }
    }
  }

  /** For use with read, as packets are more likely to be processed on another thread. */
  public static <T extends Packet> T newPooledPacket(byte id) {
    Pool<?> pool = packetPools.get(id);
    if (pool == null) pool = registerPool(id, null);
    return (T)pool.obtain();
  }

  /** For use with send, if packets are serialized on another thread, or for other usages. */
  public static <T extends Packet> T newPooledPacket(Class<T> packet) {
    Pool<?> pool = classPacketPools.get(packet);
    if (pool == null) pool = registerPool(getId(packet), packet);
    return (T)pool.obtain();
  }

  /** Never free a packet get from {@link #newLocalPacket}!! */
  public static <T extends Packet> void freePooledPacket(T packet) {
    Pool<T> pool = (Pool<T>)classPacketPools.get(packet.getClass());
    if (pool != null) pool.free(packet);
  }

  protected static Pool<?> registerPool(byte id, Class<?> packet) {
    synchronized (packetPools) {
      synchronized (classPacketPools) {
        Pool<?> pool;
        if ((pool = packetPools.get(id)) == null) { // safe check
          Prov<?> prov = getPacket(id);
          pool = new Pool<>(8, 128) { protected Object newObject() { return prov.get(); } };
          packetPools.put(id, pool);
          classPacketPools.put(packet == null ? prov.get().getClass() : packet, pool);
        }
        return pool;
      }
    }
  }
}
