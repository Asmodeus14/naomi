# Changelog

All notable changes to **Naomi** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Added
- **Signed release builds.** An upload key configured from a gitignored
  `keystore.properties` locally and from GitHub secrets in CI. Release builds
  never fall back to the debug key: without credentials they stay unsigned and
  `verifyReleaseSigning` fails with an explanation.
- **APK Signature Scheme v3.** Enables key rotation, which without a store
  holding a recoverable copy of the signing key is the only way a compromised
  key can be replaced on Android 9+ without every user uninstalling.
- **`SHA256SUMS.txt` on every release**, so the privacy claim can be checked
  against the exact binary that was downloaded rather than against the README.

### Changed
- **Distribution is GitHub Releases, not Google Play.** A version tag builds,
  signs and verifies the APK and opens a *draft* release; pushing to `main`
  publishes nothing. `versionCode` is derived from the tag
  (`major*10000 + minor*100 + patch`) rather than a build counter, so re-running
  the workflow on a tag cannot produce a second binary claiming to be that
  release. DEPLOYMENT.md is explicit about what shipping outside a store
  costs — chiefly that there is no Play App Signing, so a lost key means the app
  can never be updated again.
- The R8 mapping file is attached to each release, gzipped. Build artifacts
  expire after 90 days; the APK they describe does not.

### Removed
- The Play Internal Testing and Play production-promotion workflows, and the
  `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` secret they needed.

---

## [0.2.0] - 2026-08-29

The release where a memory stops being a single utterance.

### Added
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
