# Debrid Media Center

A native Linux desktop media and debrid manager, built as a standalone project.

> **Status:** Early development. The repository is being scaffolded; provider operations and playback must be implemented and tested before daily use.

## Goals

- Native-feeling desktop UI inspired by modern media-server dashboards.
- Search movies and TV shows, inspect release candidates, and add supported content to connected providers.
- Real-Debrid and TorBox provider adapters with clear provider-specific capability handling.
- Embedded MPV playback for supported direct media URLs.
- Library, favorites, watch progress, logs, diagnostics, and provider health.
- Optional Zurg/rclone management, kept separate from provider search and playback.
- Secure local credential storage; never commit API keys or tokens.

## Planned stack

- Python 3.12+
- PySide6 / Qt Quick (QML) for the desktop UI
- python-mpv / libmpv for playback
- SQLite for local preferences and playback state
- Linux Secret Service (via a supported keyring integration) for provider credentials

The exact provider API workflows will be implemented against the providers' current official documentation. Features unavailable from a provider will be disabled or clearly marked rather than simulated.

## Development

A starter environment on Arch-based distributions such as CachyOS:

```fish
sudo pacman -S --needed python python-pip mpv libmpv qt6-declarative qt6-multimedia
cd Debrid-Media-Center
python -m venv .venv
source .venv/bin/activate.fish
python -m pip install -e ".[dev]"
python -m debrid_media_center
```

Package names and Qt platform dependencies may vary by installation.

## Security

- Do not store API tokens in source files, screenshots, logs, or issue reports.
- Keep secrets in the desktop keyring; redact authorization headers and URLs containing tokens.
- Provider actions such as adding or deleting content require an explicit user action.
- Only use the app with content and accounts you are authorized to access.

## License

License to be selected before the first public release.
