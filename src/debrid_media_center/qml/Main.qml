import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import QtQuick.Effects

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
                spacing: 10

                RowLayout {
                    Layout.fillWidth: true
                    spacing: 10
                    Rectangle {
                        width: 38; height: 38; radius: 12
                        color: "#153b3d"
                        Text { anchors.centerIn: parent; text: "D"; color: window.accent; font.pixelSize: 23; font.bold: true }
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
                        Text {
                            anchors.left: parent.left
                            anchors.leftMargin: 8
                            anchors.verticalCenter: parent.verticalCenter
                            text: modelData.section ? modelData.section : modelData.label
                            color: modelData.section ? "#718791" : (modelData.label === "Overview" ? window.accent : window.textPrimary)
                            font.pixelSize: modelData.section ? 10 : 13
                            font.bold: modelData.section || modelData.label === "Overview"
                            font.letterSpacing: modelData.section ? 1.5 : 0
                        }
                        Rectangle {
                            anchors.fill: parent
                            visible: modelData.label === "Overview"
                            color: "#153336"
                            radius: 9
                            z: -1
                        }
                    }
                }
                Item { Layout.fillHeight: true }
                Text { text: "EARLY ACCESS  •  v0.1.0"; color: "#607781"; font.pixelSize: 10 }
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
                Text { text: "Overview"; color: window.textPrimary; font.pixelSize: 24; font.bold: true }
                Item { Layout.fillWidth: true }
                TextField {
                    id: searchField
                    placeholderText: "Search movies, shows, releases…"
                    Layout.preferredWidth: 320
                    color: window.textPrimary
                    placeholderTextColor: window.textMuted
                    background: Rectangle { color: window.surface; radius: 18; border.color: window.borderColor }
                }
                Button {
                    text: "⌕  Discover"
                    onClicked: { searchField.forceActiveFocus() }
                    background: Rectangle { color: "#153b3d"; radius: 18; border.color: "#245c5b" }
                    contentItem: Text { text: parent.text; color: window.accent; font.bold: true; horizontalAlignment: Text.AlignHCenter; verticalAlignment: Text.AlignVCenter }
                }
                Rectangle {
                    width: 38; height: 38; radius: 19; color: window.surfaceRaised; border.color: window.borderColor
                    Text { anchors.centerIn: parent; text: "⚙"; color: window.textPrimary; font.pixelSize: 18 }
                }
            }

            Flickable {
                Layout.fillWidth: true
                Layout.fillHeight: true
                contentWidth: availableWidth
                contentHeight: contentColumn.implicitHeight + 48
                clip: true

                ColumnLayout {
                    id: contentColumn
                    width: parent.width
                    spacing: 18

                    Rectangle {
                        Layout.fillWidth: true
                        Layout.leftMargin: 28
                        Layout.rightMargin: 28
                        Layout.preferredHeight: 140
                        radius: 16
                        color: "#102329"
                        border.color: "#21464b"
                        RowLayout {
                            anchors.fill: parent
                            anchors.margins: 24
                            ColumnLayout {
                                spacing: 7
                                Text { text: "YOUR MEDIA, ONE PLACE"; color: window.accent; font.pixelSize: 10; font.bold: true; font.letterSpacing: 1.7 }
                                Text { text: "Welcome to your media center"; color: window.textPrimary; font.pixelSize: 23; font.bold: true }
                                Text { text: "Connect your providers to start discovering and playing content."; color: window.textMuted; font.pixelSize: 13 }
                            }
                            Item { Layout.fillWidth: true }
                            Button {
                                text: "Connect providers"
                                background: Rectangle { radius: 9; color: window.accent }
                                contentItem: Text { text: parent.text; color: "#071317"; font.bold: true; horizontalAlignment: Text.AlignHCenter; verticalAlignment: Text.AlignVCenter; leftPadding: 12; rightPadding: 12 }
                            }
                        }
                    }

                    GridLayout {
                        Layout.fillWidth: true
                        Layout.leftMargin: 28
                        Layout.rightMargin: 28
                        columns: 3
                        columnSpacing: 14
                        rowSpacing: 14
                        Repeater {
                            model: [
                                { title: "REAL-DEBRID", value: "Not connected", detail: "Add your API token", glyph: "◉" },
                                { title: "TORBOX", value: "Not connected", detail: "Add your API key", glyph: "◉" },
                                { title: "PLAYER", value: "Ready to configure", detail: "MPV playback engine", glyph: "▶" }
                            ]
                            delegate: Rectangle {
                                required property var modelData
                                Layout.fillWidth: true
                                Layout.preferredHeight: 122
                                radius: 13
                                color: window.surface
                                border.color: window.borderColor
                                ColumnLayout {
                                    anchors.fill: parent
                                    anchors.margins: 17
                                    spacing: 8
                                    RowLayout {
                                        Layout.fillWidth: true
                                        Text { text: modelData.title; color: window.textMuted; font.pixelSize: 10; font.bold: true; font.letterSpacing: 1.2 }
                                        Item { Layout.fillWidth: true }
                                        Text { text: modelData.glyph; color: window.accent; font.pixelSize: 16 }
                                    }
                                    Text { text: modelData.value; color: window.textPrimary; font.pixelSize: 17; font.bold: true }
                                    Text { text: modelData.detail; color: window.textMuted; font.pixelSize: 12 }
                                }
                            }
                        }
                    }

                    RowLayout {
                        Layout.fillWidth: true
                        Layout.leftMargin: 28
                        Layout.rightMargin: 28
                        spacing: 16

                        Rectangle {
                            Layout.fillWidth: true
                            Layout.preferredHeight: 250
                            radius: 14
                            color: window.surface
                            border.color: window.borderColor
                            ColumnLayout {
                                anchors.fill: parent
                                anchors.margins: 20
                                spacing: 10
                                Text { text: "DISCOVER CONTENT"; color: window.textPrimary; font.pixelSize: 16; font.bold: true }
                                Text { text: "Search for a movie or TV show, compare available releases, then add or play from a connected provider."; color: window.textMuted; wrapMode: Text.WordWrap; Layout.fillWidth: true; font.pixelSize: 13 }
                                Item { Layout.fillHeight: true }
                                TextField {
                                    Layout.fillWidth: true
                                    placeholderText: "Try a title, e.g. Dune (2021)"
                                    color: window.textPrimary
                                    placeholderTextColor: window.textMuted
                                    background: Rectangle { color: "#09121a"; radius: 9; border.color: window.borderColor }
                                }
                                Button {
                                    text: "Search catalog"
                                    Layout.alignment: Qt.AlignRight
                                    background: Rectangle { color: "#153b3d"; radius: 9; border.color: "#245c5b" }
                                    contentItem: Text { text: parent.text; color: window.accent; font.bold: true; horizontalAlignment: Text.AlignHCenter; verticalAlignment: Text.AlignVCenter; leftPadding: 12; rightPadding: 12 }
                                }
                            }
                        }

                        Rectangle {
                            Layout.fillWidth: true
                            Layout.preferredHeight: 250
                            radius: 14
                            color: window.surface
                            border.color: window.borderColor
                            ColumnLayout {
                                anchors.fill: parent
                                anchors.margins: 20
                                spacing: 12
                                Text { text: "SYSTEM STATUS"; color: window.textPrimary; font.pixelSize: 16; font.bold: true }
                                Repeater {
                                    model: [
                                        { name: "Real-Debrid", state: "Awaiting setup" },
                                        { name: "TorBox", state: "Awaiting setup" },
                                        { name: "Zurg service", state: "Not configured" },
                                        { name: "MPV playback", state: "Optional dependency" }
                                    ]
                                    delegate: RowLayout {
                                        required property var modelData
                                        Layout.fillWidth: true
                                        Text { text: modelData.name; color: window.textMuted; font.pixelSize: 12 }
                                        Item { Layout.fillWidth: true }
                                        Text { text: modelData.state; color: "#c3a86b"; font.pixelSize: 11 }
                                    }
                                }
                                Item { Layout.fillHeight: true }
                            }
                        }
                    }
                    Item { Layout.preferredHeight: 12 }
                }
            }
        }
    }
}
