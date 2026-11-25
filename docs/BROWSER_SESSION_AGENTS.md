# ChatGPT + Gemini subscription handoff

This fork provides a manual handoff between Antigravity and the user's existing
ChatGPT/Gemini subscriptions. It does not call the OpenAI or Gemini model APIs,
and it does not require API keys or API billing.

## Why this is a manual handoff

OpenAI's Terms of Use effective January 1, 2026 prohibit automatically or
programmatically extracting data or Output from its services:
https://openai.com/policies/terms-of-use/

For that reason this fork does not scrape ChatGPT pages, inspect their DOM,
capture their accessibility tree, or automatically read model output. Google
OAuth also should not be embedded in a WebView; Google directs apps to use a
browser/Custom Tab sign-in flow.

## Workflow

1. Antigravity stages a question for ChatGPT or Gemini through the local Android
   gateway.
2. Open **Agent sessions** from the AGENTS pill in Antigravity.
3. Tap **Copy staged question and open provider**.
4. Paste the question into the signed-in Chrome chat and send it yourself.
5. Copy the provider's reply.
6. Return to Agent sessions and tap **Import copied provider reply**.
7. Antigravity can now read the imported reply through the local gateway.

The app never reads Chrome cookies, passwords, profile databases, browser
storage, or page contents. Clipboard access happens only after the user taps
the import button while Agent sessions is foregrounded.

## Python bridge

    from bridge import agents

    request = agents.stage("chatgpt", "Review this change for correctness.")
    print(request)

    # User sends the staged prompt in Chrome, copies the response,
    # then imports it in Agent sessions.
    reply = agents.wait_for_response(request["request_id"], timeout=900)
    print(reply["response"])

For sequential two-provider review:

    from bridge import coordinator

    result = coordinator.review("Review the patch for race conditions.")
    print(result["chatgpt"])
    print(result["gemini"])

The coordinator stages ChatGPT first, waits for an explicitly imported reply,
then stages Gemini with the first review included as context. Each provider
handoff requires the user to copy/send/import.

## Limits

This is not a live API integration. Antigravity cannot silently send messages
or fetch web chat history. The user must send prompts and import replies.
Provider entitlements, subscription limits, and account rules continue to
apply. Imported text is held in app memory until cleared or the process ends.
