import argparse
import sys
from src.controllers.note_controller import NoteController


def main():
    parser = argparse.ArgumentParser(description="Python CLI Notes App (MVC Architecture)")
    subparsers = parser.add_subparsers(dest="command", required=True, help="Available commands")

    # Add command
    parser_add = subparsers.add_parser("add", help="Add a new note")
    parser_add.add_argument("--title", required=True, help="Title of the note")
    parser_add.add_argument("--body", required=True, help="Body content of the note")

    # List command
    subparsers.add_parser("list", help="List all notes")

    # View command
    parser_view = subparsers.add_parser("view", help="View a specific note by ID")
    parser_view.add_argument("--id", required=True, help="Unique ID of the note")

    # Pin command
    parser_pin = subparsers.add_parser("pin", help="Pin a note by ID")
    parser_pin.add_argument("--id", required=True, help="Unique ID of the note")

    # Unpin command
    parser_unpin = subparsers.add_parser("unpin", help="Unpin a note by ID")
    parser_unpin.add_argument("--id", required=True, help="Unique ID of the note")

    # Delete command
    parser_delete = subparsers.add_parser("delete", help="Delete a note by ID")
    parser_delete.add_argument("--id", required=True, help="Unique ID of the note")

    args = parser.parse_args()
    controller = NoteController()

    if args.command == "add":
        controller.add_note(title=args.title, body=args.body)
    elif args.command == "list":
        controller.list_notes()
    elif args.command == "view":
        controller.view_note(note_id=args.id)
    elif args.command == "pin":
        controller.pin_note(note_id=args.id)
    elif args.command == "unpin":
        controller.unpin_note(note_id=args.id)
    elif args.command == "delete":
        controller.delete_note(note_id=args.id)
    else:
        parser.print_help()


if __name__ == "__main__":
    main()
