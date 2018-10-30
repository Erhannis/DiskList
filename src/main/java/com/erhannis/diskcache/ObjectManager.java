/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.diskcache;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 *
 * @author erhannis
 */
public class ObjectManager {
  private AtomicLong curId = new AtomicLong(1);
  
  private HashMap<Long, Object> objects = new HashMap<>();
  
  public ObjectManager() throws IOException {
    this(getTempFile());
  }

  public ObjectManager(File cache) {
    String url = "jdbc:sqlite:" + cache.getPath();
    try (Connection conn = DriverManager.getConnection(url)) {
      conn.setAutoCommit(true);
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }
  
  private static File getTempFile() throws IOException {
    File f = File.createTempFile("cache", "db");
    f.deleteOnExit();
    return f;
  }
  
  public synchronized long getHandle(Object o) {
    if (o.uniqueId != 0) {
      o.uniqueId = curId.getAndIncrement();
    }
    asdf(); //TODO Instrument class?
    return o.uniqueId;
  }

  public synchronized Object getObject(long handle) {
    
    return asdf();
  }

  public synchronized void removeHandle(Object o) {
    if (o.uniqueId != 0) {
      objects.remove(o);
    }
    o.uniqueId = 0;
  }
  
  //TODO removeHandle?
  
  private static Object asdf() {
    return null;
  }
}
