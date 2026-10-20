# Debrid Media Center

Debrid Media Center is a standalone project with a native Qt Quick Linux desktop app and a native Android app. It is not a website and is not part of BradFlix.

## Current functionality

### Native Linux desktop
- Dark, teal-accented Qt Quick interface with Overview, Discover, Movies, TV Shows, Favorites, provider pages, Transfers/Library, Zurg & Mounts, Health & Logs, Settings, and Player.
- Movie and TV title search through Debrid Media Manager's public Trakt search endpoint, including poster art when a title has an IMDb ID and a poster is available.
- Optional release search through DMM's Zurg release endpoint. This endpoint requires the user's own **active DMM sponsor API key** and DMM's release index to contain results.
- Add a selected release or a manually supplied magnet to Real-Debrid or TorBox.
- Test Real-Debrid and TorBox credentials, refresh provider libraries, and resolve supported direct playback links.
- Embedded Qt Multimedia playback for media URLs supported by the installed Qt Multimedia backend.
- Favorites persist in local Qt settings. Provider secrets are stored through the operating-system keyring.
- User-systemd controls for configurable Zurg/rclone services, including start/stop/restart and status.
- rclone mount/unmount controls with a configurable remote and mount point, plus system health and recent journal logs.

### Native Android
- Home, Discover, Library, Services, and Settings tabs.
- Movie and TV title search with poster cards.
- Optional DMM release search and one-tap add to Real-Debrid or TorBox.
- Manual magnet submission, TorBox cached availability checks, provider library refresh, and in-app Media3 playback for supported files.
- Provider credentials encrypted using Android Keystore-backed AES/GCM.

## Important provider notes

- DMM release search requires an active sponsor key and an available result in DMM's Zurg-backed index. It is not an unrestricted general-purpose torrent indexer. Manual magnet adding is available without this integration.
- Real-Debrid does not provide the same instant-cache-check capability as TorBox in this app; the Android UI explains this instead of reporting a guessed cache result.
- Playback requires a completed item with a supported direct video link. Some codecs, containers, subtitles, multi-file releases, or provider states may not be playable yet.
- The desktop Zurg/rclone controls operate only on the **current user's systemd session** and do not use sudo or silently modify privileged system services. Configure the actual service names used by your installation.
- rclone mounting requires a working rclone installation, an existing configured rclone remote, FUSE support, and a writable mount point.
- The desktop CI smoke test validates Python compilation and Qt Quick loading; it does not replace testing with real provider accounts and media files.

## Run on CachyOS / Arch Linux (Fish shell)

Install Python and make sure a Secret Service/keyring provider is available in your desktop session. Then:

```fish
cd Debrid-Media-Center
python -m venv .venv
source .venv/bin/activate.fish
python -m pip install --upgrade pip
python -m pip install -e ".[dev]"
mkdir -p ~/.local/share/applications
cp packaging/debrid-media-center.desktop ~/.local/share/applications/
python -m debrid_media_center
```

The desktop app needs an available Linux keyring service for saving API keys. The embedded player uses Qt Multimedia; playback support depends on the installed Qt multimedia backend and media codecs.

## Android test build

Open [GitHub Actions](https://github.com/jedixscum/Debrid-Media-Center/actions) and select the latest successful **Android APK test build** run. Download the `debrid-media-center-android-debug` artifact and install its APK on your Android device. Debug builds are for testing and are not Play Store releases.

## Linux desktop CI artifact

The **Linux desktop test build** workflow runs Python compilation checks, loads the Qt Quick interface offscreen, builds a Python distribution, and uploads a package artifact. Download it from the latest successful [GitHub Actions run](https://github.com/jedixscum/Debrid-Media-Center/actions).

## Security

- Never commit API keys or tokens to the repository.
- Desktop credentials are stored through the OS keyring; Android credentials are encrypted with Android Keystore-backed AES/GCM.
- Authorization headers and temporary playback links should not be posted in screenshots or logs.
- Adding content and starting playback require explicit user action.
- Use the app only with content you are authorized to access.

## Development

- Desktop source: `src/debrid_media_center/`
- Android source: `android/`
- Desktop smoke test: `tests/test_qml_smoke.py`
- CI workflows: `.github/workflows/`
