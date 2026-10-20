"""Threaded provider and media API bridge for the Qt Quick interface."""

from __future__ import annotations

import threading
from typing import Any
from urllib.parse import quote

import keyring
import requests
from PySide6.QtCore import QObject, QSettings, Signal, Slot

KEYRING_SERVICE = "DebridMediaCenter"
DMM_BASE = "https://debridmediamanager.com"
RD_BASE = "https://api.real-debrid.com/rest/1.0"
TB_BASE = "https://api.torbox.app/v1/api"
VIDEO_EXTS = (".mkv", ".mp4", ".m4v", ".avi", ".mov", ".webm", ".ts")


class Backend(QObject):
    statusChanged = Signal(str)
    titlesReady = Signal("QVariantList")
    releasesReady = Signal("QVariantList")
    libraryReady = Signal("QVariantList")
    connectionChanged = Signal(str, str)
    playbackReady = Signal(str, str)
    favoritesChanged = Signal("QVariantList")

    def __init__(self) -> None:
        super().__init__()
        self._settings = QSettings("DebridMediaCenter", "DebridMediaCenter")
        stored = self._settings.value("favorites", [])
        self._favorites: list[dict] = [dict(item) for item in stored if isinstance(item, dict)] if isinstance(stored, list) else []

    @staticmethod
    def _token(provider: str) -> str:
        return keyring.get_password(KEYRING_SERVICE, provider) or ""

    @staticmethod
    def _request(method: str, url: str, *, token: str = "", params: dict | None = None,
                 data: Any = None, json_body: Any = None, headers: dict | None = None,
                 timeout: int = 18) -> Any:
        request_headers = {"Accept": "application/json"}
        if token:
            request_headers["Authorization"] = f"Bearer {token}"
        if headers:
            request_headers.update(headers)
        response = requests.request(method, url, headers=request_headers, params=params,
                                     data=data, json=json_body, timeout=timeout)
        try:
            payload = response.json()
        except ValueError:
            payload = {}
        if not response.ok:
            detail = payload.get("detail") or payload.get("error") or f"HTTP {response.status_code}"
            if response.status_code in (401, 403):
                detail = "Authentication failed. Check the saved key and account access."
            elif response.status_code == 429:
                detail = "Rate limit reached. Wait a moment and retry."
            raise RuntimeError(str(detail))
        return payload

    def _run(self, label: str, operation, success) -> None:
        self.statusChanged.emit(label)

        def target() -> None:
            try:
                result = operation()
                success(result)
                self.statusChanged.emit("Ready")
            except Exception as exc:
                self.statusChanged.emit(f"Error: {exc}")

        threading.Thread(target=target, daemon=True, name="dmc-network").start()

    @Slot(str)
    def searchTitles(self, query: str) -> None:
        query = query.strip()
        if len(query) < 2:
            self.statusChanged.emit("Enter at least two characters to search.")
            self.titlesReady.emit([])
            return

        def operation() -> list[dict]:
            rows = self._request("GET", f"{DMM_BASE}/api/trakt/search",
                                 params={"query": query, "types": "movie,show"}, timeout=15)
            output = []
            for row in rows if isinstance(rows, list) else []:
                kind = row.get("type", "movie")
                media = row.get(kind) or {}
                title = media.get("title")
                if not title:
                    continue
                ids = media.get("ids") or {}
                imdb = ids.get("imdb") or ""
                output.append({
                    "id": str(imdb or ids.get("trakt") or ""),
                    "imdb": str(imdb or ""),
                    "title": str(title),
                    "type": str(kind),
                    "year": str(media.get("year") or ""),
                    "overview": str(media.get("overview") or ""),
                    "poster": f"https://images.metahub.space/poster/medium/{imdb}/img" if str(imdb).startswith("tt") else "",
                })
            return output

        self._run("Searching movie and TV metadata…", operation, self.titlesReady.emit)

    @Slot(str, str, str)
    def searchReleases(self, imdb_id: str, media_type: str, quality: str) -> None:
        imdb_id = imdb_id.strip()
        if not imdb_id.startswith("tt") or not imdb_id[2:].isdigit():
            self.statusChanged.emit("Select a title with a valid IMDb ID.")
            self.releasesReady.emit([])
            return

        def operation() -> list[dict]:
            key = self._token("dmm")
            if not key:
                raise RuntimeError("Save an active DMM sponsor API key in Services to enable release search.")
            kind = "tv" if media_type in ("show", "tv") else "movie"
            response = self._request(
                "POST", f"{DMM_BASE}/api/zurg/search-torrents",
                headers={"x-api-key": key},
                json_body={"imdbId": imdb_id, "mediaType": kind, "quality": quality, "limit": 30},
                timeout=25,
            )
            rows = response.get("results") or []
            if kind == "tv":
                rows = [item for season in (response.get("seasons") or {}).values() for item in season]
            results = []
            for row in rows:
                title = row.get("title")
                info_hash = str(row.get("hash") or "").lower()
                if not title or len(info_hash) != 40 or any(ch not in "0123456789abcdef" for ch in info_hash):
                    continue
                results.append({"title": str(title), "hash": info_hash, "size": float(row.get("fileSize") or 0)})
            return sorted(results, key=lambda item: item["size"], reverse=True)

        self._run("Searching available releases…", operation, self.releasesReady.emit)

    @Slot(str, str)
    def saveKey(self, provider: str, token: str) -> None:
        provider = provider.strip().lower()
        if provider not in ("real_debrid", "torbox", "dmm"):
            self.statusChanged.emit("Unknown service.")
            return
        if not token.strip():
            self.statusChanged.emit("Enter a key before saving.")
            return
        try:
            keyring.set_password(KEYRING_SERVICE, provider, token.strip())
            self.connectionChanged.emit(provider, "Key saved in system keyring")
            self.statusChanged.emit("Key saved securely.")
        except Exception as exc:
            self.statusChanged.emit(f"Could not save key: {exc}")

    @Slot(str)
    def removeKey(self, provider: str) -> None:
        try:
            keyring.delete_password(KEYRING_SERVICE, provider)
        except Exception:
            pass
        self.connectionChanged.emit(provider, "Not configured")
        self.statusChanged.emit("Saved key removed.")

    @Slot(str)
    def testConnection(self, provider: str) -> None:
        def operation() -> str:
            token = self._token(provider)
            if not token:
                raise RuntimeError("Save an API key in Services first.")
            if provider == "real_debrid":
                result = self._request("GET", f"{RD_BASE}/user", token=token)
                return f"Connected · {result.get('username') or 'Real-Debrid account'}"
            if provider == "torbox":
                result = self._request("GET", f"{TB_BASE}/user/me", token=token)
                data = result.get("data") or result
                if result.get("success") is False:
                    raise RuntimeError(result.get("detail") or "TorBox rejected the key.")
                return f"Connected · {data.get('email') or data.get('username') or 'TorBox account'}"
            raise RuntimeError("DMM sponsor keys are checked when release search is used.")

        def done(result: str) -> None:
            self.connectionChanged.emit(provider, result)

        self._run(f"Testing {provider.replace('_', ' ')}…", operation, done)

    @Slot(str, str, result=str)
    def keyStatus(self, provider: str) -> str:
        return "Key saved in system keyring" if self._token(provider) else "Not configured"

    @Slot(str, str)
    def addMagnet(self, provider: str, magnet: str) -> None:
        magnet = magnet.strip()
        if not magnet.lower().startswith("magnet:?xt=urn:btih:"):
            self.statusChanged.emit("Enter a valid magnet link.")
            return

        def operation() -> str:
            token = self._token(provider)
            if not token:
                raise RuntimeError("Connect this provider in Services first.")
            if provider == "real_debrid":
                result = self._request("POST", f"{RD_BASE}/torrents/addMagnet", token=token,
                                       data={"magnet": magnet}, timeout=25)
                item_id = str(result.get("id") or "")
                if item_id:
                    try:
                        self._request("POST", f"{RD_BASE}/torrents/selectFiles/{quote(item_id)}",
                                      token=token, data={"files": "all"})
                    except Exception:
                        return f"Added to Real-Debrid · ID {item_id}. Select files manually if it does not progress."
                return f"Magnet added to Real-Debrid · ID {item_id or 'submitted'}"
            if provider == "torbox":
                result = self._request("POST", f"{TB_BASE}/torrents/createtorrent", token=token,
                                       data={"magnet": magnet}, timeout=25)
                if result.get("success") is False:
                    raise RuntimeError(result.get("detail") or "TorBox could not add this magnet.")
                return result.get("detail") or "Magnet submitted to TorBox."
            raise RuntimeError("Unknown provider.")

        self._run("Adding magnet to provider…", operation, self.statusChanged.emit)

    @Slot(str)
    def loadLibrary(self, provider: str) -> None:
        def operation() -> list[dict]:
            token = self._token(provider)
            if not token:
                raise RuntimeError("Connect this provider in Services first.")
            if provider == "real_debrid":
                rows = self._request("GET", f"{RD_BASE}/torrents", token=token)
            elif provider == "torbox":
                response = self._request("GET", f"{TB_BASE}/torrents/mylist", token=token)
                if response.get("success") is False:
                    raise RuntimeError(response.get("detail") or "Could not load TorBox library.")
                rows = response.get("data") or []
            else:
                raise RuntimeError("Unknown provider.")
            output = []
            for row in rows if isinstance(rows, list) else []:
                progress = float(row.get("progress") or 0)
                if provider == "torbox" and 0 <= progress <= 1:
                    progress *= 100
                output.append({
                    "id": str(row.get("id") or row.get("torrent_id") or ""),
                    "title": str(row.get("filename") or row.get("name") or row.get("title") or "Untitled item"),
                    "status": str(row.get("status") or row.get("download_state") or "Unknown"),
                    "progress": max(0, min(100, int(progress))),
                    "provider": provider,
                })
            return output

        self._run("Loading provider library…", operation, self.libraryReady.emit)

    @Slot(str, str, str)
    def playItem(self, provider: str, item_id: str, title: str) -> None:
        def operation() -> str:
            token = self._token(provider)
            if not token:
                raise RuntimeError("Connect this provider in Services first.")
            if provider == "real_debrid":
                info = self._request("GET", f"{RD_BASE}/torrents/info/{quote(item_id)}", token=token)
                if info.get("status") != "downloaded":
                    raise RuntimeError(f"Item is not ready to play yet (status: {info.get('status', 'unknown')}).")
                files, links = info.get("files") or [], info.get("links") or []
                link_index, chosen = 0, ""
                for file_info in files:
                    if int(file_info.get("selected") or 0) != 1:
                        continue
                    link = links[link_index] if link_index < len(links) else ""
                    link_index += 1
                    if str(file_info.get("path") or "").lower().endswith(VIDEO_EXTS) and link:
                        chosen = link
                        break
                if not chosen and links:
                    chosen = links[0]
                if not chosen:
                    raise RuntimeError("No selected video link. Select a video file in Real-Debrid first.")
                result = self._request("POST", f"{RD_BASE}/unrestrict/link", token=token,
                                       data={"link": chosen}, timeout=25)
                url = result.get("download") or ""
            elif provider == "torbox":
                result = self._request("GET", f"{TB_BASE}/torrents/mylist", token=token,
                                       params={"id": item_id})
                if result.get("success") is False:
                    raise RuntimeError(result.get("detail") or "Could not inspect TorBox item.")
                item = result.get("data")
                if isinstance(item, list):
                    item = item[0] if item else {}
                item = item or {}
                files = item.get("files") or []
                file_info = next((f for f in files if str(f.get("name") or f.get("short_name") or "").lower().endswith(VIDEO_EXTS)), None)
                if not file_info or file_info.get("id") is None:
                    raise RuntimeError("No video file found or the torrent is still processing.")
                result = self._request("GET", f"{TB_BASE}/torrents/requestdl", token=token,
                                       params={"token": token, "torrent_id": item_id, "file_id": file_info["id"]},
                                       timeout=25)
                if result.get("success") is False:
                    raise RuntimeError(result.get("detail") or "TorBox could not create a playback link.")
                url = result.get("data") or ""
            else:
                raise RuntimeError("Unknown provider.")
            if not isinstance(url, str) or not url.startswith("http"):
                raise RuntimeError("Provider did not return a playable URL.")
            return url

        self._run(f"Preparing playback: {title}…", operation,
                  lambda url: self.playbackReady.emit(title, url))

    @Slot(str, str)
    def toggleFavorite(self, title: str, imdb_id: str) -> None:
        match = next((x for x in self._favorites if x.get("imdb") == imdb_id), None)
        if match:
            self._favorites.remove(match)
        else:
            self._favorites.append({"title": title, "imdb": imdb_id})
        self._settings.setValue("favorites", self._favorites)
        self.favoritesChanged.emit(list(self._favorites))

    @Slot(result="QVariantList")
    def favorites(self) -> list[dict]:
        return list(self._favorites)
