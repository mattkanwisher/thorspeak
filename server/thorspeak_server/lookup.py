import logging

from pydantic import BaseModel

from . import llm

log = logging.getLogger("thorspeak.lookup")


class LookupResult(BaseModel):
    word: str
    reading: str
    meaning: str
    part_of_speech: str
    example: str
    example_translation: str


async def lookup(word: str, context: str | None) -> LookupResult:
    ctx = f'\nIt appeared in this game dialogue line: "{context}"' if context else ""
    result = await llm.complete_structured(
        system=(
            "You are a Japanese dictionary for a language learner playing retro "
            "Japanese video games. Given a Japanese word or fragment (possibly with "
            "OCR noise), return its dictionary form and gloss. `reading` is the kana "
            "reading. `meaning` is a concise English gloss. `example` is a short, "
            "natural Japanese example sentence using the word (game-flavored if it "
            "fits), and `example_translation` is its English translation."
        ),
        user=f"Word: {word}{ctx}",
        output=LookupResult,
    )
    log.info("lookup %r -> %s (%s)", word, result.reading, result.meaning[:40])
    return result
