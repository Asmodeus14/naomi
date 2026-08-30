# Releasing Naomi

Naomi is distributed as a signed APK on **GitHub Releases**. There is no app
store in the path, which makes this document the whole publishing pipeline: what
CI attaches to a release is what people install.

**Contents**

- [The shape of it](#the-shape-of-it)
- [What distributing outside a store actually means](#what-distributing-outside-a-store-actually-means)
- [Files that must never be committed](#files-that-must-never-be-committed)
- [1. The signing key](#1-the-signing-key)
- [2. Local signing](#2-local-signing)
- [3. GitHub secrets](#3-github-secrets)
- [4. Cutting a release](#4-cutting-a-release)
- [5. Versioning](#5-versioning)
- [6. When something goes wrong](#6-when-something-goes-wrong)
- [Commands](#commands)

---

## The shape of it

```
push to main
    └─ android.yml
         lint · unit tests · both privacy gates
         builds both flavours, debug and release        ← releases nothing

push tag v*
    └─ release.yml
         the same gates, again
         → signed APK (offline flavour)
         → SHA256SUMS.txt + R8 mapping
         → DRAFT GitHub Release                         ← a human presses Publish
```

Two things about this are deliberate.

**Pushing to main releases nothing.** It builds and tests. Cutting a release
takes a tag, which is a separate and visible act.

**The release is created as a draft.** Nothing is downloadable until someone
opens it, reads it, and publishes. There is no automatic path from a commit to a
file on a stranger's phone.

Only the **`offline`** flavour is ever published. The `connected` flavour declares
`INTERNET` and exists to be built from source by someone who deliberately wants
the web-reading layer. Publishing both would make the download page the place
where somebody installs the networked one by accident.

---

## What distributing outside a store actually means

Worth being straight about, because it is not all upside.

**No $25 developer account, no review, no data-safety form, no store policy.**
Releases land when you decide they land.

**You are the only thing standing behind the signature.** Play offers *Play App
Signing*, where Google holds the app signing key and yours is only an upload key
— which makes a lost key recoverable. Outside a store there is no such
safety net. The key in `~/.android-keystores/naomi-upload.jks` **is** the app
signing key.

> **If that file and its password are lost, the app can never be updated again.**
> Not by you, not by anyone. Every existing user would have to uninstall — losing
> their memories — and install a differently-signed build. There is no reset
> procedure and no appeal, because there is no third party. Back it up
> ([§1](#1-the-signing-key)) before you ship anything.

**Users have to allow installs from an unknown source,** and Play Protect may
warn on first launch. This is normal for sideloading, but it is friction, and it
is the one part of the experience a store would have smoothed over.

**Nothing updates itself.** A sideloaded APK has no update channel. Users must
watch the repository, or point something like [Obtainium](https://github.com/ImranR98/Obtainium)
at it. Say so in the release notes rather than letting people sit on an old build
assuming it is current.

**No crash reporting.** Which is consistent with an app that has no `INTERNET`
permission — but it means a crash is only ever seen if a user reports it. That is
why the R8 `mapping.txt` is attached to every release: it is the only way to turn
a pasted stack trace back into readable line numbers, and it must outlive the
90-day expiry of a build artifact.

If Naomi ever does go to Play, the keystore below becomes the *upload* key and
this section gets much shorter.

---

## Files that must never be committed

All of these are in `.gitignore`.

| Pattern | What it is |
|---|---|
| `*.jks`, `*.keystore`, `*.p12` | The signing key. Losing control of it means someone else can ship updates that install over yours. |
| `keystore.properties` | Local signing passwords. |
| `*.pem`, `*.key` | Private keys of any kind. |
| `.env`, `.env.*` | Anything else with credentials in it. |
| `local.properties` | Machine-local SDK paths. |

`keystore.properties.example` **is** committed. It contains placeholders only.

---

## 1. The signing key

**A keystore already exists for this project** at:

```
~/.android-keystores/naomi-upload.jks     (alias: upload, RSA 4096, valid ~30 years)
```

It lives **outside the repository on purpose**. A file that is not in the working
tree cannot be committed by accident, which is a stronger guarantee than
`.gitignore` — that only protects you until someone runs `git add -f`.

To create one from scratch instead:

```bash
mkdir -p ~/.android-keystores
keytool -genkeypair -v \
  -keystore ~/.android-keystores/naomi-upload.jks \
  -alias upload \
  -keyalg RSA -keysize 4096 -validity 10950 \
  -storetype PKCS12
```

10950 days is ~30 years. An app cannot be updated past its signing certificate's
expiry, so this is not a place to be modest.

### Protecting it

**Back it up before you do anything else.** Put the `.jks` and its password in a
password manager or an encrypted archive somewhere that is not this machine and
not this repository. Read the warning in
[What distributing outside a store actually means](#what-distributing-outside-a-store-actually-means):
without a store holding a copy of the signing key, a dead disk is the end of the
app's update path.

- Never email it, never put it in a chat, never commit it.
- Never reuse it for another app.
- The password should be long and random. Nobody types it — Gradle reads it.

---

## 2. Local signing

`app/build.gradle.kts` reads credentials from `keystore.properties` if present,
otherwise from environment variables. There is no third path and no fallback to
the debug key: without credentials, release builds stay unsigned and
`verifyReleaseSigning` fails with an explanation.

```bash
cp keystore.properties.example keystore.properties
# then edit it — the file is gitignored
```

```properties
storeFile=/absolute/path/to/naomi-upload.jks
storePassword=…
keyAlias=upload
keyPassword=…
```

On Windows use forward slashes or escaped backslashes (`C:\\Users\\you\\…`).

Confirm it works:

```bash
./gradlew :app:verifyReleaseSigning
./gradlew :app:assembleOfflineRelease
```

---

## 3. GitHub secrets

**MANUAL STEP.** I cannot create these — they require your GitHub account.

Go to **Settings → Secrets and variables → Actions → New repository secret** and
add four:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | The keystore, base64-encoded (below) |
| `KEYSTORE_PASSWORD` | `storePassword` from `keystore.properties` |
| `KEY_ALIAS` | `upload` |
| `KEY_PASSWORD` | `keyPassword` from `keystore.properties` |

For a PKCS12 keystore created as in [§1](#1-the-signing-key), the store and key
passwords are the same value.

To encode the keystore:

```bash
# macOS / Linux
base64 -w0 ~/.android-keystores/naomi-upload.jks | pbcopy   # or > keystore.b64

# Windows PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$HOME\.android-keystores\naomi-upload.jks")) | Set-Clipboard
```

Paste the whole string as `KEYSTORE_BASE64`. If you wrote it to a file, **delete
that file afterwards** — it is the keystore in another form.

Secrets are masked in logs. The workflow additionally never echoes them: the
keystore is decoded to a file outside the workspace and removed in an `always()`
step, so it goes even if the build fails.

Until these four exist, `release.yml` fails at "Restore upload keystore" with a
named error. Nothing is published and nothing is half-published.

---

## 4. Cutting a release

```bash
# 1. Bump versionName in app/build.gradle.kts
# 2. Close the [Unreleased] section of CHANGELOG.md — same commit
# 3. Push that commit to main and let CI go green
git tag -a v0.3.0 -m "Release 0.3.0"
git push origin v0.3.0
```

The tag starts `release.yml`, which re-runs lint, the unit tests and both privacy
gates, builds and signs the APK, verifies the signature with `apksigner`,
generates `SHA256SUMS.txt`, and opens a **draft** release.

Then, by hand:

4. **Actions → Release** — check it went green.
5. **Releases → the draft** — read the generated notes, write the human half, and
   check the APK is attached and the size looks right (~2 MB).
6. **Publish release.**

The tag and `versionName` must agree. If they do not, the workflow stops before
building and tells you which to change — a mismatch would ship an APK that
reports a different version from the release it is attached to.

To release without pushing a tag — a re-run after a CI hiccup, say — use
**Actions → Release → Run workflow** and select the existing tag in the
"Use workflow from" dropdown. A branch is rejected; the version has to come from
a tag.

---

## 5. Versioning

Two numbers, two different jobs.

**`versionName`** — `"0.3.0"` in `app/build.gradle.kts`. Edited by hand. It should
change because a release means something, not because a build happened. Bump it in
the same commit that closes the `[Unreleased]` section of `CHANGELOG.md`.

**`versionCode`** — derived from the tag: `major * 10000 + minor * 100 + patch`.

| Tag | versionCode |
|---|---|
| `v0.2.0` | 200 |
| `v0.2.1` | 201 |
| `v0.3.0` | 300 |
| `v1.0.0` | 10000 |

Deterministic on purpose. A build counter would give the same tag a different
number on a re-run, and with no store to reject the duplicate, two different
binaries could end up claiming to be the same release. The scheme assumes minor
and patch stay under 100; the workflow checks and fails if they do not.

Local builds use `VERSION_CODE_FALLBACK` (3), deliberately below any released
code, so a laptop build cannot install over a real release and pass for newer.
To override it for a one-off:

```bash
VERSION_CODE=1234 ./gradlew :app:assembleOfflineRelease
```

---

## 6. When something goes wrong

**"Tag says X but app/build.gradle.kts says Y."**
Bump `versionName`, commit, delete the tag (`git tag -d v0.3.0 && git push origin
:v0.3.0`), and re-tag the new commit.

**Signing fails in CI.**
`verifyReleaseSigning` runs before the build and names the missing variable. The
usual cause is `KEYSTORE_BASE64` pasted with line breaks — re-encode with
`base64 -w0`.

**"App not installed" on a user's phone.**
Almost always one of three things: a different signing key from the version they
have (they must uninstall first, which loses their memories), a versionCode that
is not higher than the installed one, or Android below 8.0.

**Play Protect warns on install.**
Expected for a signature it has not seen before. It becomes less frequent as more
people install the same signed build. There is nothing to fix.

**A bad build got published.**
Delete the release — or mark it a pre-release — so nobody else downloads it, then
ship a fix under a higher version. You cannot remove it from devices that already
installed it. This is why the release is a draft first: the review step is the
last cheap moment.

### If the keystore is lost

There is no recovery. See
[What distributing outside a store actually means](#what-distributing-outside-a-store-actually-means).
Existing users cannot be updated; the app has to be republished under a new key
and, in practice, a new package name, and everyone reinstalls from scratch.
Back it up now rather than reading this twice.

### If the keystore is exposed

Treat it as compromised even if you are not sure. Anyone holding it can build an
APK that installs over yours as a legitimate update.

Releases are signed with **APK Signature Scheme v3**, which supports key
rotation: you can generate a new key and sign with a *proof-of-rotation lineage*
linking it to the old one, and Android 9+ accepts the result as a legitimate
update. `apksigner rotate` produces the lineage; `apksigner sign --lineage` uses
it. Devices on Android 8.x do not understand v3 and will refuse the update, so
those users still have to uninstall and reinstall.

Rotation only works if the old key is still available to sign the lineage. It is
a remedy for *exposure*, not for *loss* — which is the asymmetry worth
remembering: a leaked key is survivable, a lost one is not.

If it was ever committed, remember that rewriting history does not un-publish
anything already fetched.

---

## Commands

```bash
# Local development
./gradlew installOfflineDebug           # build + install debug on a device
./gradlew testOfflineDebugUnitTest      # unit tests
./gradlew lintOfflineDebug              # lint (a hard gate, not advisory)
./gradlew check                         # everything above + both privacy gates

# Release, locally
./gradlew :app:verifyReleaseSigning     # confirm signing is configured
./gradlew :app:assembleOfflineRelease   # the signed APK that gets published

# Verify what you built
aapt2 dump badging     app/build/outputs/apk/offline/release/app-offline-release.apk | grep version
aapt2 dump permissions app/build/outputs/apk/offline/release/app-offline-release.apk
apksigner verify -v    app/build/outputs/apk/offline/release/app-offline-release.apk
sha256sum              app/build/outputs/apk/offline/release/app-offline-release.apk

# The connected flavour — not published, build it yourself if you want it
./gradlew :app:assembleConnectedRelease
```

**Artifact paths.** Because the project has product flavours, these are *not* the
conventional `…/release/app-release.apk`:

| Artifact | Path |
|---|---|
| APK | `app/build/outputs/apk/offline/release/app-offline-release.apk` |
| R8 mapping | `app/build/outputs/mapping/offlineRelease/mapping.txt` |

`./gradlew :app:bundleOfflineRelease` still produces an AAB if you ever need one,
but nothing publishes it — an AAB is a store format and cannot be installed
directly.
