# Changelog

Every release of Stellar Disc, newest first. Download builds from [Releases](https://github.com/courier-iot/Stellar-Disc-Player/releases).

### v3.4.0
**Added:** play by name for voice assistants: "play Bohemian Rhapsody", "play some Daft Punk", "play the album Discovery", "play my Gym playlist" or just "play music" from Stella (StellAI), Google Assistant, Android Auto and others finds the songs in your library and plays them (a song keeps going with more by the same artist). Uplink requests from Stella: "download Midnight City by M83" (a link, or the top YouTube result for the words) as music or video at an Autopilot quality, optionally added to your queue when done. A *Let assistants start downloads* switch in Settings (off: Uplink opens filled in and you tap Download). Tests for the matching.
**Removed:** nothing
**Changed:** the player accepts play requests that carry a search instead of a file; everything the app plays itself works as before. Uplink accepts a YouTube search ("ytsearch1:…") in place of a link.

### v3.2.1
**Added:** a keep-alive ping while the new phone is choosing what to move. Tests proving the faster matcher picks exactly what the old one did.
**Removed:** nothing
**Changed:** a move's share stays on for up to 60 minutes of quiet (normal shares keep 10). The song checker indexes your library by cleaned title and "artist title" instead of comparing every pair, so thousands of songs check in a fraction of a second.

### v3.2.0
(Also covers work delivered as v3.2.0 and v3.3.0 but never uploaded; this is the first release after v3.1.2.)
**Added:** Move to a new phone over Wi-Fi with a storage bar, quick picks, Fit to space and grouped tick boxes, bringing playlists, favourites, play counts and settings. The Ink & Screentone theme (red, blue, pure-black inks; Bangers font, OFL). Uplink Autopilot (Music/Video × Best/Balanced/Data saver) with a Manual control toggle. Plain-language download errors with DETAILS. One engine status line plus an Engine room fold in Settings. A "VPN is on" hint for Swap.
**Removed:** the FLAC and WAV formats; the playlist question for non-playlist links in Autopilot.
**Changed:** the engine spool-up screen grows with its log and shrinks the turbine on short screens, so text is no longer cut off. Themed Uplink wording. Status bar icons go dark on the paper theme. Moves match files by size and tags instead of fingerprints so the new phone isn't kept waiting.

### v3.1.2
**Added:** nothing
**Removed:** the per-page mini players (replaced by one shared bar)
**Changed:** Swap pins its connection to the network the other phone is on. A hotspot without mobile data looks like "no internet" to Android, which otherwise sends the traffic over mobile data instead. The share code now lists up to 4 private addresses (Wi-Fi and hotspot first). The mini player lives in the app shell under every music page (home, library, playlists, mixes, search) and stays put while pages change.

### v3.1.1
**Added:** connection details on the Swap "No signal" screen (what went wrong for each address tried). Unit tests for the new LAN client.
**Removed:** nothing
**Changed:** Nearby Swap now connects. Android blocks plain `http://` for modern apps, so the receiving phone's requests never left it; Swap now uses a small direct socket client (`swap/LanHttp.kt`). Transfers were already end-to-end encrypted, so no app-wide "allow insecure traffic" exception was added.

### v3.1.0
**Added:** a ⇄ SHARE chip and share icon on playlist pages opened from the home screen (QR code or links). A "Send one of yours" list on the Swap screen. The mini player on playlist, mix and search pages.
**Removed:** the top-left "under the hood" button on the home screen (▤ FULL LIBRARY does the same).
**Changed:** the Swap screen is now "send or receive a playlist". Gestures & tips lists the new share routes.

### v3.0.2
**Added:** nothing
**Removed:** the workflow step that compiled QuickJS-NG (the downloader library now ships its own, 16 KB ready)
**Changed:** youtubedl-android 0.17.4 → 0.18.1 (yt-dlp 2025.11.12, Python 3.12, FFmpeg 7.1.1). Every native library is now 16 KB-page ready, so Uplink works on 16 KB phones.

### v3.0.1
**Added:** nothing
**Removed:** nothing
**Changed:** fullscreen video no longer goes black after rotating (fullscreen always keeps the picture, even when rotating rebuilds the Now Playing preview behind it).

### v3.0.0
**Added:** ♥ and shuffle buttons on the media notification and lock screen. A resizable widget (small, wide, large) with a ♥ button. Pure black backgrounds for OLED. An A–Z fast-scroll rail on the Songs list. A Gestures & tips page in Settings. A branded launch screen and a themed monochrome icon.
**Removed:** nothing
**Changed:** proper empty screens for no music, playlists, favourites and downloads. The welcome tour covers swipes and favourites. Fullscreen video keeps a single picture after rotating (the Now Playing video view hands over instead of leaving a frozen frame).

### v2.9.0
**Added:** encrypted Swap transfers (key only in the QR code). Backups of favourites, pins, play counts, listening history, lyrics, tag edits and source links, matched by song. A strict yt-dlp options toggle and a solver-from-GitHub fallback toggle. 16 KB page support for QuickJS, plus a build check that lists which native libraries are 16 KB ready and a notice when a newer downloader library is out. Plain-language messages when the downloader can't start.
**Removed:** downloading yt-dlp's YouTube solver scripts from GitHub on every run (now an opt-in fallback)
**Changed:** an unreadable YouTube sign-in is detected and cleared with a message. The random rotation refills from the playback service. Multi-file delete works on Android 10. Partial downloads and transfers are cleaned up on every cold start. Undo returns queue items beside their old neighbours.

### v2.8.0
**Added:** favourites with a ♥ Favourites playlist, volume levelling, resume for long tracks, Bluetooth auto-play, undo for queue/playlist removals and playlist deletes, multi-select in the library, listening stats, Uplink Wi-Fi only, automatic playlist-or-single detection for Uplink links, a "What's new" sheet, and gestures (swipe down to close, swipe up for lyrics, double-tap to play/pause, long-press for the queue, edge volume slide, mini player swipes). Swap transfers resume if Wi-Fi drops.
**Removed:** Android cloud backup of app data (use the app's own backup instead)
**Changed:** the YouTube sign-in is encrypted with the phone's keystore. yt-dlp options that can run programs are blocked. Swap shuts off after 10 idle minutes and locks out repeated wrong codes. Crash reports are scrubbed of paths and file names. Update downloads only ever point at this repo's releases. Deleted files go to the system trash on Android 11+.

### v2.7.0
**Added:** a rolling 150-song random rotation (from song 51, the oldest drops off and a new one is added). Downloads resume after the app is closed or restarted. Backups now include the backdrop, Clear view, home and hero settings and your own backdrop photo. Landscape and tablet layout for Now Playing. Predictive back, light haptics, and screen-reader actions for next and previous on the disc. Automatic tests that run before every build.
**Removed:** the unlimited random queue (it used to queue your whole library at once)
**Changed:** covered screens stop animating, and Battery Saver calms animations. Photo backdrops use half the memory and free it when the phone runs low. Targets Android 15, and handles its background-download time limit. Open playlist, artist and album pages come back after Android closes the app in the background. Smoother screen transitions. The app's main controller is split into smaller parts (settings, backups, lyrics, Uplink options) so future updates are less likely to break things.

### v2.6.0
**Added:** 13 photo backdrops from real images (Carina nebula, Spiral galaxy, Deep field, Saturn, Saturn close-up, Eclipse, Primordial black holes, Warp drive photo, ISS window, Neon city, Kabukicho, Night train, Tokyo from above), each with subtle motion like slow drift, rain, glints or flicker. A "Your photo" backdrop that uses any picture from your gallery. A new drawn Moonwave scene. A Backdrop photos section in Credits & Support.
**Removed:** the drawn Freighter bridge, Ringworld, Neon city, Deep void and Spiral galaxy scenes (replaced by the photo versions)
**Changed:** Synthwave now has layered mountain ranges, a far-off skyline and the sun's reflection on the grid. Photo backdrops line up with the disc and get a soft shade at the top and bottom so the controls stay readable.

### v2.5.0
**Added:** Clear view in Now Playing (fade the disc or video out to see the backdrop in full; tap to bring it back). Swipe the centre of Now Playing to change tracks. Uplink engines that spool up when you open Uplink and cool down when you leave, each with its own screen. 4 accent gradients: Obsidian Silver (with pure black), Crimson Ember, Glacier Comet and Rose Gold. 4 backdrops for 12 in total: Spiral galaxy, Pulsar, Fighter cockpit and Freighter bridge. Random rotation when you start from a single song.
**Removed:** the backdrop-cycling ✦ chip from Now Playing (backdrops are picked in Settings → Look)
**Changed:** the Now Playing centre is now the Stellar Disc (a spinning neon record) instead of a planet in orbit rings, so there's no planet inside a planet. The Black hole backdrop's accretion disk is one continuous ring.

### v2.4.0
**Added:** 8 animated player backdrops bundled in the app (Synthwave, Nebula, Warp drive, Aurora, Black hole, Ringworld, Neon city, Deep void), with a ✦ chip in Now Playing to cycle them and a preview picker in Settings → Look. A hero picker on Home: Top pick, Most played, Playlist, Rare listen, Forgotten gem, Fresh arrival or Random. Shooting stars, flaring bright stars and drifting nebula clouds in the starfield.
**Removed:** the large "New playlist" card on Home
**Changed:** "+ NEW" is now a small tab next to the Playlists heading. Planets are smoother, with rings that pass behind the planet and come round in front, plus cloud bands, a storm spot, a shadow side and a neon rim. The synthwave sun is now banded. The double row of picks no longer repeats the hero song.

### v2.3.0
**Added:** a "Skip what I already have" option in Uplink (on by default), which uses yt-dlp's download archive to skip items you've already downloaded (tracked separately for audio and video, and forgotten when you delete the file), plus a post-download duplicate check that removes a new file if you already have the same song and still adds your existing copy to the target playlist. Delete from phone now works from Home, Search and the playlist and mix pages.
**Removed:** the playlist position ("3 - ") from downloaded file names and titles
**Changed:** titles like "Artist - Song" with no artist tag are now split into artist and title, and " - Topic" is dropped from YouTube artist names.

### v2.2.2
**Added:** none
**Removed:** 0:00-length songs and videos from the library
**Changed:** the library now skips any track Android reports as 0:00, as well as clips under 5 seconds

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
