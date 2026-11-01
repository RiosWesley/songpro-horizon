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
    // Centred under the speed, close to the phone cluster UI's own proportions (500x292 physical).
    hole:      { x: 585,  y: 150, width: 750, height: 438 },
    adasMini:  { x: 124,  y: 160, width: 425, height: 252 },
    leftCard:  { x: 124,  y: 424, width: 425, height: 168 },
    rightCard: { x: 1371, y: 160, width: 371, height: 432 }
}

// What open-headunit must be told: the map hole in physical display pixels (cluster_display_rect).
function physical(r) {
    return { x: Math.round(r.x * 2 / 3), y: Math.round(r.y * 2 / 3),
             width: Math.round(r.width * 2 / 3), height: Math.round(r.height * 2 / 3) }
}
