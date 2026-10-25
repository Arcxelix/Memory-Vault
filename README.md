<div align="center">

# Memory Vault

**A native Android photo journal for organising memories into stories.**
Dark, rounded, game-HUD look. Photos, videos, tags, notes, voice dictation, and a home-screen widget.

![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9-7F52FF?logo=kotlin&logoColor=white)
![Java](https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk&logoColor=white)
![Min SDK](https://img.shields.io/badge/minSdk-26-blue)
![Target SDK](https://img.shields.io/badge/targetSdk-34-blue)
![License](https://img.shields.io/badge/license-GPLv3-blue)


</div>

---

## Contents

- [Overview](#overview)
- [Features](#features)
- [Screenshots](#screenshots)
- [Getting started](#getting-started)
- [How it works](#how-it-works)
- [Project structure](#project-structure)
- [Tech stack](#tech-stack)
- [Permissions and privacy](#permissions-and-privacy)
- [Known limitations](#known-limitations)
- [Roadmap](#roadmap)
- [Contributing](#contributing)
- [License](#license)

## Overview

Memory Vault groups photos and videos into **stories**. Each story has a cover, a scrolling row of
memories, and a row of **tags** (cut-outs of people, places or objects taken from your pictures).
Every memory, tag and cover can carry a name, a description and journal notes, typed or dictated.

Everything is stored on the device. Nothing is uploaded and no account is needed.

## Features

### Albums
- Opens straight into the albums grid.
- Each tile shows the story's thumbnail and name, in the order you arranged.
- Long-press and drag to reorder; the order is saved.
- Toggle between a **3x3** and **4x4** grid (remembered between launches).
- Floating button to create a story.

### Stories
- Large cover that can be a photo or a video. Video covers **autoplay with sound**, looping or
  playing once depending on a per-story setting.
- Tap the cover to reveal video controls and a menu: rename the story, change the thumbnail,
  edit the thumbnail's own details, toggle looping, add media, delete the story.
- Sideways-scrolling **Memories** row (the cover is not repeated here), each with an optional name.
- Sideways-scrolling **Tags** row at the bottom.

### Detail drawer
- Tap any memory, tag or cover to open a bottom drawer with the picture or video, its name,
  description and journal notes.
- **Pen** icon edits the text. **Mic** icons dictate it with the phone's own speech recogniser.
- **Edit photo**: crop, rotate, flip, brightness, contrast, colour. Save over the original or as a copy.
- **Switch out**: replace the photo or video with another one; the name, description and notes stay exactly as they were.

### Choosing pictures, everywhere
Anywhere a picture is needed (new story, adding to a story, cover, tags, switching out) you can
pick from the **gallery**, **take a photo** or **record a video**. When adding several, the camera
keeps offering "Capture another?" until you tap Done.

### Covers
Changing a thumbnail never loses the old one: it drops into the story's memories row. The new
cover can come from the story's own memories or from outside (gallery or camera).

### Tags
Cut a tag out of:
- one of the story's own photos,
- any other photo from the gallery, or
- a freshly captured photo.

Several outside photos can be queued and cut one after another. Tags belong to one story and open
the same drawer as any memory.

### Share into the app
Select photos and videos in the Gallery, WhatsApp or any other app, tap **Share**, choose
**Memory Vault**, then add them to a new or an existing story.

### Home-screen widget (Beta)
Add **Story slideshow** from the widget picker. It cycles every story's cover, in album order, on loop:
a photo is shown for **20 s**, a video plays **once**. Tapping it opens the albums page with the
story that was showing highlighted.

> Android widgets cannot host a real video player, so a video cover is shown as a frame-per-second
> preview (up to 30 s). The actual video, with sound, plays in the app.

## Screenshots

> Add your own screenshots to `docs/screenshots/` and link them here.

| Albums | Story | Drawer | Editor | Widget |
|:------:|:-----:|:------:|:------:|:------:|
| _todo_ | _todo_ | _todo_ | _todo_ | _todo_ |

## Getting started

### Requirements
- Android Studio **Koala (2024.1)** or newer
- JDK 17 (bundled with Android Studio)
- An Android 8.0+ (API 26) device or emulator

### Build and run
```bash
git clone https://GitHub.com/Arcxelix/Memory-Vault
cd Memory-Vault
```
1. Open the folder in Android Studio and let Gradle sync.
2. Pick a device and press **Run**.

The Gradle wrapper script and jar are not committed. Android Studio handles this on its own. For
command-line builds, generate them once with a local Gradle install:
```bash
gradle wrapper --gradle-version 8.7
./gradlew assembleDebug
```
The APK is written to `app/build/outputs/apk/debug/`.

### Trying the features
| Feature | How |
|---|---|
| Share import | In Gallery, select several photos or videos, tap Share, choose Memory Vault |
| Widget | Long-press the home screen, Widgets, Memory Vault, Story slideshow |
| Dictation | Tap the mic in any text field (needs a speech recogniser such as Google voice typing) |
| Camera | Choose "Take a photo" or "Record a video" in any picker (needs a camera app) |

## How it works

### Data model
Two SQLite tables, accessed through a small Java helper:

| Table | Purpose |
|---|---|
| `stories` | name, order, loop setting, and a cached copy of the cover's path/thumbnail (used by the grid and widget) |
| `items` | every picture or video: `kind` = memory (0), tag (1) or cover (2), plus path, thumbnail, name, description, notes, order |

The cover is an ordinary item of kind *cover*. That is why it has its own text, why changing a
cover is just a kind swap, and why the old cover can slide into the memories row without losing anything.

### Files
Everything you add is **copied into app-private storage** (`files/media`). Shared content from
WhatsApp and similar apps often disappears or loses access; copying makes every memory permanent.
Video thumbnails are generated once and stored in `files/thumbs`.

### Editor
The editor previews a downsized image (max 1600 px) for speed. When saving, it reloads the photo at
up to 3072 px and replays the same rotate, flip, crop and colour steps, so the result stays sharp.
The original file is never modified by the editor itself; the caller decides whether to replace it
or keep a copy.

### Widget
A `RemoteViewsService` feeds an `AdapterViewFlipper` that advances every second. A photo cover is
repeated for 20 slots; a video cover becomes one cached frame per second of video. Because the
flipper runs inside the launcher, the slideshow keeps moving without the app running. The data is
refreshed whenever stories change.

## Project structure

```
MemoryVault/
├── build.gradle.kts, settings.gradle.kts, gradle.properties
└── app/src/main/
    ├── AndroidManifest.xml
    ├── java/com/memoryvault/app/
    │   ├── AlbumsActivity.kt        albums grid, drag reorder, 3x3 / 4x4, widget highlight
    │   ├── StoryActivity.kt         cover, memories, tags, cover/tag flows
    │   ├── EditorActivity.kt        crop + basic editing (tag mode and edit mode)
    │   ├── ImportActivity.kt        receives Share from other apps
    │   ├── data/
    │   │   ├── DbHelper.java        SQLite schema, migrations, queries (Java)
    │   │   ├── Models.kt            Story, Item, kinds
    │   │   └── Repository.kt        data + file handling, cover logic, widget refresh
    │   ├── ui/
    │   │   ├── ItemSheet.kt         detail drawer
    │   │   ├── MediaPicker.kt       gallery / camera chooser
    │   │   ├── VoiceInput.kt        speech-to-text helper
    │   │   ├── CropView.kt          crop frame, rotate, flip, live colour filter
    │   │   ├── BitmapOps.kt         shared image maths for preview and final save
    │   │   ├── Adapters.kt          albums, memories/tags, picker grids
    │   │   ├── Dialogs.kt           text prompt, busy dialog, helpers
    │   │   └── SquareCardView.kt
    │   └── widget/
    │       ├── StoryWidgetProvider.kt
    │       └── StoryWidgetService.kt
    └── res/                         layouts, drawables, themes, widget + FileProvider XML
```

## Tech stack

| Area | Choice |
|---|---|
| Languages | Kotlin (UI, logic) and Java (database layer) |
| UI | Android Views, ViewBinding, Material Components 1.12 (Material 3 dark theme, custom styles) |
| Lists and drag | RecyclerView, `ItemTouchHelper` |
| Images | Glide 4.16 |
| Video | Media3 ExoPlayer and PlayerView 1.3 |
| Async | Kotlin coroutines and `lifecycleScope` |
| Storage | SQLite (`SQLiteOpenHelper`) and app-private files |
| Build | Gradle 8.7, Android Gradle Plugin 8.5, Kotlin 1.9.24 |

## Permissions and privacy

The app declares **no runtime permissions**.

- Gallery access uses the system **Photo Picker**.
- Camera capture uses the **system camera app** through a `FileProvider`, so no `CAMERA` permission is needed.
- Speech-to-text uses the system `RecognizerIntent`.
- Shared files arrive through temporary read grants and are copied immediately.
- There is no network code and no analytics.

## Known limitations

- Portrait only. Remove `screenOrientation` in the manifest to allow rotation.
- The widget previews a video cover as frames; it cannot play video or audio.
- Saving an edit with "Save" replaces the original photo. Use "Save copy" to keep both.
- Editing is for photos only, not videos.
- Large photos are processed at up to 3072 px on the longest side when edited.
- Speech recognition and camera capture depend on apps installed on the phone.
- No backup or export yet. Uninstalling the app removes its data.

## Roadmap

- Export and backup of stories
- Drag to reorder memories inside a story
- Search across names, descriptions and notes
- Dates and map locations for memories
- Optional lock screen for private stories
- Landscape layouts and tablet support

## Contributing

Issues and pull requests are welcome.

1. Fork the repository and create a branch: `git checkout -b feature/short-description`
2. Keep changes focused and match the existing code style (Kotlin official style).
3. Test on a real device when a change touches the camera, widget or video playback.
4. Open a pull request describing what changed and why.

## License

Released under the GNU Public License v3.0 . See [LICENSE](LICENSE).
