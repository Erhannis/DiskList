/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.diskcache;

import com.ea.agentloader.AgentLoader;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author erhannis
 */
public class Test {
  public static class Blah {
    public int test;

    @Override
    protected void finalize() throws Throwable {
      hijack();
      super.finalize();
    }
    
    private static void hijack() {
      System.out.println("blah");
    }
  }

  /**
   * @param args the command line arguments
   */
  public static void main2(String[] args) throws InterruptedException {
    Thread t = new Thread(() -> {
      ArrayList<Blah> blahs = new ArrayList<>();
      while (true) {
        Blah b = new Blah();
        b.test = (int)System.currentTimeMillis();
        blahs.add(b);
        System.gc();
      }
    });
    t.setDaemon(true);
    t.start();
    ReferenceQueue<Blah> rq = new ReferenceQueue<Blah>();
    WeakReference<Blah> wr = getBlah(rq);
    Reference<? extends Blah> r = rq.remove();
    System.out.println("got r");
    Thread.sleep(1000000);
  }

  private static WeakReference<Blah> getBlah(ReferenceQueue<Blah> rq) {
    Blah blah = new Blah();
    blah.test = 4;
    return new WeakReference<>(blah, rq);
  }
  
  public static class HelloAgent
    {
        public static void agentmain(String agentArgs, Instrumentation inst)
        {
            System.out.println(agentArgs);
            System.out.println("Hi from the agent!");
            System.out.println("I've got instrumentation!: " + inst);
            inst.addTransformer(new ClassFileTransformer() {
              @Override
              public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
                
              }
            });
        }
    }

    public static void main(String[] args)
    {
        AgentLoader.loadAgentClass(HelloAgent.class.getName(), "Hello!");
    }
}
