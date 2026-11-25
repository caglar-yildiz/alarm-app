"""Builds the promo soundtrack from the app's own alarm tones plus synthesized vibration buzz and UI sounds.
Also writes env.json: per-frame loudness and vibration state so the visuals pulse in sync with the audio."""
import json, sys, wave
import numpy as np

RAW = sys.argv[1]          # app/src/main/res/raw
OUT = sys.argv[2]          # output dir
SR = 44100
FPS = 30
TOTAL = 32.0
T = json.load(open(f"{OUT}/timeline.json"))

n = int(TOTAL * SR)
mix = np.zeros(n)
t_all = np.arange(n) / SR


def load(name):
    w = wave.open(f"{RAW}/{name}.wav")
    data = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(float) / 32768
    if w.getnchannels() == 2:
        data = data.reshape(-1, 2).mean(1)
    sr = w.getframerate()
    # resample to SR (linear)
    x = np.arange(0, len(data), sr / SR)
    return np.interp(x, np.arange(len(data)), data)


def place(sig, at, gain=1.0):
    i = int(at * SR)
    j = min(n, i + len(sig))
    if j > i:
        mix[i:j] += sig[: j - i] * gain


def loop(sig, start, end, gain=1.0, fade=0.03):
    length = int((end - start) * SR)
    reps = int(np.ceil(length / len(sig)))
    s = np.tile(sig, reps)[:length].copy()
    f = int(fade * SR)
    s[:f] *= np.linspace(0, 1, f)
    s[-f:] *= np.linspace(1, 0, f)
    place(s, start, gain)


