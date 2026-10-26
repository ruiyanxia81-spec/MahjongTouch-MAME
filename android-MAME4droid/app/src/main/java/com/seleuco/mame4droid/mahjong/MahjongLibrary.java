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
        "xymg", "mjelctrn", "mjelct3", "mjelct3a", "mjdialq2", "mjcamera", "mjcamerb",
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
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); refresh(); }
    private void refresh() {
        list.removeAllViews();
        TextView title = new TextView(this); title.setText("Mahjong Touch · 0.7.1 测试版");
        title.setTextColor(Color.WHITE); title.setTextSize(28); list.addView(title);
        TextView help = new TextView(this);
        help.setText("导入你自己的麻将 ROM ZIP，以及需要的父 ROM、BIOS 或声音依赖 ZIP。保持原文件名，不要解压。\n\n下方只显示已识别的麻将游戏；其他 ZIP 将保留为依赖文件。运行后点击“换游戏”返回此列表，再选择其他游戏。\n\n幸运满贯：导入 xymg.zip 后，点击“幸运满贯”即可启动。当前使用 MAME 0.289。\n");
        help.setTextColor(Color.LTGRAY); help.setTextSize(16); list.addView(help);
        importer = new Button(this); importer.setText("导入游戏 / 依赖 ZIP"); list.addView(importer);
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
            Button game = new Button(this); game.setText("开始游戏 · " + (id.equals("xymg") ? "幸运满贯 (xymg)" : id)); list.addView(game);
            game.setOnClickListener(v -> startActivity(new Intent(this, MAME4droid.class)
                .setAction(Intent.ACTION_VIEW).setData(Uri.fromFile(f))));
        }
        status.setText(count + " 个麻将游戏 · 已保存 " + files.length + " 个 ZIP\n");
        Button catalogue = new Button(this); catalogue.setText("支持的游戏 / 帮助"); list.addView(catalogue);
        catalogue.setOnClickListener(v -> {
            ArrayList<String> names = new ArrayList<>(GAMES); Collections.sort(names);
            new AlertDialog.Builder(this).setTitle("麻将游戏目录")
                .setMessage(String.join(", ", names) + "\n\n这是受限的麻将游戏目录。其他文件名的 ZIP 会保留为依赖，请使用匹配 MAME 0.289 的 ROM。\n\n本应用基于 GPL MAME4droid 前端及 MAME 核心。源代码： github.com/ruiyanxia81-spec/MahjongTouch-MAME")
                .setPositiveButton("确定", null).show();
        });
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != 7 || result != RESULT_OK || data == null) return;
        ArrayList<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) for(int n=0;n<data.getClipData().getItemCount();n++) uris.add(data.getClipData().getItemAt(n).getUri());
        else if (data.getData() != null) uris.add(data.getData());
        importer.setEnabled(false); status.setText("正在导入……");
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
            final String message = "已导入 " + count + " 个 ZIP。\n" + errors;
            runOnUiThread(() -> { if(!isFinishing() && !isDestroyed()) { refresh(); new AlertDialog.Builder(this).setMessage(message).setPositiveButton("确定",null).show(); } });
        });
    }
    @Override protected void onDestroy() { io.shutdown(); super.onDestroy(); }
}
