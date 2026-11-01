import QtQuick 2.15

// The world behind the instruments, painted in one shader: a sky that lightens into a soft
// horizon, and a floor in true perspective running out to it, misted in the distance and lit
// under the car.
//
// In map mode it is opened, with soft edges, where the map goes: the Android projection is
// composited *under* the native scene and only shows through transparent pixels. (Computed here,
// not masked: on the car a hidden Canvas mask is never painted.)
ShaderEffect {
    id: backdrop
    property HorizonTheme theme
    property bool mapMode: false
    property var hole: ({ x: 0, y: 0, width: 0, height: 0 })
    // Where the floor meets the sky, and how far the horizon drops at the edges.
    property real horizonY: 236
    property real arc: 40

    readonly property size frame: Qt.size(width, height)
    readonly property color skyTop: theme.skyTop
    readonly property color skyHorizon: theme.skyHorizon
    readonly property color floorHorizon: theme.floorHorizon
    readonly property color floorBottom: theme.floorBottom
    readonly property color light: theme.horizonLight
    readonly property real vignette: theme.vignette
    readonly property real cut: mapMode ? 1 : 0
    readonly property rect holeArea: Qt.rect(hole.x, hole.y, hole.width, hole.height)
    // The map dissolves into the scene: wide fades at the sides, where there is only map, short
    // ones top and bottom, where the phone puts the manoeuvre and arrival cards.
    readonly property vector4d holeFeather: Qt.vector4d(80, 22, 80, 22)

    fragmentShader: "
        varying highp vec2 qt_TexCoord0;
        uniform lowp float qt_Opacity;
        uniform highp vec2 frame;
        uniform lowp vec4 skyTop;
        uniform lowp vec4 skyHorizon;
        uniform lowp vec4 floorHorizon;
        uniform lowp vec4 floorBottom;
        uniform lowp vec4 light;
        uniform lowp float vignette;
        uniform highp float horizonY;
        uniform highp float arc;
        uniform lowp float cut;
        uniform highp vec4 holeArea;
        uniform highp vec4 holeFeather;

        // The floor's camera: depth = K / (pixels below the horizon); sideways metres = px * z / F.
        const highp float K = 1452.0;
        const highp float F = 900.0;

        void main() {
            highp vec2 p = qt_TexCoord0 * frame;
            highp float cx = frame.x * 0.5;
            highp float u = (p.x - cx) / cx;
            highp float hy = horizonY + arc * u * u;
            highp float d = p.y - hy;

            lowp vec3 col;
            highp float glow = 0.0;
            if (d < 0.0) {
                // Sky: dark overhead, lightening into the horizon.
                highp float t = clamp(p.y / hy, 0.0, 1.0);
                col = mix(skyTop.rgb, skyHorizon.rgb, t * t * t);
                glow += exp(-(d * d) / (2.0 * 30.0 * 30.0)) * mix(0.35, 1.0, 1.0 - smoothstep(0.2, 0.8, abs(u))) * 0.45;
            } else {
                highp float z = K / max(d, 0.5);
                highp float gx = (p.x - cx) * z / F;
                // Floor: misted towards the horizon, dark close to the driver.
                col = mix(floorBottom.rgb, floorHorizon.rgb, 1.0 - exp(-z / 16.0));
                glow += exp(-(d * d) / (2.0 * 14.0 * 14.0)) * mix(0.35, 1.0, 1.0 - smoothstep(0.2, 0.8, abs(u))) * 0.4;

                // Light pooled under the car.
                highp vec2 q = vec2(gx / 2.6, (z - 4.6) / 2.4);
                highp float pool = max(0.0, 1.0 - dot(q, q));
                glow += pool * pool * 0.18;
            }
            // The horizon itself: a thin bright line only where the road meets it.
            glow += exp(-(d * d) / (2.0 * 1.4 * 1.4)) * (1.0 - smoothstep(0.24, 0.46, abs(u))) * 0.5;
            col = mix(col, light.rgb, clamp(glow, 0.0, 1.0));

            // The edges fall into shadow.
            highp float r = length(vec2(u * 0.95, (p.y / frame.y - 0.42) * 1.5));
            col *= mix(1.0 - vignette, 1.0, smoothstep(1.3, 0.45, r));

            highp float a = 1.0;
            if (cut > 0.5) {
                a = 1.0 - smoothstep(0.0, holeFeather.x, p.x - holeArea.x)
                        * smoothstep(0.0, holeFeather.z, holeArea.x + holeArea.z - p.x)
                        * smoothstep(0.0, holeFeather.y, p.y - holeArea.y)
                        * smoothstep(0.0, holeFeather.w, holeArea.y + holeArea.w - p.y);
            }
            gl_FragColor = vec4(col, 1.0) * (a * qt_Opacity);
        }"
}
