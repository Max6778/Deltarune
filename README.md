# BB+ Mod Launcher (IL2CPP, native mods)
Private repo. Put the 0.14.3 APK at base/game.apk, run "Build modded APK", install `bbplus-modded.apk`
(package com.baldimods.bbplus, installs next to the original).

How it works: launcher screen -> PLAY loads libmodloader.so, starts the game; the loader thread waits for
libil2cpp.so + Assembly-CSharp, then copies each mods/*.so (or mods/<folder>/*.so) into internal storage,
dlopens it and calls `int mod_init(const BBModContext*)` (see native/modapi.h).
Files named *.armeabi-v7a.so / *.arm64-v8a.so are filtered to the running ABI.
Log: files/modloader.log (internal) and logcat tag BBModLoader.
Mods are written in C++ against the IL2CPP C API (resolve with dlsym on ctx->il2cpp_handle) using class names from
dump.cs; use the PC Assembly-CSharp.dll (ILSpy) for readable logic. Hooks: bundle a hooking lib (e.g. Dobby) in your mod
and use ctx->il2cpp_base + RVA from dump.cs. examples/hello shows the API (out/hello.*.so is built by the workflow).
