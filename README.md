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

The launcher has a **Save game logcat for this run** setting. When enabled, it captures logcat lines for the game's
process into the app's private `files/game-logcat.txt`; reopen the launcher and choose **View saved game logcat**.
Capture stops after 5 MB per run. The native loader keeps its detailed log at `files/modloader.log` and uses the
`BBModLoader` logcat tag.

## BepInEx IL2CPP plugins

The launcher vendors the Android IL2CPP bootstrap/runtime from
[BepInEx.Android.Launcher](https://github.com/NextBep/BepInEx.Android) and builds it for **arm64-v8a**.
The build replaces the game's arm64 `libmain.so`, embeds the BepInEx and .NET runtime archives, and keeps the
existing `libmodloader.so` for native mods. BepInEx logs are emitted to Android logcat under the reference
bootstrap's `BepInExNative` tag. The game's 32-bit ABI continues to use the original Unity loader and native
mods; BepInEx is not enabled there.

Use **Import mods** to select a BepInEx runtime ZIP (with a `BepInEx/` root), a plugin `.dll`, or a ZIP containing
plugin DLLs. A BepInEx-root ZIP such as `api_v11202.zip` is extracted with its original folder structure into
app-private storage; loose plugin DLLs go to `BepInEx/plugins`. The game must be the matching Baldi's Basics Plus
0.14.3 IL2CPP build (Unity 2021.3.58f1, arm64) used as the base APK.

### Compatibility research

The launcher accepts and installs `api_v11202.zip`, but it cannot currently run that API. Its entry point derives
from `BepInEx.BaseUnityPlugin`, and the package includes `FixPluginTypesSerialization`, which the API source says is
needed for custom component serialization. Those are part of the BepInEx 5 Mono ecosystem. This launcher uses the
Android BepInEx 6 IL2CPP plugin runtime; the API needs a source port (including its Unity/game type references,
Harmony patches, and serialization support) before it can load. The import screen warns about this rather than
claiming success. Sources: [MTM101BMDE](https://github.com/benjaminpants/MTM101BMDE),
[NextBep Android](https://github.com/NextBep/BepInEx.Android), and the
[BepInEx IL2CPP plugin guide](https://docs.bepinex.dev/master/articles/dev_guide/plugin_tutorial/2_plugin_start.html).

BB+ mod projects commonly depend on the MTM API and other BepInEx 5 Mono libraries; for example,
[ModdedModesAPI](https://github.com/PixelGuy123/ModdedModesAPI) documents BepInEx 5.4.2x as a dependency. That means
converting only the launcher or moving DLLs into `plugins` does not make existing desktop mods Android-compatible.
The platform/runtime adaptation and API port are separate work. The BepInEx bootstrap itself has not yet been
validated on a device; CI is configured with NDK 27 and CMake 3.22 to build the arm64 native components.
