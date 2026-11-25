import React from 'react'
import { CapybaraMascot, DEFAULT_RIG_STATE, MascotRigState } from './CapybaraMascot'

interface SceneFrame {
  rig: MascotRigState
  visibleLineCount: number
  newestLineProgress: number
  chartProgress: number
  leftKeyDown: boolean
  rightKeyDown: boolean
  leftRipple: number
  rightRipple: number
  screenPulse: number
  deskImpactY: number
  stoolCompress: number
  stageZoom: number
  stagePanX: number
  stagePanY: number
  typingIntensity: number
  steamSway: number
}

const clamp = (v: number, min: number, max: number) => Math.min(max, Math.max(min, v))

const easeOutCubic = (x: number) => 1 - Math.pow(1 - clamp(x, 0, 1), 3)
const easeOutBack = (x: number) => {
  const t = clamp(x, 0, 1)
  const c1 = 1.70158
  const c3 = c1 + 1
  return 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2)
}
const easeInOutCubic = (x: number) => {
  const t = clamp(x, 0, 1)
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2
}
const lerp = (a: number, b: number, t: number) => a + (b - a) * clamp(t, 0, 1)

function turnToPose(turn: number): MascotRigState['pose'] {
  if (turn < 0.28) return 'profile'
  if (turn < 0.74) return 'threeQuarter'
  return 'nearFront'
}

/** Exact timestamps (in seconds) when new lines appear on the laptop during rapid typing */
const NEW_LINE_TIMESTAMPS = [1.36, 1.52, 1.68, 1.92, 2.1, 2.28, 2.5, 2.7, 2.88]

const REPORT_LINES: Array<{ width: number; accent?: boolean }> = [
  { width: 22, accent: true }, // Starter header
  { width: 28 },               // Starter row 1
  { width: 19 },               // Starter row 2
  // 9 progressive lines typed during 1.30–2.92s bursts:
  { width: 26, accent: true },
  { width: 29 },
  { width: 21 },
  { width: 27 },
  { width: 24, accent: true },
  { width: 30 },
  { width: 18 },
  { width: 26 },
  { width: 23, accent: true },
]

