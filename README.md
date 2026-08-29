<div align="center">

# Naomi

**You talk. Naomi remembers.**

A local-first memory assistant for Android. Speak a thought; it becomes a titled,
filed, connected memory — without leaving your phone.

<img src="docs/screenshots/home.png" width="280" alt="Naomi's home screen: a single orb, a greeting, and recent memories" />

</div>

---

## What it does

Most note apps give you a list. Naomi gives you a structure, built out of your own
words as you go.

Say this:

> "The ring buffer in Nyx is stable now. Synchronisation between the producer and
> consumer still drops frames. I need to profile the fence waits tomorrow."

Naomi produces:

| | |
|---|---|
| **Title** | Ring Buffer |
| **Filed under** | `Ring Buffer` |
| **Task** | Profile the fence waits — due tomorrow, as a real date |

Then say this, a day later:

> "The ring buffer uses too much memory under load. I think a slab allocator would
> fix it."

It does **not** create a second "Ring Buffer" topic. It recognises the subject you
already have, files the memory there, nests `Slab Allocator` beneath it, and pulls
out the idea you flagged with "I think".

<div align="center">
<img src="docs/screenshots/topics.png" width="260" alt="Knowledge tree" />
<img src="docs/screenshots/memory.png" width="260" alt="A single memory with its topic path and extracted task" />
<img src="docs/screenshots/tasks.png" width="260" alt="Tasks bucketed by resolved due date" />
</div>

That grouping is the whole product. A note app that files every sentence under a
new heading has not organised anything.

---

## Privacy

Naomi's privacy claim is not a policy — it is a property of the build, and you can
check it yourself in under a minute:

```bash
./gradlew assembleOfflineRelease
aapt2 dump permissions app/build/outputs/apk/offline/release/app-offline-release-unsigned.apk
```

That prints the complete permission list of the shipped APK:

