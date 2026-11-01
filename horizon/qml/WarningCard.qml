import QtQuick 2.15

// Horizon's card behind a factory warning (EPB, doors, tyres...). The warning itself is the
// factory's, drawn above Horizon; it centres on BYD's own card (658x396 at y 197 with its shadow,
// shown only in full-screen navigation), so this covers that card's visible part.
Rectangle {
    id: card
    property HorizonTheme theme
    property bool shown: false

    x: 659; y: 215; width: 602; height: 350
    radius: 28
    color: theme.night ? "#0b131d" : "#f3f6f9"
    border.width: 1
    border.color: theme.hairline
    opacity: shown ? 0.96 : 0
    visible: opacity > 0
    Behavior on opacity { NumberAnimation { duration: 200 } }

    // A faint light along the top edge, as on Horizon's other surfaces.
    Rectangle {
        anchors { left: parent.left; right: parent.right; top: parent.top; margins: 1 }
        height: parent.height / 3
        radius: parent.radius
        gradient: Gradient {
            GradientStop { position: 0; color: card.theme.night ? "#14ffffff" : "#26ffffff" }
            GradientStop { position: 1; color: "#00ffffff" }
        }
    }
}
