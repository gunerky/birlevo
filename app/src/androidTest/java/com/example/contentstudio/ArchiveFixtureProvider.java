package com.example.contentstudio;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Synthetic data in the test APK only. No archive content from a person. */
public class ArchiveFixtureProvider extends ContentProvider {
    @Override public android.os.Bundle call(String method, String arg, android.os.Bundle extras) {
        if (method.equals("tree-grant")) { ArchiveTreeProvider.grant(getContext()); return new android.os.Bundle(); }
        if (method.equals("tree-reset")) {
            try { ArchiveTreeProvider.reset(getContext(), arg == null ? "normal" : arg); return new android.os.Bundle(); }
            catch(IOException e) { throw new IllegalStateException(e); }
        }
        return super.call(method, arg, extras);
    }
    @Override public boolean onCreate() { return true; }
    private synchronized File fixture(String name) throws IOException {
        if (!Arrays.asList("safe.zip", "slow.zip", "unsafe.zip", "broken.zip", "export.bin", "fail.bin", "large.wav").contains(name)) throw new FileNotFoundException();
        File folder = new File(getContext().getFilesDir(), "archive-fixtures"); folder.mkdirs();
        File file = new File(folder, name);
        if (name.equals("large.wav") && file.length() != 128L * 1024 * 1024 + 65536 + 44) {
            long dataBytes = 128L * 1024 * 1024 + 65536;
            try (FileOutputStream out = new FileOutputStream(file)) {
                // The provider runs in the test APK process; do not load production app classes here.
                java.nio.ByteBuffer header = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN);
                header.put(new byte[]{'R','I','F','F'}).putInt((int)dataBytes+36).put(new byte[]{'W','A','V','E','f','m','t',' '})
                    .putInt(16).putShort((short)1).putShort((short)2).putInt(96000).putInt(384000)
                    .putShort((short)4).putShort((short)16).put(new byte[]{'d','a','t','a'}).putInt((int)dataBytes);
                out.write(header.array());
                byte[] block = new byte[65536];
                // Quiet deterministic PCM; 350 seconds, not an oversized decoded bitmap allocation.
                for (int i=0; i<block.length; i+=4) { block[i]=17; block[i+2]=31; }
                for (long left=dataBytes; left>0; left-=block.length) out.write(block);
            }
        }
        if (!file.exists() && name.endsWith(".zip")) {
            try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(file))) {
                for (String path : name.equals("unsafe.zip") ? new String[]{"../outside.txt"} : new String[]{"folder/", "folder/hello.txt", "日本.txt", "empty.bin"}) {
                    out.putNextEntry(new ZipEntry(path));
                    if (!path.endsWith("/") && !path.equals("empty.bin")) out.write(("Exact original: " + path).getBytes("UTF-8"));
                    out.closeEntry();
                }
                if (name.equals("slow.zip")) {
                    out.putNextEntry(new ZipEntry("large.bin"));
                    byte[] bytes = new byte[1024 * 1024]; new Random(17).nextBytes(bytes); out.write(bytes); out.closeEntry();
                }
            }
            if (name.equals("broken.zip")) try (RandomAccessFile out = new RandomAccessFile(file, "rw")) { out.setLength(30); }
        }
        return file;
    }
    @Override public String getType(Uri uri) { return uri.getLastPathSegment().endsWith(".wav") ? "audio/wav" : uri.getLastPathSegment().endsWith(".zip") ? "application/zip" : "application/octet-stream"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        try {
            File file = fixture(uri.getLastPathSegment());
            String[] columns = projection != null ? projection : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
            MatrixCursor result = new MatrixCursor(columns); Object[] values = new Object[columns.length];
            for(int i=0;i<columns.length;i++) values[i] = columns[i].equals(OpenableColumns.DISPLAY_NAME) ? file.getName() : file.length();
            result.addRow(values); return result;
        } catch(IOException e) { throw new IllegalStateException(e); }
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if(uri.getLastPathSegment().equals("fail.bin")) throw new FileNotFoundException("Synthetic destination failure");
        if(uri.getLastPathSegment().equals("slow.zip") && mode.equals("r")) try {
            File file = fixture("slow.zip"); ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createReliablePipe();
            new Thread(() -> {
                try (InputStream input = new FileInputStream(file); OutputStream output = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                    byte[] buffer = new byte[8192]; int count;
                    while ((count = input.read(buffer)) >= 0) { output.write(buffer, 0, count); Thread.sleep(5); }
                } catch(Exception ignored) { }
            }, "synthetic-archive-source").start(); return pipe[0];
        } catch(IOException e) { throw new FileNotFoundException(e.toString()); }
        try { return ParcelFileDescriptor.open(fixture(uri.getLastPathSegment()), mode.equals("r") ? ParcelFileDescriptor.MODE_READ_ONLY :
            ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE); }
        catch(IOException e) { throw new FileNotFoundException(e.toString()); }
    }
    @Override public int delete(Uri uri, String selection, String[] args) { try { return fixture(uri.getLastPathSegment()).delete() ? 1 : 0; } catch(IOException e) { return 0; } }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
