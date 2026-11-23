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
mkdir -p work/dex
"$BT/d8" --release --min-api 21 --lib "$AJAR" --output work/dex $(find work/cls -name '*.class')

echo "== decode + patch"
java -jar apktool.jar d -f -o work/dec "$BASE"
python3 tools/inject.py work/dec "$NEWPKG"
python3 tools/fixres.py work/dec
java -jar apktool.jar b -o work/unsigned.apk work/dec

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
