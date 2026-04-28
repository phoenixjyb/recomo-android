package com.recomo.user.ui.screens.library

import androidx.annotation.DrawableRes

enum class MotionLibraryViewMode {
    Grid,
    List
}

enum class MotionLibrarySidebarIcon {
    All,
    Favorite,
    Product,
    Lifestyle,
    Interview,
    Cinematic,
    Tour,
    Layers,
    Storage,
    Download,
    StudioDance
}

data class MotionLibrarySidebarCategoryUiState(
    val id: String,
    val label: String,
    val count: Int,
    val icon: MotionLibrarySidebarIcon = MotionLibrarySidebarIcon.All,
    val isSelected: Boolean = false
)

data class MotionLibrarySidebarActionUiState(
    val id: String,
    val label: String,
    val supportingLabel: String = "",
    val icon: MotionLibrarySidebarIcon = MotionLibrarySidebarIcon.Layers
)

data class MotionLibraryEntryUiState(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val typeLabel: String = "",
    val groupLabel: String = "",
    val categoryLabel: String = "",
    val primaryMeta: String = "",
    val secondaryMeta: String = "",
    val tertiaryMeta: String = "",
    val timestampLabel: String = "",
    val isFavorite: Boolean = false,
    val isSelected: Boolean = false,
    val thumbnailIcon: MotionLibrarySidebarIcon = MotionLibrarySidebarIcon.Product,
    @DrawableRes val thumbnailRes: Int? = null,
    val musicFileName: String? = null,
    val bpm: Int? = null
)

data class MotionLibraryWorkspaceUiState(
    val title: String = "Motion Library",
    val subtitle: String = "Review, filter, and launch runnable motions",
    val createLabel: String = "New Motion",
    val searchQuery: String = "",
    val searchPlaceholder: String = "Search motions",
    val resultCountLabel: String = "0 motions",
    val viewMode: MotionLibraryViewMode = MotionLibraryViewMode.Grid,
    val categoriesTitle: String = "Categories",
    val categories: List<MotionLibrarySidebarCategoryUiState> = emptyList(),
    val sidebarActions: List<MotionLibrarySidebarActionUiState> = emptyList(),
    val storageLabel: String = "Storage",
    val storageUsageLabel: String = "",
    val storageFraction: Float = 0f,
    val entries: List<MotionLibraryEntryUiState> = emptyList(),
    val emptyTitle: String = "No motions found",
    val emptyBody: String = "Try another search or switch categories"
) {
    val hasContent: Boolean get() = entries.isNotEmpty()
}

fun LibrarySummaryUiState.toMotionLibraryWorkspaceState(
    searchQuery: String = "",
    selectedCategoryId: String = "all",
    viewMode: MotionLibraryViewMode = MotionLibraryViewMode.Grid
): MotionLibraryWorkspaceUiState {
    val allEntries = buildList {
        addAll(
            foiSessions.map { session ->
                session.toMotionLibraryEntryUiState()
            }
        )
        addAll(
            poiSessions.map { session ->
                session.toMotionLibraryEntryUiState()
            }
        )
    }

    val normalizedQuery = searchQuery.trim()
    val filteredEntries = allEntries.filter { entry ->
        val matchesQuery = normalizedQuery.isBlank() ||
            entry.title.contains(normalizedQuery, ignoreCase = true) ||
            entry.subtitle.contains(normalizedQuery, ignoreCase = true) ||
            entry.categoryLabel.contains(normalizedQuery, ignoreCase = true) ||
            entry.typeLabel.contains(normalizedQuery, ignoreCase = true) ||
            entry.groupLabel.contains(normalizedQuery, ignoreCase = true)
        val matchesCategory = when (selectedCategoryId) {
            "all" -> true
            "favorites" -> entry.isFavorite
            else -> entry.groupLabel.equals(selectedCategoryId, ignoreCase = true) ||
                entry.typeLabel.equals(selectedCategoryId, ignoreCase = true) ||
                entry.categoryLabel.equals(selectedCategoryId, ignoreCase = true)
        }
        matchesQuery && matchesCategory
    }

    val shotPlansLabel = LibrarySessionType.FOI.displayGroupLabel()
    val savedMarksLabel = LibrarySessionType.POI.displayGroupLabel()

    val dynamicCategories = listOfNotNull(
        MotionLibrarySidebarCategoryUiState(
            id = "all",
            label = "All",
            count = allEntries.size,
            icon = MotionLibrarySidebarIcon.All,
            isSelected = selectedCategoryId == "all"
        ),
        MotionLibrarySidebarCategoryUiState(
            id = shotPlansLabel,
            label = shotPlansLabel,
            count = allEntries.count { it.groupLabel == shotPlansLabel },
            icon = MotionLibrarySidebarIcon.Product,
            isSelected = selectedCategoryId == shotPlansLabel
        ),
        MotionLibrarySidebarCategoryUiState(
            id = savedMarksLabel,
            label = savedMarksLabel,
            count = allEntries.count { it.groupLabel == savedMarksLabel },
            icon = MotionLibrarySidebarIcon.Interview,
            isSelected = selectedCategoryId == savedMarksLabel
        )
    ) + allEntries
        .map { it.categoryLabel }
        .filter {
            it.isNotBlank() &&
                !it.equals(shotPlansLabel, ignoreCase = true) &&
                !it.equals(savedMarksLabel, ignoreCase = true)
        }
        .distinct()
        .sorted()
        .map { category ->
            MotionLibrarySidebarCategoryUiState(
                id = category,
                label = category,
                count = allEntries.count { it.categoryLabel == category },
                icon = category.toSidebarIcon(),
                isSelected = selectedCategoryId == category
            )
        }

    val usageMb = allEntries.size * 0.85f
    return MotionLibraryWorkspaceUiState(
        subtitle = statusLabel,
        searchQuery = searchQuery,
        resultCountLabel = "${filteredEntries.size} motions",
        viewMode = viewMode,
        categories = dynamicCategories,
        sidebarActions = listOf(
            MotionLibrarySidebarActionUiState(
                id = "import",
                label = "Import Sessions",
                supportingLabel = "Gateway and disclosed JSON",
                icon = MotionLibrarySidebarIcon.Download
            ),
            MotionLibrarySidebarActionUiState(
                id = "storage",
                label = "Storage Review",
                supportingLabel = "Check local session usage",
                icon = MotionLibrarySidebarIcon.Storage
            )
        ),
        storageUsageLabel = String.format("%.1fMB / 500MB", usageMb),
        storageFraction = (usageMb / 500f).coerceIn(0f, 1f),
        entries = filteredEntries,
        emptyBody = emptyMessage
    )
}

