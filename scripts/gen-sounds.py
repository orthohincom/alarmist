#!/usr/bin/env python3
"""Generates core:sounds' bundled alarm sounds (own work, GPL-3.0-or-later like the rest of the repo).

Every sound is synthesised here from sines and noise, so there is nothing to attribute. Each one is made to loop
without a click: notes that run past the end wrap around to the start. Needs ffmpeg with libvorbis.
Usage: scripts/gen-sounds.py core/sounds/src/main/res/raw [id ...]
"""
import math
import os
import random
import struct
import subprocess
import sys
import tempfile
import wave

RATE = 22050
TAU = 2 * math.pi


def buf(seconds):
    return [0.0] * int(seconds * RATE)


def mix(out, samples, start, wrap=True):
    """Adds samples at start (seconds). With wrap, whatever runs past the end continues at the beginning."""
    n, i0 = len(out), int(start * RATE)
    for i, s in enumerate(samples):
        j = i0 + i
        if j >= n:
            if not wrap:
                break
            j %= n
        out[j] += s


def env_ar(i, n, attack, release):
    t, d = i / RATE, n / RATE
    return min(1.0, t / attack if attack else 1.0, (d - t) / release if release else 1.0)


def tone(freq, seconds, partials=((1, 1.0),), attack=0.006, release=0.006):
    """A steady tone with short fades so it doesn't click."""
    n = int(seconds * RATE)
    total = sum(a for _, a in partials)
    return [
        env_ar(i, n, attack, release) * sum(a * math.sin(TAU * freq * m * i / RATE) for m, a in partials) / total
        for i in range(n)
    ]


def struck(freq, seconds, partials, decay, attack=0.002):
    """A struck tone (bell, bar): each partial decays exponentially, higher ones faster."""
    n = int(seconds * RATE)
    total = sum(a for _, a, _ in partials)
    out = []
    for i in range(n):
        t = i / RATE
        s = sum(a * math.exp(-t / (decay * d)) * math.sin(TAU * freq * m * t) for m, a, d in partials)
        out.append(min(1.0, t / attack) * s / total)
    return out


def square(freq, seconds, harmonics=7):
    """Band-limited square: odd harmonics at 1/k."""
    return tone(freq, seconds, tuple((k, 1 / k) for k in range(1, 2 * harmonics, 2)))


def note(name):
    """'A4' -> Hz. Sharps only ('C#5')."""
    names = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"]
    pitch, octave = name[:-1], int(name[-1])
    return 440.0 * 2 ** ((names.index(pitch) + 12 * (octave + 1) - 69) / 12)


# ---- Classic --------------------------------------------------------------------------------------------------

def default():
    # Four short beeps and a pause, the fallback sound core:ringing has always had.
    out = []
    for k in range(4):
        out += tone(1046.5, 0.12, ((1, 1.0), (2, 0.3)))
        out += [0.0] * int((0.08 if k < 3 else 0.5) * RATE)
    return out


def digital():
    # A digital watch: beep-beep, beep-beep, pause.
    out = buf(1.4)
    for group in (0.0, 0.4):
        for k in range(2):
            mix(out, square(2093.0, 0.07, harmonics=3), group + k * 0.12)
    return out


def alarm_clock():
    # A mechanical bell: a hammer striking about 18 times a second, then a pause.
    out = buf(2.6)
    partials = ((1, 1.0, 1.0), (2.76, 0.6, 0.5), (5.4, 0.35, 0.3), (8.93, 0.2, 0.2))
    strike = struck(1250.0, 0.4, partials, decay=0.12)
    for k in range(int(1.9 * 18)):
        mix(out, [0.5 * s for s in strike], k / 18)
    return out


def telephone():
    # An old telephone bell: ring-ring, pause. Two bells struck alternately at 20 Hz.
    out = buf(3.0)
    partials = ((1, 1.0, 1.0), (2.4, 0.5, 0.6), (4.1, 0.25, 0.4))
    bells = [struck(f, 0.25, partials, decay=0.08) for f in (900.0, 1060.0)]
    for burst in (0.0, 0.6):
        for k in range(8):
            mix(out, [0.5 * s for s in bells[k % 2]], burst + k * 0.05)
    return out


