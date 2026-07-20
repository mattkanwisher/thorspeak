import tempfile
from pathlib import Path

import genanki

# Stable IDs so re-imports update instead of duplicating (per genanki docs:
# generate once with random.randrange(1 << 30, 1 << 31) and hard-code).
MODEL_ID = 1607392319
DECK_ID = 2059400110

MODEL = genanki.Model(
    MODEL_ID,
    "ThorSpeak Vocab",
    fields=[
        {"name": "Word"},
        {"name": "Reading"},
        {"name": "Meaning"},
        {"name": "PartOfSpeech"},
        {"name": "Example"},
        {"name": "ExampleTranslation"},
        {"name": "Source"},
    ],
    templates=[
        {
            "name": "Recognition",
            "qfmt": '<div style="font-size: 48px; text-align: center;">{{Word}}</div>',
            "afmt": (
                "{{FrontSide}}<hr id=answer>"
                '<div style="text-align: center;">'
                '<div style="font-size: 24px;">{{Reading}}</div>'
                '<div style="font-size: 20px; margin-top: 8px;">{{Meaning}}</div>'
                '<div style="color: #888;">{{PartOfSpeech}}</div>'
                '<div style="margin-top: 12px;"><i>{{Example}}</i><br>{{ExampleTranslation}}</div>'
                '<div style="margin-top: 12px; font-size: 12px; color: #aaa;">{{Source}}</div>'
                "</div>"
            ),
        }
    ],
)


def build_apkg(cards: list[dict]) -> Path:
    deck = genanki.Deck(DECK_ID, "ThorSpeak")
    for card in cards:
        note = genanki.Note(
            model=MODEL,
            fields=[
                card["word"],
                card["reading"],
                card["meaning"],
                card.get("part_of_speech") or "",
                card.get("example") or "",
                card.get("example_translation") or "",
                card.get("source_text") or "",
            ],
            guid=genanki.guid_for(card["word"], card["reading"]),
        )
        deck.add_note(note)
    out = Path(tempfile.mkstemp(suffix=".apkg")[1])
    genanki.Package(deck).write_to_file(str(out))
    return out
