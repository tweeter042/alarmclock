#!/usr/bin/env python3
"""Generates every bundled alarm/timer tone procedurally (no third-party audio, no licences needed).

Output: core/alarm/src/main/res/raw/tone_<id>.ogg (Vorbis, mono, 44.1 kHz).
Usage:  python3 -I tools/sounds/generate_tones.py   (needs numpy and ffmpeg with libvorbis)

Each tone is designed to loop seamlessly (it ends after its tail has decayed) and is peak-normalised.
Deterministic: re-running produces identical audio.
"""
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np

SR = 44100
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "core", "alarm", "src", "main", "res", "raw")
rng = np.random.default_rng(20261127)


# ----------------------------------------------------------------------------- helpers
def t_axis(dur):
    return np.arange(int(dur * SR)) / SR


def note(name):
    """'A4' -> Hz. Supports sharps (C#5)."""
    names = {"C": -9, "D": -7, "E": -5, "F": -4, "G": -2, "A": 0, "B": 2}
    base = names[name[0]]
    rest = name[1:]
    if rest.startswith("#"):
        base += 1
        rest = rest[1:]
    elif rest.startswith("b"):
        base -= 1
        rest = rest[1:]
    octave = int(rest)
    return 440.0 * 2 ** ((base + (octave - 4) * 12) / 12)


def adsr(n, a=0.005, d=0.1, s=0.7, r=0.1):
    a_n, d_n, r_n = int(a * SR), int(d * SR), int(r * SR)
    s_n = max(0, n - a_n - d_n - r_n)
    env = np.concatenate([
        np.linspace(0, 1, a_n, endpoint=False),
        np.linspace(1, s, d_n, endpoint=False),
        np.full(s_n, s),
        np.linspace(s, 0, r_n),
    ])
    return np.pad(env, (0, max(0, n - len(env))))[:n]


def exp_decay(t, rate):
    return np.exp(-t * rate)


def fade_edges(x, ms=4):
    n = int(SR * ms / 1000)
    if len(x) > 2 * n:
        x[:n] *= np.linspace(0, 1, n)
        x[-n:] *= np.linspace(1, 0, n)
    return x


def bell(freq, dur=2.5, ratio=1.4, index=3.0, decay=2.2):
    t = t_axis(dur)
    mod = index * exp_decay(t, decay * 1.5) * np.sin(2 * np.pi * freq * ratio * t)
    return fade_edges(np.sin(2 * np.pi * freq * t + mod) * exp_decay(t, decay))


def marimba(freq, dur=1.2):
    t = t_axis(dur)
    x = np.sin(2 * np.pi * freq * t) * exp_decay(t, 5.5)
    x += 0.35 * np.sin(2 * np.pi * freq * 3.9 * t) * exp_decay(t, 22)
    x += 0.12 * np.sin(2 * np.pi * freq * 9.2 * t) * exp_decay(t, 45)
    return fade_edges(x * np.minimum(1, t / 0.002))


def kalimba(freq, dur=1.6):
    t = t_axis(dur)
    x = np.sin(2 * np.pi * freq * t) * exp_decay(t, 3.2)
    x += 0.25 * np.sin(2 * np.pi * freq * 5.95 * t) * exp_decay(t, 18)
    x += 0.08 * rng.standard_normal(len(t)) * exp_decay(t, 300)  # tine click
    return fade_edges(x * np.minimum(1, t / 0.001))


def epiano(freq, dur=1.8):
    t = t_axis(dur)
    x = np.zeros_like(t)
    for h, amp in ((1, 1.0), (2, 0.35), (3, 0.12), (4, 0.06)):
        x += amp * np.sin(2 * np.pi * freq * h * t) * exp_decay(t, 2.2 + h * 0.9)
    x += 0.2 * np.sin(2 * np.pi * freq * 14 * t) * exp_decay(t, 60)  # tine
    return fade_edges(x * np.minimum(1, t / 0.003))


