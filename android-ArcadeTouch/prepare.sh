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

    fun sendDirectionDoubleTap(keyCode: Int) {
        queueEvent {
            if (dashDirection == null) {
                dashDirection = keyCode
                dashFrame = 0
            }
        }
    }

    private fun advanceDirectionDoubleTap() {
        val direction = dashDirection ?: return
        when (dashFrame++) {
            0, 6 -> LibretroDroid.onKeyEvent(0, KeyEvent.ACTION_UP, direction)
            3, 9 -> LibretroDroid.onKeyEvent(0, KeyEvent.ACTION_DOWN, direction)
            12 -> dashDirection = null
        }
    }

    fun sendJoypadKeys(released: IntArray, pressed: IntArray) {
        queueEvent {
            if (released.any { it == dashDirection }) dashDirection = null
            released.forEach { LibretroDroid.onKeyEvent(0, KeyEvent.ACTION_UP, it) }
            pressed.forEach { LibretroDroid.onKeyEvent(0, KeyEvent.ACTION_DOWN, it) }
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
view.write_text(source.replace(frame, "                advanceDirectionDoubleTap()\n" + frame))
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
