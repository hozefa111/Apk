# Optional voice round entry — v2.5.0

The original manual Round form is retained. A new “Voice se round add karo” button
opens a separate staged guide: Players → Hukum → Bid → Partners → Result → Review.
Opening/cancelling the guide does not change the manual draft. No record is written
until the final review receives an explicit “save karo” / “save round” / “सेव करो”
or the user taps Confirm Save. A generic yes is not a save command.

Tap Start voice guide for spoken questions followed by Android's speech recognition
screen. Each accepted answer advances to the next spoken question. Speak answer
listens once. Cancelling speech or an unclear answer pauses automatic continuation;
there is no unbounded listening/retry loop. Pause voice, Back and Cancel are available.
Typed answers use the same parser. Language selects hi-IN or en-IN, with matching
question audio where the installed TTS engine supports it. Manual entry needs no
speech provider.

Names match full roster names, without fuzzy guesses. Unknown or duplicate names
require another answer; the displayed stable player numbers are a fallback. Say
“all players” / “sabhi” or “previous players” / “pichhle wale players” for participants.
Previous participants come from the last saved game and are filtered to active IDs.
Numbers shown in the guide retain their roster order across Activity recreation.

Bids follow the unchanged manual picker and ScoreRules: 155–250 inclusive in steps
of five. Digits, Devanagari digits, common English forms (one seventy / one hundred
and seventy) and Hindi forms (एक सौ सत्तर) are supported. There must be at least one
partner and one opponent, and Hukum cannot be their own partner. The final screen
shows every selected player's existing-rule score preview. Commands include bid
badlo, hukum badlo, partner badlo, result badlo, players badlo, peeche and cancel.
Changing a detail requires finishing the remaining steps and confirming again.

The voice draft is saved in Activity instance state, separately from the persistent
manual draft. Rotation/process recreation never resumes recording or saves a round
automatically. A pending recognition result is matched to the original step/draft;
late, cancelled or duplicate results cannot confirm another round. A submitted
voice round uses the manual form's durable ID/payload and retry path. Admin login,
live sync, unchanged selected active player names and absence of another pending
save are checked before applying the staged draft. Offline confirmation leaves the
manual draft unchanged. Firebase gateway/scoring rules and production data are not
changed by this feature.

The app does not request RECORD_AUDIO or record/store audio itself. It launches
Android's chosen speech provider; that provider may send audio to its service.
Speech languages, accuracy, availability and offline support depend on the device.
Questions remain visible if TTS is missing. Recognition failure keeps the guide open
with typed/manual fallback. Device speech accuracy and real microphone audio require
physical-handset validation; emulator tests inject provider responses rather than
claiming to recognize real speech.