private fun LibrarySessionSummaryUiItem.toMotionLibraryEntryUiState(
): MotionLibraryEntryUiState {
    val normalizedType = type.displayLabel()
    val groupLabel = type.displayGroupLabel()
    val derivedCategory = category.takeIf {
        it.isNotBlank() &&
            !it.equals(normalizedType, ignoreCase = true) &&
            !it.equals(groupLabel, ignoreCase = true)
    } ?: groupLabel
    val tertiaryLabel = robotName.takeIf { it.isNotBlank() }
        ?: frameId.takeIf { it.isNotBlank() }
        ?: "Ready to stage"
    val isStudioDance = sessionType?.equals("studio_dance", ignoreCase = true) == true
    val primaryMetaLabel = when {
        isStudioDance && bpm != null -> "${bpm} BPM · ${type.countLabel(count)}"
        count > 0 -> type.countLabel(count)
        else -> "Ready to stage"
    }
    return MotionLibraryEntryUiState(
        id = sessionId,
        title = displayMotionTitle(),
        subtitle = displayMotionSubtitle(),
        typeLabel = normalizedType,
        groupLabel = groupLabel,
        categoryLabel = derivedCategory,
        primaryMeta = primaryMetaLabel,
        secondaryMeta = category.takeIf {
            it.isNotBlank() &&
                !it.equals(normalizedType, ignoreCase = true) &&
                !it.equals(groupLabel, ignoreCase = true)
        } ?: robotName.ifBlank { "Studio motion" },
        tertiaryMeta = tertiaryLabel,
        timestampLabel = "",
        isFavorite = isSelected,
        isSelected = isSelected,
        thumbnailIcon = if (isStudioDance) MotionLibrarySidebarIcon.StudioDance else derivedCategory.toSidebarIcon(),
        musicFileName = musicFile,
        bpm = bpm
    )
}

private fun String.toSidebarIcon(): MotionLibrarySidebarIcon =
    when (lowercase()) {
        "favorite", "favorites" -> MotionLibrarySidebarIcon.Favorite
        "product" -> MotionLibrarySidebarIcon.Product
        "lifestyle" -> MotionLibrarySidebarIcon.Lifestyle
        "interview" -> MotionLibrarySidebarIcon.Interview
        "cinematic" -> MotionLibrarySidebarIcon.Cinematic
        "tour" -> MotionLibrarySidebarIcon.Tour
        "shot plans", "saved marks" -> MotionLibrarySidebarIcon.Layers
        "studio dance" -> MotionLibrarySidebarIcon.StudioDance
        else -> MotionLibrarySidebarIcon.Layers
    }
