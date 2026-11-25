"""Manual ChatGPT/Gemini handoff using existing signed-in browser sessions.

No model APIs are called. A human sends the staged prompt in Chrome, copies the
reply, and explicitly imports it in Antigravity's Agent sessions screen.
"""
from typing import Any, Dict, Optional
import time

try:
    from . import client
except ImportError:
    import client


def status() -> Dict[str, Any]:
    return client.send_command("agent_status")


def stage(provider: str, prompt: str) -> Dict[str, Any]:
    """Stage a prompt for ChatGPT or Gemini; then use Agent sessions to copy it."""
    return client.send_command("agent_stage", {"provider": provider, "prompt": prompt})


def read(request_id: Optional[str] = None) -> Dict[str, Any]:
    payload = {"request_id": request_id} if request_id else {}
    return client.send_command("agent_read", payload)


def wait_for_response(request_id: str, timeout: float = 900.0,
                       poll_interval: float = 2.0) -> Dict[str, Any]:
    """Wait until the user imports the copied provider reply in Agent sessions."""
    deadline = time.monotonic() + max(1.0, timeout)
    while time.monotonic() < deadline:
        result = read(request_id)
        if result.get("ok") and result.get("status") == "response_imported":
            return result
        time.sleep(max(0.25, poll_interval))
    return {"ok": False, "error": "Timed out waiting for the user to import the provider reply.",
            "request_id": request_id}


def clear() -> Dict[str, Any]:
    return client.send_command("agent_clear")
