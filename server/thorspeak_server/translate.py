import asyncio
import logging

from . import llm
from .db import Database
from .normalize import normalize, text_key

log = logging.getLogger("thorspeak.translate")

LANG_NAMES = {"en": "English", "th": "Thai"}

_inflight: dict[str, asyncio.Future] = {}


async def translate(db: Database, text: str, target_lang: str) -> tuple[str, bool]:
    """Translate Japanese game text. Returns (translation, was_cached)."""
    norm = normalize(text)
    key = text_key(norm, target_lang)

    cached = db.get_translation(key)
    if cached is not None:
        return cached, True

    # In-flight dedupe: concurrent requests for the same line await one call.
    if key in _inflight:
        return await asyncio.shield(_inflight[key]), False

    loop = asyncio.get_running_loop()
    fut: asyncio.Future = loop.create_future()
    _inflight[key] = fut
    try:
        result = await _call_llm(norm, target_lang)
        db.put_translation(key, norm, target_lang, result)
        fut.set_result(result)
        return result, False
    except Exception as e:
        fut.set_exception(e)
        # Consume the exception so an un-awaited future doesn't log a warning.
        fut.exception()
        raise
    finally:
        _inflight.pop(key, None)


async def _call_llm(text: str, target_lang: str) -> str:
    lang_name = LANG_NAMES[target_lang]
    result = await llm.complete(
        system=(
            "You translate Japanese video-game dialogue (often from retro Game Boy "
            f"games) into natural, colloquial {lang_name}. The text comes from OCR "
            "and may contain small recognition errors — translate the most plausible "
            "intended line. Output ONLY the translation, no notes or quotes."
        ),
        user=text,
    )
    log.info("translated %r -> %r (%s)", text[:40], result[:40], target_lang)
    return result
