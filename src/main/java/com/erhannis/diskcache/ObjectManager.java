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

/**
 *
 * @author erhannis
 */
public class ObjectManager {
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
    WeakReference<Object> wr = new WeakReference<>(o);
    return (long)asdf();
  }

  public synchronized Object getObject(long handle) {
    return asdf();
  }
  
  //TODO removeHandle?
  
  private static Object asdf() {
    return null;
  }
}
