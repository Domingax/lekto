package com.lekto.app;

import android.net.Uri;
import android.util.Base64;

import androidx.documentfile.provider.DocumentFile;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Native plugin for reading and writing files inside a Storage Access Framework (SAF)
 * tree URI — i.e. a directory the user granted access to via the system directory picker.
 *
 * Capacitor's built-in Filesystem plugin refuses to write to content:// URIs, so we need
 * direct DocumentFile + ContentResolver calls.
 */
@CapacitorPlugin(name = "VaultFs")
public class VaultFsPlugin extends Plugin {

    /**
     * Navigate from a SAF tree root to a sub-directory, creating intermediate directories
     * if they do not exist yet.
     *
     * @param root   DocumentFile for the tree root
     * @param parts  path segments to navigate (empty segments are skipped)
     * @param create whether to create missing directories
     * @return the target DocumentFile, or null if navigation failed
     */
    private DocumentFile navigateDirs(DocumentFile root, String[] parts, boolean create) {
        DocumentFile current = root;
        for (String part : parts) {
            if (part == null || part.isEmpty()) continue;
            DocumentFile child = current.findFile(part);
            if (child == null || !child.isDirectory()) {
                if (!create) return null;
                child = current.createDirectory(part);
                if (child == null) return null;
            }
            current = child;
        }
        return current;
    }

    /**
     * Write a base64-encoded binary file to a path relative to a SAF tree URI.
     *
     * Required call params:
     *   treeUri  {string}  — the SAF tree URI (content://…/tree/…)
     *   path     {string}  — relative path inside the tree, e.g. "books/file.epub"
     *   data     {string}  — base64-encoded file content
     */
    @PluginMethod
    public void writeFile(PluginCall call) {
        String treeUriStr = call.getString("treeUri");
        String relativePath = call.getString("path");
        String base64Data = call.getString("data");

        if (treeUriStr == null || relativePath == null || base64Data == null) {
            call.reject("treeUri, path and data are required");
            return;
        }

        try {
            Uri treeUri = Uri.parse(treeUriStr);
            DocumentFile treeDoc = DocumentFile.fromTreeUri(getContext(), treeUri);
            if (treeDoc == null) {
                call.reject("Invalid or inaccessible tree URI");
                return;
            }

            String[] parts = relativePath.split("/");
            if (parts.length == 0) {
                call.reject("path must not be empty");
                return;
            }

            // All segments except the last are directories
            String[] dirParts = new String[parts.length - 1];
            System.arraycopy(parts, 0, dirParts, 0, parts.length - 1);
            String filename = parts[parts.length - 1];

            DocumentFile dir = navigateDirs(treeDoc, dirParts, true);
            if (dir == null) {
                call.reject("Failed to navigate or create parent directories");
                return;
            }

            // Delete existing file so we can recreate it (SAF has no overwrite mode)
            DocumentFile existing = dir.findFile(filename);
            if (existing != null && existing.isFile()) {
                existing.delete();
            }

            String mimeType = filename.endsWith(".epub") ? "application/epub+zip" : "application/octet-stream";
            DocumentFile newFile = dir.createFile(mimeType, filename);
            if (newFile == null) {
                call.reject("Failed to create file: " + filename);
                return;
            }

            byte[] bytes = Base64.decode(base64Data, Base64.DEFAULT);
            OutputStream os = getContext().getContentResolver().openOutputStream(newFile.getUri(), "w");
            if (os == null) {
                call.reject("Failed to open output stream for: " + filename);
                return;
            }

            try {
                os.write(bytes);
                os.flush();
            } finally {
                os.close();
            }

            call.resolve();
        } catch (IOException e) {
            call.reject("IO error: " + e.getMessage());
        } catch (Exception e) {
            call.reject(e.getMessage() != null ? e.getMessage() : "Unknown error");
        }
    }

    /**
     * Create a directory (and all intermediate parents) inside a SAF tree URI.
     * Succeeds silently if the directory already exists.
     *
     * Required call params:
     *   treeUri  {string}  — the SAF tree URI
     *   path     {string}  — relative directory path, e.g. "books"
     */
    @PluginMethod
    public void mkdir(PluginCall call) {
        String treeUriStr = call.getString("treeUri");
        String relativePath = call.getString("path");

        if (treeUriStr == null || relativePath == null) {
            call.reject("treeUri and path are required");
            return;
        }

        try {
            Uri treeUri = Uri.parse(treeUriStr);
            DocumentFile treeDoc = DocumentFile.fromTreeUri(getContext(), treeUri);
            if (treeDoc == null) {
                call.reject("Invalid or inaccessible tree URI");
                return;
            }

            String[] parts = relativePath.split("/");
            DocumentFile result = navigateDirs(treeDoc, parts, true);
            if (result == null) {
                call.reject("Failed to create directory: " + relativePath);
                return;
            }

            call.resolve();
        } catch (Exception e) {
            call.reject(e.getMessage() != null ? e.getMessage() : "Unknown error");
        }
    }
}
