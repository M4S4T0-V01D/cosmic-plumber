#!/usr/bin/env python3
"""Writes art/icon-512.png, the Void Store icon: 32 x 32 pixel art scaled up 16 times with nearest-neighbor.

A pipe wrench over the title screen's Chicago sunset, with the Sears Tower and a UFO.

    python3 art/gen_icon.py
"""
from pathlib import Path

from PIL import Image

OUT = Path(__file__).resolve().parent / "icon-512.png"
N = 32

SKY = [(0x1A, 0x10, 0x33), (0x2A, 0x16, 0x48), (0x3B, 0x1F, 0x5C), (0x5C, 0x24, 0x66), (0x8A, 0x2E, 0x62),
       (0xC0, 0x40, 0x55), (0xFF, 0x7A, 0x3D)]
INK = (0x0E, 0x0A, 0x1F)
LIT = (0xFF, 0xC2, 0x4D)
STAR = (0xFF, 0xFF, 0xFF)
GREEN = (0x3D, 0xFF, 0x9A)
BEAM = (0x4E, 0x7A, 0x74)
CITY = (0x26, 0x17, 0x42)
STEEL, STEEL_DK, STEEL_LT = (0xC0, 0xC4, 0xD0), (0x7A, 0x80, 0x90), (0xEE, 0xF0, 0xF6)
RED, RED_DK = (0xE0, 0x40, 0x40), (0x8A, 0x1E, 0x2E)


def main() -> None:
    im = Image.new("RGB", (N, N))
    px = im.load()

    def rect(x, y, w, h, c):
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                if 0 <= xx < N and 0 <= yy < N:
                    px[xx, yy] = c

    # Banded sunset, as on the title screen.
    for y in range(N):
        rect(0, y, N, 1, SKY[min(len(SKY) - 1, y * len(SKY) // 24)])
    for x, y in [(3, 2), (12, 4), (24, 2), (22, 8), (6, 10), (30, 12)]:
        px[x, y] = STAR

    # UFO with its tractor beam.
    for i in range(7):
        rect(5 - i // 3, 8 + i, 3 + i // 3 * 2, 1, BEAM)
    rect(5, 5, 3, 1, (0x8F, 0xD8, 0xFF))
    rect(3, 6, 7, 2, STEEL)
    px[4, 7], px[6, 7], px[8, 7] = (0xFF, 0x5C, 0x8A), LIT, (0xFF, 0x5C, 0x8A)

    # Skyline: the Sears Tower on the right, lit windows, the lake in front.
    for x, top, w in [(0, 21, 4), (4, 23, 3), (7, 19, 3), (10, 22, 4), (14, 20, 3), (17, 23, 3), (20, 12, 5),
                      (25, 18, 3), (28, 21, 4)]:
        rect(x, top, w, 27 - top, CITY)
        for wy in range(top + 2, 26, 2):
            for wx in range(x + 1, x + w - 1, 2):
                if (wx * 7 + wy * 3) % 5 < 2:
                    px[wx, wy] = LIT
    rect(21, 8, 1, 4, CITY)
    rect(23, 9, 1, 3, CITY)
    rect(0, 27, N, 5, (0x1C, 0x2E, 0x5C))
    for y, w in [(28, 8), (29, 6), (30, 4)]:
        rect(22 - w // 2, y, w, 1, (0xC0, 0x60, 0x50))

    # Pipe wrench: handle from bottom left up to the shank, two parallel jaws opening to the right, outlined in ink.
    parts = [(18, 5, 8, 3), (19, 5, 3, 10), (19, 12, 7, 2)]  # upper jaw, shank, lower jaw
    for i in range(14):
        rect(5 + i, 26 - i, 4, 5, INK)
    for x, y, w, h in parts:
        rect(x - 1, y - 1, w + 2, h + 2, INK)
    for i in range(14):
        x, y = 6 + i, 27 - i
        rect(x, y - 1, 2, 3, RED)
        px[x + 1, y + 1] = RED_DK
    for x, y, w, h in parts:
        rect(x, y, w, h, STEEL)
    rect(18, 5, 8, 1, STEEL_LT)
    for x in (22, 24):                  # teeth
        px[x, 7] = STEEL_DK
        px[x + 1, 12] = STEEL_DK
    rect(19, 9, 3, 2, STEEL_DK)         # knurled adjusting nut
    px[20, 9] = STEEL_LT

    # A green cosmic sparkle off the jaws.
    for x, y in [(28, 3), (29, 2), (30, 3), (29, 4), (29, 3)]:
        px[x, y] = GREEN

    im.resize((512, 512), Image.NEAREST).save(OUT, optimize=True)


if __name__ == "__main__":
    main()
