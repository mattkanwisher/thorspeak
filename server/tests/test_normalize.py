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


def test_has_speakable():
    from thorspeak_server.normalize import has_speakable

    assert has_speakable("ゆうしゃよ、まおうをたおしてくれ！")
    assert has_speakable("Hello!")
    assert has_speakable("สวัสดี")
    assert not has_speakable("...")
    assert not has_speakable("……")
    assert not has_speakable("!?、。・")
    assert not has_speakable("")


def test_char_overlap_hallucination_guard():
    from thorspeak_server.normalize import char_overlap

    # real capture: manga-ocr and ML Kit read (mostly) the same dialogue,
    # even with a couple of ML Kit misreads on the pixel font
    ocr = "ゆうしゃよ、まおうをたおしてくれ!"
    mlkit_imperfect = "ゆうしゃよ まおうをたむしてくれ"
    assert char_overlap(ocr, mlkit_imperfect) > 0.7

    # actual hallucinations manga-ocr produced on a settings-menu frame
    menu = "メッセージそくどウィンドウカラーおわりはやいふつうおそい"
    assert char_overlap("そのため、", menu) < 0.3
    assert char_overlap("それでも、これまでのお客様においては、", menu) < 0.3

    assert char_overlap("", menu) == 0.0
    assert char_overlap("こんにちは", "こんにちは") == 1.0
