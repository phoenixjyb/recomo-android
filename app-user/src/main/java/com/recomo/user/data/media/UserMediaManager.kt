package com.recomo.user.data.media

import android.content.Context
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unified on-device media storage manager.
 *
 * Directory layout under `getExternalFilesDir(null)/Recomo/`:
 *   Recordings/   — tablet-side video recordings
 *   CopyStyle/    — preset preview videos (pushed via adb)
 *   Library/      — downloaded trajectory sessions / motion data
 *   Downloads/    — media synced from Orin
 */
@Singleton
class UserMediaManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    enum class Category(val dirName: String) {
        Recordings("Recordings"),
        CopyStyle("CopyStyle"),
        Library("Library"),
        Downloads("Downloads"),
        StudioDanceMusic("StudioDanceMusic")
    }

    private val root: File by lazy {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        File(base, "Recomo").apply { mkdirs() }
    }

    fun rootDirectory(): File = root

    fun directoryFor(category: Category): File =
        File(root, category.dirName).apply { mkdirs() }

    fun fileFor(category: Category, fileName: String): File =
        File(directoryFor(category), fileName)

    fun fileExists(category: Category, fileName: String): Boolean =
        fileFor(category, fileName).let { it.exists() && it.length() > 0L }

    suspend fun listFiles(
        category: Category,
        extensions: Set<String>? = null
    ): List<File> = withContext(Dispatchers.IO) {
        val dir = directoryFor(category)
        val files = dir.listFiles() ?: emptyArray()
        files.filter { f ->
            f.isFile && (extensions == null || extensions.any { ext ->
                f.name.endsWith(".$ext", ignoreCase = true)
            })
        }.sortedByDescending { it.lastModified() }
    }

    suspend fun storageUsage(): Map<Category, Long> = withContext(Dispatchers.IO) {
        Category.entries.associateWith { cat ->
            val dir = File(root, cat.dirName)
            if (dir.exists()) dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            else 0L
        }
    }

    suspend fun deleteFile(file: File): Boolean = withContext(Dispatchers.IO) {
        file.exists() && file.delete()
    }

    suspend fun cleanCategory(category: Category) = withContext(Dispatchers.IO) {
        val dir = directoryFor(category)
        dir.listFiles()?.forEach { it.delete() }
    }

    /**
     * One-time migration from old scattered directories into the unified layout.
     * Safe to call multiple times — skips if old dirs don't exist.
     */
    suspend fun migrateOldDirectories() = withContext(Dispatchers.IO) {
        val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: return@withContext

        // Old recordings: Movies/recomoVideosRawStream → Recomo/Recordings
        moveContents(File(moviesDir, "recomoVideosRawStream"), directoryFor(Category.Recordings))

        // Old downloads: Movies/recomoStudioSync → Recomo/Downloads
        moveContents(File(moviesDir, "recomoStudioSync"), directoryFor(Category.Downloads))

        // Old CopyStyle location: files/CopyStyle → Recomo/CopyStyle
        val oldCopyStyle = context.getExternalFilesDir(null)?.let { File(it, "CopyStyle") }
        if (oldCopyStyle != null) {
            moveContents(oldCopyStyle, directoryFor(Category.CopyStyle))
        }
    }

    private fun moveContents(src: File, dest: File) {
        if (!src.exists() || !src.isDirectory) return
        dest.mkdirs()
        src.listFiles()?.forEach { file ->
            val target = File(dest, file.name)
            if (!target.exists()) {
                file.renameTo(target)
            }
        }
        // Remove old dir if empty
        if (src.listFiles().isNullOrEmpty()) src.delete()
    }
}
