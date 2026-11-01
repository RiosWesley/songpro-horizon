import QtQuick 2.15

// The world behind the instruments, in one shader, after BYD's own new cluster: a lit dome
// overhead, two ranges of mountains on a horizon that curves down at the sides (a valley ahead,
// where the road goes), a soft light where the road meets it, and a glossy floor in true perspective that mirrors that light and, while driving,
// carries slow passes of light past the car. A dither keeps the long gradients from banding on the
// car's glass.
//
// In map mode it is opened where the map goes: the Android projection is composited *under* the
// native scene and only shows through transparent pixels. In a window, the phone's picture is
// taller than the clear hole and the fades top and bottom run over that extra (the cards stay in
// the hole). Full screen, only a frame is left: the scene fades out from the top, the bottom and
// the telltale columns over the map. (Computed here, not masked: on the car a hidden Canvas mask
// is never painted.)
ShaderEffect {
    id: backdrop
    property HorizonTheme theme
    property bool mapMode: false
    // Map mode: the map in a window (`hole`, the phone's whole picture) or under the full panel.
    property bool fullMap: false
    property var hole: ({ x: 0, y: 0, width: 0, height: 0 })
    // Full screen: how deep the frame reaches in from the top, the bottom and each side.
    property real topBand: 230
    property real bottomBand: 190
    property real sideBand: 190
    // km/h: the passing light moves with the car and rests when it stops.
    property real speed: 0
    // Where the floor meets the sky, and how far the horizon drops at the edges.
    property real horizonY: 250
    property real arc: 64

    readonly property size frame: Qt.size(width, height)
    readonly property color skyTop: theme.skyTop
    readonly property color floorHorizon: theme.floorHorizon
    readonly property color floorBottom: theme.floorBottom
    // The drive mode tints these; a change of mode fades over instead of jumping.
    property color skyHorizon: theme.skyHorizon
    property color dome: theme.dome
    property color light: theme.horizonLight
    property color distant: theme.horizonDistant
    property color mountainFar: theme.mountainFar
    Behavior on skyHorizon { ColorAnimation { duration: 900 } }
    Behavior on dome { ColorAnimation { duration: 900 } }
    Behavior on light { ColorAnimation { duration: 900 } }
    Behavior on distant { ColorAnimation { duration: 900 } }
    Behavior on mountainFar { ColorAnimation { duration: 900 } }
    readonly property color mountainNear: theme.mountainNear
    readonly property real domeStrength: theme.domeStrength
    readonly property real vignette: theme.vignette
    readonly property real cut: mapMode ? 1 : 0
    readonly property real full: mapMode && fullMap ? 1 : 0
    readonly property rect holeArea: Qt.rect(hole.x, hole.y, hole.width, hole.height)
    // Long fades: wide at the sides, and top and bottom over the picture's extra height.
    readonly property vector4d holeFeather: Qt.vector4d(90, 60, 90, 44)
    readonly property real motion: Math.min(1, speed / 25)
    property real travel: 0

    // Metres travelled, for the passing light; idle (no repaints) while the car stands.
    Timer {
        interval: 40; repeat: true
        running: backdrop.speed > 0.5 && backdrop.visible
        onTriggered: backdrop.travel = (backdrop.travel + backdrop.speed / 3.6 * interval / 1000) % 240
    }

    fragmentShader: "
        varying highp vec2 qt_TexCoord0;
        uniform lowp float qt_Opacity;
        uniform highp vec2 frame;
        uniform lowp vec4 skyTop;
        uniform lowp vec4 skyHorizon;
        uniform lowp vec4 dome;
        uniform lowp vec4 floorHorizon;
        uniform lowp vec4 floorBottom;
        uniform lowp vec4 light;
        uniform lowp vec4 distant;
        uniform lowp vec4 mountainFar;
        uniform lowp vec4 mountainNear;
        uniform lowp float domeStrength;
        uniform lowp float vignette;
        uniform highp float horizonY;
        uniform highp float arc;
        uniform lowp float cut;
        uniform lowp float full;
        uniform highp float topBand;
        uniform highp float bottomBand;
        uniform highp float sideBand;
        uniform highp vec4 holeArea;
        uniform highp vec4 holeFeather;
        uniform lowp float motion;
        uniform highp float travel;

        // The floor's camera: depth = K / (pixels below the horizon); sideways metres = px * z / F.
        const highp float K = 1452.0;
        const highp float F = 900.0;

        // smootherstep from 0 at d = 0 to 1 at d = f.
        highp float ease(highp float d, highp float f) {
            highp float t = clamp(d / f, 0.0, 1.0);
            return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
        }

        highp float hash(highp vec2 p) {
            return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
        }

        // A mountain range's skyline: smooth value noise in four octaves, 0..~1.
        highp float ridge(highp float x) {
            highp float h = 0.0;
            highp float amp = 0.5;
            for (int i = 0; i < 4; i++) {
                highp float c = floor(x);
                highp float f = fract(x);
                f = f * f * (3.0 - 2.0 * f);
                h += amp * mix(fract(sin(c * 91.7) * 43758.5453), fract(sin((c + 1.0) * 91.7) * 43758.5453), f);
                x *= 2.13;
                amp *= 0.5;
            }
            return h;
        }

        void main() {
            highp vec2 p = qt_TexCoord0 * frame;
            highp float cx = frame.x * 0.5;
            highp float u = (p.x - cx) / cx;
            highp float hy = horizonY + arc * u * u;
            highp float d = p.y - hy;
            highp float centre = 1.0 - smoothstep(0.1, 0.95, abs(u));

            lowp vec3 col;
            highp float glow = 0.0;
            if (d < 0.0) {
                // Sky: dark overhead, lighter into the horizon, and the dome's light from above.
                highp float t = clamp(p.y / hy, 0.0, 1.0);
                col = mix(skyTop.rgb, skyHorizon.rgb, t * t);
                highp vec2 q = vec2((p.x - cx) / 1050.0, (p.y + 140.0) / 400.0);
                col = mix(col, dome.rgb, exp(-dot(q, q) * 1.6) * domeStrength);
                // Mountains: a far range, pale in the haze, and a near one, darker; both low ahead (the
                // valley the road runs into) and higher to the sides, their crests catching the light.
                highp float up = -d;
                highp float side = abs(u);
                highp float far = (0.3 + 0.7 * pow(ridge(p.x / 430.0 + 7.0), 1.3)) * mix(60.0, 175.0, side);
                highp float near = (0.25 + 0.75 * ridge(p.x / 190.0 + 31.0)) * mix(16.0, 100.0, side * side);
                highp float inRange = 0.0;
                if (up < far) {
                    highp float base = 1.0 - up / far;
                    col = mix(mountainFar.rgb, skyHorizon.rgb, 0.12 + 0.55 * base * base);
                    glow += exp(-(far - up) / 2.2) * 0.35;
                    inRange = 1.0;
                }
                if (up < near) {
                    col = mix(mountainNear.rgb, mountainFar.rgb, 0.4 * (1.0 - up / near));
                    glow += exp(-(near - up) / 1.8) * 0.22;
                }
                // Haze rising off the horizon, strongest ahead.
                glow += exp(d / 44.0) * mix(0.18, 0.8, centre) * mix(0.55, 0.38, inRange);
            } else {
                highp float z = K / max(d, 0.5);
                highp float gx = (p.x - cx) * z / F;
                col = mix(floorBottom.rgb, floorHorizon.rgb, 1.0 - exp(-z / 14.0));
                // A glossy floor: the horizon's light mirrored in it.
                glow += exp(-d / 36.0) * mix(0.12, 0.6, centre) * 0.55;
                // Light along the road ahead.
                glow += exp(-gx * gx / 20.0) * exp(-z / 45.0) * 0.12;
                // Slow passes of light off the road, while driving.
                highp float band = 0.5 + 0.5 * cos(6.2832 * (z + travel) / 24.0);
                glow += motion * band * band * smoothstep(3.0, 9.0, abs(gx)) * exp(-z / 32.0) * 0.07;
            }
            // The light at the end of the road: a soft halo in the valley, no line across.
            highp vec2 v = vec2((p.x - cx) / 230.0, d / 75.0);
            glow += exp(-dot(v, v)) * 0.22;
            col = mix(col, light.rgb, clamp(glow, 0.0, 1.0));
            // Far off to both sides, the glow of a city on the horizon.
            highp vec2 w = vec2((abs(u) - 0.72) / 0.22, d / (d < 0.0 ? 40.0 : 18.0));
            col = mix(col, distant.rgb, exp(-dot(w, w)) * 0.32);
            // The light at the end of the road, mirrored down the wet floor.
            if (d > 0.0) {
                highp float sx = (p.x - cx) / 70.0;
                col = mix(col, light.rgb, exp(-sx * sx) * exp(-d / 200.0) * 0.1);
            }

            // The edges fall into shadow.
            highp float r = length(vec2(u * 0.92, (p.y / frame.y - 0.40) * 1.6));
            col *= mix(1.0 - vignette, 1.0, smoothstep(1.35, 0.5, r));
            // Dither: no bands in the long gradients on an 8-bit panel.
            col += (hash(p) - 0.5) / 255.0;

            highp float a = 1.0;
            if (full > 0.5) {
                // Solid behind the text (speed, bottom bar, telltales), then a long fade.
                highp float top = 1.0 - ease(p.y - 95.0, topBand - 95.0);
                highp float bottom = ease(p.y - (frame.y - bottomBand), bottomBand - 115.0);
                highp float side = 1.0 - ease(min(p.x, frame.x - p.x) - 95.0, sideBand - 95.0);
                a = max(max(top * 0.92, bottom * 0.92), side * 0.8);
            } else if (cut > 0.5) {
                a = 1.0 - ease(p.x - holeArea.x, holeFeather.x)
                        * ease(holeArea.x + holeArea.z - p.x, holeFeather.z)
                        * ease(p.y - holeArea.y, holeFeather.y)
                        * ease(holeArea.y + holeArea.w - p.y, holeFeather.w);
            }
            gl_FragColor = vec4(col, 1.0) * (a * qt_Opacity);
        }"
}
