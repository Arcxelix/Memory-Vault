# Memory Vault

Native Android photo-journaling app. Data layer in Java (`DbHelper.java`), everything else in Kotlin.

Open the `MemoryVault` folder in Android Studio (Koala or newer), let Gradle sync, then Run.
Min SDK 26, target SDK 34. Portrait only (remove `screenOrientation` in the manifest to allow rotation).

- Albums: drag to reorder (long-press), 3x3 / 4x4 toggle, + to create a story
- Story: cover (photo / autoplaying video, loop setting in the cover menu), memories row, tags row
- Drawer: tap any memory or tag; pen edits name, description and notes; mic button dictates
- Tags: + in the Tags row -> pick a photo from the story -> crop -> name
- Share: select photos/videos in Gallery, WhatsApp etc. -> Share -> Memory Vault
