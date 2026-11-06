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


def dial():
    """Medalhao redondo do sol/relogio: aro dourado e fundo de vidro escuro."""
    s = 22
    img = new(s, s)
    px = img.load()
    c = s / 2.0
    for y in range(s):
        for x in range(s):
            dx = x + 0.5 - c
            dy = y + 0.5 - c
            r = math.sqrt(dx * dx + dy * dy)
            if r > 10.9:
                continue
            if r > 9.9:
                px[x, y] = rgba(INK)
            elif r > 8.6:
                px[x, y] = rgba(GOLD_HI if dy < -2 else (GOLD if dy < 4 else GOLD_DK))
            elif r > 7.8:
                px[x, y] = rgba(INK, 220)
            else:
                k = (dy + 8) / 16.0
                col = tuple(int(GLASS_HI[i] * (1 - k) + GLASS[i] * k) for i in range(3))
                px[x, y] = rgba(col, 235)
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
    "sun": [
        ".....##.....",
        ".#...##...#.",
        "..#......#..",
        "....####....",
        "...######...",
        "##.######.##",
        "##.######.##",
        "...######...",
        "....####....",
        "..#......#..",
        ".#...##...#.",
        ".....##.....",
    ],
    "moon": [
        "...#####....",
        "..####......",
        ".####.......",
        ".###........",
        "####........",
        "####........",
        "####........",
        "####........",
        ".###........",
        ".####.......",
        "..####......",
        "...#####....",
    ],
    "brush": [
        "..........##",
        ".........###",
        "........###.",
        ".......###..",
        "......###...",
        ".....##.....",
        "...####.....",
        "..#####.....",
        ".######.....",
        ".#####......",
        "####........",
        "##..........",
    ],
    "armor": [
        "..###..###..",
        ".####..####.",
        "############",
        "############",
        ".##########.",
        "..########..",
        "..########..",
        "..########..",
        "..########..",
        "..###..###..",
        "..##....##..",
        "............",
    ],
    "list": [
        "............",
        "............",
        ".##.#######.",
        ".##.#######.",
        "............",
        ".##.#######.",
        ".##.#######.",
        "............",
        ".##.#######.",
        ".##.#######.",
        "............",
        "............",
    ],
    "pin": [
        "....####....",
        "...######...",
        "..########..",
        "..###..###..",
        "..###..###..",
        "..########..",
        "...######...",
        "...######...",
        "....####....",
        "....####....",
        ".....##.....",
        ".....##.....",
    ],
    "gem": [
        "............",
        "..########..",
        ".##.####.##.",
        "############",
        ".##########.",
        "..########..",
        "...######...",
        "....####....",
        ".....##.....",
        "............",
        "............",
        "............",
    ],
    "skull": [
        "............",
        "...######...",
        "..########..",
        ".##########.",
        ".#...##...#.",
        ".#...##...#.",
        ".####..####.",
        "..###..###..",
        "...######...",
        "...#.##.#...",
        "...######...",
        "............",
    ],
    "tree": [
        ".....##.....",
        "....####....",
        "...######...",
        "..########..",
        "...######...",
        "..########..",
        ".##########.",
        "############",
        ".....##.....",
        ".....##.....",
        "....####....",
        "............",
    ],
    "pickaxe": [
        "..########..",
        ".##########.",
        "##...##...##",
        "#....##....#",
        ".....##.....",
        ".....##.....",
        ".....##.....",
        ".....##.....",
        ".....##.....",
        ".....##.....",
        ".....##.....",
        "............",
    ],
    "anchor": [
        ".....##.....",
        "....#..#....",
        ".....##.....",
        "..########..",
        ".....##.....",
        ".....##.....",
        "#....##....#",
        "##...##...##",
        ".##..##..##.",
        "..########..",
        "....####....",
        "............",
    ],
    "portal": [
        "...######...",
        "..##....##..",
        ".##..##..##.",
        ".#..#..#..#.",
        ".#.#....#.#.",
        ".#.#....#.#.",
        ".#.#....#.#.",
        ".#..#..#..#.",
        ".##..##..##.",
        "..##....##..",
        "...######...",
        "............",
    ],
    "tower": [
        ".#.#.##.#.#.",
        ".##########.",
        "..########..",
        "..##....##..",
        "..########..",
        "..###..###..",
        "..########..",
        "..########..",
        "..###..###..",
        "..##....##..",
        ".##########.",
        "............",
    ],
    "craft": [
        "............",
        ".####..####.",
        ".#..#..#..#.",
        ".#..#..#..#.",
        ".####..####.",
        "............",
        "............",
        ".####..####.",
        ".#..#..#..#.",
        ".#..#..#..#.",
        ".####..####.",
        "............",
    ],
    "chest": [
        "............",
        "............",
        ".##########.",
        ".#........#.",
        ".#........#.",
        ".####..####.",
        ".#...##...#.",
        ".#........#.",
        ".#........#.",
        ".##########.",
        "............",
        "............",
    ],
    "flame": [
        ".....#......",
        ".....##.....",
        "....###.....",
        "....####.#..",
        "...######...",
        "..#######...",
        "..###.####..",
        ".###...###..",
        ".###...####.",
        ".####.####..",
        "..########..",
        "...######...",
    ],
    "trash": [
        "....####....",
        ".##########.",
        "............",
        "..########..",
        "..#.#..#.#..",
        "..#.#..#.#..",
        "..#.#..#.#..",
        "..#.#..#.#..",
        "..#.#..#.#..",
        "..#.#..#.#..",
        "..########..",
        "............",
    ],
    "search": [
        "...####.....",
        "..#....#....",
        ".#......#...",
        ".#......#...",
        ".#......#...",
        ".#......#...",
        "..#....#....",
        "...#####....",
        ".......###..",
        "........###.",
        ".........##.",
        "............",
    ],
    "plus": [
        "............",
        ".....##.....",
        ".....##.....",
        ".....##.....",
        ".....##.....",
        ".##########.",
        ".##########.",
        ".....##.....",
        ".....##.....",
        ".....##.....",
        ".....##.....",
        "............",
    ],
    "catalog": [
        "............",
        "............",
        ".##..##..##.",
        ".##..##..##.",
        "............",
        ".##..##..##.",
        ".##..##..##.",
        "............",
        ".##..##..##.",
        ".##..##..##.",
        "............",
        "............",
    ],
    "chat": [
        "............",
        ".##########.",
        ".#........#.",
        ".#.######.#.",
        ".#........#.",
        ".#.####...#.",
        ".#........#.",
        ".####.#####.",
        "....#.#.....",
        "....##......",
        "....#.......",
        "............",
    ],
    "next": [
        "............",
        "....#.......",
        "....##......",
        "....###.....",
        "....####....",
        "....#####...",
        "....####....",
        "....###.....",
        "....##......",
        "....#.......",
        "............",
        "............",
    ],
    "prev": [
        "............",
        ".......#....",
        "......##....",
        ".....###....",
        "....####....",
        "...#####....",
        "....####....",
        ".....###....",
        "......##....",
        ".......#....",
        "............",
        "............",
    ],
    "potion_rack": [
        "............",
        "....####....",
        ".....##.....",
        ".....##.....",
        "....####....",
        "...#....#...",
        "..#......#..",
        "..#.####.#..",
        "..#.####.#..",
        "...######...",
        "............",
        "............",
    ],
}


