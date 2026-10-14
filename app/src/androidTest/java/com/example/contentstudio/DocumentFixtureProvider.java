package com.example.contentstudio;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/** Synthetic documents only, in the separate test APK. */
public class DocumentFixtureProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    private synchronized File fixture(String name) throws IOException {
        if (!name.matches("[a-z0-9.-]+")) throw new IllegalArgumentException();
        File file = new File(getContext().getFilesDir(), "docs-" + name);
        if (file.exists() || name.startsWith("export.")) return file;
        byte[] bytes;
        switch(name) {
            case "rich.docx": case "rich.xlsx": case "shared-no-cache.xlsx": {
                try (InputStream input = getContext().getAssets().open("office/" + name)) { bytes = input.readAllBytes(); }
                break;
            }
            case "invalid.docx": {
                try (InputStream input = getContext().getAssets().open("office/duplicate.zip")) { bytes = input.readAllBytes(); }
                break;
            }
            case "records.csv": bytes = ("Name,Note,Value\r\n\"A, B\",\"line 1\r\nline 2\",\"00123\"\r\n\"İstanbul\",\"quote \"\"yes\"\"\",\"=SUM(1,2)\"\r\n\"北京\",,\"\"\r\n").getBytes(StandardCharsets.UTF_8); break;
            case "semicolon.csv": bytes = "name;value;note\r\nAlpha;1,25;x\r\nBeta;2,50;y\r\n".getBytes(StandardCharsets.UTF_8); break;
            case "wide.tsv": bytes = "a\tb\tc\td\te\tf\tg\n1\t2\t3\t4\t5\t6\tlast-cell\n".getBytes(StandardCharsets.UTF_8); break;
            case "utf16.txt": bytes = "İstanbul العربية 北京 🙂\r\nSecond line".getBytes(StandardCharsets.UTF_16); break;
            case "legacy.txt": bytes = "İstanbul ığüşöç".getBytes(Charset.forName("windows-1254")); break;
            case "rtl.txt": bytes = "العربية\nفارسی\nİstanbul\n北京\n🙂\n".getBytes(StandardCharsets.UTF_8); break;
            case "invalid.csv": bytes = "a,b\n\"unterminated".getBytes(StandardCharsets.UTF_8); break;
            case "invalid.txt": bytes = new byte[]{(byte)0xc3}; break;
            case "binary.txt": bytes = new byte[]{'P','K',3,4,0,0,0,0}; break;
            case "large-cell.csv": bytes = new byte[32769]; java.util.Arrays.fill(bytes, (byte)'a'); break;
            case "oversize.txt": bytes = new byte[4 * 1024 * 1024 + 1]; java.util.Arrays.fill(bytes, (byte)'a'); break;
            case "long.txt": {
                StringBuilder text = new StringBuilder();
                for (int i=1;i<=2400;i++) text.append("Line ").append(i).append(" all words retained in the generated text PDF.\r\n");
                bytes=text.toString().getBytes(StandardCharsets.UTF_8); break;
            }
            default: throw new FileNotFoundException(name);
        }
        try(OutputStream output = new FileOutputStream(file)) { output.write(bytes); }
        return file;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String name=uri.getLastPathSegment();
        if ("slow.txt".equals(name) || "slow.docx".equals(name)) {
            try {
                ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createPipe();
                new Thread(() -> { try(OutputStream out=new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                    byte[] data=new byte[4096]; java.util.Arrays.fill(data,(byte)'a');
                    for(int i=0;i<600;i++){out.write(data);Thread.sleep(15);}
                } catch(Exception ignored) {} },"test-document-pipe").start();
                return pipe[0];
            } catch(IOException error){throw new FileNotFoundException();}
        }
        try { return ParcelFileDescriptor.open(fixture(name), mode.contains("w") ? ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE | ParcelFileDescriptor.MODE_WRITE_ONLY : ParcelFileDescriptor.MODE_READ_ONLY); }
        catch(IOException error) { throw new FileNotFoundException(); }
    }
    @Override public String getType(Uri uri) { return "text/plain"; } // Deliberately not enough to infer CSV or encoding.
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort) {
        MatrixCursor cursor=new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE});
        String name=uri.getLastPathSegment();
        try { cursor.addRow(new Object[]{name,name.startsWith("slow.")?null:fixture(name).length()}); }
        catch(IOException error){throw new IllegalArgumentException(error);}
        return cursor;
    }
    @Override public int delete(Uri uri,String selection,String[] args) { try { return fixture(uri.getLastPathSegment()).delete()?1:0; } catch(IOException error){return 0;} }
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException();}
}
