package org.cprover;

public final class CProver {

    private CProver() {
    }

    public static native long nondetLong();

    public static native void assume(boolean condition);
}
