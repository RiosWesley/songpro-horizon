.pragma library

// Horizon layout, in the cluster's project coordinates (1920x720). The 8.8" panel shows the
// scene at 2/3 (1280x480 physical), so every rectangle here is 1.5x its size on the glass.
//
// The factory telltales are kept, in the arrangement BYD drew for full-screen navigation (the
// carrier sets WarinLightLayout's navigationFlag): a column along each side, a row along the top
// at both ends, a row bottom-left and the READY "OK" at the bottom centre. Nothing of ours is
// drawn there.
var lampBands = { left: 112, right: 1752, top: 148, bottom: 656 }
var readyLamp = { x: 900, y: 588, width: 120, height: 60 }

// Instrument mode: the factory ADAS 3D road across the middle, a card on each side over it.
var native = {
    leftCard:  { x: 124,  y: 160, width: 430, height: 432 },
    stage:     { x: 400,  y: 150, width: 1120, height: 450 },
    rightCard: { x: 1366, y: 160, width: 376, height: 432 }
}

// Map mode (the Android projection is on): the map in the middle, ADAS 3D in a window on the
// left above a compact music card, energy on the right.
var map = {
    // Centred under the speed, close to the phone cluster UI's own proportions (500x292 physical):
    // where the map is fully clear. The backdrop opens `video` over the phone's picture and fades
    // over the margin round `hole`. The phone always draws for the whole panel (so the app can
    // switch layouts live); in this layout the app scales its picture to 0.8 and centres it here,
    // and tells the phone to keep its cards inside `hole` (content insets; the app's ClusterMapArea).
    hole:      { x: 585,  y: 150, width: 750, height: 438 },
    video:     { x: 585,  y: 102, width: 750, height: 516 },
    adasMini:  { x: 124,  y: 160, width: 425, height: 252 },
    leftCard:  { x: 124,  y: 424, width: 425, height: 168 },
    rightCard: { x: 1371, y: 160, width: 371, height: 432 }
}

// Map mode, second layout: the map takes the right side instead of the energy card, the ADAS road
// stays under the speed (narrower, so it ends before the map) and power is a bar at the top, as on
// the full-screen map. The map's clear part mirrors the left card (x 1366-1742, y 160-560); `video`
// is opened over the phone's picture and faded by `feather` (left, top, right, bottom) towards it.
// The app scales and moves the picture into it.
var side = {
    video:     { x: 1318, y: 118, width: 434, height: 470 },
    feather:   [48, 42, 10, 28],
    stage:     { x: 554,  y: 150, width: 812, height: 450 }
}

// Full-screen map: the phone's picture under the whole panel. Horizon keeps a band across the top
// (speed, gear, power) and backings behind the bottom bar's figures; the map runs to the bottom
// and to the sides. The ADAS road shows in a small window, like the factory's own in navigation.
// The phone keeps its cards out of the top band and the bottom bar (content insets).
var full = {
    video:     { x: 0, y: 0, width: 1920, height: 720 },
    topSolid:  76,
    topBand:   170,
    // Above the top-left telltale row (x 510-750, y 95-140) and clear of the speed limit sign.
    power:     { x: 470, y: 22, width: 300, height: 48 },
    adas:      { x: 1400, y: 300, width: 330, height: 220 },
    menu:      { x: 124, y: 190, width: 430, height: 330 }
}

// A design rect in physical display pixels (the app's ClusterMapArea uses these).
function physical(r) {
    return { x: Math.round(r.x * 2 / 3), y: Math.round(r.y * 2 / 3),
             width: Math.round(r.width * 2 / 3), height: Math.round(r.height * 2 / 3) }
}
