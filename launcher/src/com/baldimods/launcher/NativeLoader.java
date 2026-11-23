package com.baldimods.launcher;

/** Starts libmodloader.so, which waits for IL2CPP to come up and then loads every mod .so. */
public final class NativeLoader {
    private NativeLoader() {}
    public static native void start(String modsDir, String internalDir);
}
