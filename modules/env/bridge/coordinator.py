"""Sequential two-model review using the user's existing web subscriptions.

The coordinator intentionally pauses for the user to send/copy/import each
reply. It does not automate browser input or extract model output.
"""
from typing import Any, Dict
import time

try:
    from . import agents
except ImportError:
    import agents


def review(prompt: str, timeout_per_agent: float = 900.0) -> Dict[str, Any]:
    """Ask ChatGPT first, then ask Gemini to independently review its response.

    While this function waits, open Agent sessions, copy/open the provider,
    paste and send the staged prompt in Chrome, copy the reply, and tap
    'Import copied provider reply'. Repeat for Gemini when its request is staged.
    """
    first = agents.stage("chatgpt", prompt)
    if not first.get("ok"):
        return {"ok": False, "stage": "chatgpt", "error": first.get("error", "Could not stage ChatGPT prompt")}
    chatgpt = agents.wait_for_response(first["request_id"], timeout=timeout_per_agent)
    if not chatgpt.get("ok"):
        return {"ok": False, "stage": "chatgpt", "chatgpt": chatgpt}

    second_prompt = (
        "Independently review the same task. Consider the original request and "
        "the first review below. Identify errors, missing edge cases, and any "
        "disagreements. Do not accept the first review uncritically.\n\n"
        "ORIGINAL REQUEST:\n" + prompt + "\n\n"
        "CHATGPT REVIEW:\n" + chatgpt.get("response", "")
    )
    second = agents.stage("gemini", second_prompt)
    if not second.get("ok"):
        return {"ok": False, "stage": "gemini", "chatgpt": chatgpt,
                "error": second.get("error", "Could not stage Gemini prompt")}
    gemini = agents.wait_for_response(second["request_id"], timeout=timeout_per_agent)
    if not gemini.get("ok"):
        return {"ok": False, "stage": "gemini", "chatgpt": chatgpt, "gemini": gemini}
    return {"ok": True, "chatgpt": chatgpt.get("response", ""),
            "gemini": gemini.get("response", "")}