def additive(freq, dur, harmonics, env):
    t = t_axis(dur)
    x = np.zeros_like(t)
    for h in range(1, 60):
        if freq * h > SR / 2.2:
            break
        amp = harmonics(h)
        if amp:
            x += amp * np.sin(2 * np.pi * freq * h * t)
    return fade_edges(x * env(len(t)))


def square(freq, dur, env=None):
    env = env or (lambda n: adsr(n, 0.003, 0.05, 0.8, 0.02))
    return additive(freq, dur, lambda h: (1.0 / h) if h % 2 else 0, env)


def saw(freq, dur, env=None, bright=1.0):
    env = env or (lambda n: adsr(n, 0.01, 0.1, 0.7, 0.05))
    return additive(freq, dur, lambda h: (1.0 / h) * (bright ** (h - 1)), env)


def sine(freq, dur, env=None):
    env = env or (lambda n: adsr(n, 0.004, 0.02, 0.9, 0.02))
    t = t_axis(dur)
    return fade_edges(np.sin(2 * np.pi * freq * t) * env(len(t)))


def sweep(f0, f1, dur, shape="lin", amp_env=None):
    t = t_axis(dur)
    if shape == "exp":
        f = f0 * (f1 / f0) ** (t / dur)
    else:
        f = f0 + (f1 - f0) * t / dur
    phase = 2 * np.pi * np.cumsum(f) / SR
    env = amp_env(len(t)) if amp_env else np.ones_like(t)
    return fade_edges(np.sin(phase) * env)


def noise(dur, color="white"):
    n = int(dur * SR)
    w = rng.standard_normal(n)
    if color == "white":
        return w
    spec = np.fft.rfft(w)
    freqs = np.fft.rfftfreq(n, 1 / SR)
    freqs[0] = 1
    spec /= np.sqrt(freqs) if color == "pink" else freqs
    x = np.fft.irfft(spec, n)
    return x / (np.max(np.abs(x)) + 1e-9)


def lowpass(x, cutoff):
    """One-pole low-pass (vectorised via exponential filter in blocks)."""
    a = np.exp(-2 * np.pi * cutoff / SR)
    y = np.empty_like(x)
    acc = 0.0
    for i in range(0, len(x), 4096):
        blk = x[i:i + 4096]
        out = np.empty_like(blk)
        for j, v in enumerate(blk):
            acc = (1 - a) * v + a * acc
            out[j] = acc
        y[i:i + 4096] = out
    return y


def comb(x, delay, g):
    y = np.copy(x)
    for k in range(delay, len(y), delay):
        end = min(k + delay, len(y))
        y[k:end] += g * y[k - delay:end - delay]
    return y


def allpass(x, delay, g):
    y = np.zeros_like(x)
    xd = np.concatenate([np.zeros(delay), x])[:len(x)]
    y[:delay] = -g * x[:delay] + xd[:delay]
    for k in range(delay, len(y), delay):
        end = min(k + delay, len(y))
        y[k:end] = -g * x[k:end] + xd[k:end] + g * y[k - delay:end - delay]
    return y


def reverb(x, wet=0.25, room=0.82):
    combs = [1557, 1617, 1491, 1422]
    acc = sum(comb(x, d, room) for d in combs) / len(combs)
    for d, g in ((225, 0.5), (556, 0.5)):
        acc = allpass(acc, d, g)
    return (1 - wet) * x + wet * acc


def place(total, events):
    """events: list of (start_seconds, signal, gain)."""
    out = np.zeros(int(total * SR))
    for start, sig, gain in events:
        s = int(start * SR)
        e = min(len(out), s + len(sig))
        if s < len(out):
            out[s:e] += gain * sig[: e - s]
    return out


def normalise(x, peak_db=-3.0):
    peak = np.max(np.abs(x)) + 1e-12
    return x / peak * (10 ** (peak_db / 20))


def tail_fade(x, seconds=0.25):
    n = int(seconds * SR)
    x[-n:] *= np.linspace(1, 0, n) ** 2
    return x


