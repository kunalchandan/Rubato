package one.chandan.rubato.util;

import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.documentfile.provider.DocumentFile;

import one.chandan.rubato.R;
import one.chandan.rubato.model.LocalSource;

public final class LocalSourceUtil {
    private LocalSourceUtil() {
    }

    public static LocalSource buildLocalSource(Context context, Uri uri) {
        String displayName = context.getString(R.string.music_sources_local_default_name);
        DocumentFile documentFile = DocumentFile.fromTreeUri(context, uri);
        if (documentFile != null && documentFile.getName() != null && !documentFile.getName().isEmpty()) {
            displayName = documentFile.getName();
        }

        String treeId = null;
        try {
            treeId = DocumentsContract.getTreeDocumentId(uri);
        } catch (Exception ignored) {
        }
        String volume = null;
        String relativePath = null;
        if (treeId != null) {
            int split = treeId.indexOf(':');
            if (split >= 0) {
                volume = treeId.substring(0, split);
                relativePath = treeId.substring(split + 1);
            } else {
                relativePath = treeId;
            }
        }

        if (relativePath != null) {
            relativePath = relativePath.replace("\\\\", "/");
            if (relativePath.startsWith("/")) {
                relativePath = relativePath.substring(1);
            }
            if (!relativePath.isEmpty() && !relativePath.endsWith("/")) {
                relativePath = relativePath + "/";
            }
            if (relativePath.isEmpty()) {
                relativePath = null;
            }
        }

        String treeUri = uri.toString();
        return new LocalSource(
                treeUri,
                treeUri,
                displayName,
                relativePath,
                volume,
                System.currentTimeMillis()
        );
    }
}
