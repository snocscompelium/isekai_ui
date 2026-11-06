#!/usr/bin/env python3
"""Sintetiza os sons de interface (estilo "janela magica" de isekai) e exporta em OGG.

Uso: python3 tools/gen_sounds.py <pasta_assets_isekai_ui>
Precisa de numpy, scipy e ffmpeg com libvorbis. Os .ogg podem ser trocados por outros depois.
"""
import os
import subprocess
import sys
import tempfile

import numpy as np
import scipy.io.wavfile as wavfile
import scipy.signal as sig

SR = 44100
RNG = np.random.default_rng(1729)


def silence(seconds):
    return np.zeros(int(seconds * SR))


def bell(freq, dur, amp=1.0, tau=0.3, bright=1.0):
    n = int(dur * SR)
    t = np.arange(n) / SR
    partials = ((1.0, 1.0), (2.0, 0.42 * bright), (2.76, 0.22 * bright), (4.07, 0.12 * bright), (5.4, 0.06 * bright))
    out = np.zeros(n)
    for ratio, a in partials:
        out += a * np.sin(2 * np.pi * freq * ratio * t + RNG.uniform(0, 0.3)) * np.exp(-t / (tau / ratio ** 0.6))
    attack = np.minimum(1.0, t / 0.003)
    return tail_fade(amp * out * attack)


def tail_fade(x, seconds=0.03):
    """Evita estalos quando um som e cortado antes de terminar."""
    f = min(len(x), int(seconds * SR))
    if f > 1:
        x = x.copy()
        x[-f:] *= 0.5 * (1 + np.cos(np.linspace(0, np.pi, f)))
    return x


def noise(dur):
    return RNG.standard_normal(int(dur * SR))


def bandpass(x, lo, hi, order=3):
    b, a = sig.butter(order, [lo / (SR / 2), hi / (SR / 2)], btype="band")
    return sig.lfilter(b, a, x)


def lowpass(x, hz, order=2):
    b, a = sig.butter(order, hz / (SR / 2), btype="low")
    return sig.lfilter(b, a, x)


def place(buf, s, at):
    i = int(at * SR)
    end = min(len(buf), i + len(s))
    if end > i:
        buf[i:end] += s[: end - i]


def reverb(x, seconds=0.9, mix=0.28, damp=5500):
    n = int(seconds * SR)
    t = np.arange(n) / SR
    ir = RNG.standard_normal(n) * np.exp(-t / (seconds / 5.0))
    ir = lowpass(ir, damp)
    ir[0] = 0
    ir /= np.max(np.abs(ir)) + 1e-9
    dry = np.concatenate([x, np.zeros(n)])
    wet = np.zeros(len(dry))
    conv = sig.fftconvolve(x, ir)
    wet[: min(len(dry), len(conv))] = conv[: min(len(dry), len(conv))]
    wet = wet / (np.max(np.abs(wet)) + 1e-9) * (np.max(np.abs(x)) + 1e-9)
    return dry * (1 - mix) + wet * mix


def finish(x, peak_db=-5.0, fade=0.03):
    x = np.asarray(x, dtype=np.float64)
    # corta silencio no fim
    thr = np.max(np.abs(x)) * 0.002
    idx = np.where(np.abs(x) > thr)[0]
    if len(idx):
        x = x[: idx[-1] + 1]
    f = int(fade * SR)
    if f and len(x) > f:
        x[-f:] *= np.linspace(1, 0, f)
    x = x - np.mean(x)
    peak = np.max(np.abs(x)) + 1e-9
    return x / peak * (10 ** (peak_db / 20))


def whoosh(dur, lo, hi, rise=0.5):
    n = int(dur * SR)
    t = np.linspace(0, 1, n)
    env = np.sin(np.pi * np.minimum(1, t / rise) / 2) * np.exp(-np.maximum(0, t - rise) * 6)
    return tail_fade(bandpass(noise(dur), lo, hi) * env)


def window_open():
    buf = silence(0.9)
    notes = [880.0, 1318.5, 1760.0, 2217.5]
    for i, f in enumerate(notes):
        place(buf, bell(f, 0.8, amp=0.9 - i * 0.12, tau=0.35), 0.035 * i)
    place(buf, whoosh(0.25, 2500, 9000, rise=0.6) * 0.18, 0.0)
    shimmer = bell(4435.0, 0.5, amp=0.12, tau=0.18, bright=0.3)
    place(buf, shimmer, 0.14)
    return finish(reverb(buf, 1.0, 0.3), -6)


