import QtQuick 2.15
import "qrc:/common/singleton"

// Colours and type for day and night. Neutral by default; colour is kept for state only
// (regeneration, power, over the limit) and for the drive mode, which tints the world behind the
// instruments: ECO green, NORMAL blue, SPORT amber.
QtObject {
    property bool night: true
    // "ECO", "NORMAL" or "SPORT" (HorizonData.driveMode).
    property string mode: "NORMAL"

    function tint(eco, normal, sport) {
        return mode === "ECO" ? eco : mode === "SPORT" ? sport : normal
    }

    // Backdrop: sky above the horizon and the dome's light in it, floor below, the horizon's light,
    // the mountains on it, the far glow at its ends and the edge shadow. Colours are opaque and strengths are
    // separate numbers: a ShaderEffect premultiplies a colour's alpha.
    readonly property color skyTop:         night ? "#070d17" : "#f1f5f9"
    readonly property color skyHorizon:     night ? tint("#183a3a", "#1e334b", "#3a2229") : tint("#d0e8df", "#d4e0ec", "#ecdcd6")
    readonly property color dome:           night ? tint("#4c8a78", "#5a7898", "#a25a46") : tint("#f2fff8", "#ffffff", "#fff3ec")
    readonly property real domeStrength:    night ? 0.75 : 0.85
    readonly property color floorHorizon:   night ? "#172a3f" : "#dce4ec"
    readonly property color floorBottom:    night ? "#03060a" : "#c2cedb"
    readonly property color horizonLight:   night ? tint("#a6f5d0", "#a6d8ff", "#ffbf96") : tint("#f0fff7", "#ffffff", "#fff1e8")
    readonly property color horizonDistant: night ? tint("#2c9c78", "#3d8fb0", "#c8532c") : tint("#d6f5e7", "#ffffff", "#ffdccc")
    readonly property color mountainFar:    night ? tint("#183531", "#1b2e45", "#36212a") : tint("#b1cdc2", "#b4c5d7", "#d3bdb5")
    readonly property color mountainNear:   night ? "#09121e" : "#97abc0"
    readonly property real vignette:        night ? 0.6 : 0.14
    readonly property color text:           night ? "#f1f4f7" : "#0d151e"
    readonly property color textDim:        night ? "#8692a0" : "#5a6878"
    readonly property color textFaint:      night ? "#46505c" : "#a3afbb"
    readonly property color hairline:       night ? "#26ffffff" : "#26000000"
    readonly property color track:          night ? "#1affffff" : "#1a000000"
    readonly property color accent:         night ? tint("#7ff0c4", "#7fd6ff", "#ffa070") : tint("#13865c", "#0a6fb3", "#b8461c")
    readonly property color regen:          night ? "#4ad6a0" : "#14945f"
    readonly property color power:          night ? "#f0b24c" : "#c47f10"
    readonly property color alert:          "#ff5c61"

    // The cluster's own families on the car; the offline mock maps them to Noto Sans.
    readonly property string font: CustomStyle.widthFontFamily || "sans-serif"
    readonly property string fontBold: CustomStyle.naviBoldFontFamily || font
}