export function computeSceneFrame(t: number): SceneFrame {
  const rig: MascotRigState = { ...DEFAULT_RIG_STATE }

  // Count how many lines are visible on the laptop screen (3 initial + up to 9 typed)
  let extraLines = 0
  let latestLineAge = 99
  for (const ts of NEW_LINE_TIMESTAMPS) {
    if (t >= ts) {
      extraLines += 1
      latestLineAge = Math.min(latestLineAge, t - ts)
    }
  }
  const visibleLineCount = 3 + extraLines
  const newestLineProgress = latestLineAge < 0.14 ? easeOutCubic(latestLineAge / 0.14) : 1
  const screenPulse = latestLineAge < 0.15 ? 1 - latestLineAge / 0.15 : 0
  const chartProgress = clamp((t - 1.32) / 1.6, 0, 1)

  const baseFrame: Omit<SceneFrame, 'rig'> = {
    visibleLineCount,
    newestLineProgress,
    chartProgress,
    leftKeyDown: false,
    rightKeyDown: false,
    leftRipple: 0,
    rightRipple: 0,
    screenPulse,
    deskImpactY: 0,
    stoolCompress: 0,
    stageZoom: 1,
    stagePanX: 0,
    stagePanY: 0,
    typingIntensity: 0,
    steamSway: 0,
  }

  // ---------------------------------------------------------------------------
  // 0.00 – 0.25 s: Working at its rhythm, suddenly freezes mid-keystroke
  // with near paw suspended cleanly above the keyboard + alert ear-perk!
  // ---------------------------------------------------------------------------
  if (t < 0.25) {
    rig.turn = 0
    rig.pose = 'profile'
    rig.eyeOffsetX = 1.1
    rig.eyeOffsetY = 0.5
    rig.torsoLeanDeg = 2.6
    rig.headTiltDeg = 1.4
    rig.screenGlow = 0.4

    if (t < 0.13) {
      const u = easeOutCubic(t / 0.13)
      rig.farPawY = Math.sin((t / 0.13) * Math.PI) * 2.4
      rig.nearPawY = lerp(-2, -18.5, u)
      rig.nearPawX = lerp(0, -2.8, u)
      rig.nearPawRot = lerp(-2, -17, u)
      rig.shoulderBounceY = Math.sin((t / 0.13) * Math.PI) * -0.8
      rig.breath = 0.4
    } else {
      // 0.13 – 0.25 s: Sudden dead stop mid-stroke! Ear perks up alertly!
      const alertU = easeOutBack(clamp((t - 0.13) / 0.08, 0, 1))
      rig.farPawY = 0.4
      rig.nearPawY = -18.5
      rig.nearPawX = -2.8
      rig.nearPawRot = -17
      rig.shoulderBounceY = 0
      rig.earPerkDeg = -13 * alertU
      rig.headStretchY = 1 + 0.022 * alertU
    }

    return {
      ...baseFrame,
      rig,
      rightKeyDown: t > 0.03 && t < 0.1,
      screenPulse: 0,
    }
  }

  // ---------------------------------------------------------------------------
  // 0.25 – 0.55 s: Eyes lead first, then continuous 60fps 2.5D head & torso turn
  // passing smoothly through three-quarter (0.5) to near-front (1.0).
  // ---------------------------------------------------------------------------
  if (t < 0.55) {
    if (t < 0.32) {
      const u = easeOutCubic((t - 0.25) / 0.07)
      rig.turn = lerp(0, 0.07, u)
      rig.pose = 'profile'
      rig.eyeOffsetX = lerp(1.1, -2.8, u)
      rig.eyeOffsetY = lerp(0.5, -0.2, u)
      rig.eyeOpen = lerp(1, 1.09, u)
      rig.browLift = lerp(0, 1.1, u)
      rig.earPerkDeg = lerp(-13, -7, u)
      rig.headOffsetX = lerp(0, -1.5, u)
      rig.headStretchY = lerp(1.02, 0.98, u)
      rig.headTiltDeg = lerp(1.4, 0.3, u)
      rig.torsoLeanDeg = lerp(2.6, 2.1, u)
      rig.nearPawY = -18.5
      rig.nearPawX = -2.8
      rig.nearPawRot = -17
      rig.farPawY = 0.4
    } else {
      const u = easeInOutCubic((t - 0.32) / 0.23)
      const turn = lerp(0.07, 1, u)
      rig.turn = turn
      rig.pose = turnToPose(turn)
      rig.eyeOffsetX = lerp(-2.4, 0, easeOutCubic(u))
      rig.eyeOffsetY = lerp(-0.2, 0, u)
      rig.eyeOpen = lerp(1.09, 1.1, u)
      rig.browLift = lerp(1.1, 1.2, u)
      rig.earPerkDeg = Math.sin(u * Math.PI) * -8
      rig.headphoneBounceY = Math.sin(u * Math.PI) * -1.1
      rig.headStretchY = 1 + Math.sin(u * Math.PI) * 0.03
      rig.headTiltDeg = lerp(0.3, 0, u)
      rig.torsoLeanDeg = lerp(2.1, 0, u)
      rig.nearPawY = lerp(-18.5, -7.5, u)
      rig.nearPawX = lerp(-2.8, -1, u)
      rig.nearPawRot = lerp(-17, -4, u)
      rig.farPawY = lerp(0.4, -6, u)
    }

    const zoomU = easeOutCubic(clamp((t - 0.32) / 0.23, 0, 1))
    return {
      ...baseFrame,
      rig,
      stageZoom: lerp(1, 1.028, zoomU),
      stagePanX: lerp(0, 3.5, zoomU),
      stagePanY: lerp(0, 2, zoomU),
      screenPulse: 0,
    }
  }

  // ---------------------------------------------------------------------------
  // 0.55 – 1.00 s: Widens eyes slightly, shrugs shoulders, and freezes still
  // for a clear moment of realization ("he just noticed us watching").
  // ---------------------------------------------------------------------------
  if (t < 1.0) {
    rig.turn = 1
    rig.pose = 'nearFront'
    rig.eyeOffsetX = 0
    rig.eyeOffsetY = 0
    rig.torsoLeanDeg = 0

    // 0.55 – 0.66 s: Crisp widen + shrug; 0.66 – 1.00 s: Dead-still hold (340 ms)
    const u = easeOutCubic(clamp((t - 0.55) / 0.11, 0, 1))
    const pop = Math.sin(clamp((t - 0.55) / 0.11, 0, 1) * Math.PI)
    rig.eyeOpen = lerp(1.1, 1.38, u)
    rig.browLift = lerp(1.2, 2.8, u)
    rig.shoulderShrug = lerp(0, 1, u)
    rig.surpriseExpression = lerp(0, 0.85, u)
    rig.earPerkDeg = lerp(0, 7.5, u)
    rig.headphoneBounceY = pop * -1.4
    rig.headStretchY = lerp(1.02, 0.975, u)
    rig.torsoStretchY = lerp(1, 0.975, u)
    rig.nearPawY = lerp(-7.5, -12, u)
    rig.nearPawX = lerp(-1, -2.5, u)
    rig.nearPawRot = lerp(-4, -8, u)
    rig.farPawY = lerp(-6, -10.5, u)
    rig.farPawRot = lerp(0, -6, u)

    return {
      ...baseFrame,
      rig,
      stoolCompress: u * 0.8,
      stageZoom: lerp(1.028, 1.042, u),
      stagePanX: 4.5,
      stagePanY: 2.5,
      screenPulse: 0,
    }
  }

  // ---------------------------------------------------------------------------
  // 1.00 – 1.30 s: Snaps back toward the laptop, leans forward over the keyboard.
  // One paw lands first (1.18 s) and the second paw lands right after (1.27 s).
  // ---------------------------------------------------------------------------
  if (t < 1.3) {
    const whipU = easeOutCubic(clamp((t - 1.0) / 0.12, 0, 1))
    const turn = lerp(1, 0, whipU)
    rig.turn = turn
    rig.pose = turnToPose(turn)

    rig.blink = t > 1.01 && t < 1.07 ? Math.sin(((t - 1.01) / 0.06) * Math.PI) : 0
    rig.surpriseExpression = lerp(0.85, 0, clamp((t - 1.0) / 0.08, 0, 1))

    rig.eyeOffsetX = lerp(0, 1.5, whipU)
    rig.eyeOffsetY = lerp(0, 0.9, whipU)
    rig.eyeOpen = lerp(1.38, 0.91, whipU)
    rig.browLift = lerp(2.8, -0.5, whipU)
    rig.shoulderShrug = lerp(1, 0, easeOutCubic(clamp((t - 1.0) / 0.11, 0, 1)))

    const leanOvershoot = easeOutBack(clamp((t - 1.0) / 0.22, 0, 1))
    rig.torsoLeanDeg = lerp(0, 10.8, leanOvershoot)
    rig.headTiltDeg = lerp(0, 5.5, leanOvershoot)
    rig.headStretchY = 1 - Math.sin(whipU * Math.PI) * 0.055
    rig.torsoStretchY = 1 - Math.sin(whipU * Math.PI) * 0.04
    rig.earPerkDeg = Math.sin(whipU * Math.PI) * 14
    rig.headphoneBounceY = Math.sin(whipU * Math.PI * 1.5) * 1.6
    rig.screenGlow = lerp(0.2, 0.55, whipU)

    // Paw 1 (farPaw) arcs and lands FIRST at 1.18 s
    if (t < 1.08) {
      rig.farPawY = lerp(-10.5, -14.5, (t - 1.0) / 0.08)
      rig.farPawRot = -11
    } else if (t < 1.18) {
      const p1 = easeInOutCubic((t - 1.08) / 0.1)
      rig.farPawY = lerp(-14.5, 3.6, p1)
      rig.farPawRot = lerp(-11, 3.5, p1)
      rig.pawBlur = Math.sin(p1 * Math.PI) * 0.7
    } else {
      const settle1 = easeOutCubic(clamp((t - 1.18) / 0.06, 0, 1))
      rig.farPawY = lerp(3.6, 1.6, settle1)
      rig.farPawRot = lerp(3.5, 1, settle1)
    }

    // Paw 2 (nearPaw) stays aloft until 1.18 s, then lands RIGHT AFTER at 1.27 s
    if (t < 1.18) {
      rig.nearPawY = lerp(-12, -16.5, clamp((t - 1.0) / 0.1, 0, 1))
      rig.nearPawRot = -15
    } else if (t < 1.27) {
      const p2 = easeInOutCubic((t - 1.18) / 0.09)
      rig.nearPawY = lerp(-16.5, 3.5, p2)
      rig.nearPawRot = lerp(-15, 3.5, p2)
      rig.pawBlur = Math.max(rig.pawBlur, Math.sin(p2 * Math.PI) * 0.8)
    } else {
      const settle2 = easeOutCubic(clamp((t - 1.27) / 0.03, 0, 1))
      rig.nearPawY = lerp(3.5, 1.4, settle2)
      rig.nearPawRot = 1
    }

    const rightHitAge = t - 1.18
    const leftHitAge = t - 1.27
    const rightRipple = rightHitAge >= 0 && rightHitAge < 0.12 ? 1 - rightHitAge / 0.12 : 0
    const leftRipple = leftHitAge >= 0 && leftHitAge < 0.12 ? 1 - leftHitAge / 0.12 : 0
    const deskImpactY = rightRipple * 0.9 + leftRipple * 0.85
    const unzoomU = easeOutCubic(clamp((t - 1.0) / 0.14, 0, 1))

    return {
      ...baseFrame,
      rig,
      leftKeyDown: t >= 1.26,
      rightKeyDown: t >= 1.17 && t < 1.25,
      leftRipple,
      rightRipple,
      deskImpactY,
      stoolCompress: lerp(0.8, 1.4, leanOvershoot),
      stageZoom: lerp(1.042, 1, unzoomU),
      stagePanX: lerp(4.5, 0, unzoomU),
      stagePanY: lerp(2.5, 0, unzoomU),
      steamSway: Math.sin(whipU * Math.PI) * 3.5,
      screenPulse,
    }
  }

  // ---------------------------------------------------------------------------
  // 1.30 – 3.50 s: Rapid typing bursts (performative busy energy!), shoulders
  // bounce with the keystrokes, new lines appear on laptop in rhythm, then
  // winds down smoothly (2.92 – 3.50 s) to a gentle working pace.
  // ---------------------------------------------------------------------------
  if (t < 3.5) {
    rig.turn = 0
    rig.pose = 'profile'

    if (t < 2.92) {
      const inGap1 = t >= 1.76 && t < 1.86
      const inGap2 = t >= 2.34 && t < 2.44
      const inBurst = !inGap1 && !inGap2

      rig.torsoLeanDeg = 10.8 + (inBurst ? Math.sin(t * 36) * 0.45 : -0.5)
      rig.headTiltDeg = 5.5 + (inBurst ? Math.sin(t * 38) * 1.1 : -0.6)
      rig.eyeOffsetX = 1.45
      rig.eyeOffsetY = 0.9 + Math.sin(t * 14) * 0.25
      rig.eyeOpen = 0.9
      rig.browLift = -0.55
      rig.screenGlow = 0.55 + screenPulse * 0.42

      if (inBurst) {
        const phase = (t - 1.3) * Math.PI * 2 * 9.5
        const sNear = Math.sin(phase)
        const sFar = Math.sin(phase + Math.PI)

        rig.nearPawY = sNear > 0 ? sNear * 3.6 : sNear * 11.5
        rig.nearPawRot = sNear * 7.5
        rig.nearPawX = sNear * 1.5

        rig.farPawY = sFar > 0 ? sFar * 3.8 : sFar * 11.2
        rig.farPawRot = sFar * 7.5
        rig.farPawX = sFar * 1.5
        rig.pawBlur = 0.85

        // Shoulders, ears & headphones bounce rhythmically with the rapid keystrokes
        const doubleBeat = Math.sin(phase * 2)
        rig.shoulderBounceY = doubleBeat * 2.5
        rig.headphoneBounceY = Math.sin(phase * 2 - 0.5) * 1.25
        rig.earPerkDeg = Math.sin(phase * 2 - 0.8) * 5.5
        rig.torsoStretchY = 1 - doubleBeat * 0.016

        return {
          ...baseFrame,
          rig,
          leftKeyDown: sNear > 0.35,
          rightKeyDown: sFar > 0.35,
          leftRipple: Math.max(0, sNear - 0.45) * 1.6,
          rightRipple: Math.max(0, sFar - 0.45) * 1.6,
          deskImpactY: Math.max(0, doubleBeat) * 0.55,
          stoolCompress: 1.4 + doubleBeat * 0.45,
          typingIntensity: 1,
          steamSway: 1.2,
          screenPulse,
        }
      } else {
        rig.nearPawY = -6.5
        rig.nearPawRot = -6
        rig.farPawY = -4
        rig.farPawRot = -3.5
        rig.shoulderBounceY = -0.9
        rig.earPerkDeg = -4
        return {
          ...baseFrame,
          rig,
          stoolCompress: 1.1,
          typingIntensity: 0.25,
          screenPulse,
        }
      }
    } else {
      // 2.92 – 3.50 s: Smooth wind-down into gentle steady working rhythm
      const coolU = easeInOutCubic((t - 2.92) / 0.58)
      rig.torsoLeanDeg = lerp(10.8, 2.6, coolU)
      rig.headTiltDeg = lerp(5.5, 1.3, coolU)
      rig.eyeOffsetX = lerp(1.45, 1.1, coolU)
      rig.eyeOffsetY = lerp(0.9, 0.5, coolU)
      rig.eyeOpen = lerp(0.9, 1, coolU)
      rig.browLift = lerp(-0.55, 0, coolU)
      rig.screenGlow = lerp(0.55, 0.38, coolU)
      rig.pawBlur = lerp(0.6, 0, coolU)
      rig.blink = t > 3.22 && t < 3.34 ? Math.sin(((t - 3.22) / 0.12) * Math.PI) : 0

      const freq = lerp(6.2, 1.6, coolU)
      const ampUp = lerp(9, 4.2, coolU)
      const phase = (t - 2.92) * Math.PI * 2 * freq
      const sNear = Math.sin(phase)
      const sFar = Math.sin(phase + Math.PI)

      rig.nearPawY = sNear > 0 ? sNear * 2.2 : sNear * ampUp
      rig.nearPawRot = sNear * lerp(5.5, 2.5, coolU)
      rig.farPawY = sFar > 0 ? sFar * 2.4 : sFar * ampUp
      rig.farPawRot = sFar * lerp(5.5, 2.5, coolU)
      rig.shoulderBounceY = Math.sin(phase * 2) * lerp(1.8, 0.35, coolU)
      rig.headphoneBounceY = Math.sin(phase * 2 - 0.4) * lerp(0.9, 0.15, coolU)

      return {
        ...baseFrame,
        rig,
        leftKeyDown: sNear > 0.5,
        rightKeyDown: sFar > 0.5,
        stoolCompress: lerp(1.3, 0.3, coolU),
        typingIntensity: 1 - coolU,
        screenPulse: 0,
      }
    }
  }

  // ---------------------------------------------------------------------------
  // 3.50 s+: Lifelike gentle steady work rhythm for the remainder of the visit
  // (does not repeat the surprise reaction unless user leaves and re-enters).
  // ---------------------------------------------------------------------------
  const calmT = t - 3.5
  const phase = calmT * Math.PI * 2 * 1.35
  const sNear = Math.sin(phase)
  const sFar = Math.sin(phase + Math.PI)

  const blinkCycle = calmT % 3.4
  const periodicBlink =
    blinkCycle > 1.8 && blinkCycle < 1.94
      ? Math.sin(((blinkCycle - 1.8) / 0.14) * Math.PI)
      : 0
  const earCycle = calmT % 4.6
  const earTwitch =
    earCycle > 2.9 && earCycle < 3.12
      ? Math.sin(((earCycle - 2.9) / 0.22) * Math.PI) * -6
      : 0

  rig.turn = 0
  rig.pose = 'profile'
  rig.torsoLeanDeg = 2.6 + Math.sin(calmT * 2.1) * 0.35
  rig.headTiltDeg = 1.3 + Math.sin(calmT * 1.7) * 0.55
  rig.breath = (Math.sin(calmT * 2.1) + 1) * 0.45
  rig.eyeOffsetX = 1.1 + Math.sin(calmT * 1.1) * 0.25
  rig.eyeOffsetY = 0.45 + Math.sin(calmT * 0.85) * 0.35
  rig.eyeOpen = 1
  rig.blink = periodicBlink
  rig.earPerkDeg = earTwitch
  rig.screenGlow = 0.38 + Math.sin(calmT * 2.1) * 0.05
  rig.nearPawY = sNear > 0 ? sNear * 2 : sNear * 4.2
  rig.nearPawRot = sNear * 2.5
  rig.farPawY = sFar > 0 ? sFar * 2.1 : sFar * 4
  rig.farPawRot = sFar * 2.5
  rig.shoulderBounceY = Math.sin(calmT * 2.1) * 0.45

  return {
    ...baseFrame,
    rig,
    visibleLineCount: REPORT_LINES.length,
    chartProgress: 1,
    leftKeyDown: sNear > 0.65,
    rightKeyDown: sFar > 0.65,
    stoolCompress: 0.25 + Math.sin(calmT * 2.1) * 0.15,
    screenPulse: 0,
  }
}

