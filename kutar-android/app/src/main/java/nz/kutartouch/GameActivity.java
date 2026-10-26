package nz.kutartouch;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.graphics.Color;
import android.net.Uri;
import android.webkit.*;
import android.widget.*;
import java.io.*;
import java.util.*;

public class GameActivity extends Activity {
    private WebView web;
    private String game;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); game=getIntent().getStringExtra("game");
        if(!LibraryActivity.valid(game)||!LibraryActivity.gameFile(this,game).isFile()){finish();return;}
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(1);layout.setBackgroundColor(Color.rgb(29,27,48));
        LinearLayout header=new LinearLayout(this);Button back=new Button(this);back.setText("换游戏");header.addView(back);back.setOnClickListener(v->askLeave());
        TextView title=new TextView(this);title.setText("Kutar Touch · "+game+"\n首次启动可能需要 20–90 秒");title.setTextColor(Color.WHITE);header.addView(title);layout.addView(header);
        web=new WebView(this);layout.addView(web,new LinearLayout.LayoutParams(-1,0,1));setContentView(layout);
        WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setMediaPlaybackRequiresUserGesture(false);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setSupportZoom(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return true;}
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest request) {
                Uri uri=request.getUrl();String path=uri.getPath();
                try {
                    if(!"https".equals(uri.getScheme())||!"appassets.androidplatform.net".equals(uri.getHost())||path==null)throw new IOException("Blocked origin");
                    InputStream in;String mime;
                    if(path.equals("/runtime/"+game+".zip")){in=new FileInputStream(LibraryActivity.gameFile(GameActivity.this,game));mime="application/zip";}
                    else {
                        Set<String> allowed=new HashSet<>(Arrays.asList("/runtime/index.html","/runtime/boxedwine-shell.js","/runtime/boxedwine.js","/runtime/boxedwine.wasm","/runtime/boxedwine.zip","/runtime/LICENSE.txt","/runtime/NOTICE.txt"));
                        if(!allowed.contains(path))throw new IOException("Unknown asset");
                        in=getAssets().open(path.substring(1));mime=path.endsWith(".html")?"text/html":path.endsWith(".js")?"application/javascript":path.endsWith(".wasm")?"application/wasm":path.endsWith(".zip")?"application/zip":"text/plain";
                    }
                    Map<String,String> headers=new HashMap<>();headers.put("Cache-Control","no-store");headers.put("X-Content-Type-Options","nosniff");
                    return new WebResourceResponse(mime,null,200,"OK",headers,in);
                }catch(Exception e){return new WebResourceResponse("text/plain","utf-8",404,"Not Found",Collections.emptyMap(),new ByteArrayInputStream("Unavailable local resource".getBytes()));}
            }
        });
        web.setWebChromeClient(new WebChromeClient());
        web.loadUrl("https://appassets.androidplatform.net/runtime/index.html?app="+game+"&p="+game+".exe&resolution=640x480&sound=true&disableHideCursor=true");
    }
    private void askLeave(){
        if(web!=null)web.evaluateJavascript("window.releaseControls&&releaseControls()",null);
        new AlertDialog.Builder(this).setTitle("返回游戏选单？").setMessage("这会结束本次游戏。已导入的 EXE 将保留。")
            .setPositiveButton("换游戏",(d,w)->leave()).setNegativeButton("继续游戏",null).show();
    }
    private void leave(){startActivity(new Intent(this,LibraryActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));finish();android.os.Process.killProcess(android.os.Process.myPid());}
    @Override public void onBackPressed(){askLeave();}
    @Override protected void onPause(){if(web!=null){web.evaluateJavascript("window.releaseControls&&releaseControls()",null);web.onPause();web.pauseTimers();}super.onPause();}
    @Override protected void onResume(){super.onResume();if(web!=null){web.resumeTimers();web.onResume();web.evaluateJavascript("window.releaseControls&&releaseControls()",null);}}
    @Override protected void onDestroy(){if(web!=null){web.stopLoading();web.destroy();web=null;}super.onDestroy();}
}
