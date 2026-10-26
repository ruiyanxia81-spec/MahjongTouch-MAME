package nz.kutartouch;

import android.app.*;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.graphics.Color;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;
import java.security.MessageDigest;

public class LibraryActivity extends Activity {
    static final String[] IDS = {"Chan", "HipDance", "Endroll"};
    static final String[] TITLES = {"剑术大师 · Chan", "摇摆舞 · HipDance", "片尾曲 · Endroll"};
    // Restrict this private runtime to the three exact user-provided games tested in this project.
    static final String[] HASHES = {"a058eb98455a95dbfdf82ba1a70c4fb46e515305f3ba7426d355599679b4deaa", "c39889f9e875613eeff751528ccf188acd83e0e8c45b5150bc4561edfdf99aba", "4e1671d83ef20feac0fd166b0ead52305dfc352d129e7060bff7d3f455559ff5"};
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private LinearLayout list;
    private boolean importing;
    static boolean valid(String id) { return Arrays.asList(IDS).contains(id); }
    static File gameFile(Activity a,String id) { return new File(a.getFilesDir(), "games/" + id + ".zip"); }
    @Override public void onCreate(Bundle state) { super.onCreate(state); list=new LinearLayout(this); list.setOrientation(1); list.setPadding(24,32,24,24); list.setBackgroundColor(Color.rgb(29,27,48)); ScrollView scroll=new ScrollView(this); scroll.addView(list); setContentView(scroll); refresh(); }
    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); setIntent(i); refresh(); }
    private void refresh() {
        list.removeAllViews();
        TextView title=new TextView(this); title.setText("Kutar Touch"); title.setTextSize(30); title.setTextColor(Color.WHITE); list.addView(title);
        TextView help=new TextView(this); help.setText("经典小游戏 · 原版 EXE 兼容运行测试版 0.1\n\n首次启动需要准备运行环境，请耐心等待。\n导入 Chan.exe、HipDance.exe、Endroll.exe，保持原文件内容。\n\n触屏左键 / 右键对应原版鼠标操作。开始 = F5，重置 = F7。此版本仍需手机验证速度、声音及折叠切换。\n"); help.setTextSize(16); help.setTextColor(Color.LTGRAY); list.addView(help);
        Button imp=new Button(this); imp.setText(importing?"正在导入……":"导入三个 EXE 游戏"); imp.setEnabled(!importing); list.addView(imp);
        imp.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE); i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true); startActivityForResult(i,7);});
        for(int n=0;n<IDS.length;n++) { final String id=IDS[n]; Button play=new Button(this); boolean installed=gameFile(this,id).isFile(); play.setText(TITLES[n]+(installed?"  ·  启动":"  ·  未导入")); play.setEnabled(installed&&!importing); list.addView(play); play.setOnClickListener(v->startActivity(new Intent(this,GameActivity.class).putExtra("game",id))); }
        Button about=new Button(this); about.setText("关于 / 开源信息"); list.addView(about); about.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("运行方式")
            .setMessage("通过 Boxedwine 26R1 与 Wine 运行原版 Windows 程序；并非原生重写版。运行环境随安装包提供，离线运行。游戏需要用户导入，不随 APK 提供。\n\nBoxedwine: GPL-2.0\nhttps://github.com/danoon2/Boxedwine/tree/26R1.0\nWine: LGPL-2.1\nhttps://gitlab.winehq.org/wine/wine\n\nKutar 原作：GIGA-RENSYA。此为非官方个人兼容测试项目。源代码位于 MahjongTouch-MAME 仓库的 kutar/android-runtime 分支 / kutar-android 目录。")
            .setPositiveButton("确定",null).show());
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data); if(request!=7||result!=RESULT_OK||data==null)return;
        ArrayList<Uri> uris=new ArrayList<>(); if(data.getClipData()!=null)for(int n=0;n<data.getClipData().getItemCount();n++)uris.add(data.getClipData().getItemAt(n).getUri()); else if(data.getData()!=null)uris.add(data.getData());
        importing=true;refresh();
        io.execute(()->{StringBuilder message=new StringBuilder();
            for(Uri uri:uris) { File temp=null;
                try {String name=null;try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())name=c.getString(0);}
                    int index=-1;for(int n=0;n<IDS.length;n++)if((IDS[n]+".exe").equalsIgnoreCase(name))index=n;
                    if(index<0)throw new IOException("请选择 Chan.exe、HipDance.exe 或 Endroll.exe");
                    ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("无法读取文件");byte[] buf=new byte[65536];int n;while((n=in.read(buf))!=-1){if(bytes.size()+n>16*1024*1024)throw new IOException("文件过大");bytes.write(buf,0,n);}}
                    byte[] exe=bytes.toByteArray();StringBuilder hex=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(exe))hex.append(String.format("%02x",b&255));
                    if(!HASHES[index].equals(hex.toString()))throw new IOException(name+" 版本不符；仅接受此次提供的原版文件");
                    File dest=gameFile(this,IDS[index]);dest.getParentFile().mkdirs();temp=File.createTempFile("import-",".tmp",dest.getParentFile());
                    try(ZipOutputStream z=new ZipOutputStream(new FileOutputStream(temp))){z.putNextEntry(new ZipEntry(IDS[index]+".exe"));z.write(exe);z.closeEntry();}
                    java.nio.file.Files.move(temp.toPath(),dest.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);message.append(name).append(" 已导入\n");
                }catch(Exception e){message.append(e.getMessage()).append('\n');}finally{if(temp!=null)temp.delete();}
            }
            runOnUiThread(()->{importing=false;if(!isFinishing()&&!isDestroyed()){refresh();new AlertDialog.Builder(this).setMessage(message.toString()).setPositiveButton("确定",null).show();}});
        });
    }
    @Override protected void onDestroy(){io.shutdown();super.onDestroy();}
}
