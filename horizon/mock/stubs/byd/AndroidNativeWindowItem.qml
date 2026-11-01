import QtQuick 2.15
import com.byd.DataSource 1.0
// Mock of com.byd.AndroidNativeWindowItem: the Android cluster display composited under the
// native scene. Here it shows a captured frame of what Song Pro Horizon draws on that display (the
// scenario's mockProjection), scaled and moved as the app places it for the layout
// (mockPlacement: "scale,x,y" in physical display pixels, like cluster_map_placement).
Item {
    property string disp_name
    property int disp_width
    property int disp_height
    property int disp_mode
    property bool disp_visible
    property bool multi_view
    readonly property var placement: (DataSource.mockPlacement || "1,0,0").split(",").map(Number)
    clip: true

    Image {
        width: parent.width
        height: parent.height
        source: mockAssets + "/" + (DataSource.mockProjection || "projection.png")
        fillMode: Image.Stretch
        smooth: true
        // The scene is 1.5x the panel's physical pixels.
        transform: [
            Scale { xScale: placement[0]; yScale: placement[0] },
            Translate { x: placement[1] * 1.5; y: placement[2] * 1.5 }
        ]
    }
}
