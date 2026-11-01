import QtQuick 2.15
import com.byd.DataSource 1.0
import "../../qml"

// The cluster's theme menu page with the Simples slot selected, as the carrier patches it: Horizon's
// picture over BYD's and the "Horizon" label (menu/ThemeSwitch.qml, 710x322 in the menu card).
Rectangle {
    width: 752; height: 453
    radius: 24
    color: DataSource.dayNightMode === 2 ? "#1b222c" : "#eef1f4"
    Item {
        x: 14; y: 96; width: 710; height: 322
        Image {
            id: picture
            anchors.horizontalCenter: parent.horizontalCenter
            y: 35
            source: DataSource.dayNightMode === 2 ? "qrc:/images10_25/menu/night/theme_simple_ev.png" : "qrc:/images10_25/menu/day/theme_simple_ev.png"
            ThemePreview { anchors.fill: parent }
        }
        Text {
            anchors { top: picture.bottom; horizontalCenter: picture.horizontalCenter }
            text: "Horizon"
            color: DataSource.dayNightMode === 2 ? "#ffffff" : "#333333"
            font.pixelSize: 27
        }
    }
}
