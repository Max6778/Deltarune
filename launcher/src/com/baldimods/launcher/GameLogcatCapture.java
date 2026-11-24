package com.baldimods.launcher;

import android.os.Process;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/** Captures this game's process logcat while the Unity activity is running. */
final class GameLogcatCapture {
    private static boolean started;

    private GameLogcatCapture() {}

    static synchronized void start(final File output) {
        if (started) return;
        started = true;
        Thread worker = new Thread(() -> capture(output), "BBPlus-GameLogcat");
        worker.start();
    }

    private static void capture(File output) {
        java.lang.Process logcat = null;
        try {
            File parent = output.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs())
                throw new java.io.IOException("could not create log directory");
            logcat = new ProcessBuilder("logcat", "-v", "threadtime", "--pid=" + Process.myPid())
                    .redirectErrorStream(true).start();
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                    new FileOutputStream(output, false), StandardCharsets.UTF_8));
            writer.println("BB+ game logcat started; PID " + Process.myPid());
            writer.flush();
            BufferedReader reader = new BufferedReader(new InputStreamReader(logcat.getInputStream(), StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                writer.println(line);
                writer.flush();
                if (output.length() > 5L * 1024L * 1024L) {
                    writer.println("Log capture stopped at the 5 MB limit.");
                    break;
                }
            }
            reader.close();
            writer.close();
        } catch (Exception e) {
            try {
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                        new FileOutputStream(output, true), StandardCharsets.UTF_8));
                writer.println("Could not capture logcat: " + e);
                writer.close();
            } catch (Exception ignored) {}
        } finally {
            if (logcat != null) logcat.destroy();
        }
    }
}
