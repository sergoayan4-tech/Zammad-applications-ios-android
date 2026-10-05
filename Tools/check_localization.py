#!/usr/bin/env python3
"""Cross-checks localization keys between Localization.swift and the sources."""
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "Zammad")

loc_path = os.path.join(SRC, "Core", "Localization.swift")
loc = open(loc_path, encoding="utf-8").read()

en_part = loc.split("static let en: [String: String] = [", 1)[1].split("static let ru:", 1)[0]
ru_part = loc.split("static let ru: [String: String] = [", 1)[1]

pair = re.compile(r'"([a-zA-Z0-9_.]+)"\s*:\s*"((?:[^"\\]|\\.)*)"')
en = dict(pair.findall(en_part))
ru = dict(pair.findall(ru_part))

print("en keys: %d, ru keys: %d" % (len(en), len(ru)))
print("missing in ru:", sorted(set(en) - set(ru)) or "-")
print("missing in en:", sorted(set(ru) - set(en)) or "-")

swift_files = []
for dirpath, _, filenames in os.walk(SRC):
    for name in filenames:
        if name.endswith(".swift"):
            swift_files.append(os.path.join(dirpath, name))

used = set()
pat_call = re.compile(r'\.t\("([^"]+)"\)')
pat_lit = re.compile(r'"([a-z][a-zA-Z0-9]*(?:\.[a-zA-Z0-9]+)+)"')

prefixes = (
    "tickets.", "error.", "connect.", "common.", "ticket.", "new.",
    "settings.", "reply.", "tab.", "article.",
)
non_l10n = {
    "app.zammad.ios.credentials", "prefs.language", "prefs.appearance",
    "session.server", "session.authMode", "session.username", "session.secret",
    "CFBundleShortVersionString", "CFBundleVersion",
}

for path in swift_files:
    text = open(path, encoding="utf-8").read()
    for match in pat_call.finditer(text):
        used.add(match.group(1))
    for match in pat_lit.finditer(text):
        value = match.group(1)
        if value in en or value.startswith(prefixes):
            used.add(value)

missing = sorted(key for key in used if key not in en and key not in non_l10n)
unused = sorted(key for key in en if key not in used)

print()
print("swift files: %d" % len(swift_files))
print("used keys: %d" % len(used))
print("USED BUT NOT DEFINED:", missing or "-")
print("DEFINED BUT UNUSED:", unused or "-")

problems = []
missing_ru = sorted(set(en) - set(ru))
missing_en = sorted(set(ru) - set(en))
if missing_ru:
    problems.append("keys missing in ru: %s" % ", ".join(missing_ru))
if missing_en:
    problems.append("keys missing in en: %s" % ", ".join(missing_en))
if missing:
    problems.append("used but not defined: %s" % ", ".join(missing))

if problems:
    print()
    for item in problems:
        print("FAIL: " + item)
    raise SystemExit(1)
print()
print("OK")
