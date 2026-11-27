package com.downly.app

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import java.io.IOException

/**
 * The folder picked in Settings ("Download folder"), stored as the "tree" URI that Android's folder picker
 * returns. Blank = the normal Downloads folder. No storage permission is needed for a picked folder.
 *
 * Access to earlier picked folders is kept on purpose: files saved there must stay openable and deletable.
 */
object SaveLocation {

    const val DEFAULT_LABEL = "Downloads"
    private const val OTHER_LABEL = "Chosen folder"
    private const val INTERNAL = "Internal storage"
    private const val STORAGE_AUTHORITY = "com.android.externalstorage.documents"
    private const val DOWNLOADS_AUTHORITY = "com.android.providers.downloads.documents"

    /** Remembers the permission for the picked folder, also after a restart. @return false if Android refused. */
    fun keepAccess(context: Context, tree: Uri): Boolean = runCatching {
        context.contentResolver.takePersistableUriPermission(
            tree,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
    }.isSuccess

    /** Creates an empty file in the picked folder. Android adds " (1)" when the name is taken. */
    fun createFile(resolver: ContentResolver, folder: String, name: String, mime: String): Uri {
        val tree = Uri.parse(folder)
        val directory = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        return DocumentsContract.createDocument(resolver, directory, mime, name)
            ?: throw IOException("Could not create the file in the chosen folder")
    }

    /** false = SD card, USB drive or another app's storage: saving there uses no space on the phone itself. */
    fun isOnPhoneStorage(folder: String): Boolean {
        if (folder.isBlank()) return true
        return runCatching {
            val tree = Uri.parse(folder)
            when (tree.authority) {
                DOWNLOADS_AUTHORITY -> true
                STORAGE_AUTHORITY ->
                    DocumentsContract.getTreeDocumentId(tree).substringBefore(':') in setOf("primary", "home")
                else -> false
            }
        }.getOrDefault(false)
    }

    /** Readable name of the folder chosen in Settings. */
    fun folderLabel(folder: String): String {
        if (folder.isBlank()) return DEFAULT_LABEL
        return runCatching {
            val tree = Uri.parse(folder)
            label(tree.authority, DocumentsContract.getTreeDocumentId(tree))
        }.getOrDefault(OTHER_LABEL)
    }

    /**
     * Full readable path for Settings: "Internal storage/Movies", "SD card/Movies/New".
     * Blank (default) = "Internal storage/Download".
     */
    fun folderPath(folder: String): String {
        if (folder.isBlank()) return "$INTERNAL/Download"
        return runCatching {
            val tree = Uri.parse(folder)
            if (tree.authority == DOWNLOADS_AUTHORITY) return@runCatching "$INTERNAL/Download"
            if (tree.authority != STORAGE_AUTHORITY) return@runCatching OTHER_LABEL
            val id = DocumentsContract.getTreeDocumentId(tree)
            if (':' !in id) return@runCatching OTHER_LABEL
            val root = id.substringBefore(':')
            val path = id.substringAfter(':').trim('/')
            val base = when (root) {
                "primary" -> INTERNAL
                "home" -> "$INTERNAL/Documents"
                else -> "SD card"
            }
            if (path.isEmpty()) base else "$base/$path"
        }.getOrDefault(OTHER_LABEL)
    }

    /** Readable name of the folder that holds a finished file. */
    fun savedFolderLabel(savedUri: String?): String {
        if (savedUri.isNullOrBlank()) return DEFAULT_LABEL
        return runCatching {
            val uri = Uri.parse(savedUri)
            // Only files in a picked folder have a "tree" URI. Everything else is in Downloads.
            if (uri.pathSegments.firstOrNull() != "tree") return@runCatching DEFAULT_LABEL
            val file = DocumentsContract.getDocumentId(uri)
            val parent = if ('/' in file) file.substringBeforeLast('/') else file.substringBefore(':') + ":"
            label(uri.authority, parent)
        }.getOrDefault(OTHER_LABEL)
    }

    /** "primary:Movies/New" -> "Movies/New", "1A2B-3C4D:Movies" -> "SD card / Movies" */
    private fun label(authority: String?, documentId: String): String {
        if (authority != STORAGE_AUTHORITY || ':' !in documentId) return OTHER_LABEL
        val root = documentId.substringBefore(':')
        val path = documentId.substringAfter(':').trim('/')
        val base = when (root) {
            "primary" -> if (path.isEmpty()) return "Phone storage" else return path
            "home" -> "Documents"
            else -> "SD card"
        }
        return if (path.isEmpty()) base else "$base / $path"
    }
}
