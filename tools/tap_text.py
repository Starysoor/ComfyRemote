#!/usr/bin/env python3
"""Tap the center of a UI node by its visible text (fresh uiautomator dump)."""
import re, subprocess, sys, time

ADB = r'F:\Projects\android-sdk\platform-tools\adb.exe'
TEXT = sys.argv[1]
OCCURRENCE = int(sys.argv[2]) if len(sys.argv) > 2 else 0
SLEEP = float(sys.argv[3]) if len(sys.argv) > 3 else 1.0

def dump():
    subprocess.run([ADB, 'shell', 'uiautomator', 'dump', '/sdcard/ui.xml'], capture_output=True)
    r = subprocess.run([ADB, 'shell', 'cat', '/sdcard/ui.xml'], capture_output=True)
    return r.stdout.decode('utf-8', 'replace')

def tap(x, y):
    subprocess.run([ADB, 'shell', 'input', 'tap', str(x), str(y)])

xml = dump()
blocks = re.findall(r'<node[^>]*/>|<node[^>]*>', xml)
centers = []
for b in blocks:
    if f'text="{TEXT}"' in b:
        m = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', b)
        if m:
            x1, y1, x2, y2 = map(int, m.groups())
            centers.append(((x1 + x2) // 2, (y1 + y2) // 2))
if len(centers) <= OCCURRENCE:
    print(f'NOT FOUND: "{TEXT}" (occurrence {OCCURRENCE}); found {len(centers)}')
    sys.exit(1)
x, y = centers[OCCURRENCE]
print(f'tap "{TEXT}" [{OCCURRENCE}] at {x},{y}')
tap(x, y)
time.sleep(SLEEP)
