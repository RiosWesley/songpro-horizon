import QtQuick 2.15
// Every factory telltale lit over a 100 px grid, to see which zones the theme must keep clear.
Rectangle {
    width: 1920; height: 720; color: "#101418"
    Repeater { model: 20; Rectangle { x: index * 100; width: 1; height: 720; color: "#2a3440" } }
    Repeater { model: 8; Rectangle { y: index * 100; width: 1920; height: 1; color: "#2a3440" } }
    Repeater { model: 20; Text { x: index * 100 + 3; y: 3; text: index * 100; color: "#56687a"; font.pixelSize: 14 } }
    Repeater { model: 8; Text { x: 3; y: index * 100 + 3; text: index * 100; color: "#56687a"; font.pixelSize: 14 } }
    Loader { anchors.fill: parent; source: "qrc:/qml10_25/WarinLightLayout.qml" }
}
