/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.diskcache;

import com.ea.agentloader.AgentLoader;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import java.io.ByteArrayOutputStream;
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
import javax.sql.rowset.serial.SerialBlob;

/**
 * This all is kindof a mess. I half-tried to write it cleanly, but it's also
 * just kindof a proof-of-concept. Upon re-write, I should more clearly define
 * the transitions from memory to disk, back, and to death from either location.
 *
 * Rewrite might be organized like: getLocation(long) - MEMORY, DISK, NONE
 * manageObject(Object) (should return handle?) unmanageObject(Object) (should
 * accept handle?) moveFromMemoryToDisk(long) moveFromDiskToMemory(long)
 * countDiskReferences(long) ...
 *
 * Should probably also standardize whether internally I use getHandle or
 * directly pull the handle
 *
 * @author erhannis
 */
public class ObjectManager {
  private final Kryo mKryo;

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
    AgentLoader.loadAgentClass(FinalizationAgent.class.getName(), null);

    mKryo = new Kryo();
    mKryo.setRegistrationRequired(false); //TODO Is this a security hazard?
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
    //TODO Handle null?
    if (o.uniqueId != 0) {
      o.uniqueId = curId.getAndIncrement();
      objects.put(o.uniqueId, new WeakReference<Object>(o));
    }
    FinalizationAgent.instrument(o.getClass());
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
    Input input = new Input(objectBlob.getBinaryStream());
    Object o = mKryo.readClassAndObject(input);
    //TODO Do we need to set uniqueId?
    input.close();
    rs.close();
    objects.put(handle, new WeakReference<>(o));
    return o;
  }

  public synchronized void removeHandle(Object o) {
    if (o.uniqueId != 0) {
      objects.remove(o);
    }
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
   * @return true if finalization should continue. false if should abort.
   */
  public synchronized boolean objectIsDying(Object o) throws SQLException {
    if (o.uniqueId != 0 && objects.containsKey(o.uniqueId)) {
      objects.remove(o.uniqueId);
      //TODO Add to serialization list, rather than here
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      Output output = new Output(baos);
      mKryo.writeClassAndObject(output, o);
      output.flush();
      output.close();
      Blob blob;
      try {
        blob = new SerialBlob(baos.toByteArray());
      } catch (SQLException ex) {
        throw new RuntimeException(ex);
      }
      PreparedStatement pstmt = conn.prepareStatement("INSERT INTO objects(id, value) VALUES (?,?)");
      pstmt.setLong(1, o.uniqueId);
      pstmt.setBlob(2, blob);
      pstmt.executeUpdate();
      return false;
    }
    return true;
  }

  //<editor-fold defaultstate="collapsed" desc="List">
  //TODO Is this needed?
  protected synchronized void listRegister(DiskList list) {
    getHandle(list);
  }

  protected synchronized void listAdd(DiskList list, Object o) throws SQLException {
    long listId = getHandle(list);
    int idx = list.size();
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

    return getObject(objectId);
  }

  //TODO Remove this?
  protected synchronized int listSize(DiskList list) throws SQLException {
    ResultSet rs = conn.createStatement().executeQuery("SELECT count(*) FROM lists WHERE list_id = " + list.uniqueId);
    rs.first();
    int size = rs.getInt(1);
    rs.close();
    return size;
  }

  protected synchronized void listRemove(DiskList list, int index) throws SQLException {
    //TODO Be careful; you shouldn't necessarily delete the handle, yet; gotta reference count or something
    PreparedStatement pstmt = conn.prepareStatement("SELECT object_id FROM lists WHERE list_id = ? AND idx = ?");
    pstmt.setLong(1, list.uniqueId);
    pstmt.setInt(2, index);
    ResultSet rs = pstmt.executeQuery();
    if (!rs.first()) {
      throw new IndexOutOfBoundsException();
    }
    long objectId = rs.getLong(1);
    rs.close();

    pstmt = conn.prepareStatement("DELETE FROM lists WHERE list_id = ? AND idx = ?");
    pstmt.setLong(1, list.uniqueId);
    pstmt.setInt(2, index);
    pstmt.executeUpdate();

    if (getDiskReferenceCount(objectId) == 0) {
      deleteObjectFromDisk(objectId);
    }
  }
  //</editor-fold>

  private long getDiskReferenceCount(long handle) throws SQLException {
    PreparedStatement pstmt = conn.prepareStatement("SELECT count(*) FROM lists WHERE object_id = ?");
    pstmt.setLong(1, handle);
    ResultSet rs = pstmt.executeQuery();
    rs.first();
    long result = rs.getLong(1);
    rs.close();
    return result;
  }

  private void deleteObjectFromDisk(long handle) throws SQLException {
    {
      // Deserializing for finalization
      //TODO Could mark things for not needing it
      Object o = getObject(handle);
      o.uniqueId = 0;
    }

    PreparedStatement pstmt = conn.prepareStatement("DELETE FROM objects WHERE id = ?");
    pstmt.setLong(1, handle);
    pstmt.executeUpdate();
  }

  @Override
  protected void finalize() throws Throwable {
    //TODO Deserialize and allow-to-finalize all objects?  Kinda heavy
  }

//  private static Object asdf() {
//    return null;
//  }
}
