from thorspeak_server.normalize import audio_key, normalize, text_key


def test_nfkc_unifies_widths():
    assert normalize("Ａｂｃ") == "Abc"
    assert normalize("ｶﾞﾝﾊﾞﾚ") == "ガンバレ"


def test_whitespace_stripped():
    assert normalize("はい 、 そう です\n") == "はい、そうです"
    assert normalize("  ") == ""


def test_keys_stable_and_distinct():
    a = audio_key("はい、そうです", "ja", "ja-JP-NanamiNeural")
    b = audio_key("はい 、そうです", "ja", "ja-JP-NanamiNeural")  # same after normalize
    c = audio_key("はい、そうです", "en", "en-US-JennyNeural")
    assert a == b
    assert a != c
    assert len(a) == 64


def test_text_key_by_lang():
    assert text_key("こんにちは", "en") != text_key("こんにちは", "th")
