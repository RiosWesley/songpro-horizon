package com.andrerinas.openheadunit.byd.theme

/**
 * The marked insertions Horizon makes in the car's own factory QML (Song Pro 8.8", SX309).
 *
 * Kotlin twin of `horizon/tools/build_horizon_sx309_88.py`: same anchors, same text, same bytes,
 * so the carrier built on the car equals the one built offline (HorizonCarrierTest checks it).
 * Factory sources are CRLF with a BOM; they are handled as ISO-8859-1 strings, one char per byte,
 * so every byte not touched by a patch survives unchanged.
 */
internal object HorizonPatches {

    private const val BEGIN = "// HORIZON_BEGIN\r\n"
    private const val END = "// HORIZON_END\r\n"
    private val MARKED = Regex("[ ]*// HORIZON_BEGIN\r\n.*?[ ]*// HORIZON_END\r\n", RegexOption.DOT_MATCHES_ALL)

    fun bytes(source: String): ByteArray = source.toByteArray(Charsets.ISO_8859_1)
    fun text(bytes: ByteArray): String = String(bytes, Charsets.ISO_8859_1)
    private fun latin(s: String) = String(s.toByteArray(Charsets.UTF_8), Charsets.ISO_8859_1)

    /** A marked insertion: [text] lines are written CRLF, like the factory source. */
    private fun block(text: String, indent: String): String =
        indent + BEGIN + text.trim('\n').split("\n").joinToString("") { indent + latin(it) + "\r\n" } + indent + END

    private fun insertAfter(src: String, anchor: String, text: String, indent: String): String {
        unique(src, anchor)
        return src.replaceFirst(anchor, anchor + block(text, indent))
    }

    private fun insertBefore(src: String, anchor: String, text: String, indent: String): String {
        unique(src, anchor)
        return src.replaceFirst(anchor, block(text, indent) + anchor)
    }

    private fun unique(src: String, anchor: String) {
        val n = count(src, anchor)
        check(n == 1) { "anchor is not unique (${n}x): ${anchor.trim()}" }
    }

    private fun count(src: String, s: String): Int {
        var n = 0
        var i = src.indexOf(s)
        while (i >= 0) {
            n++
            i = src.indexOf(s, i + s.length)
        }
        return n
    }

    fun stripMarkers(src: String): String = MARKED.replace(src, "")

    // ---- qml10_25/cluster.qml ----------------------------------------------------------------

    private val ROOT_PROPS = """
        // Horizon takes the factory "Simples" slot (themeIndex 2) of the cluster's theme menu; "Classico" stays
        // the factory's. In that slot the app can still choose the factory Simples (menu.json "skin":
        // "factory"); it is read here, as Horizon itself is not loaded then.
        property bool horizonSkin: true
        Timer {
            interval: 1000; running: true; repeat: true; triggeredOnStart: true
            onTriggered: {
                var xhr = new XMLHttpRequest()
                xhr.onreadystatechange = function () {
                    if (xhr.readyState !== XMLHttpRequest.DONE || xhr.responseText.length === 0)
                        return
                    try { rootItemId.horizonSkin = JSON.parse(xhr.responseText).skin !== "factory" } catch (e) { }
                }
                xhr.open("GET", "file:///data/cells/cell2-rw/data/media/0/.cluster/horizon/menu.json")
                xhr.send()
            }
        }
        // Horizon runs only in its slot and while the factory instruments would: powered on, power-on
        // animation done, the always-displayed layer and the telltale layer visible, on the 8.8" panel.
        readonly property bool horizonWanted: DataSource.themeIndex === 0x02 && rootItemId.horizonSkin && DataSource.screenSize === 0x01 && rootItemId.width === 1920 && rootItemId.height === 720 && DataSource.powerGear === 0x03 && DataSource.playAnim === true && alwyasDisplayId.visible && warningLayoutId.visible
        // Fail-open: every factory item Horizon replaces keeps its stock look unless this is true. It
        // steps aside while the factory theme-switch animation plays over the whole panel.
        readonly property bool horizonActive: rootItemId.horizonWanted && !rootItemId.isInThemeSwithAnim && horizonLoader.status === Loader.Ready && horizonLoader.item !== null
        // Horizon shows the music itself: the factory media strip (top centre, with lyrics) yields.
        onHorizonActiveChanged: createMediaInfo()
        // The native 2D ADAS window yields only if Horizon's own 3D road is up (adasReady if Horizon
        // exposes it, else the absence of adasFallback).
        readonly property bool horizonAdas3d: rootItemId.horizonActive && (horizonLoader.item.adasReady !== undefined ? horizonLoader.item.adasReady === true : horizonLoader.item.adasFallback === false)
        // Without the 3D road the native compact 2D ADAS window stays, but in Horizon's ADAS place (the
        // centre, or the left window in map mode) instead of its corner over the energy card.
        readonly property bool horizonAdas2d: rootItemId.horizonActive && !rootItemId.horizonAdas3d
        readonly property bool horizonMap: DataSource.naviType === 0x04 || DataSource.naviType === 0x03
    """.trimIndent()

