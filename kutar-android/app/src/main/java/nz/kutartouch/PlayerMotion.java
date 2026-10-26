package nz.kutartouch;
/** Visual time is separate from game state so a miss still shows its swing. */
final class PlayerMotion {
    private static final int[] BODY={0,1,2,3,2,1},LEGS={0,1,2,3,4,3,2,1};
    double clock,age=1;int side=-1;
    void reset(){clock=0;age=1;side=-1;}
    void attack(int direction){side=direction;age=0;}
    void step(double seconds){clock+=seconds;age+=seconds;}
    boolean swinging(){return age<.34;}
    int swordRow(){if(!swinging())return 1;if(age<.06)return 0;if(age<.17)return 3;if(age<.26)return 2;return 1;}
    int bodyRow(){return BODY[(int)(clock*6)%6];}
    int legsRow(){return LEGS[(int)(clock*7)%8];}
    float power(){return swinging()?(float)Math.sin(Math.PI*age/.34):0;}
}
