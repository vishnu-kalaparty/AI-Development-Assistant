import json
import os
import uuid
from datetime import datetime


class Note:
    """Represents an individual note."""

    def __init__(self, title: str, body: str, note_id: str = None, timestamp: str = None, isPinned: bool = False):
        self.id = note_id or str(uuid.uuid4())[:8]  # Short unique ID
        self.title = title
        self.body = body
        self.timestamp = timestamp or datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        self.isPinned = bool(isPinned)

    def to_dict(self) -> dict:
        return {
            "id": self.id,
            "title": self.title,
            "body": self.body,
            "timestamp": self.timestamp,
            "isPinned": self.isPinned,
        }

    @classmethod
    def from_dict(cls, data: dict) -> "Note":
        return cls(
            note_id=data.get("id"),
            title=data.get("title", ""),
            body=data.get("body", ""),
            timestamp=data.get("timestamp"),
            isPinned=data.get("isPinned", False),
        )


class NoteManager:
    """Manages note collection and JSON file persistence."""

    def __init__(self, storage_file: str = "notes.json"):
        self.storage_file = storage_file
        self.notes = []
        self.load()

    def load(self) -> None:
        """Load notes from the JSON storage file."""
        if not os.path.exists(self.storage_file):
            self.notes = []
            return
        try:
            with open(self.storage_file, "r", encoding="utf-8") as f:
                data = json.load(f)
                if isinstance(data, list):
                    self.notes = [Note.from_dict(item) for item in data]
                else:
                    self.notes = []
        except (json.JSONDecodeError, IOError):
            self.notes = []

    def save(self) -> None:
        """Save current notes to the JSON storage file."""
        try:
            with open(self.storage_file, "w", encoding="utf-8") as f:
                json.dump([note.to_dict() for note in self.notes], f, indent=4)
        except IOError as e:
            raise RuntimeError(f"Failed to save notes to {self.storage_file}: {e}")

    def add_note(self, title: str, body: str) -> Note:
        """Create and add a new note, then persist."""
        note = Note(title=title, body=body)
        self.notes.append(note)
        self.save()
        return note

    def list_notes(self) -> list:
        """Return all notes with pinned notes at the top, sorted by timestamp descending (newest first)."""
        with_index = list(enumerate(self.notes))
        pinned_items = [item for item in with_index if item[1].isPinned]
        unpinned_items = [item for item in with_index if not item[1].isPinned]

        pinned_sorted = sorted(pinned_items, key=lambda item: (item[1].timestamp, item[0]), reverse=True)
        unpinned_sorted = sorted(unpinned_items, key=lambda item: (item[1].timestamp, item[0]), reverse=True)

        return [item[1] for item in pinned_sorted + unpinned_sorted]

    def get_note(self, note_id: str) -> Note:
        """Find and return a note by ID, or None if not found."""
        for note in self.notes:
            if note.id == note_id:
                return note
        return None

    def set_pin_status(self, note_id: str, is_pinned: bool) -> bool:
        """Set the pinned status of a note by ID. Returns True if updated, False if not found."""
        note = self.get_note(note_id)
        if note:
            note.isPinned = is_pinned
            self.save()
            return True
        return False

    def delete_note(self, note_id: str) -> bool:
        """Delete a note by ID. Returns True if deleted, False if not found."""
        note = self.get_note(note_id)
        if note:
            self.notes.remove(note)
            self.save()
            return True
        return False
