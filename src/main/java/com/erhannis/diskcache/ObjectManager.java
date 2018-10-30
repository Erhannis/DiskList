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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author erhannis
 */
public class ObjectManager {
  // I don't really like having a singleton, here - these are pretty difficult circumstances, though
  //TODO I could have the ID registration be separate from the cache management?  That'd be better, anyway.
  public static final ObjectManager singleton = new ObjectManager();

  private AtomicLong curId = new AtomicLong(1);

  private HashMap<Long, Object> objects = new HashMap<>();

  private final Connection conn;  
  
  private ObjectManager() {
    this(getTempFile());
  }

  private ObjectManager(File cache) {
    try {
      String url = "jdbc:sqlite:" + cache.getPath();
      conn = DriverManager.getConnection(url);
      conn.setAutoCommit(true);

      conn.createStatement().execute("CREATE TABLE IF NOT EXISTS objects (id INTEGER PRIMARY KEY, value BLOB);");
      conn.createStatement().execute("CREATE TABLE IF NOT EXISTS lists (list_id INTEGER, idx INTEGER, object_id INTEGER, PRIMARY KEY (list_id, idx));");
    } catch (SQLException ex) {
      throw new RuntimeException(ex);
    }
  }

  private static File getTempFile() {
    try {
      File f = File.createTempFile("cache", "db");
      f.deleteOnExit();
      return f;
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  public synchronized long getHandle(Object o) {
    if (o.uniqueId != 0) {
      o.uniqueId = curId.getAndIncrement();
      objects.put(o.uniqueId, o);
    }
    asdf(); //TODO Instrument class?
    return o.uniqueId;
  }

  public synchronized Object getObject(long handle) {
    return objects.get(handle);
  }

  public synchronized void removeHandle(Object o) {
    if (o.uniqueId != 0) {
      objects.remove(o);
    }
    asdf(); //TODO UN-instrument???
    asdf(); //TODO De-serialize and finalize.
    o.uniqueId = 0;
  }

  //TODO Should be private or something?
  /**
   * Do not call this method yourself. This method is called by the
   * instrumentation added to classes' `finalize` methods. If the object is
   * managed and will therefore be serialized, the finalization should probably
   * not occur at this point.
   *
   * @param o
   * @return
   */
  public synchronized boolean objectIsDying(Object o) {
    if (o.uniqueId != 0 && objects.containsKey(o.uniqueId)) {
      //TODO Add to serialization list, rather than here
      objects.remove(o.uniqueId);
      asdf();
      return false;
    }
    return true;
  }

  //<editor-fold defaultstate="collapsed" desc="List">
  protected synchronized void listRegister(DiskList list) {
    getHandle(list);
    conn.createStatement().execute();
    asdf();
  }

  protected synchronized void listAdd(DiskList list, Object o) {
    getHandle(o);
    asdf();
  }

  protected synchronized Object listGet(DiskList list, int index) {
    asdf();
  }
 
  /**
   * Assumes `list` has already been registered
   * @param list
   * @return 
   */
  protected synchronized int listSize(DiskList list) throws SQLException {
    ResultSet rs = conn.createStatement().executeQuery("SELECT count(*) FROM lists WHERE list_id = " + list.uniqueId);
    rs.first();
    int size = rs.getInt(1);
    rs.close();
    return size;
  }

  protected synchronized void listRemove(DiskList list, int index) {
    //TODO Be careful; you shouldn't necessarily delete the handle, yet; gotta reference count or something
    asdf();
  }
  //</editor-fold>

  @Override
  protected void finalize() throws Throwable {
    asdf(); //TODO Deserialize and allow-to-finalize all objects?  Kinda heavy
  }

  private static Object asdf() {
    return null;
  }
}