def env(length, a=0.005, r=0.1):
    e = np.ones(length)
    ai, ri = max(1, min(int(a * SR), length // 4)), max(1, min(int(r * SR), length // 2))
    e[:ai] = np.linspace(0, 1, ai)
    e[-ri:] = np.linspace(1, 0, ri)
    return e


def tone(freq, dur, gain=0.3, kind="sine", decay=None):
    tt = np.arange(int(dur * SR)) / SR
    if kind == "sine":
        s = np.sin(2 * np.pi * freq * tt)
    else:
        s = np.sign(np.sin(2 * np.pi * freq * tt)) * 0.6
    e = env(len(tt))
    if decay:
        e *= np.exp(-tt / decay)
    return s * e * gain


def vib_on(t):
    """Phone vibration pattern while ringing: 0.55 s buzz, 0.25 s pause."""
    for a, b in T["ringing"]:
        if a <= t < b:
            return ((t - a) % 0.8) < 0.55
    return False


# --- ambient pad (night) ---
def pad(start, end, gain):
    tt = np.arange(int((end - start) * SR)) / SR
    s = (np.sin(2 * np.pi * 110 * tt) + .6 * np.sin(2 * np.pi * 164.8 * tt) + .4 * np.sin(2 * np.pi * 220.5 * tt))
    s *= 0.5 + 0.5 * np.sin(2 * np.pi * 0.25 * tt)
    e = np.ones(len(tt)); f = int(0.6 * SR)
    e[:f] = np.linspace(0, 1, f); e[-f:] = np.linspace(1, 0, f)
    place(s * e, start, gain)


pad(0, T["ring1"][0] + 0.05, 0.05)
# clock ticks in the intro
for k in np.arange(0.4, T["ring1"][0] - 0.1, 0.5):
    place(tone(2400, 0.02, 0.12, decay=0.006), k)

# --- alarm rings: the app's Klaxon Blast tone, plus a vibration buzz ---
klaxon = load("beng_klaxon")
klaxon /= np.abs(klaxon).max()
wail = load("beng_wail")
wail /= np.abs(wail).max()
loop(klaxon, *T["ring1"], gain=0.62)
loop(klaxon, *T["ring2"], gain=0.62)
loop(wail, *T["ring2"], gain=0.25)

# vibration motor: ~170 Hz rough buzz with rattle
buzz = np.zeros(n)
for a, b in T["ringing"]:
    i, j = int(a * SR), int(b * SR)
    tt = t_all[i:j]
    on = np.array([vib_on(x) for x in tt[::100]]).repeat(100)[: j - i].astype(float)
    # smooth on/off edges
    k = np.ones(441) / 441
    on = np.convolve(on, k, mode="same")
    s = (np.sin(2 * np.pi * 172 * tt) + 0.5 * np.sign(np.sin(2 * np.pi * 86 * tt)) + 0.25 * np.random.default_rng(1).standard_normal(len(tt)))
    buzz[i:j] = s * on
mix += buzz * 0.16

# impact hit at each ring start
for a, _ in T["ringing"]:
    tt = np.arange(int(0.6 * SR)) / SR
    boom = np.sin(2 * np.pi * (60 + 80 * np.exp(-tt / 0.05)) * tt) * np.exp(-tt / 0.18)
    place(boom, a, 0.7)

# volume key presses (clicks) and the volume bouncing back
for k in T["vol_presses"]:
    place(tone(1800, 0.03, 0.25, decay=0.01), k)
    place(tone(900, 0.05, 0.15, decay=0.02), k + 0.18)

# typing taps
for k in T["taps"]:
    place(tone(1200, 0.04, 0.22, decay=0.012), k)

# success chime on dismiss
d = T["dismiss"]
place(tone(784, 0.35, 0.25, decay=0.2), d)
place(tone(1175, 0.5, 0.25, decay=0.3), d + 0.1)

# mission chips pop
for k in T["chip_pops"]:
    place(tone(660 + 90 * (k % 1 * 10), 0.08, 0.15, decay=0.04), k)

# calm after dismiss / back to sleep
pad(T["dismiss"] + 0.3, T["notif"], 0.04)

# fast-forward whoosh
a, b = T["ff"]
tt = np.arange(int((b - a + 0.3) * SR)) / SR
noise = np.random.default_rng(2).standard_normal(len(tt))
# crude band sweep via moving average of varying length
sweep = np.convolve(noise, np.ones(8) / 8, mode="same") * np.sin(np.pi * tt / tt[-1]) ** 2
place(sweep, a, 0.25)

# notification chime
place(tone(988, 0.25, 0.3, decay=0.15), T["notif"])
place(tone(1319, 0.4, 0.3, decay=0.25), T["notif"] + 0.12)

# countdown ticks, accelerating
c0, c1 = T["countdown"]
k = c0
while k < c1:
    p = (k - c0) / (c1 - c0)
    place(tone(1500 + 600 * p, 0.03, 0.18 + 0.12 * p, decay=0.01), k)
    k += 0.28 - 0.2 * p

# briefing chime
place(tone(523, 0.6, 0.18, decay=0.4), T["brief"])
place(tone(659, 0.6, 0.18, decay=0.4), T["brief"] + 0.12)
place(tone(784, 0.9, 0.18, decay=0.5), T["brief"] + 0.24)
pad(T["brief"], TOTAL, 0.045)

# logo hit with the app's bell tone
bell = load("beng_bell")
bell /= np.abs(bell).max()
seg = bell[: int(0.9 * SR)] * np.linspace(1, 0, int(0.9 * SR))
place(seg, T["end"] + 0.15, 0.35)
place(tone(196, 1.6, 0.35, decay=0.6), T["end"] + 0.15)

# master: soft clip + normalise
mix = np.tanh(mix * 1.3)
mix /= np.abs(mix).max() / 0.95
fade = int(0.5 * SR)
mix[-fade:] *= np.linspace(1, 0, fade)

stereo = np.stack([mix, mix], 1)
with wave.open(f"{OUT}/audio.wav", "wb") as w:
    w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR)
    w.writeframes((stereo * 32767).astype(np.int16).tobytes())

# per-frame envelopes for visuals
frames = int(TOTAL * FPS)
loud, vib = [], []
alarm_only = np.zeros(n)
for a, b in T["ringing"]:
    i, j = int(a * SR), int(b * SR)
    alarm_only[i:j] = mix[i:j]
for f in range(frames):
    i = int(f / FPS * SR); j = int((f + 1) / FPS * SR)
    loud.append(float(np.sqrt(np.mean(alarm_only[i:j] ** 2))))
    vib.append(1 if vib_on((f + 0.5) / FPS) else 0)
m = max(loud) or 1
json.dump({"loud": [round(x / m, 3) for x in loud], "vib": vib}, open(f"{OUT}/env.json", "w"))
print("ok", frames)
