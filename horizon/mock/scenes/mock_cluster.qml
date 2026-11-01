import QtQuick 2.15
import com.byd.DataSource 1.0
import com.byd.AndroidNativeWindowItem 1.0
import "../../qml"

// The layers as the cluster stacks them: the Android projection at the bottom, the Horizon scene,
// and the factory telltales on top (loaded from the car's own RCC).
Item {
    width: 1920; height: 720
    AndroidNativeWindowItem {
        anchors.fill: parent
        visible: DataSource.naviType === 4
    }
    HorizonCluster {
        id: horizon
        leftPage: DataSource.mockLeftPage
        mediaDirectory: mockAssets + "/" + DataSource.mockMediaDir + "/"
        powerHistorySeed: DataSource.mockPowerHistory.split(",").map(Number)
        powerStyle: DataSource.mockPowerStyle || "ring"
    }
    // As on the car: with Horizon up, the telltales take BYD's full-navigation arrangement.
    Loader {
        anchors.fill: parent
        source: "../patched/WarinLightLayout.qml"
        onLoaded: item.horizonActive = true
    }
    // Stand-in for a factory warning (the Center draws it above Horizon on the car), as in the
    // car photo of "EPB ativado": an icon and a line of text, centred on BYD's card at y 197.
    Item {
        visible: (DataSource.mockWarningText || "") !== ""
        x: (parent.width - width) / 2
        y: 197; width: 658; height: 396
        Column {
            anchors.centerIn: parent
            spacing: 16
            Rectangle {
                anchors.horizontalCenter: parent.horizontalCenter
                width: 96; height: 96; radius: 48
                color: "transparent"
                border { width: 5; color: DataSource.dayNightMode === 2 ? "#e6e6e6" : "#333333" }
                Text {
                    anchors.centerIn: parent
                    text: "P"
                    color: DataSource.dayNightMode === 2 ? "#e6e6e6" : "#333333"
                    font { pixelSize: 54; bold: true }
                }
            }
            Text {
                anchors.horizontalCenter: parent.horizontalCenter
                text: DataSource.mockWarningText || ""
                color: DataSource.dayNightMode === 2 ? "#ffffff" : "#333333"
                font.pixelSize: 34
            }
        }
    }
}
