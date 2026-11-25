// Render the original ZIP's SVG frames. Usage: npm run build [-- path/to/node_modules]
// Add --reaction-only after node_modules to render just the first-save artwork.
// Set FRANCA_CHROME to a Chromium executable and have ffmpeg on PATH.
import { spawn, execFileSync } from 'node:child_process'
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { pathToFileURL, fileURLToPath } from 'node:url'
import { createRequire } from 'node:module'
import { setTimeout as delay } from 'node:timers/promises'

const root = dirname(fileURLToPath(import.meta.url))
const args = process.argv.slice(2)
const reactionOnly = args.includes('--reaction-only')
const dependencies = resolve(args.find(arg => arg !== '--reaction-only') ?? join(root, 'node_modules'))
const { buildSync } = createRequire(join(dependencies, '..', 'package.json'))('esbuild')
const work = resolve(root, '../../build/desk-scene-render')
const frames = join(work, 'frames')
const profile = join(work, 'chrome')
const output = resolve(root, '../../app/src/main/res/raw/franca_desk_scene.gif')
const preview = resolve(root, '../../app/src/main/res/drawable-nodpi/franca_desk_preview.png')
const reaction = resolve(root, '../../app/src/main/res/drawable-nodpi/franca_first_movement.png')
const width = 684
const height = 504
const fps = 25
const frameCount = 217

mkdirSync(frames, { recursive: true })
const { outputFiles } = buildSync({
  absWorkingDir: root,
  entryPoints: ['index.tsx'],
  bundle: true,
  write: false,
  jsx: 'automatic',
  format: 'iife',
  define: { 'process.env.NODE_ENV': '"production"' },
  nodePaths: [dependencies],
})

const page = join(work, 'scene.html')
const script = outputFiles[0].text.replace(/<\/script/gi, '<\\/script')
writeFileSync(page, '<!doctype html><meta charset="UTF-8"><style>' +
  'html,body{margin:0;overflow:hidden}#scene>div>svg{width:684px;height:504px;display:block}' +
  '#scene>svg{width:315px;height:285px;display:block}' +
  '</style><div id="scene"></div><script>' + script + '</script>')

const chrome = process.env.FRANCA_CHROME ?? 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const browser = spawn(chrome, [
  '--headless', '--disable-gpu', '--disable-background-networking', '--no-first-run',
  '--remote-debugging-port=0', '--user-data-dir=' + profile, 'about:blank',
], { windowsHide: true, stdio: ['ignore', 'ignore', 'pipe'] })

try {
  const endpoint = await new Promise((resolve, reject) => {
    browser.once('error', reject)
    browser.once('exit', code => reject(new Error('Chrome quit with status ' + code)))
    browser.stderr.on('data', data => {
      const match = data.toString().match(/DevTools listening on (ws:\/\/\S+)/)
      if (match) resolve(new URL(match[1]))
    })
  })
  const tabs = await fetch('http://' + endpoint.host + '/json/list').then(response => response.json())
  const socket = new WebSocket(tabs.find(tab => tab.type === 'page').webSocketDebuggerUrl)
  await new Promise((resolve, reject) => {
    socket.addEventListener('open', resolve, { once: true })
    socket.addEventListener('error', reject, { once: true })
  })

  let sequence = 0
  const pending = new Map()
  socket.addEventListener('message', event => {
    const message = JSON.parse(event.data)
    const call = pending.get(message.id)
    if (!call) return
    pending.delete(message.id)
    if (message.error) call.reject(new Error(message.error.message))
    else call.resolve(message.result)
  })
  function send(method, params = {}) {
    const id = ++sequence
    return new Promise((resolve, reject) => {
      pending.set(id, { resolve, reject })
      socket.send(JSON.stringify({ id, method, params }))
    })
  }

  try {
    await send('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: false })
    await send('Emulation.setDefaultBackgroundColorOverride', { color: { r: 0, g: 0, b: 0, a: 0 } })
    await send('Page.navigate', { url: pathToFileURL(page).href })
    for (let attempt = 0; attempt < 100; attempt++) {
      const { result } = await send('Runtime.evaluate', { expression: 'typeof window.renderDeskFrame' })
      if (result.value === 'function') break
      if (attempt === 99) throw new Error('The scene did not load')
      await delay(50)
    }

    for (let i = 0; !reactionOnly && i < frameCount; i++) {
      const { exceptionDetails } = await send('Runtime.evaluate', {
        expression: 'window.renderDeskFrame(' + (i / fps) + ')',
      })
      if (exceptionDetails) throw new Error(exceptionDetails.text)
      const { data } = await send('Page.captureScreenshot', { format: 'png' })
      writeFileSync(join(frames, 'frame-' + String(i).padStart(3, '0') + '.png'), Buffer.from(data, 'base64'))
    }

    const { exceptionDetails } = await send('Runtime.evaluate', { expression: 'window.renderSaveReaction()' })
    if (exceptionDetails) throw new Error(exceptionDetails.text)
    const { result } = await send('Runtime.evaluate', {
      expression: `(() => {
        const bounds = document.querySelector('#scene>svg').getBBox()
        const x = Math.max(0, Math.floor(bounds.x * 1.5) - 8)
        const y = Math.max(0, Math.floor(bounds.y * 1.5) - 8)
        return {
          x, y, scale: 1,
          width: Math.ceil((bounds.x + bounds.width) * 1.5) + 8 - x,
          height: Math.ceil((bounds.y + bounds.height) * 1.5) + 8 - y
        }
      })()`,
      returnByValue: true,
    })
    const { data } = await send('Page.captureScreenshot', { format: 'png', clip: result.value })
    mkdirSync(dirname(reaction), { recursive: true })
    writeFileSync(reaction, Buffer.from(data, 'base64'))
  } finally {
    socket.close()
  }
} finally {
  browser.kill()
}

if (reactionOnly) {
  console.log(reaction)
  process.exit(0)
}

execFileSync('ffmpeg', [
  '-y', '-loglevel', 'error', '-framerate', String(fps), '-i', join(frames, 'frame-%03d.png'),
  '-frames:v', String(frameCount), '-vf',
  'split[scene][colors];[colors]palettegen=stats_mode=diff[palette];' +
  '[scene][palette]paletteuse=dither=sierra2_4a:diff_mode=rectangle',
  '-loop', '-1', output,
], { stdio: 'inherit' })
mkdirSync(dirname(preview), { recursive: true })
execFileSync('ffmpeg', [
  '-y', '-loglevel', 'error', '-i', output, '-frames:v', '1', '-vf', 'scale=342:252', preview,
], { stdio: 'inherit' })
console.log(output)
