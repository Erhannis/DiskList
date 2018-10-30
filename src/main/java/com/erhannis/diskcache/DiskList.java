/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.diskcache;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author erhannis
 */
public class DiskList<T> {
  private AtomicInteger size = new AtomicInteger(0);
  private final Context ctx;

  public DiskList(Context ctx) {
    ctx.manager.listRegister(this);
    this.ctx = ctx;
  }

  //TODO Actually implement List?
  public synchronized void add(T t) {
    try {
      ctx.manager.listAdd(this, t);
      size.incrementAndGet();
    } catch (SQLException ex) {
      Logger.getLogger(DiskList.class.getName()).log(Level.SEVERE, null, ex);
    }
  }

  public synchronized T get(int index) {
    try {
      return (T) ctx.manager.listGet(this, index);
    } catch (SQLException ex) {
      Logger.getLogger(DiskList.class.getName()).log(Level.SEVERE, null, ex);
    }
  }

  public synchronized int size() {
//    try {
//      return ctx.manager.listSize(this);
//    } catch (SQLException ex) {
//      Logger.getLogger(DiskList.class.getName()).log(Level.SEVERE, null, ex);
//    }
    return size.get();
  }

  public synchronized void remove(int index) {
    ctx.manager.listRemove(this, index);
    size.decrementAndGet();
  }
}
