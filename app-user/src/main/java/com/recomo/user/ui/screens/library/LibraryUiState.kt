package com.recomo.user.ui.screens.library

import com.recomo.user.control.UserLibraryTarget

enum class LibrarySessionType {
    FOI,
    POI
}

fun LibrarySessionType.displayLabel(): String =
    when (this) {
        LibrarySessionType.FOI -> "Shot Plan"
        LibrarySessionType.POI -> "Saved Mark"
    }

fun LibrarySessionType.displayGroupLabel(): String =
    when (this) {
        LibrarySessionType.FOI -> "Shot Plans"
        LibrarySessionType.POI -> "Saved Marks"
    }

fun LibrarySessionType.defaultTitle(): String =
    when (this) {
        LibrarySessionType.FOI -> "Untitled shot plan"
        LibrarySessionType.POI -> "Untitled saved mark"
    }

fun LibrarySessionType.countLabel(count: Int): String =
    when (this) {
        LibrarySessionType.FOI -> "$count keyframes"
        LibrarySessionType.POI -> "$count marks"
    }

data class LibrarySessionSummaryUiItem(
    val type: LibrarySessionType,
    val sessionId: String,
    val sessionName: String,
    val robotName: String = "",
    val category: String = "",
    val frameId: String = "",
    val count: Int = 0,
    val isSelected: Boolean = false,
    val sceneType: String = "",
    val linkedMap: String = "",
    val libraryTarget: UserLibraryTarget? = null,
    val sessionType: String? = null,
    val musicFile: String? = null,
    val bpm: Int? = null
)

data class LibrarySummaryUiState(
    val foiSessions: List<LibrarySessionSummaryUiItem> = emptyList(),
    val poiSessions: List<LibrarySessionSummaryUiItem> = emptyList(),
    val statusLabel: String = "Ready",
    val isLoading: Boolean = false,
    val emptyMessage: String = "No sessions yet"
) {
    val hasContent: Boolean get() = foiSessions.isNotEmpty() || poiSessions.isNotEmpty()
    val totalCount: Int get() = foiSessions.size + poiSessions.size
}

fun LibrarySessionSummaryUiItem.displayMotionTitle(): String =
    sessionName.trim().ifBlank { type.defaultTitle() }

fun LibrarySessionSummaryUiItem.displayMotionSubtitle(): String =
    listOfNotNull(
        robotName.trim().takeIf { it.isNotBlank() },
        category.trim().takeIf {
            it.isNotBlank() &&
                !it.equals(type.displayLabel(), ignoreCase = true) &&
                !it.equals(type.displayGroupLabel(), ignoreCase = true)
        }
    ).joinToString(" · ").ifBlank { "Studio motion" }

fun LibrarySessionSummaryUiItem.displayMotionMeta(): String =
    listOfNotNull(
        type.displayLabel(),
        count.takeIf { it > 0 }?.let(type::countLabel),
        category.trim().takeIf {
            it.isNotBlank() &&
                !it.equals(type.displayLabel(), ignoreCase = true) &&
                !it.equals(type.displayGroupLabel(), ignoreCase = true)
        }
    ).joinToString(" · ").ifBlank { type.displayLabel() }
