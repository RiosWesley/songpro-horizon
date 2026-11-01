import QtQuick 2.15
import com.byd.DataSource 1.0

// Diagnostic build only: the raw ADAS values the cluster receives, to learn on the road which
// targets and distances exist with ACC/ICC off. Read-only; it shows what DataSource already has.
Rectangle {
    id: diag
    property HorizonTheme theme
    color: "#cc05080c"
    radius: 10
    width: column.width + 28
    height: column.height + 20

    readonly property var targets: [
        ["F1", "frontTargetOne"], ["F2", "frontTargetTwo"], ["LF", "leftFrontTarget"],
        ["RF", "rightFrontTarget"], ["LR", "leftRearTarget"], ["RR", "rightRearTarget"], ["R ", "rearTarget"]
    ]

    function num(v) {
        if (v === undefined || v === null) return "-"
        return Math.abs(v - Math.round(v)) < 0.001 ? String(Math.round(v)) : Number(v).toFixed(1)
    }

    // Re-read a few times a second: the values change by the hundred while driving.
    property int tick: 0
    Timer { interval: 200; running: true; repeat: true; onTriggered: diag.tick++ }

    Column {
        id: column
        x: 14; y: 10
        spacing: 2
        Repeater {
            model: diag.targets
            Text {
                readonly property var t: modelData
                color: (diag.tick, DataSource[t[1] + "State"]) ? "#ffd166" : "#8692a0"
                font { family: "monospace"; pixelSize: 19 }
                text: (diag.tick, t[0] + " st " + diag.num(DataSource[t[1] + "State"])
                      + "  x " + diag.num(DataSource[t[1] + "PosX"]) + "  y " + diag.num(DataSource[t[1] + "PosY"]))
            }
        }
        Text {
            color: "#f1f4f7"
            font { family: "monospace"; pixelSize: 19 }
            text: (diag.tick, "main " + diag.num(DataSource.mainTargetIndex) + "  ahead " + diag.num(DataSource.aheadDistance)
                   + "  lvl " + diag.num(DataSource.carDistanceLevel) + "  accTxt " + diag.num(DataSource.accRemoteText))
        }
        Text {
            color: "#f1f4f7"
            font { family: "monospace"; pixelSize: 19 }
            text: (diag.tick, "if " + diag.num(DataSource.adasInterfaceDisplay) + "  show " + diag.num(DataSource.adasShowMode)
                   + "  type " + diag.num(DataSource.adasType) + "  plat " + diag.num(DataSource.adasPlatform)
                   + "  win " + diag.num(DataSource.adasWindow))
        }
        Text {
            color: "#f1f4f7"
            font { family: "monospace"; pixelSize: 19 }
            text: (diag.tick, "lane d " + diag.num(DataSource.carToLeftLaneDistance) + "  L " + diag.num(DataSource.leftLineType)
                   + "/" + diag.num(DataSource.leftLineColor) + "  R " + diag.num(DataSource.rightLineType)
                   + "/" + diag.num(DataSource.rightLineColor) + "  w " + diag.num(DataSource.roadWidth)
                   + "  rad " + diag.num(DataSource.laneRadius))
        }
    }
}
