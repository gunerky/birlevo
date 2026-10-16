package com.example.contentstudio;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;
import java.util.Random;

/** Test APK only; uses platform classes so its independent process needs no app libraries. */
public class ImageFixtureProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    private synchronized File fixture(String name) throws IOException {
        if (!name.matches("[a-z0-9.-]+")) throw new IllegalArgumentException();
        File file = new File(getContext().getFilesDir(), name);
        if (file.exists() || name.startsWith("export.")) return file;
        if (name.equals("large-square.png") || name.equals("large-landscape.png")) {
            int edgeWidth = name.equals("large-square.png") ? 4000 : 5000;
            int edgeHeight = name.equals("large-square.png") ? 4000 : 2500;
            Bitmap large = Bitmap.createBitmap(edgeWidth,edgeHeight,Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(large);
            android.graphics.Paint paint = new android.graphics.Paint();
            paint.setColor(Color.RED); canvas.drawRect(0,0,edgeWidth/2f,edgeHeight,paint);
            paint.setColor(Color.BLUE); canvas.drawRect(edgeWidth/2f,0,edgeWidth,edgeHeight,paint);
            try (FileOutputStream output = new FileOutputStream(file)) { large.compress(Bitmap.CompressFormat.PNG,100,output); }
            finally { large.recycle(); }
            return file;
        }
        if (name.startsWith("modern")) {
            try (InputStream in = getContext().getAssets().open("images/" + name); OutputStream out = new FileOutputStream(file)) {
                byte[] buffer = new byte[65536]; int size;
                while ((size = in.read(buffer)) >= 0) out.write(buffer, 0, size);
            }
            return file;
        }
        if (name.equals("wrong-type.jpg")) {
            // The provider advertises JPEG, but the actual bytes are a valid one-pixel GIF.
            byte[] gif = android.util.Base64.decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7", android.util.Base64.DEFAULT);
            try (FileOutputStream output = new FileOutputStream(file)) { output.write(gif); }
            return file;
        }
        if (name.equals("invalid.jpg")) {
            try (FileOutputStream output = new FileOutputStream(file)) { output.write("invalid image".getBytes()); }
            return file;
        }
        boolean logo = name.equals("overlay-logo.png");
        boolean tiny = name.equals("tiny.png") || name.equals("alpha.webp"), rotated = name.startsWith("orientation-");
        int width = logo ? 100 : tiny ? 16 : rotated ? 120 : 2048, height = logo ? 50 : tiny ? 16 : rotated ? 80 : 1536;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        int[] pixels = new int[width * height];
        Random random = new Random(73);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int color;
            if (logo) color = (x >= 40 && x < 60 && y >= 15 && y < 35) ? Color.TRANSPARENT :
                (x < 50 ? Color.BLUE : Color.argb(128, 0, 0, 255));
            else if (tiny) color = x == 8 && y == 8 ? Color.GREEN : Color.TRANSPARENT;
            else if (rotated) color = y < height / 2 ? (x < width / 2 ? Color.RED : Color.GREEN) :
                (x < width / 2 ? Color.BLUE : Color.YELLOW);
            else color = Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256));
            pixels[y * width + x] = color;
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        try (FileOutputStream output = new FileOutputStream(file)) {
            Bitmap.CompressFormat codec = name.endsWith(".webp") ?
                (android.os.Build.VERSION.SDK_INT >= 30 ? Bitmap.CompressFormat.WEBP_LOSSLESS : Bitmap.CompressFormat.WEBP) :
                (tiny || logo) ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG;
            bitmap.compress(codec, 100, output);
        } finally { bitmap.recycle(); }
        if (rotated) {
            ExifInterface exif = new ExifInterface(file.getPath());
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, name.substring("orientation-".length(), name.indexOf('.')));
            exif.setAttribute(ExifInterface.TAG_ARTIST, "private marker");
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, "41/1,0/1,0/1");
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N");
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, "29/1,0/1,0/1");
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "E");
            exif.saveAttributes();
        }
        return file;
    }

    @Override public String getType(Uri uri) {
        String name = uri.getLastPathSegment();
        return name.endsWith(".webp") ? "image/webp" : name.endsWith(".png") ? "image/png" : "image/jpeg";
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        String name = uri.getLastPathSegment();
        String[] columns = projection != null ? projection : new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        try {
            File file = fixture(name.equals("slow.jpg") ? "noise.jpg" : name);
            MatrixCursor cursor = new MatrixCursor(columns);
            Object[] values = new Object[columns.length];
            for (int i = 0; i < columns.length; i++) values[i] = columns[i].equals(OpenableColumns.DISPLAY_NAME) ? name : file.length();
            cursor.addRow(values);
            return cursor;
        } catch (IOException error) { throw new IllegalStateException(error); }
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        if (name.equals("fail.jpg")) throw new FileNotFoundException("Simulated destination failure");
        try {
            if (name.equals("slow.jpg")) {
                ParcelFileDescriptor[] pipes = ParcelFileDescriptor.createPipe();
                File file = fixture("noise.jpg");
                Thread writer = new Thread(() -> {
                    try (OutputStream output = new ParcelFileDescriptor.AutoCloseOutputStream(pipes[1]);
                         InputStream input = new FileInputStream(file)) {
                        byte[] buffer = new byte[512];
                        int size;
                        while ((size = input.read(buffer)) >= 0) { output.write(buffer, 0, size); Thread.sleep(2); }
                    } catch (IOException | InterruptedException expectedOnCancellation) { /* Reader closed pipe. */ }
                });
                writer.setDaemon(true); writer.start();
                return pipes[0];
            }
            int flags = mode.equals("r") ? ParcelFileDescriptor.MODE_READ_ONLY :
                ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE | ParcelFileDescriptor.MODE_WRITE_ONLY;
            return ParcelFileDescriptor.open(fixture(name), flags);
        } catch (IOException error) { throw new FileNotFoundException(error.getMessage()); }
    }
    @Override public int delete(Uri uri, String selection, String[] args) {
        if (!uri.getLastPathSegment().equals("export.jpg")) throw new IllegalArgumentException();
        return new File(getContext().getFilesDir(), "export.jpg").delete() ? 1 : 0;
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
