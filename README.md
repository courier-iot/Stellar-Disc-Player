# Stellar Disc

A cyberpunk / deep-space music player for Android. It plays the music files stored on your phone.

## Features (v2.2.1)

- **Modern home:** a top bar with the "under the hood" toggle on the left, the logo in the middle, and search and settings on the right. Below that:
  - **Regular picks:** a big hero card plus a double row of picks, learned from what you finish, skip and play at each time of day.
  - **Your playlists**, then **pinned playlists** and **pinned songs** (long-press a song or open a playlist to pin it).
  - **Generated mixes:** Late night rotation, Morning starters, On repeat, Most listened to, Least listened to, Forgotten in time, Never played, Second chances, Fresh arrivals, genre mixes and an artist deep dive. Mixes can be saved as playlists.
- **Under the hood:** the original library view, one tap away (and back).
- **Search** across songs, artists, albums, playlists and phone videos.
- **YouTube sign-in:** sign in right inside the app (Settings → Uplink engine), or import a cookies.txt as a backup, so Uplink gets past "confirm you're not a bot".
- **Built-in JavaScript runtime:** the build compiles QuickJS-NG into the APK, so yt-dlp can solve YouTube's checks on the phone. Settings shows whether it's included.
- **Nightly yt-dlp** updates (optional) and **always-on yt-dlp options** applied to every download.
- **Audio-only videos:** play videos as sound only to save battery (toggle in Now Playing or Settings).
- **Library:** Songs, Albums and Artists tabs, search across title/artist/album, sort by A–Z, Newest or Artist, Play all and Shuffle all.
- **Now Playing:** album art inside spinning neon orbit rings with a moon, a synthwave horizon with a scrolling neon grid and a rising planet, animated spectrum bars, a seek bar, shuffle, and repeat (off / all / one).
- **Queue:** a sheet showing the real play order (follows shuffle). Tap any track to jump to it.
- **Background playback:** keeps playing with the screen off or the app closed. Notification, lock-screen and Bluetooth/headphone controls work, and it pauses when headphones are unplugged.
- **Mini player** with a progress line.
- **Generated planet art** for tracks with no cover, a different colour per album.
- **Playlists:** a Playlists tab. Long-press any track to play it next, add it to the queue, or add it to a playlist. Rename and delete from inside a playlist.
- **Uplink downloader (yt-dlp):**
  - Paste a link, or use **Share → Stellar Disc** from YouTube, a browser, or any app.
  - Works with YouTube, SoundCloud, Bandcamp, Vimeo and [1,000+ other sites](https://github.com/yt-dlp/yt-dlp/blob/master/supportedsites.md).
  - Grab a whole playlist or album, or just the one item.
  - Audio formats: MP3, M4A, OPUS, FLAC, WAV or ORIGINAL (untouched), with a bitrate picker for MP3/M4A/OPUS.
  - Video formats: MP4 (H.264, plays anywhere) or MKV · BEST (highest-quality VP9/AV1 streams), with a max resolution from 480p to 4K.
  - Embeds title, artist and cover art.
  - **Add to:** always saved to your library (Music/Stellar Disc or Movies/Stellar Disc). Optionally adds the download to an existing playlist or a new one, and can queue it up when finished.
  - Keeps downloading in the background with a progress notification, and you can cancel any download.
  - Your format choices are remembered for next time.
  - Advanced box for extra yt-dlp options.
- **Swap (playlist sharing between phones):**
  - **Nearby · Wi-Fi:** open a playlist, tap **Share**, and a QR code appears. Your friend taps the **swap icon** in their library header and scans it. Both phones must be on the same Wi-Fi, or your friend can join your phone's hotspot (no router needed). It doesn't use Google services or the internet.
  - **Song checker:** before anything transfers, every track is sorted into three groups. HAVE means the exact same file or source link (skipped). MAYBE means the same song as a different file, matched by title, artist and length (your copy is used unless you tick it). NEW means not in your library (ticked by default).
  - Only the tracks you pick are sent. Every file is fingerprint-checked on arrival, and the playlist is rebuilt in the original order using your own copies where you already have them.
  - **Links:** share a playlist as a readable list of source links (text or QR). It works over any distance. The receiver shares or pastes it into Swap, runs the same checker, and downloads the new tracks through Uplink.
  - Transfers keep going in the background with a notification.
- **Video player:** fullscreen with auto-rotate and auto-hiding controls. Double-tap the sides to skip 10s, double-tap the middle to play or pause, and swipe the left side for brightness or the right side for volume. Includes a subtitles toggle, fit/fill, and picture-in-picture (it pops out automatically when you leave the app mid-video).
- **Videos tab:** every video on your phone (after you allow video access) with thumbnails.
- **Stream without downloading:** in Uplink, tap Stream to play a link right away, then tap Save in Now Playing to download it later.
- **Subtitles in downloads:** video downloads can embed subtitles (pick the languages).
- **Picks up where you left off:** the queue, position, shuffle and repeat survive closing the app or restarting the phone.
- **Sleep timer:** 15/30/45/60/90 minutes or end of track, with a 15-second fade out.
- **Speed control** from 0.5× to 2×, and **smooth fades** between tracks (2 to 8 seconds).
- **Reorderable queue:** move tracks up or down, or remove them.
- **Equalizer** with your phone's presets, a band editor and bass boost.
- **Real visualizer (optional):** bars that react to the actual music. Android calls the permission "microphone", but only the app's own sound is read. It's off by default.
- **Lyrics:** reads lyrics stored in MP3s, or paste them, import an .lrc file, or look them up on lrclib.net. Synced lyrics scroll along, and you can tap a line to jump there.
- **Tag editor:** fix the title, artist, album and cover art. Edits are kept inside Stellar Disc (the files themselves aren't rewritten).
- **Folders tab** with "hide this folder", and **delete from phone** (Android asks you to confirm).
- **Smart lists:** Recently played, Most played and Recently added.
- **Backup and restore** of playlists and settings (it rebuilds playlists even on a new phone), plus **M3U import and export**.
- **Home screen widget:** a neon mini player with artwork and previous, play/pause and next buttons.
- **First-run tour**, an **update checker** that looks at this repo's GitHub releases once a day, and a **crash log** you can report straight to GitHub Issues.
- **Video playback:** downloaded MP4/MKV files play in Now Playing with the picture shown. Leave the screen and the audio keeps going.
- **Credits & Support:** the responsible-use note, an "Update yt-dlp" button, the supported sites list, and credits and licences for every open-source part.
- **Settings:** 5 accent themes (Neon Cyan, Synth Magenta, Solar Flare, Toxic Nebula, Ion Violet) and a toggle for the animated starfield to save battery.

Requires Android 8.0 or newer on a 64-bit phone (arm64). The APK is bigger than before (roughly 60–80 MB) because it carries Python, yt-dlp and FFmpeg.

## Get the APK (built by GitHub)

1. Create a new GitHub repo and upload the **contents** of this folder (branch `main`).
   - Make sure the hidden `.github` folder goes up too. Some file managers hide folders starting with a dot, and without it nothing builds.
2. GitHub Actions builds the app automatically. The **Actions** tab shows progress, which takes about 4–6 minutes.
3. Open **Releases → latest** on your phone and download `StellarDisc-v1.0.0.apk`.
4. Open it and allow installing from your browser if Android asks.
5. Open Stellar Disc and allow access to music (and notifications for the media controls).

## Important: licence

Uplink uses **youtubedl-android**, which is licensed under **GPL-3.0**. If you share the APK with anyone (including through your own app store), the app's source must be available under GPL-3.0 too. The simplest way to do that:

1. Keep the GitHub repo **public**.
2. Add a licence: on GitHub, choose **Add file → Create new file**, name it `LICENSE`, click **Choose a license template**, pick **GNU General Public License v3.0**, then commit.

## Updates and problem reports

The update checker and "Report a problem" link use `courier-iot/Stellar-Disc-Player` (set in `app/src/main/java/com/stellapp/disc/AppInfo.kt`). The checker reads the version from the APK file name in the latest release, which the build workflow already does. For updates to install over the old app, set up the fixed signing key below.

## Recommended: a fixed signing key

Without this, each build gets a random key and Android refuses to install it over the old one. You'd have to uninstall first and lose your settings. Do this once on your Linux PC:

```bash
keytool -genkeypair -v -keystore stellardisc.jks -alias stellardisc \
  -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 stellardisc.jks > stellardisc.b64
```

Then in the repo: **Settings → Secrets and variables → Actions → New repository secret** and add:

| Secret name               | Value                                    |
|---------------------------|------------------------------------------|
| `SIGNING_KEYSTORE_BASE64` | the whole contents of `stellardisc.b64` |
| `SIGNING_STORE_PASSWORD`  | the keystore password you chose          |
| `SIGNING_KEY_ALIAS`       | `stellardisc`                           |
| `SIGNING_KEY_PASSWORD`    | the key password (often the same)       |

Keep `stellardisc.jks` backed up somewhere safe and **never** commit it. If you lose it, you can't ship updates that install over the existing app.

## Making updates

Change `appVersion` near the top of `app/build.gradle.kts` (format `vLARGE.MEDIUM.SMALL`). The version code is calculated from it, so Android always sees the new build as newer.

## Changelog

### v2.2.1
**Added:** an automatic one-time yt-dlp update before the first download or stream when the bundled copy is too old for the JavaScript runtime.
**Removed:** none
**Changed:** the runtime options (`--js-runtimes`, `--remote-components`) are now only passed to yt-dlp 2025.11.12 or newer, which fixes "no such option: --js-runtimes".

### v2.2.0
**Added:** in-app YouTube sign-in that turns your sign-in into cookies for yt-dlp automatically, the QuickJS-NG JavaScript runtime built into the APK (compiled by the GitHub workflow) and passed to yt-dlp together with its challenge-solver scripts, an optional nightly yt-dlp update channel, always-on yt-dlp options, and a runtime status line in Settings.
**Removed:** none
**Changed:** the Settings section is renamed from YouTube sign-in to Uplink engine. Signing out now also clears the in-app browser's cookies. The build workflow gains a QuickJS step, and if that step fails the app still builds without the runtime.

### v2.1.0
**Added:** the modern home screen (Regular picks hero and double row, playlists, pinned playlists, pinned songs, generated mixes), an on-device taste engine that learns from finished, partial and skipped listens and the time of day, pin and unpin for songs and playlists, "save mix as playlist", a search screen, genre mixes (Android 11+), YouTube sign-in with a cookies.txt file, and audio-only video playback.
**Removed:** none
**Changed:** the original library is now the "under the hood" view, with a toggle to switch. The long-press menu adds Pin to home, and playlist pages add a pin button. Settings gains Video and YouTube sign-in sections plus a Modern home switch.

### v2.0.0
**Added:** a fullscreen video player (gestures, auto-rotate, subtitles toggle, fit/fill, picture-in-picture), a Videos tab for every phone video, streaming without downloading (plus save-from-stream), subtitle embedding for video downloads, resume after closing or rebooting, a sleep timer, playback speed, smooth fades between tracks, a reorderable queue, an equalizer with bass boost, an optional real visualizer, lyrics (from MP3 files, paste, .lrc import, lrclib.net, synced scrolling and tap-to-seek), a tag and cover editor, a Folders tab with hide, delete from phone, smart lists (recently played, most played, recently added), backup and restore, M3U import and export, a home screen widget, a first-run tour, an update checker, a crash log with GitHub reporting, a full Settings screen, and HLS/DASH stream support.
**Removed:** the small settings pop-up (replaced by a full Settings screen) and the planet-with-pulse app icon.
**Changed:** the new icon is a neon disc with a pulse line. Playback skips broken files instead of stopping. The library header shows track and video counts. Songs lists hide phone videos (they live in the Videos tab). Credits & Support adds app update checks and crash reports. The widget, notification and video artwork use your custom covers.

### v1.2.0
**Added:** Swap. Share a playlist over the same Wi-Fi or hotspot with a QR code. A built-in song checker (exact-file fingerprint, source-link match and fuzzy title/artist/length match) sorts tracks into HAVE, MAYBE and NEW. You choose what to receive, files are integrity-checked, and the playlist is rebuilt using your existing copies. Link sharing as text or a QR code, with import into Uplink. Uplink now remembers each download's source link. There's a Share button on playlists, a Swap scanner in the library header, background transfer notifications, and an "open in Swap" shortcut when a shared list is pasted into Uplink.
**Removed:** the Rescan button from the library header (it's now in Settings).
**Changed:** the library header is slimmed to Uplink, Swap and Settings. Songs now record their file size for the checker. The app allows plain HTTP, which Swap needs to talk between phones on your local network (each session is protected by a one-time code).

### v1.1.0
**Added:** Uplink downloader (yt-dlp) with share-to-app, playlist/album downloads, audio formats MP3/M4A/OPUS/FLAC/WAV/ORIGINAL with bitrate choice, video formats MP4 and MKV · BEST with max resolution, cover art and tag embedding, "Add to" playlist (existing or new) and queue-when-done, background downloads with notifications and cancel, advanced yt-dlp options, remembered format defaults. Playlists tab, long-press track menu (play next, add to queue, add to/remove from playlist), playlist rename/delete. Video playback in Now Playing. Credits & Support screen with the responsible-use note, Update yt-dlp button, supported sites link and open-source credits. Internet permission (needed for downloads).
**Removed:** support for 32-bit-only phones (to keep the APK size down).
**Changed:** the library also shows videos saved in Movies/Stellar Disc, marked VID. The search box was restyled to match the other text fields, and the library tabs now scroll sideways. The Uplink button sits in the library header, and Settings links to Credits & Support.

### v1.0.0
**Added:** library with Songs/Albums/Artists, search, sort, play and shuffle all, Now Playing screen with orbit art, synthwave grid and spectrum bars, queue sheet, background playback with notification and lock-screen controls, mini player, generated planet art, 5 accent themes, starfield toggle, GitHub build workflow with optional fixed signing.
**Removed:** —
**Changed:** —
