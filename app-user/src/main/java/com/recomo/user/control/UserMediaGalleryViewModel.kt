package com.recomo.user.control

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.recomo.user.data.media.UserMediaItem
import com.recomo.user.data.media.UserMediaManager
import com.recomo.user.data.media.UserMediaRepository
import com.recomo.user.data.media.UserMediaType
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class UserMediaGalleryFilter {
    All,
    Videos,
    Images,
    Downloaded
}

enum class UserMediaGallerySort {
    Date,
    Size,
    Name
}

enum class UserMediaGalleryViewMode {
    Grid,
    List
}

data class UserMediaGalleryUiState(
    val allItems: List<UserMediaItem> = emptyList(),
    val items: List<UserMediaItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val searchQuery: String = "",
    val filter: UserMediaGalleryFilter = UserMediaGalleryFilter.All,
    val sort: UserMediaGallerySort = UserMediaGallerySort.Date,
    val viewMode: UserMediaGalleryViewMode = UserMediaGalleryViewMode.Grid,
    val downloadedIds: Set<String> = emptySet(),
    val downloadProgress: Map<String, Float> = emptyMap(),
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L
)

@HiltViewModel
class UserMediaGalleryViewModel @Inject constructor(
    private val mediaRepository: UserMediaRepository,
    private val mediaManager: UserMediaManager
) : ViewModel() {
    val copyStyleDirectory: File get() = mediaManager.directoryFor(UserMediaManager.Category.CopyStyle)
    private val searchQuery = MutableStateFlow("")
    private val filter = MutableStateFlow(UserMediaGalleryFilter.All)
    private val sort = MutableStateFlow(UserMediaGallerySort.Date)
    private val viewMode = MutableStateFlow(UserMediaGalleryViewMode.Grid)

    private val queryState = combine(searchQuery, filter, sort, viewMode) { query, activeFilter, activeSort, activeViewMode ->
        GalleryQueryState(query, activeFilter, activeSort, activeViewMode)
    }

    val uiState: StateFlow<UserMediaGalleryUiState> = combine(
        mediaRepository.mediaItems,
        mediaRepository.isLoading,
        mediaRepository.error,
        mediaRepository.downloadProgress,
        queryState
    ) { items, isLoading, error, downloadProgress, queryState ->
        val filtered = items
            .filter { item ->
                when (queryState.filter) {
                    UserMediaGalleryFilter.All -> true
                    UserMediaGalleryFilter.Videos -> item.type == UserMediaType.VIDEO
                    UserMediaGalleryFilter.Images -> item.type == UserMediaType.IMAGE
                    UserMediaGalleryFilter.Downloaded -> mediaRepository.isDownloaded(item)
                }
            }
            .filter { item ->
                val queryText = queryState.query.trim().lowercase()
                if (queryText.isBlank()) true else item.filename.lowercase().contains(queryText)
            }
            .sortedWith(
                when (queryState.sort) {
                    UserMediaGallerySort.Date -> compareByDescending<UserMediaItem> { it.timestamp }
                    UserMediaGallerySort.Size -> compareByDescending<UserMediaItem> { it.size }
                    UserMediaGallerySort.Name -> compareBy<UserMediaItem> { it.filename.lowercase() }
                }
            )

        val downloadedIds = items.filter(mediaRepository::isDownloaded).mapTo(linkedSetOf()) { it.id }
        UserMediaGalleryUiState(
            allItems = items,
            items = filtered,
            isLoading = isLoading,
            errorMessage = error,
            searchQuery = queryState.query,
            filter = queryState.filter,
            sort = queryState.sort,
            viewMode = queryState.viewMode,
            downloadedIds = downloadedIds,
            downloadProgress = downloadProgress,
            downloadedBytes = items.sumOf(mediaRepository::localSize),
            totalBytes = items.sumOf { it.size }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserMediaGalleryUiState()
    )

    init {
        refresh()
    }

    fun onSearchQueryChange(value: String) {
        searchQuery.value = value
    }

    fun onFilterChange(value: UserMediaGalleryFilter) {
        filter.value = value
    }

    fun onSortChange(value: UserMediaGallerySort) {
        sort.value = value
    }

    fun onViewModeChange(value: UserMediaGalleryViewMode) {
        viewMode.value = value
    }

    fun refresh() {
        viewModelScope.launch {
            mediaRepository.refresh().onFailure {
                if (mediaRepository.mediaItems.value.isEmpty()) {
                    mediaRepository.fetchMediaList()
                }
            }
        }
    }

    fun downloadMedia(item: UserMediaItem) {
        if (mediaRepository.isDownloaded(item)) return
        viewModelScope.launch {
            mediaRepository.downloadMedia(item).collect { progress ->
                mediaRepository.setDownloadProgress(item.id, progress)
            }
        }
    }

    fun deleteLocalMedia(item: UserMediaItem) {
        viewModelScope.launch {
            mediaRepository.deleteLocalMedia(item)
        }
    }

    fun getLocalFile(item: UserMediaItem): File? = mediaRepository.getLocalFile(item)
}

private data class GalleryQueryState(
    val query: String,
    val filter: UserMediaGalleryFilter,
    val sort: UserMediaGallerySort,
    val viewMode: UserMediaGalleryViewMode
)
