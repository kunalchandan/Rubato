#!/usr/bin/env python3
import argparse
import os
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET


def resolve_adb():
    android_home = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if android_home:
        adb = os.path.join(android_home, "platform-tools", "adb")
        if os.path.exists(adb) or os.path.exists(adb + ".exe"):
            return adb
    local_appdata = os.environ.get("LOCALAPPDATA")
    if local_appdata:
        adb = os.path.join(local_appdata, "Android", "Sdk", "platform-tools", "adb.exe")
        if os.path.exists(adb):
            return adb
    return "adb"


def run_adb(adb, serial, args, check=True, capture=False):
    cmd = [adb]
    if serial:
        cmd += ["-s", serial]
    cmd += args
    if capture:
        return subprocess.check_output(cmd)
    if check:
        subprocess.check_call(cmd)
    else:
        subprocess.call(cmd)
    return b""


def ensure_device(adb, serial):
    output = run_adb(adb, serial, ["devices"], capture=True).decode("utf-8", errors="ignore")
    lines = [line.strip() for line in output.splitlines() if "\tdevice" in line]
    if serial:
        for line in lines:
            if line.startswith(serial + "\t"):
                return
        raise RuntimeError(f"Device {serial} not available. adb devices:\n{output}")
    if not lines:
        raise RuntimeError(f"No devices available. adb devices:\n{output}")


def capture_screenshot(adb, serial, out_path):
    data = run_adb(adb, serial, ["exec-out", "screencap", "-p"], capture=True)
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    with open(out_path, "wb") as f:
        f.write(data)


def set_pref_string(adb, serial, name, value):
    prefs_path = "/data/data/one.chandan.rubato/shared_prefs/one.chandan.rubato_preferences.xml"
    with tempfile.TemporaryDirectory() as tmp:
        local = os.path.join(tmp, "prefs.xml")
        with open(local, "wb") as f:
            data = run_adb(adb, serial, ["shell", "run-as", "one.chandan.rubato", "cat", prefs_path], capture=True)
            f.write(data)
        root = ET.parse(local).getroot()
        updated = False
        for node in root.findall("string"):
            if node.attrib.get("name") == name:
                node.text = value
                updated = True
                break
        if not updated:
            node = ET.SubElement(root, "string", {"name": name})
            node.text = value
        ET.ElementTree(root).write(local, encoding="utf-8", xml_declaration=True)
        run_adb(adb, serial, ["push", local, "/data/local/tmp/rubato_prefs.xml"])
        run_adb(adb, serial, ["shell", "run-as", "one.chandan.rubato", "cp",
                              "/data/local/tmp/rubato_prefs.xml", prefs_path])


def restart_app(adb, serial):
    run_adb(adb, serial, ["shell", "am", "force-stop", "one.chandan.rubato"], check=False)
    run_adb(adb, serial, ["shell", "am", "start", "-n",
                          "one.chandan.rubato/.ui.activity.MainActivity"], check=False)


def prompt(msg):
    print(f"\n{msg}")
    input("Press Enter to capture...")


def capture_set(adb, serial, base_dir, label, items):
    print(f"\n=== Capturing {label} screenshots ===")
    for idx, (filename, instruction) in enumerate(items, start=1):
        prompt(f"[{label} {idx}/{len(items)}] {instruction}")
        capture_screenshot(adb, serial, os.path.join(base_dir, filename))


def main():
    parser = argparse.ArgumentParser(description="Capture Rubato mockup screenshots via adb.")
    parser.add_argument("--serial", help="adb device serial")
    parser.add_argument("--set", choices=["all", "feat", "light", "dark"], default="all")
    parser.add_argument("--auto-theme", action="store_true", default=True,
                        help="Apply light/dark themes automatically before capture (default)")
    parser.add_argument("--no-auto-theme", dest="auto_theme", action="store_false",
                        help="Do not change theme automatically")
    args = parser.parse_args()

    adb = resolve_adb()
    ensure_device(adb, args.serial)

    root_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    mockup_dir = os.path.join(root_dir, "mockup")

    feat_items = [
        ("1_screenshot.png", "Home (feature highlight)"),
        ("2_screenshot.png", "Search"),
        ("3_screenshot.png", "Library"),
        ("4_screenshot.png", "Downloaded"),
        ("5_screenshot.png", "Album page"),
        ("6_screenshot.png", "Artist page"),
        ("7_screenshot.png", "Playlist page"),
        ("8_screenshot.png", "Player / now playing"),
    ]
    light_items = list(feat_items)
    dark_items = list(feat_items)

    if args.set in ("all", "feat"):
        restart_app(adb, args.serial)
        capture_set(adb, args.serial, os.path.join(mockup_dir, "feat"), "Feature", feat_items)

    if args.set in ("all", "light"):
        if args.auto_theme:
            set_pref_string(adb, args.serial, "theme", "light")
        restart_app(adb, args.serial)
        capture_set(adb, args.serial, os.path.join(mockup_dir, "light"), "Light", light_items)

    if args.set in ("all", "dark"):
        if args.auto_theme:
            set_pref_string(adb, args.serial, "theme", "dark")
        restart_app(adb, args.serial)
        capture_set(adb, args.serial, os.path.join(mockup_dir, "dark"), "Dark", dark_items)

    print("\nDone. Mockup screenshots updated.")


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"Error: {exc}")
        sys.exit(1)
