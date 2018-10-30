/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.diskcache;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
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

  private HashMap<Long, WeakReference<Object>> objects = new HashMap<>();

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
      objects.put(o.uniqueId, new WeakReference<Object>(o));
    }
    asdf(); //TODO Instrument class?
    return o.uniqueId;
  }

  public synchronized Object getObject(long handle) throws SQLException {
    WeakReference<Object> wr = objects.get(handle);
    if (wr != null) {
      Object o = wr.get();
      if (o != null) {
        //TODO Deal with null values
        return o;
      }
    }
    PreparedStatement pstmt = conn.prepareStatement("SELECT value FROM objects WHERE id = ?");
    pstmt.setLong(1, handle);
    ResultSet rs = pstmt.executeQuery();
    if (!rs.first()) {
      //TODO Should return null?
      throw new IndexOutOfBoundsException();
    }
    Blob objectBlob = rs.getBlob(1);
    asdf;
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
  //TODO Is this needed?
  protected synchronized void listRegister(DiskList list) {
    getHandle(list);
    asdf();
  }

  protected synchronized void listAdd(DiskList list, Object o) throws SQLException {
    long listId = getHandle(list);
    int idx = listSize(list); //TODO OW
    long objectId = getHandle(o);

    PreparedStatement pstmt = conn.prepareStatement("INSERT INTO lists(list_id, idx, object_id) VALUES (?,?,?)");
    pstmt.setLong(1, listId);
    pstmt.setInt(2, idx);
    pstmt.setLong(3, objectId);
    pstmt.executeUpdate();
  }

  protected synchronized Object listGet(DiskList list, int index) throws SQLException {
    long listId = getHandle(list);

    //TODO Weak hash map, avoid unnecessary reads
    PreparedStatement pstmt = conn.prepareStatement("SELECT object_id FROM lists WHERE list_id = ? AND idx = ?");
    pstmt.setLong(1, listId);
    pstmt.setInt(2, index);
    ResultSet rs = pstmt.executeQuery();
    if (!rs.first()) {
      throw new IndexOutOfBoundsException();
    }
    long objectId = rs.getLong(1);

    asdf();
  }

  //TODO Remove this?
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
