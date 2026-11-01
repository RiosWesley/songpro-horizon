import QtQuick 2.15
import com.byd.DataSource 1.0

// What the factory centre is showing: the Center container is drawn above Horizon, and each of
// its screens opens on a DataSource flag (qml10_25/Center.qml), which Horizon reads too. While one
// is up Horizon gives it the stage: the ADAS road steps back, and the warnings, which carry their
// own card only in full-screen navigation, get Horizon's card behind them (WarningCard).
QtObject {
    // Warnings: EPB, doors, tyres, key, maintenance, drive mode, speed, DMS...
    readonly property bool warning: shown(DataSource.commonWarningInfoNeedDisplay)
        || shown(DataSource.speedWarningInfoNeedDisplay) || shown(DataSource.cumErrorWarningInfoNeedDisplay)
        || shown(DataSource.generPowerWarningInfoNeedDisplay) || shown(DataSource.carBodyNeedDisplay)
        || shown(DataSource.tireWarningInfoNeedDisplay) || shown(DataSource.tirePressureAbnormalNeedDisplay)
        || shown(DataSource.phonePromptNeedDisplay) || shown(DataSource.maintainWarningInfoDisplay)
        || shown(DataSource.modeSwitchWarningInfoNeedDisplay) || shown(DataSource.dmsAnimNeedDisplay)
        || (DataSource.carKeyWarningInfoDisplay >= 0x1 && DataSource.carKeyWarningInfoDisplay <= 0xE)
    // The steering wheel menu (back key).
    readonly property bool menu: shown(DataSource.menuNeedDisplay)
    // Screens of their own: 0-100 timer, charging and V2L, trip summary, native navigation,
    // door-opening warning, the energy flow test.
    readonly property bool screen: shown(DataSource.speedUpTimerInterfaceDisplay)
        || shown(DataSource.chargeNeedDisplay) || shown(DataSource.dischargeNeedDisplay)
        || shown(DataSource.journeyInterfaceDisplay)
        || (DataSource.naviType === 2 && DataSource.dilinkName === 0x02 && (DataSource.naviState === 0 || DataSource.naviState === 1))
        || (DataSource.adasPlatform !== 0x1 && DataSource.dowWarningState !== undefined && DataSource.dowWarningState !== 0x0)
        || DataSource.performanceMode === 0x1
    readonly property bool busy: warning || menu || screen

    // A flag the firmware does not have reads undefined: never "shown".
    function shown(value) {
        return value !== undefined && DataSource.UI_DISPLAY !== undefined && value === DataSource.UI_DISPLAY
    }
}
