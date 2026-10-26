package nz.kutartouch;
import java.util.Random;
public class NativeRulesCheck {
    static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);}
    public static void main(String[] args){
        ChanState s=new ChanState(new Random(1));s.step(100);check(s.mode==ChanState.Mode.TITLE,"title must not simulate");
        s.start();ChanState.Enemy left=new ChanState.Enemy(-1,0,false);left.x=130;s.enemies.add(left);
        check(s.strike(-1)==1&&s.score==1&&s.enemies.isEmpty(),"correct side within reach hits once");
        s.start();s.enemies.add(left);check(s.strike(1)==-1&&s.mode==ChanState.Mode.OVER,"wrong side cannot hit opposite enemy");
        s.start();check(s.strike(-1)==-1,"empty swing ends basic mode");
        s.start();ChanState.Enemy far=new ChanState.Enemy(1,0,false);far.x=350;s.enemies.add(far);check(s.strike(1)==-1,"no hitting distant enemies");
        s.start();ChanState.Enemy close=new ChanState.Enemy(-1,0,false);close.x=168;s.enemies.add(close);s.step(.01);check(s.mode==ChanState.Mode.OVER,"contact ends game");
        s.start();check(s.score==0&&s.enemies.isEmpty(),"restart clears previous round");
        ChanState a=new ChanState(new Random(8)),b=new ChanState(new Random(8));a.start();b.start();
        for(int n=0;n<120;n++)a.step(1.0/60);for(int n=0;n<240;n++)b.step(1.0/120);
        check(a.enemies.size()==b.enemies.size(),"time-based spawn count independent of render rate");
        check(Math.abs(a.enemies.get(0).x-b.enemies.get(0).x)<2,"time-based movement independent of render rate");
        System.out.println("Native game state checks passed");
    }
}
