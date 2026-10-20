import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import QtMultimedia

ApplicationWindow {
    id: window
    width: 1440
    height: 900
    minimumWidth: 1050
    minimumHeight: 680
    visible: true
    title: "Debrid Media Center"
    color: "#080e14"

    property color surface: "#0e171f"
    property color surfaceRaised: "#111d27"
    property color borderColor: "#20313b"
    property color accent: "#54d6c8"
    property color textPrimary: "#e5f2f4"
    property color textMuted: "#8ba0aa"
    property string currentPage: "Overview"
    property string statusText: "Ready"
    property var titleResults: []
    property var releaseResults: []
    property var libraryItems: []
    property var favoriteItems: []
    property var selectedTitle: null
    property string playerSource: ""
    property string playerTitle: ""
    property string rdStatus: "Checking key status…"
    property string tbStatus: "Checking key status…"
    property string dmmStatus: "Checking key status…"

    function pageIndex(name) {
        if (name === "Discover") return 1
        if (name === "Movies") return 2
        if (name === "TV Shows") return 3
        if (name === "Favorites") return 4
        if (name === "Real-Debrid") return 5
        if (name === "TorBox") return 6
        if (name === "Transfers") return 7
        if (name === "Zurg & Mounts") return 8
        if (name === "Health & Logs") return 9
        if (name === "Settings") return 10
        if (name === "Player") return 11
        return 0
    }
    function filteredTitles(kind) {
        return titleResults.filter(function(item) {
            return kind === "movie" ? item.type === "movie" : item.type === "show"
        })
    }
    function titleLabel(item) {
        return item.title + (item.year ? " (" + item.year + ")" : "")
    }

    Connections {
        target: backend
        function onStatusChanged(message) { window.statusText = message }
        function onTitlesReady(results) {
            window.titleResults = results
            window.selectedTitle = null
            window.releaseResults = []
            window.statusText = "Found " + results.length + " title(s)."
        }
        function onReleasesReady(results) {
            window.releaseResults = results
            window.statusText = "Found " + results.length + " release(s)."
        }
        function onLibraryReady(items) {
            window.libraryItems = items
            window.statusText = "Loaded " + items.length + " library item(s)."
        }
        function onConnectionChanged(provider, message) {
            if (provider === "real_debrid") window.rdStatus = message
            else if (provider === "torbox") window.tbStatus = message
            else if (provider === "dmm") window.dmmStatus = message
            window.statusText = message
        }
        function onPlaybackReady(title, url) {
            window.playerTitle = title
            window.playerSource = url
            window.currentPage = "Player"
        }
        function onFavoritesChanged(items) { window.favoriteItems = items }
    }

    MediaPlayer {
        id: mediaPlayer
        source: window.playerSource
        audioOutput: AudioOutput { }
        videoOutput: playerVideo
        onSourceChanged: {
            if (source.toString().length > 0) play()
        }
        onErrorOccurred: function(error, errorString) {
            window.statusText = "Player: " + errorString
        }
        onPlaybackStateChanged: {
            if (playbackState === MediaPlayer.PlayingState) window.statusText = "Playing " + window.playerTitle
        }
    }

    RowLayout {
        anchors.fill: parent
        spacing: 0

        Rectangle {
            Layout.preferredWidth: 230
            Layout.fillHeight: true
            color: "#0b1219"
            border.color: window.borderColor
            ColumnLayout {
                anchors.fill: parent
                anchors.margins: 18
                spacing: 8

                RowLayout {
                    Layout.fillWidth: true
                    spacing: 10
                    Rectangle {
                        width: 40; height: 40; radius: 12
                        color: "#153b3d"
                        Text { anchors.centerIn: parent; text: "D"; color: window.accent; font.pixelSize: 24; font.bold: true }
                    }
                    ColumnLayout {
                        spacing: 0
                        Text { text: "DEBRID"; color: window.textPrimary; font.bold: true; font.pixelSize: 15 }
                        Text { text: "MEDIA CENTER"; color: window.accent; font.pixelSize: 10; font.letterSpacing: 1.4 }
                    }
                }
                Rectangle { Layout.fillWidth: true; height: 1; color: window.borderColor; Layout.topMargin: 12; Layout.bottomMargin: 8 }

                Repeater {
                    model: [
                        { section: "WORKSPACE" },
                        { label: "Overview", glyph: "⌂" },
                        { label: "Discover", glyph: "⌕" },
                        { label: "Movies", glyph: "▣" },
                        { label: "TV Shows", glyph: "▤" },
                        { label: "Favorites", glyph: "☆" },
                        { section: "PROVIDERS" },
                        { label: "Real-Debrid", glyph: "●" },
                        { label: "TorBox", glyph: "●" },
                        { label: "Transfers", glyph: "⇅" },
                        { section: "SYSTEM" },
                        { label: "Zurg & Mounts", glyph: "▦" },
                        { label: "Health & Logs", glyph: "♥" },
                        { label: "Settings", glyph: "⚙" }
                    ]
                    delegate: Item {
                        required property var modelData
                        Layout.fillWidth: true
                        Layout.preferredHeight: modelData.section ? 26 : 39
                        Rectangle {
                            anchors.fill: parent
                            visible: !modelData.section && window.currentPage === modelData.label
                            color: "#153336"
                            radius: 9
                        }
                        Text {
                            anchors.left: parent.left
                            anchors.leftMargin: 10
                            anchors.verticalCenter: parent.verticalCenter
                            text: modelData.section ? modelData.section : modelData.glyph + "   " + modelData.label
                            color: modelData.section ? "#718791" : (window.currentPage === modelData.label ? window.accent : window.textPrimary)
                            font.pixelSize: modelData.section ? 10 : 13
                            font.bold: modelData.section || window.currentPage === modelData.label
                            font.letterSpacing: modelData.section ? 1.5 : 0
                        }
                        MouseArea {
                            anchors.fill: parent
                            enabled: !modelData.section
                            cursorShape: Qt.PointingHandCursor
                            onClicked: {
                                window.currentPage = modelData.label
                                if (modelData.label === "Favorites") window.favoriteItems = backend.favorites()
                            }
                        }
                    }
                }
                Item { Layout.fillHeight: true }
                Text { text: "NATIVE DESKTOP  •  v0.2.0"; color: "#607781"; font.pixelSize: 10 }
            }
        }

        ColumnLayout {
            Layout.fillWidth: true
            Layout.fillHeight: true
            spacing: 0

            RowLayout {
                Layout.fillWidth: true
                Layout.preferredHeight: 74
                Layout.leftMargin: 28
                Layout.rightMargin: 28
                Text { text: window.currentPage; color: window.textPrimary; font.pixelSize: 24; font.bold: true }
                Item { Layout.fillWidth: true }
                TextField {
                    id: globalSearch
                    placeholderText: "Search movies and shows…"
                    Layout.preferredWidth: 300
                    color: window.textPrimary
                    placeholderTextColor: window.textMuted
                    onAccepted: { backend.searchTitles(text); window.currentPage = "Discover" }
                    background: Rectangle { color: window.surface; radius: 18; border.color: window.borderColor }
                }
                Button {
                    text: "⌕  Search"
                    onClicked: { backend.searchTitles(globalSearch.text); window.currentPage = "Discover" }
                    background: Rectangle { color: "#153b3d"; radius: 18; border.color: "#245c5b" }
                    contentItem: Text { text: parent.text; color: window.accent; font.bold: true; horizontalAlignment: Text.AlignHCenter; verticalAlignment: Text.AlignVCenter }
                }
            }

            StackLayout {
                id: pages
                Layout.fillWidth: true
                Layout.fillHeight: true
                currentIndex: window.pageIndex(window.currentPage)

                // Overview
                Flickable {
                    clip: true
                    contentWidth: availableWidth
                    contentHeight: overviewColumn.implicitHeight + 48
                    ColumnLayout {
                        id: overviewColumn
                        width: parent.width
                        spacing: 18
                        Rectangle {
                            Layout.fillWidth: true; Layout.leftMargin: 28; Layout.rightMargin: 28
                            Layout.preferredHeight: 145; radius: 16; color: "#102329"; border.color: "#21464b"
                            RowLayout {
                                anchors.fill: parent; anchors.margins: 24
                                ColumnLayout {
                                    spacing: 7
                                    Text { text: "YOUR MEDIA, ONE PLACE"; color: window.accent; font.pixelSize: 10; font.bold: true; font.letterSpacing: 1.7 }
                                    Text { text: "Welcome to your media center"; color: window.textPrimary; font.pixelSize: 24; font.bold: true }
                                    Text { text: "Search titles, add releases, and play files from connected services."; color: window.textMuted; font.pixelSize: 13 }
                                }
                                Item { Layout.fillWidth: true }
                                Button { text: "Discover content"; onClicked: window.currentPage = "Discover" }
                            }
                        }
                        GridLayout {
                            Layout.fillWidth: true; Layout.leftMargin: 28; Layout.rightMargin: 28
                            columns: 3; columnSpacing: 14; rowSpacing: 14
                            Repeater {
                                model: [
                                    { title: "REAL-DEBRID", value: window.rdStatus, detail: "Provider connection", glyph: "◉" },
                                    { title: "TORBOX", value: window.tbStatus, detail: "Provider connection", glyph: "◉" },
                                    { title: "LIBRARY", value: window.libraryItems.length + " items", detail: "Last loaded locally", glyph: "▤" }
                                ]
                                delegate: Rectangle {
                                    required property var modelData
                                    Layout.fillWidth: true; Layout.preferredHeight: 125
                                    radius: 13; color: window.surface; border.color: window.borderColor
                                    ColumnLayout {
                                        anchors.fill: parent; anchors.margins: 17; spacing: 8
                                        RowLayout {
                                            Layout.fillWidth: true
                                            Text { text: modelData.title; color: window.textMuted; font.pixelSize: 10; font.bold: true; font.letterSpacing: 1.2 }
                                            Item { Layout.fillWidth: true }
                                            Text { text: modelData.glyph; color: window.accent; font.pixelSize: 16 }
                                        }
                                        Text { text: modelData.value; color: window.textPrimary; font.pixelSize: 15; font.bold: true; elide: Text.ElideRight; Layout.fillWidth: true }
                                        Text { text: modelData.detail; color: window.textMuted; font.pixelSize: 12 }
                                    }
                                }
                            }
                        }
                        RowLayout {
                            Layout.fillWidth: true; Layout.leftMargin: 28; Layout.rightMargin: 28; spacing: 16
                            Rectangle {
                                Layout.fillWidth: true; Layout.preferredHeight: 230; radius: 14; color: window.surface; border.color: window.borderColor
                                ColumnLayout {
                                    anchors.fill: parent; anchors.margins: 20; spacing: 10
                                    Text { text: "DISCOVER CONTENT"; color: window.textPrimary; font.pixelSize: 16; font.bold: true }
                                    Text { text: "Search the catalog, inspect release options when DMM release access is configured, and add magnets to your account."; color: window.textMuted; wrapMode: Text.WordWrap; Layout.fillWidth: true; font.pixelSize: 13 }
                                    Item { Layout.fillHeight: true }
                                    Button { text: "Open Discover"; onClicked: window.currentPage = "Discover" }
                                }
                            }
                            Rectangle {
                                Layout.fillWidth: true; Layout.preferredHeight: 230; radius: 14; color: window.surface; border.color: window.borderColor
                                ColumnLayout {
                                    anchors.fill: parent; anchors.margins: 20; spacing: 12
                                    Text { text: "SYSTEM STATUS"; color: window.textPrimary; font.pixelSize: 16; font.bold: true }
                                    Text { text: window.statusText; color: window.textMuted; wrapMode: Text.WordWrap; Layout.fillWidth: true }
                                    Item { Layout.fillHeight: true }
                                    Button { text: "Manage connections"; onClicked: window.currentPage = "Settings" }
                                }
                            }
                        }
                        Item { Layout.preferredHeight: 12 }
                    }
                }

                // Discover
                Flickable {
                    clip: true; contentWidth: availableWidth; contentHeight: discoverColumn.implicitHeight + 40
                    ColumnLayout {
                        id: discoverColumn
                        width: parent.width; spacing: 14
                        Rectangle {
                            Layout.fillWidth: true; Layout.leftMargin: 28; Layout.rightMargin: 28
                            radius: 14; color: window.surface; border.color: window.borderColor
                            ColumnLayout {
                                anchors.fill: parent; anchors.margins: 18; spacing: 10
                                Text { text: "TITLE SEARCH"; color: window.accent; font.pixelSize: 10; font.bold: true; font.letterSpacing: 1.5 }
                                RowLayout {
                                    Layout.fillWidth: true
                                    TextField {
                                        id: titleQuery
                                        Layout.fillWidth: true
                                        placeholderText: "Movie or TV show title…"
                                        color: window.textPrimary; placeholderTextColor: window.textMuted
                                        onAccepted: backend.searchTitles(text)
                                        background: Rectangle { color: "#09121a"; radius: 9; border.color: window.borderColor }
                                    }
                                    Button { text: "Search titles"; onClicked: backend.searchTitles(titleQuery.text) }
                                }
                                Text { text: window.statusText; color: window.textMuted; font.pixelSize: 12; wrapMode: Text.WordWrap }
                                Repeater {
                                    model: window.titleResults
                                    delegate: Rectangle {
                                        required property var modelData
                                        Layout.fillWidth: true; Layout.preferredHeight: 150
                                        radius: 12; color: window.surfaceRaised
                                        border.color: window.selectedTitle && window.selectedTitle.id === modelData.id ? window.accent : window.borderColor
                                        RowLayout {
                                            anchors.fill: parent; anchors.margins: 12; spacing: 14
                                            Rectangle {
                                                Layout.preferredWidth: 82; Layout.preferredHeight: 124
                                                radius: 7; color: "#09121a"; clip: true
                                                Image {
                                                    anchors.fill: parent
                                                    source: modelData.poster || ""
                                                    asynchronous: true
                                                    fillMode: Image.PreserveAspectCrop
                                                    visible: status === Image.Ready
                                                }
                                                Text { anchors.centerIn: parent; text: "DMC"; color: window.accent; font.bold: true; visible: parent.children.length > 0 && parent.children[0].status !== Image.Ready }
                                            }
                                            ColumnLayout {
                                                Layout.fillWidth: true; spacing: 5
                                                RowLayout {
                                                    Layout.fillWidth: true
                                                    Text { text: modelData.title; color: window.textPrimary; font.pixelSize: 15; font.bold: true; Layout.fillWidth: true; elide: Text.ElideRight }
                                                    Text { text: modelData.type.toUpperCase(); color: window.accent; font.pixelSize: 10; font.bold: true }
                                                }
                                                Text { text: modelData.year ? modelData.year : "Year unavailable"; color: window.textMuted; font.pixelSize: 12 }
                                                Text { text: modelData.overview || "No description available."; color: window.textMuted; font.pixelSize: 12; wrapMode: Text.WordWrap; maximumLineCount: 2; elide: Text.ElideRight; Layout.fillWidth: true }
                                                RowLayout {
                                                    Layout.fillWidth: true
                                                    Button {
                                                        text: window.selectedTitle && window.selectedTitle.id === modelData.id ? "Selected" : "Select title"
                                                        onClicked: { window.selectedTitle = modelData; window.releaseResults = [] }
                                                        background: Rectangle { color: "#153b3d"; radius: 8 }
                                                        contentItem: Text { text: parent.text; color: window.accent; font.bold: true; horizontalAlignment: Text.AlignHCenter; verticalAlignment: Text.AlignVCenter; leftPadding: 8; rightPadding: 8 }
                                                    }
                                                    Button {
                                                        text: "☆ Favorite"
                                                        onClicked: backend.toggleFavorite(modelData.title, modelData.imdb)
                                                        background: Rectangle { color: "#1b2631"; radius: 8 }
                                                        contentItem: Text { text: parent.text; color: window.textPrimary; horizontalAlignment: Text.AlignHCenter; verticalAlignment: Text.AlignVCenter; leftPadding: 8; rightPadding: 8 }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Rectangle {
                            Layout.fillWidth: true; Layout.leftMargin: 28; Layout.rightMargin: 28
                            radius: 14; color: window.surface; border.color: window.borderColor
                            ColumnLayout {
                                anchors.fill: parent; anchors.margins: 18; spacing: 10
                                Text { text: "RELEASE SEARCH"; color: window.accent; font.pixelSize: 10; font.bold: true; font.letterSpacing: 1.5 }
                                RowLayout {
                                    Layout.fillWidth: true
                                    ComboBox { id: qualityChoice; model: ["best", "4k", "1080p", "720p"]; currentIndex: 0 }
                                    Button {
                                        text: "Find releases"
                                        enabled: window.selectedTitle !== null
                                        onClicked: backend.searchReleases(window.selectedTitle.imdb, window.selectedTitle.type, qualityChoice.currentText)
                                    }
                                }
                                Text { text: "Requires an active DMM sponsor API key saved in Settings. Without it, you can still add a magnet manually below."; color: window.textMuted; font.pixelSize: 12; wrapMode: Text.WordWrap; Layout.fillWidth: true }
                                Repeater {
                                    model: window.releaseResults
                                    delegate: Rectangle {
                                        required property var modelData
                                        Layout.fillWidth: true; Layout.preferredHeight: 82
                                        radius: 10; color: window.surfaceRaised; border.color: window.borderColor
                                        RowLayout {
                                            anchors.fill: parent; anchors.margins: 12; spacing: 12
                                            ColumnLayout {
                                                Layout.fillWidth: true; spacing: 4
                                                Text { text: modelData.title; color: window.textPrimary; font.bold: true; font.pixelSize: 13; Layout.fillWidth: true; elide: Text.ElideRight }
                                                Text { text: (modelData.size > 0 ? (modelData.size / 1024).toFixed(2) + " GB" : "Size unknown") + "  ·  " + modelData.hash; color: window.textMuted; font.pixelSize: 10; Layout.fillWidth: true; elide: Text.ElideMiddle }
                                            }
                                            Button { text: "Add RD"; onClicked: backend.addMagnet("real_debrid", "magnet:?xt=urn:btih:" + modelData.hash) }
                                            Button { text: "Add TorBox"; onClicked: backend.addMagnet("torbox", "magnet:?xt=urn:btih:" + modelData.hash); background: Rectangle { color: "#1b2631"; radius: 8 } }
                                        }
                                    }
                                }
                            }
                        }

                        Rectangle {
                            Layout.fillWidth: true; Layout.leftMargin: 28; Layout.rightMargin: 28
                            radius: 14; color: window.surface; border.color: window.borderColor
                            ColumnLayout {
                                anchors.fill: parent; anchors.margins: 18; spacing: 10
                                Text { text: "MANUAL MAGNET"; color: window.accent; font.pixelSize: 10; font.bold: true; font.letterSpacing: 1.5 }
                                TextArea {
                                    id: magnetInput
                                    Layout.fillWidth: true; Layout.preferredHeight: 75
                                    placeholderText: "magnet:?xt=urn:btih:…"
                                    color: window.textPrimary; placeholderTextColor: window.textMuted
                                    background: Rectangle { color: "#09121a"; radius: 9; border.color: window.borderColor }
                                }
                                RowLayout {
                                    ComboBox { id: manualProvider; model: ["Real-Debrid", "TorBox"]; currentIndex: 0 }
                                    Button {
                                        text: "Add magnet"
                                        onClicked: backend.addMagnet(manualProvider.currentIndex === 0 ? "real_debrid" : "torbox", magnetInput.text)
                                    }
                                }
                            }
                        }
                    }
                }

                // Movies
                Flickable {
                    clip: true; contentWidth: availableWidth; contentHeight: moviesColumn.implicitHeight + 40
                    ColumnLayout {
                        id: moviesColumn; width: parent.width; spacing: 14
                        Text { text: "Movies"; color: window.textPrimary; font.pixelSize: 22; font.bold: true; Layout.leftMargin: 28 }
                        Text { text: "Search titles in Discover to populate your movie collection."; color: window.textMuted; Layout.leftMargin: 28 }
                        Repeater {
                            model: window.filteredTitles("movie")
                            delegate: Rectangle {
                                required property var modelData
                                Layout.fillWidth: true; Layout.leftMargin: 28; Layout.rightMargin: 28; Layout.preferredHeight: 84
                                radius: 10; color: window.surface; border.color: window.borderColor
                                RowLayout {
                                    anchors.fill: parent; anchors.margins: 14
                                    Text { text: window.titleLabel(modelData); color: window.textPrimary; font.bold: true; Layout.fillWidth: true }
                                    Button { text: "Select"; onClicked: { window.selectedTitle = modelData; window.currentPage = "Discover" } }
                                    Button { text: "☆"; onClicked: backend.toggleFavorite(modelData.title, modelData.imdb) }
                                }
                            }
                        }
                    }
                }

                // TV shows
                Flickable {
                    clip: true; contentWidth: availableWidth; contentHeight: showsColumn.implicitHeight + 40
                    ColumnLayout {
                        id: showsColumn; width: parent.width; spacing: 14
                        Text { text: "TV Shows"; color: window.textPrimary; font.pixelSize: 22; font.bold: true; Layout.leftMargin: 28 }
                        Text { text: "Search titles in Discover to populate your show collection."; color: window.textMuted; Layout.leftMargin: 28 }
                        Repeater {
                            model: window.filteredTitles("show")
                            delegate: Rectangle {
                                required property var modelData
                                Layout.fillWidth: true; Layout.leftMargin: 28; Layout.rightMargin: 28; Layout.preferredHeight: 84
                                radius: 10; color: window.surface; border.color: window.borderColor
                                RowLayout {
                                    anchors.fill: parent; anchors.margins: 14
                                    Text { text: window.titleLabel(modelData); color: window.textPrimary; font.bold: true; Layout.fillWidth: true }
                                    Button { text: "Select"; onClicked: { window.selectedTitle = modelData; window.currentPage = "Discover" } }
                                    Button { text: "☆"; onClicked: backend.toggleFavorite(modelData.title, modelData.imdb) }
                                }
                            }
                        }
                    }
                }

                // Favorites
                Flickable {
                    clip: true; contentWidth: availableWidth; contentHeight: favoritesColumn.implicitHeight + 40
                    ColumnLayout {
                        id: favoritesColumn; width: parent.width; spacing: 12
                        Text { text: "Favorites"; color: window.textPrimary; font.pixelSize: 22; font.bold: true; Layout.leftMargin: 28 }
                        Repeater {
                            model: window.favoriteItems
                            delegate: Rectangle {
                                required property var modelData
                                Layout.fillWidth: true; Layout.leftMargin: 28; Layout.rightMargin: 28; Layout.preferredHeight: 62
                                radius: 10; color: window.surface; border.color: window.borderColor
                                RowLayout {
                                    anchors.fill: parent; anchors.margins: 14
                                    Text { text: modelData.title; color: window.textPrimary; font.bold: true; Layout.fillWidth: true }
                                    Button { text: "Remove"; onClicked: backend.toggleFavorite(modelData.title, modelData.imdb) }
                                }
                            }
                        }
                        Text { visible: window.favoriteItems.length === 0; text: "No favorites yet. Use ☆ Favorite on a title in Discover."; color: window.textMuted; Layout.leftMargin: 28 }
                    }
                }

                // Real-Debrid
                Item {
                    ColumnLayout {
                        anchors.fill: parent; anchors.margins: 28; spacing: 14
                        Text { text: "Real-Debrid"; color: window.textPrimary; font.pixelSize: 24; font.bold: true }
                        Text { text: window.rdStatus; color: window.textMuted; font.pixelSize: 13 }
                        Button { text: "Open Services"; onClicked: window.currentPage = "Settings" }
                        Button { text: "Load Real-Debrid library"; onClicked: backend.loadLibrary("real_debrid") }
                        Item { Layout.fillHeight: true }
                    }
                }

                // TorBox
                Item {
                    ColumnLayout {
                        anchors.fill: parent; anchors.margins: 28; spacing: 14
                        Text { text: "TorBox"; color: window.textPrimary; font.pixelSize: 24; font.bold: true }
                        Text { text: window.tbStatus; color: window.textMuted; font.pixelSize: 13 }
                        Button { text: "Open Services"; onClicked: window.currentPage = "Settings" }
                        Button { text: "Load TorBox library"; onClicked: backend.loadLibrary("torbox") }
                        Item { Layout.fillHeight: true }
                    }
                }

                // Transfers / Library
                Flickable {
                    clip: true; contentWidth: availableWidth; contentHeight: transfersColumn.implicitHeight + 40
                    ColumnLayout {
                        id: transfersColumn; width: parent.width; spacing: 12
                        RowLayout {
                            Layout.fillWidth: true; Layout.leftMargin: 28; Layout.rightMargin: 28
                            Text { text: "Transfers & Library"; color: window.textPrimary; font.pixelSize: 22; font.bold: true; Layout.fillWidth: true }
                            ComboBox { id: libraryProvider; model: ["Real-Debrid", "TorBox"] }
                            Button { text: "Refresh"; onClicked: backend.loadLibrary(libraryProvider.currentIndex === 0 ? "real_debrid" : "torbox") }
                        }
                        Repeater {
                            model: window.libraryItems
                            delegate: Rectangle {
                                required property var modelData
                                Layout.fillWidth: true; Layout.leftMargin: 28; Layout.rightMargin: 28; Layout.preferredHeight: 92
                                radius: 10; color: window.surface; border.color: window.borderColor
                                RowLayout {
                                    anchors.fill: parent; anchors.margins: 14; spacing: 12
                                    ColumnLayout {
                                        Layout.fillWidth: true; spacing: 4
                                        Text { text: modelData.title; color: window.textPrimary; font.bold: true; Layout.fillWidth: true; elide: Text.ElideRight }
                                        Text { text: modelData.status + " · " + modelData.progress + "%"; color: window.textMuted; font.pixelSize: 11 }
                                        ProgressBar { Layout.fillWidth: true; value: modelData.progress / 100.0 }
                                    }
                                    Button { text: "Play"; enabled: modelData.id.length > 0; onClicked: backend.playItem(modelData.provider, modelData.id, modelData.title) }
                                }
                            }
                        }
                    }
                }

                // Zurg
                Item {
                    ColumnLayout {
                        anchors.fill: parent; anchors.margins: 28; spacing: 14
                        Text { text: "Zurg & Mounts"; color: window.textPrimary; font.pixelSize: 24; font.bold: true }
                        Text { text: "Local service controls are not wired up yet. This page intentionally does not start or stop services until their platform-specific configuration is implemented."; color: window.textMuted; wrapMode: Text.WordWrap; Layout.fillWidth: true }
                        Rectangle { Layout.fillWidth: true; height: 1; color: window.borderColor }
                        Text { text: "You can continue to use provider libraries and playback independently of Zurg/rclone."; color: window.textMuted; wrapMode: Text.WordWrap; Layout.fillWidth: true }
                    }
                }

                // Health and logs
                Item {
                    ColumnLayout {
                        anchors.fill: parent; anchors.margins: 28; spacing: 14
                        Text { text: "Health & Logs"; color: window.textPrimary; font.pixelSize: 24; font.bold: true }
                        Text { text: window.statusText; color: window.textMuted; wrapMode: Text.WordWrap; Layout.fillWidth: true }
                        Text { text: "API operations use HTTPS, finite timeouts, and the operating-system keyring for credentials."; color: window.textMuted; wrapMode: Text.WordWrap; Layout.fillWidth: true }
                        Button { text: "Test Real-Debrid"; onClicked: backend.testConnection("real_debrid") }
                        Button { text: "Test TorBox"; onClicked: backend.testConnection("torbox") }
                    }
                }

                // Settings / Services
                Flickable {
                    clip: true; contentWidth: availableWidth; contentHeight: settingsColumn.implicitHeight + 40
                    ColumnLayout {
                        id: settingsColumn; width: parent.width; spacing: 14
                        Text { text: "Services & Settings"; color: window.textPrimary; font.pixelSize: 24; font.bold: true; Layout.leftMargin: 28 }
                        Text { text: "Credentials are stored in your operating-system keyring, never in source files."; color: window.textMuted; Layout.leftMargin: 28; Layout.rightMargin: 28; wrapMode: Text.WordWrap }
                        Repeater {
                            model: [
                                { key: "real_debrid", title: "Real-Debrid", desc: "API token for your own account." },
                                { key: "torbox", title: "TorBox", desc: "API key for your own account." },
                                { key: "dmm", title: "DMM sponsor key", desc: "Optional. Requires an active DMM sponsorship to search releases." }
                            ]
                            delegate: Rectangle {
                                required property var modelData
                                Layout.fillWidth: true; Layout.leftMargin: 28; Layout.rightMargin: 28; Layout.preferredHeight: modelData.key === "dmm" ? 170 : 190
                                radius: 14; color: window.surface; border.color: window.borderColor
                                ColumnLayout {
                                    anchors.fill: parent; anchors.margins: 16; spacing: 8
                                    Text { text: modelData.title; color: window.textPrimary; font.pixelSize: 16; font.bold: true }
                                    Text { text: modelData.desc; color: window.textMuted; font.pixelSize: 12; wrapMode: Text.WordWrap; Layout.fillWidth: true }
                                    TextField {
                                        id: keyField
                                        Layout.fillWidth: true
                                        placeholderText: "Paste key to save or replace"
                                        echoMode: TextInput.Password
                                        color: window.textPrimary; placeholderTextColor: window.textMuted
                                        background: Rectangle { color: "#09121a"; radius: 8; border.color: window.borderColor }
                                    }
                                    RowLayout {
                                        Layout.fillWidth: true
                                        Button { text: "Save key"; onClicked: { backend.saveKey(modelData.key, keyField.text); keyField.text = "" } }
                                        Button {
                                            text: "Test"
                                            visible: modelData.key !== "dmm"
                                            onClicked: backend.testConnection(modelData.key)
                                            background: Rectangle { color: "#1b2631"; radius: 8 }
                                        }
                                        Button {
                                            text: "Remove"
                                            onClicked: backend.removeKey(modelData.key)
                                            background: Rectangle { color: "#1b2631"; radius: 8 }
                                        }
                                        Item { Layout.fillWidth: true }
                                        Text {
                                            text: modelData.key === "real_debrid" ? window.rdStatus : (modelData.key === "torbox" ? window.tbStatus : window.dmmStatus)
                                            color: window.textMuted; font.pixelSize: 10; elide: Text.ElideRight
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Player
                Item {
                    ColumnLayout {
                        anchors.fill: parent; anchors.margins: 24; spacing: 12
                        RowLayout {
                            Layout.fillWidth: true
                            Text { text: "Now Playing"; color: window.textPrimary; font.pixelSize: 24; font.bold: true; Layout.fillWidth: true }
                            Button { text: "Back to library"; onClicked: { mediaPlayer.stop(); window.playerSource = ""; window.currentPage = "Transfers" } }
                        }
                        Text { text: window.playerTitle; color: window.accent; font.pixelSize: 15; font.bold: true }
                        Rectangle {
                            Layout.fillWidth: true; Layout.fillHeight: true
                            color: "#020407"; radius: 12; border.color: window.borderColor; clip: true
                            VideoOutput { id: playerVideo; anchors.fill: parent; fillMode: VideoOutput.PreserveAspectFit }
                            Text { anchors.centerIn: parent; text: mediaPlayer.errorString; color: "#ff8b8b"; visible: mediaPlayer.error !== MediaPlayer.NoError; wrapMode: Text.WordWrap; width: parent.width * 0.8 }
                        }
                        RowLayout {
                            Layout.fillWidth: true
                            Button { text: "▶ Play"; onClicked: mediaPlayer.play() }
                            Button { text: "Ⅱ Pause"; onClicked: mediaPlayer.pause() }
                            Button { text: "■ Stop"; onClicked: mediaPlayer.stop() }
                            Slider {
                                Layout.fillWidth: true
                                from: 0; to: mediaPlayer.duration > 0 ? mediaPlayer.duration : 1
                                value: mediaPlayer.position
                                onMoved: mediaPlayer.position = value
                            }
                            Text { text: mediaPlayer.duration > 0 ? Math.floor(mediaPlayer.position / 60000) + ":" + ("0" + Math.floor(mediaPlayer.position / 1000) % 60).slice(-2) : "--:--"; color: window.textMuted }
                        }
                    }
                }
            }

            Rectangle {
                Layout.fillWidth: true; Layout.preferredHeight: 30
                color: "#0b1219"; border.color: window.borderColor
                RowLayout {
                    anchors.fill: parent; anchors.leftMargin: 14; anchors.rightMargin: 14
                    Text { text: "●"; color: window.accent; font.pixelSize: 9 }
                    Text { text: window.statusText; color: window.textMuted; font.pixelSize: 10; Layout.fillWidth: true; elide: Text.ElideRight }
                    Text { text: "LOCAL-FIRST"; color: "#607781"; font.pixelSize: 9; font.letterSpacing: 1.2 }
                }
            }
        }
    }

    Component.onCompleted: {
        window.rdStatus = backend.keyStatus("real_debrid")
        window.tbStatus = backend.keyStatus("torbox")
        window.dmmStatus = backend.keyStatus("dmm")
        window.favoriteItems = backend.favorites()
    }
}