    // The compact window is 189x120 at (1323, 446): centre (1417.5, 506). Horizon's ADAS place is the
    // stage centre (960, 390) or the map-mode left window centre (336.5, 286).
    private val ADAS2D_PLACE = """
        opacity: rootItemId.horizonAdas3d ? 0 : 1
        scale: rootItemId.horizonAdas2d ? (rootItemId.horizonMap ? 2.1 : 2.4) : 1
        layer.textureSize: rootItemId.horizonAdas2d ? Qt.size(189 * scale, 120 * scale) : Qt.size(0, 0)
        transform: Translate {
            x: rootItemId.horizonAdas2d ? (rootItemId.horizonMap ? 336.5 : 960) - 1417.5 : 0
            y: rootItemId.horizonAdas2d ? (rootItemId.horizonMap ? 286 : 390) - 506 : 0
        }
    """.trimIndent()

    private val ROOT_LOADER = """
        // z: above the factory always-displayed layer (0) and the Android surface (-3), below the centre
        // container (3: menu, drive info, warning pop-ups), the ADAS text tips (111) and every telltale (100).
        Loader {
            id: horizonLoader
            z: 1
            active: rootItemId.horizonWanted
            asynchronous: true
            source: "qrc:/horizon/HorizonCluster.qml"
            visible: rootItemId.horizonActive
        }
    """.trimIndent()

    private val MEDIA_GUARD = """
        if(rootItemId.horizonActive){
            if(rootItemId.mediaInfoUi != null){
                rootItemId.mediaInfoUi.destroy()
                rootItemId.mediaInfoUi = null
            }
            return
        }
    """.trimIndent()

    fun patchRoot(src: String): String {
        var out = insertAfter(src, "    id:rootItemId\r\n", ROOT_PROPS, "    ")
        out = insertAfter(out, "        id: bgImageContainerId\r\n", "opacity: rootItemId.horizonActive ? 0 : 1", "        ")
        out = insertAfter(out, "        id:alwyasDisplayId\r\n", "opacity: rootItemId.horizonActive ? 0 : 1", "        ")
        out = insertAfter(out, "        id: adasWindowBackground\r\n", "opacity: rootItemId.horizonActive ? 0 : 1", "        ")
        out = insertAfter(out, "        id: adas2dNaviWindowBg\r\n", "opacity: rootItemId.horizonAdas3d ? 0 : 1", "        ")
        out = insertAfter(out, "        id:adasContainerParentId\r\n", ADAS2D_PLACE, "        ")
        out = insertAfter(out, "        id:warningLayoutId\r\n", "horizonActive: rootItemId.horizonActive", "        ")
        out = insertAfter(out, "        id:centerContainerId\r\n",
            "horizonActive: rootItemId.horizonActive\nhorizonAdas3d: rootItemId.horizonAdas3d", "        ")
        out = insertBefore(out, "    Loader{\r\n        id:presentationLoaderId\r\n", ROOT_LOADER, "    ")
        return insertAfter(out, "    function createMediaInfo(){\r\n", MEDIA_GUARD, "        ")
    }

