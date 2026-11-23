package com.baldimods.launcher;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
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

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        File ext = getExternalFilesDir(null);
        modsDir = new File(ext != null ? ext : getFilesDir(), "mods");
        modsDir.mkdirs();

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

        Button imp = new Button(this);
        imp.setText("Import mods (.so or .zip)");
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
        InputStream in = getContentResolver().openInputStream(u);
        try {
            if (name.toLowerCase().endsWith(".zip")) {
                File dest = new File(modsDir, name.substring(0, name.length() - 4));
                dest.mkdirs();
                unzip(in, dest);
            } else {
                copy(in, new FileOutputStream(new File(modsDir, name)));
            }
        } finally { in.close(); }
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
}
