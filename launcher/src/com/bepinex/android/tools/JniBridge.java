package com.bepinex.android.tools;

import android.app.Activity;

/** Unity loading overlay is optional in this embedded launcher. */
public final class JniBridge {
    private JniBridge() {}

    public static void SetLoadingState(Activity activity, boolean enabled) {}
    public static void SetLoadingText(Activity activity, String text) {}
}
