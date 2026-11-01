import QtQuick 2.15
import com.byd.DataSource 1.0
// Mock of com.byd.AndroidNativeWindowItem: the Android cluster display composited under the
// native scene. Here it shows a captured frame of what open-headunit draws on that display (the
// window by default, or the scenario's mockProjection, e.g. the full-screen one).
Image {
    property string disp_name
    property int disp_width
    property int disp_height
    property int disp_mode
    property bool disp_visible
    property bool multi_view
    source: mockAssets + "/" + (DataSource.mockProjection || "projection.png")
    fillMode: Image.Stretch
    smooth: true
}
