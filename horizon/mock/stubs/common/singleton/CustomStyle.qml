pragma Singleton
import QtQuick 2.15
// Mock of the native CustomStyle singleton (it ships in the cluster binary, not in the RCC).
QtObject {
    property int bgZ: 0
    property int presentationZ: -1
    property int warningLightZ: 50
    property int powerOnAnimationZ: 90
    property int approachAnimaionZ: 80
    property string naviFontFamily: "Noto Sans"
    property string naviBoldFontFamily: "Noto Sans"
    property string widthFontFamily: "Noto Sans"
    property int centerChildOpacityAnimateDuration: 300
    property bool isSingular: false
}