    // ---- qml10_25/menu/ThemeSwitch.qml ---------------------------------------------------------

    // The cluster's theme menu: Horizon is the second entry, in place of the factory "Simples" (the
    // root patch runs Horizon only in that slot).
    private const val THEMES_LABEL_ORIG = "            text: TranslateStr.menu_themeSimpleStr\r\n"
    private const val THEMES_LABEL_NEW = "            text: \"Horizon\" // HORIZON: the Simples slot is Horizon's" +
        " (was TranslateStr.menu_themeSimpleStr)\r\n"
    private val THEMES_PICTURE = """
        Loader {
            anchors.fill: parent
            source: "qrc:/horizon/ThemePreview.qml"
        }
    """.trimIndent()

    fun patchThemes(src: String): String {
        val out = insertAfter(src, "            id: techImgId\r\n", THEMES_PICTURE, "            ")
        unique(out, THEMES_LABEL_ORIG)
        return out.replaceFirst(THEMES_LABEL_ORIG, THEMES_LABEL_NEW)
    }

    fun unpatchThemes(src: String): String = stripMarkers(src).replaceFirst(THEMES_LABEL_NEW, THEMES_LABEL_ORIG)

    // ---- qml10_25/WarinLightLayout.qml -------------------------------------------------------

    private const val NAV_ORIG = "    property bool navigationFlag: (DataSource.dilinkName===0x02&&DataSource.naviType===0x04)?true:false\r\n"
    private const val NAV_NEW = "    property bool navigationFlag: ((DataSource.dilinkName===0x02&&DataSource.naviType===0x04)?true:false)" +
        " || horizonLamps.horizonActive // HORIZON: edge telltale layout while Horizon is active\r\n"

    fun patchLamps(src: String): String {
        var out = insertAfter(src, "import com.byd.WarningLight 1.0\r\n\r\nItem {\r\n",
            "id: horizonLamps\nproperty bool horizonActive: false", "    ")
        unique(out, NAV_ORIG)
        out = out.replaceFirst(NAV_ORIG, NAV_NEW)
        // Horizon draws the speed limit itself: hide the native camera/TSR plate only while it does.
        return insertAfter(out, "        displayState:DataSource.limitTrafficSignRecognition\r\n",
            "opacity: horizonLamps.horizonActive ? 0 : 1", "        ")
    }

    fun unpatchLamps(src: String): String = stripMarkers(src).replaceFirst(NAV_NEW, NAV_ORIG)

    // ---- qml10_25/Center.qml -----------------------------------------------------------------

    private val CENTER_PROPS = """
        // Set by the patched root. Horizon draws energy and trip itself, so while it is up the drive-info
        // pages (energy flow included) yield; while its 3D road is up the large 2D ADAS pane yields too.
        // Menu, warnings, charging and the rest of the centre are untouched.
        property bool horizonActive: false
        property bool horizonAdas3d: false
        onHorizonActiveChanged: driveInfoUIUpdate()
        onHorizonAdas3dChanged: updateAdas()
    """.trimIndent()

    private val DRIVEINFO_GUARD = """
        if(centerContainerId.horizonActive){
            if(centerContainerId.driveInfoUi){
                centerContainerId.driveInfoUi.destroy()
                centerContainerId.driveInfoUi = null
            }
            energyFlow.requestToDisplay = false
            return
        }
    """.trimIndent()

    private val ADAS_GUARD = """
        if(centerContainerId.horizonAdas3d){
            if(centerContainerId.adasUi != null){
                centerContainerId.adasUi.destroy()
                centerContainerId.adasUi = null
                centerContainerId.preAdasUiState = 0x00
            }
            return
        }
    """.trimIndent()

    fun patchCenter(src: String): String {
        val out = insertAfter(src, "    id:centerContainerId\r\n", CENTER_PROPS, "    ")
            .let { insertAfter(it, "    function driveInfoUIUpdate(){\r\n", DRIVEINFO_GUARD, "        ") }
        return insertAfter(out, "    function updateAdas(){\r\n", ADAS_GUARD, "        ")
    }

