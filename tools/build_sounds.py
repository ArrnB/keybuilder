#!/usr/bin/env python3
"""
Makes the key sounds in app/src/main/res/raw/key_*.wav.

They are not recordings: each one is a few lines of arithmetic (a tone, a burst of noise,
a fade), so there is nobody to credit and nothing to license. Change the numbers, run
    python3 tools/build_sounds.py
and build the app again to hear the difference. To add a sound, add a function here, a name
to KEY_SOUNDS in model/KeyboardModel.kt, and a line in ime/KeySounds.kt.
"""
import math
import os
import random
import struct
import wave

RATE = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "raw")


def save(name, samples):
    """Writes numbers between -1 and 1 as a 16-bit mono WAV file, with a 2 ms fade at both ends so it never clicks."""
    edge = int(RATE * 0.002)
    for i in range(min(edge, len(samples))):
        samples[i] *= i / edge
        samples[-1 - i] *= i / edge
    peak = max(1e-9, max(abs(s) for s in samples))
    data = b"".join(struct.pack("<h", int(max(-1, min(1, s / peak * 0.85)) * 32767)) for s in samples)
    os.makedirs(OUT, exist_ok=True)
    with wave.open(os.path.join(OUT, f"key_{name}.wav"), "wb") as f:
        f.setnchannels(1)
        f.setsampwidth(2)
        f.setframerate(RATE)
        f.writeframes(data)
    print(f"key_{name}.wav  {len(samples) * 1000 // RATE} ms  {len(data)} bytes")


def tone(freq, seconds, decay, freq_end=None):
    """A sine wave that dies away. With freq_end it slides from one pitch to another."""
    n = int(RATE * seconds)
    out, phase = [], 0.0
    for i in range(n):
        t = i / n
        f = freq if freq_end is None else freq + (freq_end - freq) * t
        phase += 2 * math.pi * f / RATE
        out.append(math.sin(phase) * math.exp(-decay * t))
    return out


def noise(seconds, decay, smooth=0.0, seed=1):
    """A hiss that dies away. smooth (0..1) takes the sharpness off, making it more of a thud."""
    rnd = random.Random(seed)
    n = int(RATE * seconds)
    out, last = [], 0.0
    for i in range(n):
        last = smooth * last + (1 - smooth) * rnd.uniform(-1, 1)
        out.append(last * math.exp(-decay * i / n))
    return out


def mix(*parts):
    n = max(len(p) for p, _ in parts)
    out = [0.0] * n
    for samples, level in parts:
        for i, s in enumerate(samples):
            out[i] += s * level
    return out


save("click", mix((noise(0.012, 9, 0.2, seed=3), 0.9), (tone(2100, 0.020, 8), 0.5)))
save("pop", tone(520, 0.060, 5, freq_end=140))
save("tick", mix((tone(3600, 0.014, 7), 1.0), (noise(0.004, 6, 0.0, seed=5), 0.3)))
save("thock", mix((tone(170, 0.075, 6, freq_end=110), 1.0), (noise(0.030, 10, 0.85, seed=7), 0.9), (noise(0.006, 6, 0.1, seed=8), 0.25)))
save("drop", tone(650, 0.085, 4, freq_end=1500))
save("beep", tone(880, 0.060, 2.5))
save("typewriter", mix((noise(0.018, 7, 0.05, seed=11), 1.0), (tone(1250, 0.055, 6), 0.45), (tone(310, 0.040, 8), 0.5)))
