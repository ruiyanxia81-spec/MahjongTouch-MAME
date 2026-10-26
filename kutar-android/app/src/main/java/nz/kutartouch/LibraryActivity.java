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
    static void applyInsets(android.view.View view) {
        final int l=view.getPaddingLeft(),t=view.getPaddingTop(),r=view.getPaddingRight(),b=view.getPaddingBottom();
        view.setOnApplyWindowInsetsListener((v,insets)->{
            if(android.os.Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout());v.setPadding(l+bars.left,t+bars.top,r+bars.right,b+bars.bottom);}
            else v.setPadding(l+insets.getSystemWindowInsetLeft(),t+insets.getSystemWindowInsetTop(),r+insets.getSystemWindowInsetRight(),b+insets.getSystemWindowInsetBottom());
            return insets;
        });
    }
    static boolean valid(String id) { return Arrays.asList(IDS).contains(id); }
    static File gameFile(Activity a,String id) { return new File(a.getFilesDir(), "games/" + id + ".zip"); }
    @Override public void onCreate(Bundle state) { super.onCreate(state); list=new LinearLayout(this); list.setOrientation(1); list.setPadding(24,32,24,24); list.setBackgroundColor(Color.rgb(29,27,48)); ScrollView scroll=new ScrollView(this); scroll.addView(list); applyInsets(scroll); setContentView(scroll); refresh(); }
    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); setIntent(i); refresh(); }
    private void refresh() {
        list.removeAllViews();
        TextView title=new TextView(this); title.setText("Kutar Touch · 原生测试版"); title.setTextSize(30); title.setTextColor(Color.WHITE); list.addView(title);
        TextView help=new TextView(this); help.setText("原生重写测试版 0.2\n\nChan 已改为 Android 原生绘制与输入，不再运行 Windows 或模拟器。复用你导入 EXE 中的原版图片和音效。\n\n这是基础玩法重写：左右出剑、命中、空挥、碰撞和计分可测试。原版精确计时、完整武士对决、特殊事件尚未还原。\n\nHipDance 和 Endroll 尚未原生重写，本版不会启动它们。\n"); help.setTextSize(16); help.setTextColor(Color.LTGRAY); list.addView(help);
        Button imp=new Button(this); imp.setText(importing?"正在导入……":"导入三个 EXE 游戏"); imp.setEnabled(!importing); list.addView(imp);
        imp.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE); i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true); startActivityForResult(i,7);});
        for(int n=0;n<IDS.length;n++) { final String id=IDS[n]; Button play=new Button(this); boolean installed=gameFile(this,id).isFile(); play.setText(TITLES[n]+(id.equals("Chan")?(installed?"  ·  原生启动":"  ·  未导入"):"  ·  原生重写待完成")); play.setEnabled(id.equals("Chan")&&installed&&!importing); list.addView(play); play.setOnClickListener(v->startActivity(new Intent(this,GameActivity.class).putExtra("game",id))); }
        Button about=new Button(this); about.setText("关于 / 开源信息"); list.addView(about); about.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("运行方式")
            .setMessage("本版使用 Android Canvas、MediaPlayer 和 SoundPool，未包含或运行 Boxedwine、Wine、WebView、WebAssembly 或 x86 模拟器。\n\nChan 游戏逻辑为基础重写，不是原版机器码的完整还原。图片和声音从用户导入的原 EXE 中读取，APK 不附带原作素材。\n\nKutar 原作：GIGA-RENSYA。非官方个人原生重写测试版。源代码：github.com/ruiyanxia81-spec/MahjongTouch-MAME 的 kutar/android-runtime 分支，kutar-android 目录。Android 前端源码为 GPL-2.0-or-later。")
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
