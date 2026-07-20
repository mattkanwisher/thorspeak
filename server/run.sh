#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
# systemd user services don't have ~/.local/bin (uv) on PATH
export PATH="$HOME/.local/bin:$PATH"
exec uv run uvicorn thorspeak_server.main:app --host 0.0.0.0 --port "${THORSPEAK_PORT:-8737}"
