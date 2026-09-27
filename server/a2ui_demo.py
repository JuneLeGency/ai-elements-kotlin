"""A2UI v1.0 surfaces for the demo (https://a2ui.org), and the user actions that come back.

The messages follow the A2UI v1.0 envelope and Basic Catalog; `record_fixtures.py` validates them
with the official `a2ui-core` SDK. Each protocol carries them on its A2UI binding:

- AG-UI: an `a2ui-surface` ACTIVITY_SNAPSHOT with `a2ui_operations` (as `@ag-ui/a2ui-middleware`);
  a user action returns in `RunAgentInput.forwardedProps.a2uiAction.userAction`.
- AI SDK: a `data-a2ui` part; a user action returns as a `data-a2ui` part of the user message.
- A2A: a DataPart with `metadata.mimeType = "application/a2ui+json"` (A2UI A2A extension).
"""

from __future__ import annotations

from typing import Any

VERSION = "v1.0"
BASIC_CATALOG = "https://a2ui.org/specification/v1_0/catalogs/basic/catalog.json"
MEDIA_TYPE = "application/a2ui+json"


def booking_form(city: str) -> list[dict[str, Any]]:
    """A hotel booking form: name, date and room, and a Book button that sends them back."""
    surface = f"booking-{city.lower().replace(' ', '-')}"
    components = [
        {"id": "root", "component": "Card", "child": "form"},
        {"id": "form", "component": "Column", "children": ["title", "subtitle", "guest", "date", "room", "actions"]},
        {"id": "title", "component": "Text", "text": {"call": "formatString", "args": {"value": "## Stay in ${/city}"}}},
        {"id": "subtitle", "component": "Text", "text": "Hotel Lumen · from $180 / night", "variant": "caption"},
        {"id": "guest", "component": "TextField", "label": "Guest name", "value": {"path": "/booking/guest"},
         "checks": [{"condition": {"call": "required", "args": {"value": {"path": "/booking/guest"}}}, "message": "Enter the guest's name"}]},
        {"id": "date", "component": "DateTimeInput", "label": "Check-in", "value": {"path": "/booking/date"}, "enableDate": True},
        {"id": "room", "component": "ChoicePicker", "label": "Room", "variant": "mutuallyExclusive", "displayStyle": "chips",
         "options": [{"label": "Standard", "value": "standard"}, {"label": "Deluxe", "value": "deluxe"}, {"label": "Suite", "value": "suite"}],
         "value": {"path": "/booking/room"}},
        {"id": "actions", "component": "Row", "children": ["book"], "justify": "end"},
        {"id": "book_label", "component": "Text", "text": "Book"},
        {"id": "book", "component": "Button", "child": "book_label", "variant": "primary",
         "checks": [{"condition": {"call": "required", "args": {"value": {"path": "/booking/guest"}}}, "message": "Enter the guest's name"}],
         "action": {"event": {"name": "book_hotel", "userMessage": {"call": "formatString", "args": {"value": "Book Hotel Lumen for ${/booking/guest}"}},
                              "context": {"city": {"path": "/city"}, "guest": {"path": "/booking/guest"}, "date": {"path": "/booking/date"}, "room": {"path": "/booking/room"}}}}},
    ]
    return [
        {"version": VERSION, "createSurface": {"surfaceId": surface, "catalogId": BASIC_CATALOG, "sendDataModel": True}},
        {"version": VERSION, "updateComponents": {"surfaceId": surface, "components": components}},
        {"version": VERSION, "updateDataModel": {"surfaceId": surface, "value": {"city": city, "booking": {"guest": "", "date": "2026-10-01", "room": ["deluxe"]}}}},
    ]


def action_from_agui(body: dict[str, Any]) -> dict[str, Any] | None:
    """The A2UI user action of an AG-UI run (`forwardedProps.a2uiAction.userAction`)."""
    return ((body.get("forwardedProps") or {}).get("a2uiAction") or {}).get("userAction")


def action_from_ui_messages(body: dict[str, Any]) -> dict[str, Any] | None:
    """The A2UI user action in the latest AI SDK user message (a `data-a2ui` part)."""
    for message in reversed(body.get("messages") or []):
        if message.get("role") != "user":
            continue
        for part in message.get("parts") or []:
            if part.get("type") == "data-a2ui":
                return next((m["action"] for m in part.get("data") or [] if isinstance(m, dict) and "action" in m), None)
        return None
    return None


def describe(action: dict[str, Any]) -> str:
    """The action for the model's instructions."""
    ctx = action.get("context") or {}
    return f"The user submitted '{action.get('name')}' on surface '{action.get('surfaceId')}' with {ctx}."
