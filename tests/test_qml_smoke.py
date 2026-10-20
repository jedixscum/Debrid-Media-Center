import os
from pathlib import Path

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")
os.environ.setdefault("QT_QUICK_BACKEND", "software")

from PySide6.QtCore import QUrl
from PySide6.QtGui import QGuiApplication
from PySide6.QtQml import QQmlApplicationEngine

from debrid_media_center.backend import Backend


def test_main_qml_loads_without_syntax_errors():
    app = QGuiApplication.instance() or QGuiApplication([])
    engine = QQmlApplicationEngine()
    backend = Backend()
    engine.rootContext().setContextProperty("backend", backend)
    qml_path = Path(__file__).parents[1] / "src" / "debrid_media_center" / "qml" / "Main.qml"
    engine.load(QUrl.fromLocalFile(str(qml_path)))
    assert engine.rootObjects(), "Qt Quick failed to load Main.qml"
    engine.rootObjects()[0].deleteLater()
    engine.deleteLater()
    app.processEvents()
