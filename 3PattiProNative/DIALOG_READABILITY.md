# Dialog readability — 2.3.1

The reported Hukum-history screen used one newline-separated AlertDialog message
for every round. It inherited Android's flat grey alert surface, had no row
spacing, score signs/colours or hierarchy, and rendered every entry as body text.

## Changes

- Hukum, Partner and Non-partner history use recycled, wrapping ListView cards.
  Date/time, Hukum name, bid/result and signed points have separate text styles.
  Positive, negative and zero points are green, red and muted respectively. A
  scrolling player/total header and fixed dialog title/Close action retain context.
  Every selected round stays reachable; tapping opens its existing details.
- Round Details and Round Options use structured facts and per-player score cards,
  including full partner/opponent lists and explicit player roles. Bodies scroll
  within a measured viewport; action buttons remain in the native footer.
- Shared dark rounded surfaces, gold titles, readable message line spacing,
  inset form fields, and adaptive footer buttons apply to app-owned dialogs,
  choice menus, confirmations and authentication. Existing action/onShow/onDismiss
  callbacks are preserved. Native file pickers and share targets keep their OS UI.
- Session summary uses the same bounded scrolling body. Share preview allocates
  remaining height to the image, adapts its paging controls to narrow large-text
  layouts, and keeps footer controls separate. Date pickers use the dialog theme.
- Role selection uses existing HH role rules and Firestore ordering; nonparticipants
  and settlement records are excluded, including zero-point opponents correctly.
  No scoring, gameplay or production Firebase records/configuration are changed.

## Verification

Six new JVM cases cover all roles, membership, zero-point rounds, settlement
exclusion, nanosecond/tied ordering and input immutability. New Android coverage
recreates the reported 47-round case with long names, scrolls to its final item,
opens structured details, checks positive/negative/zero colours, and verifies
Close/share/paging controls with 2x fonts in landscape. All existing animation,
authentication, data-safety, backup and image-export tests remain in the matrix.
Completed final-source results and screenshots accompany the delivered APK.
