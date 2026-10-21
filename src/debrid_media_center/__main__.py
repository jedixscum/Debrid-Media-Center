"""Application entry point."""

from __future__ import annotations

import sys
from pathlib import Path

from PySide6.QtCore import QUrl
from PySide6.QtGui import QGuiApplication, QIcon
from PySide6.QtQml import QQmlApplicationEngine

from debrid_media_center.backend import Backend
from debrid_media_center.system_backend import SystemBackend


def main() -> int:
    app = QGuiApplication(sys.argv)
    app.setApplicationName("Debrid Media Center")
    app.setOrganizationName("DebridMediaCenter")
    icon_path = Path(__file__).parent / "qml" / "dmc-icon.svg"
    if icon_path.exists():
        app.setWindowIcon(QIcon(str(icon_path)))

    engine = QQmlApplicationEngine()
    backend = Backend()
    system_backend = SystemBackend()
    engine.rootContext().setContextProperty("backend", backend)
    engine.rootContext().setContextProperty("systemBackend", system_backend)
    qml_file = Path(__file__).parent / "qml" / "Main.qml"
    engine.load(QUrl.fromLocalFile(str(qml_file)))
    if not engine.rootObjects():
        return 1
    return app.exec()


if __name__ == "__main__":
    raise SystemExit(main())
