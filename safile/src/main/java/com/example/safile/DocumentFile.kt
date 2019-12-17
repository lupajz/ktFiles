/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.safile

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document.*
import android.provider.DocumentsContract.EXTRA_LOADING
import android.text.TextUtils
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class DocumentFile {

    companion object {
        suspend fun fromUri(
            context: Context,
            documentUri: Uri,
            coroutineContext: CoroutineContext = Dispatchers.IO
        ) = suspendCancellableCoroutine<DocumentFile?> { continuation ->
            val job = GlobalScope.launch(coroutineContext) {
                try {
                    // Do the query
                    if (isTreeUri(documentUri)) {
                        continuation.resume(buildTreeSaFile(context, documentUri))
                    } else {
                        continuation.resume(null)
                    }
                } catch (exception: Throwable) {
                    continuation.resumeWithException(exception)
                }
            }
            continuation.invokeOnCancellation {
                // Cancel the query
                job.cancel()
            }
        }

        private fun isTreeUri(documentUri: Uri): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                return DocumentsContract.isTreeUri(documentUri)
            }
            val paths = documentUri.pathSegments
            return paths.size >= 2 && paths[0] == "tree"
        }

        private fun buildTreeSaFile(context: Context, treeUri: Uri): DocumentFile {
            val documentId = DocumentsContract.getTreeDocumentId(treeUri)
            val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
            return DocumentFile(context, uri)
        }
    }

    private val context: Context
    private val properties: Properties

    val uri: Uri get() = properties.uri
    val documentId get() = properties.id
    val displayName get() = properties.displayName
    val type get() = properties.type
    val lastModified get() = properties.lastModified
    val flags get() = properties.flags
    val size get() = properties.size
    private var children: Array<DocumentFile>? = null

    private constructor(
        context: Context,
        properties: Properties
    ) {
        this.context = context
        this.properties = properties
    }

    private constructor(context: Context, documentUri: Uri) {
        this.context = context.applicationContext
        val cr = context.contentResolver

        properties = cr.query(documentUri, null, null, null, null).use { cursor ->
            require(!(cursor == null || !cursor.moveToFirst())) {
                "Cannot create document from Uri: $documentUri"
            }
            var data = ""
            for (i in 0 until cursor.columnCount) {
                data += cursor.getColumnName(i) + ", "
            }
            Log.d("nicole", "Parent Columns: $data")
            Properties(
                uri = documentUri,
                id = cursor.getString(cursor.getColumnIndex(COLUMN_DOCUMENT_ID)),
                type = cursor.getString(cursor.getColumnIndex(COLUMN_MIME_TYPE)),
                displayName = cursor.getString(cursor.getColumnIndex(COLUMN_DISPLAY_NAME)),
                lastModified = cursor.getLong(cursor.getColumnIndex(COLUMN_LAST_MODIFIED)),
                flags = cursor.getLong(cursor.getColumnIndex(COLUMN_FLAGS)),
                size = cursor.getLong(cursor.getColumnIndex(COLUMN_SIZE))
            )
        }
        children = buildChildDocuments(documentUri)
    }

    fun isFile(context: Context?, self: Uri?): Boolean {
        return !isDirectory && !TextUtils.isEmpty(type)
    }

    val isDirectory get() = type == MIME_TYPE_DIR

    fun renameTo(displayName: String?): Boolean {
        return false
    }

    fun listFiles(): Array<DocumentFile> = children ?: emptyArray()

    private fun buildChildDocuments(parentUri: Uri): Array<DocumentFile> {
        val childrenUri =
            DocumentsContract.buildChildDocumentsUriUsingTree(
                parentUri,
                DocumentsContract.getDocumentId(this.uri)
            )

        val cr = context.contentResolver
        return cr.query(childrenUri, null, null, null, null).use { cursor ->
            if (cursor == null || !cursor.moveToFirst()) {
                return emptyArray()
            }
            cursor.extras?.getString(EXTRA_LOADING)?.let { loading ->
                Log.d("nicole", "Loading: $loading")
            }

            val idCol = cursor.getColumnIndex(COLUMN_DOCUMENT_ID)
            val typeCol = cursor.getColumnIndex(COLUMN_MIME_TYPE)
            val displayNameCol = cursor.getColumnIndex(COLUMN_DISPLAY_NAME)
            val lastModifiedCol = cursor.getColumnIndex(COLUMN_LAST_MODIFIED)
            val flagsCol = cursor.getColumnIndex(COLUMN_FLAGS)
            val sizeCol = cursor.getColumnIndex(COLUMN_SIZE)

            val childDocs = mutableListOf<DocumentFile>()
            while (cursor.moveToNext()) {
                val documentUri = DocumentsContract.buildDocumentUriUsingTree(
                    uri,
                    documentId
                )

                val properties = Properties(
                    uri = documentUri,
                    id = cursor.getString(idCol),
                    type = cursor.getString(typeCol),
                    displayName = cursor.getString(displayNameCol),
                    lastModified = cursor.getLong(lastModifiedCol),
                    flags = cursor.getLong(flagsCol),
                    size = cursor.getLong(sizeCol)
                )
                childDocs += DocumentFile(context, properties)
            }
            childDocs.toTypedArray()
        }
    }

    private class Properties(
        val uri: Uri,
        val id: String,
        val displayName: String?,
        val type: String?,
        val lastModified: Long,
        val flags: Long,
        val size: Long
    )
}