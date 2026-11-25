# Promo video (English)

`beng-promo-en.mp4`: 32 s, 1080×1920 (9:16), 30 fps, with sound.

The screens are redrawn in HTML from the app's own colours, layout and English strings; the sound uses the app's
Klaxon Blast, Rising Wail and Rapid Fire Bell tones plus a synthesized vibration buzz.

Rebuild (needs Python 3 + numpy, Node + Playwright, ffmpeg):

```sh
cd promo
python3 gen_audio.py ../app/src/main/res/raw .   # audio.wav + env.json (sync data for the shake/flash)
node render.js video                             # frames/*.jpg
ffmpeg -framerate 30 -i frames/f%04d.jpg -i audio.wav -c:v libx264 -crf 18 -pix_fmt yuv420p \
  -c:a aac -b:a 192k -shortest -movflags +faststart beng-promo-en.mp4
```

Scene timings live in `timeline.json`; captions are the `CAPS` list in `promo.html`.
