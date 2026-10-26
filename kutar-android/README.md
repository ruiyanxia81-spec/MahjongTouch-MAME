## 0.2.1 character feedback update

Cycles original torso/leg poses and adds idle bob/attack lean. Uses an independent visual clock to finish a 340 ms sword swing even when an empty swing has already ended the logical round; the game-over overlay waits for the animation. Adds directional slash feedback. Pause/background suspend visual time. These animations are a reconstruction, not verified original motion timing. CI covers pose progression, direction/retrigger and reset as well as existing gameplay rules. Phone visual verification remains required.

# Kutar Touch Native 0.2.0 — Chan basic-gameplay reconstruction

This update responds to the user's report that the original-EXE compatibility build is extremely slow on their phone. The runtime has been removed. Android Canvas draws original imported sprites; time-based Java logic handles gameplay; MediaPlayer/SoundPool play imported audio. No Windows/x86/Wine/Boxedwine/WebView/WASM code runs in this version.

## Honest scope

Only **Chan** is playable in this native prototype. HipDance and Endroll remain listed but disabled and explicitly labelled pending native rewrite. The other games' rules must be verified before reproducing them.

Implemented: left/right attacks, enemies approaching from either side, distance-and-side hit checks, miss/contact game over, progressive speed, score/best score, original background/sprite/audio assets, title/start/restart, pause, sound toggle, layout changes preserving the active game state. Test aids show attack ranges and rendered FPS. Fixed logical viewport is 400×300, scaled to fit without changing aspect ratio.

Game logic is reconstructed **basic gameplay**, not original source recovery. Spawn intervals, speed, hit ranges, score values and the simplified samurai encounters are provisional. Original duel timing, special characters/events, exact original animation composition and scoring calibration remain unfinished. Do not describe this as a finished faithful port. It needs phone testing and comparison with the Windows original.

The user still imports their original Chan.exe; existing 0.1 imports are reused. The importer verifies the known file SHA-256. The native asset decoder then reads the embedded resource table and BMP/WAV data without executing the EXE. No original game assets or EXEs are in this repository or APK. For future asset-parser changes, increment the cache directory revision.

## Validation

CI independently checks title/start/restart, valid hit, wrong-side miss, empty/distant miss, contact loss, and movement/spawns at 60 vs 120 updates per second. Then it compiles the Android APK and checks package/launcher identity, small package size, and absence of the former runtime assets. These checks are not a measurement of phone FPS, audio support, sprite composition or input latency.

Phone regression: launch native Chan; confirm title, background/character sprite alignment and sound; start and test both attack buttons; compare displayed FPS/input latency against the slow compatibility version; check pause/resume, screen rotation, fold/unfold, game over/restart and saved best score. Compare timing and special encounters with the Windows original before claiming gameplay parity.

The app keeps `nz.kutartouch.classics` and increments versionCode to 2 / versionName 0.2.0. The workflow uses the same debug signing cache on the same branch so it should update the previous test app while retaining imported EXEs. No production signing is claimed.
