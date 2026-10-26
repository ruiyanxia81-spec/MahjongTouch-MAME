# Mahjong Touch 0.7.0 test build

This continues `mahjongtouch/standalone-brand` / PR #2. It is a dedicated Android Mahjong frontend using the existing MAME4droid JNI and MAME 0.289 core, not a new emulator core.

## Implemented

- Private emulator activity and a Mahjong-only launcher with an explicit initial 31-name catalogue. Unrecognised archives can be imported as dependencies but cannot be launched. The catalogue is deliberately limited; it is not every Mahjong game supported by MAME.
- Multi-select ZIP import through Android's document picker. Original lower-case ROM names, ZIP validation, atomic replacement, and an app-private shared ROM directory. No ROM contents are supplied.
- A–N, Chi (Space), Pon (left Alt), Kan (left Ctrl), Riichi (left Shift), Win (Z), Coin (5), Start (1). Direct JNI keyboard events, minimum 60 ms taps, independent key state, cancellation/pause/detach release. Arrows and OK handle startup notices.
- Responsive game viewport above four rows of controls. Screen/density/layout configuration changes rebuild Android views while leaving the native emulation thread alive. This still needs OPPO Find N3 testing; Android can terminate background processes.
- Separate engine process permits returning to the library and launching another ROM without reusing stale native core state. Games/Back asks before ending the game. Current game progress is not carried between different ROM launches.
- Independent `nz.mahjongtouch.arcade` package, Mahjong tile icon, version 0.7.0.

## Build integrity

Earlier successful builds compiled only the JNI loader: the source repository's `jniLibs/arm64-v8a` contains no MAME core. The updated workflow extracts the arm64 libraries from the official upstream v1.41.2 APK, pinned by SHA-256 `b2e05497f4c7b8b0d5ac84a4755569050f60a4c9c69c7e219f6783bd9c5bb2ad`, and builds this project's JNI loader from source. It checks the core's keyboard symbol, packaged ELF architecture and size, application identity and launcher. See the workflow for the exact source URL. Original GPL/MAME notices remain; upstream source and build instructions are in this fork and at https://github.com/seleuco/MAME4droid-Current/tree/v1.41.2 .

Compilation and package inspection do **not** prove successful game boot, input behaviour, audio or fold continuity.

## Phone test

1. Download the workflow artifact, unzip it, and install `MahjongTouch-0.7.0-debug.apk` (allow installation from your download/file manager). This is an arm64 debug-signed test build.
2. Open Mahjong Touch. Import `mjelctrn.zip` and matching dependency ZIPs together. Include `ym2413.zip` if required by the MAME 0.289 set; do not unzip or rename ROM files. An archive passing ZIP validation does not prove its ROM checksums match.
3. Tap Play for mjelctrn. Read any missing-file or checksum message from MAME. Use OK/arrows for startup notices. Report the exact message if the game does not boot.
4. Test Coin and Start, every A–N tile key and Chi/Pon/Kan/Riichi/Win when legal in the game. Test quick taps and simultaneous holds; confirm no stuck keys after background/resume.
5. With a game running, unfold/fold the Find N3 and rotate it. Confirm the game, sound, tile selection and controls continue; record which transition fails if any. Enable OPPO's per-app cover-screen continuation setting if offered by the device.
6. Use Games, confirm exit, import another recognised Mahjong ROM with its dependencies, and launch it. Relaunch the app to confirm imported ZIPs persist.

If an older debug APK reports a signing conflict, Android requires uninstalling it before this CI-signed build can be installed. Uninstalling deletes app-private imported files; retain the original ZIPs outside the app.
