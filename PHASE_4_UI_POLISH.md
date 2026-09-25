# KydoPhone Phase 4 — Material 3 UI polish

This patch keeps the current project's Gradle/AGP configuration untouched.
Merge/copy `app/src` into the current GitHub project.

## Included
- Fixed SMS thread/compose return navigation: the top-left back action returns directly to the Messages tab instead of depending on the current navigation-stack shape.
- Added a very short Material 3 fade transition when switching the main tabs (120ms in / 90ms out) to make the app feel more polished without reintroducing a heavy animation.
- Added semantic leading icons to contact editor fields:
  - person icon for name fields
  - badge icon for suffix
  - business icon for company
  - phone icon for phone numbers
- Added restrained Material 3 semantic coloring to those icons using primary/secondary/tertiary colors.
- Added animated validation-error appearance in the contact editor.
- Added small semantic icons to contact detail company/suffix rows.
- Colored the SMS thread call/back actions using the current Material 3 color scheme.

## Deliberately not included
- No broad redesign.
- No custom animation framework.
- No per-screen hard-coded colors that would break light/dark or dynamic color.
- No Gradle/AGP changes.

The next UI pass can focus on cards, spacing, typography hierarchy, list-item shapes, selected navigation states, SMS bubbles, and empty states while preserving the existing functionality.
