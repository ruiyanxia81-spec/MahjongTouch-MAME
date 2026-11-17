#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
upstream=vendor/LibretroDroid
if [[ ! -d "$upstream/.git" ]]; then
    git clone --no-checkout https://github.com/Swordfish90/LibretroDroid.git "$upstream"
fi
git -C "$upstream" checkout --detach --force 683652f8705a6953218444d72ed77d2c5913fffb
git -C "$upstream" submodule update --init --recursive
python3 - "$upstream" <<'PY'
import pathlib
import sys
project = pathlib.Path(sys.argv[1]) / "libretrodroid"
build = project / "build.gradle"
build.write_text(build.read_text().replace('    compileSdkVersion 33', '    compileSdkVersion 35\n    ndkVersion "28.2.13676358"'))
for header in ("rumble.h", "utils/javautils.h"):
    declaration = project / "src/main/cpp" / header
    declaration.write_text("#include <functional>\n" + declaration.read_text())
view = project / "src/main/java/com/swordfish/libretrodroid/GLRetroView.kt"
source = view.read_text()
anchor = "    fun sendMotionEvent("
assert source.count(anchor) == 1
source = source.replace(anchor, """    // These fields and the native input calls below belong to the GL thread.
    private var dashDirection: Int? = null
    private var dashFrame = 0

    private var dashWithAttack = false
    private var physicalAttackHeld = false
    private var dashAttackHeld = false
    private val attackKey = KeyEvent.KEYCODE_BUTTON_B

    private var attackTurboPeriod = 0
    private var attackTurboFrame = 0
    private var attackTurboBypassed = false
    private var sentAttackHeld = false

    fun setAttackTurboPeriod(period: Int) {
        queueEvent {
            attackTurboPeriod = period
            attackTurboFrame = 0
            syncAttackButton()
        }
    }

    private fun syncAttackButton() {
        val held = dashAttackHeld || (physicalAttackHeld &&
            (attackTurboBypassed || attackTurboPeriod == 0 || attackTurboFrame < attackTurboPeriod / 2))
        if (held == sentAttackHeld) return
        sentAttackHeld = held
        LibretroDroid.onKeyEvent(0, if (held) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, attackKey)
    }

    private fun advanceAttackTurbo() {
        if (!physicalAttackHeld || attackTurboBypassed || attackTurboPeriod == 0) return
        syncAttackButton()
        attackTurboFrame = (attackTurboFrame + 1) % attackTurboPeriod
    }

    private fun setDashAttack(held: Boolean) {
        dashAttackHeld = held
        syncAttackButton()
    }

    fun sendDirectionDoubleTap(keyCode: Int, attack: Boolean) {
        queueEvent {
            if (dashDirection == null) {
                dashDirection = keyCode
                dashFrame = 0
                dashWithAttack = attack
            }
        }
    }

    private fun advanceDirectionDoubleTap() {
        val direction = dashDirection ?: return
        when (dashFrame++) {
            0, 6 -> LibretroDroid.onKeyEvent(0, KeyEvent.ACTION_UP, direction)
            3, 9 -> LibretroDroid.onKeyEvent(0, KeyEvent.ACTION_DOWN, direction)
            12 -> if (dashWithAttack) setDashAttack(true) else dashDirection = null
            16 -> {
                setDashAttack(false)
                dashDirection = null
            }
        }
    }

    fun sendJoypadKeys(released: IntArray, pressed: IntArray, jumpHeld: Boolean) {
        queueEvent {
            if (attackTurboBypassed != jumpHeld || attackKey in released || attackKey in pressed) {
                attackTurboFrame = 0
            }
            attackTurboBypassed = jumpHeld
            if (released.any { it == dashDirection }) {
                dashDirection = null
                setDashAttack(false)
            }
            released.forEach {
                if (it == attackKey) physicalAttackHeld = false
                if (it != attackKey) LibretroDroid.onKeyEvent(0, KeyEvent.ACTION_UP, it)
            }
            pressed.forEach {
                if (it == attackKey) physicalAttackHeld = true
                if (it != attackKey) LibretroDroid.onKeyEvent(0, KeyEvent.ACTION_DOWN, it)
            }
            syncAttackButton()
        }
    }

""" + anchor)
surface = "        if (isGameLoaded) return@catchExceptions"
assert source.count(surface) == 1
source = source.replace(surface, """        if (isGameLoaded) {
            LibretroDroid.onSurfaceCreated()
            return@catchExceptions
        }""")
frame = "                LibretroDroid.step(this@GLRetroView)"
assert source.count(frame) == 1
view.write_text(source.replace(frame, "                advanceDirectionDoubleTap()\n                advanceAttackTurbo()\n" + frame))
PY
archive=$(mktemp)
trap 'rm -f "$archive"' EXIT
curl --fail --location --retry 3 --output "$archive" \
    https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/fbneo_libretro_android.so.zip
sha256sum "$archive"
mkdir -p app/src/main/jniLibs/arm64-v8a
core=app/src/main/jniLibs/arm64-v8a/libfbneo.so
unzip -p "$archive" fbneo_libretro_android.so > "$core"
test -s "$core"
readelf -h "$core" | grep AArch64
