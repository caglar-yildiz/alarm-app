const { chromium } = require('playwright');
const fs = require('fs');
const dir = __dirname;
const [mode, ...rest] = process.argv.slice(2);
(async () => {
  const html = fs.readFileSync(dir + '/promo.html', 'utf8')
    .replace('__TIMELINE__', fs.readFileSync(dir + '/timeline.json', 'utf8'))
    .replace('__ENV__', fs.readFileSync(dir + '/env.json', 'utf8'));
  fs.writeFileSync(dir + '/built.html', html);
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1080, height: 1920 }, deviceScaleFactor: 1 });
  page.on('pageerror', e => console.error('PAGEERR', e.message));
  await page.goto('file://' + dir + '/built.html');
  await page.evaluate(() => document.fonts.ready);
  if (mode === 'stills') {
    fs.mkdirSync(dir + '/stills', { recursive: true });
    for (const t of rest) {
      await page.evaluate(t => render(t), +t);
      await page.screenshot({ path: `${dir}/stills/t${t}.png` });
    }
  } else {
    const total = 32, fps = 30;
    fs.mkdirSync(dir + '/frames', { recursive: true });
    for (let f = 0; f < total * fps; f++) {
      await page.evaluate(t => render(t), f / fps);
      await page.screenshot({ path: `${dir}/frames/f${String(f).padStart(4, '0')}.jpg`, type: 'jpeg', quality: 93 });
      if (f % 60 === 0) console.log('frame', f);
    }
  }
  await browser.close();
})();
