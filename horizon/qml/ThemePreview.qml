import QtQuick 2.15
import com.byd.DataSource 1.0

// Horizon's picture in the cluster's own theme menu, over the "Simples" picture (the carrier patches
// menu/ThemeSwitch.qml): BYD's preview size, 496x199, with Horizon's own backdrop drawn at full size
// and scaled down, a speed and a power figure like the factory pictures.
Item {
    id: preview
    width: 496
    height: 199
    clip: true

    HorizonTheme { id: theme; night: DataSource.dayNightMode === 0x02 }

    Item {
        readonly property real s: Math.max(preview.width / 1920, preview.height / 720)
        width: 1920
        height: 720
        scale: s
        transformOrigin: Item.TopLeft
        x: (preview.width - width * s) / 2
        y: (preview.height - height * s) / 2
        Backdrop { anchors.fill: parent; theme: theme }
    }

    Column {
        anchors { horizontalCenter: parent.horizontalCenter; top: parent.top; topMargin: 10 }
        Text {
            anchors.horizontalCenter: parent.horizontalCenter
            text: "80"
            color: theme.text
            font { family: theme.fontBold; pixelSize: 46; bold: true }
        }
        Text {
            anchors.horizontalCenter: parent.horizontalCenter
            text: "km/h"
            color: theme.textDim
            font { family: theme.font; pixelSize: 14 }
        }
    }

    Column {
        anchors { right: parent.right; rightMargin: 40; verticalCenter: parent.verticalCenter; verticalCenterOffset: 14 }
        Text {
            anchors.horizontalCenter: parent.horizontalCenter
            text: "22"
            color: theme.text
            font { family: theme.fontBold; pixelSize: 34; bold: true }
        }
        Text {
            anchors.horizontalCenter: parent.horizontalCenter
            text: "kW"
            color: theme.power
            font { family: theme.font; pixelSize: 13 }
        }
    }

    Rectangle {
        anchors.fill: parent
        color: "transparent"
        radius: 10
        border.width: 1
        border.color: theme.hairline
    }
}
