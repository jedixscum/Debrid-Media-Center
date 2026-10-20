import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import QtMultimedia

ApplicationWindow {
    id: win
    width: 1440
    height: 900
    minimumWidth: 1100
    minimumHeight: 700
    visible: true
    title: "Debrid Media Center"
    color: "#080e14"

    property color bg: "#080e14"
    property color panel: "#0e171f"
    property color raised: "#111d27"
    property color border: "#20313b"
    property color teal: "#54d6c8"
    property color text: "#e5f2f4"
    property color muted: "#8ba0aa"
    property string page: "Overview"
    property string status: "Ready"
    property var titles: []
    property var releases: []
    property var library: []
    property var favorites: []
    property var selected: null
    property string playerUrl: ""
    property string playerTitle: ""
    property string logs: ""
    property var systemInfo: ({})
    property var systemConfig: ({})

    function magnet(hash) { return "magnet:?xt=urn:btih:" + hash }
    function pageIndex(p) {
        var pages = ["Overview","Discover","Movies","TV Shows","Favorites","Real-Debrid","TorBox","Transfers","Zurg & Mounts","Health & Logs","Settings","Player"]
        return Math.max(0, pages.indexOf(p))
    }
    function filtered(kind) { return win.titles.filter(function(x) { return x.type === kind }) }
    function openTitle(item) { win.selected = item; win.releases = []; win.page = "Discover" }

    Connections {
        target: backend
        function onStatusChanged(m) { win.status = m }
        function onTitlesReady(r) { win.titles = r; win.selected = null; win.releases = []; win.status = "Found " + r.length + " title(s)." }
        function onReleasesReady(r) { win.releases = r; win.status = "Found " + r.length + " release(s)." }
        function onLibraryReady(r) { win.library = r; win.status = "Loaded " + r.length + " item(s)." }
        function onFavoritesChanged(r) { win.favorites = r }
        function onPlaybackReady(t, u) { win.playerTitle = t; win.playerUrl = u; win.page = "Player" }
        function onConnectionChanged(provider, m) { win.status = m }
    }
    Connections {
        target: systemBackend
        function onStatusChanged(m) { win.status = m }
        function onInfoReady(i) { win.systemInfo = i }
        function onLogsReady(t) { win.logs = t }
    }

    MediaPlayer {
        id: player
        source: win.playerUrl
        audioOutput: AudioOutput { }
        videoOutput: video
        onSourceChanged: if (source.toString() !== "") play()
        onErrorOccurred: function(e, s) { win.status = "Player: " + s }
        onPlaybackStateChanged: if (playbackState === MediaPlayer.PlayingState) win.status = "Playing " + win.playerTitle
    }

    Component.onCompleted: {
        win.favorites = backend.favorites()
        win.systemConfig = systemBackend.config()
        systemBackend.refresh()
    }

    RowLayout {
        anchors.fill: parent
        spacing: 0

        Rectangle {
            Layout.preferredWidth: 245
            Layout.fillHeight: true
            color: "#0b1219"
            border.color: win.border
            ColumnLayout {
                anchors.fill: parent
                anchors.margins: 16
                spacing: 6
                RowLayout {
                    Layout.fillWidth: true
                    Layout.topMargin: 4
                    Rectangle { width: 42; height: 42; radius: 12; color: "#153b3d"; Text { anchors.centerIn: parent; text: "D"; color: win.teal; font.pixelSize: 25; font.bold: true } }
                    ColumnLayout { spacing: 0; Text { text: "DEBRID"; color: win.text; font.bold: true; font.pixelSize: 15 }; Text { text: "MEDIA CENTER"; color: win.teal; font.pixelSize: 10; font.letterSpacing: 1.5 } }
                }
                Rectangle { Layout.fillWidth: true; height: 1; color: win.border; Layout.topMargin: 10; Layout.bottomMargin: 8 }
                ListView {
                    id: nav
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    clip: true
                    spacing: 3
                    model: [
                        {s:"WORKSPACE"},{n:"Overview",g:"⌂"},{n:"Discover",g:"⌕"},{n:"Movies",g:"▣"},{n:"TV Shows",g:"▤"},{n:"Favorites",g:"☆"},
                        {s:"PROVIDERS"},{n:"Real-Debrid",g:"●"},{n:"TorBox",g:"●"},{n:"Transfers",g:"⇅"},
                        {s:"SYSTEM"},{n:"Zurg & Mounts",g:"▦"},{n:"Health & Logs",g:"♥"},{n:"Settings",g:"⚙"}
                    ]
                    delegate: Item {
                        width: nav.width
                        height: modelData.s ? 28 : 40
                        Text { anchors.left: parent.left; anchors.leftMargin: 10; anchors.verticalCenter: parent.verticalCenter; text: modelData.s ? modelData.s : modelData.g + "   " + modelData.n; color: modelData.s ? "#718791" : (win.page === modelData.n ? win.teal : win.text); font.pixelSize: modelData.s ? 10 : 13; font.bold: modelData.s || win.page === modelData.n; font.letterSpacing: modelData.s ? 1.4 : 0 }
                        Rectangle { anchors.fill: parent; radius: 9; color: "#153336"; visible: !modelData.s && win.page === modelData.n; z: -1 }
                        MouseArea { anchors.fill: parent; enabled: !modelData.s; cursorShape: Qt.PointingHandCursor; onClicked: { win.page = modelData.n; if (modelData.n === "Favorites") win.favorites = backend.favorites(); if (modelData.n === "Zurg & Mounts") { win.systemConfig = systemBackend.config(); systemBackend.refresh() } if (modelData.n === "Health & Logs") systemBackend.loadLogs() } }
                    }
                }
                Text { text: "NATIVE LINUX  •  v0.3.0"; color: "#607781"; font.pixelSize: 10 }
            }
        }

        ColumnLayout {
            Layout.fillWidth: true; Layout.fillHeight: true; spacing: 0
            RowLayout {
                Layout.fillWidth: true; Layout.preferredHeight: 76; Layout.leftMargin: 28; Layout.rightMargin: 28
                Text { text: win.page; color: win.text; font.pixelSize: 25; font.bold: true }
                Item { Layout.fillWidth: true }
                TextField {
                    id: search
                    Layout.preferredWidth: 330
                    placeholderText: "Search movies and shows…"
                    color: win.text; placeholderTextColor: win.muted
                    onAccepted: { backend.searchTitles(text); win.page = "Discover" }
                    background: Rectangle { color: win.panel; radius: 18; border.color: win.border }
                }
                Button {
                    text: "⌕  Search"
                    onClicked: { backend.searchTitles(search.text); win.page = "Discover" }
                    background: Rectangle { color: "#153b3d"; radius: 18; border.color: "#245c5b" }
                    contentItem: Text { text: parent.text; color: win.teal; font.bold: true; horizontalAlignment: Text.AlignHCenter; verticalAlignment: Text.AlignVCenter }
                }
            }

            StackLayout {
                Layout.fillWidth: true; Layout.fillHeight: true; currentIndex: win.pageIndex(win.page)

                Flickable {
                    clip: true; contentWidth: width; contentHeight: overview.implicitHeight + 50
                    ColumnLayout { id: overview; width: parent.width; spacing: 16
                        Rectangle { Layout.fillWidth: true; Layout.leftMargin: 28; Layout.rightMargin: 28; Layout.preferredHeight: 150; radius: 16; color: "#102329"; border.color: "#21464b"; RowLayout { anchors.fill: parent; anchors.margins: 24; ColumnLayout { spacing: 7; Text { text: "YOUR MEDIA, ONE PLACE"; color: win.teal; font.pixelSize: 10; font.bold: true; font.letterSpacing: 1.6 }; Text { text: "Welcome to your media center"; color: win.text; font.pixelSize: 24; font.bold: true }; Text { text: "Search, add, manage and play your authorized media from one native app."; color: win.muted; font.pixelSize: 13 } }; Item { Layout.fillWidth: true }; Button { text: "Discover content"; onClicked: win.page = "Discover" } } }
                        GridLayout { Layout.fillWidth: true; Layout.leftMargin: 28; Layout.rightMargin: 28; columns: 3; columnSpacing: 14; rowSpacing: 14
                            Repeater { model: [{a:"REAL-DEBRID",v:backend.keyStatus("real_debrid")},{a:"TORBOX",v:backend.keyStatus("torbox")},{a:"LIBRARY",v:win.library.length + " items"}]; delegate: Rectangle { required property var modelData; Layout.fillWidth: true; Layout.preferredHeight: 115; radius: 13; color: win.panel; border.color: win.border; ColumnLayout { anchors.fill: parent; anchors.margins: 16; Text { text: modelData.a; color: win.muted; font.pixelSize: 10; font.bold: true; font.letterSpacing: 1.2 }; Text { text: modelData.v; color: win.text; font.pixelSize: 15; font.bold: true; elide: Text.ElideRight; Layout.fillWidth: true }; Text { text: modelData.a === "LIBRARY" ? "Provider items loaded this session" : "Credential status"; color: win.muted; font.pixelSize: 11 } } } }
                        }
                        RowLayout { Layout.fillWidth: true; Layout.leftMargin: 28; Layout.rightMargin: 28; spacing: 14
                            Rectangle { Layout.fillWidth: true; Layout.preferredHeight: 190; radius: 14; color: win.panel; border.color: win.border; ColumnLayout { anchors.fill: parent; anchors.margins: 18; Text { text: "QUICK START"; color: win.text; font.pixelSize: 16; font.bold: true }; Text { text: "1. Add your provider keys in Settings.\n2. Search a movie or show.\n3. Select a release or paste a magnet.\n4. Open your provider library and play completed files."; color: win.muted; font.pixelSize: 13; lineHeight: 1.2; Layout.fillWidth: true; wrapMode: Text.WordWrap }; Item { Layout.fillHeight: true }; Button { text: "Open Settings"; onClicked: win.page = "Settings" } } }
                            Rectangle { Layout.fillWidth: true; Layout.preferredHeight: 190; radius: 14; color: win.panel; border.color: win.border; ColumnLayout { anchors.fill: parent; anchors.margins: 18; Text { text: "SYSTEM"; color: win.text; font.pixelSize: 16; font.bold: true }; Text { text: win.status; color: win.muted; wrapMode: Text.WordWrap; Layout.fillWidth: true }; Item { Layout.fillHeight: true }; Button { text: "Zurg & Mounts"; onClicked: win.page = "Zurg & Mounts" } } }
                        }
                    }
                }

                Flickable {
                    clip: true; contentWidth: width; contentHeight: discover.implicitHeight + 45
                    ColumnLayout { id: discover; width: parent.width; spacing: 12
                        Rectangle { Layout.fillWidth: true; Layout.leftMargin: 28; Layout.rightMargin: 28; radius: 14; color: win.panel; border.color: win.border; ColumnLayout { anchors.fill: parent; anchors.margins: 18; spacing: 10
                            Text { text: "TITLE SEARCH"; color: win.teal; font.pixelSize: 10; font.bold: true; font.letterSpacing: 1.5 }
                            RowLayout { Layout.fillWidth: true; TextField { id: titleSearch; Layout.fillWidth: true; placeholderText: "Movie or TV show title…"; color: win.text; placeholderTextColor: win.muted; onAccepted: backend.searchTitles(text); background: Rectangle { color: "#09121a"; radius: 9; border.color: win.border } }; Button { text: "Search"; onClicked: backend.searchTitles(titleSearch.text) } }
                            RowLayout { Layout.fillWidth: true; Text { text: win.status; color: win.muted; Layout.fillWidth: true }; Button { text: "Clear"; onClicked: { win.titles=[]; win.selected=null; win.releases=[] } } }
                        } }
                        Repeater { model: win.titles; delegate: Rectangle { required property var modelData; Layout.fillWidth: true; Layout.leftMargin: 28; Layout.rightMargin: 28; Layout.preferredHeight: 150; radius: 12; color: win.raised; border.color: win.selected && win.selected.id === modelData.id ? win.teal : win.border; RowLayout { anchors.fill: parent; anchors.margins: 12; spacing: 14
                            Rectangle { Layout.preferredWidth: 82; Layout.preferredHeight: 124; radius: 7; color: "#09121a"; clip: true; Image { id: poster; anchors.fill: parent; source: modelData.poster || ""; asynchronous: true; fillMode: Image.PreserveAspectCrop; visible: status === Image.Ready }; Text { anchors.centerIn: parent; text: modelData.type === "movie" ? "MOVIE" : "TV"; color: win.teal; font.bold: true; visible: poster.status !== Image.Ready } }
                            ColumnLayout { Layout.fillWidth: true; spacing: 5; RowLayout { Layout.fillWidth: true; Text { text: modelData.title; color: win.text; font.pixelSize: 16; font.bold: true; Layout.fillWidth: true; elide: Text.ElideRight }; Text { text: modelData.type.toUpperCase(); color: win.teal; font.pixelSize: 10; font.bold: true } }; Text { text: modelData.year || "Year unavailable"; color: win.muted; font.pixelSize: 12 }; Text { text: modelData.overview || "No description available."; color: win.muted; font.pixelSize: 12; maximumLineCount: 2; elide: Text.ElideRight; wrapMode: Text.WordWrap; Layout.fillWidth: true }; RowLayout { Layout.fillWidth: true; Button { text: "Select"; onClicked: win.openTitle(modelData) }; Button { text: backend.favorites().some(function(x){return x.imdb === modelData.imdb}) ? "★" : "☆"; onClicked: backend.toggleFavorite(modelData.title, modelData.imdb) }; Item { Layout.fillWidth: true } } } } } }
                        Rectangle { Layout.fillWidth: true; Layout.leftMargin: 28; Layout.rightMargin: 28; visible: win.selected !== null; radius: 14; color: "#102329"; border.color: "#21464b"; ColumnLayout { anchors.fill: parent; anchors.margins: 18; spacing: 10; Text { text: win.selected ? "RELEASES FOR " + win.selected.title.toUpperCase() : ""; color: win.teal; font.pixelSize: 11; font.bold: true; font.letterSpacing: 1.3 }; RowLayout { Layout.fillWidth: true; ComboBox { id: quality; model: ["best","2160p","1080p","720p"]; currentIndex: 0; Layout.preferredWidth: 130 }; Button { text: "Find releases"; onClicked: if (win.selected) backend.searchReleases(win.selected.imdb, win.selected.type, quality.currentText) }; Text { text: win.releases.length + " release(s)"; color: win.muted } }; Repeater { model: win.releases; delegate: Rectangle { required property var modelData; Layout.fillWidth: true; Layout.preferredHeight: 58; radius: 9; color: win.panel; border.color: win.border; RowLayout { anchors.fill: parent; anchors.margins: 10; Text { text: modelData.title; color: win.text; Layout.fillWidth: true; elide: Text.ElideRight }; Text { text: (modelData.size / 1073741824).toFixed(2) + " GB"; color: win.muted; font.pixelSize: 11 }; Button { text: "Add RD"; onClicked: backend.addMagnet("real_debrid", win.magnet(modelData.hash)) }; Button { text: "Add TorBox"; onClicked: backend.addMagnet("torbox", win.magnet(modelData.hash)) } } } } } }
                        Rectangle { Layout.fillWidth: true; Layout.leftMargin: 28; Layout.rightMargin: 28; radius: 14; color: win.panel; border.color: win.border; ColumnLayout { anchors.fill: parent; anchors.margins: 18; Text { text: "MANUAL MAGNET"; color: win.text; font.pixelSize: 15; font.bold: true }; TextField { id: magnet; Layout.fillWidth: true; placeholderText: "magnet:?xt=urn:btih:…"; color: win.text; placeholderTextColor: win.muted; background: Rectangle { color: "#09121a"; radius: 9; border.color: win.border } }; RowLayout { Button { text: "Add to Real-Debrid"; onClicked: backend.addMagnet("real_debrid", magnet.text) }; Button { text: "Add to TorBox"; onClicked: backend.addMagnet("torbox", magnet.text) } } } }
                    }
                }

                // Movies
                Flickable { clip: true; contentWidth: width; contentHeight: movies.implicitHeight + 40; ColumnLayout { id: movies; width: parent.width; spacing: 12; Text { text: "Movie results from your last search"; color: win.muted; Layout.leftMargin: 28 }; Repeater { model: win.filtered("movie"); delegate: Rectangle { required property var modelData; Layout.fillWidth: true; Layout.leftMargin: 28; Layout.rightMargin: 28; Layout.preferredHeight: 120; radius: 12; color: win.panel; border.color: win.border; RowLayout { anchors.fill: parent; anchors.margins: 12; Text { text: modelData.title + (modelData.year ? " (" + modelData.year + ")" : ""); color: win.text; font.bold: true; Layout.fillWidth: true }; Button { text: "Open"; onClicked: win.openTitle(modelData) } } } } } }
                Flickable { clip: true; contentWidth: width; contentHeight: shows.implicitHeight + 40; ColumnLayout { id: shows; width: parent.width; spacing: 12; Text { text: "TV results from your last search"; color: win.muted; Layout.leftMargin: 28 }; Repeater { model: win.filtered("show"); delegate: Rectangle { required property var modelData; Layout.fillWidth: true; Layout.leftMargin: 28; Layout.rightMargin: 28; Layout.preferredHeight: 120; radius: 12; color: win.panel; border.color: win.border; RowLayout { anchors.fill: parent; anchors.margins: 12; Text { text: modelData.title + (modelData.year ? " (" + modelData.year + ")" : ""); color: win.text; font.bold: true; Layout.fillWidth: true }; Button { text: "Open"; onClicked: win.openTitle(modelData) } } } } } }

                Flickable { clip: true; contentWidth: width; contentHeight: favs.implicitHeight + 40; ColumnLayout { id: favs; width: parent.width; spacing: 12; Text { text: "Saved favorites"; color: win.muted; Layout.leftMargin: 28 }; Repeater { model: win.favorites; delegate: Rectangle { required property var modelData; Layout.fillWidth: true; Layout.leftMargin: 28; Layout.rightMargin: 28; Layout.preferredHeight: 70; radius: 11; color: win.panel; border.color: win.border; RowLayout { anchors.fill: parent; anchors.margins: 14; Text { text: modelData.title; color: win.text; Layout.fillWidth: true; font.bold: true }; Button { text: "Remove"; onClicked: backend.toggleFavorite(modelData.title, modelData.imdb) } } } } } }

                Flickable { clip: true; contentWidth: width; contentHeight: providerPage.implicitHeight + 40; ColumnLayout { id: providerPage; width: parent.width; spacing: 14; Text { text: "Real-Debrid"; color: win.teal; font.pixelSize: 20; font.bold: true; Layout.leftMargin: 28 }; Text { text: "Use Settings to save or test your key, then refresh the provider library."; color: win.muted; Layout.leftMargin: 28 }; Button { text: "Refresh library"; Layout.leftMargin: 28; onClicked: backend.loadLibrary("real_debrid") } } }
                Flickable { clip: true; contentWidth: width; contentHeight: providerPage2.implicitHeight + 40; ColumnLayout { id: providerPage2; width: parent.width; spacing: 14; Text { text: "TorBox"; color: win.teal; font.pixelSize: 20; font.bold: true; Layout.leftMargin: 28 }; Text { text: "Use Settings to save or test your key, then refresh the provider library."; color: win.muted; Layout.leftMargin: 28 }; Button { text: "Refresh library"; Layout.leftMargin: 28; onClicked: backend.loadLibrary("torbox") } } }

                Flickable { clip: true; contentWidth: width; contentHeight: libraryPage.implicitHeight + 50; ColumnLayout { id: libraryPage; width: parent.width; spacing: 12; RowLayout { Layout.leftMargin: 28; Layout.rightMargin: 28; Layout.fillWidth: true; Text { text: "Provider library"; color: win.text; font.pixelSize: 18; font.bold: true }; Item { Layout.fillWidth: true }; Button { text: "Real-Debrid"; onClicked: backend.loadLibrary("real_debrid") }; Button { text: "TorBox"; onClicked: backend.loadLibrary("torbox") } }; Repeater { model: win.library; delegate: Rectangle { required property var modelData; Layout.fillWidth: true; Layout.leftMargin: 28; Layout.rightMargin: 28; Layout.preferredHeight: 92; radius: 12; color: win.panel; border.color: win.border; ColumnLayout { anchors.fill: parent; anchors.margins: 13; RowLayout { Layout.fillWidth: true; Text { text: modelData.title; color: win.text; font.bold: true; Layout.fillWidth: true; elide: Text.ElideRight }; Text { text: modelData.provider; color: win.teal; font.pixelSize: 10 } }; RowLayout { Layout.fillWidth: true; Text { text: modelData.status + "  ·  " + modelData.progress + "%"; color: win.muted; Layout.fillWidth: true }; Button { text: "Play"; enabled: modelData.progress >= 100 || modelData.status.toLowerCase() === "downloaded"; onClicked: backend.playItem(modelData.provider, modelData.id, modelData.title) } } } } } } }

                Flickable { clip: true; contentWidth: width; contentHeight: zurgPage.implicitHeight + 50; ColumnLayout { id: zurgPage; width: parent.width; spacing: 14
                    Rectangle { Layout.fillWidth: true; Layout.leftMargin: 28; Layout.rightMargin: 28; radius: 14; color: win.panel; border.color: win.border; ColumnLayout { anchors.fill: parent; anchors.margins: 18; Text { text: "ZURG / SYSTEMD"; color: win.teal; font.pixelSize: 16; font.bold: true }; Text { text: "Controls use your user systemd session; no sudo or hidden privilege escalation is performed."; color: win.muted; wrapMode: Text.WordWrap; Layout.fillWidth: true }; RowLayout { Layout.fillWidth: true; TextField { id: zurgName; Layout.fillWidth: true; text: win.systemConfig.zurgService || "zurg"; placeholderText: "zurg.service"; color: win.text; background: Rectangle { color: "#09121a"; radius: 8; border.color: win.border } }; TextField { id: rcloneName; Layout.fillWidth: true; text: win.systemConfig.rcloneService || "rclone"; placeholderText: "rclone.service"; color: win.text; background: Rectangle { color: "#09121a"; radius: 8; border.color: win.border } }; Button { text: "Save"; onClicked: { systemBackend.saveConfig(zurgName.text, rcloneName.text); win.systemConfig = systemBackend.config() } } }; RowLayout { Text { text: "Zurg: " + (win.systemInfo.zurg || systemBackend.serviceStatus(zurgName.text)); color: win.text }; Item { Layout.fillWidth: true }; Button { text: "Start"; onClicked: systemBackend.startService(zurgName.text) }; Button { text: "Restart"; onClicked: systemBackend.restartService(zurgName.text) }; Button { text: "Stop"; onClicked: systemBackend.stopService(zurgName.text) } } } }
                    Rectangle { Layout.fillWidth: true; Layout.leftMargin: 28; Layout.rightMargin: 28; radius: 14; color: win.panel; border.color: win.border; ColumnLayout { anchors.fill: parent; anchors.margins: 18; Text { text: "RCLONE MOUNT"; color: win.text; font.pixelSize: 16; font.bold: true }; RowLayout { Layout.fillWidth: true; TextField { id: remote; Layout.fillWidth: true; text: win.systemConfig.remote || ""; placeholderText: "remote:path"; color: win.text; background: Rectangle { color: "#09121a"; radius: 8; border.color: win.border } }; TextField { id: mountpoint; Layout.fillWidth: true; text: win.systemConfig.mountpoint || ""; placeholderText: "~/zurg"; color: win.text; background: Rectangle { color: "#09121a"; radius: 8; border.color: win.border } }; Button { text: "Save"; onClicked: { systemBackend.saveMountConfig(remote.text, mountpoint.text); win.systemConfig = systemBackend.config() } } }; Text { text: "Mount: " + (win.systemInfo.mount || systemBackend.mountStatus()) + "   ·   rclone: " + (win.systemInfo.rcloneInstalled ? "installed" : "not installed"); color: win.muted }; RowLayout { Button { text: "Mount"; onClicked: systemBackend.startMount() }; Button { text: "Unmount"; onClicked: systemBackend.stopMount() }; Item { Layout.fillWidth: true }; Button { text: "Refresh"; onClicked: systemBackend.refresh() } } } }
                } }

                Flickable { clip: true; contentWidth: width; contentHeight: health.implicitHeight + 50; ColumnLayout { id: health; width: parent.width; spacing: 14; RowLayout { Layout.leftMargin: 28; Layout.rightMargin: 28; Text { text: "Health & Logs"; color: win.text; font.pixelSize: 20; font.bold: true }; Item { Layout.fillWidth: true }; Button { text: "Refresh"; onClicked: systemBackend.refresh() }; Button { text: "Reload logs"; onClicked: systemBackend.loadLogs() } }; GridLayout { Layout.leftMargin: 28; Layout.rightMargin: 28; Layout.fillWidth: true; columns: 3; columnSpacing: 12; Repeater { model: [{n:"Zurg",v:win.systemInfo.zurg||"unknown"},{n:"rclone",v:win.systemInfo.rcloneInstalled?"installed":"missing"},{n:"Mount",v:win.systemInfo.mount||"unknown"}]; delegate: Rectangle { required property var modelData; Layout.fillWidth: true; Layout.preferredHeight: 90; radius: 11; color: win.panel; border.color: win.border; ColumnLayout { anchors.fill: parent; anchors.margins: 14; Text { text: modelData.n; color: win.muted; font.pixelSize: 10; font.bold: true }; Text { text: modelData.v; color: win.text; font.pixelSize: 15; font.bold: true } } } } }; Rectangle { Layout.fillWidth: true; Layout.leftMargin: 28; Layout.rightMargin: 28; Layout.preferredHeight: 500; radius: 12; color: "#05090d"; border.color: win.border; ScrollView { anchors.fill: parent; anchors.margins: 12; TextArea { text: win.logs || "No logs loaded yet."; readOnly: true; wrapMode: TextEdit.NoWrap; color: "#b9d0d3"; selectionColor: win.teal; background: null; font.family: "monospace"; font.pixelSize: 11 } } } } }

                Flickable { clip: true; contentWidth: width; contentHeight: settings.implicitHeight + 50; ColumnLayout { id: settings; width: parent.width; spacing: 14; Text { text: "Connections & Settings"; color: win.text; font.pixelSize: 20; font.bold: true; Layout.leftMargin: 28 }; Text { text: "Secrets are stored in the Linux desktop keyring. Entering a key replaces the saved value."; color: win.muted; Layout.leftMargin: 28; Layout.rightMargin: 28; wrapMode: Text.WordWrap }
                    Repeater { model: [{n:"Real-Debrid",p:"real_debrid"},{n:"TorBox",p:"torbox"},{n:"DMM sponsor",p:"dmm"}]; delegate: Rectangle { required property var modelData; Layout.fillWidth: true; Layout.leftMargin: 28; Layout.rightMargin: 28; Layout.preferredHeight: 120; radius: 12; color: win.panel; border.color: win.border; RowLayout { anchors.fill: parent; anchors.margins: 14; ColumnLayout { Layout.fillWidth: true; Text { text: modelData.n; color: win.text; font.bold: true }; Text { text: backend.keyStatus(modelData.p); color: win.muted; font.pixelSize: 11 }; TextField { id: key; Layout.fillWidth: true; placeholderText: "API key"; echoMode: TextInput.Password; color: win.text; placeholderTextColor: win.muted; background: Rectangle { color: "#09121a"; radius: 8; border.color: win.border } } }; Button { text: "Save"; onClicked: backend.saveKey(modelData.p, key.text) }; Button { text: "Test"; onClicked: backend.testConnection(modelData.p) }; Button { text: "Remove"; onClicked: backend.removeKey(modelData.p) } } } }
                    Text { text: "Status: " + win.status; color: win.muted; Layout.leftMargin: 28; Layout.rightMargin: 28; wrapMode: Text.WordWrap }
                } }

                Item { // Player
                    Rectangle { anchors.fill: parent; color: "#05090d"; VideoOutput { id: video; anchors.fill: parent; fillMode: VideoOutput.PreserveAspectFit }; ColumnLayout { anchors.left: parent.left; anchors.right: parent.right; anchors.bottom: parent.bottom; anchors.margins: 20; spacing: 8; Rectangle { Layout.fillWidth: true; height: 60; radius: 12; color: "#b0000000"; RowLayout { anchors.fill: parent; anchors.margins: 12; Text { text: win.playerTitle || "Player"; color: "white"; font.bold: true; Layout.fillWidth: true }; Button { text: player.playbackState === MediaPlayer.PlayingState ? "Pause" : "Play"; onClicked: player.playbackState === MediaPlayer.PlayingState ? player.pause() : player.play() }; Button { text: "Stop"; onClicked: player.stop() }; Button { text: "Close"; onClicked: { player.stop(); win.playerUrl=""; win.page="Transfers" } } } } } }
                }
            }
            Rectangle { Layout.fillWidth: true; Layout.preferredHeight: 28; color: "#0b1219"; border.color: win.border; Text { anchors.left: parent.left; anchors.leftMargin: 18; anchors.verticalCenter: parent.verticalCenter; text: win.status; color: win.muted; font.pixelSize: 11; elide: Text.ElideRight; width: parent.width - 36 } }
        }
    }
}
