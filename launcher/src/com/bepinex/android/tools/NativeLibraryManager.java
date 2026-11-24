package com.bepinex.android.tools;

/** Static config bridge used by libnextbep from Unity's native loader. */
public final class NativeLibraryManager {
    private static volatile FusionConfig runtimeConfig;

    private NativeLibraryManager() {}

    public static void setRuntimeConfig(FusionConfig config) {
        runtimeConfig = config;
    }

    public static FusionConfig getRuntimeConfig() {
        return runtimeConfig;
    }
}
