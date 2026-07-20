"""LLM provider layer: direct Anthropic or any OpenAI-compatible endpoint.

Provider selection (THORSPEAK_LLM_PROVIDER):
  - "auto" (default): anthropic if ANTHROPIC_API_KEY is set, else openrouter
    if OPENROUTER_API_KEY is set.
  - "anthropic" / "openrouter": forced.

"openrouter" speaks the OpenAI chat/completions protocol, so pointing
THORSPEAK_OPENROUTER_BASE_URL at Ollama, llama.cpp, vLLM etc. also works.
"""

import asyncio
import json
import logging
import re

import httpx
from anthropic import AsyncAnthropic
from fastapi import HTTPException
from pydantic import BaseModel

from .config import settings

log = logging.getLogger("thorspeak.llm")

_anthropic: AsyncAnthropic | None = None
_http: httpx.AsyncClient | None = None


def active_provider() -> str:
    """Resolve the configured provider; "" if no usable key is present."""
    p = settings.llm_provider
    if p == "anthropic":
        return p if settings.anthropic_api_key else ""
    if p == "openrouter":
        return p if settings.openrouter_api_key else ""
    # auto
    if settings.anthropic_api_key:
        return "anthropic"
    if settings.openrouter_api_key:
        return "openrouter"
    return ""


def _require_provider() -> str:
    p = active_provider()
    if not p:
        raise HTTPException(
            status_code=503,
            detail=(
                "No LLM configured on the server: set ANTHROPIC_API_KEY or "
                "OPENROUTER_API_KEY in server/.env (see .env.example)"
            ),
        )
    return p


async def complete(system: str, user: str) -> str:
    """Plain text completion via the active provider."""
    if _require_provider() == "anthropic":
        response = await _get_anthropic().messages.create(
            model=settings.claude_model,
            max_tokens=1024,
            system=system,
            messages=[{"role": "user", "content": user}],
        )
        return "".join(b.text for b in response.content if b.type == "text").strip()
    return (await _openrouter_chat(system, user)).strip()


async def complete_structured(system: str, user: str, output: type[BaseModel]) -> BaseModel:
    """Completion parsed into `output`. Anthropic uses native structured output;
    OpenAI-compatible endpoints get the schema in the prompt and must reply with
    raw JSON (fenced JSON is tolerated)."""
    if _require_provider() == "anthropic":
        response = await _get_anthropic().messages.parse(
            model=settings.claude_model,
            max_tokens=1024,
            system=system,
            messages=[{"role": "user", "content": user}],
            output_format=output,
        )
        if response.parsed_output is None:
            raise RuntimeError("LLM returned no parseable output")
        return response.parsed_output

    schema = json.dumps(output.model_json_schema())
    system = (
        f"{system}\n\nRespond with a single JSON object matching this JSON schema, "
        f"and nothing else:\n{schema}"
    )
    text = await _openrouter_chat(system, user)
    return output.model_validate_json(extract_json(text))


def extract_json(text: str) -> str:
    """Strip markdown code fences some models wrap around JSON output."""
    text = text.strip()
    m = re.search(r"```(?:json)?\s*(.*?)\s*```", text, re.DOTALL)
    return m.group(1) if m else text


def _get_anthropic() -> AsyncAnthropic:
    global _anthropic
    if _anthropic is None:
        _anthropic = AsyncAnthropic(api_key=settings.anthropic_api_key)
    return _anthropic


async def _openrouter_chat(system: str, user: str) -> str:
    global _http
    if _http is None:
        _http = httpx.AsyncClient(timeout=60)
    url = settings.openrouter_base_url.rstrip("/") + "/chat/completions"
    body = {
        "model": settings.openrouter_model,
        "max_tokens": 1024,
        "messages": [
            {"role": "system", "content": system},
            {"role": "user", "content": user},
        ],
    }
    headers = {
        "Authorization": f"Bearer {settings.openrouter_api_key}",
        "X-Title": "ThorSpeak",
    }
    last_error: Exception | None = None
    for attempt in range(3):
        if attempt:
            await asyncio.sleep(2**attempt)
        try:
            r = await _http.post(url, json=body, headers=headers)
        except httpx.TransportError as e:
            last_error = e
            continue
        if r.status_code == 429 or r.status_code >= 500:
            last_error = HTTPException(502, f"LLM endpoint returned {r.status_code}")
            continue
        if r.status_code != 200:
            raise HTTPException(
                status_code=502,
                detail=f"LLM endpoint error {r.status_code}: {r.text[:200]}",
            )
        data = r.json()
        try:
            content = data["choices"][0]["message"]["content"]
        except (KeyError, IndexError, TypeError):
            raise HTTPException(502, f"Unexpected LLM response shape: {str(data)[:200]}")
        if content is None:
            raise HTTPException(502, "LLM returned empty content")
        return content
    raise last_error if last_error else RuntimeError("unreachable")
