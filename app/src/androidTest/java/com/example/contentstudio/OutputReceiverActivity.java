package com.example.contentstudio;

import android.app.Activity;
import android.os.Bundle;
import android.net.Uri;
import android.content.Intent;
import java.io.*;

/** Separate test-package UID verifies that a read grant really works across applications. */
public class OutputReceiverActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Uri uri = getIntent().getData();
        if (uri == null) uri = getIntent().getParcelableExtra(Intent.EXTRA_STREAM);
        File output = new File(getFilesDir(), "export.jpg");
        try (InputStream input = getContentResolver().openInputStream(uri);
             FileOutputStream stream = new FileOutputStream(output)) {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = input.read(buffer)) >= 0) stream.write(buffer, 0, count);
        } catch (Exception error) { output.delete(); }
        finish();
    }
}
