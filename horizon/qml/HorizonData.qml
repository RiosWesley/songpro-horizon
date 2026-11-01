import QtQuick 2.15
import com.byd.DataSource 1.0

// The one place that knows the native DataSource encodings (sentinels, enums, units). Horizon
// components read these clean values and never DataSource directly, so a firmware with other
// encodings only needs this file changed.
Item {
    visible: false
    property alias mediaDirectory: feed.directory

    readonly property bool night: DataSource.dayNightMode !== 0x01
    // The Android projection (open-headunit's map) is on: navigation FULL_SCREEN (4) or the
    // reduced SMALL_SCREEN (3).
    readonly property bool mapMode: DataSource.naviType === 0x04 || DataSource.naviType === 0x03
    readonly property bool poweredOn: DataSource.powerGear === 0x03

    readonly property int speed: DataSource.speed >= 0 && DataSource.speed <= 300 ? DataSource.speed : 0
    readonly property string speedUnit: DataSource.speedUnit === 0x02 ? "mph" : "km/h"
    readonly property int speedLimit: DataSource.limitTrafficSymbolValue >= 5 && DataSource.limitTrafficSymbolValue <= 150
                                      ? DataSource.limitTrafficSymbolValue : 0
    readonly property string gear: ["", "P", "R", "N", "D"][DataSource.gearMode] || ""
    readonly property string driveMode: DataSource.ecoSportMode === 1 ? "ECO"
                                      : DataSource.ecoSportMode === 2 ? "SPORT" : "NORMAL"

    readonly property int soc: DataSource.powerBatterySoc >= 0 && DataSource.powerBatterySoc <= 100
                               ? DataSource.powerBatterySoc : -1
    readonly property int rangeKm: DataSource.socMile >= 0 && DataSource.socMile <= 0x3E7 ? DataSource.socMile : -1
    // kW, negative while regenerating.
    readonly property real powerKw: DataSource.realPower
    // What the factory band shows there (ODO, trip A/B…, chosen in the car's menu); empty when off.
    readonly property string odometer: DataSource.odoIndex !== 0 ? String(DataSource.odoValue) : ""
    // Song Pro DM-i is a plug-in hybrid: fuel beside the battery.
    readonly property int fuelPercent: DataSource.fuelPercent >= 0 && DataSource.fuelPercent <= 100 ? DataSource.fuelPercent : -1
    readonly property int fuelRangeKm: DataSource.fuelMile >= 0 && DataSource.fuelMile <= 0x3E7 ? DataSource.fuelMile : -1

    // Last 50 km, per 100 km, as the factory cluster shows them.
    readonly property string recent50Power: DataSource.recent50PowerValue !== undefined ? String(DataSource.recent50PowerValue) : "--"
    readonly property string recent50Fuel: DataSource.recent50FuelValue !== undefined ? String(DataSource.recent50FuelValue) : "--"

    // Trip page.
    readonly property string tripKm: DataSource.journeyMileText !== undefined ? Number(DataSource.journeyMileText).toFixed(1) : "--"
    readonly property string driveTime: String(DataSource.driveTime || "--")
    readonly property string avgSpeed: DataSource.avgSpeedValue !== -1 ? String(DataSource.avgSpeedValue) : "--"

    readonly property bool tempKnown: DataSource.outsideTempDisplay !== 0
    readonly property string outsideTemp: DataSource.outsideTemp + (DataSource.outsideTempUnit === 0 ? "°C" : "°F")

    // Music. Preferably from open-headunit's now-playing feed (cover, progress, synced lyrics);
    // otherwise the native instrument media text (radio, Bluetooth, or no feed), shown as the title.
    NowPlayingFeed { id: feed }
    readonly property bool fromFeed: feed.alive && feed.title.length > 0
    readonly property string nativeText: String(DataSource.mediaInfoString || "").trim()

    readonly property bool mediaPresent: fromFeed || (DataSource.mediaInfoNeedDisplay !== 0 && nativeText.length > 0)
    readonly property bool playing: fromFeed ? feed.playing : DataSource.multimediaPlayStatus === 1
    readonly property string title: fromFeed ? feed.title : nativeText
    readonly property string artist: fromFeed ? feed.artist : ""
    readonly property url artUrl: fromFeed ? feed.artUrl : ""
    readonly property real positionMs: fromFeed ? feed.positionMs : 0
    readonly property real durationMs: fromFeed ? feed.durationMs : 0
    readonly property string prevLyric: fromFeed ? feed.prevLyric : ""
    readonly property string lyric: fromFeed ? feed.lyric : ""
    readonly property string nextLyric: fromFeed ? feed.nextLyric : ""
}
