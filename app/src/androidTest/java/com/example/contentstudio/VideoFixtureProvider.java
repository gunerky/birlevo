package com.example.contentstudio;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;
import java.util.Arrays;

/** Separate test APK only, with no dependency on app or Kotlin classes. */
public class VideoFixtureProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    private synchronized File fixture(String name) throws IOException {
        if (!Arrays.asList("demo.mp4", "audio.mp4", "silent.mp4", "low-bitrate.mp4", "portrait.mp4", "cancel.mp4", "invalid.mp4", "export.mp4", "export.webm", "fail.mp4",
                "hevc.mp4", "hdr.mp4", "container.mkv", "tracks.mp4", "phone.mov", "phone.3gp", "phone.webm",
                "phone-vorbis.webm", "phone-mp3.mkv", "phone-10bit.mp4", "phone-mpeg4.mp4", "edit-first.mp4", "edit-second.mp4").contains(name))
            throw new IllegalArgumentException();
        File file = new File(getContext().getFilesDir(), name);
        if (!file.exists() && !name.equals("export.mp4") && !name.equals("export.webm") && !name.equals("fail.mp4")) {
            try (InputStream in = getContext().getAssets().open("videos/" + name); OutputStream out = new FileOutputStream(file)) {
                byte[] buffer = new byte[65536]; int size;
                while ((size = in.read(buffer)) >= 0) out.write(buffer, 0, size);
            }
        }
        return file;
    }
    @Override public String getType(Uri uri) { return uri.getLastPathSegment().equals("export.webm") ? "video/webm" : "video/mp4"; }
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
        if (uri.getLastPathSegment().equals("fail.mp4")) throw new FileNotFoundException("Simulated write failure");
        try {
            return ParcelFileDescriptor.open(fixture(uri.getLastPathSegment()), mode.equals("r") ? ParcelFileDescriptor.MODE_READ_ONLY :
                ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE | ParcelFileDescriptor.MODE_WRITE_ONLY);
        } catch (IOException e) { throw new FileNotFoundException(e.getMessage()); }
    }
    @Override public int delete(Uri uri, String selection, String[] args) {
        String name = uri.getLastPathSegment();
        if (!name.equals("export.mp4") && !name.equals("export.webm")) throw new IllegalArgumentException();
        return new File(getContext().getFilesDir(), name).delete() ? 1 : 0;
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
