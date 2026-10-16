package com.example.contentstudio;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;
import java.io.*;

/** Real DocumentsProvider protocol in the test APK, with explicit failure injection. */
public class ArchiveTreeProvider extends DocumentsProvider {
    static final String AUTH = "com.example.contentstudio.test.archive.tree";
    private static String mode = "normal";
    private static boolean linked;
    private File root() { File f = new File(getContext().getFilesDir(), "archive-tree"); f.mkdirs(); return f; }
    static void reset(Context context, String value) throws IOException {
        File root = new File(context.getFilesDir(), "archive-tree");
        remove(root); root.mkdirs(); mode = value; linked = false;
        try (FileOutputStream out = new FileOutputStream(new File(root, "sentinel.txt"))) { out.write(new byte[]{7,8,9}); }
        grant(context);
    }
    static void grant(Context context) {
        context.grantUriPermission("studio.birlevo.android", DocumentsContract.buildTreeDocumentUri(AUTH, "root"),
            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
    }
    private static void remove(File f) throws IOException {
        if (f.isDirectory()) { File[] children = f.listFiles(); if(children != null) for(File child : children) remove(child); }
        if(f.exists() && !f.delete()) throw new IOException("cleanup failed");
    }
    private File file(String id) throws FileNotFoundException {
        try {
            if(!id.equals("root") && !id.startsWith("root/")) throw new IOException();
            File f = id.equals("root") ? root() : new File(root(), id.substring(5));
            if(!f.getCanonicalPath().startsWith(root().getCanonicalPath() + "/") && !f.equals(root())) throw new IOException();
            return f;
        } catch(IOException e) { throw new FileNotFoundException(); }
    }
    private String id(File f) { return f.equals(root()) ? "root" : "root/" + root().toPath().relativize(f.toPath()).toString().replace(File.separatorChar, '/'); }
    private static final String[] DOC = {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_FLAGS};
    private void row(MatrixCursor cursor, File f) {
        MatrixCursor.RowBuilder r = cursor.newRow();
        for(String col : cursor.getColumnNames()) {
            switch(col) {
                case DocumentsContract.Document.COLUMN_DOCUMENT_ID: r.add(id(f)); break;
                case DocumentsContract.Document.COLUMN_DISPLAY_NAME: r.add(f.getName()); break;
                case DocumentsContract.Document.COLUMN_MIME_TYPE: r.add(f.isDirectory() ? DocumentsContract.Document.MIME_TYPE_DIR : "application/octet-stream"); break;
                case DocumentsContract.Document.COLUMN_SIZE: r.add(f.length()); break;
                case DocumentsContract.Document.COLUMN_FLAGS: r.add(f.isDirectory() ? DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE | DocumentsContract.Document.FLAG_SUPPORTS_DELETE : DocumentsContract.Document.FLAG_SUPPORTS_WRITE | DocumentsContract.Document.FLAG_SUPPORTS_DELETE); break;
                default: r.add(null);
            }
        }
    }
    @Override public boolean onCreate() { return true; }
    @Override public Cursor queryRoots(String[] projection) {
        return new MatrixCursor(projection != null ? projection : new String[]{DocumentsContract.Root.COLUMN_ROOT_ID});
    }
    @Override public Cursor queryDocument(String documentId, String[] projection) throws FileNotFoundException {
        File f = file(documentId); if(!f.exists()) throw new FileNotFoundException();
        MatrixCursor result = new MatrixCursor(projection != null ? projection : DOC); row(result,f); return result;
    }
    @Override public Cursor queryChildDocuments(String parent, String[] projection, String sort) throws FileNotFoundException {
        MatrixCursor result = new MatrixCursor(projection != null ? projection : DOC);
        File[] files = file(parent).listFiles(); if(files == null) throw new FileNotFoundException();
        for(File f : files) row(result, f);
        if(mode.equals("child-alias") && linked && !parent.equals("root")) row(result,new File(root(),"sentinel.txt"));
        return result;
    }
    @Override public boolean isChildDocument(String parent, String child) { return child.startsWith(parent + "/"); }
    @Override public String createDocument(String parent, String mime, String name) throws FileNotFoundException {
        if(mode.equals("alias")) return "root/sentinel.txt";
        if(mode.equals("root-alias")) return "root";
        if(mode.equals("child-alias") && !mime.equals(DocumentsContract.Document.MIME_TYPE_DIR)) { linked = true; return "root/sentinel.txt"; }
        File f = new File(file(parent), mode.equals("rename") ? name + "-renamed" : name);
        try {
            if(DocumentsContract.Document.MIME_TYPE_DIR.equals(mime) ? !f.mkdir() : !f.createNewFile()) throw new IOException();
        } catch(IOException e) { throw new FileNotFoundException(); }
        return id(f);
    }
    @Override public ParcelFileDescriptor openDocument(String documentId, String access, CancellationSignal signal) throws FileNotFoundException {
        if(access.contains("w") && (mode.equals("write-fail") || mode.equals("write-delete-fail"))) throw new FileNotFoundException("synthetic write failure");
        return ParcelFileDescriptor.open(file(documentId), ParcelFileDescriptor.parseMode(access));
    }
    @Override public void deleteDocument(String documentId) throws FileNotFoundException {
        if(documentId.equals("root") || documentId.equals("root/sentinel.txt") || mode.equals("write-delete-fail")) throw new FileNotFoundException("not owned or deletion unavailable");
        try { remove(file(documentId)); } catch(IOException e) { throw new FileNotFoundException(); }
    }
}
