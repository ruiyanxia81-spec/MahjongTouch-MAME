# Kutar Touch 0.1.0 — compatibility experiment

A dedicated offline Android launcher for the exact three user-supplied Windows games Chan.exe, HipDance.exe and Endroll.exe. Uses Boxedwine 26R1 WebAssembly and Wine 6.0 to execute original x86 Windows game code. This is **not a native Android reimplementation**. The originals are not committed, distributed or bundled; users import their own copies. Imports are limited to the SHA-256 hashes of the supplied versions.

- Independent package `nz.kutartouch.classics`, app name/icon and three-game launcher.
- Chinese left/right mouse buttons, F5 Start and F7 Reset, full-window/zoom toggle, bounded startup log.
- Configuration changes preserve the live WebView; a separate runtime process is ended on returning to the library. Phone fold continuity is not verified.
- Runtime files are supplied offline in the APK, pinned by SHA-256 in `.github/workflows/kutar-android.yml`. WebView serves a strict local resource allowlist; no arbitrary remote pages, filesystem access or generic program launcher.
- Browser fullscreen requests are suppressed so native/Web touch controls remain accessible.
- Stable debug keystore cache for subsequent test builds; this is a testing APK, not production signing.

## Validation and limits

All 74 embedded BMP/WAV resources were decoded and validated locally (25 Chan, 18 HipDance, 31 Endroll), establishing that original artwork/audio are present. Windows accelerator resources identify Start=F5 and Reset=F7. No decompiled game logic is substituted.

Desktop Chromium compatibility tests opened original game windows; HipDance reached gameplay after F5. Android compilation/package checks are separate from phone validation. First startup and emulation can be slow; animation speed, audio latency, simultaneous touches, saving, background/resume and OPPO Find N3 fold transitions must be tested on the phone. Embedded Wine menus can show incorrect Japanese text; original bitmap artwork is retained. The zoom assumes the original default 640×480 desktop and window position; use full-window mode for dialogs or if the crop is wrong.

## Phone test

Install APK, import the original three EXEs (unmodified), select a game, wait for the title, then tap Start F5. Test both Left and Right, restart, and switching among games. Use Full window if a dialog appears outside the zoomed game region. Open Startup log and send a screenshot if startup fails. Compare speed and audio against Windows. Test folding and background/resume separately.

See NOTICE.txt and the upstream pinned source/build links for the compatibility runtime. Existing Mahjong project code is untouched on its original development branch.
