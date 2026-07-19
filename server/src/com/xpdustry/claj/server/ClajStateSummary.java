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

import java.lang.management.*;

import com.sun.management.OperatingSystemMXBean;

import arc.Core;
import arc.util.OS;

import com.xpdustry.claj.common.status.ClajVersion;
import com.xpdustry.claj.server.util.NetworkSpeed;


/**
 * Class that hold a summary of the current server state at his call. <br>
 * To get more statistics and more accurate results, the summary will rely on java management interfaces.
 */
public class ClajStateSummary {
  private static final RuntimeMXBean jvm = ManagementFactory.getRuntimeMXBean();
  private static final MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
  private static final OperatingSystemMXBean cpu = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);

  public final ClajVersion version;
  public final int majorVersion;
  public final String javaVersion;
  public final long uptime;
  public final int mainTps, netTps;
  public final long usedHeap, allocatedHeap, maxHeap;
  public final long usedMeta, allocatedMeta, maxMeta;
  //TODO: add buffers summary?
  /** in %. {@code -1} if unknown. */
  public final float javaCpuLoad, systemCpuLoad;
  public final int rooms, clients, connections;
  /** This ignores Ethernet/IP/TCP/ArcNet headers. {@code -1} if disabled. */
  public final long uploadSpeed, downloadSpeed, totalUpload, totalDownload;
  public final long uploadTransfert, downloadTransfert, totalTransfertUpload, totalTransfertDownload;

  @SuppressWarnings("deprecation")
  ClajStateSummary() {
    version =  ClajVars.version;
    majorVersion = ClajVars.version.majorVersion;
    javaVersion = OS.javaVersion;
    uptime = jvm.getUptime();
    mainTps = Core.graphics.getFramesPerSecond();
    netTps = ClajVars.relay.getFramesPerSecond();

    MemoryUsage usage = mem.getHeapMemoryUsage();
    usedHeap = usage.getUsed();
    allocatedHeap = usage.getCommitted();
    maxHeap = usage.getMax();
    usage = mem.getNonHeapMemoryUsage();
    usedMeta = usage.getUsed();
    allocatedMeta = usage.getCommitted();
    maxMeta = usage.getMax();
    javaCpuLoad = (float)(cpu.getProcessCpuLoad()*100);
    systemCpuLoad = (float)(cpu.getSystemCpuLoad()*100);

    rooms = ClajVars.relay.rooms.size;
    clients = ClajVars.relay.clientsInRooms();
    connections = ClajVars.relay.getConnections().length;

    NetworkSpeed net = ClajVars.relay.networkSpeed;
    if (net != null) {
      uploadSpeed = net.uploadSpeed();
      downloadSpeed = net.downloadSpeed();
      totalUpload = net.totalUpload();
      totalDownload = net.totalDownload();
    } else {
      uploadSpeed = downloadSpeed = totalUpload = totalDownload = -1;
    }
    net = ClajVars.relay.packetCounter;
    if (net != null) {
      uploadTransfert = net.uploadSpeed();
      downloadTransfert = net.downloadSpeed();
      totalTransfertUpload = net.totalUpload();
      totalTransfertDownload = net.totalDownload();
    } else {
      uploadTransfert = downloadTransfert = totalTransfertUpload = totalTransfertDownload = -1;
    }
  }

  public static ClajStateSummary now() {
    return new ClajStateSummary();
  }
}
