#!/usr/bin/env bash
set -euo pipefail
BASE=${BASE_APK:-base/game.apk}; NEWPKG=${NEW_PACKAGE:-com.baldimods.bbplus}
[ -f "$BASE" ] || { echo "Missing $BASE"; exit 1; }
SDK=${ANDROID_HOME:-/usr/local/lib/android/sdk}
BT=$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)
AJAR=$(ls -d "$SDK"/platforms/android-* | sort -V | tail -1)/android.jar
NDK=${ANDROID_NDK_LATEST_HOME:-${ANDROID_NDK_HOME:-$(ls -d "$SDK"/ndk/* | sort -V | tail -1)}}
TC="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
rm -rf work out && mkdir -p work out

echo "== native loader"
for pair in "arm64-v8a:aarch64-linux-android24" "armeabi-v7a:armv7a-linux-androideabi24"; do
  abi=${pair%%:*}; tgt=${pair##*:}
  mkdir -p "work/libs/lib/$abi"
  "$TC/$tgt-clang++" -shared -fPIC -O2 -std=c++17 -static-libstdc++ -Wl,-z,max-page-size=16384 \
     -Inative -o "work/libs/lib/$abi/libmodloader.so" native/modloader.cpp -llog -ldl
done
# example mod, ready to import
"$TC/aarch64-linux-android24-clang++" -shared -fPIC -O2 -std=c++17 -Inative -o out/hello.arm64-v8a.so examples/hello/hello.cpp -ldl
"$TC/armv7a-linux-androideabi24-clang++" -shared -fPIC -O2 -std=c++17 -Inative -o out/hello.armeabi-v7a.so examples/hello/hello.cpp -ldl

echo "== launcher dex"
mkdir -p work/cls work/dex
javac --release 8 -Xlint:-options -cp "$AJAR" -d work/cls $(find launcher/src -name '*.java')
"$BT/d8" --release --min-api 21 --lib "$AJAR" --output work/dex $(find work/cls -name '*.class')

echo "== decode + patch"
java -jar apktool.jar d -f -o work/dec "$BASE"
python3 tools/inject.py work/dec "$NEWPKG"
python3 tools/fixres.py work/dec
# Embed the reference launcher's BepInEx 6/IL2CPP core and CoreCLR payload.
mkdir -p work/dec/assets
cp launcher/assets/BepInEx.Android.zip launcher/assets/dotnet.zip work/dec/assets/
java -jar apktool.jar b -o work/unsigned.apk work/dec

echo "== build Android IL2CPP bootstrap (arm64 only)"
cmake -S native -B work/bepinex-cmake \
  -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-28 \
  -DANDROID_STL=c++_static -DCMAKE_BUILD_TYPE=Release
cmake --build work/bepinex-cmake --parallel
cp work/bepinex-cmake/out/libmain.so work/libs/lib/arm64-v8a/libmain.so
cp work/bepinex-cmake/out/libnextbep.so work/libs/lib/arm64-v8a/
cp native/jniLibs/arm64-v8a/*.so work/libs/lib/arm64-v8a/
# Remove Unity's original libmain so the BepInEx shim registers NativeLoader.
if unzip -Z1 work/unsigned.apk | grep -qx "lib/arm64-v8a/libmain.so"; then
  zip -q -d work/unsigned.apk lib/arm64-v8a/libmain.so
fi

echo "== add dex + native libs (stored, uncompressed)"
N=2; while unzip -Z1 work/unsigned.apk | grep -qx "classes$N.dex"; do N=$((N+1)); done
cp work/dex/classes.dex "work/classes$N.dex"
(cd work && zip -q -0 unsigned.apk "classes$N.dex")
(cd work/libs && zip -q -0 -r ../unsigned.apk lib)

echo "== align + sign"
"$BT/zipalign" -f -p 4 work/unsigned.apk work/aligned.apk
if [ -n "${KEYSTORE_B64:-}" ]; then
  printf '%s' "$KEYSTORE_B64" | tr -d ' \n\r\t"' | base64 -d > work/ks.jks
  PASS=${KEYSTORE_PASS:?KEYSTORE_PASS secret is empty}; ALIAS=${KEY_ALIAS:-key}
else
  echo "WARNING: no KEYSTORE_B64 - throwaway key; later builds will not install over this one"
  keytool -genkeypair -keystore work/ks.jks -alias key -keyalg RSA -keysize 2048 -validity 36500 \
    -storepass bbplusmods -keypass bbplusmods -dname "CN=BBPlusMods"
  PASS=bbplusmods; ALIAS=key
fi
"$BT/apksigner" sign --ks work/ks.jks --ks-pass "pass:$PASS" --key-pass "pass:$PASS" --ks-key-alias "$ALIAS" \
  --out out/bbplus-modded.apk work/aligned.apk
"$BT/apksigner" verify out/bbplus-modded.apk && echo "OK"
