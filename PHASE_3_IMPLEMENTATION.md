# KydoPhone Phase 3 implementation

Implemented from the requested feature list:

1. SMS search
   - Searches contact name, sender ID/number, and latest message preview.
   - Uses the same rounded Material 3 search treatment as Contacts/Recents.

2. Blocked SMS senders
   - Blocked conversations are hidden by default.
   - A filter menu can reveal blocked conversations when needed.
   - Existing blocked conversations remain stored so they can be revealed/unblocked.
   - Incoming SMS from blocked senders is stored but does not generate a notification.

3. Alphanumeric SMS sender IDs
   - Sender keys now support both phone numbers and text sender IDs such as advertising/service names.
   - A sender such as `AD-NAME` can be blocked even though it has no phone number.

4. Contacts management
   - First name
   - Middle name
   - Last name
   - Suffix
   - Company
   - Multiple phone numbers
   - Contact detail exposes a WhatsApp action when WhatsApp/WhatsApp Business is installed.
   - Company is included in Contacts search.
   - Uses Android Contacts Provider; no separate contact database.

5. Missed-call filter
   - When Missed is enabled, each displayed group contains only missed entries.
   - This prevents a mixed group whose latest call is incoming/outgoing from appearing as though it were a normal call while still matching the missed filter.

6. Sluggishness / UI
   - SMS filtering is derived from the already-loaded conversation list rather than doing provider queries while typing.
   - Existing reduced 180ms navigation transitions are retained.
   - SMS thread direct scrolling is retained to avoid animated repositioning after every message update.

## Important merge note

The project snapshot used for this patch has an older Gradle/AGP version than the user's current GitHub repository. **Do not overwrite the current repository's Gradle configuration with this snapshot.**

Merge/copy the changed `app/src` files into the current repository and keep the current working AGP/Gradle configuration.

GitHub Actions remains the authoritative build check for the current repository.
