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

import java.util.Collection;
import java.util.Set;

import arc.func.Boolf;
import arc.struct.ObjectSet;


@SuppressWarnings("unchecked")
public class CompatibleObjectSet<T> extends ObjectSet<T> implements Set<T> {
  public CompatibleObjectSet() {}
  public CompatibleObjectSet(int initialCapacity) { super(initialCapacity); }
  public CompatibleObjectSet(ObjectSet<? extends T> set) { super(set); }
  public CompatibleObjectSet(int initialCapacity, float loadFactor) { super(initialCapacity, loadFactor); }

  public int size() { return size; }

  public Object[] toArray() { return super.toSeq().items; }
  public <E> E[] toArray(E[] a) {
    if (a.length < size) return (E[])toArray();
    int i = 0;
    for (T o : this) a[i++] = (E)o;
    for (; i<a.length;) a[i++] = null;
    return a;
  }

  public boolean add(Object e) { return super.add((T)e); }
  public boolean remove(Object key) { return super.remove((T)key); }
  public boolean contains(Object key) { return super.contains((T)key); }

  public boolean containsAll(Collection<?> c) {
    for (Object o : c) {
      if (!contains(o)) return false;
    }
    return true;
  }
  public boolean addAll(Collection<? extends T> c) { return anyOne(c, this::add); }
  public boolean retainAll(Collection<?> c) {
    boolean removed = false;
    for (T o : this) {
      if (!c.contains(o)) {
        remove(o);
        removed = true;
      }
    }
    return removed;
  }
  public boolean removeAll(Collection<?> c) { return anyOne(c, this::remove); }

  private static boolean anyOne(Collection<?> c, Boolf<Object> action) {
    boolean done = false;
    for (Object o : c) {
      if (action.get(o)) done = true;
    }
    return done;
  }
}
