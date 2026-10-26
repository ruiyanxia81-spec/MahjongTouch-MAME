package nz.kutartouch;
import java.util.*;
/** Reconstructed basic rules. Original timing/boss patterns are not recovered. */
public final class ChanState {
    public enum Mode { TITLE, PLAYING, OVER }
    public static final class Enemy {public double x;public final int side,skin;public final boolean boss;Enemy(int side,int skin,boolean boss){this.side=side;this.skin=skin;this.boss=boss;x=side<0?-35:435;}}
    public final ArrayList<Enemy> enemies=new ArrayList<>();public Mode mode=Mode.TITLE;
    public int score,streak,slashSide;public double elapsed,slashAge=1,spawnIn;
    public String message="点击开始，敌人进入攻击范围时出剑";
    private final Random random;private int sinceBoss;
    public ChanState(){this(new Random());}public ChanState(Random random){this.random=random;}
    public void start(){enemies.clear();score=0;streak=0;sinceBoss=0;elapsed=0;slashAge=1;spawnIn=.9;mode=Mode.PLAYING;message="敌人靠近后按对应方向出剑；空挥或碰撞则结束";}
    public void step(double dt){
        if(mode!=Mode.PLAYING)return;elapsed+=dt;slashAge+=dt;spawnIn-=dt;
        if(spawnIn<=0){boolean boss=sinceBoss>=10;if(boss&&enemies.size()>0)spawnIn=.2;else{enemies.add(new Enemy(random.nextBoolean()?-1:1,random.nextInt(4),boss));if(boss)sinceBoss=0;spawnIn=boss?2.4:Math.max(.65,1.65-elapsed*.008)+random.nextDouble()*.35;}}
        for(Enemy e:enemies){double speed=e.boss?45:Math.min(160,58+elapsed*.62);e.x-=e.side*speed*dt;if(Math.abs(e.x-200)<=33){fail("敌人碰到了你");break;}}
    }
    public int strike(int side){
        if(mode!=Mode.PLAYING)return 0;slashSide=side;slashAge=0;Enemy closest=null;double distance=Double.MAX_VALUE;
        for(Enemy e:enemies){double d=(e.x-200)*side;if(e.side==side&&d>=0&&d<distance){distance=d;closest=e;}}
        if(closest!=null&&distance<=103&&distance>33){enemies.remove(closest);int points=closest.boss?10:1;score+=points;streak++;if(!closest.boss)sinceBoss++;message=closest.boss?"武士击破 +10":"命中 +1";return points;}
        fail("空挥了！等敌人靠近再出剑");return -1;
    }
    private void fail(String why){mode=Mode.OVER;message=why;}
}
