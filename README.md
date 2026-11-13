# Stellar Disc

**A music player from deep space.** Your songs, videos and playlists in one neon cockpit, or on a hand-inked manga page.

[![Latest release](https://img.shields.io/github/v/release/courier-iot/Stellar-Disc-Player?label=latest&color=00b8c4)](https://github.com/courier-iot/Stellar-Disc-Player/releases/latest)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3ddc84)
![No Google services](https://img.shields.io/badge/Google%20services-none-555555)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE)

Stellar Disc plays the music and videos on your phone, learns what you like, and builds mixes around it. It can also download from 1,000+ sites, share playlists between phones over Wi-Fi, and move your whole library to a new phone. No account, no ads, no tracking, and no Google services.

## Download

**[Get the latest APK →](https://github.com/courier-iot/Stellar-Disc-Player/releases/latest)**

1. On your phone, open the link above and download `StellarDisc-vX.Y.Z.apk`.
2. Open it. If Android asks, allow installs from your browser.
3. Launch Stellar Disc and allow access to your music (and notifications, for the playback controls).

Requires Android 8.0 or newer on a 64-bit phone. The app checks for updates once a day and tells you when one is ready.

## Highlights

**For everyone**

- **Made for your library.** Songs, albums, artists, folders and playlists, with a fast A–Z rail and search across everything.
- **A home screen that learns.** Top picks and mixes such as *Late night rotation*, *On repeat* and *Forgotten in time*, shaped by what you play, skip and finish.
- **Gesture-first player.** Swipe the disc to skip, swipe up for lyrics, double-tap to pause. Every gesture is listed in *Settings → Gestures & tips*.
- **Two looks.** *Cyber*: animated space scenes, real NASA and Hubble imagery, or your own photo. *Ink & Screentone*: a manga page in red, blue or black ink.
- **Plays everywhere.** Background playback, lock screen and notification controls with ♥ and shuffle, Bluetooth auto-play, and a resizable home screen widget.
- **Download with one tap.** Share a link to Stellar Disc, pick *Music* or *Video* and *Best*, *Balanced* or *Data saver*. If something fails, it says why in plain words.
- **Share with friends.** Show a QR code; on the same Wi-Fi, their phone checks what they already have and receives only the rest.
- **Moving phones?** Send your music, videos, playlists, favourites and settings to a new phone. You see whether it fits before anything is sent.
- **Ask for a song by name.** "Play Bohemian Rhapsody", "play some Daft Punk", "play my Gym playlist": Stella (StellAI), Google Assistant, Android Auto and other assistants can start music from your library, and "skip" or "pause" works too. Stella can also send songs to Uplink ("download Midnight City by M83"); it opens filled in for you to confirm, or downloads straight away with *Settings → Let assistants start downloads*.
- **Kind to your battery.** Pure-black mode for OLED, calmer animations in Battery Saver, and downloads that can wait for Wi-Fi.

**For power users**

- **Uplink, powered by yt-dlp:** *Manual control* for MP3, M4A, Opus or untouched original audio, MP4 or MKV video up to 4K, chosen bitrates, embedded subtitles, and raw yt-dlp options (filtered by a safety guard, with an optional strict mode). Nightly yt-dlp updates are optional.
- **YouTube sign-in or cookies.txt** for age-restricted content and bot checks. Credentials are stored encrypted with Android Keystore.
- **Equalizer** with presets, a band editor and bass boost, plus speed control, crossfades, volume levelling and a sleep timer.
- **Lyrics** from MP3 tags, `.lrc` files or lrclib.net. Synced lyrics scroll along; tap a line to jump there.
- **Tag editor** that leaves your files untouched, listening stats, smart lists, and an editable queue that survives restarts.
- **Full backups** of playlists, favourites, play counts, lyrics and tag edits, matched song by song on a new phone, plus M3U import and export.
- **Built for new hardware:** every native library supports 16 KB memory pages, and each build reports it.

## Privacy & security

- No analytics, no ads, no account and no Google services. The internet is used only for downloads, streams, lyric look-ups, yt-dlp updates and the daily update check.
- Phone-to-phone transfers are end-to-end encrypted. The key travels only inside the QR code, every request needs a one-time code, and repeated wrong attempts are locked out.
- Your YouTube sign-in is encrypted on the phone and is never included in backups or transfers.
- Nothing goes to Android's cloud backup. Backups are files you create and keep.

## Tips

- **"The site thinks we're a bot"**: connect your account in *Settings → Uplink engine*, or tap *Update yt-dlp* in *Credits & Support*.
- **Swap or a move says "No signal"**: both phones need the same Wi-Fi, or one can join the other's hotspot. Pause any VPN first.
- **Downloads waiting?** *Wi-Fi only* may be on (*Settings → Uplink engine*).

## Responsible use

Stellar Disc is a player and a downloader for content you have the right to keep. Only download or share what you own or have permission to use.

## Credits & license

Stellar Disc is free software under the [GNU GPL v3.0](LICENSE). It builds on [yt-dlp](https://github.com/yt-dlp/yt-dlp), [youtubedl-android](https://github.com/yausername/youtubedl-android), FFmpeg, Python, QuickJS, AndroidX Media3, Jetpack Compose and Glance, ZXing, and the [Bangers](https://github.com/googlefonts/bangers) typeface (SIL OFL). Photo backdrops come from NASA, ESA/Hubble and Unsplash. Full credits and licences are in the app under *Credits & Support*.

---

[Changelog](CHANGELOG.md) · [Building from source](docs/BUILDING.md) · [Report a problem](https://github.com/courier-iot/Stellar-Disc-Player/issues)
