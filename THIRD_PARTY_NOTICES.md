# Third-party notices

## Android BepInEx bootstrap

The `native/main`, `native/nextbep`, `native/jniLibs/arm64-v8a`, and
`launcher/assets/BepInEx.Android.zip` files are derived from the Android
BepInEx launcher implementation, based on the
[NextBep/BepInEx.Android project](https://github.com/NextBep/BepInEx.Android).
The project is a BepInEx fork under LGPL-2.1; a copy is in
[`native/nextbep/LICENSE`](native/nextbep/LICENSE). Source corresponding to the
native bootstrap is included in this repository. The runtime archives and
prebuilt dependencies remain their upstream authors' works and retain their
upstream license terms.

## MTM101BaldAPI

The launcher does not redistribute `api_v11202.zip`; it imports the archive the
user selects. The API source is available at
[benjaminpants/MTM101BMDE](https://github.com/benjaminpants/MTM101BMDE) and its
repository identifies the project as MIT licensed. The API's source README
requires BepInEx 5 and `FixPluginTypesSerialization` for custom component
serialization. The launcher warns that this Mono package is not yet ported to
its BepInEx 6 IL2CPP runtime.

## Game files

No Baldi's Basics Plus game APK or game assets are added by this integration.
The build expects the user-provided `base/game.apk`.
