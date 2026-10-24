## Final bidding chance — v2.7.15 (36)

- When everyone else has passed, the highest bidder can Keep the current bid or Increase once, then proceed straight to Hukum.
- Applies to the default 155 bidder and any competitive highest bidder, in 4- and 6-player games. No extra initial default turn and no restarted auction.
- At 250, bidding finalizes automatically. Human final-choice timeout keeps the current bid; bots use existing own-hand bidding strength.
- Server validation, original command receipts and turn generations reject invalid, repeated and late actions.

### Verification
- 60 engine/final-bid tests, 22 service tests, 24 backend tests passed (4 emulator-gated tests skipped).
- 8 browser touch scenarios passed for 155/180 Keep/Increase in both modes.
- 14 card-playback browser checks passed.
- Signed release build and Android lint passed; all 15 APK asset hashes match source.
- Publication is gated on installing this exact stable-signed APK over public v2.7.14 in an Android 11 emulator, retaining app data and verifying startup.

Same package and stable signing certificate. Existing accounts, rooms, scoring, private cards, history and data retained. Web and free backend updated. Existing clients should update for the new final-choice controls. No production gameplay or physical-phone test is claimed. This change does not establish that Firebase daily quotas cannot be exhausted.
