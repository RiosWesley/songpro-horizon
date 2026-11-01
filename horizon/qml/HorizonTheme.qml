import QtQuick 2.15
import "qrc:/common/singleton"

// Colours and type for day and night. Neutral by default; colour is kept for state only
// (regeneration, power, over the limit).
QtObject {
    property bool night: true

    // Backdrop: sky above the horizon, floor below it, the horizon's light and the edge shadow.
    readonly property color skyTop:         night ? "#020407" : "#f5f8fb"
    readonly property color skyHorizon:     night ? "#11202f" : "#dde7f0"
    readonly property color floorHorizon:   night ? "#0c1825" : "#e7edf2"
    readonly property color floorBottom:    night ? "#04080d" : "#d2dbe3"
    readonly property color horizonLight:   night ? "#4a8fb8" : "#8fb6d0"
    readonly property real vignette:        night ? 0.55 : 0.12
    readonly property color text:           night ? "#f1f4f7" : "#0d151e"
    readonly property color textDim:        night ? "#8692a0" : "#5a6878"
    readonly property color textFaint:      night ? "#46505c" : "#a3afbb"
    readonly property color hairline:       night ? "#26ffffff" : "#26000000"
    readonly property color track:          night ? "#1affffff" : "#1a000000"
    readonly property color accent:         night ? "#7fd6ff" : "#0a6fb3"
    readonly property color regen:          night ? "#4ad6a0" : "#14945f"
    readonly property color power:          night ? "#f0b24c" : "#c47f10"
    readonly property color alert:          "#ff5c61"

    // The cluster's own families on the car; the offline mock maps them to Noto Sans.
    readonly property string font: CustomStyle.widthFontFamily || "sans-serif"
    readonly property string fontBold: CustomStyle.naviBoldFontFamily || font
}
