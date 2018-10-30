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
import java.lang.instrument.UnmodifiableClassException;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.bytecode.AccessFlag;
import javax.lang.model.element.Modifier;
import sun.misc.Unsafe;

/**
 *
 * @author erhannis
 */
public class Test {

  public static class Blah {

    static {
      System.out.println("Loading Blah");
    }

    public volatile boolean inFinalize = false;
    public int test;
    public final WeakReference<Blah> self;

    public Blah() {
      self = new WeakReference<>(this);
    }

    @Override
    protected void finalize() throws Throwable {
      inFinalize = true;
      Thread.sleep(1000);
      System.out.println("in finalize; self = " + self.get());
      hijack();
      super.finalize();
    }

    private static void hijack() {
      System.out.println("hijack Blah");
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
        b.test = (int) System.currentTimeMillis();
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

  public static class ClassBlah {

    static {
      System.out.println("Loading ClassBlah");
    }

    private int value;

    public int setValue(int i) {
      System.out.println("changing value from " + value + " to " + i);
      int i0 = value;
      value = i;
      return i0;
    }

    @Override
    protected void finalize() throws Throwable {
      System.out.println("thisId " + System.identityHashCode(this));
      super.finalize();
    }
  }

  public static class A {
    static {
      System.out.println("loading A");
    }
  }

  public static class B extends A {
    static {
      System.out.println("loading B");
    }

    @Override
    protected void finalize() throws Throwable {
      System.out.println("(B).finalize()");
      super.finalize(); //To change body of generated methods, choose Tools | Templates.
    }
  }

  public static class C extends B {
    static {
      System.out.println("loading C");
    }
  }

  public static class HelloAgent {
    private HashSet<Class<?>> handled = new HashSet<>();

    private static ClassFileTransformer transformer = new ClassFileTransformer() {
      public byte[] transform(ClassLoader loader, String className, Class redefiningClass, ProtectionDomain domain, byte[] bytes) throws IllegalClassFormatException {
        return transformClass(redefiningClass, bytes);
      }

      private byte[] transformClass(Class classToTransform, byte[] b) {
        ClassPool pool = ClassPool.getDefault();
        CtClass cl = null;
        try {
          cl = pool.makeClass(new java.io.ByteArrayInputStream(b));

          System.out.println("Transforming class: " + cl.getName());

//            try {
//              cl.getDeclaredField("diskCacheHandle");
//              throw new RuntimeException("Class already has field diskCacheHandle");
//            } catch (NotFoundException e) {
//            }
//            cl.addField(new CtField(CtClass.longType, "diskCacheHandle", cl));
//            
//            CtMethod newMethod = new CtMethod(CtClass.voidType, "diskCacheMethod", new CtClass[0], cl);
//            newMethod.setModifiers(AccessFlag.PRIVATE);
//            newMethod.setBody("System.out.println(\"in diskCacheMethod\");");
//            cl.addMethod(newMethod);
          CtBehavior[] methods = cl.getDeclaredBehaviors();
          for (int i = 0; i < methods.length; i++) {
            changeMethod(methods[i]);
          }
          b = cl.toBytecode();
        } catch (Exception e) {
          e.printStackTrace();
        } finally {
          if (cl != null) {
            cl.detach();
          }
        }
        return b;
      }

      private void changeMethod(CtBehavior method) throws NotFoundException, CannotCompileException {
        //System.out.println("check changing method " + method.getDeclaringClass().getName() + " . " + method.getLongName());
        if (method.getName().equals("finalize")) {
          method.insertBefore("System.out.println(\"started method at \" + new java.util.Date());");
          method.insertAfter("System.out.println(\"ended method at \" + new java.util.Date());");
        }
      }
    };

    public static void premain(String args, Instrumentation instrumentation) {
      instrumentation.addTransformer(transformer);
      System.out.println("premain");
    }

    public static void agentmain(String agentArgs, Instrumentation inst) {
      System.out.println(agentArgs);
      System.out.println("Hi from the agent!");
      System.out.println("I've got instrumentation!: " + inst);

      //Unsafe unsafe = Unsafe.getUnsafe();
//      inst.addTransformer(, true);
//
//      try {
//        System.out.println("redefine: " + inst.isRedefineClassesSupported());
//        System.out.println("retransform: " + inst.isRetransformClassesSupported());
//        System.out.println("native: " + inst.isNativeMethodPrefixSupported());
//        inst.retransformClasses(ClassBlah.class);
//        inst.retransformClasses(ArrayList.class);
//        inst.retransformClasses(Object.class);
//        inst.retransformClasses(List.class);
//      } catch (UnmodifiableClassException ex) {
//        ex.printStackTrace();
//      }
    }
  }

  public static class Zombie {

    private int value;

    public int setValue(int i) {
      System.out.println("changing value from " + value + " to " + i);
      int i0 = value;
      value = i;
      return i0;
    }

    @Override
    protected void finalize() throws Throwable {
      Test.finalized.add(this);
    }
  }

  public static ArrayList<Object> finalized = new ArrayList<>();

  public static void main(String[] args) throws InterruptedException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException, NoSuchMethodException {
    { // I think maybe the extra field is working.
      HashMap<Object, Long> addresses = new HashMap<>();
      Object o0 = new Object();
      System.out.println("address: " + o0.uniqueId);
      long id = 1;
      while (true) {
        Object o = new Object();
        o.uniqueId = id;
        id++;
        long address = o.uniqueId;
        addresses.put(o, address);

        for (Entry<Object, Long> entry : addresses.entrySet()) {
          long curAddress = entry.getKey().uniqueId;
          if (entry.getValue() != curAddress) {
            throw new RuntimeException("Address changed after " + addresses.size() + ", from " + entry.getValue() + " to " + curAddress);
          }
        }
        if (addresses.size() % 100 == 0) {
          System.out.println("count " + addresses.size());
        }

        if (1 == 0) {
          break;
        }
      }

      if (1 == 1) {
        return;
      }
    }
    { // I think maybe the extra field is working.  Reflection is SLOW, though....I think?
      Field field = Object.class.getDeclaredField("uniqueId");

      HashMap<Object, Long> addresses = new HashMap<>();
      Object o0 = new Object();
      System.out.println("address: " + field.getLong(o0));
      long id = 1;
      while (true) {
        Object o = new Object();
        field.setLong(o, id);
        id++;
        long address = field.getLong(o);
        addresses.put(o, address);

        for (Entry<Object, Long> entry : addresses.entrySet()) {
          long curAddress = field.getLong(entry.getKey());
          if (entry.getValue() != curAddress) {
            throw new RuntimeException("Address changed after " + addresses.size() + ", from " + entry.getValue() + " to " + curAddress);
          }
        }
        if (addresses.size() % 100 == 0) {
          System.out.println("count " + addresses.size());
        }

        if (1 == 0) {
          break;
        }
      }

      if (1 == 1) {
        return;
      }
    }
    {
      C c = new C();
      Object o = new Object();
      Field field = Object.class.getDeclaredField("uniqueId");
      field.setLong(o, 10);
      System.out.println("id: " + field.getLong(o));

      if (1 == 1) {
        return;
      }
    }
    {
      AgentLoader.loadAgentClass(HelloAgent.class.getName(), "Hello!");
      C c = new C();

      if (1 == 1) {
        return;
      }
    }
    { // Good, Object has a defined `finalize` method
      Method m = Object.class.getDeclaredMethod("finalize");
      System.out.println(m);
      if (1 == 1) {
        return;
      }
    }
    { // No dice; weak references aren't returned if the finalizer restores a reference.
      WeakReference<Zombie> wr = new WeakReference<>(new Zombie());
      while (finalized.isEmpty()) {
        System.gc();
      }
      System.out.println("equal: " + (finalized.get(0) == wr.get()));

      if (1 == 1) {
        return;
      }
    }
    { // Address changed after 2260, from 3985369739 to 3992993792
      Field f = Unsafe.class.getDeclaredField("theUnsafe");
      f.setAccessible(true);
      Unsafe unsafe = (Unsafe) f.get(null);

      HashMap<Object, Long> addresses = new HashMap<>();
      Object o0 = new Object();
      System.out.println("address: " + addressOf(unsafe, o0));
      while (true) {
        Object o = new Object();
        long address = addressOf(unsafe, o);
        addresses.put(o, address);

        for (Entry<Object, Long> entry : addresses.entrySet()) {
          long curAddress = addressOf(unsafe, entry.getKey());
          if (entry.getValue() != curAddress) {
            throw new RuntimeException("Address changed after " + addresses.size() + ", from " + entry.getValue() + " to " + curAddress);
          }
        }

        if (1 == 0) {
          break;
        }
      }

      if (1 == 1) {
        return;
      }
    }
    if (1 == 0) {
      // Non-GCd objects collided after 105451
      HashMap<Integer, Object> codes = new HashMap<>();
      while (true) {
        Object o = new Object();
        int code = System.identityHashCode(o);
        if (codes.containsKey(code)) {
          throw new RuntimeException("Found collision after " + codes.size() + " objects");
        }
        codes.put(code, o);
        if (1 == 0) {
          break;
        }
      }
      if (1 == 1) {
        return;
      }
    }
    if (1 == 0) {
      // GCd objects collided after 105451 
      HashSet<Integer> codes = new HashSet<>();
      while (true) {
        Object o = new Object();
        int code = System.identityHashCode(o);
        if (codes.contains(code)) {
          throw new RuntimeException("Found collision after " + codes.size() + " objects");
        }
        codes.add(code);
        if (1 == 0) {
          break;
        }
      }
      if (1 == 1) {
        return;
      }
    }
    if (1 == 0) {
      /* 
            Results:
            If the dying class has a WR to itself, its referent is kept through finalization, UNLESS it's (been?) shared with the outside world.
            Any WRs elsewhere have their referent set to null before the referent's finalizer is called.
       */
      Blah blah = new Blah();
      WeakReference<Blah> wr = new WeakReference<>(blah);
      while (true) {
        blah = wr.get();
        System.out.println(System.currentTimeMillis() + " wr: " + blah);
        if (blah != null) {
          if (blah.inFinalize) {
            System.out.println(System.currentTimeMillis() + " Caught in finalize!");
            return;
          }
        }
        blah = null;
        System.gc();
        Thread.sleep(10);
      }
    }
    {
      AgentLoader.loadAgentClass(HelloAgent.class.getName(), "Hello!");
      ClassBlah cb = new ClassBlah();
      int cbId = System.identityHashCode(cb);
      cb.setValue(0);
      ClassBlah cb2 = new ClassBlah();
      cb2.setValue(0);
      cb = null;
      System.gc();
      Thread.sleep(1000);
      System.gc();
      System.out.println("cbId " + cbId);
      if (1 == 1) {
        return;
      }
    }
  }

  public static long addressOf(Unsafe unsafe, Object o) {
    Object[] array = new Object[]{o};

    long baseOffset = unsafe.arrayBaseOffset(Object[].class);
    int addressSize = unsafe.addressSize();
    long objectAddress;
    switch (addressSize) {
      case 4:
        objectAddress = unsafe.getInt(array, baseOffset);
        break;
      case 8:
        objectAddress = unsafe.getLong(array, baseOffset);
        break;
      default:
        throw new Error("unsupported address size: " + addressSize);
    }

    return (objectAddress);
  }
}