# ---------------------------------------------------------------- brasoes 16x16 (simbolos de reino de exemplo)

EMBLEMS = {
    # nome: (cor do campo, cor do desenho, desenho 10x9)
    "crown": ((62, 98, 168), (232, 192, 96), [
        "..........",
        "#...##...#",
        "##..##..##",
        "###.##.###",
        "##########",
        "##########",
        "#.##..##.#",
        "##########",
        "..........",
    ]),
    "lily": ((125, 82, 168), (232, 192, 96), [
        "....##....",
        "...####...",
        ".#.####.#.",
        "##..##..##",
        "##.####.##",
        ".########.",
        "...####...",
        "..######..",
        "....##....",
    ]),
    "eagle": ((184, 66, 60), (232, 192, 96), [
        "....##....",
        "#..####..#",
        "##.####.##",
        "##########",
        ".########.",
        "..######..",
        "...####...",
        "..##..##..",
        "..........",
    ]),
    "tower": ((93, 107, 126), (236, 226, 198), [
        "##.####.##",
        "##########",
        ".########.",
        ".###..###.",
        ".###..###.",
        ".########.",
        ".###..###.",
        ".###..###.",
        "##########",
    ]),
    "swords": ((44, 40, 50), (222, 222, 226), [
        "##......##",
        "###....###",
        ".###..###.",
        "..######..",
        "...####...",
        "..######..",
        ".###..###.",
        "###....###",
        "##......##",
    ]),
    "sun": ((76, 122, 158), (240, 200, 90), [
        "....##....",
        ".#......#.",
        "...####...",
        "..######..",
        "#.######.#",
        "..######..",
        "...####...",
        ".#......#.",
        "....##....",
    ]),
    "moon": ((44, 58, 102), (236, 226, 198), [
        "....###...",
        "..####....",
        ".####.....",
        ".###......",
        ".###......",
        ".####.....",
        "..####....",
        "....###...",
        "..........",
    ]),
    "flame": ((142, 46, 42), (240, 176, 72), [
        "....#.....",
        "....##....",
        "...###.#..",
        "..#####...",
        "..######..",
        ".###.####.",
        ".##...###.",
        ".###.###..",
        "..######..",
    ]),
    "cross": ((233, 223, 194), (184, 56, 52), [
        "...####...",
        "...####...",
        "##########",
        "##########",
        "...####...",
        "...####...",
        "...####...",
        "...####...",
        "..........",
    ]),
    "chevron": ((184, 106, 46), (236, 226, 198), [
        "..........",
        "....##....",
        "...####...",
        "..##..##..",
        ".##....##.",
        "##..##..##",
        "#..####..#",
        "..##..##..",
        ".##....##.",
    ]),
    "tree": ((47, 110, 74), (232, 192, 96), [
        "....##....",
        "...####...",
        "..######..",
        "...####...",
        "..######..",
        ".########.",
        "....##....",
        "....##....",
        "...####...",
    ]),
    "anchor": ((46, 140, 140), (236, 226, 198), [
        "....##....",
        "...#..#...",
        "....##....",
        ".########.",
        "....##....",
        "#...##...#",
        "##..##..##",
        ".##.##.##.",
        "..######..",
    ]),
}


