package nz.arcadetouch;

/** Player-one input state shared by the Android controls and the emulator frame loop. */
public final class InputState {
    public enum Mode {
        TOUCH,
        GAMEPAD
    }

    public enum Control {
        // The low 16 bits of the output use the libretro joypad button IDs.
        ATTACK(8),
        JUMP(0),
        COIN(2),
        START(3),
        UP(4),
        DOWN(5),
        LEFT(6),
        RIGHT(7),
        ATTACK_AND_JUMP(16);

        private final int bit;

        Control(int bitIndex) {
            bit = 1 << bitIndex;
        }
    }

    private Mode mode = Mode.TOUCH;
    private int heldControls;

    public synchronized Mode mode() {
        return mode;
    }

    public synchronized void setMode(Mode selectedMode) {
        if (selectedMode == mode) {
            return;
        }
        mode = java.util.Objects.requireNonNull(selectedMode);
        heldControls = 0;
    }

    public synchronized void setHeld(Mode source, Control control, boolean held) {
        if (source != mode) {
            return;
        }
        if (held) {
            heldControls |= control.bit;
        } else {
            heldControls &= ~control.bit;
        }
    }

    public synchronized void clear(Mode source) {
        if (source == mode) {
            heldControls = 0;
        }
    }

    /** Read once per emulator frame so both combo buttons change together. */
    public synchronized int joypadMask() {
        int mask = heldControls & 0xffff;
        if ((heldControls & Control.ATTACK_AND_JUMP.bit) != 0) {
            mask |= Control.ATTACK.bit | Control.JUMP.bit;
        }
        return mask;
    }
}
