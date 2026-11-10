#!/bin/bash
set -e
cd "$(dirname "$0")"
SDK=${SDK:-../sdk}
rm -rf out/classes out/dex out/base.apk out/raw.apk out/aligned.apk out/verwatch.apk
mkdir -p out/classes out/dex

aapt package -f -M srcres/AndroidManifest.xml -S srcres/res -I $SDK/android-30.jar -F out/base.apk

javac -encoding UTF-8 -source 8 -target 8 -nowarn -classpath $SDK/android.jar:libs/shizuku-api.jar:libs/shizuku-provider.jar:libs/shizuku-aidl.jar:libs/shizuku-shared.jar -d out/classes $(find src -name '*.java')

java -cp $SDK/r8.jar com.android.tools.r8.D8 --release --min-api 26 --lib $SDK/android.jar \
     --output out/dex $(find out/classes -name '*.class') libs/shizuku-api.jar libs/shizuku-provider.jar libs/shizuku-aidl.jar libs/shizuku-shared.jar

python3 - <<'PY'
import zipfile
src = zipfile.ZipFile("out/base.apk")
with zipfile.ZipFile("out/raw.apk", "w") as z:
    for name in src.namelist():
        data = src.read(name)
        ct = zipfile.ZIP_STORED if name == "resources.arsc" else zipfile.ZIP_DEFLATED
        z.writestr(zipfile.ZipInfo(name), data, ct)
    z.write("out/dex/classes.dex", "classes.dex", zipfile.ZIP_DEFLATED)
PY

zipalign -f 4 out/raw.apk out/aligned.apk
apksigner sign --ks out/verwatch.keystore --ks-key-alias verwatch \
  --ks-pass pass:verwatch123 --key-pass pass:verwatch123 \
  --out out/verwatch.apk out/aligned.apk
apksigner verify out/verwatch.apk && echo "OK out/verwatch.apk $(stat -c%s out/verwatch.apk) bytes"
