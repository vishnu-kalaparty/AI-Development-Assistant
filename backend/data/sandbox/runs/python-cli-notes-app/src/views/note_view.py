class NoteView:
    """Handles terminal formatting and output for notes."""

    @staticmethod
    def display_notes_list(notes: list) -> None:
        if not notes:
            print("No notes found.")
            return

        print("-" * 68)
        print(f"{'ID':<10} | {'PIN':<5} | {'TIMESTAMP':<20} | {'TITLE'}")
        print("-" * 68)
        for note in notes:
            pin_status = "📌" if note.isPinned else " "
            title = note.title if len(note.title) <= 27 else note.title[:24] + "..."
            print(f"{note.id:<10} | {pin_status:<5} | {note.timestamp:<20} | {title}")
        print("-" * 68)
        print(f"Total notes: {len(notes)}")

    @staticmethod
    def display_note_detail(note) -> None:
        if not note:
            print("Error: Note not found.")
            return

        pinned_str = "Yes (📌)" if note.isPinned else "No"
        print("=" * 60)
        print(f"ID:        {note.id}")
        print(f"Title:     {note.title}")
        print(f"Pinned:    {pinned_str}")
        print(f"Timestamp: {note.timestamp}")
        print("-" * 60)
        print(f"Body:\n{note.body}")
        print("=" * 60)

    @staticmethod
    def display_success(message: str) -> None:
        print(f"[SUCCESS] {message}")

    @staticmethod
    def display_error(message: str) -> None:
        print(f"[ERROR] {message}")
