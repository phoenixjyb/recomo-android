package com.recomo.user.data.media

import kotlinx.serialization.Serializable

@Serializable
data class UserMediaItem(
    val id: String,
    val filename: String,
    val type: UserMediaType,
    val timestamp: Long,
    val size: Long,
    val resolution: UserMediaResolution? = null,
    val duration: Int? = null,
    val codec: String? = null,
    val fps: Int? = null,
    val bitrate: Int? = null,
    val thumbnailUrl: String? = null,
    val downloadUrl: String
)

@Serializable
enum class UserMediaType {
    VIDEO,
    IMAGE
}

@Serializable
data class UserMediaListResponse(
    val items: List<UserMediaItem>,
    val total: Int,
    val page: Int,
    val pageSize: Int
)

@Serializable
data class UserMediaResolution(
    val width: Int,
    val height: Int
)

data class UserMediaFilter(
    val type: UserMediaType? = null,
    val sortBy: UserMediaSortField = UserMediaSortField.TIMESTAMP,
    val sortOrder: UserMediaSortOrder = UserMediaSortOrder.DESCENDING
)

enum class UserMediaSortField {
    TIMESTAMP,
    FILENAME,
    SIZE,
    DURATION
}

enum class UserMediaSortOrder {
    ASCENDING,
    DESCENDING
}
