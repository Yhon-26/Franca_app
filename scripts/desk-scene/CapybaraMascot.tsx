import React from 'react'

export type MascotPose = 'profile' | 'threeQuarter' | 'nearFront'

export interface MascotRigState {
  pose: MascotPose
  /**
   * Continuous 2.5D turnaround angle:
   * 0 = profile (de perfil), 0.5 = threeQuarter (tres cuartos), 1 = nearFront (casi de frente).
   * If omitted, derived automatically from `pose`.
   */
  turn?: number
  /** Gaze shift in SVG px (-left/viewer, +right/laptop) */
  eyeOffsetX: number
  eyeOffsetY: number
  /** 1 = neutral calm, >1.2 = widened surprise, <0.95 = focused squint */
  eyeOpen: number
  /** 0 = open eyes, 1 = closed blink */
  blink: number
  /** Eyebrow lift in px (+upward) */
  browLift: number
  /** 0..1 surprised mouth 'o' & subtle cheek blush intensity */
  surpriseExpression: number
  /** Independent ear rotation follow-through in degrees (+back/droop, -perk up) */
  earPerkDeg: number
  /** Secondary spring bounce on headphones in px */
  headphoneBounceY: number
  /** Head tilt in degrees (+clockwise/down toward laptop) */
  headTiltDeg: number
  headOffsetX: number
  headOffsetY: number
  /** Volume-preserving head squash/stretch (>1 = vertical stretch, <1 = vertical squash) */
  headStretchY: number
  /** Forward torso pitch toward laptop in degrees */
  torsoLeanDeg: number
  /** Volume-preserving torso squash/stretch */
  torsoStretchY: number
  /** Subtle breathing chest expansion in px */
  breath: number
  /** 0..1 upward shoulder shrug */
  shoulderShrug: number
  /** Rhythmic vertical bounce on shoulders/upper torso in px */
  shoulderBounceY: number
  /** Articulated near front paw offsets & rotation */
  nearPawX: number
  nearPawY: number
  nearPawRot: number
  /** Articulated far front paw offsets & rotation */
  farPawX: number
  farPawY: number
  farPawRot: number
  /** 0..1 motion-smear trail opacity on rapid paw downstrokes */
  pawBlur: number
  /** 0..1 intensity of cool blue-white laptop screen light on snout/chest */
  screenGlow: number
}

export const DEFAULT_RIG_STATE: MascotRigState = {
  pose: 'profile',
  eyeOffsetX: 0,
  eyeOffsetY: 0,
  eyeOpen: 1,
  blink: 0,
  browLift: 0,
  surpriseExpression: 0,
  earPerkDeg: 0,
  headphoneBounceY: 0,
  headTiltDeg: 0,
  headOffsetX: 0,
  headOffsetY: 0,
  headStretchY: 1,
  torsoLeanDeg: 2,
  torsoStretchY: 1,
  breath: 0,
  shoulderShrug: 0,
  shoulderBounceY: 0,
  nearPawX: 0,
  nearPawY: 0,
  nearPawRot: 0,
  farPawX: 0,
  farPawY: 0,
  farPawRot: 0,
  pawBlur: 0,
  screenGlow: 0.35,
}

export interface CapybaraMascotProps {
  pose?: MascotPose
  rig?: Partial<MascotRigState>
  smile?: boolean
  /** Visualizes the separated rig groups (head, torso, paws) in the pose sheet */
  showLayerGuides?: boolean
  className?: string
  ariaLabel?: string
}

const clamp = (v: number, min: number, max: number) => Math.min(max, Math.max(min, v))

/**
 * Smooth 3-keyframe interpolator across turn in [0, 1]:
 * turn = 0   -> exact vProfile
 * turn = 0.5 -> exact vThreeQuarter
 * turn = 1   -> exact vNearFront
 */
function k3(vProfile: number, vThreeQuarter: number, vNearFront: number, turn: number): number {
  const t = clamp(turn, 0, 1)
  if (t <= 0.5) {
    const u = t * 2
    const s = u * u * (3 - 2 * u)
    return vProfile + (vThreeQuarter - vProfile) * s
  }
  const u = (t - 0.5) * 2
  const s = u * u * (3 - 2 * u)
  return vThreeQuarter + (vNearFront - vThreeQuarter) * s
}

function poseToTurn(pose: MascotPose): number {
  if (pose === 'profile') return 0
  if (pose === 'threeQuarter') return 0.5
  return 1
}

function n(val: number): string {
  return val.toFixed(2)
}

/**
 * Original mascot of Franca:
 * Adult caramel capybara with broad snout, small ears, and signature electric-blue (#2457F5)
 * headphones featuring a curved white line on the earcup.
 * Supports both discrete 3-pose reuse ('profile' | 'threeQuarter' | 'nearFront') and
 * silky-smooth 60fps continuous 2.5D rotation via `rig.turn` (0..1).
 */
