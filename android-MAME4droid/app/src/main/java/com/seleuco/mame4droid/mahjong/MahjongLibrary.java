package com.seleuco.mame4droid.mahjong;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.graphics.Color;
import android.view.View;
import android.widget.*;
import com.seleuco.mame4droid.MAME4droid;
import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipFile;

/** Dedicated launcher. ROM archives are never extracted or distributed by the app. */
public class MahjongLibrary extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private LinearLayout list;
    private TextView status;
    private Button importer;
    public static File roms(Activity a) { return new File(a.getFilesDir(), "MahjongTouch/roms"); }
    // Conservative catalogue: dependency archives and unrelated arcade games cannot launch.
    public static final Set<String> GAMES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "mjelctrn", "mjelct3", "mjelct3a", "mjdialq2", "mjcamera", "mjcamerb",
        "mjfocus", "mjfocusm", "mjgottub", "mjsikaku", "mjsikakb", "mjsikakc",
        "mjvanilla", "mjgarden", "mjstory", "mjyougo", "telmahjn", "tokimbsj",
        "togenkyo", "otonano", "housemnq", "housemn2", "seiha", "seiham",
        "bijokkoy", "bijokkog", "orangec", "vipclub", "kaguya", "kaguya2", "kanatuen"
    )));
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this); root.setOrientation(1);
        root.setPadding(24, 32, 24, 24); root.setBackgroundColor(Color.rgb(18,43,43));
        ScrollView scroll = new ScrollView(this); list = new LinearLayout(this); list.setOrientation(1);
        scroll.addView(list); root.addView(scroll); setContentView(root);
        roms(this).mkdirs(); refresh();
    }
    private void refresh() {
        list.removeAllViews();
        TextView title = new TextView(this); title.setText("Mahjong Touch · 0.7 test");
        title.setTextColor(Color.WHITE); title.setTextSize(28); list.addView(title);
        TextView help = new TextView(this);
        help.setText("Import your Mahjong ROM ZIP and all required parent / BIOS / sound ZIPs together. Keep their original filenames.\n\nOnly recognised Mahjong games appear below. Other ZIPs are retained as dependencies. ROM compatibility is checked by MAME when you start a game.\n\nFor mjelctrn, import mjelctrn.zip and the dependencies required by your ROM set (including ym2413.zip if requested). This build uses MAME 0.289.\n");
        help.setTextColor(Color.LTGRAY); help.setTextSize(16); list.addView(help);
        importer = new Button(this); importer.setText("Import ROM / dependency ZIPs"); list.addView(importer);
        importer.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*");
            i.addCategory(Intent.CATEGORY_OPENABLE); i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForResult(i, 7);
        });
        status = new TextView(this); status.setTextColor(Color.WHITE); list.addView(status);
        File[] files = roms(this).listFiles((d,n) -> n.endsWith(".zip"));
        if (files == null) files = new File[0]; Arrays.sort(files);
        int count = 0;
        for (File f : files) {
            String id = f.getName().substring(0, f.getName().length()-4);
            if (!GAMES.contains(id)) continue;
            count++;
            Button game = new Button(this); game.setText("Play  ·  " + id); list.addView(game);
            game.setOnClickListener(v -> startActivity(new Intent(this, MAME4droid.class)
                .setAction(Intent.ACTION_VIEW).setData(Uri.fromFile(f))));
        }
        status.setText(count + " Mahjong game(s) · " + files.length + " ZIP(s) stored\n");
        Button catalogue = new Button(this); catalogue.setText("Supported ROM names / help"); list.addView(catalogue);
        catalogue.setOnClickListener(v -> {
            ArrayList<String> names = new ArrayList<>(GAMES); Collections.sort(names);
            new AlertDialog.Builder(this).setTitle("Initial Mahjong catalogue")
                .setMessage(String.join(", ", names) + "\n\nThis is a restricted initial catalogue, not every MAME Mahjong game. Imported files with other names are kept as dependencies. Use matching MAME 0.289 ROM sets.\n\nThe app is based on the GPL MAME4droid frontend and MAME core. Source: github.com/ruiyanxia81-spec/MahjongTouch-MAME")
                .setPositiveButton("OK", null).show();
        });
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != 7 || result != RESULT_OK || data == null) return;
        ArrayList<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) for(int n=0;n<data.getClipData().getItemCount();n++) uris.add(data.getClipData().getItemAt(n).getUri());
        else if (data.getData() != null) uris.add(data.getData());
        importer.setEnabled(false); status.setText("Importing…");
        io.execute(() -> {
            int count = 0; StringBuilder errors = new StringBuilder();
            for (Uri uri : uris) {
                File temp = null;
                try {
                    String name = null;
                    try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                        if(c != null && c.moveToFirst()) name = c.getString(0);
                    }
                    if(name == null || !name.matches("(?i)[a-z0-9_]+\\.zip")) throw new IOException("Use original ROM .zip filenames");
                    name = name.toLowerCase(Locale.ROOT);
                    temp = File.createTempFile("import-", ".tmp", roms(this));
                    try(InputStream in = getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(temp)) {
                        if(in == null) throw new IOException("Cannot read " + name);
                        byte[] buffer = new byte[65536]; int n; long size = 0;
                        while((n=in.read(buffer)) != -1) { size += n; if(size > 512L*1024*1024) throw new IOException("ZIP too large"); out.write(buffer,0,n); }
                    }
                    try(ZipFile z = new ZipFile(temp)) { if(z.size()==0) throw new IOException("Empty ZIP: " + name); }
                    java.nio.file.Files.move(temp.toPath(), new File(roms(this),name).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    count++;
                } catch(Exception e) { errors.append(e.getMessage()).append('\n'); }
                finally { if(temp != null) temp.delete(); }
            }
            final String message = "Imported " + count + " ZIP(s).\n" + errors;
            runOnUiThread(() -> { if(!isFinishing() && !isDestroyed()) { refresh(); new AlertDialog.Builder(this).setMessage(message).setPositiveButton("OK",null).show(); } });
        });
    }
    @Override protected void onDestroy() { io.shutdown(); super.onDestroy(); }
}
