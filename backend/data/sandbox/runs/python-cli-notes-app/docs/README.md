# Python CLI Notes App

A lightweight, command-line-driven note-taking application in Python utilizing the Model-View-Controller (MVC) architectural pattern and local JSON file persistence.

## Architecture Pattern

**MVC (Model-View-Controller) for CLI** — Adapts the classic MVC pattern to a command-line interface:
- **Models (`src/models/`)**: Data management (`Note` class with `isPinned` attribute), unique ID generation, timestamps, and JSON file persistence (`NoteManager` class with sorting logic prioritizing pinned notes).
- **Views (`src/views/`)**: Terminal output formatting (`NoteView` class) for note lists (showing pin status indicator 📌), details, success confirmations, and error messages.
- **Controllers (`src/controllers/`)**: Command coordination (`NoteController` class) linking user inputs with model actions (`pin`, `unpin`) and view rendering.
- **Entry Point (`src/main.py`)**: CLI argument parsing and dispatching.

## File Tree

```text
docs/
└── README.md
src/
├── __init__.py
├── main.py
├── models/
│   ├── __init__.py
│   └── note_model.py
├── views/
│   ├── __init__.py
│   └── note_view.py
└── controllers/
    ├── __init__.py
    └── note_controller.py
tests/
├── __init__.py
└── test_notes.py
```

## Usage Instructions

### Run the CLI application

```bash
# Add a note
python3 -m src.main add --title "Shopping List" --body "Milk, eggs, bread"

# List all notes (pinned notes appear at the top with a 📌 indicator)
python3 -m src.main list

# View a specific note by ID
python3 -m src.main view --id <NOTE_ID>

# Pin a note by ID
python3 -m src.main pin --id <NOTE_ID>

# Unpin a note by ID
python3 -m src.main unpin --id <NOTE_ID>

# Delete a note by ID
python3 -m src.main delete --id <NOTE_ID>
```

### Run the test suite

```bash
python3 -m unittest discover -s tests -p "test_*.py"
```