# ----------------------------------------------------------------------------- tones
def sunrise_chime():
    seq = ["C5", "E5", "G5", "C6", "G5", "E5", "D5", "G5", "B5", "D6", "B5", "G5"]
    ev = []
    for rep in range(2):
        for i, n in enumerate(seq):
            ev.append((rep * 9 + i * 0.6, bell(note(n), 2.6, ratio=2.0, index=1.6, decay=1.8), 0.5 + rep * 0.25))
    return reverb(place(19, ev), wet=0.3)


def gentle_bells():
    pattern = [("E5", 0.0), ("B4", 0.9), ("G#5", 1.8), ("E5", 3.2), ("C#5", 4.1), ("B4", 5.0)]
    ev = [(r * 6.4 + t, bell(note(n), 3.0, ratio=1.4, index=2.4, decay=1.3), 0.6) for r in range(3) for n, t in pattern]
    return reverb(place(20, ev), wet=0.35, room=0.86)


def soft_keys():
    mel = ["E4", "G4", "C5", "E5", "D5", "B4", "G4", "B4", "C5", "G4", "E4", "G4", "A4", "C5", "E5", "D5"]
    ev = [(i * 0.5, epiano(note(n)), 0.5) for i, n in enumerate(mel)]
    ev += [(i * 2.0, epiano(note(n), 2.2), 0.35) for i, n in enumerate(["C3", "G2", "A2", "F2"])]
    x = place(8.6, ev)
    return reverb(np.tile(x, 2), wet=0.22)


def marimba_tone():
    mel = ["C5", "E5", "G5", "E5", "A5", "G5", "E5", "C5", "D5", "F5", "A5", "F5", "G5", "E5", "D5", "C5"]
    ev = [(i * 0.28, marimba(note(n)), 0.6) for i, n in enumerate(mel)]
    x = place(4.8, ev)
    return reverb(np.tile(x, 4), wet=0.15)


def kalimba_tone():
    mel = ["D5", "F#5", "A5", "D6", "A5", "F#5", "B5", "A5", "F#5", "E5", "D5", "E5", "F#5", "A5", "E5", "D5"]
    ev = [(i * 0.42, kalimba(note(n)), 0.6) for i, n in enumerate(mel)]
    x = place(7.2, ev)
    return reverb(np.tile(x, 3), wet=0.25)


def birdsong():
    ev = []
    t = 0.0
    while t < 18:
        f0 = rng.uniform(2600, 4200)
        for k in range(rng.integers(2, 6)):
            d = rng.uniform(0.05, 0.12)
            chirp = sweep(f0, f0 * rng.uniform(1.2, 1.6), d, "exp", lambda n: np.hanning(n))
            trem = 1 + 0.4 * np.sin(2 * np.pi * 38 * t_axis(d))
            ev.append((t + k * (d + 0.03), chirp * trem, rng.uniform(0.3, 0.6)))
        t += rng.uniform(0.8, 1.8)
    return reverb(place(19, ev), wet=0.3)


def ocean_wake():
    dur = 20.0
    t = t_axis(dur)
    waves = noise(dur, "pink") * (0.35 + 0.65 * (0.5 - 0.5 * np.cos(2 * np.pi * t / 5.0)) ** 2)
    waves = lowpass(waves, 1200)
    chimes = place(dur, [(s, bell(note(n), 3.0, 2.0, 1.2, 1.5), 0.5)
                         for s, n in ((1.0, "G5"), (3.5, "D6"), (6.0, "B5"), (11.0, "G5"), (13.5, "E6"), (16.0, "D6"))])
    x = 0.6 * waves / (np.max(np.abs(waves)) + 1e-9) + reverb(chimes, 0.4)
    # make the loop seamless: crossfade end into start
    n = int(1.0 * SR)
    x[:n] = x[:n] * np.linspace(0, 1, n) + x[-n:] * np.linspace(1, 0, n)
    return x[:-n]