export const ReportesDeskScene: React.FC<{ frameTime: number }> = ({ frameTime }) => {
  const elapsed = frameTime
  const frame = computeSceneFrame(elapsed)

  // Show up to 6 lines inside the screen window below the mini bar-chart, scrolling smoothly
  const maxVisibleRows = 6
  const visibleSlice = REPORT_LINES.slice(
    Math.max(0, frame.visibleLineCount - maxVisibleRows),
    frame.visibleLineCount,
  )

  const steamDrift1 = (elapsed * 0.55) % 1
  const steamDrift2 = (elapsed * 0.55 + 0.5) % 1

  return (
    <div
      className="w-full max-w-[342px] mx-auto select-none"
      data-testid="reportes-desk-scene"
      data-pose={frame.rig.pose}
      data-turn={(frame.rig.turn ?? 0).toFixed(2)}
      data-elapsed={elapsed.toFixed(2)}
    >
      <svg
        viewBox="0 0 342 252"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
        className="w-full h-auto overflow-visible"
        role="img"
        aria-label="Capibara de Franca preparando reportes frente a su laptop"
      >
        <defs>
          <linearGradient id="laptopScreenGlow" x1="238" y1="145" x2="150" y2="145" gradientUnits="userSpaceOnUse">
            <stop offset="0%" stopColor="#2457F5" stopOpacity={0.14 + frame.screenPulse * 0.14} />
            <stop offset="100%" stopColor="#2457F5" stopOpacity="0" />
          </linearGradient>
          <linearGradient id="studioWindowBeam" x1="90" y1="36" x2="220" y2="196" gradientUnits="userSpaceOnUse">
            <stop offset="0%" stopColor="#FFFFFF" stopOpacity="0.65" />
            <stop offset="100%" stopColor="#EBEEF5" stopOpacity="0" />
          </linearGradient>
        </defs>

        {/* Stage camera group: subtle focus-in during eye contact, zero movement of surrounding app UI */}
        <g
          transform={`translate(${(171 + frame.stagePanX).toFixed(2)}, ${(132 + frame.stagePanY).toFixed(2)}) scale(${frame.stageZoom.toFixed(4)}) translate(-171, -132)`}
        >
          {/* Layered immersive studio backdrop */}
          <ellipse cx="171" cy="134" rx="148" ry="97" fill="#EAEFF7" />
          <path d="M96 48 L184 42 L242 194 L132 194 Z" fill="url(#studioWindowBeam)" />
          <g opacity="0.45">
            <rect x="52" y="82" width="38" height="26" rx="6" fill="#F8FAFD" />
            <rect x="58" y="89" width="18" height="2.5" rx="1.25" fill="#CFD8EA" />
            <rect x="58" y="95" width="25" height="2.5" rx="1.25" fill="#DCE3F1" />
          </g>

          {/* Grounded floor shadow */}
          <ellipse cx="174" cy="224" rx="144" ry="9.5" fill="#DCE3F0" />

          {/* Volumetric screen glow cone illuminating workspace */}
          <path d="M229 112 L138 88 L142 186 L218 183 Z" fill="url(#laptopScreenGlow)" />

          {/* Minimalist low stool under the capybara (compresses subtly with weight) */}
          <g data-element="stool">
            <path d="M80 198 L73 224" stroke="#B9C3D9" strokeWidth="4.5" strokeLinecap="round" />
            <path d="M118 198 L125 224" stroke="#B9C3D9" strokeWidth="4.5" strokeLinecap="round" />
            <rect
              x="64"
              y={189 + frame.stoolCompress * 0.7}
              width="70"
              height={Math.max(7.5, 9.5 - frame.stoolCompress * 0.7)}
              rx="4.75"
              fill="#CFD7E7"
            />
            <rect
              x="66"
              y={189 + frame.stoolCompress * 0.7}
              width="66"
              height="3.6"
              rx="1.8"
              fill="#E3E9F5"
            />
          </g>

          {/* Desk, Laptop & Small Mug group (receives subtle physical micro-thump on paw impacts) */}
          <g transform={`translate(0, ${frame.deskImpactY.toFixed(2)})`}>
            <g data-element="desk">
              <path d="M160 197 L160 224" stroke="#C2CBE0" strokeWidth="5" strokeLinecap="round" />
              <path d="M304 197 L304 224" stroke="#C2CBE0" strokeWidth="5" strokeLinecap="round" />
              <rect x="144" y="189" width="180" height="8.5" rx="4.25" fill="#CBD4E6" />
              <rect x="144" y="189" width="180" height="3.8" rx="1.9" fill="#E3E8F3" />
            </g>

            <g data-element="laptop" transform="translate(-8, 0)">
              <ellipse cx="215" cy="189.5" rx="50" ry="2.5" fill="#0B1B53" opacity="0.15" />

              <rect
                x="169"
                y={frame.leftKeyDown ? 182.9 : 181.1}
                width="13"
                height="3"
                rx="1"
                fill="#2457F5"
              />
              <rect
                x="185"
                y={frame.rightKeyDown ? 182.3 : 180.4}
                width="13"
                height="3"
                rx="1"
                fill="#0B1B53"
              />
              <rect x="201" y="180.8" width="11" height="2.6" rx="1" fill="#6D7A9E" />

              {/* Crisp electric-blue key-impact ripples when paws strike */}
              {frame.leftRipple > 0.05 && (
                <path
                  d={`M168 ${179 - (1 - frame.leftRipple) * 4} Q175 ${175 - (1 - frame.leftRipple) * 5} 182 ${179 - (1 - frame.leftRipple) * 4}`}
                  stroke="#2457F5"
                  strokeWidth="1.6"
                  strokeLinecap="round"
                  opacity={frame.leftRipple * 0.85}
                />
              )}
              {frame.rightRipple > 0.05 && (
                <path
                  d={`M184 ${178 - (1 - frame.rightRipple) * 4} Q191 ${174 - (1 - frame.rightRipple) * 5} 198 ${178 - (1 - frame.rightRipple) * 4}`}
                  stroke="#2457F5"
                  strokeWidth="1.6"
                  strokeLinecap="round"
                  opacity={frame.rightRipple * 0.85}
                />
              )}

              <path d="M158 183.5 L224 181 L262 188.5 L168 189.2 Z" fill="#0B1B53" />
              <path d="M160 184 L224 182 L259 187.8 L169 188.2 Z" fill="#9AA7C4" />
              <path d="M261 188.5 L265.5 188 L277.5 113.5 L273 113.5 Z" fill="#9AA7C4" />
              <path d="M223 181.5 L262 188.5 L274 113.5 L234 107.5 Z" fill="#0B1B53" />
              <path
                d="M227 177.5 L258.5 183.2 L269.2 117.5 L237.5 112.5 Z"
                fill={frame.screenPulse > 0.2 ? '#F1F6FF' : '#FFFFFF'}
              />

              {/* Franca mini-report UI inside laptop screen */}
              <g transform="matrix(0.84, 0.14, -0.16, 0.97, 239.5, 117.2)">
                <rect x="0" y="0" width="31" height="11.5" rx="2" fill="#F2F5FC" />
                <circle cx="3.2" cy="3" r="1.1" fill="#2457F5" />
                <rect x="6" y="2.2" width="10" height="1.6" rx="0.8" fill="#0B1B53" opacity="0.45" />
                <rect
                  x="20"
                  y={9.5 - 3.5 * Math.max(0.35, frame.chartProgress)}
                  width="2.4"
                  height={3.5 * Math.max(0.35, frame.chartProgress)}
                  rx="0.8"
                  fill="#9DB5FF"
                />
                <rect
                  x="23.5"
                  y={9.5 - 5.5 * Math.max(0.25, frame.chartProgress)}
                  width="2.4"
                  height={5.5 * Math.max(0.25, frame.chartProgress)}
                  rx="0.8"
                  fill="#2457F5"
                />
                <rect
                  x="27"
                  y={9.5 - 7.2 * Math.max(0.2, frame.chartProgress)}
                  width="2.4"
                  height={7.2 * Math.max(0.2, frame.chartProgress)}
                  rx="0.8"
                  fill="#0B1B53"
                />

                {visibleSlice.map((line, idx) => {
                  const yPos = 15 + idx * 6.8
                  const isNewest =
                    idx === visibleSlice.length - 1 && frame.visibleLineCount > 3
                  const grow = isNewest ? frame.newestLineProgress : 1
                  return (
                    <g key={`${frame.visibleLineCount - visibleSlice.length + idx}`}>
                      <rect
                        x="0"
                        y={yPos}
                        width={Math.max(2, line.width * grow)}
                        height={line.accent ? 3.2 : 2.5}
                        rx="1.25"
                        fill={line.accent ? '#2457F5' : '#0B1B53'}
                        opacity={line.accent ? 0.96 : isNewest && elapsed < 3.1 ? 0.85 : 0.44}
                      />
                      {line.accent && grow > 0.7 && (
                        <rect
                          x={line.width + 2}
                          y={yPos}
                          width={Math.max(3, (30 - line.width - 2) * grow)}
                          height="3.2"
                          rx="1.25"
                          fill="#DCE5FF"
                        />
                      )}
                    </g>
                  )
                })}

                {visibleSlice.length > 0 && (
                  <rect
                    x={Math.min(
                      29.2,
                      visibleSlice[visibleSlice.length - 1].width *
                        (frame.visibleLineCount > 3 ? frame.newestLineProgress : 1) +
                        1.6,
                    )}
                    y={14.6 + (visibleSlice.length - 1) * 6.8}
                    width="1.6"
                    height="3.6"
                    rx="0.8"
                    fill="#2457F5"
                    opacity={elapsed < 3.0 || Math.floor(elapsed * 2.2) % 2 === 0 ? 1 : 0.15}
                  />
                )}
              </g>
            </g>

            {/* Small Mug on the Desk with warm curling steam & subtle desk-vibration ripple */}
            <g data-element="small-mug" transform="translate(-4, 0)">
              <path
                d={`M${(294 + frame.steamSway * steamDrift1).toFixed(2)} ${(168 - steamDrift1 * 13).toFixed(2)} C${(291 + frame.steamSway).toFixed(2)} ${(164 - steamDrift1 * 13).toFixed(2)}, ${(298 + frame.steamSway).toFixed(2)} ${(160 - steamDrift1 * 13).toFixed(2)}, ${(295 + frame.steamSway * 1.2).toFixed(2)} ${(155 - steamDrift1 * 13).toFixed(2)}`}
                stroke="#9AA7C4"
                strokeWidth="1.4"
                strokeLinecap="round"
                opacity={Math.sin(steamDrift1 * Math.PI) * 0.42}
              />
              <path
                d={`M${(299 + frame.steamSway * steamDrift2).toFixed(2)} ${(169 - steamDrift2 * 12).toFixed(2)} C${(302 + frame.steamSway).toFixed(2)} ${(165 - steamDrift2 * 12).toFixed(2)}, ${(296 + frame.steamSway).toFixed(2)} ${(161 - steamDrift2 * 12).toFixed(2)}, ${(299 + frame.steamSway * 1.2).toFixed(2)} ${(156 - steamDrift2 * 12).toFixed(2)}`}
                stroke="#9AA7C4"
                strokeWidth="1.3"
                strokeLinecap="round"
                opacity={Math.sin(steamDrift2 * Math.PI) * 0.36}
              />

              <ellipse cx="297" cy="189.2" rx="11" ry="1.8" fill="#0B1B53" opacity="0.14" />
              <path
                d="M305 175.5 C310.5 175.5, 310.5 184.5, 305 184.5"
                stroke="#FFFFFF"
                strokeWidth="2.8"
                strokeLinecap="round"
              />
              <path
                d="M305 175.5 C310.5 175.5, 310.5 184.5, 305 184.5"
                stroke="#CBD4E6"
                strokeWidth="1"
                strokeLinecap="round"
              />
              <path
                d="M287 172.5 L306 172.5 L305 186.5 C304.8 188.2, 303.4 189, 301.5 189 L291.5 189 C289.6 189, 288.2 188.2, 288 186.5 Z"
                fill="#FFFFFF"
              />
              <path
                d="M300 172.5 L306 172.5 L305 186.5 C304.8 188.2, 303.5 189, 301.5 189 L299.5 189 Z"
                fill="#E3E9F4"
              />
              <path
                d="M291 180.5 Q296.5 182.5 302 180.5"
                stroke="#2457F5"
                strokeWidth="2"
                strokeLinecap="round"
              />
              <ellipse cx="296.5" cy="172.5" rx="9.5" ry="2.2" fill="#E8EDF7" />
              <ellipse cx="296.5" cy="172.7" rx="7.6" ry="1.4" fill="#4A2B13" />
              {(frame.typingIntensity > 0.3 || frame.deskImpactY > 0.2) && (
                <ellipse
                  cx="296.5"
                  cy="172.7"
                  rx={3.5 + ((elapsed * 12) % 3.2)}
                  ry="0.7"
                  stroke="#D9A87E"
                  strokeWidth="0.7"
                  opacity="0.55"
                />
              )}
            </g>
          </g>

          {/* Animated Capybara Mascot (2.5D Continuous Rig) */}
          <g transform={`translate(16, ${(31 + frame.stoolCompress * 0.55).toFixed(2)})`}>
            <CapybaraMascot rig={frame.rig} />
          </g>

          {/* Kinetic typing speed-arcs above paws during high-speed bursts */}
          {frame.typingIntensity > 0.5 && (
            <g opacity={frame.typingIntensity * 0.65}>
              <path
                d="M161 159 C165 153, 173 153, 176 160"
                stroke="#2457F5"
                strokeWidth="1.5"
                strokeLinecap="round"
                strokeDasharray="4 4"
                strokeDashoffset={-((elapsed * 65) % 8)}
              />
              <path
                d="M178 156 C182 150, 190 150, 193 157"
                stroke="#0B1B53"
                strokeWidth="1.4"
                strokeLinecap="round"
                strokeDasharray="4 4"
                strokeDashoffset={(elapsed * 65) % 8}
                opacity="0.5"
              />
            </g>
          )}
        </g>
      </svg>
    </div>
  )
}
