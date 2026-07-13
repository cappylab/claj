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

package com.xpdustry.claj.server.util;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.channels.Selector;
import java.util.*;

import arc.struct.ObjectSet;


public class NioUtils {
  private static Field skf, pskf;
  private static boolean useUnsafe;
  private static Object theUnsafe;
  private static long sko, psko;
  private static Method pom;

  private static void applyOptimizations(Selector selector) throws Exception {
    // Cache fields via reflection. Will also determine whether the use of Unsafe is needed.
    if (skf == null || pskf == null) {
      // Find the class. The name never changed, but keep that safe.
      Class<?> clazz = selector.getClass();
      while (!"sun.nio.ch.SelectorImpl".equals(clazz.getName()) && clazz != Selector.class) {
        clazz = clazz.getSuperclass();
      }
      if (clazz == Selector.class) throw new RuntimeException("Unable to locate 'sun.nio.ch.SelectorImpl'");

      // The names never changed
      skf = clazz.getDeclaredField("selectedKeys");
      pskf = clazz.getDeclaredField("publicSelectedKeys");

      // Check standard way
      try {
        skf.setAccessible(true);
        pskf.setAccessible(true);
        useUnsafe = false;
      } catch (Exception e) {
        useUnsafe = true;
      }
    }

    // Cache unsafe methods, if we using it. Reflection is needed, otherwise compilation will fail.
    if (useUnsafe && theUnsafe == null) {
      Class<?> clazz = Class.forName("sun.misc.Unsafe");
      Field f = clazz.getDeclaredField("theUnsafe");
      f.setAccessible(true);
      theUnsafe = f.get(null);
      Method m = clazz.getDeclaredMethod("objectFieldOffset", Field.class);
      sko = (long)m.invoke(theUnsafe, skf);
      psko = (long)m.invoke(theUnsafe, pskf);
      pom = clazz.getDeclaredMethod("putObject", Object.class, long.class, Object.class);
    }

    // Change fields with same set
    Set<Object> set = new CompatibleObjectSet<>();
    if (useUnsafe) {
      pom.invoke(theUnsafe, selector, sko, set);
      pom.invoke(theUnsafe, selector, psko, set);
    } else {
      skf.set(selector, set);
      pskf.set(selector, set);
    }

    // Successfully optimized
  }

  public static Selector newSelector() throws IOException {
    return Selector.open();
  }

  /**
   * @return a selector with an optimized {@link Selector#selectedKeys()} that does not allocate a new node
   *         everytimes a key become available, and reuses iterators.
   * @throws RuntimeException if the method fails to optimize the selector.
   */
  public static Selector newOptimizedSelector() throws IOException, RuntimeException {
      Selector selector = newSelector();
      try {
        applyOptimizations(selector);
        return selector;
      } catch (Exception e) {
        try { selector.close(); }
        catch (Exception ignored) {}
        throw new RuntimeException("Cannot optimize new selector with reflection or unsafe", e);
      }
  }


  /**
   * Set class making compatibility between arc and java collections. <br>
   * Only adapted for nio use, as bulk methods are not defined.
   */
  @SuppressWarnings("unchecked")
  private static class CompatibleObjectSet<T> extends ObjectSet<T> implements Set<T> {
    public CompatibleObjectSet() {}
    public int size() { return size; }
    public Object[] toArray() { return super.toSeq().items; }
    public <E> E[] toArray(E[] a) { return (E[])toArray(); }
    public boolean add(Object e) { return super.add((T)e); }
    public boolean remove(Object key) { return super.remove((T)key); }
    public boolean contains(Object key) { return super.contains((T)key); }
    public boolean containsAll(Collection<?> c) { throw new UnsupportedOperationException(); }
    public boolean addAll(Collection<? extends T> c) { throw new UnsupportedOperationException(); }
    public boolean retainAll(Collection<?> c) { throw new UnsupportedOperationException(); }
    public boolean removeAll(Collection<?> c) { throw new UnsupportedOperationException(); }
  }
}
