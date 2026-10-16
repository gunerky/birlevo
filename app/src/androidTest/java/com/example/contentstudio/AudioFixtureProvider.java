package com.example.contentstudio;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;
import java.util.Arrays;

/** Separate test APK only, with no dependency on app or Kotlin classes. */
public class AudioFixtureProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    private synchronized File fixture(String name) throws IOException {
        if (!Arrays.asList("batch-192000.wav", "mix.wav", "long.m4a", "mono44100.wav", "tone.wav", "tone.mp3", "tone.m4a", "tone.aac", "tone.ogg", "tone.opus", "tone.flac", "invalid.mp3", "export.wav", "export.m4a", "export.ogg", "export.flac", "export.mp3", "fail.wav").contains(name))
            throw new IllegalArgumentException();
        File file = new File(getContext().getFilesDir(), name);
        if (!file.exists() && !name.startsWith("export.") && !name.equals("fail.wav")) {
            try (InputStream in = getContext().getAssets().open("audio/" + name); OutputStream out = new FileOutputStream(file)) {
                byte[] buffer = new byte[65536]; int size;
                while ((size = in.read(buffer)) >= 0) out.write(buffer, 0, size);
            }
        }
        return file;
    }
    @Override public String getType(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name.endsWith("wav")) return "audio/wav";
        if (name.endsWith("ogg")) return "audio/ogg";
        if (name.endsWith("opus")) return "audio/opus";
        if (name.endsWith("flac")) return "audio/flac";
        if (name.endsWith("mp3")) return "audio/mpeg";
        if (name.endsWith("aac")) return "audio/aac";
        return "audio/mp4";
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        try {
            File file = fixture(uri.getLastPathSegment());
            String[] columns = projection != null ? projection : new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
            MatrixCursor result = new MatrixCursor(columns);
            Object[] values = new Object[columns.length];
            for (int i = 0; i < columns.length; i++) values[i] = columns[i].equals(OpenableColumns.DISPLAY_NAME) ? file.getName() : file.length();
            result.addRow(values); return result;
        } catch (IOException e) { throw new IllegalStateException(e); }
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (uri.getLastPathSegment().equals("fail.wav")) throw new FileNotFoundException("Simulated write failure");
        try {
            return ParcelFileDescriptor.open(fixture(uri.getLastPathSegment()), mode.equals("r") ? ParcelFileDescriptor.MODE_READ_ONLY :
                ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE | ParcelFileDescriptor.MODE_WRITE_ONLY);
        } catch (IOException e) { throw new FileNotFoundException(e.getMessage()); }
    }
    @Override public int delete(Uri uri, String selection, String[] args) {
        if (!uri.getLastPathSegment().startsWith("export.")) throw new IllegalArgumentException();
        return new File(getContext().getFilesDir(), uri.getLastPathSegment()).delete() ? 1 : 0;
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
