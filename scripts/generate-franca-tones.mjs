import { mkdirSync, writeFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { join } from 'node:path'

const raw = fileURLToPath(new URL('../app/src/main/res/raw/', import.meta.url))
const rate = 24_000
const tones = [
  { name: 'franca_tap', seconds: 0.085, notes: [[0, 0.073, 560, 0.36]] },
  { name: 'franca_saved', seconds: 0.32, notes: [[0, 0.16, 523.25, 0.40], [0.095, 0.21, 783.99, 0.36]] },
  { name: 'franca_yape_received', seconds: 0.67, notes: [[0, 0.24, 523.25, 0.53], [0.14, 0.27, 659.25, 0.49], [0.30, 0.35, 880, 0.47]] },
  { name: 'franca_yape_sent', seconds: 0.60, notes: [[0, 0.24, 659.25, 0.48], [0.16, 0.40, 493.88, 0.48]] },
  { name: 'franca_bcp_expense', seconds: 0.67, notes: [[0, 0.18, 440, 0.51], [0.12, 0.20, 554.37, 0.43], [0.29, 0.33, 493.88, 0.46]] },
]

function samples({ seconds, notes }) {
  return Array.from({ length: Math.round(rate * seconds) }, (_, frame) => {
    const time = frame / rate
    return notes.reduce((mix, [start, duration, frequency, volume]) => {
      const t = time - start
      if (t < 0 || t >= duration) return mix
      const attack = Math.min(t / 0.008, 1)
      const decay = Math.exp(-4.6 * t / duration)
      const release = Math.min((duration - t) / 0.03, 1)
      const phase = 2 * Math.PI * frequency * t
      const bell = 0.82 * Math.sin(phase) + 0.12 * Math.sin(2 * phase) + 0.06 * Math.sin(3 * phase)
      return mix + attack * decay * release * volume * bell
    }, 0)
  })
}

function wave(pcm) {
  const buffer = Buffer.alloc(44 + pcm.length * 2)
  buffer.write('RIFF', 0)
  buffer.writeUInt32LE(buffer.length - 8, 4)
  buffer.write('WAVEfmt ', 8)
  buffer.writeUInt32LE(16, 16)
  buffer.writeUInt16LE(1, 20)
  buffer.writeUInt16LE(1, 22)
  buffer.writeUInt32LE(rate, 24)
  buffer.writeUInt32LE(rate * 2, 28)
  buffer.writeUInt16LE(2, 32)
  buffer.writeUInt16LE(16, 34)
  buffer.write('data', 36)
  buffer.writeUInt32LE(pcm.length * 2, 40)
  pcm.forEach((value, index) => {
    buffer.writeInt16LE(Math.round(Math.max(-1, Math.min(1, value)) * 32767), 44 + index * 2)
  })
  return buffer
}

mkdirSync(raw, { recursive: true })
for (const tone of tones) {
  writeFileSync(join(raw, `${tone.name}.wav`), wave(samples(tone)))
}
if (process.argv[2]) {
  const gap = new Array(Math.round(0.48 * rate)).fill(0)
  writeFileSync(process.argv[2], wave(tones.flatMap((tone, index) => [
    ...(index === 0 ? [] : gap),
    ...samples(tone),
  ])))
}
