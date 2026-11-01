import QtQuick 2.15
// Mock of com.byd.WarningLight: shown while its native state is on or blinking.
Item {
    property int displayState: 0
    property bool flashCompletedShow: false
    property int flashCount: 0
    visible: displayState === 1 || displayState === 2
}
