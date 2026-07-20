from pathlib import Path

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict

DEFAULT_VOICES = {
    "ja": "ja-JP-NanamiNeural",
    "en": "en-US-JennyNeural",
    "th": "th-TH-PremwadeeNeural",
}

LANGS = ("ja", "en", "th")


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    anthropic_api_key: str = Field(default="", alias="ANTHROPIC_API_KEY")
    port: int = Field(default=8737, alias="THORSPEAK_PORT")
    cache_dir: Path = Field(default=Path("~/.cache/thorspeak"), alias="THORSPEAK_CACHE_DIR")
    audio_cache_max_mb: int = Field(default=2048, alias="THORSPEAK_AUDIO_CACHE_MAX_MB")
    # "ja:voice,en:voice,th:voice" overrides for the built-in defaults
    default_voices: str = Field(default="", alias="THORSPEAK_DEFAULT_VOICES")
    claude_model: str = Field(default="claude-haiku-4-5", alias="THORSPEAK_CLAUDE_MODEL")

    @property
    def audio_dir(self) -> Path:
        return self.cache_dir.expanduser() / "audio"

    @property
    def db_path(self) -> Path:
        return self.cache_dir.expanduser() / "thorspeak.db"

    def voice_for(self, lang: str) -> str:
        overrides = dict(
            pair.split(":", 1) for pair in self.default_voices.split(",") if ":" in pair
        )
        return overrides.get(lang) or DEFAULT_VOICES[lang]


settings = Settings()