def shield_inside(x, y, inset=0.0):
    """Escudo 16x16: topo reto, laterais retas e ponta arredondada embaixo."""
    if y < 1 + inset or y > 15 - inset:
        return False
    cx = 8.0
    px = x + 0.5
    py = y + 0.5
    if py <= 9.0:
        hw = 7.0 - inset
    else:
        t = min(1.0, (py - 9.0) / 6.5)
        hw = (7.0 - inset) * math.sqrt(max(0.0, 1.0 - t * t)) - inset * 0.3
    return abs(px - cx) <= hw


def emblem(field, charge, rows):
    img = new(16, 16)
    px = img.load()
    for y in range(16):
        for x in range(16):
            if not shield_inside(x, y):
                continue
            if not shield_inside(x, y, 1.0):
                px[x, y] = rgba(INK)
            elif not shield_inside(x, y, 2.0):
                px[x, y] = rgba(GOLD_HI if y < 7 else GOLD)
            else:
                k = 1.12 if (x + y) < 12 else (0.88 if (x + y) > 20 else 1.0)
                px[x, y] = rgba(tuple(max(0, min(255, int(c * k))) for c in field))
    # sombra do desenho (1 px para baixo/direita) e o desenho
    ox, oy = 3, 3
    for dy, row in enumerate(rows):
        assert len(row) == 10, (row, len(row))
        for dx, ch in enumerate(row):
            if ch != "#":
                continue
            sx, sy = ox + dx + 1, oy + dy + 1
            if shield_inside(sx, sy, 2.0):
                r0, g0, b0, a0 = px[sx, sy]
                px[sx, sy] = (r0 * 6 // 10, g0 * 6 // 10, b0 * 6 // 10, a0)
    for dy, row in enumerate(rows):
        for dx, ch in enumerate(row):
            if ch != "#":
                continue
            x, y = ox + dx, oy + dy
            if not shield_inside(x, y, 2.0):
                continue
            k = 1.1 if dy < 3 else (0.9 if dy > 6 else 1.0)
            px[x, y] = rgba(tuple(max(0, min(255, int(c * k))) for c in charge))
    return img


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


# ---------------------------------------------------------------- mochila (previa de shulker/sacola)
# Couro claro: o jogo tinge com a cor da shulker/sacola (multiplicacao), por isso a base e quase branca.

LEATHER_HI = (255, 251, 242)
LEATHER = (238, 229, 214)
LEATHER_SH = (206, 193, 174)
LEATHER_OUT = (72, 60, 50)
THREAD = (255, 252, 246)


def leather_px(x, y):
    """Granulado do couro, repetivel a cada 6 px (combina com o fatiamento em 9 partes)."""
    n = ((x * 7 + y * 13) % 6 == 0) or ((x * 3 + y * 5) % 11 == 0)
    return LEATHER_SH if n else LEATHER


def backpack_body():
    w = h = 32
    img = new(w, h)
    px = img.load()
    for y in range(h):
        for x in range(w):
            cx = min(x, w - 1 - x)
            cy = min(y, h - 1 - y)
            if cx + cy < 2:  # cantos arredondados
                continue
            if cx == 0 or cy == 0 or (cx + cy == 2):
                px[x, y] = rgba(LEATHER_OUT)
                continue
            c = leather_px(x, y)
            if cy == 1 and y < h // 2:
                c = LEATHER_HI
            if cy == 1 and y > h // 2:
                c = LEATHER_SH
            # costura tracejada a 3 px da borda (periodo 3 = divide a parte repetida de 18 px)
            ring3 = min(cx, cy) == 3
            if ring3 and ((x + y) % 3 != 0):
                c = THREAD
            px[x, y] = rgba(c)
    return img


def backpack_flap():
    """Tampa da mochila: topo bem arredondado (raio 6), costura e sombra na borda de baixo."""
    w, h = 48, 18
    img = new(w, h)
    px = img.load()
    rad = 6.0
    for y in range(h):
        for x in range(w):
            cx = min(x, w - 1 - x)
            out_line = False
            if y < rad and cx < rad:
                dx, dy = rad - 0.5 - cx, rad - 0.5 - y
                d = math.sqrt(dx * dx + dy * dy)
                if d > rad - 0.1:
                    continue
                out_line = d > rad - 1.1
            if out_line or cx == 0 or y == 0 or y == h - 1:
                px[x, y] = rgba(LEATHER_OUT)
                continue
            c = tuple(int(v * 0.94) for v in leather_px(x, y))
            if y == 1 or (y < rad and cx < rad and y <= 2):
                c = LEATHER_HI
            if y == h - 2:
                c = tuple(int(v * 0.78) for v in LEATHER_SH)
            stitch = (y == h - 4 and 3 <= cx) or (cx == 3 and 4 <= y <= h - 4)
            if stitch and ((x + y) % 3 != 0):
                c = THREAD
            px[x, y] = rgba(c)
    return img


def backpack_strap():
    """Correia vertical (repetivel) com furos."""
    w, h = 6, 12
    img = new(w, h)
    px = img.load()
    for y in range(h):
        for x in range(w):
            if x == 0 or x == w - 1:
                px[x, y] = rgba(LEATHER_OUT)
            else:
                c = tuple(int(v * 0.82) for v in LEATHER)
                if x == 1:
                    c = tuple(int(v * 0.9) for v in LEATHER_HI)
                if y % 6 == 3 and 2 <= x <= 3:
                    c = LEATHER_OUT
                px[x, y] = rgba(c)
    return img


def backpack_side():
    """Bolso lateral (aparece meio escondido atras do corpo)."""
    w, h = 8, 22
    img = new(w, h)
    px = img.load()
    for y in range(h):
        for x in range(w):
            cx = min(x, w - 1 - x)
            cy = min(y, h - 1 - y)
            if cx + cy < 2:
                continue
            if cx == 0 or cy == 0 or cx + cy == 2:
                px[x, y] = rgba(LEATHER_OUT)
                continue
            c = tuple(int(v * 0.86) for v in leather_px(x, y))
            if y == 4:
                c = tuple(int(v * 0.7) for v in LEATHER_SH)
            px[x, y] = rgba(c)
    return img


def backpack_handle():
    w, h = 16, 6
    img = new(w, h)
    px = img.load()
    for y in range(h):
        for x in range(w):
            dx = (x + 0.5 - w / 2.0) / (w / 2.0)
            dy = (y + 0.5 - h) / h
            d = math.sqrt(dx * dx + dy * dy)
            if 0.62 <= d <= 1.0:
                px[x, y] = rgba(LEATHER_OUT if (d > 0.9 or d < 0.7) else LEATHER)
    return img


def backpack_buckle():
    rows = [
        "..######..",
        ".#oooooo#.",
        "#o#####o#.",
        "#o#..#.#o#",
        "#o#..#.#o#",
        "#o#####o#.",
        ".#oooooo#.",
        "..######..",
    ]
    img = new(10, 8)
    px = img.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == "#":
                px[x, y] = rgba(INK)
            elif ch == "o":
                px[x, y] = rgba(GOLD_HI if y < 4 else GOLD)
    return img


def backpack_pocket():
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
                px[x, y] = rgba((40, 28, 20), 120)
            elif x == 1 or y == 1:
                px[x, y] = rgba((20, 13, 9), 150)
            elif x == w - 2 or y == h - 2:
                px[x, y] = rgba((255, 240, 220), 60)
            else:
                px[x, y] = rgba((30, 20, 14), 105)
    return img


def progress_arrow(full):
    """Seta grande de progresso (22x15): vazia (escura) ou cheia (marfim + dourado)."""
    w, h = 22, 15
    img = new(w, h)
    px = img.load()

    def inside(x, y):
        if x < 0 or y < 0 or x >= w or y >= h:
            return False
        if x <= 13 and 5 <= y <= 9:
            return True
        if x >= 12:
            return abs(y - 7) <= (21 - x) * 7.0 / 9.0 + 0.35
        return False

    for y in range(h):
        for x in range(w):
            if not inside(x, y):
                continue
            edge = not (inside(x - 1, y) and inside(x + 1, y) and inside(x, y - 1) and inside(x, y + 1))
            if full:
                px[x, y] = rgba(IVORY_HI if edge else (GOLD_HI if y < 7 else GOLD))
            else:
                px[x, y] = rgba((96, 88, 108) if edge else (34, 30, 42), 235)
    return img


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
    out["dial"] = (dial(), sprites + "dial.png", None)
    out["progress_arrow"] = (progress_arrow(False), sprites + "progress_arrow.png", None)
    out["progress_arrow_full"] = (progress_arrow(True), sprites + "progress_arrow_full.png", None)
    for name, rows in ICONS.items():
        out["icon/" + name] = (icon(rows), sprites + "icon/" + name + ".png", None)
    for name, (field, charge, rows) in EMBLEMS.items():
        out["emblem/" + name] = (emblem(field, charge, rows), plain + "emblem/" + name + ".png", None)
    out["backpack_body"] = (backpack_body(), sprites + "backpack_body.png", 7)
    out["backpack_flap"] = (backpack_flap(), sprites + "backpack_flap.png", {"left": 8, "top": 7, "right": 8, "bottom": 6})
    out["backpack_strap"] = (backpack_strap(), sprites + "backpack_strap.png", None)
    out["backpack_side"] = (backpack_side(), sprites + "backpack_side.png", {"left": 3, "top": 6, "right": 3, "bottom": 6})
    out["backpack_handle"] = (backpack_handle(), sprites + "backpack_handle.png", None)
    out["backpack_buckle"] = (backpack_buckle(), sprites + "backpack_buckle.png", None)
    out["backpack_pocket"] = (backpack_pocket(), sprites + "backpack_pocket.png", None)
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