export const CapybaraMascot: React.FC<CapybaraMascotProps> = ({
  pose,
  rig,
  smile = false,
  showLayerGuides = false,
  className = '',
  ariaLabel,
}) => {
  const state: MascotRigState = {
    ...DEFAULT_RIG_STATE,
    ...rig,
    pose: pose ?? rig?.pose ?? DEFAULT_RIG_STATE.pose,
  }

  const turn =
    pose !== undefined
      ? poseToTurn(pose)
      : state.turn !== undefined
        ? clamp(state.turn, 0, 1)
        : poseToTurn(state.pose)

  const shrugDy = -8.5 * state.shoulderShrug + state.shoulderBounceY
  const torsoScaleY = state.torsoStretchY
  const torsoScaleX = 1 / Math.sqrt(Math.max(0.85, torsoScaleY))

  const headScaleY = state.headStretchY
  const headScaleX = 1 / Math.sqrt(Math.max(0.85, headScaleY))

  const headLeanShiftX = state.torsoLeanDeg * 0.72
  const headLeanShiftY = Math.abs(state.torsoLeanDeg) * 0.22
  const totalHeadX = state.headOffsetX + headLeanShiftX
  const totalHeadY =
    state.headOffsetY +
    headLeanShiftY +
    state.shoulderShrug * 1.6 +
    state.shoulderBounceY * 0.65

  // =========================================================================
  // CONTINUOUS 2.5D TORSO GEOMETRY (profile -> threeQuarter -> nearFront)
  // =========================================================================
  const torsoD = `
    M ${n(k3(52, 54, 62, turn))} ${n(k3(148, 148, 149, turn))}
    C ${n(k3(48, 50, 58, turn))} 125, ${n(k3(58, 60, 65, turn))} 96, ${n(k3(78, 79, 81, turn))} ${n(k3(84, 84, 86, turn))}
    C ${n(k3(93, 95, 94, turn))} ${n(k3(78, 78, 80, turn))}, ${n(k3(110, 114, 109, turn))} ${n(k3(83, 84, 80, turn))}, ${n(k3(118, 122, 120, turn) + state.breath * 0.6)} ${n(k3(98, 100, 88, turn))}
    C ${n(k3(127, 130, 132, turn) + state.breath)} ${n(k3(114, 116, 98, turn))}, ${n(k3(129, 129, 134, turn) + state.breath)} ${n(k3(136, 138, 126, turn))}, ${n(k3(120, 119, 126, turn))} ${n(k3(153, 154, 151, turn))}
    C ${n(k3(113, 110, 119, turn))} 160, ${n(k3(64, 65, 70, turn))} 160, ${n(k3(52, 54, 62, turn))} ${n(k3(148, 148, 149, turn))}
    Z
  `

  const bibD = `
    M ${n(k3(111, 98, 82, turn))} ${n(k3(95, 94, 96, turn))}
    C ${n(k3(116, 108, 91, turn))} ${n(k3(94, 93, 91, turn))}, ${n(k3(121, 119, 109, turn) + state.breath * 0.7)} ${n(k3(103, 99, 91, turn))}, ${n(k3(123, 122, 117, turn) + state.breath)} ${n(k3(116, 114, 97, turn))}
    C ${n(k3(125, 123, 123, turn) + state.breath)} ${n(k3(131, 132, 116, turn))}, ${n(k3(121, 118, 122, turn))} ${n(k3(145, 147, 138, turn))}, ${n(k3(116, 109, 115, turn))} ${n(k3(150, 153, 151, turn))}
    C ${n(k3(110, 99, 104, turn))} ${n(k3(152, 154, 155, turn))}, ${n(k3(106, 92, 91, turn))} ${n(k3(146, 147, 154, turn))}, ${n(k3(106, 91, 83, turn))} ${n(k3(134, 132, 150, turn))}
    C ${n(k3(106, 91, 76, turn))} ${n(k3(118, 114, 133, turn))}, ${n(k3(107, 94, 76, turn))} ${n(k3(103, 99, 108, turn))}, ${n(k3(111, 98, 82, turn))} ${n(k3(95, 94, 96, turn))}
    Z
  `

  const shouldersD = `
    M ${n(k3(74, 70, 63, turn))} ${n(k3(93, 96, 103, turn))}
    C ${n(k3(79, 76, 65, turn))} ${n(k3(81, 82, 87, turn))}, ${n(k3(105, 111, 131, turn))} ${n(k3(81, 82, 87, turn))}, ${n(k3(115, 119, 132, turn))} ${n(k3(94, 96, 103, turn))}
    C ${n(k3(118, 122, 133, turn))} ${n(k3(102, 105, 112, turn))}, ${n(k3(111, 112, 119, turn))} ${n(k3(109, 111, 112, turn))}, ${n(k3(98, 97, 99, turn))} ${n(k3(109, 110, 106, turn))}
    C ${n(k3(85, 81, 79, turn))} ${n(k3(109, 111, 112, turn))}, ${n(k3(71, 68, 62, turn))} ${n(k3(101, 104, 111, turn))}, ${n(k3(74, 70, 63, turn))} ${n(k3(93, 96, 103, turn))}
    Z
  `

  // =========================================================================
  // CONTINUOUS 2.5D HEAD, SNOUT, EARS & HEADPHONES GEOMETRY
  // =========================================================================
  const headD = `
    M ${n(k3(72, 68, 68, turn))} ${n(k3(72, 68, 67, turn))}
    C ${n(k3(69, 66, 68, turn))} ${n(k3(57, 51, 49, turn))}, ${n(k3(75, 76, 79, turn))} ${n(k3(43, 42, 41, turn))}, ${n(k3(93, 96, 99, turn))} ${n(k3(42, 42, 41, turn))}
    C ${n(k3(111, 114, 119, turn))} ${n(k3(41.5, 42, 41, turn))}, ${n(k3(132, 127, 130, turn))} ${n(k3(44.5, 46.5, 49, turn))}, ${n(k3(145, 136, 130, turn))} ${n(k3(48, 52, 67, turn))}
    C ${n(k3(153, 142, 130, turn))} ${n(k3(51, 57, 79, turn))}, ${n(k3(153, 141, 120, turn))} ${n(k3(75, 78, 88, turn))}, ${n(k3(137, 122, 99, turn))} ${n(k3(84, 86, 88, turn))}
    C ${n(k3(121, 103, 79, turn))} ${n(k3(86.5, 88, 88, turn))}, ${n(k3(77, 71, 68, turn))} ${n(k3(85, 81, 79, turn))}, ${n(k3(72, 68, 68, turn))} ${n(k3(72, 68, 67, turn))}
    Z
  `

  const snoutD = `
    M ${n(k3(126, 111, 84, turn))} ${n(k3(45.5, 50.5, 55, turn))}
    C ${n(k3(134, 119, 92, turn))} ${n(k3(46, 48, 51.5, turn))}, ${n(k3(142, 130, 109, turn))} ${n(k3(47, 48.5, 51.5, turn))}, ${n(k3(146, 136, 117, turn))} ${n(k3(49, 52, 55, turn))}
    C ${n(k3(152.5, 141.5, 120, turn))} ${n(k3(52.5, 56.5, 60, turn))}, ${n(k3(152.5, 141, 120, turn))} ${n(k3(74, 76, 78, turn))}, ${n(k3(141, 130, 114, turn))} ${n(k3(83, 84.5, 85, turn))}
    C ${n(k3(132, 119, 105, turn))} ${n(k3(85, 86, 86.5, turn))}, ${n(k3(124, 109, 95, turn))} ${n(k3(82, 83, 86.5, turn))}, ${n(k3(122.5, 107.5, 87, turn))} ${n(k3(74, 74, 84.5, turn))}
    C ${n(k3(121, 106.5, 82, turn))} ${n(k3(64, 63, 78, turn))}, ${n(k3(121.5, 107.5, 81.5, turn))} ${n(k3(52, 54, 60, turn))}, ${n(k3(126, 111, 84, turn))} ${n(k3(45.5, 50.5, 55, turn))}
    Z
  `

  // Ears parameters
  const nearEarX = k3(77, 70, 73, turn)
  const nearEarY = k3(45.5, 46.5, 45.5, turn)
  const nearEarRot = k3(-26, -28, -24, turn) + state.earPerkDeg

  const farEarOpacity = clamp((turn - 0.14) / 0.24, 0, 1)
  const farEarX = k3(98, 118, 125, turn)
  const farEarY = k3(45, 44.5, 45.5, turn)
  const farEarScale = k3(0.45, 0.92, 1, turn)
  const farEarRot = k3(8, 18, 22, turn) - state.earPerkDeg * 0.8

  // Eyes parameters
  const nearEyeX = k3(113, 96.5, 79.5, turn)
  const nearEyeY = k3(56.5, 56.5, 56, turn)
  const farEyeOpacity = clamp((turn - 0.16) / 0.22, 0, 1)
  const farEyeX = k3(135, 124.5, 120, turn)
  const farEyeY = k3(55.5, 54.8, 56, turn)
  const farEyeScaleX = k3(0.3, 0.78, 0.96, turn)

  const openFactor = state.eyeOpen * (1 - clamp(state.blink, 0, 1) * 0.92)

  // Nostrils & Mouth
  const leftNostrilX = k3(142, 119.5, 92, turn)
  const leftNostrilY = k3(56.5, 58.5, 60.5, turn)
  const rightNostrilOpacity = clamp((turn - 0.15) / 0.25, 0, 1)
  const rightNostrilX = k3(149, 134.5, 109.5, turn)
  const rightNostrilY = k3(57, 58.5, 60.5, turn)

  // Headphones parameters (with secondary bounce)
  const hpDy = state.headphoneBounceY
  const bandLeftX = k3(90, 74, 66, turn)
  const bandLeftY = k3(54, 58, 62, turn) + hpDy
  const bandRightX = k3(98, 124, 132, turn)
  const bandRightY = k3(54, 57, 62, turn) + hpDy
  const bandCtrlLeftX = k3(89, 76, 67, turn)
  const bandCtrlRightX = k3(99, 119, 131, turn)
  const bandPeakY = k3(36, 34, 31.5, turn) + hpDy * 1.15

  const nearCupCushionX = k3(91.5, 75.5, 68.5, turn)
  const nearCupShellX = k3(89.8, 72.5, 65, turn)
  const nearCupY = k3(63.5, 64, 64.5, turn) + hpDy
  const nearCupRx = k3(9.8, 6.8, 5.8, turn)
  const nearCupRy = k3(13.2, 13.2, 13, turn)
  // Signature curved white line flips smoothly from side-view arc to outer-edge foreshortened arc
  const nearArcStartX = k3(87.5, 73.5, 65.5, turn)
  const nearArcCtrlX = k3(94, 68.8, 61.5, turn)

  const farCupOpacity = clamp((turn - 0.18) / 0.22, 0, 1)
  const farCupCushionX = k3(108, 123.5, 129.5, turn)
  const farCupShellX = k3(110, 125.5, 132.8, turn)
  const farCupY = k3(62.5, 62.5, 64.5, turn) + hpDy
  const farCupRx = k3(2, 4.5, 5.4, turn)
  const farCupRy = k3(10, 11.4, 12.7, turn)

  // Paws 2.5D horizontal foreshortening across turn
  const farPawReachX = k3(165, 160, 145, turn)
  const nearPawReachX = k3(151, 146, 123, turn)
  const nearPawShoulderX = k3(93, 90, 81, turn)

  const screenRimOpacity = clamp(state.screenGlow * (1 - turn * 0.65), 0, 0.9)

  return (
    <svg
      width="210"
      height="190"
      viewBox="0 0 210 190"
      fill="none"
      overflow="visible"
      xmlns="http://www.w3.org/2000/svg"
      className={className}
      role={ariaLabel ? 'img' : 'presentation'}
      aria-label={ariaLabel}
    >
      {/* =========================================================
          LAYER 1: FAR FRONT PAW (Separated behind torso)
          ========================================================= */}
      <g
        data-part="far-paw"
        transform={`translate(${state.farPawX + state.torsoLeanDeg * 0.35}, ${state.farPawY}) rotate(${state.farPawRot}, 118, 118)`}
      >
        {/* Kinetic motion smear ghost during rapid typing bursts */}
        {state.pawBlur > 0.05 && (
          <path
            d={`M114 111 C131 110, 149 116, ${n(farPawReachX)} 128 C${n(farPawReachX + 4)} 132, ${n(farPawReachX + 2)} 139, ${n(farPawReachX - 5)} 139 Z`}
            fill="#B8763C"
            opacity={state.pawBlur * 0.28}
          />
        )}
        <path
          d={`M114 111 C129 114, ${n(farPawReachX - 14)} 124, ${n(farPawReachX)} 137 C${n(farPawReachX + 5)} 141, ${n(farPawReachX + 3)} 148, ${n(farPawReachX - 5)} 148 C${n(farPawReachX - 18)} 148, 128 138, 112 126 Z`}
          fill="#9E612B"
        />
        <path
          d={`M${n(farPawReachX - 10)} 138 C${n(farPawReachX - 3)} 137, ${n(farPawReachX + 4)} 140, ${n(farPawReachX + 4)} 145 C${n(farPawReachX + 4)} 148.5, ${n(farPawReachX - 3)} 149, ${n(farPawReachX - 11)} 148 C${n(farPawReachX - 13)} 145, ${n(farPawReachX - 13)} 140, ${n(farPawReachX - 10)} 138 Z`}
          fill="#4E2C14"
        />
        <path
          d={`M${n(farPawReachX - 3)} 144 L${n(farPawReachX + 3)} 144.5`}
          stroke="#361D0B"
          strokeWidth="1.2"
          strokeLinecap="round"
        />
        {showLayerGuides && (
          <rect
            x="112"
            y="110"
            width="60"
            height="41"
            rx="8"
            stroke="#2457F5"
            strokeWidth="1.2"
            strokeDasharray="3 3"
          />
        )}
      </g>

      {/* =========================================================
          LAYER 2: TORSO & SEATED HINDQUARTERS (Separated body + shoulders)
          ========================================================= */}
      <g
        data-part="torso"
        transform={`translate(94, 154) rotate(${state.torsoLeanDeg}) scale(${n(torsoScaleX)}, ${n(torsoScaleY)}) translate(-94, -154)`}
      >
        {/* Seated back haunch visible in nearFront */}
        {turn > 0.45 && (
          <path
            d="M53 148 C51 134, 60 123, 71 126 L75 156 L57 156 Z"
            fill="#A86931"
            opacity={clamp((turn - 0.45) / 0.35, 0, 1)}
          />
        )}

        {/* Main adult capybara body */}
        <path d={torsoD} fill="#C68446" />

        {/* Warm sunlit dorsal fur highlight */}
        <path
          d={`M${n(k3(55, 57, 65, turn))} 136 C${n(k3(54, 56, 63, turn))} 115, ${n(k3(63, 65, 70, turn))} 94, ${n(k3(80, 81, 84, turn))} 86 C87 83, 93 83, 97 85 C${n(k3(81, 80, 76, turn))} 92, ${n(k3(67, 68, 70, turn))} 113, ${n(k3(65, 66, 69, turn))} 139 Z`}
          fill="#D99B5E"
          opacity={k3(1, 0.9, 0.55, turn)}
        />

        {/* Cream-caramel chest bib */}
        <path d={bibD} fill="#DFAC76" />

        {/* Subtle laptop screen cool rim light on front chest */}
        {screenRimOpacity > 0.05 && (
          <path
            d={`M${n(k3(117, 119, 120, turn) + state.breath)} 101 C${n(k3(125, 126, 126, turn) + state.breath)} 116, ${n(k3(126, 125, 125, turn) + state.breath)} 134, ${n(k3(119, 117, 119, turn))} 148`}
            stroke="#BFD3FF"
            strokeWidth="2.4"
            strokeLinecap="round"
            opacity={screenRimOpacity * 0.65}
          />
        )}

        {/* Seated hind haunch & tucked hind feet */}
        {turn < 0.85 && (
          <g opacity={clamp(1 - (turn - 0.55) / 0.3, 0, 1)}>
            <path
              d={`M56 147.5 C60 133.5, 76 130, ${n(k3(92, 88, 84, turn))} 139 C99 144, 103 152, ${n(k3(103, 98, 94, turn))} 157 L58 157 Z`}
              fill="#B37237"
            />
            <path
              d={`M${n(k3(94, 89, 84, turn))} 151.5 C${n(k3(103, 98, 92, turn))} 150.5, ${n(k3(112, 106, 99, turn))} 152.5, ${n(k3(113, 107, 100, turn))} 157.5 C${n(k3(113, 107, 100, turn))} 159.5, ${n(k3(102, 97, 91, turn))} 160, ${n(k3(92, 88, 83, turn))} 158 Z`}
              fill="#5C361A"
            />
          </g>
        )}

        {turn > 0.55 && (
          <g opacity={clamp((turn - 0.55) / 0.3, 0, 1)}>
            <path d="M72 153 C79 151, 86 153, 87 158 L70 158 Z" fill="#5C361A" />
            <path d="M111 153 C118 151, 125 153, 126 158 L109 158 Z" fill="#5C361A" />
          </g>
        )}

        {/* Articulated upper-shoulder mantle (shrugs up in surprise & bounces on rapid typing) */}
        <g transform={`translate(0, ${shrugDy})`}>
          <path d={shouldersD} fill="#C68446" />
          {turn > 0.45 && (
            <g opacity={clamp((turn - 0.45) / 0.35, 0, 1)}>
              <path
                d="M67 97 C71 89, 80 86, 88 86"
                stroke="#D99B5E"
                strokeWidth="2.5"
                strokeLinecap="round"
              />
              <path
                d="M128 97 C124 89, 116 86, 109 86"
                stroke="#D99B5E"
                strokeWidth="2.5"
                strokeLinecap="round"
              />
            </g>
          )}
          <path
            d={`M${n(k3(82, 80, 81, turn))} ${n(k3(88, 89, 90, turn))} C92 ${n(k3(92, 94, 95, turn))}, 106 ${n(k3(92, 94, 95, turn))}, ${n(k3(114, 115, 117, turn))} ${n(k3(88, 89, 90, turn))}`}
            stroke="#9C5E29"
            strokeWidth="2.4"
            strokeLinecap="round"
          />
        </g>

        {showLayerGuides && (
          <rect
            x="49"
            y="78"
            width="86"
            height="83"
            rx="12"
            stroke="#0B1B53"
            strokeWidth="1.2"
            strokeDasharray="4 3"
          />
        )}
      </g>

      {/* =========================================================
          LAYER 3: HEAD, SNOUT, EARS & SIGNATURE HEADPHONES
          ========================================================= */}
      <g
        data-part="head"
        transform={`translate(${n(totalHeadX + 98)}, ${n(totalHeadY + 80)}) rotate(${state.headTiltDeg}) scale(${n(headScaleX)}, ${n(headScaleY)}) translate(-98, -80)`}
      >
        {/* Soft shadow cast by head onto neck */}
        <ellipse cx={k3(101, 99, 99, turn)} cy="87.5" rx="21" ry="5.2" fill="#9A5D28" opacity="0.55" />

        {/* Far small ear (emerges smoothly on upper-right contour during turn) */}
        {farEarOpacity > 0.02 && (
          <g
            opacity={farEarOpacity}
            transform={`translate(${n(farEarX)}, ${n(farEarY)}) rotate(${n(farEarRot)}) scale(${n(farEarScale)})`}
          >
            <ellipse cx="0" cy="-1" rx="5.8" ry="8.4" fill="#6E3D19" />
            <ellipse cx="-0.3" cy="0" rx="3" ry="4.7" fill="#4A260D" />
          </g>
        )}

        {/* Far Headphone Cup (emerges behind right temple in 3/4 and near-front) */}
        {farCupOpacity > 0.02 && (
          <g opacity={farCupOpacity}>
            <ellipse cx={farCupCushionX} cy={farCupY} rx={farCupRx} ry={farCupRy + 0.5} fill="#0B1B53" />
            <ellipse cx={farCupShellX} cy={farCupY} rx={farCupRx} ry={farCupRy} fill="#2457F5" />
            <path
              d={`M${n(farCupShellX + 0.2)} ${n(farCupY - 7)} C${n(farCupShellX + 3.2)} ${n(farCupY - 3)}, ${n(farCupShellX + 3.2)} ${n(farCupY + 3)}, ${n(farCupShellX + 0.2)} ${n(farCupY + 7)}`}
              stroke="#FFFFFF"
              strokeWidth="2"
              strokeLinecap="round"
            />
          </g>
        )}

        {/* Over-head Headband Arch (rendered behind skull when open in 3/4 & near-front) */}
        {turn >= 0.28 && (
          <g>
            <path
              d={`M${n(bandLeftX)} ${n(bandLeftY)} C${n(bandCtrlLeftX)} ${n(bandPeakY)}, ${n(bandCtrlRightX)} ${n(bandPeakY)}, ${n(bandRightX)} ${n(bandRightY)}`}
              stroke="#2457F5"
              strokeWidth="6.6"
              strokeLinecap="round"
            />
            <path
              d={`M${n(bandLeftX + 2.5)} ${n(bandLeftY - 5)} C${n(bandCtrlLeftX + 6)} ${n(bandPeakY + 2.5)}, ${n(bandCtrlRightX - 6)} ${n(bandPeakY + 2.5)}, ${n(bandRightX - 2.5)} ${n(bandRightY - 5)}`}
              stroke="#5A82FF"
              strokeWidth="1.8"
              strokeLinecap="round"
            />
          </g>
        )}

        {/* Near small rounded-teardrop ear (with expressive follow-through rotation!) */}
        <g transform={`translate(${n(nearEarX)}, ${n(nearEarY)}) rotate(${n(nearEarRot)})`}>
          <ellipse cx="0" cy="-1" rx="6.6" ry="9" fill="#7D461E" />
          <ellipse cx="0.4" cy="0" rx="3.4" ry="5.2" fill="#542C10" />
        </g>

        {/* Adult Capybara Head Silhouette */}
        <path d={headD} fill="#C68446" />

        {/* Crown & Forehead Bridge Warm Highlight */}
        <path
          d={`M${n(k3(83, 80, 82, turn))} 44.5 C${n(k3(101, 95, 93, turn))} 42.5, ${n(k3(123, 112, 106, turn))} 44, ${n(k3(140, 123, 117, turn))} ${n(k3(47.8, 48, 44.5, turn))} C${n(k3(136, 115, 111, turn))} 50, ${n(k3(114, 96, 88, turn))} 49.5, ${n(k3(85, 81, 82, turn))} 48.5 Z`}
          fill="#D99B5E"
        />

        {/* Broad Toasted Cocoa-Caramel Snout Block */}
        <path d={snoutD} fill="#9B5D2E" />
        <path
          d={`M${n(k3(132, 114, 89, turn))} ${n(k3(49, 53, 55.5, turn))} C${n(k3(140, 121, 96, turn))} ${n(k3(49.5, 51, 53.5, turn))}, ${n(k3(146, 130, 105, turn))} ${n(k3(52, 51.5, 53.5, turn))}, ${n(k3(148, 135, 112, turn))} ${n(k3(57.5, 54.5, 55.5, turn))}`}
          stroke="#B3713E"
          strokeWidth="2"
          strokeLinecap="round"
        />

        {/* Laptop Screen Cool Rim Highlight on Front of Snout */}
        {screenRimOpacity > 0.05 && (
          <path
            d={`M${n(k3(145, 136, 120, turn))} ${n(k3(49.5, 53, 58, turn))} C${n(k3(151.5, 140.5, 121, turn))} ${n(k3(54, 59, 64, turn))}, ${n(k3(151, 140, 120, turn))} ${n(k3(71, 73, 75, turn))}, ${n(k3(142, 132, 116, turn))} ${n(k3(81, 82, 82, turn))}`}
            stroke="#C7D8FF"
            strokeWidth="2.2"
            strokeLinecap="round"
            opacity={screenRimOpacity * 0.8}
          />
        )}

        {/* Subtle warm bashful cheek blush during surprised eye-contact */}
        {state.surpriseExpression > 0.05 && (
          <g opacity={state.surpriseExpression * 0.34}>
            <ellipse cx={k3(107, 89, 75, turn)} cy="67.5" rx="6.2" ry="3.8" fill="#E26D5A" />
            {turn > 0.35 && (
              <ellipse cx={k3(132, 129, 123, turn)} cy="67.5" rx="5.5" ry="3.6" fill="#E26D5A" />
            )}
          </g>
        )}

        {/* Nostrils */}
        <ellipse
          cx={leftNostrilX}
          cy={leftNostrilY}
          rx="3"
          ry="2.15"
          transform={`rotate(-12, ${n(leftNostrilX)}, ${n(leftNostrilY)})`}
          fill="#3B200E"
        />
        {rightNostrilOpacity > 0.02 && (
          <ellipse
            cx={rightNostrilX}
            cy={rightNostrilY}
            rx={k3(1.8, 2.2, 2.9, turn)}
            ry="2.1"
            opacity={rightNostrilOpacity}
            transform={`rotate(12, ${n(rightNostrilX)}, ${n(rightNostrilY)})`}
            fill="#3B200E"
          />
        )}

        {/* Mouth & Capybara Philtrum (morphs from profile lip to front Y-philtrum + surprised 'o') */}
        {turn < 0.25 ? (
          <path
            d="M150.5 71.5 C145 72.5, 139 74, 134 72.5"
            stroke="#3B200E"
            strokeWidth="2.1"
            strokeLinecap="round"
          />
        ) : (
          <g opacity={clamp((turn - 0.15) / 0.2, 0, 1)}>
            <path
              d={`M${n(k3(144, 128.5, 101, turn))} 63.5 L${n(k3(143, 128, 101, turn))} ${n(75 - state.surpriseExpression * 1.5)}`}
              stroke="#3B200E"
              strokeWidth="2"
              strokeLinecap="round"
            />
            {state.surpriseExpression > 0.45 ? (
              <ellipse
                cx={k3(142, 128, 101, turn)}
                cy="78"
                rx={2.4 + state.surpriseExpression * 1.1}
                ry={1.6 + state.surpriseExpression * 1.4}
                fill="#3B200E"
              />
            ) : (
              <path
                d={`M${n(k3(137, 122, 94.5, turn))} ${smile ? 74 : 78} Q${n(k3(143, 128, 101, turn))} ${smile ? 81 : 74.2} ${n(k3(148, 133.5, 107.5, turn))} ${smile ? 74 : 78}`}
                stroke="#3B200E"
                strokeWidth="2"
                strokeLinecap="round"
              />
            )}
          </g>
        )}

        {/* Near Eye (Left) with dynamic pupil, blink eyelid & catchlights */}
        <g transform={`translate(${n(nearEyeX)}, ${n(nearEyeY)})`}>
          {state.blink > 0.82 ? (
            <path d="M-4.8 0.5 Q 0 2.4 4.8 0.5" stroke="#3B200E" strokeWidth="2.2" strokeLinecap="round" />
          ) : (
            <g>
              <ellipse
                cx="0"
                cy="0"
                rx={4.9 * Math.max(0.95, state.eyeOpen * 0.97)}
                ry={Math.max(0.8, 4.5 * openFactor)}
                fill="#FFFFFF"
                opacity={
                  turn > 0.2 || Math.abs(state.eyeOffsetX) > 0.8 || state.eyeOpen > 1.08 ? 0.96 : 0.28
                }
              />
              <ellipse
                cx={state.eyeOffsetX}
                cy={state.eyeOffsetY}
                rx="3.65"
                ry={Math.max(0.8, 3.8 * Math.min(1.12, openFactor))}
                fill="#0B1B53"
              />
              <circle cx={state.eyeOffsetX + 1.15} cy={state.eyeOffsetY - 1.25} r="1.25" fill="#FFFFFF" />
              {state.eyeOpen > 1.2 && (
                <circle cx={state.eyeOffsetX - 1.3} cy={state.eyeOffsetY + 1.2} r="0.65" fill="#FFFFFF" opacity="0.85" />
              )}
            </g>
          )}
          <path
            d={`M-5.1 ${n(-4 - state.browLift)} Q 0 ${n(-6.4 - state.browLift)} 5 ${n(-3.7 - state.browLift)}`}
            stroke="#5A3317"
            strokeWidth="2"
            strokeLinecap="round"
          />
        </g>

        {/* Far Eye (Right) emerging smoothly in 3/4 and near-front */}
        {farEyeOpacity > 0.02 && (
          <g
            opacity={farEyeOpacity}
            transform={`translate(${n(farEyeX)}, ${n(farEyeY)}) scale(${n(farEyeScaleX)}, 1)`}
          >
            {state.blink > 0.82 ? (
              <path d="M-4.4 0.5 Q 0 2.3 4.4 0.5" stroke="#3B200E" strokeWidth="2.2" strokeLinecap="round" />
            ) : (
              <g>
                <ellipse
                  cx="0"
                  cy="0"
                  rx={4.7 * Math.max(0.95, state.eyeOpen * 0.96)}
                  ry={Math.max(0.8, 4.5 * openFactor)}
                  fill="#FFFFFF"
                  opacity="0.94"
                />
                <ellipse
                  cx={state.eyeOffsetX * 0.85}
                  cy={state.eyeOffsetY}
                  rx="3.55"
                  ry={Math.max(0.8, 3.7 * Math.min(1.12, openFactor))}
                  fill="#0B1B53"
                />
                <circle cx={state.eyeOffsetX * 0.85 + 1.1} cy={state.eyeOffsetY - 1.2} r="1.2" fill="#FFFFFF" />
                {state.eyeOpen > 1.2 && (
                  <circle cx={state.eyeOffsetX * 0.85 - 1.2} cy={state.eyeOffsetY + 1.1} r="0.6" fill="#FFFFFF" opacity="0.85" />
                )}
              </g>
            )}
            <path
              d={`M-4.6 ${n(-4 - state.browLift)} Q 0 ${n(-6.4 - state.browLift)} 4.8 ${n(-4 - state.browLift)}`}
              stroke="#5A3317"
              strokeWidth="2"
              strokeLinecap="round"
            />
          </g>
        )}

        {/* Over-head Headband when in side-profile (over the side of the skull) */}
        {turn < 0.28 && (
          <g>
            <path
              d={`M${n(bandLeftX)} ${n(bandLeftY)} C${n(bandCtrlLeftX)} ${n(bandPeakY)}, ${n(bandCtrlRightX)} ${n(bandPeakY)}, ${n(bandRightX)} ${n(bandRightY)}`}
              stroke="#2457F5"
              strokeWidth="6.5"
              strokeLinecap="round"
            />
            <path
              d={`M${n(bandLeftX + 1.2)} ${n(bandLeftY - 2)} C${n(bandCtrlLeftX + 1.5)} ${n(bandPeakY + 2)}, ${n(bandCtrlRightX - 1.5)} ${n(bandPeakY + 2)}, ${n(bandRightX - 1.5)} ${n(bandRightY - 4)}`}
              stroke="#5A82FF"
              strokeWidth="1.8"
              strokeLinecap="round"
            />
          </g>
        )}

        {/* Near Electric-Blue Headphone Cup + Signature Curved White Line */}
        <g>
          <ellipse cx={nearCupCushionX} cy={nearCupY} rx={nearCupRx + 0.6} ry={nearCupRy + 0.5} fill="#0B1B53" />
          <ellipse cx={nearCupShellX} cy={nearCupY} rx={nearCupRx} ry={nearCupRy} fill="#2457F5" />
          {/* Subtle upper specular rim on headphone shell */}
          <path
            d={`M${n(nearCupShellX - nearCupRx * 0.45)} ${n(nearCupY - nearCupRy * 0.7)} Q${n(nearCupShellX)} ${n(nearCupY - nearCupRy * 0.92)} ${n(nearCupShellX + nearCupRx * 0.45)} ${n(nearCupY - nearCupRy * 0.7)}`}
            stroke="#6C91FF"
            strokeWidth="1.4"
            strokeLinecap="round"
          />
          {/* Signature curved white line on the earcup */}
          <path
            d={`M${n(nearArcStartX)} ${n(nearCupY - 8.2)} C${n(nearArcCtrlX)} ${n(nearCupY - 4)}, ${n(nearArcCtrlX)} ${n(nearCupY + 4)}, ${n(nearArcStartX)} ${n(nearCupY + 8.2)}`}
            stroke="#FFFFFF"
            strokeWidth={k3(2.5, 2.35, 2.2, turn)}
            strokeLinecap="round"
          />
        </g>

        {showLayerGuides && (
          <rect
            x="58"
            y="30"
            width="98"
            height="61"
            rx="12"
            stroke="#2457F5"
            strokeWidth="1.2"
            strokeDasharray="4 3"
          />
        )}
      </g>

      {/* =========================================================
          LAYER 4: NEAR FRONT PAW (Separated in foreground for crisp silhouette)
          ========================================================= */}
      <g
        data-part="near-paw"
        transform={`translate(${state.nearPawX + state.torsoLeanDeg * 0.35}, ${state.nearPawY}) rotate(${state.nearPawRot}, 98, 118)`}
      >
        {/* Kinetic motion smear ghost during rapid typing bursts */}
        {state.pawBlur > 0.05 && (
          <path
            d={`M${n(nearPawShoulderX)} 112 C${n(nearPawShoulderX + 18)} 111, ${n(nearPawReachX - 18)} 117, ${n(nearPawReachX)} 130 C${n(nearPawReachX + 5)} 134, ${n(nearPawReachX + 3)} 141, ${n(nearPawReachX - 6)} 141 Z`}
            fill="#DDA065"
            opacity={state.pawBlur * 0.34}
          />
        )}
        {/* Forearm separation shadow over torso */}
        <path
          d={`M${n(nearPawShoulderX + 3)} 122 C${n(nearPawShoulderX + 18)} 134, ${n(nearPawReachX - 18)} 143, ${n(nearPawReachX - 3)} 147`}
          stroke="#8E5320"
          strokeWidth="3.2"
          strokeLinecap="round"
          opacity="0.45"
        />
        {/* Articulated caramel foreleg & paw */}
        <path
          d={`M${n(nearPawShoulderX)} 112 C${n(nearPawShoulderX + 18)} 115, ${n(nearPawReachX - 18)} 125, ${n(nearPawReachX)} 139 C${n(nearPawReachX + 5)} 143, ${n(nearPawReachX + 3)} 149.5, ${n(nearPawReachX - 6)} 149.5 C${n(nearPawReachX - 22)} 149.5, ${n(nearPawShoulderX + 15)} 139, ${n(nearPawShoulderX - 2)} 126 Z`}
          fill="#C88648"
        />
        <path
          d={`M${n(nearPawShoulderX + 4)} 115 C${n(nearPawShoulderX + 20)} 118, ${n(nearPawReachX - 18)} 126, ${n(nearPawReachX - 6)} 137`}
          stroke="#DDA065"
          strokeWidth="2.3"
          strokeLinecap="round"
        />
        {/* Dark cocoa capybara paw tip & toe division */}
        <path
          d={`M${n(nearPawReachX - 11)} 140 C${n(nearPawReachX - 3)} 139, ${n(nearPawReachX + 4)} 142, ${n(nearPawReachX + 4)} 146.8 C${n(nearPawReachX + 4)} 150, ${n(nearPawReachX - 4)} 150.5, ${n(nearPawReachX - 12)} 149.5 C${n(nearPawReachX - 14)} 146.5, ${n(nearPawReachX - 14)} 142, ${n(nearPawReachX - 11)} 140 Z`}
          fill="#4E2C14"
        />
        <path
          d={`M${n(nearPawReachX - 4)} 145.5 L${n(nearPawReachX + 3)} 146`}
          stroke="#361D0B"
          strokeWidth="1.2"
          strokeLinecap="round"
        />
        {showLayerGuides && (
          <rect
            x="78"
            y="109"
            width="80"
            height="44"
            rx="8"
            stroke="#2457F5"
            strokeWidth="1.2"
            strokeDasharray="3 3"
          />
        )}
      </g>
    </svg>
  )
}
