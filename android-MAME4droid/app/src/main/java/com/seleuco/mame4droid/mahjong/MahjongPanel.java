package com.seleuco.mame4droid.mahjong;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.*;
import com.seleuco.mame4droid.Emulator;
import com.seleuco.mame4droid.MAME4droid;
import java.util.HashMap;
import java.util.Map;

/** Sends keyboard input directly to JNI, independently of virtual-keyboard preferences. */
public final class MahjongPanel extends LinearLayout {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<Integer, Long> pressed = new HashMap<>();
    private final Map<Integer, Runnable> releases = new HashMap<>();
    private final MAME4droid activity;
    public MahjongPanel(MAME4droid a) {
        super(a); activity = a; setOrientation(VERTICAL); setBackgroundColor(Color.rgb(18,43,43));
        for(int row=0;row<2;row++) {
            LinearLayout line = row();
            for(int col=0;col<7;col++) { int n=row*7+col; key(line, String.valueOf((char)('A'+n)), KeyEvent.KEYCODE_A+n); }
        }
        LinearLayout actions = row();
        key(actions,"吃",KeyEvent.KEYCODE_SPACE); key(actions,"碰",KeyEvent.KEYCODE_ALT_LEFT);
        key(actions,"杠",KeyEvent.KEYCODE_CTRL_LEFT); key(actions,"立直",KeyEvent.KEYCODE_SHIFT_LEFT);
        key(actions,"和牌",KeyEvent.KEYCODE_Z); key(actions,"投币",KeyEvent.KEYCODE_5); key(actions,"开始",KeyEvent.KEYCODE_1);
        LinearLayout utility = row();
        Button library = button(utility,"换游戏"); library.setOnClickListener(v -> leave());
        key(utility,"←",KeyEvent.KEYCODE_DPAD_LEFT); key(utility,"↑",KeyEvent.KEYCODE_DPAD_UP);
        key(utility,"↓",KeyEvent.KEYCODE_DPAD_DOWN); key(utility,"→",KeyEvent.KEYCODE_DPAD_RIGHT);
        key(utility,"确认",KeyEvent.KEYCODE_ENTER);
        key(utility,"下注",KeyEvent.KEYCODE_3);
    }
    private LinearLayout row() {
        LinearLayout r = new LinearLayout(getContext());
        int dp = getResources().getConfiguration().orientation == 2 ? 40 : 48;
        addView(r,new LayoutParams(-1,Math.round(dp*getResources().getDisplayMetrics().density))); return r;
    }
    private Button button(LinearLayout row,String label) {
        Button b = new Button(getContext()); b.setText(label); b.setTextSize(12); b.setAllCaps(false);
        b.setMinWidth(0); b.setMinimumWidth(0); b.setPadding(0,0,0,0);
        row.addView(b,new LayoutParams(0,-1,1)); return b;
    }
    private void key(LinearLayout row,String label,int code) {
        Button b = button(row,label); b.setContentDescription(label);
        b.setOnTouchListener((v,event) -> {
            switch(event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if(!Emulator.isEmulating()) return true;
                    Runnable pending = releases.remove(code); if(pending != null) handler.removeCallbacks(pending);
                    pressed.put(code,SystemClock.uptimeMillis());
                    Emulator.setKeyData(code,Emulator.KEY_DOWN,(char)0); b.setPressed(true); return true;
                case MotionEvent.ACTION_UP:
                    b.setPressed(false); Long start = pressed.get(code);
                    if(start != null) {
                        Runnable release = () -> { Emulator.setKeyData(code,Emulator.KEY_UP,(char)0); pressed.remove(code); releases.remove(code); };
                        releases.put(code,release); handler.postDelayed(release,Math.max(0,60-(SystemClock.uptimeMillis()-start)));
                    }
                    v.performClick(); return true;
                case MotionEvent.ACTION_CANCEL:
                    releaseAll(); b.setPressed(false); return true;
                default: return true;
            }
        });
        // Accessibility services invoke click rather than touch.
        b.setOnClickListener(v -> {
            if(!Emulator.isEmulating() || pressed.containsKey(code)) return;
            pressed.put(code,SystemClock.uptimeMillis()); Emulator.setKeyData(code,Emulator.KEY_DOWN,(char)0);
            Runnable release = () -> { Emulator.setKeyData(code,Emulator.KEY_UP,(char)0); pressed.remove(code); releases.remove(code); };
            releases.put(code,release); handler.postDelayed(release,60);
        });
    }
    public void releaseAll() {
        handler.removeCallbacksAndMessages(null);
        for(int code : pressed.keySet()) Emulator.setKeyData(code,Emulator.KEY_UP,(char)0);
        pressed.clear(); releases.clear();
    }
    public void leave() {
        releaseAll();
        new AlertDialog.Builder(activity).setTitle("更换游戏？")
            .setMessage("将结束当前游戏并返回麻将游戏列表。当前未保存的进度会丢失。")
            .setPositiveButton("换游戏",(d,w) -> { activity.returnToMahjongLibrary(); })
            .setNegativeButton("继续游戏",null).show();
    }
    @Override protected void onDetachedFromWindow() { releaseAll(); super.onDetachedFromWindow(); }
}