def rising_tones():
    ev = []
    freqs = [note(n) for n in ["C5", "D5", "E5", "G5", "A5", "C6", "D6", "E6"]]
    for r in range(3):
        for i, f in enumerate(freqs):
            ev.append((r * 5.0 + i * 0.5, sine(f, 0.35, lambda n: adsr(n, 0.01, 0.05, 0.8, 0.12)), 0.25 + 0.1 * i))
    return reverb(place(15.2, ev), 0.15)


def classic_beep():
    b = square(1000, 0.09, lambda n: adsr(n, 0.002, 0.01, 0.9, 0.01))
    b = lowpass(b, 6000)
    group = place(1.0, [(i * 0.14, b, 0.8) for i in range(4)])
    return np.tile(group, 10)


def digital_alarm():
    hi = sine(2048, 0.1)
    lo = sine(2560, 0.1)
    unit = place(0.5, [(0.0, hi, 0.7), (0.12, lo, 0.7), (0.24, hi, 0.7)])
    return np.tile(unit, 24)


def lcd_watch():
    p = sine(4096, 0.045)
    unit = place(1.0, [(0.0, p, 0.6), (0.09, p, 0.6), (0.18, p, 0.6), (0.27, p, 0.6)])
    return np.tile(unit, 16)


def tick_tock():
    tick = lowpass(noise(0.03) * exp_decay(t_axis(0.03), 160), 5000)
    tock = lowpass(noise(0.04) * exp_decay(t_axis(0.04), 120), 2200)
    ev = []
    for i in range(16):
        ev.append((i * 1.0, tick, 0.6))
        ev.append((i * 1.0 + 0.5, tock, 0.6))
    ev += [(s, bell(note("A5"), 2.0, 2.0, 1.4, 2.0), 0.5) for s in (4.0, 12.0)]
    return place(16, ev)


def pulse():
    ev = []
    for bar in range(8):
        root = [note("A3"), note("F3"), note("C4"), note("G3")][bar % 4]
        for s in range(8):
            env = lambda n: adsr(n, 0.005, 0.08, 0.2, 0.05)
            ev.append((bar * 2.0 + s * 0.25, lowpass(saw(root * (2 if s % 2 else 1), 0.22, env, 0.85), 2500), 0.5))
    return place(16, ev)


def retro_arcade():
    mel = ["E5", "E5", "R", "E5", "R", "C5", "E5", "R", "G5", "R", "R", "R", "G4", "R", "R", "R",
           "C5", "R", "R", "G4", "R", "R", "E4", "R", "R", "A4", "R", "B4", "R", "A#4", "A4", "R"]
    ev = []
    step = 0.15
    for i, n in enumerate(mel):
        if n != "R":
            ev.append((i * step, square(note(n), step * 0.9, lambda k: adsr(k, 0.002, 0.03, 0.6, 0.02)), 0.4))
    bass = ["C3", "C3", "G2", "G2", "A2", "A2", "F2", "G2"]
    for i, n in enumerate(bass):
        ev.append((i * step * 4, square(note(n), step * 3.5, lambda k: adsr(k, 0.002, 0.05, 0.5, 0.03)), 0.25))
    x = place(len(mel) * step, ev)
    return np.tile(x, 4)


def terminal_beep():
    b = square(880, 0.12, lambda n: adsr(n, 0.002, 0.02, 0.8, 0.02))
    b2 = square(1320, 0.08, lambda n: adsr(n, 0.002, 0.02, 0.8, 0.02))
    unit = place(1.6, [(0.0, b, 0.6), (0.2, b, 0.6), (0.4, b2, 0.6)])
    return np.tile(unit, 10)


