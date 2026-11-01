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
    }
    Loader {
        anchors.fill: parent
        source: "../patched/WarinLightLayout.qml"
    }
}
