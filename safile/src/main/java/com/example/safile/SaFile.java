package com.example.safile;

import static android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME;
import static android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID;
import static android.provider.DocumentsContract.Document.COLUMN_FLAGS;
import static android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED;
import static android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE;
import static android.provider.DocumentsContract.Document.COLUMN_SIZE;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Objects;

public class SaFile {

    private final Context mContext;
    private final Uri mUri;

    private final String mDocumentId;
    private final String mName;
    private final String mMimeType;
    private final long mLastModified;
    private final long mFlags;
    private final long mSize;

    private SaFile[] mChildren;

    private SaFile(@NonNull Context context,
            @NonNull Uri parentDocument,
            @NonNull String documentId,
            @Nullable String mimeType,
            @Nullable String displayName,
            long lastModified,
            long flags,
            long size
    ) {
        mContext = context;

        mDocumentId = documentId;
        mUri = DocumentsContract.buildDocumentUriUsingTree(parentDocument,
                documentId);
        mName = displayName;
        mMimeType = mimeType;
        mLastModified = lastModified;
        mFlags = flags;
        mSize = size;
    }

    private SaFile(@NonNull Context context, @NonNull Uri uri) {
        mContext = context.getApplicationContext();
        mUri = uri;

        final ContentResolver cr = context.getContentResolver();
        try (Cursor cursor = cr.query(uri, null, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) {
                throw new IllegalArgumentException("Cannot create document from Uri: " + uri);
            }

            String data = "";
            for (int i = 0; i < cursor.getColumnCount(); ++i) {
                data += cursor.getColumnName(i) + ", ";
            }
            Log.d("nicole", "Parent Columns: " + data);

            mDocumentId = cursor.getString(cursor.getColumnIndex(COLUMN_DOCUMENT_ID));
            mMimeType = cursor.getString(cursor.getColumnIndex(COLUMN_MIME_TYPE));
            mName = cursor.getString(cursor.getColumnIndex(COLUMN_DISPLAY_NAME));
            mLastModified = cursor.getLong(cursor.getColumnIndex(COLUMN_LAST_MODIFIED));
            mFlags = cursor.getLong(cursor.getColumnIndex(COLUMN_FLAGS));
            mSize = cursor.getLong(cursor.getColumnIndex(COLUMN_SIZE));
        }

        final Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(mUri,
                DocumentsContract.getDocumentId(mUri));
        try (Cursor cursor = cr.query(childrenUri, null, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) {
                return;
            }

            String data = "";
            for (int i = 0; i < cursor.getColumnCount(); ++i) {
                data += cursor.getColumnName(i) + ", ";
            }
            Log.d("nicole", "Columns: " + data);

            while (cursor.moveToNext()) {
                data = "";
                for (int i = 0; i < cursor.getColumnCount(); ++i) {
                    data += cursor.getString(i) + ", ";
                }
                Log.d("nicole", "Child: " + data);
            }
        }
    }

    public String getName() {
        return mName;
    }

    public String getType() {
        return mMimeType;
    }

    public boolean isFile(Context context, Uri self) {
        return !isDirectory() && !TextUtils.isEmpty(mMimeType);
    }

    public boolean isDirectory() {
        return Objects.equals(mMimeType, DocumentsContract.Document.MIME_TYPE_DIR);
    }

    public boolean renameTo(String displayName) {
        return false;
    }

    public SaFile[] listFiles() {
        return null;
    }

    public static SaFile fromUri(Context context, Uri treeUri) {
        if (isTreeUri(treeUri)) {
            String documentId = DocumentsContract.getTreeDocumentId(treeUri);
            Uri uri = DocumentsContract.buildDocumentUriUsingTree(
                    treeUri,
                    documentId
            );
            return new SaFile(context, uri);
        }
        return null;
    }

    private static boolean isTreeUri(Uri documentUri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return DocumentsContract.isTreeUri(documentUri);
        }
        List<String> paths = documentUri.getPathSegments();
        return paths.size() >= 2 && Objects.equals(paths.get(0), "tree");
    }
}
