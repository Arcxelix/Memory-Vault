package com.memoryvault.app.data

const val KIND_MEDIA = 0
const val KIND_TAG = 1

/** The story's cover is stored as an item too, so it has its own name, description and notes. */
const val KIND_COVER = 2

data class Story(
    val id: Long,
    val name: String,
    val coverPath: String?,
    val coverThumb: String?,
    val coverVideo: Boolean,
    val loopVideo: Boolean,
    val position: Int
)

/** A photo/video inside a story (kind = KIND_MEDIA) or a tag cut out of a photo (kind = KIND_TAG). */
data class Item(
    val id: Long,
    val storyId: Long,
    val kind: Int,
    val path: String,
    val thumb: String?,
    val video: Boolean,
    val name: String?,
    val description: String?,
    val notes: String?,
    val position: Int
)
