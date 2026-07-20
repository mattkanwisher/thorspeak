"""manga-ocr wrapper. The model (~450 MB) downloads from HuggingFace on first
load; loading takes a few seconds on subsequent startups. Inference is sync and
CPU-bound — callers run it in a thread pool."""

import io
import logging

from PIL import Image

log = logging.getLogger("thorspeak.ocr")


class Ocr:
    def __init__(self) -> None:
        self._mocr = None

    @property
    def loaded(self) -> bool:
        return self._mocr is not None

    def load(self) -> None:
        from manga_ocr import MangaOcr

        log.info("Loading manga-ocr model (downloads ~450 MB on first run)...")
        self._mocr = MangaOcr()
        log.info("manga-ocr model loaded")

    def read(self, image_bytes: bytes) -> str:
        if self._mocr is None:
            raise RuntimeError("OCR model not loaded yet")
        image = Image.open(io.BytesIO(image_bytes)).convert("RGB")
        return self._mocr(image)


ocr = Ocr()
