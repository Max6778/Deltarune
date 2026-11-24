package com.baldimods.launcher;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import android.app.AlertDialog;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Launcher screen: import mods into the app's own storage, then start the game. */
public class LauncherActivity extends Activity {
    private static final int REQ_IMPORT = 1;
    private static final String GAME = "com.unity3d.player.UnityPlayerActivity";
    private File modsDir;
    private ListView list;
    private TextView status;
    private CheckBox captureLogcat;
    private File logcatFile;
    private File bepInExDir;
    private File pluginDir;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        File ext = getExternalFilesDir(null);
        modsDir = new File(ext != null ? ext : getFilesDir(), "mods");
        modsDir.mkdirs();
        logcatFile = new File(getFilesDir(), "game-logcat.txt");
        bepInExDir = new File(getFilesDir(), "BepInEx");
        pluginDir = new File(bepInExDir, "plugins");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 48, 32, 32);

        TextView title = new TextView(this);
        title.setText("Baldi's Basics Plus - Mod Launcher");
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        Button play = new Button(this);
        play.setText("PLAY");
        play.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { play(); } });
        root.addView(play);

        captureLogcat = new CheckBox(this);
        captureLogcat.setText("Save game logcat for this run");
        captureLogcat.setChecked(getPreferences(MODE_PRIVATE).getBoolean("capture_game_logcat", false));
        captureLogcat.setOnCheckedChangeListener((button, checked) ->
                getPreferences(MODE_PRIVATE).edit().putBoolean("capture_game_logcat", checked).apply());
        root.addView(captureLogcat);

        Button viewLog = new Button(this);
        viewLog.setText("View saved game logcat");
        viewLog.setOnClickListener(v -> showLogcat());
        root.addView(viewLog);

        Button imp = new Button(this);
        imp.setText("Import mods (.so, .dll or .zip)");
        imp.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { pick(); } });
        root.addView(imp);

        status = new TextView(this);
        status.setText("Mods folder: " + modsDir.getAbsolutePath() + "\nLong-press a mod to delete it.");
        root.addView(status);

        list = new ListView(this);
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
                String name = (String) p.getItemAtPosition(pos);
                deleteRec(new File(modsDir, name));
                refresh();
                return true;
            }
        });
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        refresh();
    }

    private void refresh() {
        String[] names = modsDir.list();
        if (names == null) names = new String[0];
        Arrays.sort(names);
        list.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, names));
    }

    private void pick() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(i, REQ_IMPORT);
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        if (req != REQ_IMPORT || res != RESULT_OK || data == null) return;
        List<Uri> uris = new ArrayList<Uri>();
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++) uris.add(data.getClipData().getItemAt(i).getUri());
        } else if (data.getData() != null) uris.add(data.getData());
        int ok = 0;
        for (Uri u : uris) {
            try { importOne(u); ok++; } catch (Exception e) { status.setText("Import failed: " + e); }
        }
        if (ok > 0) status.setText("Imported " + ok + " item(s).");
        refresh();
    }

    private void importOne(Uri u) throws IOException {
        String name = displayName(u);
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".zip")) {
            InputStream probe = getContentResolver().openInputStream(u);
            boolean bepinexLayout;
            try { bepinexLayout = hasBepInExRoot(probe); }
            finally { probe.close(); }
            boolean dllArchive = false;
            if (!bepinexLayout) {
                probe = getContentResolver().openInputStream(u);
                try { dllArchive = hasDll(probe); }
                finally { probe.close(); }
            }
            InputStream in = getContentResolver().openInputStream(u);
            try {
                if (bepinexLayout) {
                    unzip(in, getFilesDir());
                    status.setText("Imported BepInEx files from " + name);
                } else if (dllArchive) {
                    pluginDir.mkdirs();
                    unzip(in, pluginDir);
                    status.setText("Imported plugin files from " + name);
                } else {
                    File dest = new File(modsDir, name.substring(0, name.length() - 4));
                    dest.mkdirs();
                    unzip(in, dest);
                }
            } finally { in.close(); }
        } else if (lower.endsWith(".dll")) {
            pluginDir.mkdirs();
            InputStream in = getContentResolver().openInputStream(u);
            try { copy(in, new FileOutputStream(new File(pluginDir, name))); }
            finally { in.close(); }
        } else {
            InputStream in = getContentResolver().openInputStream(u);
            try { copy(in, new FileOutputStream(new File(modsDir, name))); }
            finally { in.close(); }
        }
    }

    private static boolean hasBepInExRoot(InputStream in) throws IOException {
        ZipInputStream zip = new ZipInputStream(in);
        ZipEntry entry;
        int scanned = 0;
        while ((entry = zip.getNextEntry()) != null && scanned++ < 256) {
            String path = entry.getName().replace('\\', '/');
            if (path.equals("BepInEx/") || path.startsWith("BepInEx/")) return true;
        }
        return false;
    }

    private static boolean hasDll(InputStream in) throws IOException {
        ZipInputStream zip = new ZipInputStream(in);
        ZipEntry entry;
        int scanned = 0;
        while ((entry = zip.getNextEntry()) != null && scanned++ < 2048) {
            if (!entry.isDirectory() && entry.getName().toLowerCase(Locale.ROOT).endsWith(".dll")) return true;
        }
        return false;
    }

    private String displayName(Uri u) {
        Cursor c = getContentResolver().query(u, null, null, null, null);
        try {
            if (c != null && c.moveToFirst()) {
                int ix = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (ix >= 0) return new File(c.getString(ix)).getName();
            }
        } finally { if (c != null) c.close(); }
        return "mod_" + System.currentTimeMillis();
    }

    private static void unzip(InputStream in, File dest) throws IOException {
        ZipInputStream z = new ZipInputStream(in);
        dest.mkdirs();
        ZipEntry e;
        String base = dest.getCanonicalPath() + File.separator;
        while ((e = z.getNextEntry()) != null) {
            File f = new File(dest, e.getName());
            if (!f.getCanonicalPath().startsWith(base)) continue; // zip-slip guard
            if (e.isDirectory()) { f.mkdirs(); continue; }
            f.getParentFile().mkdirs();
            copy(z, new FileOutputStream(f), false);
        }
    }

    private static void copy(InputStream in, OutputStream out) throws IOException { copy(in, out, true); }
    private static void copy(InputStream in, OutputStream out, boolean closeIn) throws IOException {
        byte[] buf = new byte[65536];
        int n;
        try { while ((n = in.read(buf)) > 0) out.write(buf, 0, n); }
        finally { out.close(); }
    }

    private static void deleteRec(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteRec(k);
        f.delete();
    }

    private void play() {
        try {
            prepareBepInEx();
        } catch (Throwable t) {
            status.setText("BepInEx setup failed: " + t + "\nStarting with native mods only.");
        }
        try {
            if (captureLogcat.isChecked()) GameLogcatCapture.start(logcatFile);
            System.loadLibrary("modloader");
            NativeLoader.start(modsDir.getAbsolutePath(), getFilesDir().getAbsolutePath());
        } catch (Throwable t) {
            status.setText("Mod loader failed to start: " + t + "\nStarting the game without mods.");
        }
        Intent i = new Intent();
        i.setClassName(getPackageName(), GAME);
        startActivity(i);
        finish();
    }

    private void prepareBepInEx() throws IOException {
        if (!android.os.Process.is64Bit()) {
            status.setText("BepInEx Android IL2CPP currently requires a 64-bit device.");
            return;
        }
        File libraryDir = new File(getApplicationInfo().nativeLibraryDir);
        if (!new File(libraryDir, "libil2cpp.so").isFile())
            throw new IOException("libil2cpp.so was not extracted from the game APK");

        File core = new File(bepInExDir, "core/BepInEx.Core.dll");
        if (!core.isFile()) extractAssetZip("BepInEx.Android.zip", bepInExDir, true);
        File corelib = new File(getFilesDir(), "dotnet/System.Private.CoreLib.dll");
        if (!corelib.isFile()) extractAssetZip("dotnet.zip", new File(getFilesDir(), "dotnet"), false);

        File unityData = new File(getFilesDir(), "unity-data/bin/Data");
        File metadata = new File(unityData, "Managed/Metadata/global-metadata.dat");
        if (!metadata.isFile()) {
            copyAssetTree("bin/Data", unityData);
            if (!metadata.isFile()) throw new IOException("Unity global-metadata.dat was not found");
        }

        FusionConfig config = new FusionConfig(getPackageName(), libraryDir.getAbsolutePath(),
                getFilesDir().getAbsolutePath(), getCodeCacheDir().getAbsolutePath(),
                bepInExDir.getAbsolutePath(), new File(getFilesDir(), "dotnet").getAbsolutePath(),
                unityData.getAbsolutePath(), "2021.3.58f1");
        NativeLibraryManager.setRuntimeConfig(config);
    }

    private void extractAssetZip(String assetName, File destination, boolean preserveConfig)
            throws IOException {
        destination.mkdirs();
        ZipInputStream zip = new ZipInputStream(getAssets().open(assetName));
        ZipEntry entry;
        String base = destination.getCanonicalPath() + File.separator;
        while ((entry = zip.getNextEntry()) != null) {
            File target = new File(destination, entry.getName());
            String canonical = target.getCanonicalPath();
            if (!canonical.equals(destination.getCanonicalPath()) && !canonical.startsWith(base))
                throw new IOException("Unsafe runtime archive path: " + entry.getName());
            if (preserveConfig && entry.getName().startsWith("config/") && target.exists()) continue;
            if (entry.isDirectory()) target.mkdirs();
            else {
                File parent = target.getParentFile();
                if (parent != null) parent.mkdirs();
                copy(zip, new FileOutputStream(target), false);
            }
            zip.closeEntry();
        }
        zip.close();
    }

    private void copyAssetTree(String assetPath, File destination) throws IOException {
        destination.mkdirs();
        String[] children = getAssets().list(assetPath);
        if (children != null && children.length > 0) {
            for (String child : children) copyAssetTree(assetPath + "/" + child, new File(destination, child));
            return;
        }
        try (InputStream in = getAssets().open(assetPath)) {
            copy(in, new FileOutputStream(destination));
        } catch (IOException e) {
            if (children == null || children.length == 0) throw e;
        }
    }

    private void showLogcat() {
        if (!logcatFile.isFile()) {
            new AlertDialog.Builder(this).setMessage("No saved game logcat yet. Turn on the setting and launch the game.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        try {
            RandomAccessFile file = new RandomAccessFile(logcatFile, "r");
            long start = Math.max(0, file.length() - 500_000);
            file.seek(start);
            byte[] bytes = new byte[(int)(file.length() - start)];
            file.readFully(bytes);
            file.close();
            String content = new String(bytes, "UTF-8");
            if (start > 0) content = "[Showing the last 500 KB]\n" + content;
            TextView text = new TextView(this);
            text.setText(content);
            text.setTextIsSelectable(true);
            text.setTextSize(12);
            int pad = (int)(16 * getResources().getDisplayMetrics().density);
            text.setPadding(pad, pad, pad, pad);
            ScrollView scroll = new ScrollView(this);
            scroll.addView(text);
            new AlertDialog.Builder(this).setTitle("Game logcat").setView(scroll)
                    .setPositiveButton("Close", null).show();
        } catch (Exception e) {
            new AlertDialog.Builder(this).setMessage("Could not read game logcat: " + e)
                    .setPositiveButton("OK", null).show();
        }
    }
}
