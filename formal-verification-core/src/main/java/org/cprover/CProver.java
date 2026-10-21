package org.cprover;

/** Intrinsics interpreted by JBMC. Never invoke on an ordinary JVM. */
public final class CProver {
    private CProver() { }
    public static native long nondetLong();
    public static native int nondetInt();
    public static native boolean nondetBoolean();
    public static native void assume(boolean condition);
}
