package com.software.sello.platform;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Documents for tests, each path a way a real provider behaves:
 * {@code ok/<name>}, a file in this provider's cache; {@code refuse}, the provider will
 * not open it; {@code readonly}, it opens only for reading, so the first write fails;
 * {@code pipe}, a pipe whose reader takes every byte and reports no error, as a
 * provider that uploads a stream does; {@code broken}, a pipe whose reader has already
 * gone, as when an upload is cut off;
 * {@code reported}, a pipe that takes every byte but whose provider has reported that
 * it failed, as when an upload is refused after it started.
 *
 * <p>Java, not Kotlin: the provider runs in the test package's own process, which has
 * no Kotlin standard library.
 */
public final class TestDocumentsProvider extends ContentProvider {
    public static final String AUTHORITY = "com.software.sello.test.documents";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        List<String> segments = uri.getPathSegments();
        String kind = segments.isEmpty() ? "" : segments.get(0);
        switch (kind) {
            case "ok":
                return ParcelFileDescriptor.open(
                        file(segments.get(1)), ParcelFileDescriptor.parseMode(mode));
            case "readonly":
                File readOnly = file("readonly");
                try {
                    new FileOutputStream(readOnly).close();
                } catch (IOException e) {
                    throw new FileNotFoundException(e.getMessage());
                }
                return ParcelFileDescriptor.open(readOnly, ParcelFileDescriptor.MODE_READ_ONLY);
            case "pipe":
                try {
                    ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createReliablePipe();
                    drain(pipe[0]);
                    return pipe[1];
                } catch (IOException e) {
                    throw new FileNotFoundException(e.getMessage());
                }
            case "broken":
                try {
                    ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createReliablePipe();
                    pipe[0].closeWithError("The upload was cut off");
                    return pipe[1];
                } catch (IOException e) {
                    throw new FileNotFoundException(e.getMessage());
                }
            case "reported":
                try {
                    ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createReliablePipe();
                    // A copy keeps the reading side open, so every write still succeeds
                    // and the report is the only sign of the failure.
                    ParcelFileDescriptor reader = pipe[0].dup();
                    pipe[0].closeWithError("The upload was refused");
                    drain(reader);
                    return pipe[1];
                } catch (IOException e) {
                    throw new FileNotFoundException(e.getMessage());
                }
            default:
                throw new FileNotFoundException("refused");
        }
    }

    @Override
    public Cursor query(
            Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        MatrixCursor cursor = new MatrixCursor(new String[] {OpenableColumns.DISPLAY_NAME});
        cursor.addRow(new Object[] {uri.getLastPathSegment()});
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        return "application/json";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    private static void drain(ParcelFileDescriptor reader) {
        new Thread(() -> {
            try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(reader)) {
                byte[] buffer = new byte[8192];
                while (in.read(buffer) != -1) {
                    // Discarded: only the report matters.
                }
            } catch (IOException ignored) {
                // The writer went away; nothing is left to drain.
            }
        }).start();
    }

    private File file(String name) {
        return new File(getContext().getCacheDir(), name);
    }
}
