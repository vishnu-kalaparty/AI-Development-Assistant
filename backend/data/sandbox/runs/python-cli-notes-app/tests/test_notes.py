import os
import tempfile
import unittest

from src.models.note_model import Note, NoteManager
from src.controllers.note_controller import NoteController
from src.views.note_view import NoteView


class TestNoteModelAndManager(unittest.TestCase):

    def setUp(self):
        self.test_dir = tempfile.TemporaryDirectory()
        self.storage_path = os.path.join(self.test_dir.name, "test_notes.json")
        self.manager = NoteManager(storage_file=self.storage_path)

    def tearDown(self):
        self.test_dir.cleanup()

    def test_note_creation(self):
        note = Note(title="Test Title", body="Test Body")
        self.assertEqual(note.title, "Test Title")
        self.assertEqual(note.body, "Test Body")
        self.assertFalse(note.isPinned)
        self.assertTrue(note.id)
        self.assertTrue(note.timestamp)

        d = note.to_dict()
        self.assertIn("isPinned", d)
        note2 = Note.from_dict(d)
        self.assertEqual(note.id, note2.id)
        self.assertEqual(note.title, note2.title)
        self.assertEqual(note.body, note2.body)
        self.assertEqual(note.isPinned, note2.isPinned)

    def test_add_and_list_notes(self):
        self.assertEqual(len(self.manager.list_notes()), 0)
        note = self.manager.add_note("Note 1", "Body 1")
        notes = self.manager.list_notes()
        self.assertEqual(len(notes), 1)
        self.assertEqual(notes[0].id, note.id)
        self.assertEqual(notes[0].title, "Note 1")

    def test_pin_and_sorting_logic(self):
        # Add note 1 (older)
        note1 = self.manager.add_note("Note 1", "Body 1")
        # Add note 2 (newer)
        note2 = self.manager.add_note("Note 2", "Body 2")

        # Initially, newest first (Note 2 then Note 1)
        notes = self.manager.list_notes()
        self.assertEqual(notes[0].id, note2.id)
        self.assertEqual(notes[1].id, note1.id)

        # Pin Note 1 (older note). Pinned notes should come first regardless of timestamp.
        success = self.manager.set_pin_status(note1.id, True)
        self.assertTrue(success)

        notes_pinned = self.manager.list_notes()
        self.assertEqual(notes_pinned[0].id, note1.id)
        self.assertTrue(notes_pinned[0].isPinned)
        self.assertEqual(notes_pinned[1].id, note2.id)
        self.assertFalse(notes_pinned[1].isPinned)

        # Unpin Note 1. Should return to chronological (Note 2 then Note 1)
        success_unpin = self.manager.set_pin_status(note1.id, False)
        self.assertTrue(success_unpin)

        notes_unpinned = self.manager.list_notes()
        self.assertEqual(notes_unpinned[0].id, note2.id)
        self.assertEqual(notes_unpinned[1].id, note1.id)

    def test_persistence_with_pin(self):
        note = self.manager.add_note("Persistent Note", "Persistent Body")
        self.manager.set_pin_status(note.id, True)

        # Create a new manager pointing to the same file
        manager2 = NoteManager(storage_file=self.storage_path)
        notes = manager2.list_notes()
        self.assertEqual(len(notes), 1)
        self.assertEqual(notes[0].id, note.id)
        self.assertTrue(notes[0].isPinned)

    def test_get_and_delete_note(self):
        note = self.manager.add_note("To Delete", "Will be deleted")
        found = self.manager.get_note(note.id)
        self.assertIsNotNone(found)
        self.assertEqual(found.title, "To Delete")

        deleted = self.manager.delete_note(note.id)
        self.assertTrue(deleted)
        self.assertIsNone(self.manager.get_note(note.id))
        self.assertEqual(len(self.manager.list_notes()), 0)

        # Deleting non-existent note
        deleted_again = self.manager.delete_note("nonexistent")
        self.assertFalse(deleted_again)


class TestNoteController(unittest.TestCase):

    def setUp(self):
        self.test_dir = tempfile.TemporaryDirectory()
        self.storage_path = os.path.join(self.test_dir.name, "test_notes.json")
        self.controller = NoteController(storage_file=self.storage_path)

    def tearDown(self):
        self.test_dir.cleanup()

    def test_controller_actions(self):
        # Add via controller
        self.controller.add_note("Ctrl Note", "Ctrl Body")
        notes = self.controller.manager.list_notes()
        self.assertEqual(len(notes), 1)
        note_id = notes[0].id

        # View existing note
        self.controller.view_note(note_id)

        # Pin note
        self.controller.pin_note(note_id)
        self.assertTrue(self.controller.manager.get_note(note_id).isPinned)

        # Unpin note
        self.controller.unpin_note(note_id)
        self.assertFalse(self.controller.manager.get_note(note_id).isPinned)

        # Pin / Unpin non-existent note
        self.controller.pin_note("bad-id")
        self.controller.unpin_note("bad-id")

        # View non-existent note
        self.controller.view_note("bad-id")

        # Delete note
        self.controller.delete_note(note_id)
        self.assertEqual(len(self.controller.manager.list_notes()), 0)

        # Delete non-existent note
        self.controller.delete_note("bad-id")


if __name__ == "__main__":
    unittest.main()
