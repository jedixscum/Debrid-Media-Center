"""Local service, mount, and diagnostics controls for the Linux desktop app."""

from __future__ import annotations

import shutil
import subprocess
from pathlib import Path

from PySide6.QtCore import QObject, QSettings, Signal, Slot


class SystemBackend(QObject):
    statusChanged = Signal(str)
    serviceChanged = Signal(str, str)
    mountChanged = Signal(str, str)
    infoReady = Signal("QVariantMap")
    logsReady = Signal(str)

    def __init__(self) -> None:
        super().__init__()
        self.settings = QSettings("DebridMediaCenter", "DebridMediaCenter")

    def _setting(self, key: str, default: str) -> str:
        return str(self.settings.value(key, default) or default).strip()

    def _run(self, args: list[str], timeout: int = 8) -> tuple[int, str]:
        try:
            result = subprocess.run(args, capture_output=True, text=True, timeout=timeout, check=False)
            return result.returncode, (result.stdout or result.stderr or "").strip()
        except FileNotFoundError:
            return 127, f"Command not found: {args[0]}"
        except subprocess.TimeoutExpired:
            return 124, "Command timed out."

    @Slot(str, result=str)
    def serviceStatus(self, service: str) -> str:
        service = service.strip()
        if not service:
            return "Not configured"
        code, output = self._run(["systemctl", "--user", "is-active", service])
        return output or ("active" if code == 0 else "inactive")

    @Slot(str)
    def startService(self, service: str) -> None:
        self._serviceAction("start", service)

    @Slot(str)
    def stopService(self, service: str) -> None:
        self._serviceAction("stop", service)

    @Slot(str)
    def restartService(self, service: str) -> None:
        self._serviceAction("restart", service)

    def _serviceAction(self, action: str, service: str) -> None:
        service = service.strip()
        if not service:
            self.statusChanged.emit("Enter a user systemd service name first.")
            return
        code, output = self._run(["systemctl", "--user", action, service], timeout=15)
        self.serviceChanged.emit(service, self.serviceStatus(service))
        self.statusChanged.emit(f"{service}: {action} completed." if code == 0 else f"{service}: {output or 'systemctl failed'}")

    @Slot(str, str)
    def saveConfig(self, zurg_service: str, rclone_service: str) -> None:
        self.settings.setValue("zurg_service", zurg_service.strip() or "zurg")
        self.settings.setValue("rclone_service", rclone_service.strip() or "rclone")
        self.statusChanged.emit("Service configuration saved.")

    @Slot(str, str)
    def saveMountConfig(self, remote: str, mountpoint: str) -> None:
        self.settings.setValue("rclone_remote", remote.strip())
        self.settings.setValue("rclone_mountpoint", mountpoint.strip())
        self.statusChanged.emit("Mount configuration saved.")

    @Slot(result="QVariantMap")
    def config(self) -> dict:
        return {
            "zurgService": self._setting("zurg_service", "zurg"),
            "rcloneService": self._setting("rclone_service", "rclone"),
            "remote": self._setting("rclone_remote", ""),
            "mountpoint": self._setting("rclone_mountpoint", str(Path.home() / "zurg")),
        }

    @Slot(result=str)
    def mountStatus(self) -> str:
        mountpoint = self._setting("rclone_mountpoint", str(Path.home() / "zurg"))
        code, output = self._run(["findmnt", "-T", mountpoint, "-o", "TARGET", "--noheadings"])
        return "mounted" if code == 0 and output else "not mounted"

    @Slot()
    def startMount(self) -> None:
        remote = self._setting("rclone_remote", "")
        mountpoint = self._setting("rclone_mountpoint", str(Path.home() / "zurg"))
        if not remote or not mountpoint:
            self.statusChanged.emit("Set an rclone remote and mount point first.")
            return
        target = str(Path(mountpoint).expanduser())
        Path(target).mkdir(parents=True, exist_ok=True)
        code, output = self._run(["rclone", "mount", remote, target, "--daemon", "--vfs-cache-mode", "full", "--dir-cache-time", "1m"], timeout=20)
        self.mountChanged.emit(mountpoint, self.mountStatus())
        self.statusChanged.emit(f"rclone mounted {remote} at {mountpoint}." if code == 0 else (output or "rclone mount failed."))

    @Slot()
    def stopMount(self) -> None:
        mountpoint = self._setting("rclone_mountpoint", str(Path.home() / "zurg"))
        target = str(Path(mountpoint).expanduser())
        for tool in ("fusermount3", "fusermount"):
            if shutil.which(tool):
                code, output = self._run([tool, "-u", target], timeout=10)
                if code == 0:
                    self.mountChanged.emit(mountpoint, "not mounted")
                    self.statusChanged.emit(f"Unmounted {mountpoint}.")
                    return
        self.mountChanged.emit(mountpoint, self.mountStatus())
        self.statusChanged.emit("Could not unmount the configured mount point.")

    @Slot()
    def refresh(self) -> None:
        cfg = self.config()
        self.infoReady.emit({
            "zurg": self.serviceStatus(cfg["zurgService"]),
            "rclone": self.serviceStatus(cfg["rcloneService"]),
            "mount": self.mountStatus(),
            "rcloneInstalled": bool(shutil.which("rclone")),
            "systemctlInstalled": bool(shutil.which("systemctl")),
            "findmntInstalled": bool(shutil.which("findmnt")),
        })
        self.statusChanged.emit("System status refreshed.")

    @Slot()
    def loadLogs(self) -> None:
        cfg = self.config()
        blocks: list[str] = []
        for label, service in (("ZURG", cfg["zurgService"]), ("RCLONE SERVICE", cfg["rcloneService"])):
            if not service:
                continue
            code, output = self._run(["journalctl", "--user", "-u", service, "-n", "80", "--no-pager", "-o", "short-iso"], timeout=10)
            blocks.append(f"===== {label}: {service} =====\n{output or ('No journal output (exit ' + str(code) + ').')}")
        self.logsReady.emit("\n\n".join(blocks))

    @Slot(result="QVariantMap")
    def diagnostics(self) -> dict:
        code, kernel = self._run(["uname", "-sr"])
        return {
            "kernel": kernel if code == 0 else "Unavailable",
            "rclone": shutil.which("rclone") or "Not installed",
            "systemctl": shutil.which("systemctl") or "Not installed",
            "findmnt": shutil.which("findmnt") or "Not installed",
            "home": str(Path.home()),
        }
