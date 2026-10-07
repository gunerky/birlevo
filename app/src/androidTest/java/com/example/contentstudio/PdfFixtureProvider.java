package com.example.contentstudio;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;
import android.database.MatrixCursor;
import android.provider.OpenableColumns;
import java.io.*;
import java.util.Arrays;

/** Test APK only. Independent provider process uses only Android platform classes. */
public class PdfFixtureProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "export.png".equals(uri.getLastPathSegment()) ? "image/png" : "application/pdf"; }
    private synchronized File fixture(String name) throws IOException {
        if (!Arrays.asList("export.pdf", "export.png", "reader.pdf", "second.pdf", "jpeg2000.pdf", "fake.pdf", "protected.pdf", "oversize.pdf").contains(name))
            throw new FileNotFoundException("Unknown test document");
        File file = new File(getContext().getFilesDir(), name);
        if (file.exists() || name.startsWith("export.")) return file;
        if (name.equals("protected.pdf") || name.equals("jpeg2000.pdf")) {
            try (InputStream input = getContext().getAssets().open("pdfs/" + name); OutputStream output = new FileOutputStream(file)) {
                byte[] buffer = new byte[65536]; int count;
                while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
            }
        } else if (name.equals("fake.pdf")) {
            try (OutputStream output = new FileOutputStream(file)) { output.write("not a PDF".getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
        } else if (name.equals("oversize.pdf")) {
            try (RandomAccessFile output = new RandomAccessFile(file, "rw")) { output.setLength(128L * 1024 * 1024 + 1); }
        } else {
            android.graphics.pdf.PdfDocument document = new android.graphics.pdf.PdfDocument();
            try {
                int[][] dimensions = {{400,200}, {200,400}, {595,842}};
                int[] colors = {android.graphics.Color.BLUE, android.graphics.Color.RED, android.graphics.Color.GREEN};
                for (int index=0; index<3; index++) {
                    android.graphics.pdf.PdfDocument.Page page = document.startPage(new android.graphics.pdf.PdfDocument.PageInfo.Builder(dimensions[index][0], dimensions[index][1], index+1).create());
                    page.getCanvas().drawColor(colors[index]);
                    android.graphics.Paint paint = new android.graphics.Paint(); paint.setColor(android.graphics.Color.WHITE); paint.setTextSize(18);
                    page.getCanvas().drawText("Reader fixture page " + (index+1), 20, 50, paint);
                    document.finishPage(page);
                }
                try (OutputStream output = new FileOutputStream(file)) { document.writeTo(output); }
            } finally { document.close(); }
        }
        return file;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        if ("slow.pdf".equals(name)) {
            try {
                final ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
                new Thread(() -> {
                    try (OutputStream output = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                        Thread.sleep(750);
                        try (InputStream input = new FileInputStream(fixture("reader.pdf"))) {
                            byte[] buffer = new byte[65536]; int count;
                            while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
                        }
                    } catch (Exception ignored) { }
                }).start();
                return pipe[0];
            } catch (IOException error) { throw new FileNotFoundException(error.getMessage()); }
        }
        if ("fail.pdf".equals(name)) throw new FileNotFoundException("Simulated destination failure");
        int flags = "r".equals(mode) ? ParcelFileDescriptor.MODE_READ_ONLY :
            ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE | ParcelFileDescriptor.MODE_WRITE_ONLY;
        try { return ParcelFileDescriptor.open(fixture(name), flags); }
        catch (IOException error) { throw new FileNotFoundException(error.getMessage()); }
    }
    @Override public int delete(Uri uri, String selection, String[] args) {
        if (!Arrays.asList("export.pdf", "export.png").contains(uri.getLastPathSegment())) throw new IllegalArgumentException();
        return new File(getContext().getFilesDir(), uri.getLastPathSegment()).delete() ? 1 : 0;
    }
    // DocumentsContract.deleteDocument uses the provider call protocol, not ContentResolver.delete.
    @Override public Bundle call(String method, String arg, Bundle extras) {
        if ("android:deleteDocument".equals(method)) {
            Uri document = extras.getParcelable("uri");
            delete(document, null, null);
            return new Bundle();
        }
        return super.call(method, arg, extras);
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        try {
            File file = fixture("slow.pdf".equals(uri.getLastPathSegment()) ? "reader.pdf" : uri.getLastPathSegment());
            String[] columns = projection != null ? projection : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
            MatrixCursor result = new MatrixCursor(columns); Object[] values = new Object[columns.length];
            for (int index=0; index<columns.length; index++) values[index] = columns[index].equals(OpenableColumns.DISPLAY_NAME) ? file.getName() : file.length();
            result.addRow(values); return result;
        } catch (IOException error) { throw new IllegalStateException(error); }
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
