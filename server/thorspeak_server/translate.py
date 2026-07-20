import asyncio
import logging

from anthropic import AsyncAnthropic
from fastapi import HTTPException

from .config import settings
from .db import Database
from .normalize import normalize, text_key

log = logging.getLogger("thorspeak.translate")

LANG_NAMES = {"en": "English", "th": "Thai"}

_client: AsyncAnthropic | None = None
_inflight: dict[str, asyncio.Future] = {}


def _get_client() -> AsyncAnthropic:
    global _client
    if not settings.anthropic_api_key:
        raise HTTPException(
            status_code=503,
            detail="ANTHROPIC_API_KEY is not configured on the server (.env)",
        )
    if _client is None:
        _client = AsyncAnthropic(api_key=settings.anthropic_api_key)
    return _client


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
        result = await _call_claude(norm, target_lang)
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


async def _call_claude(text: str, target_lang: str) -> str:
    client = _get_client()
    lang_name = LANG_NAMES[target_lang]
    response = await client.messages.create(
        model=settings.claude_model,
        max_tokens=1024,
        system=(
            "You translate Japanese video-game dialogue (often from retro Game Boy "
            f"games) into natural, colloquial {lang_name}. The text comes from OCR "
            "and may contain small recognition errors — translate the most plausible "
            "intended line. Output ONLY the translation, no notes or quotes."
        ),
        messages=[{"role": "user", "content": text}],
    )
    result = "".join(b.text for b in response.content if b.type == "text").strip()
    log.info("translated %r -> %r (%s)", text[:40], result[:40], target_lang)
    return result
