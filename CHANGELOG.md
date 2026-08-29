# Changelog

All notable changes to **Naomi** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

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
  manifest.
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
