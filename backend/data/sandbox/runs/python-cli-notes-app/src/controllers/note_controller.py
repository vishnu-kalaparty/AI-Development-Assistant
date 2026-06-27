from src.models.note_model import NoteManager
from src.views.note_view import NoteView


class NoteController:
    """Coordinates business logic between NoteManager and NoteView."""

    def __init__(self, storage_file: str = "notes.json"):
        self.manager = NoteManager(storage_file=storage_file)
        self.view = NoteView()

    def add_note(self, title: str, body: str) -> None:
        if not title or not title.strip():
            self.view.display_error("Note title cannot be empty.")
            return
        if not body:
            body = ""
        note = self.manager.add_note(title=title.strip(), body=body)
        self.view.display_success(f"Note created successfully with ID: {note.id}")

    def list_notes(self) -> None:
        notes = self.manager.list_notes()
        self.view.display_notes_list(notes)

    def view_note(self, note_id: str) -> None:
        if not note_id:
            self.view.display_error("Note ID is required.")
            return
        note = self.manager.get_note(note_id)
        if note:
            self.view.display_note_detail(note)
        else:
            self.view.display_error(f"Note with ID '{note_id}' not found.")

    def pin_note(self, note_id: str) -> None:
        if not note_id:
            self.view.display_error("Note ID is required.")
            return
        success = self.manager.set_pin_status(note_id, True)
        if success:
            self.view.display_success(f"Note with ID '{note_id}' pinned successfully.")
        else:
            self.view.display_error(f"Note with ID '{note_id}' not found.")

    def unpin_note(self, note_id: str) -> None:
        if not note_id:
            self.view.display_error("Note ID is required.")
            return
        success = self.manager.set_pin_status(note_id, False)
        if success:
            self.view.display_success(f"Note with ID '{note_id}' unpinned successfully.")
        else:
            self.view.display_error(f"Note with ID '{note_id}' not found.")

    def delete_note(self, note_id: str) -> None:
        if not note_id:
            self.view.display_error("Note ID is required.")
            return
        success = self.manager.delete_note(note_id)
        if success:
            self.view.display_success(f"Note with ID '{note_id}' deleted successfully.")
        else:
            self.view.display_error(f"Note with ID '{note_id}' not found.")
