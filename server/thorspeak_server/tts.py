"""edge-tts synthesis with a content-addressed disk cache.

Files live at {cache_dir}/audio/{sha256}.mp3. Writes go to a .tmp file then
atomically rename, so a crash mid-synthesis never leaves a half-written file
servable. An in-flight table dedupes concurrent generation of the same hash.
"""

import asyncio
import logging
import time
from pathlib import Path

import edge_tts

from .config import settings
from .normalize import audio_key, normalize

log = logging.getLogger("thorspeak.tts")

_inflight: dict[str, asyncio.Future] = {}


def audio_path(hash_: str) -> Path:
    return settings.audio_dir / f"{hash_}.mp3"


async def synthesize(text: str, lang: str, voice: str) -> tuple[str, bool]:
    """Ensure audio exists for (text, lang, voice). Returns (hash, was_cached)."""
    norm = normalize(text)
    hash_ = audio_key(norm, lang, voice)
    path = audio_path(hash_)
    if path.exists():
        path.touch()  # bump mtime so LRU pruning sees it as recently used
        return hash_, True

    if hash_ in _inflight:
        await asyncio.shield(_inflight[hash_])
        return hash_, False

    loop = asyncio.get_running_loop()
    fut: asyncio.Future = loop.create_future()
    _inflight[hash_] = fut
    try:
        await _generate(norm, voice, path)
        fut.set_result(None)
        return hash_, False
    except Exception as e:
        fut.set_exception(e)
        fut.exception()
        raise
    finally:
        _inflight.pop(hash_, None)


async def _generate(text: str, voice: str, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(".tmp")
    last_err: Exception | None = None
    for attempt in range(3):
        try:
            communicate = edge_tts.Communicate(text, voice)
            await communicate.save(str(tmp))
            tmp.rename(path)
            log.info("synthesized %r with %s -> %s", text[:40], voice, path.name)
            return
        except Exception as e:  # edge-tts is an unofficial endpoint; be tolerant
            last_err = e
            tmp.unlink(missing_ok=True)
            wait = 2**attempt
            log.warning("edge-tts attempt %d failed (%s); retrying in %ds", attempt + 1, e, wait)
            await asyncio.sleep(wait)
    raise RuntimeError(f"edge-tts failed after retries: {last_err}")


def cache_stats() -> tuple[int, int]:
    """Returns (file_count, total_bytes)."""
    files = list(settings.audio_dir.glob("*.mp3")) if settings.audio_dir.exists() else []
    return len(files), sum(f.stat().st_size for f in files)


def prune_cache() -> int:
    """LRU-prune the audio cache (by mtime) down to the configured max size.
    Returns the number of files removed."""
    max_bytes = settings.audio_cache_max_mb * 1024 * 1024
    if not settings.audio_dir.exists():
        return 0
    files = sorted(
        settings.audio_dir.glob("*.mp3"), key=lambda f: f.stat().st_mtime
    )
    total = sum(f.stat().st_size for f in files)
    removed = 0
    for f in files:
        if total <= max_bytes:
            break
        size = f.stat().st_size
        f.unlink(missing_ok=True)
        total -= size
        removed += 1
    if removed:
        log.info("pruned %d audio files from cache", removed)
    # Also clean up stale .tmp files older than an hour
    cutoff = time.time() - 3600
    for tmp in settings.audio_dir.glob("*.tmp"):
        if tmp.stat().st_mtime < cutoff:
            tmp.unlink(missing_ok=True)
    return removed


async def prune_loop() -> None:
    while True:
        await asyncio.sleep(3600)
        try:
            prune_cache()
        except Exception:
            log.exception("cache prune failed")
