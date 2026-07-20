#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
exec uv run uvicorn thorspeak_server.main:app --host 0.0.0.0 --port "${THORSPEAK_PORT:-8737}"
