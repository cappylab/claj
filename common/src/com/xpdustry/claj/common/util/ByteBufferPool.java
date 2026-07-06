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

package com.xpdustry.claj.common.util;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Function;


public final class ByteBufferPool {
  private static final ByteBufferPool INSTANCE = new ByteBufferPool();

  public static ByteBufferPool get() {
    return INSTANCE;
  }

  public static ByteBuffer getHeap(int size) {
    return get().obtain(size, false);
  }

  public static ByteBuffer getDirect(int size) {
    return get().obtain(size, true);
  }

  public static boolean free(ByteBuffer buff) {
    return get().release(buff);
  }


  /** Optimization to avoid a bucket with only empty buffers. */
  private static final ByteBuffer EMPTY_HEAP = ByteBuffer.allocate(0),
                                  EMPTY_DIRECT = ByteBuffer.allocateDirect(0);

  private final ConcurrentHashMap<Integer, Bucket> heaps, directs;
  private final Function<Integer, Bucket> newBucket;
  public final int factor, bucketCap;

  /** Creates a pool with a default {@link #factor} of {@code 1024} and a {@link #bucketCap} of {@code 512}. */
  public ByteBufferPool() {
    this(1024, 512);
  }

  public ByteBufferPool(int factor, int bucketCap) {
    this.heaps = new ConcurrentHashMap<>(8);
    this.directs = new ConcurrentHashMap<>(8);
    this.factor = factor;
    this.bucketCap = bucketCap;
    this.newBucket = _ -> new Bucket(bucketCap);
  }

  protected ByteBuffer newBuffer(boolean direct, int capacity) {
    return direct ? ByteBuffer.allocateDirect(capacity) : ByteBuffer.allocate(capacity);
  }

  public ByteBuffer obtain(int size) {
    return obtain(size, false);
  }

  /**
   * Get a buffer of the given {@code size} from free pool, or a new one. <br>
   * Capacity is rounded to the upper {@link #factor}, but limited to the given {@code size}.
   */
  public ByteBuffer obtain(int size, boolean direct) {
    if (size <= 0) return direct ? EMPTY_DIRECT : EMPTY_HEAP;
    int bucketSize = ((size + factor - 1) / factor) * factor;
    Bucket bucket = (direct ? directs : heaps).get(bucketSize);
    ByteBuffer buf = null;
    if (bucket != null) {
      buf = bucket.poll();
      if (buf != null) buf = (ByteBuffer)buf.clear();
    }
    if (buf == null) buf = newBuffer(direct, bucketSize);
    return (ByteBuffer)buf.limit(size);
  }

  /**
   * Double release protection not handled!
   * @return whether the buffer was added to the free buffer pool.
   *         A buffer might not be added for several reason: a {@code null} value, zero capacity,
   *         not at the defined {@link #factor}, or simply because the associated bucket is full.
   */
  public boolean release(ByteBuffer buf) {
    if (buf == null || buf.capacity() <= 0 || buf.capacity() % factor != 0) return false;
    return (buf.isDirect() ? directs : heaps).computeIfAbsent(buf.capacity(), newBucket).offer(buf);
  }

  /** Fill a {@code bucket} completely. */
  public void fill(int bucket) {
    fill(bucket, bucketCap, false);
  }

  public void fill(int bucket, int size) {
    fill(bucket, size, false);
  }

  /** Fill a {@code bucket} with {@code size} new buffers. */
  public void fill(int bucket, int size, boolean direct) {
    if (size <= 0 || bucket <= 0) return;
    int bucketSize = bucket * factor;
    Bucket b = (direct ? directs : heaps).computeIfAbsent(bucketSize, newBucket);
    for (int i=0; i<size; i++) {
      if (!b.offer(newBuffer(direct, bucketSize))) return;
    }
  }

  public void clear() {
    heaps.clear();
    directs.clear();
  }


  private static final class Bucket {
    final int capacity;
    final AtomicReferenceArray<ByteBuffer> chunk;
    final AtomicInteger head, tail;

    Bucket(int size) {
      capacity = size;
      chunk = new AtomicReferenceArray<>(size);
      head = new AtomicInteger(0);
      tail = new AtomicInteger(0);
    }

    ByteBuffer poll() {
      for (;;) {
        int h = head.get();
        int t = tail.get();
        if (h == t) return null; // Bucket empty
        ByteBuffer buf = chunk.get(h);
        if (buf != null && head.compareAndSet(h, (h + 1) % capacity)) {
          chunk.set(h, null); // Help GC
          return buf;
        }
        // Lost CAS race or slot is in mid-writing, re-read
      }
    }

    boolean offer(ByteBuffer buf) {
      for (;;) {
        int t = tail.get();
        int h = head.get();
        int i = (t + 1) % capacity;
        if (i == h) return false; // Bucket full
        if (tail.compareAndSet(t, i)) {
          chunk.set(t, buf);
          return true;
        }
        // Lost CAS race, re-read
      }
    }
  }
}