def window_close():
    buf = silence(0.6)
    for i, f in enumerate([1760.0, 1318.5, 880.0]):
        place(buf, bell(f, 0.5, amp=0.75 - i * 0.12, tau=0.18), 0.04 * i)
    place(buf, whoosh(0.18, 1500, 6000, rise=0.2) * 0.12, 0.0)
    return finish(reverb(buf, 0.7, 0.25), -8)


def hover():
    buf = silence(0.12)
    place(buf, bell(2960.0, 0.12, amp=1.0, tau=0.025, bright=0.4), 0)
    return finish(buf, -16, fade=0.01)


def click():
    buf = silence(0.25)
    place(buf, bell(1760.0, 0.25, amp=0.8, tau=0.06), 0)
    place(buf, bell(2637.0, 0.2, amp=0.5, tau=0.045), 0.012)
    place(buf, tail_fade(bandpass(noise(0.004), 3000, 9000) * 0.4, 0.002), 0)
    return finish(reverb(buf, 0.35, 0.15), -10, fade=0.02)


def toggle():
    buf = silence(0.7)
    place(buf, bell(1318.5, 0.6, amp=0.7, tau=0.08), 0)
    place(buf, bell(1975.5, 0.6, amp=0.8, tau=0.1), 0.065)
    return finish(reverb(buf, 0.4, 0.18), -10, fade=0.02)


def paper(dur, crinkles, start=0.0):
    n = int(dur * SR)
    env = np.zeros(n)
    for _ in range(crinkles):
        at = int(RNG.uniform(start, 0.85) * n)
        ln = int(RNG.uniform(0.006, 0.03) * SR)
        a = RNG.uniform(0.3, 1.0)
        seg = np.exp(-np.linspace(0, 5, ln)) * a
        end = min(n, at + ln)
        env[at:end] += seg[: end - at]
    swell = np.sin(np.pi * np.linspace(0, 1, n)) ** 0.7
    rustle = bandpass(noise(dur), 900, 7000) * (env * 0.8 + swell * 0.25)
    return tail_fade(rustle)


def map_open():
    buf = silence(1.0)
    place(buf, paper(0.42, 28) * 0.55, 0)
    thump_t = np.arange(int(0.18 * SR)) / SR
    thump = np.sin(2 * np.pi * (150 - 260 * thump_t) * thump_t) * np.exp(-thump_t / 0.05)
    place(buf, tail_fade(thump * 0.5), 0.30)
    place(buf, bell(1760.0, 0.6, amp=0.32, tau=0.25), 0.34)
    place(buf, bell(2637.0, 0.5, amp=0.18, tau=0.2), 0.38)
    return finish(reverb(buf, 0.6, 0.18), -6)


def map_close():
    buf = silence(0.6)
    place(buf, paper(0.28, 18) * 0.5, 0)
    thump_t = np.arange(int(0.14 * SR)) / SR
    thump = np.sin(2 * np.pi * (130 - 200 * thump_t) * thump_t) * np.exp(-thump_t / 0.04)
    place(buf, tail_fade(thump * 0.45), 0.2)
    return finish(reverb(buf, 0.45, 0.15), -8)


SOUNDS = {
    "window_open": window_open,
    "window_close": window_close,
    "hover": hover,
    "click": click,
    "toggle": toggle,
    "map_open": map_open,
    "map_close": map_close,
}


def main():
    root = sys.argv[1]
    out_dir = os.path.join(root, "sounds", "ui")
    os.makedirs(out_dir, exist_ok=True)
    sounds_json = {}
    for name, fn in SOUNDS.items():
        data = fn()
        pcm = np.clip(data * 32767, -32768, 32767).astype(np.int16)
        with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
            wavfile.write(tmp.name, SR, pcm)
            ogg = os.path.join(out_dir, name + ".ogg")
            subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", tmp.name, "-ac", "1",
                            "-c:a", "libvorbis", "-q:a", "6", ogg], check=True)
            os.unlink(tmp.name)
        sounds_json["ui." + name] = {"sounds": ["isekai_ui:ui/" + name]}
    import json
    with open(os.path.join(root, "sounds.json"), "w") as f:
        json.dump(sounds_json, f, indent=2)


if __name__ == "__main__":
    main()