# ---- Gentle ---------------------------------------------------------------------------------------------------

def chimes():
    # Wind chimes: random notes of a pentatonic scale, long tails.
    rnd = random.Random(7)
    out = buf(8.0)
    scale = [note(n) for n in ("C5", "D5", "E5", "G5", "A5", "C6", "D6", "E6")]
    partials = ((1, 1.0, 1.0), (2.76, 0.4, 0.4), (5.4, 0.15, 0.25))
    t = 0.0
    while t < 7.8:
        mix(out, [rnd.uniform(0.3, 0.6) * s for s in struck(rnd.choice(scale), 3.0, partials, decay=0.8)], t)
        t += rnd.uniform(0.25, 0.7)
    return out


def melody(notes, beat, freq_fn, seconds):
    out = buf(seconds)
    t = 0.0
    for n, beats in notes:
        if n:
            mix(out, freq_fn(note(n)), t)
        t += beats * beat
    return out


def music_box():
    tune = [("E6", 1), ("G6", 1), ("C7", 2), ("B6", 1), ("G6", 1), ("A6", 2), ("F6", 1), ("A6", 1), ("D7", 2),
            ("C7", 1), ("B6", 1), ("G6", 2), ("E6", 1), ("G6", 1), ("C7", 1), ("E7", 1), ("D7", 2), ("C7", 2),
            (None, 2)]
    partials = ((1, 1.0, 1.0), (3.0, 0.3, 0.3), (5.97, 0.12, 0.15))
    return melody(tune, 0.22, lambda f: [0.6 * s for s in struck(f, 1.8, partials, decay=0.45)], 24 * 0.22)


def marimba():
    tune = [("C5", 1), ("E5", 1), ("G5", 1), ("C6", 1), ("G5", 1), ("E5", 1), ("A4", 1), ("C5", 1), ("E5", 1),
            ("A5", 1), ("E5", 1), ("C5", 1), ("F4", 1), ("A4", 1), ("C5", 1), ("F5", 1), ("G4", 1), ("B4", 1),
            ("D5", 1), ("G5", 1), (None, 4)]
    partials = ((1, 1.0, 1.0), (4.0, 0.35, 0.15), (9.2, 0.08, 0.06))
    return melody(tune, 0.18, lambda f: [0.7 * s for s in struck(f, 1.0, partials, decay=0.22)], 24 * 0.18)


def sunrise():
    # Soft swells of a major chord that rise in pitch, for gentle waking.
    out = buf(9.0)
    for k, root in enumerate(("C4", "F4", "G4")):
        f0 = note(root)
        n = int(3.6 * RATE)
        swell = []
        for i in range(n):
            t = i / RATE
            env = math.sin(math.pi * i / n) ** 2
            s = sum(math.sin(TAU * f0 * r * t) + 0.3 * math.sin(TAU * f0 * r * 2.003 * t) for r in (1, 1.26, 1.5, 2))
            swell.append(0.15 * env * s)
        mix(out, swell, k * 3.0)
    return out


# ---- Loud -----------------------------------------------------------------------------------------------------

def sweep(freq_fn, seconds, partials):
    """A tone whose frequency follows freq_fn(t), adjusted so the phase meets itself at the loop point."""
    n = int(seconds * RATE)
    total_cycles = sum(freq_fn(i / RATE) for i in range(n)) / RATE
    k = round(total_cycles) / total_cycles
    out, phase = [], 0.0
    total = sum(a for _, a in partials)
    for i in range(n):
        phase += TAU * k * freq_fn(i / RATE) / RATE
        out.append(sum(a * math.sin(m * phase) for m, a in partials) / total)
    return out