| Permission | Why |
|---|---|
| `RECORD_AUDIO` | Speech capture |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MICROPHONE` | Ambient mode keeps recording when the app is backgrounded |
| `POST_NOTIFICATIONS` | Reminders you asked for out loud, and the notification that foreground services legally require |
| `VIBRATE` | Capture start/stop haptics |
| `RECEIVE_BOOT_COMPLETED` | Alarms are dropped on reboot; without this a reminder set for Friday is lost by a restart on Wednesday |
| `com.google.android.apps.aicore.service.BIND_SERVICE` | Binder IPC to the system AICore service for Gemini Nano. Not a network permission — it binds to a local system service. |

**There is no `INTERNET` permission.** Without it the app cannot open a socket at
all. Telemetry is not merely absent; it is not expressible. There is no analytics
SDK, no crash reporter, no account, and no server.

### The two builds

This is the `offline` flavour, which is the default and the only one published.

There is a second flavour, `connected`, which adds a web-reading layer for
looking up public pages. It is a **separate APK** that you have to build
yourself — not a setting inside this one. That is deliberate: a runtime switch
can only ever be a promise about which code paths run, whereas a build that does
not contain the networking module cannot reach the network whatever its code
does. Making the choice at install time is what lets the paragraph above stay
absolute rather than becoming "unless a setting is on".

If you build `connected`, the honest description changes: that APK declares
`INTERNET`, and you are trusting the module boundary described below rather than
the operating system. Its permissions are worth checking too:

```bash
./gradlew assembleConnectedDebug
aapt2 dump permissions app/build/outputs/apk/connected/debug/app-connected-debug.apk
```

### Why that took work, and why it is checked automatically

Leaving `INTERNET` out of `AndroidManifest.xml` is not sufficient, and believing
otherwise is how apps ship a privacy claim that is false.

ML Kit's GenAI client pulls in `com.google.android.datatransport:transport-backend-cct`
— Google's telemetry upload backend — and **that library declares `INTERNET` and
`ACCESS_NETWORK_STATE` in its own manifest.** Manifest merging adds a dependency's
permissions to yours. Naomi's source manifest listed no network permission, and
the built APK had full network access anyway.

The fix is `tools:node="remove"` on both permissions, which strips them at merge
time. Nano still works: it reaches AICore over Binder IPC, and the model is
downloaded by the system service, never by this process.

Because a single dependency bump could quietly undo that, the assertion is a build
gate. `./gradlew check` runs `checkOfflineReleaseHasNoNetworkPermission`, which
parses the merged manifest and fails the build if either permission reappears. CI
runs it on every push. The check is verified to fail when it should — not just to
pass today.

### The other half: what the networked code is allowed to know

A permission check proves the offline APK cannot open a socket. It says nothing
about the `connected` build, where a socket is the point. So the boundary there
is structural instead.

The web layer is two modules. `:web-api` is plain Kotlin with no Android plugin,
no manifest and no dependencies — its entire vocabulary is a URL in and a page
out. `:web-impl` holds the only code in this repository that can open a socket,
and the only manifest that asks for `INTERNET`. Neither depends on `:app`, so
neither has a *type* for a memory, a transcript or a topic. Code that tried to
send one would not compile.

`checkWebModuleBoundary` enforces both facts on every build: that `:web-api` and
`:web-impl` cannot reach Room or `:app`, and that `:web-impl` is absent from the
offline build entirely. Like the permission gate, it is verified to fail when
violated.

This is why fetching a page is a per-link action on something you shared in, and
not a search over your memories. Naomi has no mechanism to combine the two.

**Specifically:**

- **Audio** — captured by Android's on-device `SpeechRecognizer` with
  `EXTRA_PREFER_OFFLINE`. Retention is yours to choose in Settings, and defaults
  to *never keep*. Note that `SpeechRecognizer` is implemented by whichever
  recognition service your device ships; on most phones that is Google's, and its
  own behaviour is outside Naomi's control. This is the one part of the pipeline
  Naomi cannot make a guarantee about, which is why it is called out here.
- **Transcripts** — stored in a local Room/SQLite database in the app's private
  directory. They are never uploaded.
- **Understanding** — either Gemini Nano, executed by Android's **AICore** system
  service, or Naomi's built-in keyphrase engine. Nano is chosen precisely because
  the model is managed by the platform: Naomi never downloads it, so no network
  capability is needed. Settings shows which one is active on your device.
- **Backups** — `allowBackup="false"`, with explicit
  [backup](app/src/main/res/xml/backup_rules.xml) and
  [extraction](app/src/main/res/xml/data_extraction_rules.xml) rules. The memory
  database is not copied to Google Drive or to a new device.
- **Export** — Markdown, via the system share sheet, only when you ask.

---

## How the understanding works

No cloud model, no bundled weights, no subject-matter word lists. Two providers
are tried in order, and both run on the device:

1. **Gemini Nano**, if AICore reports the model available. Its response is treated
   as untrusted — the JSON is salvaged, validated, and rejected outright if the
   title is blank or overlong or the topic path is empty. Dates it reports are
   re-resolved by Naomi's own parser so a due date can never be a hallucinated
   timestamp.
2. **The built-in engine**, always available, needing no model at all.

The built-in engine is a [RAKE](https://en.wikipedia.org/wiki/Automatic_summarization)
pass — candidate phrases are runs of words containing no stop word, verb or date,
scored by word degree over frequency. `Lexicon.kt` holds only structural English
(articles, modals, predicates, temporal words); nothing in it knows what a ring
buffer or a tomato seedling is, which is the point.

Placement then consults **your existing topics first**
([`TopicResolver.kt`](app/src/main/java/com/naomi/app/ai/intelligence/TopicResolver.kt)).
A memory attaches to a topic you already have when a candidate phrase matches one;
otherwise it becomes a new root. When nothing is confident enough it goes to
`Inbox` rather than inventing a category you never said.

Matching is deliberately strict. Merging two distinct topics silently destroys
your organisation and is nearly impossible to notice; failing to merge leaves a
duplicate you can fix in one tap. The threshold reflects that asymmetry.

---

## Screenshots

| Knowledge tree | Search | Settings |
|---|---|---|
| <img src="docs/screenshots/topics.png" width="220" /> | <img src="docs/screenshots/search.png" width="220" /> | <img src="docs/screenshots/settings.png" width="220" /> |

| Dark | Ambient mode | Widget |
|---|---|---|
| <img src="docs/screenshots/dark-mode.png" width="220" /> | <img src="docs/screenshots/ambient.png" width="220" /> | <img src="docs/screenshots/widget.png" width="220" /> |

All screenshots are from the running app on a `medium_phone` emulator, not from
mockups.

---

## Architecture

```
app/src/main/java/com/naomi/app/
├── ai/intelligence/      Lexicon, KeyphraseExtractor, TopicResolver,
│                         TopicMatcher, TaskExtractor, TemporalParser
├── audio/                SpeechRecognizer wrapper + ambient foreground service
├── data/
│   ├── database/         Room entities, DAOs, migrations, exported schemas
│   └── repository/       Repository implementations
├── domain/
│   ├── intelligence/     IntelligenceProvider + Nano and heuristic providers
│   ├── model/            Domain types
│   ├── repository/       Repository interfaces
│   └── usecases/         ProcessThoughtUseCase, SearchMemoriesUseCase, …
├── presentation/         Compose UI, one package per screen
│   ├── components/       NaomiOrb, NaomiCard, MemoryRow, empty states
│   └── theme/            Colour, type, spacing, shape, motion
└── widget/               RemoteViews home-screen widget (three sizes)
```

Kotlin, Jetpack Compose, Material 3, Room, Coroutines/Flow, Navigation Compose.
Around 9,700 lines across 82 files. No dependency injection framework —
construction happens once in `NaomiApp`, which is enough at this size.

**Capture is a `Flow<ProcessingStage>`.** Stages are emitted where the work
actually happens. There are no artificial delays: an earlier version showed three
"AI processing" steps driven by 900 ms of `delay()`, and that is exactly what this
design exists to prevent. If a stage cannot be reported honestly, it is not shown.

---

## Build and run

Requires JDK 17 and the Android SDK (compileSdk 36).

```bash
git clone <this-repo>
cd naomi
./gradlew installOfflineDebug      # build and install on a connected device
./gradlew testOfflineDebugUnitTest # unit tests
./gradlew lintOfflineDebug         # lint — a build gate, not advisory
./gradlew assembleOfflineRelease   # minified release APK (~2 MB)
./gradlew check                    # the above plus both privacy gates
```

Task names carry the flavour. `offline` is the default and the one that is
published; substitute `connected` to build the variant with the web-reading
layer, which declares `INTERNET`. See [The two builds](#the-two-builds).

Minimum Android 8.0 (API 26). Gemini Nano needs a device with AICore; everywhere
else the built-in engine runs instead, and Settings tells you which you have.

Lint is a hard gate on purpose. It is the check that caught three widget layouts
using view classes RemoteViews does not permit — they compiled cleanly and crashed
in the launcher process at inflate time.

---

## Status

**Naomi has not had a stable release, and the version in this repository is
`0.1.0` with no tag.** Release builds are unsigned; there is no keystore here and
there should not be one.

What works and is verified on a device: capture by voice or text, title and topic
extraction, topic reuse across sessions, subtopic nesting, task extraction with
resolved dates, search with context, the knowledge tree, Markdown export,
persisted System/Light/Dark theming, and the home-screen widget in three sizes.

Known limitations, stated plainly:

- Topic names come from your own words. A first memory about tomato seedlings
  creates a topic called "Tomato Seedlings", not "Gardening" — Naomi will not
  invent a category you did not say. Depth appears as you keep talking.
- Two memories genuinely about the same thing can share a title. The summary in
  the list is what distinguishes them.
- Inter is specified by the design but not bundled; the app uses the platform
  sans-serif. The scale, weights and tracking are what carry the design, and
  those survive the substitution. See `theme/Type.kt`.
- Instrumented UI tests are not written yet; coverage is unit tests over the
  intelligence layer plus manual device verification.

See [CHANGELOG.md](CHANGELOG.md) for what changed and [ROADMAP.md](ROADMAP.md) for
what is next.

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Security reports go through
[SECURITY.md](SECURITY.md) — please do not open a public issue for them.

If you change the intelligence layer, add a test to
`app/src/test/java/com/naomi/app/ai/IntelligenceEngineTest.kt`. Every test in that
file exists because a real defect got through: the 2NF/3NF collision, the
"Balcony Soil Needs Better" title, the duplicate "Tomato Seedlings" topic, the
"Fence Waits Tomorrow" topic. Assert on behaviour with exact equality, and include
the negative case.

## Licence

[Apache 2.0](LICENSE).
