package nz.kutartouch;
import android.content.Context;
import android.graphics.*;
import android.media.*;
import android.os.SystemClock;
import android.view.*;
import java.io.*;
import java.util.*;

final class ChanView extends View {
    final ChanState state=new ChanState();
    private final GameAssets assets;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Rect src=new Rect();private final RectF dst=new RectF();
    private final SoundPool sounds;private final Map<String,Integer> soundIds=new HashMap<>();private final Set<Integer> loaded=new HashSet<>();
    private MediaPlayer music;private boolean sound=true,paused=false,suspended=false;
    private long last=0,fpsStart=0;private int frames=0,fps=0,best=0;private boolean recorded=false;
    ChanView(Context c,GameAssets a){
        super(c);assets=a;setBackgroundColor(Color.rgb(29,27,48));best=c.getSharedPreferences("native-scores",0).getInt("chan",0);
        sounds=new SoundPool.Builder().setMaxStreams(6).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        sounds.setOnLoadCompleteListener((pool,id,status)->{if(status==0)loaded.add(id);});
        for(String name:new String[]{"swing","hit","hit2","over","go"}){File f=new File(a.directory,name+".wav");if(f.isFile())soundIds.put(name,sounds.load(f.getPath(),1));}
        try{music=new MediaPlayer();music.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());music.setDataSource(new File(a.directory,"bgm.wav").getPath());music.setLooping(true);music.setVolume(.45f,.45f);music.prepare();}catch(Exception e){if(music!=null)music.release();music=null;}
    }
    private void effect(String name){Integer id=soundIds.get(name);if(sound&&id!=null&&loaded.contains(id))sounds.play(id,1,1,1,0,1);}
    void start(){state.start();paused=false;recorded=false;last=0;effect("go");syncMusic();postInvalidateOnAnimation();}
    void strike(int side){if(paused||suspended)return;int result=state.strike(side);if(result>0){effect(result>1?"hit2":"hit");}else if(result<0){effect("swing");effect("over");syncMusic();}invalidate();}
    boolean togglePause(){paused=!paused;last=0;syncMusic();postInvalidateOnAnimation();return paused;}
    boolean toggleSound(){sound=!sound;syncMusic();return sound;}
    void suspend(boolean value){suspended=value;last=0;syncMusic();if(!value)postInvalidateOnAnimation();}
    private void syncMusic(){if(music==null)return;boolean play=sound&&!paused&&!suspended&&state.mode==ChanState.Mode.PLAYING;try{if(play&&!music.isPlaying())music.start();else if(!play&&music.isPlaying())music.pause();}catch(IllegalStateException ignored){}}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);long now=SystemClock.elapsedRealtimeNanos();double dt=last==0?0:Math.min(.05,(now-last)/1e9);last=now;
        if(!paused&&!suspended&&state.mode==ChanState.Mode.PLAYING){while(dt>0){double step=Math.min(dt,1.0/120);state.step(step);dt-=step;}if(state.mode==ChanState.Mode.OVER){effect("over");syncMusic();}}
        if(state.mode==ChanState.Mode.OVER&&!recorded){recorded=true;best=Math.max(best,state.score);getContext().getSharedPreferences("native-scores",0).edit().putInt("chan",best).apply();}
        frames++;if(fpsStart==0)fpsStart=now;if(now-fpsStart>=1_000_000_000L){fps=(int)(frames*1e9/(now-fpsStart));frames=0;fpsStart=now;}
        float scale=Math.min(getWidth()/400f,getHeight()/300f);c.save();c.translate((getWidth()-400*scale)/2,(getHeight()-300*scale)/2);c.scale(scale,scale);
        if(state.mode==ChanState.Mode.TITLE){Bitmap title=assets.image("title");if(title!=null){paint.setAlpha(255);c.drawBitmap(title,null,new RectF(0,0,400,300),paint);}text(c,"原生基础玩法测试 · 点击上方开始",200,289,12,Color.WHITE);}
        else{
            Bitmap bg=assets.image("bg");draw(c,bg,0,117,400,300,0,35,400,255);draw(c,bg,0,0,400,117,0,255,400,300);
            paint.setColor(Color.rgb(29,27,48));c.drawRect(0,0,400,35,paint);
            text(c,"得分 "+state.score,12,24,17,Color.WHITE,false);text(c,"最高 "+best,200,24,14,Color.rgb(255,222,150));text(c,fps+" FPS",388,24,11,Color.LTGRAY,true);
            // Subtle range marks are an aid in this test build, not original artwork.
            paint.setColor(0x77885930);paint.setStrokeWidth(1);c.drawLine(97,278,167,278,paint);c.drawLine(233,278,303,278,paint);
            for(ChanState.Enemy e:state.enemies)enemy(c,e);player(c);
            if(state.mode==ChanState.Mode.OVER){paint.setColor(0xb51d1b30);c.drawRect(0,0,400,300,paint);text(c,"游戏结束",200,123,27,Color.WHITE);text(c,state.message,200,153,15,Color.rgb(255,222,150));text(c,"得分 "+state.score+" · 点开始重来",200,183,17,Color.WHITE);}
            else if(paused){paint.setColor(0xb51d1b30);c.drawRect(0,0,400,300,paint);text(c,"已暂停",200,160,28,Color.WHITE);}
            else text(c,state.message,200,298,10,Color.rgb(60,43,23));
        }c.restore();if(!paused&&!suspended&&state.mode==ChanState.Mode.PLAYING)postInvalidateOnAnimation();
    }
    private void enemy(Canvas c,ChanState.Enemy e){
        float x=(float)e.x;int frame=(int)(state.elapsed*9)%5;
        if(!e.boss){int row=e.skin*2+(e.side<0?1:0);draw(c,assets.image("ninja"),frame*80,row*135,frame*80+79,row*135+133,x-26,196,x+26,284);}
        else {int color=e.skin%5;int pose=(int)(state.elapsed*3)%3;draw(c,assets.image("samurai1"),pose*131,color*146,pose*131+130,color*146+145,x-40,163,x+40,253);draw(c,assets.image("samurai1"),frame*75,731+color%3*53,frame*75+74,783+color%3*53,x-22,252,x+23,284);}
    }
    private void player(Canvas c){
        Bitmap p=assets.image("kutar");int body=(int)(state.elapsed*4)%2;
        draw(c,p,body*73,520,body*73+72,605,175,184,225,244);
        draw(c,p,145,520,216,565,175,244,225,276);
        draw(c,p,280,0,357,52,176,172,224,204);
        int left=1,right=1;if(state.slashAge<.22){int row=(int)(state.slashAge/.22*4);if(state.slashSide<0)left=Math.min(3,row);else right=Math.min(3,row);}
        draw(c,p,0,left*130,136,left*130+129,108,124,201,216);
        draw(c,p,137,right*130,279,right*130+129,199,124,296,216);
    }
    private void draw(Canvas c,Bitmap b,int l,int t,int r,int bottom,float x,float y,float right,float down){if(b==null)return;src.set(l,t,Math.min(r,b.getWidth()),Math.min(bottom,b.getHeight()));dst.set(x,y,right,down);paint.setAlpha(255);c.drawBitmap(b,src,dst,paint);}
    private void text(Canvas c,String value,float x,float y,float size,int color){text(c,value,x,y,size,color,true);paint.setTextAlign(Paint.Align.CENTER);}
    private void text(Canvas c,String value,float x,float y,float size,int color,boolean end){paint.setColor(color);paint.setTextSize(size);paint.setTypeface(Typeface.DEFAULT_BOLD);paint.setTextAlign(end?(x==200?Paint.Align.CENTER:Paint.Align.RIGHT):Paint.Align.LEFT);c.drawText(value,x,y,paint);}
    void close(){suspended=true;if(music!=null){music.release();music=null;}sounds.release();assets.close();}
}
