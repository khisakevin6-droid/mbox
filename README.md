# MBOX

Android movie and TV app (Kotlin, Jetpack Compose, Material 3, Media3 ExoPlayer). It plays only content
you are authorized to access. **This project has not been compiled or run by its author; the first GitHub
Actions build is the first real test (see "Status").**

## Status (read this first)
- The source was written without an Android SDK or network access, so it has **never been built or tested**.
  A first build may need small fixes. If the Actions run fails, open the failed step's log and send me the
  red error lines.
- The Gradle wrapper JAR is binary and could not be generated here, so `gradlew` scripts are not included.
  The workflow installs Gradle 8.9 itself and runs `gradle`. To get a wrapper locally, install Gradle and run
  `gradle wrapper --gradle-version 8.9` in the project folder.
- Demo catalogue sources point to well-known public sample hosts. They were **not** verified; they are all
  flagged `verified: false` and shown as "Demo source" in the app.

## Architecture
```
com.mbox.stream
  domain/      Title, Episode, StreamSource models + CatalogRepository interface
  data/        JSON parser, asset/remote/TMDB repositories, Room database (favorites, watchlist,
               progress/history, saved videos)
  sources/     SourceProvider interface + CatalogSourceProvider + HttpJsonSourceProvider
  settings/    DataStore preferences
  ui/          Home, Discover, Library, Detail, Settings screens, theme
  navigation/  NavHost + bottom bar (Home, Discover, Library, Settings)
  player/      PlayerActivity (owns ExoPlayer lifecycle) + Compose controls
  magnet/      Optional isolated magnet validator/UI (no torrent engine)
```
Metadata providers are swapped in Settings (sample / your own catalogue URL / TMDB) because the UI only
talks to `CatalogRepository`.

## Catalogue JSON format
```json
{
  "titles": [
    {
      "id": "unique-id", "title": "My Film", "type": "movie", "year": 1922, "runtime": 94,
      "rating": 7.9, "popularity": 50, "added": "2026-01-31",
      "synopsis": "...", "poster": "https://...", "backdrop": "https://...",
      "genres": ["Horror"], "demo": false,
      "sources": [
        { "provider": "My server", "url": "https://example.com/film.m3u8", "type": "hls",
          "subtitle": "https://example.com/film.vtt", "verified": true, "note": "Own content" }
      ]
    },
    {
      "id": "my-series", "title": "My Series", "type": "series", "genres": ["Drama"],
      "seasons": [ { "number": 1, "episodes": [
        { "number": 1, "title": "Pilot", "runtime": 24,
          "sources": [ { "provider": "My server", "url": "https://example.com/e1.mp4", "type": "mp4" } ] }
      ] } ]
    }
  ]
}
```
`type` for sources: `mp4`, `hls`, `dash` or `auto`. Do not use `null` values; omit a field instead.
To use your own file: host it over HTTPS, then Settings > Metadata provider > "My catalogue URL".
To replace the demo content in the APK, edit `app/src/main/assets/catalog.json`.

A **source API** (optional, Settings) is a URL template such as `https://example.com/sources?id={id}&ep={episode}`
that returns `{"sources":[ ... ]}` using the same source objects.

## Source types
| Source | How |
|---|---|
| Public-domain / own server / direct URL | `sources` in the catalogue, or Library > My Videos > Add URL (https only) |
| Licensed API | Source API template in Settings |
| Local file | Library > My Videos > Add file (Storage Access Framework) |

Plain HTTP is blocked by default; see `res/xml/network_security_config.xml` to allow a specific host.

## TMDB
Create a free key at themoviedb.org, paste it in Settings, choose "TMDB". The key stays on the device and is
never committed. TMDB gives metadata and artwork only (no video). Attribution is shown in Settings.

## Magnet module (optional, off by default)
Implemented: enable in Settings; paste a link; the app validates it and shows name, info-hash, trackers and size;
you can hand it to a torrent app you installed. **Not implemented:** in-app torrent download/streaming.
No torrent library is bundled because licensing and Android compatibility have not been verified.
The app never searches for or ships magnet links.

## Upload to GitHub
1. Create a GitHub account and a new empty repository (e.g. `mbox`), no README.
2. Easiest (no git): open the repository, choose **Add file > Upload files**, drag in the *contents* of the
   unzipped project folder (including the hidden `.github` and `.gitignore`), then **Commit changes**.
   Check that `.github/workflows/android-build.yml` exists in the repository.
3. If you use git instead: `git init`, `git add .`, `git commit -m "MBOX"`, `git branch -M main`,
   `git remote add origin <your repo URL>`, `git push -u origin main`.

## Run the build
Pushing to `main` starts it automatically. Manual run: **Actions** tab > **Android Build** > **Run workflow**.
(If Actions is disabled on a new fork, click the green button to enable it.)

## Download the APK
Actions tab > click the latest run > scroll to **Artifacts** at the bottom > **MBOX-debug-apk** (a zip).
Unzip it to get `app-debug.apk`. Artifacts are kept for 90 days by default.

## Install on Android
Copy `app-debug.apk` to the phone, open it, allow "Install unknown apps" for the app you opened it from when asked,
then install. Debug builds are signed with a debug key, which is fine for personal use.

## Player test checklist
- [ ] Play a demo MP4; seek bar, 10s buttons and double-tap left/right seek work
- [ ] Pause/play; controls hide after ~3.5s and reappear on tap
- [ ] Speeds 0.5x to 2x change playback
- [ ] Left-half vertical swipe changes brightness; right-half changes volume; horizontal swipe seeks
- [ ] Fit / Fill / Zoom / Stretch change the picture
- [ ] Rotate button switches portrait/landscape without restarting playback
- [ ] Load an .srt/.vtt via the CC button; switch audio tracks if the stream has several
- [ ] Close and reopen the title: playback resumes; title appears in Continue Watching
- [ ] Turn on airplane mode mid-stream: error message and Retry button appear
- [ ] HLS and DASH demo titles play; an unsupported file shows a friendly message
- [ ] Add a local video from the device and play it

## Implemented vs. not
Implemented (written, untested): all screens listed in the spec, search/genre filter/sort, favorites, watchlist,
history and Continue Watching, series seasons/episodes, three metadata providers, source picker, custom URL and
local-file playback, custom Media3 player with the features above, settings (theme, speed, subtitle language,
clear history/cache, licenses), magnet validator, unit tests for the magnet parser and catalogue JSON, CI workflow.

Not implemented / needs further work: torrent playback; DRM; downloads/offline; automatic next episode;
episode-level metadata from TMDB; picture-in-picture; Chromecast; release signing; verified public-domain
URLs (replace the demo entries with sources you have checked); instrumented UI tests.
