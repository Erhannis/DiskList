/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.diskcache;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.security.ProtectionDomain;
import java.util.HashSet;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.NotFoundException;

/**
 *
 * Cobbled together from the example at https://github.com/electronicarts/ea-agent-loader
 * and http://appcrawler.com/wordpress/2013/01/02/simple-byte-code-injection-example-with-javassist/
 * with subsequent modifications
 */
public class FinalizationAgent {
  private static Instrumentation mInstrumentation;

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
        method.insertBefore("if (!ObjectManager.singleton.objectIsDying(this)) {return;}");
      }
    }
  };

  public static synchronized void agentmain(String agentArgs, Instrumentation inst) {
    FinalizationAgent.mInstrumentation = inst;
    System.out.println("Hi from the agent!");
    System.out.println("I've got instrumentation!: " + inst);

    inst.addTransformer(transformer, true);
  }

  private static final HashSet<Class<?>> instrumentedClasses = new HashSet<>();

  public static synchronized void instrument(Class clazz) {
    System.out.println("instrument");
    if (instrumentedClasses.contains(clazz)) {
      return;
    }
    try {
      clazz.getDeclaredMethod("finalize");
      try {
        mInstrumentation.retransformClasses(clazz);
      } catch (UnmodifiableClassException ex) {
        ex.printStackTrace();
      }
      instrumentedClasses.add(clazz);
    } catch (NoSuchMethodException e) {
      instrument(clazz.getSuperclass());
      instrumentedClasses.add(clazz);
    }
  }

}
