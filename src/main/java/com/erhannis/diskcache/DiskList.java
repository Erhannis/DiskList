/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.diskcache;

import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @author erhannis
 */
public class DiskList<T> {
  //private AtomicInteger size = new AtomicInteger(0);
  private final Context ctx;

  public DiskList(Context ctx) {
    ctx.manager.listRegister(this);
    this.ctx = ctx;
  }

  //TODO Actually implement List?
  public void add(T t) {
    ctx.manager.listAdd(this, t);
  }

  public T get(int index) {
    return (T) ctx.manager.listGet(this, index);
  }

  public int size() {
    return ctx.manager.listSize(this);
  }

  public void remove(int index) {
    ctx.manager.listRemove(this, index);
  }
}
