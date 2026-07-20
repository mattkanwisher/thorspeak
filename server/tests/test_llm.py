from thorspeak_server import llm
from thorspeak_server.config import settings


def test_extract_json_plain():
    assert llm.extract_json('{"a": 1}') == '{"a": 1}'


def test_extract_json_fenced():
    assert llm.extract_json('```json\n{"a": 1}\n```') == '{"a": 1}'
    assert llm.extract_json('```\n{"a": 1}\n```') == '{"a": 1}'


def test_extract_json_fence_with_prose():
    text = 'Here you go:\n```json\n{"a": 1}\n```\nHope that helps!'
    assert llm.extract_json(text) == '{"a": 1}'


def test_active_provider_auto(monkeypatch):
    monkeypatch.setattr(settings, "llm_provider", "auto")
    monkeypatch.setattr(settings, "anthropic_api_key", "")
    monkeypatch.setattr(settings, "openrouter_api_key", "")
    assert llm.active_provider() == ""

    monkeypatch.setattr(settings, "openrouter_api_key", "sk-or-x")
    assert llm.active_provider() == "openrouter"

    # anthropic wins in auto when both are set
    monkeypatch.setattr(settings, "anthropic_api_key", "sk-ant-x")
    assert llm.active_provider() == "anthropic"


def test_active_provider_forced_without_key(monkeypatch):
    monkeypatch.setattr(settings, "llm_provider", "openrouter")
    monkeypatch.setattr(settings, "anthropic_api_key", "sk-ant-x")
    monkeypatch.setattr(settings, "openrouter_api_key", "")
    assert llm.active_provider() == ""
