import QtQuick 2.15
// The factory ADAS 3D scene from the car's own RCC, in its built-in demo mode
// (the same sources Adas3dContainer sets when DataSource.adasShowMode === 1).
Rectangle {
    width: 960; height: 370; color: "#0b1622"
    Loader {
        id: scene
        source: "qrc:/qml10_25/adas3d/Adas3d.qml"
        onLoaded: {
            item.adasModel_00_source = "qrc:/qml10_25/adas3d/components/TruckDetail.qml"
            item.adasModel_01_source = "qrc:/qml10_25/adas3d/components/CarDetail.qml"
            item.adasModel_02_source = "qrc:/qml10_25/adas3d/components/TruckDetail.qml"
            item.adasModel_03_source = "qrc:/qml10_25/adas3d/components/CarDetail.qml"
            item.adasModel_04_source = "qrc:/qml10_25/adas3d/components/BikeDetail.qml"
            item.adasModel_05_source = "qrc:/qml10_25/adas3d/components/PedestrianDetail.qml"
            item.adasModel_00_visible = true; item.adasModel_01_visible = true
            item.adasModel_02_visible = true; item.adasModel_03_visible = true
            item.adasModel_04_visible = true; item.adasModel_05_visible = true
            item.leftLaneVisible = true; item.rightLaneVisible = true
            item.adasModel_roadBgVisible = true
            item.adasModel_RoadBgSource = "qrc:/images10_25/adas3d/RoadsBG_TR_Night_np.png"
        }
    }
}
