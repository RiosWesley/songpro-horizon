import QtQuick 2.15

// A layer effect that fades its item out towards the edges of `area`, worked out in the shader.
// On the car a hidden Canvas used as an OpacityMask source is never painted, so the masks are
// computed here instead of drawn.
//   inverted: 0 -> the item shows inside `area`, fading in from its edges (a window, a fade)
//   inverted: 1 -> the item shows outside `area` and is cut away inside it (the map hole)
// `feather` is the width of each fade, in pixels: left, top, right, bottom. The fade is a
// smootherstep: it leaves the edge and arrives at full opacity without a visible start or end.
ShaderEffect {
    property rect area: Qt.rect(0, 0, width, height)
    property vector4d feather: Qt.vector4d(24, 24, 24, 24)
    property real inverted: 0
    readonly property size frame: Qt.size(width, height)

    fragmentShader: "
        varying highp vec2 qt_TexCoord0;
        uniform sampler2D source;
        uniform lowp float qt_Opacity;
        uniform highp vec2 frame;
        uniform highp vec4 area;
        uniform highp vec4 feather;
        uniform lowp float inverted;
        highp float ease(highp float d, highp float f) {
            highp float t = clamp(d / max(f, 0.001), 0.0, 1.0);
            return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
        }
        void main() {
            highp vec2 p = qt_TexCoord0 * frame;
            highp float a = ease(p.x - area.x, feather.x)
                          * ease(area.x + area.z - p.x, feather.z)
                          * ease(p.y - area.y, feather.y)
                          * ease(area.y + area.w - p.y, feather.w);
            a = mix(a, 1.0 - a, inverted);
            gl_FragColor = texture2D(source, qt_TexCoord0) * (a * qt_Opacity);
        }"
}
