/*
 * Copyright (C) 2019 The Android Open Source Project
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
import android.util.Log

class SaFile private constructor(private val context: Context, private val uri: Uri) {

    val documentId:String
    val mimeType:String
    val name:String
    val summary:String
    val lastModified:Long
    val length:Long

    private val flags:Long

    init {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                for (col in 0 until cursor.columnCount) {
                    Log.d("nicole", "file: ${cursor.columnNames[col]}=${cursor.getString(col)}")
                }
            }

            documentId = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
        }
    }

    companion object {
        fun fromUri(context: Context, treeUri: Uri) {
            if (isTreeUri(treeUri)) {
                val documentId = DocumentsContract.getTreeDocumentId(treeUri)
                val uri = DocumentsContract.buildDocumentUriUsingTree(
                    treeUri,
                    documentId
                )
                val file = SaFile(context, uri)
            }
        }

        private fun isTreeUri(documentUri: Uri) =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                DocumentsContract.isTreeUri(documentUri)
            } else {
                val paths = documentUri.pathSegments
                paths.size >= 2 && paths[0] == "tree"
            }
    }
}