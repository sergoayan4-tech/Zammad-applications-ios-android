#!/usr/bin/env python3
"""Generates the app icon (Assets.xcassets/AppIcon.appiconset/AppIcon1024.png).

Pure standard library – no Pillow required:

    python Tools/generate_icon.py
"""

import os
import struct
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUTPUT = os.path.join(
    ROOT, "Zammad", "Assets.xcassets", "AppIcon.appiconset", "AppIcon1024.png"
)

SIZE = 1024

# Speech bubble geometry (in pixels).
BUBBLE = (0.15 * SIZE, 0.24 * SIZE, 0.85 * SIZE, 0.66 * SIZE, 0.10 * SIZE)
TAIL = (
    (0.28 * SIZE, 0.62 * SIZE),
    (0.46 * SIZE, 0.62 * SIZE),
    (0.30 * SIZE, 0.80 * SIZE),
)
DOT_RADIUS = 0.042 * SIZE
DOT_CENTERS = (0.40 * SIZE, 0.50 * SIZE, 0.60 * SIZE)
DOT_Y = 0.45 * SIZE

TOP_COLOR = (40, 98, 226)
BOTTOM_COLOR = (9, 24, 70)
DOT_COLOR = (30, 78, 190)


def in_round_rect(x, y):
    x0, y0, x1, y1, radius = BUBBLE
    if x < x0 or x > x1 or y < y0 or y > y1:
        return False
    inner_x0, inner_x1 = x0 + radius, x1 - radius
    inner_y0, inner_y1 = y0 + radius, y1 - radius
    if inner_x0 <= x <= inner_x1 or inner_y0 <= y <= inner_y1:
        return True
    corner_x = inner_x0 if x < inner_x0 else inner_x1
    corner_y = inner_y0 if y < inner_y0 else inner_y1
    return (x - corner_x) ** 2 + (y - corner_y) ** 2 <= radius ** 2


def _sign(p1, p2, p3):
    return (p1[0] - p3[0]) * (p2[1] - p3[1]) - (p2[0] - p3[0]) * (p1[1] - p3[1])


def in_triangle(x, y):
    a, b, c = TAIL
    d1 = _sign((x, y), a, b)
    d2 = _sign((x, y), b, c)
    d3 = _sign((x, y), c, a)
    has_neg = (d1 < 0) or (d2 < 0) or (d3 < 0)
    has_pos = (d1 > 0) or (d2 > 0) or (d3 > 0)
    return not (has_neg and has_pos)


def in_dot(x, y):
    if abs(y - DOT_Y) > DOT_RADIUS:
        return False
    for center in DOT_CENTERS:
        if (x - center) ** 2 + (y - DOT_Y) ** 2 <= DOT_RADIUS ** 2:
            return True
    return False


def background(x, y):
    t = y / (SIZE - 1)
    return (
        int(TOP_COLOR[0] + (BOTTOM_COLOR[0] - TOP_COLOR[0]) * t),
        int(TOP_COLOR[1] + (BOTTOM_COLOR[1] - TOP_COLOR[1]) * t),
        int(TOP_COLOR[2] + (BOTTOM_COLOR[2] - TOP_COLOR[2]) * t),
    )


def pixel(x, y):
    if in_round_rect(x, y) or in_triangle(x, y):
        if in_dot(x, y):
            return DOT_COLOR
        return (255, 255, 255)
    return background(x, y)


def chunk(tag, data):
    payload = tag + data
    return struct.pack(">I", len(data)) + payload + struct.pack(
        ">I", zlib.crc32(payload) & 0xFFFFFFFF
    )


def write_png(path, size, row_provider):
    raw = bytearray()
    for y in range(size):
        raw.append(0)  # filter type: None
        raw.extend(row_provider(y))

    header = struct.pack(">IIBBBBB", size, size, 8, 2, 0, 0, 0)  # 8-bit RGB
    payload = (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", header)
        + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
        + chunk(b"IEND", b"")
    )
    with open(path, "wb") as handle:
        handle.write(payload)


def main():
    os.makedirs(os.path.dirname(OUTPUT), exist_ok=True)

    cache = {}

    def row_provider(y):
        row = bytearray()
        for x in range(SIZE):
            key = (x, y)
            color = cache.get(key)
            if color is None:
                color = pixel(x, y)
                cache[key] = color
            row.extend(color)
        return row

    write_png(OUTPUT, SIZE, row_provider)
    print("Wrote %s" % OUTPUT)


if __name__ == "__main__":
    main()
