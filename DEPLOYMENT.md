# Deploying Naomi to Google Play

This is the whole path from a commit to an app on someone's phone, including the
parts nobody can automate for you.

**Contents**

- [The shape of it](#the-shape-of-it)
- [Files that must never be committed](#files-that-must-never-be-committed)
- [1. The upload keystore](#1-the-upload-keystore)
- [2. Local signing](#2-local-signing)
- [3. GitHub secrets](#3-github-secrets)
- [4. Google Play service account](#4-google-play-service-account)
- [5. The first upload must be manual](#5-the-first-upload-must-be-manual)
- [6. Releasing to Internal Testing](#6-releasing-to-internal-testing)
- [7. Releasing to Production](#7-releasing-to-production)
- [8. Versioning](#8-versioning)
- [9. When something goes wrong](#9-when-something-goes-wrong)
- [Commands](#commands)

---

## The shape of it

```
push to main
    └─ android-release.yml
         lint · tests · privacy gates
         → signed AAB (offline flavour)
         → Play Internal Testing          ← automatic, closed track

manual: Actions → Android Production Release
    └─ android-production.yml
         type "PRODUCTION" + versionCode
         → promotes the tested artifact   ← never automatic
         → staged rollout
```

Two things about this are deliberate.

**Internal Testing is not publishing.** It is a closed track visible only to
testers you list. Reaching it automatically on every green push is safe; reaching
production automatically is not, because a production release cannot be pulled
back off devices that already installed it.

**Production promotes rather than rebuilds.** The artifact testers have been
using is the one that goes live. Rebuilding from the same source would produce a
different binary from the one that was actually tested.

Only the **`offline`** flavour is ever published. The `connected` flavour declares
`INTERNET` and exists to be built from source by someone who deliberately wants
the web-reading layer.

---

## Files that must never be committed

All of these are in `.gitignore`. The check in [§11](#9-when-something-goes-wrong)
verifies it.

| Pattern | What it is |
|---|---|
| `*.jks`, `*.keystore`, `*.p12` | The upload key. Losing control of it means someone else can ship updates as you. |
| `keystore.properties` | Local signing passwords. |
| `*.pem`, `*.key` | Private keys of any kind. |
| `service-account.json`, `*-service-account.json`, `google-play-*.json` | Play API credentials. Grants upload rights to your listing. |
| `.env`, `.env.*` | Anything else with credentials in it. |
| `local.properties` | Machine-local SDK paths. |

`keystore.properties.example` **is** committed. It contains placeholders only.

---

## 1. The upload keystore

An upload key is not the same as the app signing key. Play re-signs your app with
a key Google holds; yours only proves uploads come from you. That distinction is
what makes losing it recoverable — see [§9](#if-you-lose-the-upload-key).

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

Play requires validity through at least 2033; 10950 days (~30 years) clears that.

### Protecting it

**Back it up before you do anything else.** Put the `.jks` and its password in a
password manager or an encrypted archive somewhere that is not this machine and
not this repository. If your disk dies today, the keystore dies with it.

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
./gradlew :app:bundleOfflineRelease
```

---

## 3. GitHub secrets

**MANUAL STEP.** I cannot create these — they require your GitHub account.

Go to **Settings → Secrets and variables → Actions → New repository secret** and
add five:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | The keystore, base64-encoded (below) |
| `KEYSTORE_PASSWORD` | `storePassword` from `keystore.properties` |
| `KEY_ALIAS` | `upload` |
| `KEY_PASSWORD` | `keyPassword` from `keystore.properties` |
| `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` | The whole service-account JSON, pasted verbatim ([§4](#4-google-play-service-account)) |

To encode the keystore:

```bash
# macOS / Linux
base64 -w0 ~/.android-keystores/naomi-upload.jks | pbcopy   # or > keystore.b64

# Windows PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$HOME\.android-keystores\naomi-upload.jks")) | Set-Clipboard
```

Paste the whole string as `KEYSTORE_BASE64`. If you wrote it to a file, **delete
that file afterwards** — it is the keystore in another form.

Secrets are masked in logs. The workflows additionally never echo them: the
keystore is decoded to a file outside the workspace, and the service-account JSON
is written and then deleted in an `always()` step so it goes even if the upload
fails.

---

## 4. Google Play service account

**MANUAL STEP.** This needs your Google Cloud and Play Console accounts.

1. **Play Console → Setup → API access.** Link a Google Cloud project if you have
   not already.
2. In **Google Cloud Console → IAM & Admin → Service Accounts**, create one, e.g.
   `naomi-play-publisher`. It needs no Cloud IAM roles.
3. On that service account, **Keys → Add key → Create new key → JSON**. The file
   downloads once and cannot be re-downloaded.
4. Back in **Play Console → Users and permissions → Invite new user**, invite the
   service account's email address and grant, for Naomi only:
   - **Release to testing tracks**
   - **Release to production** *(only if you want the production workflow to work)*
   - **View app information**
5. Paste the JSON's entire contents into the `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`
   GitHub secret, then **delete the downloaded file**.

Permissions can take a few minutes to propagate. A `401`/`403` on the first run
usually means "wait and retry", not "misconfigured".

---

## 5. The first upload must be manual

**MANUAL STEP, and it cannot be skipped.**

The Play Developer API cannot create an app listing, and it cannot perform the
*first* upload of a package. Until `com.naomi.app` exists in Play Console with one
release uploaded by hand, every API upload fails.

Once, before CI can work:

1. **Play Console → Create app.** Package name must be exactly `com.naomi.app`.
2. Complete the tasks Play requires before any release: app access, ads
   declaration, content rating, target audience, data safety, privacy policy URL.
   *(Naomi's data-safety answers are unusually easy — no data leaves the device,
   and the published build has no `INTERNET` permission at all.)*
3. Build an AAB locally and upload it by hand to **Internal Testing**:
   ```bash
   ./gradlew :app:bundleOfflineRelease
   # app/build/outputs/bundle/offlineRelease/app-offline-release.aab
   ```
4. Add at least one tester email to the Internal Testing track.
5. **Opt in to Play App Signing** when prompted. It is the default and it is what
   makes a lost upload key recoverable.

After that one upload, every subsequent release can come from CI.

---

## 6. Releasing to Internal Testing

Automatic. Push to `main`; `android-release.yml` runs lint, unit tests and both
privacy gates, builds a signed AAB, uploads it as a run artifact, and pushes it to
Internal Testing.

To release without pushing — or to target `alpha`/`beta` — use
**Actions → Android Release (Internal Testing) → Run workflow** and pick a track.

If `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` is not set, the workflow still builds and
attaches the signed AAB, and just skips the upload. That is intentional: it lets
you set up signing and Play access in either order.

---

## 7. Releasing to Production

Manual, and guarded twice.

**Actions → Android Production Release → Run workflow**, then:

- `confirm` — type `PRODUCTION` exactly
- `version_code` — the versionCode already on Internal Testing that you want to
  promote (the release workflow's summary prints it)
- `rollout_percentage` — `0.1` for 10%, `1.0` for everyone

**Recommended second gate.** Create a GitHub Environment named `production` with
required reviewers, and the job will pause for approval before running:

> **MANUAL STEP:** Settings → Environments → New environment → `production` →
> Required reviewers → add yourself.

Without that environment the job still runs, so the typed confirmation is the
minimum protection. With it, nothing reaches production without a second person —
or at least a second decision.

A staged rollout can be increased or halted in **Play Console → Production →
Releases**. It cannot be undone: users who already updated keep the new version.

---

## 8. Versioning

Two numbers, two different jobs.

**`versionName`** — `"0.2.0"` in `app/build.gradle.kts`. Edited by hand. It should
change because a release means something, not because a build happened. Bump it in
the same commit that closes the `[Unreleased]` section of `CHANGELOG.md`.

**`versionCode`** — supplied by CI as `1000 + github.run_number`. Monotonic,
stateless, and it can never collide with an upload that already exists. The `1000`
offset clears the codes already used by 0.1.0 and 0.2.0.

You do not bump `versionCode` by hand and you should not try. Play rejects a
duplicate, and a code can never be reused even after its release is deleted — so
the only property that matters is that it always increases, and a human
remembering to increment it is exactly the part that fails.

Local builds use `VERSION_CODE_FALLBACK` (3), which Play never sees.

To cut a release:

```bash
# 1. Bump versionName in app/build.gradle.kts, close the CHANGELOG section
# 2. Commit and push to main  →  Internal Testing, automatically
# 3. Test it
# 4. Actions → Android Production Release → promote that versionCode
```

To override the code for a one-off local build:

```bash
VERSION_CODE=1234 ./gradlew :app:bundleOfflineRelease
```

---

## 9. When something goes wrong

**"Version code N has already been used."**
The run number went backwards, or you uploaded that code by hand. Re-run the
workflow — the next run number produces a new code. Never lower the offset.

**`401` / `403` from the Play API.**
Either the service account has not been invited in Play Console → Users and
permissions, or its permissions have not propagated yet. Wait a few minutes.

**"Package not found" on the first CI upload.**
[§5](#5-the-first-upload-must-be-manual) has not been done. The API cannot create
a listing or perform a package's first upload.

**Signing fails in CI.**
`verifyReleaseSigning` runs before the build and names the missing variable. The
usual cause is `KEYSTORE_BASE64` pasted with line breaks — re-encode with
`base64 -w0`.

**A bad build reached Internal Testing.**
Push a fix. The next build supersedes it. Testers are people who agreed to test;
this is what the track is for.

**A bad build reached Production.**
Halt the staged rollout in Play Console immediately, then ship a fix with a higher
`versionCode`. You cannot remove a version from devices that already have it.

### If you lose the upload key

Recoverable, if you enrolled in **Play App Signing** ([§5](#5-the-first-upload-must-be-manual)):

1. Generate a new upload keystore ([§1](#1-the-upload-keystore)).
2. Play Console → Setup → App integrity → **Request upload key reset**.
3. Google resets it, usually within a couple of days.
4. Update `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

Without Play App Signing, a lost key means the app can never be updated again and
must be republished under a new package name, losing every install and review.
This is the single best argument for enrolling.

### If the keystore is exposed

Treat it as compromised even if you are not sure. Request an upload key reset as
above, rotate the GitHub secrets, and — if it was ever committed — remember that
rewriting history does not un-publish anything already fetched.

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
./gradlew :app:assembleOfflineRelease   # signed APK  (sideloading)
./gradlew :app:bundleOfflineRelease     # signed AAB  (Play)

# Verify what you built
aapt2 dump badging   app/build/outputs/apk/offline/release/app-offline-release.apk | grep version
aapt2 dump permissions app/build/outputs/apk/offline/release/app-offline-release.apk
apksigner verify -v  app/build/outputs/apk/offline/release/app-offline-release.apk

# The connected flavour — not published, build it yourself if you want it
./gradlew :app:assembleConnectedRelease
```

**Artifact paths.** Because the project has product flavours, these are *not* the
conventional `…/release/app-release.aab`:

| Artifact | Path |
|---|---|
| AAB (Play) | `app/build/outputs/bundle/offlineRelease/app-offline-release.aab` |
| APK (sideload) | `app/build/outputs/apk/offline/release/app-offline-release.apk` |
| R8 mapping | `app/build/outputs/mapping/offlineRelease/mapping.txt` |

The mapping file is what turns a Play crash report back into a readable stack
trace. CI uploads it with every release; keep it for any build you ship.
