# Naomi Roadmap

This document outlines current development priorities, planned features, and long-term research directions for Naomi.

---

## ✦ Now (Current release, v0.2.0)
- [x] **Living memory:** saying more about a subject continues that memory with a dated history instead of creating a second note. Topics grow a timeline.
- [x] **Ask Naomi:** natural-language recall over local memories. Nothing is generated — every line returned is a fixed phrase or text you recorded.
- [x] **Share Sheet:** text and links shared from other apps become memories, with the link kept and shown.
- [x] **Reminders:** deadlines heard in speech go on the system clock and survive a reboot. Inexact by choice; see `ReminderScheduler`.
- [x] **A checked privacy boundary:** two build flavours, where the published one has no `INTERNET` permission, plus a module split that gives the networked code no type for a memory. Both enforced by gates in CI.
- [x] **Local speech capture:** one-tap voice capture with live waveform and on-device transcription.
- [x] **Understanding pipeline:** titles, topic placement, subtopic nesting, task extraction with resolved dates, ideas and decisions.
- [x] **Ambient mode**, **knowledge tree**, **search**, **tasks**, **home-screen widget**, configurable audio retention and Markdown export.

---

## ✦ Next
- [ ] **Instrumented tests.** The gap that matters most: every serious bug in 0.2.0 was found by driving the app, not by the suite. Room migration tests and Compose UI tests come before new features.
- [ ] **Offline semantic search.** Local embeddings so recall works when your wording differs from your earlier wording, which is the main way Ask Naomi currently misses.
- [ ] **Better merge decisions.** Whether a thought continues a memory currently depends on what it gets titled, so "blossom end rot" starts its own memory under the same topic. Comparing content rather than titles alone would fix it.
- [ ] **Interactive widget actions:** toggle a pending task from the home screen.
- [ ] **Editing.** A memory's title and text cannot be corrected by hand.

---

## ✦ Later
- [ ] **Audio playback timeline:** optional playback of the retained snippet beside its memory.
- [ ] **Wear OS companion:** a quick-record tile.
- [ ] **Multi-language topic classification.** The lexicon is English-only today.

---

## ✦ Ideas & research
- Proactive resurfacing: bringing a memory back at a moment it is likely to matter, without becoming a notification app.
- Richer temporal parsing — spoken clock times ("the meeting at 3pm"), which is the point at which exact alarms would start being worth their permission.

**Deliberately not planned.** A graph visualisation of connected topics: it looks
impressive and is not how anyone retrieves a thought. Cloud sync of memories, in
any form — the privacy boundary is the product, and a sync feature is the shortest
path to dismantling it.
