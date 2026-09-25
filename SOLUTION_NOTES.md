# KydoPhone SMS/UI solution

This patch keeps SMS embedded in KydoPhone and adds:

- per-conversation pin/unpin
- per-sender block/unblock, including suppression of incoming notifications/storage for blocked senders
- per-conversation Android notification channel settings (sound/vibration/importance are controlled by Android)
- proper bottom insets for the SMS composer with 3-button navigation and IME
- Recents search field visually aligned with Contacts
- lighter navigation transitions and non-animated SMS list jump to reduce perceived sluggishness

Important: this ZIP is based on the uploaded KydoPhone-project.zip snapshot. Your current GitHub repository has already diverged from that snapshot (notably Gradle/AGP and AppRoot fixes), so apply/merge these source changes into the current GitHub tree rather than replacing the repository wholesale.

The `libandroidx.graphics.path.so` strip message remains a warning and is not addressed here.

Conversation notification customization intentionally opens Android's notification-channel settings. Android owns channel sound/vibration/importance once the channel exists; trying to fake those settings inside the app would be unreliable.
