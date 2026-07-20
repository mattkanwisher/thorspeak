"""Text normalization and cache keying — the single source of truth.

The Android app never computes these hashes; it uses the `audio_hash` the
server returns verbatim. Changing this module invalidates existing caches.
"""

import hashlib
from collections import Counter
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


def char_overlap(text: str, reference: str) -> float:
    """Fraction of text's letters/digits that also appear in reference (multiset).

    Used to catch manga-ocr hallucinations: on menus/no-dialogue frames it
    invents plausible lines instead of returning nothing. Real OCR output
    shares most characters with what on-device ML Kit saw; inventions don't.
    """
    a = Counter(c for c in text if c.isalnum())
    b = Counter(c for c in reference if c.isalnum())
    total = sum(a.values())
    if not total:
        return 0.0
    return sum((a & b).values()) / total


def has_speakable(text: str) -> bool:
    """True if TTS has anything to pronounce — letters or digits in any script.

    Game dialogue is often just "……" (a silence beat); edge-tts errors on it.
    """
    return any(c.isalnum() for c in text)
