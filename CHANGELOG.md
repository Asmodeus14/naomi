# Changelog

All notable changes to **Naomi** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Added
- **Naomi learns your words.** A speech recogniser has never heard of your
  project, so it renders "Nyx" as "next" with complete confidence — and the
  mistake is inherited by the title, the topic it files under and the search
  index. Naomi now keeps a local vocabulary of the names, projects and technical
  terms that appear in your own memories, and uses it to repair a mishearing at
  the point of capture.
- **The recogniser's second opinion is no longer discarded.** Android returns
  several competing hypotheses per utterance and Naomi kept only the first. When
  the right word is sitting in hypothesis two, that is now treated as evidence.
- **"It's Nyx, not next."** Naomi has no keyboard, so speech is the only way to
  teach it a word. Saying the correction out loud adds the term and takes effect
  on the same capture.
- **`memory_entries.rawTranscript`.** Only the utterance that *starts* a memory
  reached `notes.rawTranscript`; everything said about it afterwards had nowhere
  to keep the words as spoken. Correcting a continuation was destroying the only
  copy. Null whenever nothing was corrected.
- **Migration tests.** The database has real migrations and deliberately no
  destructive fallback, so a bad one is a crash on launch for someone whose data
  is already there. There are now instrumented tests proving an upgrade from
  every prior version keeps existing memories.

- **Naomi can tell the time.** The parser was date-only: "at 6", "7 PM" and
  "5:30" all resolved to nothing, and every due date landed on a hardcoded 09:00
  or 19:00 — so "remind me tomorrow at 6" produced a reminder nine hours early,
  which is worse than no reminder because it is silently wrong. It now
  understands clock times, explicit calendar dates ("September 4", "on the 4th"),
  durations, and the composition of a date with a time.
- **Reminders are exact when you were.** "Remind me at 6 PM" now gets an exact
  alarm; "by Friday", whose 09:00 was chosen by the parser, still gets an inexact
  one. This is the one new permission, requested at the first reminder that needs
  it rather than at launch. Refusing it falls back to the old behaviour.
- **Events go to your calendar**, by handing them to your own calendar app
  pre-filled rather than by taking read access to every appointment you have ever
  had. No calendar permission, no manifest change.
- **Naomi decides what kind of thing you said**, without ever asking. There is no
  mode to pick.
- Spelled-out counts above twelve now parse. "In thirteen days" and everything
  from fifteen up silently lost its deadline.

### Notes on what this deliberately will not do

- **The hour is not Naomi's to invent.** "Meeting tomorrow" stays a memory rather
  than becoming a 9 AM appointment.
- **A pattern is not an appointment.** "The meeting is usually at 6" has every
  ingredient of a calendar event and is remembered as a fact, which is why the
  habitual check runs before anything is scored.
- **A hedge is respected.** "Rahul is coming tomorrow around 6" sets no alarm.
  Turning someone's deliberate vagueness into a precision they get woken by is
  not a feature.
- **A shared article cannot put things in your calendar.** A page saying "the
  hearing is tomorrow at 10 AM" has exactly the shape of an appointment without
  anyone having agreed to anything.
- **A bare number is not a time.** Dictation is full of "version 2" and
  "sprint 3".
- A correction needs a word to *sound* like a known term **and** be corroborated
  — by nearby related words, or by the recogniser having offered it. Context
  alone can never rewrite anything.
- Collocations like "next week", "last Monday" and "first time" are checked
  before any scoring and can never be overridden, no matter how much Naomi knows
  about a project called Nyx.
- The original words are always kept, so a wrong correction is recoverable.
- Text shared in from another app is neither corrected nor learned from — it is
  someone else's words, and nothing about it was ever acoustically uncertain.
- The vocabulary never leaves the device. It is an unusually precise description
  of what you work on and who you know; sending it somewhere to improve
  recognition would trade away the thing this app exists to protect. No new
  permissions, no new network surface.

---

## [0.2.0] - 2026-08-30

The release where a memory stops being a single utterance — and the first one
anybody can actually install.

### Added
- **Signed, installable releases.** Naomi is distributed as a signed APK on
  GitHub Releases. A version tag builds, signs and verifies it and opens a
  *draft* release; pushing to `main` publishes nothing. Signing credentials come
  from a gitignored `keystore.properties` locally and from GitHub secrets in CI,
  and release builds never fall back to the debug key — without credentials they
  stay unsigned and `verifyReleaseSigning` fails with an explanation.
  Earlier drafts of 0.1.0 and 0.2.0 attached an *unsigned* APK; neither was ever
  published, and both were withdrawn rather than left to be found.