def siren():
    return [0.9 * s for s in sweep(lambda t: 950 - 400 * math.cos(TAU * t / 2.0), 4.0, ((1, 1.0), (2, 0.25), (3, 0.1)))]


def buzzer():
    out = buf(1.6)
    for k in range(3):
        mix(out, square(440.0, 0.26, harmonics=12), k * 0.4)
    return out


def klaxon():
    # Rising sawtooth whoops, like a ship's alarm.
    out = buf(1.6)
    saw = ((1, 1.0), (2, 0.5), (3, 0.33), (4, 0.25), (5, 0.2), (6, 0.17))
    whoop = sweep(lambda t: 300 + 700 * t, 0.6, saw)
    n = len(whoop)
    for start in (0.0, 0.8):
        mix(out, [s * env_ar(i, n, 0.01, 0.03) for i, s in enumerate(whoop)], start)
    return out


def pulse():
    # Fast, urgent beeps.
    out = buf(1.3)
    for k in range(10):
        mix(out, tone(2637.0, 0.05, ((1, 1.0), (2, 0.2)), attack=0.003, release=0.003), k * 0.1)
    return out


# ---- Nature ---------------------------------------------------------------------------------------------------

def birds():
    rnd = random.Random(11)
    out = buf(10.0)
    t = 0.0
    while t < 9.5:
        base = rnd.uniform(2800, 4200)
        for _ in range(rnd.randint(2, 6)):  # A phrase of quick chirps.
            d = rnd.uniform(0.04, 0.12)
            drop = rnd.uniform(0.3, 0.7)
            amp = rnd.uniform(0.3, 0.6)
            chirp = sweep(lambda x, d=d, drop=drop, base=base: base * (1 + drop * (1 - x / d)), d, ((1, 1.0), (2, 0.1)))
            n = len(chirp)
            mix(out, [amp * s * math.sin(math.pi * i / n) for i, s in enumerate(chirp)], t)
            t += d + rnd.uniform(0.02, 0.08)
        t += rnd.uniform(0.4, 1.4)
    return out


def brook():
    # Low, filtered noise and little bubbles.
    rnd = random.Random(5)
    n = int(10.0 * RATE)
    noise = [rnd.uniform(-1, 1) for _ in range(n)]
    out, y = [0.0] * n, 0.0
    for lap in range(2):  # Two laps so the filter state is continuous at the loop point.
        for i in range(n):
            y += 0.08 * (noise[i] - y)
            if lap:
                out[i] = 0.9 * y
    t = 0.0
    while t < 10.0:
        f, d = rnd.uniform(400, 1200), rnd.uniform(0.03, 0.08)
        m = int(d * RATE)
        bubble = [rnd.uniform(0.15, 0.35) * math.exp(-i / (0.3 * m)) * math.sin(TAU * f * (1 + 2.0 * i / m) * i / RATE)
                  for i in range(m)]
        mix(out, bubble, t)
        t += rnd.expovariate(14)
    return out


SOUNDS = {f.__name__: f for f in (default, digital, alarm_clock, telephone, chimes, music_box, marimba, sunrise,
                                  siren, buzzer, klaxon, pulse, birds, brook)}


def write(samples, path):
    peak = max(abs(s) for s in samples) or 1.0
    scale = 0.89 / peak
    with tempfile.NamedTemporaryFile(suffix=".wav") as tmp:
        with wave.open(tmp.name, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(RATE)
            w.writeframes(b"".join(struct.pack("<h", int(s * scale * 32767)) for s in samples))
        subprocess.run(
            ["ffmpeg", "-y", "-loglevel", "error", "-i", tmp.name, "-c:a", "libvorbis", "-q:a", "4",
             "-map_metadata", "-1", "-fflags", "+bitexact", path],
            check=True,
        )


if __name__ == "__main__":
    out_dir, ids = sys.argv[1], sys.argv[2:] or list(SOUNDS)
    for sound_id in ids:
        write(SOUNDS[sound_id](), os.path.join(out_dir, f"sound_{sound_id}.ogg"))
        print(sound_id)
