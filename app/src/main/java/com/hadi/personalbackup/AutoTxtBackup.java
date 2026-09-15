package com.hadi.personalbackup;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class AutoTxtBackup {
    private static final String FOLDER_NAME = "PBackup";
    private static final String MESSAGES_FILE = "messages.txt";
    private static final String CALLS_FILE = "calls.txt";

    private AutoTxtBackup() {}

    public static String visibleFolder() {
        return "Download/PBackup";
    }

    public static synchronized void ensureFiles(Context context, String fullSmsText, String fullCallsText) {
        try {
            if (!exists(context, MESSAGES_FILE)) {
                writeFull(context, MESSAGES_FILE, fullSmsText);
            }
        } catch (Exception ignored) {}

        try {
            if (!exists(context, CALLS_FILE)) {
                writeFull(context, CALLS_FILE, fullCallsText);
            }
        } catch (Exception ignored) {}
    }

    public static synchronized void appendMessage(Context context, String entry, String fullFallbackText) {
        appendOrRecreate(context, MESSAGES_FILE, entry, fullFallbackText);
    }

    public static synchronized void appendCall(Context context, String entry, String fullFallbackText) {
        appendOrRecreate(context, CALLS_FILE, entry, fullFallbackText);
    }

    private static void appendOrRecreate(Context context, String fileName, String entry, String fullFallbackText) {
        try {
            if (exists(context, fileName)) {
                append(context, fileName, entry);
            } else {
                // If the user deleted the visible TXT file, rebuild it from the local database.
                writeFull(context, fileName, fullFallbackText);
            }
        } catch (Exception ignored) {}
    }

    private static boolean exists(Context context, String fileName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return findDownloadUri(context, fileName) != null;
        }
        return legacyFile(fileName).exists();
    }

    private static void writeFull(Context context, String fileName, String text) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Uri uri = findDownloadUri(context, fileName);
            if (uri == null) {
                uri = createDownloadUri(context, fileName);
            }

            try (OutputStream out = context.getContentResolver().openOutputStream(uri, "w")) {
                if (out == null) throw new IllegalStateException("Could not open TXT file");
                out.write(text.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }

            ContentValues done = new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING, 0);
            context.getContentResolver().update(uri, done, null, null);
        } else {
            File file = legacyFile(fileName);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (FileOutputStream out = new FileOutputStream(file, false)) {
                out.write(text.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
            scanLegacy(context, file);
        }
    }

    private static void append(Context context, String fileName, String text) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Uri uri = findDownloadUri(context, fileName);
            if (uri == null) {
                throw new IllegalStateException("TXT file not found");
            }

            try (OutputStream out = context.getContentResolver().openOutputStream(uri, "wa")) {
                if (out == null) throw new IllegalStateException("Could not append TXT file");
                out.write(text.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        } else {
            File file = legacyFile(fileName);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (FileWriter writer = new FileWriter(file, true)) {
                writer.write(text);
                writer.flush();
            }
            scanLegacy(context, file);
        }
    }

    private static Uri findDownloadUri(Context context, String fileName) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null;

        ContentResolver resolver = context.getContentResolver();
        Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
        String relativePath = Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER_NAME + "/";

        String selection = MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " +
                MediaStore.MediaColumns.RELATIVE_PATH + "=?";
        String[] args = new String[]{fileName, relativePath};

        try (Cursor cursor = resolver.query(
                collection,
                new String[]{MediaStore.MediaColumns._ID},
                selection,
                args,
                null
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                long id = cursor.getLong(0);
                return ContentUris.withAppendedId(collection, id);
            }
        } catch (Exception ignored) {}

        return null;
    }

    private static Uri createDownloadUri(Context context, String fileName) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
        values.put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER_NAME + "/"
        );
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);

        Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IllegalStateException("Could not create TXT file");
        return uri;
    }

    @SuppressWarnings("deprecation")
    private static File legacyFile(String fileName) {
        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        return new File(new File(downloads, FOLDER_NAME), fileName);
    }

    private static void scanLegacy(Context context, File file) {
        MediaScannerConnection.scanFile(
                context,
                new String[]{file.getAbsolutePath()},
                new String[]{"text/plain"},
                null
        );
    }
}
