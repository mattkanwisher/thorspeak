import asyncio
import logging
import os
from contextlib import asynccontextmanager

import anyio.to_thread
from fastapi import BackgroundTasks, FastAPI, File, Form, HTTPException, UploadFile
from fastapi.responses import FileResponse
from pydantic import BaseModel

from . import __version__
from .config import LANGS, settings
from .db import Database
from .normalize import normalize
from .ocr import ocr
from . import anki, translate as tr, tts
from .lookup import LookupResult, lookup

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(name)s %(levelname)s %(message)s")
log = logging.getLogger("thorspeak")

db: Database


@asynccontextmanager
async def lifespan(app: FastAPI):
    global db
    settings.audio_dir.mkdir(parents=True, exist_ok=True)
    db = Database(settings.db_path)
    tts.prune_cache()
    prune_task = asyncio.create_task(tts.prune_loop())
    if os.environ.get("THORSPEAK_SKIP_OCR") != "1":
        await anyio.to_thread.run_sync(ocr.load)
    else:
        log.warning("THORSPEAK_SKIP_OCR=1 — OCR endpoints will return 503")
    yield
    prune_task.cancel()


app = FastAPI(title="ThorSpeak", version=__version__, lifespan=lifespan)


# --- models ---------------------------------------------------------------


class SpeakRequest(BaseModel):
    text: str
    lang: str
    voice: str | None = None


class LookupRequest(BaseModel):
    word: str
    context: str | None = None


class FlashcardIn(LookupResult):
    source_text: str | None = None


class CachedFlags(BaseModel):
    translation: bool
    tts: bool


class ProcessResponse(BaseModel):
    text: str
    normalized: str
    lang: str
    translation: str | None
    voice: str | None
    audio_hash: str | None
    audio_url: str | None
    cached: CachedFlags


# --- helpers --------------------------------------------------------------


def _check_lang(lang: str) -> None:
    if lang not in LANGS:
        raise HTTPException(400, f"lang must be one of {LANGS}")


async def _speak_pipeline(text: str, lang: str, voice: str | None) -> ProcessResponse:
    """Shared translate-then-synthesize pipeline for /process and /speak."""
    _check_lang(lang)
    norm = normalize(text)
    if not norm:
        return ProcessResponse(
            text=text, normalized="", lang=lang, translation=None, voice=None,
            audio_hash=None, audio_url=None,
            cached=CachedFlags(translation=False, tts=False),
        )

    translation, trans_cached = (None, False)
    speak_text = norm
    if lang != "ja":
        translation, trans_cached = await tr.translate(db, norm, lang)
        speak_text = translation

    voice = voice or settings.voice_for(lang)
    audio_hash, tts_cached = await tts.synthesize(speak_text, lang, voice)
    return ProcessResponse(
        text=text,
        normalized=norm,
        lang=lang,
        translation=translation,
        voice=voice,
        audio_hash=audio_hash,
        audio_url=f"/audio/{audio_hash}.mp3",
        cached=CachedFlags(translation=trans_cached, tts=tts_cached),
    )


# --- endpoints ------------------------------------------------------------


@app.get("/health")
def health():
    return {"status": "ok", "ocr_model_loaded": ocr.loaded, "version": __version__}


@app.post("/ocr")
async def ocr_endpoint(image: UploadFile = File(...)):
    if not ocr.loaded:
        raise HTTPException(503, "OCR model not loaded")
    data = await image.read()
    text = await anyio.to_thread.run_sync(ocr.read, data)
    return {"text": text, "normalized": normalize(text), "confidence": None}


@app.post("/process", response_model=ProcessResponse)
async def process(
    image: UploadFile = File(...),
    lang: str = Form(...),
    voice: str | None = Form(None),
):
    if not ocr.loaded:
        raise HTTPException(503, "OCR model not loaded")
    data = await image.read()
    text = await anyio.to_thread.run_sync(ocr.read, data)
    return await _speak_pipeline(text, lang, voice)


@app.post("/speak", response_model=ProcessResponse)
async def speak(req: SpeakRequest):
    return await _speak_pipeline(req.text, req.lang, req.voice)


@app.get("/audio/{hash_}.mp3")
def audio(hash_: str):
    if not all(c in "0123456789abcdef" for c in hash_) or len(hash_) != 64:
        raise HTTPException(400, "invalid hash")
    path = tts.audio_path(hash_)
    if not path.exists():
        raise HTTPException(404, "not cached")
    return FileResponse(
        path,
        media_type="audio/mpeg",
        headers={"Cache-Control": "public, max-age=31536000, immutable"},
    )


@app.post("/lookup", response_model=LookupResult)
async def lookup_endpoint(req: LookupRequest):
    return await lookup(req.word, req.context)


@app.post("/flashcards", status_code=201)
def create_flashcard(card: FlashcardIn):
    stored = db.add_flashcard(card.model_dump())
    if stored is None:
        raise HTTPException(409, "flashcard already exists for this word/reading")
    return stored


@app.get("/flashcards")
def list_flashcards():
    return db.list_flashcards()


@app.delete("/flashcards/{card_id}", status_code=204)
def delete_flashcard(card_id: int):
    if not db.delete_flashcard(card_id):
        raise HTTPException(404, "no such flashcard")


@app.get("/anki/export.apkg")
def anki_export(background_tasks: BackgroundTasks):
    cards = db.list_flashcards()
    if not cards:
        raise HTTPException(404, "no flashcards to export")
    path = anki.build_apkg(cards)
    background_tasks.add_task(path.unlink, missing_ok=True)
    return FileResponse(
        path, media_type="application/octet-stream", filename="thorspeak.apkg"
    )


@app.get("/cache/stats")
def cache_stats():
    files, size = tts.cache_stats()
    return {"audio_files": files, "audio_bytes": size, **db.counts()}
