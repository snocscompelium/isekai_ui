#!/usr/bin/env python3
"""Gera as texturas (pixel art) da identidade visual marfim/dourado do Compelium's Isekai UI.

Uso: python3 tools/gen_textures.py <pasta_assets_isekai_ui> [preview.png]
As texturas podem ser editadas a mao depois (sao PNG comuns); este script so cria a base.
"""
import json
import math
import os
import sys

from PIL import Image, ImageDraw

INK = (27, 22, 15)
IVORY_HI = (250, 244, 226)
IVORY = (233, 223, 194)
IVORY_SH = (186, 168, 124)
GOLD = (201, 163, 90)
GOLD_HI = (236, 206, 136)
GOLD_DK = (132, 100, 48)
GLASS = (24, 21, 32)
GLASS_HI = (46, 40, 58)
GLASS_EDGE = (12, 10, 17)
GLOW = (255, 240, 200)
WHITE = (255, 255, 255)


def rgba(c, a=255):
    return (c[0], c[1], c[2], a)


def new(w, h):
    return Image.new("RGBA", (w, h), (0, 0, 0, 0))


def save(img, root, rel, nine=None):
    path = os.path.join(root, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    if nine is not None:
        w, h = img.size
        meta = {"gui": {"scaling": {"type": "nine_slice", "width": w, "height": h, "border": nine}}}
        with open(path + ".mcmeta", "w") as f:
            json.dump(meta, f, indent=2)


def ring(x, y, w, h):
    """Distancia (em aneis) do pixel ate a borda do retangulo."""
    return min(x, y, w - 1 - x, h - 1 - y)


def diamond(img, cx, cy, fill_hi=GOLD_HI, fill=GOLD, outline=INK):
    px = img.load()
    w, h = img.size
    for dy in range(-3, 4):
        for dx in range(-3, 4):
            x, y = cx + dx, cy + dy
            if not (0 <= x < w and 0 <= y < h):
                continue
            d = abs(dx) + abs(dy)
            if d == 0:
                px[x, y] = rgba(IVORY_HI)
            elif d == 1:
                px[x, y] = rgba(fill_hi)
            elif d == 2:
                px[x, y] = rgba(fill)
            elif d == 3:
                px[x, y] = rgba(outline, 235)


def corner_diamonds(img, inset=3):
    w, h = img.size
    for cx, cy in ((inset, inset), (w - 1 - inset, inset), (inset, h - 1 - inset), (w - 1 - inset, h - 1 - inset)):
        diamond(img, cx, cy)


# ---------------------------------------------------------------- janelas

def panel():
    w = h = 32
    img = new(w, h)
    px = img.load()
    for y in range(h):
        for x in range(w):
            r = ring(x, y, w, h)
            corner = (x in (0, w - 1)) and (y in (0, h - 1))
            if r == 0:
                px[x, y] = (0, 0, 0, 0) if corner else rgba(INK, 225)
            elif r == 1:
                top_left = (y == 1 and x < w - 2) or (x == 1 and y < h - 2)
                px[x, y] = rgba(IVORY_HI if top_left else IVORY_SH)
            elif r == 2:
                px[x, y] = rgba(IVORY)
            elif r == 3:
                px[x, y] = rgba(GOLD)
            elif r == 4:
                px[x, y] = rgba(GLASS_EDGE, 240)
            elif r == 5 and y == 5:
                px[x, y] = rgba(GLASS_HI, 222)
            else:
                px[x, y] = rgba(GLASS, 214)
    corner_diamonds(img)
    return img


def map_frame():
    w = h = 32
    img = new(w, h)
    px = img.load()
    for y in range(h):
        for x in range(w):
            r = ring(x, y, w, h)
            corner = (x in (0, w - 1)) and (y in (0, h - 1))
            if r == 0:
                px[x, y] = (0, 0, 0, 0) if corner else rgba(INK, 230)
            elif r == 1:
                top_left = (y == 1 and x < w - 2) or (x == 1 and y < h - 2)
                px[x, y] = rgba(IVORY_HI if top_left else IVORY_SH)
            elif r in (2, 3):
                px[x, y] = rgba(IVORY)
            elif r == 4:
                px[x, y] = rgba(GOLD)
            elif r == 5:
                px[x, y] = rgba(INK, 150)
            elif r == 6:
                px[x, y] = (0, 0, 0, 55)
    corner_diamonds(img)
    return img


def map_frame_glow():
    w = h = 32
    img = new(w, h)
    px = img.load()
    for y in range(h):
        for x in range(w):
            r = ring(x, y, w, h)
            if r == 0:
                px[x, y] = rgba(GLOW, 210)
            elif r == 1:
                px[x, y] = rgba(GLOW, 150)
            elif r == 2:
                px[x, y] = rgba(GLOW, 60)
    return img


def tab():
    w, h = 40, 13
    img = new(w, h)
    px = img.load()

    def left_edge(y):
        return max(0, round(6 - y * 0.5))

    for y in range(h):
        l = left_edge(y)
        r = w - 1 - l
        for x in range(l, r + 1):
            t = y / (h - 1)
            c = tuple(round(IVORY_HI[i] + (IVORY[i] - IVORY_HI[i]) * t) for i in range(3))
            px[x, y] = rgba(c)
        # contorno
        px[l, y] = rgba(INK, 230)
        px[r, y] = rgba(INK, 230)
        if l + 1 < r:
            px[l + 1, y] = rgba(IVORY_SH)
            px[r - 1, y] = rgba(IVORY_SH)
    for x in range(left_edge(0), w - left_edge(0)):
        px[x, 0] = rgba(INK, 230)
        if 1 < h:
            pass
    for x in range(left_edge(1) + 1, w - 1 - left_edge(1)):
        px[x, 1] = rgba(WHITE, 255)
    for x in range(0, w):
        px[x, h - 1] = rgba(GOLD)
    return img


def pill():
    w, h = 32, 13
    img = new(w, h)
    px = img.load()
    for y in range(h):
        for x in range(w):
            r = ring(x, y, w, h)
            corner = min(x, w - 1 - x) + min(y, h - 1 - y) < 2
            if corner:
                continue
            if r == 0:
                px[x, y] = rgba(GOLD_DK, 230)
            else:
                px[x, y] = rgba(GLASS, 215)
    return img


# ---------------------------------------------------------------- slots

def slot():
    w = h = 18
    img = new(w, h)
    px = img.load()
    for y in range(h):
        for x in range(w):
            r = ring(x, y, w, h)
            corner = (min(x, w - 1 - x) + min(y, h - 1 - y)) < 1
            if corner:
                continue
            if r == 0:
                px[x, y] = rgba(GOLD_DK, 235)
            elif r == 1 and (x == 1 or y == 1):
                px[x, y] = rgba((6, 5, 9), 215)
            elif r == 1:
                px[x, y] = rgba((58, 50, 68), 180)
            else:
                px[x, y] = rgba((13, 11, 17), 190)
    return img


def hex_inside(x, y, w, h, inset=0.0):
    cx, cy = w / 2.0, h / 2.0
    hw, hh = w / 2.0 - inset, h / 2.0 - inset
    top = hw / 2.0  # metade da aresta de cima
    dx = abs(x + 0.5 - cx)
    dy = abs(y + 0.5 - cy)
    if dy > hh:
        return False
    return dx <= hw - (hw - top) * (dy / hh)


def hex_img(fill, fill_a, outline, inner, inner_a, top_hi=None, gradient=None):
    w, h = 24, 20
    img = new(w, h)
    px = img.load()
    for y in range(h):
        for x in range(w):
            if not hex_inside(x, y, w, h):
                continue
            if not hex_inside(x, y, w, h, 1.1):
                c = outline
                if top_hi is not None and y <= 1:
                    c = top_hi
                px[x, y] = rgba(c, 245)
            elif not hex_inside(x, y, w, h, 2.1):
                px[x, y] = rgba(inner, inner_a)
            else:
                c = fill
                if gradient is not None:
                    t = y / (h - 1)
                    c = tuple(round(gradient[0][i] + (gradient[1][i] - gradient[0][i]) * t) for i in range(3))
                px[x, y] = rgba(c, fill_a)
    return img


def slot_hex():
    return hex_img((14, 12, 19), 200, GOLD, INK, 205, top_hi=GOLD_HI)


def slot_hex_selected():
    w, h = 24, 20
    img = new(w, h)
    px = img.load()
    for y in range(h):
        for x in range(w):
            if not hex_inside(x, y, w, h):
                continue
            if not hex_inside(x, y, w, h, 1.1):
                px[x, y] = rgba(GLOW, 255)
            elif not hex_inside(x, y, w, h, 2.2):
                px[x, y] = rgba(GOLD_HI, 150)
            elif not hex_inside(x, y, w, h, 3.4):
                px[x, y] = rgba(GLOW, 45)
    return img


def hex_button():
    return hex_img(GLASS, 228, IVORY, GOLD, 255, top_hi=IVORY_HI)


def hex_button_hover():
    return hex_img(IVORY, 255, INK, GOLD, 255, gradient=(IVORY_HI, IVORY))


def hex_button_on():
    return hex_img((40, 34, 46), 232, GOLD_HI, GOLD, 255, top_hi=GLOW)


def octagon_inside(x, y, size, inset=0.0):
    c = size / 2.0
    dx = abs(x + 0.5 - c)
    dy = abs(y + 0.5 - c)
    half = c - inset
    return dx <= half and dy <= half and dx + dy <= half * 1.42


def slot_result():
    s = 26
    img = new(s, s)
    px = img.load()
    for y in range(s):
        for x in range(s):
            if not octagon_inside(x, y, s):
                continue
            if not octagon_inside(x, y, s, 1.1):
                px[x, y] = rgba(GOLD_HI, 245)
            elif not octagon_inside(x, y, s, 2.1):
                px[x, y] = rgba(INK, 220)
            elif not octagon_inside(x, y, s, 3.1):
                px[x, y] = rgba(GOLD_DK, 235)
            else:
                px[x, y] = rgba((13, 11, 17), 196)
    return img


def slot_result_glow():
    s = 26
    img = new(s, s)
    px = img.load()
    for y in range(s):
        for x in range(s):
            if not octagon_inside(x, y, s):
                continue
            if not octagon_inside(x, y, s, 1.1):
                px[x, y] = rgba(GLOW, 235)
            elif not octagon_inside(x, y, s, 3.1):
                px[x, y] = rgba(GLOW, 70)
    return img


def craft_arrow():
    w, h = 12, 9
    img = new(w, h)
    px = img.load()
    shape = [
        "......#.....",
        "......##....",
        "#######.#...",
        "#.......##..",
        "#........##.",
        "#.......##..",
        "#######.#...",
        "......##....",
        "......#.....",
    ]
    # contorno em marfim, miolo em dourado
    for y, row in enumerate(shape):
        for x, ch in enumerate(row):
            if ch == "#":
                px[x, y] = rgba(IVORY)
    fill = [
        "............",
        "............",
        "............",
        ".#######....",
        ".########...",
        ".#######....",
        "............",
        "............",
        "............",
    ]
    for y, row in enumerate(fill):
        for x, ch in enumerate(row):
            if ch == "#":
                px[x, y] = rgba(GOLD)
    return img


# ---------------------------------------------------------------- texturas soltas

def glint():
    w, h = 32, 3
    img = new(w, h)
    px = img.load()
    for x in range(w):
        a = math.sin(math.pi * x / (w - 1)) ** 2
        for y in range(h):
            k = 1.0 if y == 1 else 0.35
            px[x, y] = rgba(WHITE, round(255 * a * k))
    return img


def map_arrow():
    mask = [
        "............",
        ".....##.....",
        ".....##.....",
        "....####....",
        "....####....",
        "...######...",
        "...######...",
        "..########..",
        "..###..###..",
        ".###....###.",
        ".##......##.",
        "............",
    ]
    s = 12
    img = new(s, s)
    px = img.load()
    inside = lambda x, y: 0 <= x < s and 0 <= y < s and mask[y][x] == "#"
    for y in range(s):
        for x in range(s):
            if inside(x, y):
                px[x, y] = rgba(IVORY_HI if x < 6 else IVORY_SH)
            elif any(inside(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px[x, y] = rgba(INK, 235)
    return img


def map_dot():
    s = 6
    img = new(s, s)
    px = img.load()
    for y in range(s):
        for x in range(s):
            dx = x + 0.5 - 3
            dy = y + 0.5 - 3
            r = math.sqrt(dx * dx + dy * dy)
            if r <= 1.9:
                px[x, y] = rgba((228, 76, 100))
            elif r <= 2.9:
                px[x, y] = rgba((128, 22, 44), 240)
    px[2, 2] = rgba((255, 178, 190))
    return img


def pixelize(big_img, size):
    """Reduz uma imagem grande para pixel art (sem bordas borradas)."""
    small = big_img.resize((size, size), Image.LANCZOS)
    px = small.load()
    for y in range(size):
        for x in range(size):
            r, g, b, a = px[x, y]
            px[x, y] = (r, g, b, 255) if a >= 128 else (0, 0, 0, 0)
    return small


# ---------------------------------------------------------------- icones 12x12 (brancos; o jogo pinta)

ICONS = {
    "map": [
        "............",
        "##...##.....",
        "#.###..###..",
        "#.#.#..#.##.",
        "#.#.#..#.#..",
        "#.#.#..#.#..",
        "#.#.#..#.#..",
        "#.#.#..#.#..",
        "#.#.#..#.#..",
        "##.###.##...",
        "...#..##....",
        "............",
    ],
    "camera_third": [
        "....####....",
        "...######...",
        "...######...",
        "...######...",
        "....####....",
        "............",
        "..########..",
        ".##########.",
        ".##########.",
        ".##########.",
        ".##########.",
        "............",
    ],
    "camera_first": [
        "............",
        "............",
        "...######...",
        "..##....##..",
        ".##..##..##.",
        "##..####..##",
        "##..####..##",
        ".##..##..##.",
        "..##....##..",
        "...######...",
        "............",
        "............",
    ],
    "inventory": [
        "....####....",
        "...#....#...",
        "....####....",
        "...######...",
        "..########..",
        ".####..####.",
        ".###.##.###.",
        ".####..####.",
        ".##########.",
        ".##########.",
        "..########..",
        "............",
    ],
    "center": [
        ".....##.....",
        "....####....",
        "...#.##.#...",
        "..#..##..#..",
        ".#........#.",
        "####.##.####",
        "####.##.####",
        ".#........#.",
        "..#..##..#..",
        "...#.##.#...",
        "....####....",
        ".....##.....",
    ],
    "entities": [
        "............",
        "...###......",
        "..#####.....",
        "..#####.....",
        "...###..###.",
        "........###.",
        ".###....###.",
        "#####.......",
        "#####...##..",
        ".###....##..",
        "............",
        "............",
    ],
    "claims": [
        "..##........",
        "..########..",
        "..#########.",
        "..########..",
        "..#######...",
        "..########..",
        "..##........",
        "..##........",
        "..##........",
        "..##........",
        ".####.......",
        "............",
    ],
    "grid": [
        "............",
        ".#..#..#..#.",
        "############",
        ".#..#..#..#.",
        ".#..#..#..#.",
        "############",
        ".#..#..#..#.",
        ".#..#..#..#.",
        "############",
        ".#..#..#..#.",
        "............",
        "............",
    ],
    "close": [
        "............",
        ".##......##.",
        ".###....###.",
        "..###..###..",
        "...######...",
        "....####....",
        "....####....",
        "...######...",
        "..###..###..",
        ".###....###.",
        ".##......##.",
        "............",
    ],
    "back": [
        "............",
        "....#.......",
        "...##.......",
        "..###.......",
        ".##########.",
        "###########.",
        ".##########.",
        "..###.......",
        "...##.......",
        "....#.......",
        "............",
        "............",
    ],
    "star": [
        ".....##.....",
        ".....##.....",
        "....####....",
        "############",
        ".##########.",
        "..########..",
        "...######...",
        "...######...",
        "..###..###..",
        "..##....##..",
        ".##......##.",
        "............",
    ],
    "sword": [
        ".........###",
        "........####",
        ".......####.",
        "......####..",
        ".....####...",
        "....####....",
        ".#.####.....",
        ".####.......",
        "..###.......",
        ".##.##......",
        "##..........",
        "#...........",
    ],
    "paw": [
        "...##..##...",
        "..###..###..",
        "..###..###..",
        "##..........",
        "###......###",
        "###.####.###",
        "...######...",
        "..########..",
        "..########..",
        "..###..###..",
        "............",
        "............",
    ],
    "scroll": [
        "..#########.",
        ".##.......##",
        ".##.......##",
        "..#.#####.#.",
        "..#.......#.",
        "..#.#####.#.",
        "..#.......#.",
        "..#.####..#.",
        "..#.......#.",
        "##.......##.",
        "##.......##.",
        ".#########..",
    ],
    "coin": [
        "............",
        "...######...",
        "..#......#..",
        ".#..####..#.",
        ".#.##..##.#.",
        ".#.##.....#.",
        ".#.##.....#.",
        ".#.##..##.#.",
        ".#..####..#.",
        "..#......#..",
        "...######...",
        "............",
    ],
    "house": [
        ".....##.....",
        "....####....",
        "...######...",
        "..########..",
        ".##########.",
        "############",
        "..########..",
        "..##....##..",
        "..##.##.##..",
        "..##.##.##..",
        "..########..",
        "............",
    ],
    "book": [
        "............",
        ".####..####.",
        "#....##....#",
        "#.##.##.##.#",
        "#....##....#",
        "#.##.##.##.#",
        "#....##....#",
        "#.##.##.##.#",
        "#....##....#",
        ".####..####.",
        ".....##.....",
        "............",
    ],
    "gear": [
        ".....##.....",
        "..#.####.#..",
        ".##########.",
        "..###..###..",
        ".###....###.",
        "####....####",
        "####....####",
        ".###....###.",
        "..###..###..",
        ".##########.",
        "..#.####.#..",
        ".....##.....",
    ],
    "people": [
        "..##....##..",
        ".####..####.",
        ".####..####.",
        "..##....##..",
        "............",
        ".####..####.",
        "######.#####",
        "######.#####",
        "######.#####",
        "............",
        "............",
        "............",
    ],
    "flag": [
        ".##.........",
        ".#######....",
        ".##########.",
        ".#########..",
        ".########...",
        ".#########..",
        ".##.....###.",
        ".##.........",
        ".##.........",
        ".##.........",
        "####........",
        "............",
    ],
    "compass": [
        "...######...",
        "..#......#..",
        ".#...##...#.",
        "#....##....#",
        "#...####...#",
        "#..##..##..#",
        "#..##..##..#",
        "#...####...#",
        "#....##....#",
        ".#...##...#.",
        "..#......#..",
        "...######...",
    ],
    "potion": [
        "....####....",
        ".....##.....",
        ".....##.....",
        "....####....",
        "...#....#...",
        "..#......#..",
        ".##########.",
        ".##########.",
        ".##########.",
        ".##########.",
        "..########..",
        "............",
    ],
}


def icon(rows):
    img = new(12, 12)
    px = img.load()
    for y, row in enumerate(rows):
        assert len(row) == 12, (row, len(row))
        for x, ch in enumerate(row):
            if ch == "#":
                px[x, y] = (255, 255, 255, 255)
    return img


def mod_icon():
    s = 32
    img = new(s, s)
    px = img.load()
    # hexagono escuro com moldura marfim/dourada
    for y in range(s):
        for x in range(s):
            if not hex_inside(x, y, s, s - 4, 0) if y < s - 4 else False:
                pass
    w, h = 32, 28
    off = 2
    for y in range(h):
        for x in range(w):
            if not hex_inside(x, y, w, h):
                continue
            yy = y + off
            if not hex_inside(x, y, w, h, 1.1):
                px[x, yy] = rgba(INK)
            elif not hex_inside(x, y, w, h, 3.1):
                px[x, yy] = rgba(IVORY if y < h / 2 else IVORY_SH)
            elif not hex_inside(x, y, w, h, 4.1):
                px[x, yy] = rgba(GOLD)
            else:
                px[x, yy] = rgba(GLASS)
    # janelinha de status dentro
    for y in range(10, 22):
        for x in range(9, 23):
            edge = x in (9, 22) or y in (10, 21)
            px[x, y] = rgba(IVORY if edge else (40, 34, 50))
    for x in range(11, 21):
        px[x, 13] = rgba(GOLD_HI)
    for x in range(11, 18):
        px[x, 16] = rgba(IVORY_SH)
    for x in range(11, 19):
        px[x, 18] = rgba(IVORY_SH)
    diamond(img, 16, 6)
    return img.resize((128, 128), Image.NEAREST)


def main():
    root = sys.argv[1]
    preview = sys.argv[2] if len(sys.argv) > 2 else None
    sprites = "textures/gui/sprites/"
    plain = "textures/gui/plain/"
    out = {}
    out["panel"] = (panel(), sprites + "panel.png", 7)
    out["map_frame"] = (map_frame(), sprites + "map_frame.png", 7)
    out["map_frame_glow"] = (map_frame_glow(), sprites + "map_frame_glow.png", 7)
    out["tab"] = (tab(), sprites + "tab.png", {"left": 7, "top": 3, "right": 7, "bottom": 2})
    out["pill"] = (pill(), sprites + "pill.png", 4)
    out["slot"] = (slot(), sprites + "slot.png", None)
    out["slot_hex"] = (slot_hex(), sprites + "slot_hex.png", None)
    out["slot_hex_selected"] = (slot_hex_selected(), sprites + "slot_hex_selected.png", None)
    out["slot_result"] = (slot_result(), sprites + "slot_result.png", None)
    out["slot_result_glow"] = (slot_result_glow(), sprites + "slot_result_glow.png", None)
    out["hex_button"] = (hex_button(), sprites + "hex_button.png", None)
    out["hex_button_hover"] = (hex_button_hover(), sprites + "hex_button_hover.png", None)
    out["hex_button_on"] = (hex_button_on(), sprites + "hex_button_on.png", None)
    out["craft_arrow"] = (craft_arrow(), sprites + "craft_arrow.png", None)
    for name, rows in ICONS.items():
        out["icon/" + name] = (icon(rows), sprites + "icon/" + name + ".png", None)
    out["glint"] = (glint(), plain + "glint.png", None)
    out["map_arrow"] = (map_arrow(), plain + "map_arrow.png", None)
    out["map_dot"] = (map_dot(), plain + "map_dot.png", None)
    for key, (img, rel, nine) in out.items():
        save(img, root, rel, nine)
    mod_icon().save(os.path.join(root, "icon.png"))

    if preview:
        cell = 40
        keys = list(out.keys())
        cols = 8
        rows = (len(keys) + cols - 1) // cols
        sheet = Image.new("RGBA", (cols * cell * 4, rows * cell * 4), (70, 92, 70, 255))
        for i, k in enumerate(keys):
            img = out[k][0]
            scale = 4
            big = img.resize((img.width * scale, img.height * scale), Image.NEAREST)
            x = (i % cols) * cell * 4 + 8
            y = (i // cols) * cell * 4 + 8
            sheet.alpha_composite(big, (x, y))
        sheet.save(preview)


if __name__ == "__main__":
    main()
