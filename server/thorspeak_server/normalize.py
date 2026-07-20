"""Text normalization and cache keying — the single source of truth.

The Android app never computes these hashes; it uses the `audio_hash` the
server returns verbatim. Changing this module invalidates existing caches.
"""

import hashlib
import re
import unicodedata


def normalize(text: str) -> str:
    # NFKC unifies full/half width (Ａ→A, ｶﾞ→ガ); Japanese has no meaningful
    # spaces, and OCR loves to invent them, so drop all whitespace.
    t = unicodedata.normalize("NFKC", text)
    return re.sub(r"\s+", "", t)


def audio_key(text: str, lang: str, voice: str) -> str:
    return hashlib.sha256(f"{normalize(text)}|{lang}|{voice}".encode()).hexdigest()


def text_key(text: str, target_lang: str) -> str:
    return hashlib.sha256(f"{normalize(text)}|{target_lang}".encode()).hexdigest()


def has_speakable(text: str) -> bool:
    """True if TTS has anything to pronounce — letters or digits in any script.

    Game dialogue is often just "……" (a silence beat); edge-tts errors on it.
    """
    return any(c.isalnum() for c in text)