- **`SHA256SUMS.txt` on every release**, so the no-`INTERNET` claim can be
  checked against the exact binary you downloaded rather than against this file.
- **APK Signature Scheme v3.** Naomi ships outside a store, so there is no Play
  App Signing holding a recoverable copy of the signing key. v3's
  proof-of-rotation lineage is the only mechanism that lets a compromised key be
  replaced on Android 9+ without every user uninstalling and losing their
  memories, and it cannot be added retroactively to APKs already published.
- **Living memory.** Saying more about something you have already told Naomi
  continues that memory rather than creating a second one, and the memory keeps
  a dated history of everything said about it. Topics grow a timeline.
- **Ask Naomi.** Natural-language recall — "what did I say about the ring
  buffer" — answered entirely from the local store. Nothing is generated: every
  line returned is a fixed phrase or text you recorded yourself.
- **Share Sheet.** Text and links shared from other apps become memories. The
  link is kept and shown, and deliberately never reaches the understanding layer.
- **Reminders.** Deadlines heard in speech go on the system clock and survive
  reboot. Ticking a task off cancels its alarm. Alarms are inexact by choice —
  see `ReminderScheduler` for why, and for what that costs.
- **Two build flavours.** `offline` is the default and the only one published; it
  declares no `INTERNET` permission. `connected` adds a web-reading layer as a
  separate APK you build yourself. The choice is made at install time rather than
  in Settings, because a runtime toggle can only ever be a promise about which
  code paths run.
- `checkWebModuleBoundary`, which fails the build if the networked modules can
  reach Room or `:app`, or if the networked module leaks into the offline build.

### Fixed
- **Merging silently cost recall.** A memory kept the transcript of whatever was
  said first and the summary of whatever was said last; everything in between
  lived only in its history, which search never looked at. Saying three things
  about one subject made the middle one unfindable — in an app whose only job is
  recall.
- Merge candidates were scoped to the resolved leaf topic, so saying more about a
  subject could mint an empty subtopic, fail to see the memory it was continuing,
  and duplicate it under a different parent.
- `POST_NOTIFICATIONS` was declared but never requested, so on Android 13+ it sat
  denied and every reminder would have been dropped silently. The feature would
  have shipped looking present and doing nothing.
- The notification icon was the full-colour launcher icon. Android draws small
  icons as a silhouette of their alpha channel, so it rendered as a grey blob.
- The vertical guide line used `fillMaxHeight()` inside a `LazyColumn` item,
  where the incoming height constraint is unbounded, so it resolved to zero and
  had never drawn.
- **Opening a topic could crash.** `TopicDetailScreen` lists subtopics, timeline
  days and memories in one `LazyColumn` keyed on raw database ids — but topic 2
  and note 2 are unrelated rows that happen to share an id, and Compose throws
  when a key repeats. Any topic whose subtopic id collided with one of its note
  ids died on open.
- Related-topic pills could not wrap. A `Row` does not wrap, so a third related
  topic was squeezed into a sliver one character wide; the repository returns up
  to six.

### Changed
- **The interface was de-carded.** Eighteen bordered containers became four.
  Space, a shared left edge and type weight do the grouping that boxes were
  doing; a screen of six memories now reads as one list to scan rather than six
  objects competing. What survives is a chip and the theme preview, which is
  literally a sample of the app's surface and needs an edge to be a sample of
  anything.
- **A real launcher icon.** The old one was a placeholder — a dark square with a
  white dot, and a plain `<vector>` rather than an `<adaptive-icon>`, so the
  launcher could not mask it to the device's shape, the "round" variant was
  identical to the square one, and Android 13's themed icons had no monochrome
  layer and fell back to a washed-out square.
- Every screenshot in the README was recaptured from the running app.
- `versionCode` is derived from the release tag (`major*10000 + minor*100 +
  patch`) rather than from a build counter. With no store to reject a duplicate,
  a counter would let a re-run of the same tag produce a second binary claiming
  to be that release. The release also refuses to build if the tag and
  `versionName` disagree.
- The R8 mapping file is attached to each release, gzipped — build artifacts
  expire after 90 days and the APK they describe does not, and at 38 MB raw it
  would otherwise be the largest file on the download page by a factor of
  seventeen.

---

## [0.1.0] - 2026-08-29

