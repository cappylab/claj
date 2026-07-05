//
//  ========================================================================
//  Copyright (c) 1995-2015 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package com.xpdustry.claj.common.util;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Function;


public final class ByteBufferPool {
  private static final ByteBufferPool INSTANCE = new ByteBufferPool();
  /** Optimization to avoid a bucket with only empty buffers. */
  private static final ByteBuffer EMPTY = ByteBuffer.allocate(0);

  private final ConcurrentHashMap<Integer, Bucket> buckets = new ConcurrentHashMap<>();
  private final Function<Integer, Bucket> newBucket;
  public final int factor, bucketCap;

  /** Creates a pool with a default {@link #factor} of {@code 1024} and a {@link #bucketCap} of {@code 512}. */
  public ByteBufferPool() {
    this(1024, 512);
  }

  public ByteBufferPool(int factor, int bucketCap) {
    this.factor = factor;
    this.bucketCap = bucketCap;
    this.newBucket = _ -> new Bucket(bucketCap);
  }

  /**
   * Get a buffer of the given {@code size} from free pool, or a new one. <br>
   * Capacity is rounded to the upper {@link #factor}, but limited to the given {@code size}.
   */
  public ByteBuffer obtain(int size) {
    if (size <= 0) return EMPTY;
    int bucketSize = ((size + factor - 1) / factor) * factor;
    Bucket bucket = buckets.get(bucketSize);
    if (bucket != null) {
      ByteBuffer buf = bucket.poll();
      if (buf != null) return (ByteBuffer)((ByteBuffer)buf.clear()).limit(size);
    }
    return (ByteBuffer)ByteBuffer.allocate(bucketSize).limit(size);
  }

  /**
   * Double release protection not handled!
   * @return whether the buffer was added to the free buffer pool.
   *         A buffer might not be added for several reason: a {@code null} value, zero capacity,
   *         not at the defined {@link #factor}, or simply because the associated bucket is full.
   */
  public boolean release(ByteBuffer buf) {
    if (buf == null || buf.capacity() <= 0 || buf.capacity() % factor != 0) return false;
    return buckets.computeIfAbsent(buf.capacity(), newBucket).offer(buf);
  }

  /** Fill a {@code bucket} completely. */
  public void fill(int bucket) {
    fill(bucket, bucketCap);
  }

  /** Fill a {@code bucket} with {@code size} new buffers. */
  public void fill(int bucket, int size) {
    if (size <= 0 || bucket <= 0) return;
    int bucketSize = bucket * factor;
    Bucket b = buckets.computeIfAbsent(bucketSize, newBucket);
    for (int i=0; i<size; i++) {
      if (!b.offer(ByteBuffer.allocate(bucketSize))) return;
    }
  }

  public void clear() {
    buckets.clear();
  }


  public static ByteBufferPool get() {
    return INSTANCE;
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
        if (((t + 1) % capacity) == h) return false; // Bucket full
        if (tail.compareAndSet(t, (t + 1) % capacity)) {
          chunk.set(t, buf);
          return true;
        }
        // Lost CAS race, re-read
      }
    }
  }
}