    // ---- qml10_25/adas3d/components/Model3D.qml ------------------------------------------------

    // Asynchronous target loaders let a source swap land mid-incubation, which crashes Quick3D 5.15
    // and restarts the cluster (seen on the car); synchronous ones create the model inside the swap.
    private val ASYNC_OLD = latin("asynchronous: true //异步加载")
    private val ASYNC_NEW = latin("asynchronous: false // HORIZON: was true (异步加载)")

    fun patchModel3d(src: String): String {
        check(count(src, ASYNC_OLD) == 6) { "Model3D.qml: expected 6 asynchronous target loaders" }
        return src.replace(ASYNC_OLD, ASYNC_NEW)
    }

    fun unpatchModel3d(src: String): String = src.replace(ASYNC_NEW, ASYNC_OLD)

    // ---- qml10_25/adas3d/Adas3dContainer.qml -------------------------------------------------

    // The car stays centred in its lane unless the camera gives a plausible position; a dashed ego
    // line means a lane beyond it, which is drawn too.
    private val HORIZON_LANES = """
        function horizonLanes(){
            if(DataSource.laneChangeAnimationDirection !== 0){
                return
            }
            var d = DataSource.carToLeftLaneDistance
            var left = (d > 0 && d < 100) ? -d * 0.96 : -48
            adas3dId.middleLaneLeftX = left
            adas3dId.middleLaneRightX = left + 96
            adas3dId.leftLeftLanePositionX = left - 91
            adas3dId.rightRightLanePositionX = left + 96 + 91
            adas3dId.leftLeftLaneSolid = true
            adas3dId.rightRightLaneSolid = true
            adas3dId.leftLeftLaneOpacity = DataSource.leftLineType === 0x1 ? 0.6 : 0
            adas3dId.rightRightLaneOpacity = DataSource.rightLineType === 0x1 ? 0.6 : 0
        }
    """.trimIndent()

    // Untyped targets (ACC off) the camera still places are drawn as cars while their position moves.
    private val TARGET_SLOTS = listOf(
        Triple(0, "LeftFrontTarget", "leftFrontTarget"), Triple(1, "RightFrontTarget", "rightFrontTarget"),
        Triple(2, "LeftRearTarget", "leftRearTarget"), Triple(3, "RightRearTarget", "rightRearTarget"),
        Triple(4, "FrontTargetOne", "frontTargetOne"), Triple(5, "FrontTargetTwo", "frontTargetTwo"))

    private val GHOST_STATE = """
        // Untyped targets: when each one's position last changed (see horizonGhost).
        property var horizonSeen: ({})
        Timer {
            interval: 250
            running: true
            repeat: true
            onTriggered: {
                var slots = ["leftFrontTarget", "rightFrontTarget", "leftRearTarget",
                             "rightRearTarget", "frontTargetOne", "frontTargetTwo"]
                for (var n = 0; n < slots.length; ++n) {
                    if (DataSource[slots[n] + "State"] === 0) {
                        horizonGhost(n, slots[n])
                    }
                }
            }
        }
    """.trimIndent()

    private val HORIZON_GHOST = """
        function horizonGhost(n, slot){
            if(DataSource.adasShowMode === 1){
                return
            }
            var x = DataSource[slot + "PosX"]
            var y = DataSource[slot + "PosY"]
            var key = "adasModel_0" + n + "_"
            var now = Date.now()
            var last = horizonSeen[slot]
            if(!last || last.x !== x || last.y !== y){
                last = { x: x, y: y, t: now }
                horizonSeen[slot] = last
            }
            var overSelf = Math.abs(x) < 1.4 && y <= 0
            var seen = (Math.abs(y) > 0.5 || Math.abs(x) > 0.5) && !overSelf && now - last.t < 1500
            if(seen && adas3dId[key + "source"] !== getTargetSource(1)){
                adas3dId[key + "source"] = getTargetSource(1)
            }
            adas3dId[key + "visible"] = seen
            if(seen){
                adas3dId[key + "pos_x"] = setTargetX(-x * roadWidthPer, 1)
                adas3dId[key + "pos_z"] = setTargetY(y, 1)
            }
        }
    """.trimIndent()

