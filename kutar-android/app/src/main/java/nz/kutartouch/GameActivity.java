package nz.kutartouch;
import android.app.*;import android.os.*;import android.content.Intent;import android.graphics.Color;import android.view.*;import android.widget.*;
public class GameActivity extends Activity {
    private ChanView game;private boolean foreground;private volatile boolean destroyed;
    @Override public void onCreate(Bundle b){
        super.onCreate(b);if(!"Chan".equals(getIntent().getStringExtra("game"))){finish();return;}
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(Color.rgb(29,27,48));LibraryActivity.applyInsets(root);setContentView(root);
        TextView status=new TextView(this);status.setText("正在读取原版素材……");status.setTextColor(Color.WHITE);status.setPadding(20,20,20,20);root.addView(status);
        new Thread(()->{try{GameAssets assets=GameAssets.load(this,"Chan");runOnUiThread(()->{
            if(destroyed||isFinishing()){assets.close();return;}root.removeAllViews();
            LinearLayout toolbar=new LinearLayout(this);root.addView(toolbar);Button back=button(toolbar,"选单");Button start=button(toolbar,"开始");Button pause=button(toolbar,"暂停");Button sound=button(toolbar,"声音开");
            TextView label=new TextView(this);label.setText("Chan 原生重写 · 基础玩法测试（原版节奏与武士对决待还原）");label.setTextSize(11);label.setTextColor(Color.LTGRAY);label.setPadding(8,3,8,3);root.addView(label);
            game=new ChanView(this,assets);root.addView(game,new LinearLayout.LayoutParams(-1,0,1));game.suspend(!foreground);
            LinearLayout controls=new LinearLayout(this);root.addView(controls);Button left=button(controls,"◀ 左出剑"),right=button(controls,"右出剑 ▶");int height=(int)(72*getResources().getDisplayMetrics().density);left.getLayoutParams().height=height;right.getLayoutParams().height=height;left.setTextSize(21);right.setTextSize(21);action(left,-1);action(right,1);
            back.setOnClickListener(v->leave());start.setOnClickListener(v->{game.start();pause.setText("暂停");});pause.setOnClickListener(v->pause.setText(game.togglePause()?"继续":"暂停"));sound.setOnClickListener(v->sound.setText(game.toggleSound()?"声音开":"声音关"));
        });}catch(Exception e){runOnUiThread(()->{if(destroyed)return;status.setText("素材读取失败："+e.getMessage()+"\n请返回选单重新导入原始 Chan.exe。");});}},"native-assets").start();
    }
    private Button button(LinearLayout row,String label){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setMinWidth(0);b.setPadding(0,0,0,0);row.addView(b,new LinearLayout.LayoutParams(0,-2,1));return b;}
    private void action(Button b,int side){b.setOnClickListener(v->game.strike(side));b.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN){v.setPressed(true);game.strike(side);return true;}if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){v.setPressed(false);return true;}return true;});}
    private void leave(){new AlertDialog.Builder(this).setTitle("返回选单？").setMessage("本局会结束，最高分会保留。").setPositiveButton("选单",(d,w)->{startActivity(new Intent(this,LibraryActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));finish();}).setNegativeButton("继续",null).show();}
    @Override public void onBackPressed(){leave();}
    @Override protected void onResume(){super.onResume();foreground=true;if(game!=null)game.suspend(false);}
    @Override protected void onPause(){foreground=false;if(game!=null)game.suspend(true);super.onPause();}
    @Override protected void onDestroy(){destroyed=true;if(game!=null){game.close();game=null;}super.onDestroy();}
}
