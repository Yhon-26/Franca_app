import { createRoot } from 'react-dom/client'
import { flushSync } from 'react-dom'
import { CapybaraMascot } from './CapybaraMascot'
import { ReportesDeskScene } from './ReportesDeskScene'

const root = createRoot(document.getElementById('scene')!)
Object.assign(window, {
  renderDeskFrame: (time: number) => flushSync(() => root.render(<ReportesDeskScene frameTime={time} />)),
  renderSaveReaction: () => flushSync(() => root.render(
    <CapybaraMascot smile rig={{
      turn: 0.78,
      headTiltDeg: -3,
      browLift: 1,
      nearPawY: -18,
      nearPawRot: -22,
      screenGlow: 0,
    }} />,
  )),
})
