package com.example.contentstudio;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

/** Synthetic store-demo inputs only, served from the separate test APK's directory. */
public final class StoreFixtureProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    private File fixture(Uri uri) throws IOException {
        String name = uri.getLastPathSegment();
        if (!Arrays.asList("lake.png", "cover.png", "lake-video.mp4", "lake-melody.wav",
                "travel-budget.csv", "weekend-guide.pdf").contains(name))
            throw new IllegalArgumentException("Unknown store fixture");
        File directory = getContext().getExternalFilesDir("store-capture");
        if (directory == null) throw new IOException("External fixture storage unavailable");
        File file = new File(directory, name);
        if (name.equals("weekend-guide.pdf") && !file.exists()) createPdf(file, new File(directory, "lake.png"));
        if (!file.isFile()) throw new FileNotFoundException(name);
        return file;
    }

    private void createPdf(File file, File image) throws IOException {
        PdfDocument pdf = new PdfDocument();
        try {
            Bitmap picture = BitmapFactory.decodeFile(image.getPath());
            if (picture == null) throw new IOException("Missing demo image");
            try {
                for (int pageNumber = 1; pageNumber <= 2; pageNumber++) {
                    PdfDocument.Page page = pdf.startPage(new PdfDocument.PageInfo.Builder(595, 842, pageNumber).create());
                    Canvas canvas = page.getCanvas();
                    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    canvas.drawColor(Color.WHITE);
                    paint.setColor(Color.rgb(8,22,77)); paint.setTextSize(16);
                    canvas.drawText("WEEKEND GUIDE", 40, 55, paint);
                    paint.setTextSize(34); paint.setFakeBoldText(true);
                    canvas.drawText(pageNumber == 1 ? "A little space to explore" : "Pack light. Enjoy more.", 40, 110, paint);
                    paint.setFakeBoldText(false);
                    if (pageNumber == 1) canvas.drawBitmap(picture, null, new RectF(40, 145, 555, 435), paint);
                    String[] rows = pageNumber == 1 ? new String[] {
                        "Saturday", "09:00   Walk along the lake", "12:30   Lunch with a view",
                        "16:00   Photos in the afternoon light", "Sunday", "08:30   A slow morning outdoors", "11:00   Save your favorite moments"
                    } : new String[] {"The essentials", "A light jacket and comfortable shoes", "Water, a small snack and a notebook", "A camera or your phone", "Make room for memories", "Keep a few photos. Write a few lines.", "Bring your story together."};
                    int y = pageNumber == 1 ? 480 : 180;
                    for (String row : rows) {
                        paint.setColor(Color.rgb(21,36,73)); paint.setTextSize(19);
                        canvas.drawText(row, 42, y, paint); y += 36;
                    }
                    paint.setColor(Color.rgb(86,101,129)); paint.setTextSize(11);
                    canvas.drawText("Sample document / Birlevo                                      " + pageNumber + " / 2", 42, 800, paint);
                    pdf.finishPage(page);
                }
                try (FileOutputStream out = new FileOutputStream(file)) { pdf.writeTo(out); }
            } finally { picture.recycle(); }
        } finally { pdf.close(); }
    }

    @Override public String getType(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".mp4")) return "video/mp4";
        if (name.endsWith(".wav")) return "audio/wav";
        if (name.endsWith(".csv")) return "text/csv";
        return "application/pdf";
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        try {
            File file = fixture(uri);
            String[] columns = projection == null ? new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
            MatrixCursor cursor = new MatrixCursor(columns);
            Object[] row = new Object[columns.length];
            for (int i = 0; i < columns.length; i++) row[i] = columns[i].equals(OpenableColumns.DISPLAY_NAME) ? file.getName() : file.length();
            cursor.addRow(row); return cursor;
        } catch (IOException error) { throw new IllegalStateException(error); }
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!mode.equals("r")) throw new FileNotFoundException("Read-only demo inputs");
        try { return ParcelFileDescriptor.open(fixture(uri), ParcelFileDescriptor.MODE_READ_ONLY); }
        catch (IOException error) { throw new FileNotFoundException(error.getMessage()); }
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
