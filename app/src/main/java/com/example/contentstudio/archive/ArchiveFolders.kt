package com.example.contentstudio.archive

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract as Documents
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.util.UUID

/** SAF names are individual, already validated segments, never filesystem paths. */
internal class ArchiveFolders(private val resolver: ContentResolver, private val tree: Uri, source: Uri) {
    private val known = mutableSetOf<String>()
    private val original = source
    private var root: Uri? = null
    private var unconfirmed: Uri? = null
    private val directories = mutableMapOf<String, Uri>()

    private fun id(uri: Uri) = Documents.getDocumentId(uri)
    private fun children(parent: Uri): Set<String> {
        val query = Documents.buildChildDocumentsUriUsingTree(tree, id(parent))
        return resolver.query(query, arrayOf(Documents.Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { cursor ->
            buildSet {
                while (cursor.moveToNext()) {
                    if (size >= ZipArchive.MAX_ENTRIES + 1) throw ArchiveRejected(ArchiveIssue.LIMIT)
                    add(cursor.getString(0))
                }
            }
        } ?: throw ArchiveRejected(ArchiveIssue.ACCESS)
    }

    private fun verify(uri: Uri, name: String, mime: String) {
        resolver.query(uri, arrayOf(Documents.Document.COLUMN_DISPLAY_NAME, Documents.Document.COLUMN_MIME_TYPE), null, null, null)?.use {
            check(it.moveToFirst() && it.getString(0) == name &&
                if (mime == Documents.Document.MIME_TYPE_DIR) it.getString(1) == mime
                else it.getString(1) != null && it.getString(1) != Documents.Document.MIME_TYPE_DIR)
        } ?: throw ArchiveRejected(ArchiveIssue.ACCESS)
    }

    private suspend fun create(parent: Uri, name: String, mime: String, top: Boolean = false): Uri {
        val before = children(parent)
        known.addAll(before)
        // Register ownership without a cancellation gap after the provider has created the document.
        return withContext(NonCancellable) {
            val created = Documents.createDocument(resolver, parent, mime, name) ?: throw ArchiveRejected(ArchiveIssue.ACCESS)
            val key = id(created)
            check(created.authority == tree.authority && key != id(parent) && key !in before && key !in known &&
                !(created.authority == original.authority && runCatching { id(original) == key }.getOrDefault(false)))
            if (top) unconfirmed = created
            // A provider returning an existing/renamed document is never opened for writing.
            check(key in children(parent))
            if (top) { root = created; unconfirmed = null }
            known.add(key)
            verify(created, name, mime)
            if (mime == Documents.Document.MIME_TYPE_DIR) check(children(created).isEmpty())
            created
        }
    }

    suspend fun start(): Uri {
        check(Documents.isTreeUri(tree))
        val parent = Documents.buildDocumentUriUsingTree(tree, Documents.getTreeDocumentId(tree))
        known.add(id(parent))
        return create(parent, "Birlevo-${UUID.randomUUID()}", Documents.Document.MIME_TYPE_DIR, top = true).also {
            check(children(it).isEmpty())
            directories[""] = it
        }
    }

    suspend fun file(path: String): Uri {
        check(ZipArchive.safePath(path) == path && !path.endsWith('/'))
        val segments = path.split('/')
        var prefix = ""
        var parent = checkNotNull(root)
        for (name in segments.dropLast(1)) {
            prefix = if (prefix.isEmpty()) name else "$prefix/$name"
            parent = directories[prefix] ?: create(parent, name, Documents.Document.MIME_TYPE_DIR).also { directories[prefix] = it }
        }
        return create(parent, segments.last(), "application/octet-stream")
    }

    /** Only our fresh root is removed. Never delete the chosen folder or a returned preexisting alias. */
    suspend fun cleanup(): Uri? = withContext(NonCancellable) {
        root?.takeUnless { runCatching { Documents.deleteDocument(resolver, it) }.getOrDefault(false) } ?: if (root == null) unconfirmed else null
    }
}
