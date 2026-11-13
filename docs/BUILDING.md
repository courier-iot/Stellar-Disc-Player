# Building Stellar Disc

Stellar Disc is built by GitHub Actions on every push to `main`. No local Android setup is needed.

## How a release is made

1. Change `appVersion` near the top of `app/build.gradle.kts` (format `MAJOR.MINOR.PATCH`). The Android version code is worked out from it, so every build counts as newer than the last.
2. Add the version's notes to `app/src/main/java/com/stellapp/disc/ui/WhatsNew.kt` (the in-app "What's new" sheet) and to [`CHANGELOG.md`](../CHANGELOG.md).
3. Push to `main`. The workflow (`.github/workflows/build.yml`):
   - runs the unit tests (`gradle testReleaseUnitTest`);
   - builds and signs the release APK;
   - checks that every native library supports 16 KB memory pages and reports it in the run summary;
   - flags any newer youtubedl-android release;
   - publishes a release tagged `vX.Y.Z` with `StellarDisc-vX.Y.Z.apk` attached.

A build takes about 4–6 minutes.

> **Uploading through the GitHub website?** It skips files and folders whose names start with a dot. Edit `.github/workflows/build.yml` on GitHub directly (✏️) rather than re-uploading it.

## Signing key

Updates only install over an existing copy when every build is signed with the same key. Create one once:

```bash
keytool -genkeypair -v -keystore stellardisc.jks -alias stellardisc \
  -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 stellardisc.jks > stellardisc.b64
```

Then add these under **Settings → Secrets and variables → Actions**:

| Secret                    | Value                                   |
|---------------------------|-----------------------------------------|
| `SIGNING_KEYSTORE_BASE64` | the contents of `stellardisc.b64`       |
| `SIGNING_STORE_PASSWORD`  | the keystore password                   |
| `SIGNING_KEY_ALIAS`       | `stellardisc`                           |
| `SIGNING_KEY_PASSWORD`    | the key password (often the same)       |

Keep `stellardisc.jks` and its passwords backed up offline and **never** commit them. Without the original key, existing installs can't be updated.

## Toolchain

Kotlin 2.0, Android Gradle Plugin 8.7, compile and target SDK 35, minimum SDK 26, `arm64-v8a` only. Jetpack Compose, Media3, Glance and [youtubedl-android](https://github.com/yausername/youtubedl-android) (yt-dlp, Python, FFmpeg and QuickJS). The release build is not minified.

## Update checker and problem reports

The in-app update checker and "Report a problem" link point at `courier-iot/Stellar-Disc-Player`, set in `app/src/main/java/com/stellapp/disc/AppInfo.kt`. The checker reads the version from the APK file name in the latest release, which the workflow sets automatically.
