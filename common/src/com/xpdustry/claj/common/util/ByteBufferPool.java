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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;


public final class ByteBufferPool {
  private static final ByteBufferPool INSTANCE = new ByteBufferPool();
  private static final Function<Integer, Bucket> NEW_BUCKET = _ -> new Bucket();
  /** Optimization to avoid a bucket with only empty buffers. */
  private static final ByteBuffer EMPTY = ByteBuffer.allocate(0);

  private final ConcurrentHashMap<Integer, Bucket> buckets = new ConcurrentHashMap<>();
  public final int factor, bucketCap;

  public ByteBufferPool() { this(1024, 512); }
  public ByteBufferPool(int factor, int bucketCap) {
    this.factor = factor;
    this.bucketCap = bucketCap;
  }

  public ByteBuffer obtain(int size) {
    if (size <= 0) return EMPTY;
    int bucketSize = (size + factor - 1) / factor * factor;
    Bucket bucket = buckets.get(bucketSize);
    if (bucket != null) {
      ByteBuffer buf = bucket.poll();
      if (buf != null) return (ByteBuffer)((ByteBuffer)buf.clear()).limit(size);
    }
    return (ByteBuffer)ByteBuffer.allocate(bucketSize).limit(size);
  }

  /** Double release protection not handled! */
  public void release(ByteBuffer buf) {
    if (buf == null || buf.capacity() <= 0 || buf.capacity() % factor != 0) return;
    buckets.computeIfAbsent(buf.capacity(), NEW_BUCKET).offer(buf, bucketCap);
  }

  /** Fill a {@code bucket} (starting from 1) with buffers until {@link #bucketCap}. */
  public void fill(int bucket) { fill(bucket, bucketCap); }
  /** Fill a {@code bucket} (starting from 1) with {@code size} buffers. */
  public void fill(int bucket, int size) {
    if (size <= 0 || bucket <= 0) return;
    List<ByteBuffer> buffers = new ArrayList<>(size);
    for (int i=0; i<size; i++) buffers.add(ByteBuffer.allocate(bucket * factor));
    buckets.computeIfAbsent(bucket * factor, NEW_BUCKET).offer(buffers, bucketCap);
  }

  public void clear() {
    buckets.clear();
  }


  public static ByteBufferPool get() {
    return INSTANCE;
  }


  private static final class Bucket {
    //TODO: optimize to avoid making a node every times we freeing a buffer
    final ConcurrentLinkedQueue<ByteBuffer> queue = new ConcurrentLinkedQueue<>();
    final AtomicInteger size = new AtomicInteger();

    ByteBuffer poll() {
      ByteBuffer buf = queue.poll();
      if (buf != null) size.getAndDecrement();
      return buf;
    }

    void offer(ByteBuffer buf, int cap) {
      if (size.get() >= cap) return;
      size.getAndIncrement();
      queue.offer(buf);
    }

    void offer(List<ByteBuffer> buffers, int cap) {
      int add = buffers.size();
      if (size.get() + add > cap) buffers.subList(add - (size.get() - add), add).clear();
      queue.addAll(buffers);
      size.getAndAdd(add);
    }
  }
}
