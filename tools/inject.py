#!/usr/bin/env python3
"""inject.py <decoded_dir> <new_package>
Edits an apktool-decoded Unity APK so it boots into our launcher under a new package name.
 - expands relative class names first (package rename must not change what they resolve to)
 - renames the package (permissions / authorities follow)
 - removes LAUNCHER from the Unity activity, adds LauncherActivity as the launcher
 - rewrites EXACT string literals of the old package in smali (the DeltaPatch 'Unable to locate assets' fix)
"""
import pathlib, sys
import xml.etree.ElementTree as ET

ANDROID = "http://schemas.android.com/apk/res/android"
A = "{%s}" % ANDROID
ET.register_namespace("android", ANDROID)
ET.register_namespace("app", "http://schemas.android.com/apk/res-auto")
ET.register_namespace("tools", "http://schemas.android.com/tools")
LAUNCHER_CLASS = "com.baldimods.launcher.LauncherActivity"
CLASS_TAGS = ("activity", "activity-alias", "service", "receiver", "provider")

def patch_manifest(dec, new_pkg):
    mf = dec / "AndroidManifest.xml"
    tree = ET.parse(mf); root = tree.getroot()
    old = root.get("package")
    if not old: sys.exit("manifest has no package attribute")
    if old == new_pkg: sys.exit("new package must differ from %s (it would replace the installed game)" % old)
    app = root.find("application")
    def absolute(n):
        if n.startswith("."): return old + n
        if "." not in n: return old + "." + n
        return n
    if app.get(A + "name"): app.set(A + "name", absolute(app.get(A + "name")))
    for el in app:
        if el.tag in CLASS_TAGS and el.get(A + "name"): el.set(A + "name", absolute(el.get(A + "name")))
        if el.get(A + "targetActivity"): el.set(A + "targetActivity", absolute(el.get(A + "targetActivity")))
    for el in root.iter():
        if el.tag in ("permission", "uses-permission", "uses-permission-sdk-23"):
            n = el.get(A + "name", "")
            if n.startswith(old + "."): el.set(A + "name", new_pkg + n[len(old):])
        for attr in ("authorities", "permission", "readPermission", "writePermission"):
            v = el.get(A + attr)
            if v and old in v: el.set(A + attr, v.replace(old, new_pkg))
    removed = 0
    for act in app.findall("activity"):
        for f in act.findall("intent-filter"):
            for cat in list(f.findall("category")):
                if cat.get(A + "name") in ("android.intent.category.LAUNCHER", "android.intent.category.LEANBACK_LAUNCHER"):
                    f.remove(cat); removed += 1
    el = ET.SubElement(app, "activity")
    el.set(A + "name", LAUNCHER_CLASS); el.set(A + "exported", "true")
    el.set(A + "label", "BB+ Mods")
    el.set(A + "theme", "@android:style/Theme.DeviceDefault.NoActionBar")
    f = ET.SubElement(el, "intent-filter")
    ET.SubElement(f, "action").set(A + "name", "android.intent.action.MAIN")
    ET.SubElement(f, "category").set(A + "name", "android.intent.category.LAUNCHER")
    app.set(A + "label", "BB+ Mods")
    root.set("package", new_pkg)
    tree.write(mf, encoding="utf-8", xml_declaration=True)
    print("manifest: %s -> %s (removed %d launcher categories)" % (old, new_pkg, removed))
    return old

def patch_literals(dec, old, new):
    n = 0
    for f in dec.glob("smali*/**/*.smali"):
        txt = f.read_text(encoding="utf-8")
        if '"%s"' % old not in txt: continue
        out = []
        for line in txt.split("\n"):
            if line.lstrip().startswith("const-string") and line.rstrip().endswith('"%s"' % old):
                line = line.replace('"%s"' % old, '"%s"' % new); n += 1
            out.append(line)
        f.write_text("\n".join(out), encoding="utf-8")
    print("smali: rewrote %d package literal(s)" % n)

if __name__ == "__main__":
    if len(sys.argv) != 3: sys.exit(__doc__)
    d = pathlib.Path(sys.argv[1])
    old = patch_manifest(d, sys.argv[2])
    patch_literals(d, old, sys.argv[2])
