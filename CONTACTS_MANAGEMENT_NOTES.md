# KydoPhone Contacts Management

This patch adds in-app contact management while continuing to use Android's Contacts Provider.

## Added

- Create contacts inside KydoPhone.
- Edit contacts inside KydoPhone.
- Delete contacts with confirmation.
- Add/remove multiple phone numbers.
- Preserve existing phone-data row IDs when editing, so unrelated aggregated contacts are not modified.
- Request `WRITE_CONTACTS` only when contact modification is needed.
- Keep the existing Android Contacts Provider as the source of truth; no private contacts database is introduced.
- Contact changes automatically refresh the existing contact index through the ViewModel/content observer.

## Navigation

- Contacts tab -> `New contact` -> in-app editor.
- Contact details -> Edit -> in-app editor.
- Contact details -> Delete -> confirmation -> delete from Android contacts.

## Files changed

- `app/src/main/java/org/aust/dialer/data/Models.kt`
- `app/src/main/java/org/aust/dialer/data/ContactsRepository.kt`
- `app/src/main/java/org/aust/dialer/DialerViewModel.kt`
- `app/src/main/java/org/aust/dialer/ui/ContactsTab.kt`
- `app/src/main/java/org/aust/dialer/ui/HomeScreen.kt`
- `app/src/main/java/org/aust/dialer/ui/AppRoot.kt`
- `app/src/main/java/org/aust/dialer/ui/ContactDetailScreen.kt`
- `app/src/main/java/org/aust/dialer/ui/ContactEditorScreen.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-ar/strings.xml`

## Build note

The source changes were checked structurally and the XML resources parse correctly. A Gradle build could not be executed in the preparation environment because Gradle was not installed and external network access is unavailable. The GitHub Actions build should therefore be used as the final compilation check.