First release. The **Changed** and **Fixed** entries below describe work done on
the implementation *before* it was ever published — nothing in them was shipped
to anyone. They are recorded because several were claims the project made about
itself that were not true, and a changelog that quietly starts from the corrected
state is a changelog that hides them.

### Added
- **Gemini Nano understanding**, run by Android's AICore system service where the
  device supports it, with the built-in keyphrase engine as the always-available
  fallback. Neither path uses the network; the app declares no `INTERNET`
  permission.
- **Real due dates.** Deadlines are resolved to an instant at capture time, so
  tasks can be sorted and bucketed. Previously the spoken phrase was stored as
  text and "tomorrow" stayed "tomorrow" indefinitely.
- **Search context.** Results carry the topic path and a snippet around the
  match rather than a bare title.
- Database migration 1 → 2, with schemas exported so future migrations can be
  diffed and tested.

### Changed
- **The understanding layer was rewritten.** It was a keyword cascade hardcoded
  to two demo scripts: every note in one domain was titled "Synchronization",
  every note in another "Second Normal Form", and anything outside those two
  subjects produced a topic named after its first long word. Titles and topics
  are now derived from the passage by RAKE keyphrase extraction, and a memory
  attaches to a topic the user already has rather than minting a near-duplicate.
- Release builds are minified and resource-shrunk. The APK went from ~19 MB to
  ~2 MB.
- The release workflow publishes an unsigned **release** build as a draft. It
  previously published a **debuggable** APK as a public release, which allows
  anyone with `adb` to read the on-device memory database.
- Topic matching threshold raised from 0.65, which rated "2NF" against "3NF" at
  0.67 and silently merged them.

### Fixed
- **The app shipped with full network access despite claiming none.** ML Kit's
  GenAI client pulls in `transport-backend-cct`, Google's telemetry upload
  backend, which declares `INTERNET` and `ACCESS_NETWORK_STATE`; manifest merging
  added both to the APK. They are now stripped with `tools:node="remove"`, and
  `checkReleaseHasNoNetworkPermission` fails the build if either returns. Verified
  against the built APK with `aapt2 dump permissions`, not just against the source
  manifest. (Renamed to `checkOfflineRelease…` after 0.1.0, when flavours arrived.)
- The medium widget layout contained `<Row>`, a Compose tag with no Android
  view behind it, and three layouts used `<View>`, which RemoteViews does not
  permit. All four crashed at inflate time in the launcher.
- Destructive migration was enabled with no migrations defined, so any schema
  change would have silently erased every memory.
- Related topics were inserted with `parentId = null`, filling the root topic
  list with orphaned duplicates of nodes that had just been created.
- Notes were linked only to the root and leaf topics, so every intermediate
  topic rendered as empty.
- The capture sheet showed three "AI processing" stages driven by 900 ms of
  `delay()`. Stages now reflect the pipeline and are not shown when they cannot
  be reported honestly.
- `VIBRATE` was never declared, so every haptic failed silently inside a
  `catch`.
- `allowBackup` was true with no extraction rules, which uploaded the private
  transcript database to Google Drive.
- Task titles kept their trigger phrase and trailing preposition ("Should revise
  2NF before"), and only the first two tasks in a passage were extracted.
- Storage settings reported a "Notes & Hierarchy" size computed as
  `noteCount × 512` — a fabricated number presented as a measurement.
- Privacy settings claimed "Audio: Not stored permanently" while the retention
  selector below it offered "Keep forever".

### Known limitations
- Topic names come from the user's own words, so a first memory about tomato
  seedlings creates a topic called "Tomato Seedlings" rather than "Gardening".
  Naomi does not invent categories the speaker did not say.
- Inter is specified by the design but not bundled; the app uses the platform
  sans-serif. See the note in `theme/Type.kt`.
- Release builds are unsigned. There is no keystore in this repository.

---

## Pre-release implementation - 2026-08-25

The initial generated implementation. Never published; recorded here so the
**Changed** and **Fixed** entries above have something to refer to.

- On-device speech-to-text with a live waveform visualiser.
- Extraction of topics, tasks, ideas and decisions from a transcript.
- Typography-first interface with the animated Naomi Orb.
- System / Light / Dark theme, persisted.
- Knowledge tree with note counts and subtopic navigation.
- Search across memories, topics and tasks.
- Task list with completion toggles.
- Ambient mode: continuous capture via a foreground service.
- Home-screen widget in three sizes, built on RemoteViews.
- Configurable audio retention and Markdown export.
- GitHub Actions build and release workflows.