    // The band from the car to the vehicle it follows, coloured by the time gap (1 red, 2 amber,
    // 3 green), as the factory 2D pane draws it; the 3D scene's own function is commented out.
    private val HORIZON_DISTANCE = """
        function horizonCarDistance(){
            var slot = ["", "adasModel_00_", "adasModel_01_", "adasModel_02_", "adasModel_03_",
                        "adasModel_04_", "adasModel_05_"][DataSource.mainTargetIndex] || ""
            var level = DataSource.carTimeDisLevel
            if(DataSource.laneChangeAnimationDirection !== 0 || slot === "" || level < 1 || level > 3){
                adas3dId.distanceVisible = false
                return
            }
            var end = adas3dId[slot + "pos_z"]
            var source = adas3dId[slot + "source"]
            var maxTargetZ = maxTargetXCar
            if(source === "qrc:/qml10_25/adas3d/components/PedestrianDetail.qml"
                    || source === "qrc:/qml10_25/adas3d/components/BikeDetail.qml"){
                end = end - 20
                maxTargetZ = maxTargetZHuman
            }else if(source === "qrc:/qml10_25/adas3d/components/CarDetail.qml"){
                maxTargetZ = maxTargetZCar
            }else if(source === "qrc:/qml10_25/adas3d/components/TruckDetail.qml"){
                end = end - 30
                maxTargetZ = maxTargetZTruck
            }
            var share = end / maxTargetZ
            adas3dId.distanceEnd = share > 0.95 ? 0.95 : share
            adas3dId.distanceColor = ["", "#f62728", "#e08102", "#17975f"][level]
            adas3dId.distanceVisible = true
        }
        Connections {
            target: DataSource
            function onCarTimeDisLevelChanged(){ updateCarDistanceLevel() }
            function onMainTargetIndexChanged(){ updateCarDistanceLevel() }
        }
    """.trimIndent()
    private const val DISTANCE_NOW = "horizonCarDistance()\nreturn"

    private val ROAD_ALWAYS = """
        adas3dId.adasModel_roadBgVisible = Qt.binding(function () {
            return DataSource.naviType !== 4 && DataSource.naviType !== 3
        })
    """.trimIndent()
    private const val ROAD_ANCHOR = "        adas3dId.adasModel_roadBgVisible = DataSource.adasInterfaceDisplay === 0x1" +
        " && DataSource.naviType !== 4? true : false\r\n"

    fun patchAdas3d(src: String): String {
        var out = insertBefore(src, "    function updateSelfCarToLeftLinePosition(){\r\n", HORIZON_LANES, "    ")
        out = insertAfter(out, "    function updateSelfCarToLeftLinePosition(){\r\n", "horizonLanes()\nreturn", "        ")
        for (anchor in listOf("    function updateLeftLineType(){\r\n", "    function updateRightLineType(){\r\n")) {
            out = insertAfter(out, anchor, "Qt.callLater(horizonLanes)", "        ")
        }
        // The popup background behind the map-mode window gives way to Horizon's own soft window.
        out = insertAfter(out, "        id: bgId\r\n", "opacity: 0", "        ")
        out = insertBefore(out, "    function setTargetX(temp,type)\r\n", HORIZON_GHOST, "    ")
        out = insertAfter(out, "    id:adas3dContainerRootId\r\n", GHOST_STATE, "    ")
        for ((n, name, prop) in TARGET_SLOTS) {
            val guard = "if(DataSource.${prop}State === 0){\n    horizonGhost($n, \"$prop\")\n    return\n}"
            for (part in listOf("Type", "PosX", "PosY")) {
                out = insertAfter(out, "    function update$name$part(){\r\n", guard, "        ")
            }
        }
        out = insertBefore(out, "    function updateCarDistanceLevel(){\r\n", HORIZON_DISTANCE, "    ")
        out = insertAfter(out, "    function updateCarDistanceLevel(){\r\n", DISTANCE_NOW, "        ")
        return insertAfter(out, ROAD_ANCHOR, ROAD_ALWAYS, "        ")
    }
}