def neon_synth():
    chords = [["A3", "C4", "E4"], ["F3", "A3", "C4"], ["C4", "E4", "G4"], ["G3", "B3", "D4"]]
    ev = []
    for c, chord in enumerate(chords * 2):
        for s in range(16):
            n = chord[s % 3]
            f = note(n) * (2 if (s // 3) % 2 else 1)
            sig = saw(f, 0.11, lambda k: adsr(k, 0.003, 0.05, 0.4, 0.03), 0.8)
            sig += saw(f * 1.005, 0.11, lambda k: adsr(k, 0.003, 0.05, 0.4, 0.03), 0.8)
            ev.append((c * 2.0 + s * 0.125, lowpass(sig, 3200), 0.25))
        ev.append((c * 2.0, lowpass(saw(note(chord[0]) / 2, 1.9, None, 0.7), 900), 0.35))
    return reverb(place(16, ev), 0.25)


def siren():
    one = sweep(600, 1400, 1.6, "lin", lambda n: np.ones(n)) * 0.9
    two = sweep(1400, 600, 1.6, "lin", lambda n: np.ones(n)) * 0.9
    cycle = np.concatenate([one, two])
    return np.tile(cycle, 5)


def heavy_sleeper():
    a = square(880, 0.25, lambda n: adsr(n, 0.002, 0.02, 1.0, 0.01))
    b = square(1175, 0.25, lambda n: adsr(n, 0.002, 0.02, 1.0, 0.01))
    burst = noise(0.12) * adsr(int(0.12 * SR), 0.001, 0.02, 0.8, 0.02)
    unit = place(1.2, [(0.0, a, 0.8), (0.3, b, 0.8), (0.6, a, 0.8), (0.9, burst, 0.5)])
    return np.tile(unit, 12)


def timer_done():
    p = bell(note("A5"), 0.6, 2.0, 1.0, 6.0)
    unit = place(2.0, [(0.0, p, 0.7), (0.22, p, 0.7), (0.44, p, 0.7)])
    return np.tile(unit, 6)


def awake_ping():
    p = bell(note("E6"), 1.2, 2.0, 1.5, 4.0)
    return np.tile(place(2.5, [(0.0, p, 0.7), (0.3, bell(note("B5"), 1.2, 2.0, 1.5, 4.0), 0.5)]), 6)


TONES = {
    "sunrise_chime": (sunrise_chime, -3),
    "gentle_bells": (gentle_bells, -3),
    "soft_keys": (soft_keys, -3),
    "marimba": (marimba_tone, -3),
    "kalimba": (kalimba_tone, -3),
    "birdsong": (birdsong, -3, 3.0),
    "ocean_wake": (ocean_wake, -3, 1.8),
    "rising_tones": (rising_tones, -2),
    "classic_beep": (classic_beep, -1.5),
    "digital_alarm": (digital_alarm, -1.5),
    "lcd_watch": (lcd_watch, -2),
    "tick_tock": (tick_tock, -2, 2.5),
    "pulse": (pulse, -2),
    "retro_arcade": (retro_arcade, -2),
    "terminal_beep": (terminal_beep, -2),
    "neon_synth": (neon_synth, -2),
    "siren": (siren, -1),
    "heavy_sleeper": (heavy_sleeper, -0.5),
    "timer_done": (timer_done, -2),
    "awake_ping": (awake_ping, -3),
}


def write_ogg(name, x):
    x = np.clip(x, -1, 1)
    pcm = (x * 32767).astype("<i2")
    with tempfile.TemporaryDirectory() as tmp:
        wav_path = os.path.join(tmp, f"{name}.wav")
        with wave.open(wav_path, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        out = os.path.join(OUT, f"tone_{name}.ogg")
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav_path, "-c:a", "libvorbis", "-q:a", "4",
                        "-map_metadata", "-1", "-fflags", "+bitexact", "-flags:a", "+bitexact", out], check=True)
        return out


def main():
    os.makedirs(OUT, exist_ok=True)
    only = set(sys.argv[1:])
    for name, spec in TONES.items():
        fn, peak = spec[0], spec[1]
        drive = spec[2] if len(spec) > 2 else 0.0
        if only and name not in only:
            continue
        x = normalise(fn(), 0.0)
        if drive:  # gentle soft-clip compression to lift quiet, sparse tones
            x = np.tanh(drive * x) / np.tanh(drive)
        x = tail_fade(normalise(x, peak))
        path = write_ogg(name, x)
        print(f"{name:15s} {len(x) / SR:5.1f}s  {os.path.getsize(path) / 1024:6.1f} KiB")


if __name__ == "__main__":
    main()
