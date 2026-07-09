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

package com.xpdustry.claj.server.util;


/** Calculate speed of an arbitrary thing, per seconds. E.g. network speed; in bytes per seconds. (thread-safe)*/
public class NetworkSpeed {
  private static final long nanosPerSecond = 1_000_000_000;

  protected final Object uploadLock = new Object(), dowloadLock = new Object();
  protected long lastUpload, uploadSpeed, uploadAccum, totalUpload,
                 lastDownload, downloadSpeed, downloadAccum, totalDownload;

  public void uploadMark() { uploadMark(1); }
  public void uploadMark(int count) {
    synchronized (uploadLock) {
      uploadAccum += count;
      totalUpload += count;

      long time = System.nanoTime(), diff = time - lastUpload;
      if (diff >= nanosPerSecond) {
        uploadSpeed = uploadAccum * nanosPerSecond / diff;
        uploadAccum = 0;
        lastUpload = time;
      }
    }
  }

  public void downloadMark() { downloadMark(1); }
  public void downloadMark(int count) {
    synchronized (dowloadLock) {
      downloadAccum += count;
      totalDownload += count;

      long time = System.nanoTime(), diff = time - lastDownload;
      if (diff >= nanosPerSecond) {
        downloadSpeed = downloadAccum * nanosPerSecond / diff;
        downloadAccum = 0;
        lastDownload = time;
      }
    }
  }

  /** Number of things per second. E.g. bytes per seconds */
  public long uploadSpeed() {
    uploadMark(0); // Try to fill holes
    return uploadSpeed;
  }

  /** Number of things per second. E.g. bytes per seconds. */
public long downloadSpeed() {
    downloadMark(0); // Try to fill holes
    return downloadSpeed;
  }

  /** Total number of things. E.g. total bytes */
  public long totalUpload() {
    return totalUpload;
  }

  /** Total number of things. E.g. total bytes */
  public long totalDownload() {
    return totalDownload;
  }
}
