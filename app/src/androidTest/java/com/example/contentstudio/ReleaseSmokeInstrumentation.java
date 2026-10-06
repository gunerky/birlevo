package com.example.contentstudio;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.LocaleManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.pdf.PdfRenderer;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Bundle;
import android.os.LocaleList;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.List;

/** Platform-only black-box runner: independent of app/Compose/Kotlin class names that R8
 * can optimize away. Fixtures and this instrumentation live solely in the test APK. */
public class ReleaseSmokeInstrumentation extends Instrumentation {
    private Activity activity;
    private boolean restoreOnly;
    private boolean pdfOnly;
    private String workflow;
    private final StringBuilder log = new StringBuilder();
    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        restoreOnly = args != null && "true".equals(args.getString("restoreOnly"));
        pdfOnly = args != null && "true".equals(args.getString("pdfOnly"));
        workflow = args == null ? null : args.getString("workflow");
        start();
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            android.accessibilityservice.AccessibilityServiceInfo service = getUiAutomation().getServiceInfo();
            service.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            getUiAutomation().setServiceInfo(service);
            if (android.os.Build.VERSION.SDK_INT >= 33) getTargetContext().getSystemService(LocaleManager.class)
                .setApplicationLocales(LocaleList.forLanguageTags("en-US"));
            if (workflow != null) {
                switch (workflow) {
                    case "image": image(); break;
                    case "video": video(); break;
                    case "pdf": pdf(); break;
                    case "transfer": transfer(); break;
                    case "formats": formats(); break;
                    case "photos": photos(); break;
                    case "support": support(); break;
                    default: throw new IllegalArgumentException("Unknown workflow");
                }
                result.putString("stream", log + "\nOK (1 " + workflow + " workflow)\n");
            } else if (pdfOnly) {
                pdf(); result.putString("stream", log + "\nOK (1 PDF workflow)\n");
            } else if (restoreOnly) {
                reopenTransferred(); result.putString("stream", log + "\nOK (1 restored project workflow)\n");
            } else {
                progress("image"); image(); progress("video"); video(); progress("PDF"); pdf(); progress("transfer"); transfer();
                boolean extended = getTargetContext().getPackageManager().getPackageInfo("studio.birlevo.android", 0).versionCode >= 8;
                if (extended) { progress("formats"); formats(); progress("photos"); photos(); }
                boolean compatibility = getTargetContext().getPackageManager().getPackageInfo("studio.birlevo.android", 0).versionCode >= 9;
                if (compatibility) { progress("support"); support(); }
                result.putString("stream", log + "\nOK (" + (compatibility ? 7 : extended ? 6 : 4) + " release workflows)\n");
            }
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            try {
                java.io.File screenshot = new java.io.File(getTargetContext().getExternalFilesDir(null), "qa/native-failure.png");
                screenshot.getParentFile().mkdirs();
                Bitmap screen = getUiAutomation().takeScreenshot();
                try (java.io.FileOutputStream stream = new java.io.FileOutputStream(screenshot)) {
                    screen.compress(Bitmap.CompressFormat.PNG, 100, stream);
                } finally { screen.recycle(); }
                describe(activeRoot(), 0);
            } catch (Throwable ignored) { }
            java.io.StringWriter stack = new java.io.StringWriter();
            error.printStackTrace(new java.io.PrintWriter(stack));
            result.putString("stream", log + "\n" + stack);
            finish(Activity.RESULT_CANCELED, result);
        }
    }
    private void describe(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 15) return;
        if (node.getText() != null || node.isEditable() || node.isScrollable()) {
            android.graphics.Rect bounds = new android.graphics.Rect(); node.getBoundsInScreen(bounds);
            log.append("Node ").append(node.getClassName()).append(" text=").append(node.getText())
                .append(" enabled=").append(node.isEnabled()).append(" editable=").append(node.isEditable()).append(" scrollable=").append(node.isScrollable())
                .append(" actions=").append(node.getActions()).append(" bounds=").append(bounds).append("\n");
        }
        for (int i=0; i<node.getChildCount(); i++) describe(node.getChild(i), depth+1);
    }
    private void progress(String task) {
        Bundle status = new Bundle(); status.putString("stream", "Release workflow: " + task + "\n");
        sendStatus(0, status);
    }
    private void launch() {
        activity = startActivitySync(new Intent().setClassName("studio.birlevo.android",
            "com.example.contentstudio.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        waitForIdleSync();
        // At 2x font size the first action card may be below the viewport. The wordmark
        // identifies the home screen; each subsequent action scrolls to its own target.
        find("Birlevo", 15000, false);
    }
    private void close() { runOnMainSync(() -> activity.finish()); waitForIdleSync(); }
    private String string(String name) {
        int id = getTargetContext().getResources().getIdentifier(name, "string", "com.example.contentstudio");
        if (id == 0) id = getTargetContext().getResources().getIdentifier(name, "string", "studio.birlevo.android");
        if (id == 0) throw new AssertionError("Missing localized resource " + name);
        return getTargetContext().getString(id);
    }
    private String pixels(int edge) {
        int unit = getTargetContext().getResources().getIdentifier("unit_pixels", "string", "studio.birlevo.android");
        if (unit == 0) unit = getTargetContext().getResources().getIdentifier("unit_pixels", "string", "com.example.contentstudio");
        return unit == 0 ? edge + " px" : getTargetContext().getString(unit,
            java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(edge));
    }
    private void support() throws Exception {
        launch(); click(string("supported_files"));
        find(string("files_input") + ": JPEG / PNG / WebP", 15000, true);
        find(string("files_output") + ": JPEG / PNG / WebP", 15000, true);
        capture("supported-files");
        find(string("files_output") + ": PDF", 15000, true);
        find(string("files_pdf_creation"), 15000, true);
        find(string("files_pending"), 15000, true); click(string("close"));
        click("Compress image");
        picker(Intent.ACTION_OPEN_DOCUMENT, "content://com.example.contentstudio.test.fixtures/wrong-type.jpg", () -> click("Choose image"));
        find(string("error_image_format"), 15000, true); capture("unsupported-image"); close();
        String[][] videos = {{"hevc.mp4", "error_video_codec"}, {"hdr.mp4", "error_video_hdr"},
            {"tracks.mp4", "error_video_tracks"}, {"container.mkv", "error_video_container"}};
        for (String[] video : videos) {
            launch(); click("Edit video");
            picker(Intent.ACTION_OPEN_DOCUMENT, "content://com.example.contentstudio.test.videos/" + video[0], () -> click("Choose video"));
            find(string(video[1]), 15000, true); close();
        }
        log.append("Supported files help and actual GIF/HEVC/HDR/multiple-track/container errors: passed\n");
    }
    private void capture(String name) throws Exception {
        waitForIdleSync();
        java.io.File file = new java.io.File(getTargetContext().getExternalFilesDir(null), "qa/" + name + ".png");
        file.getParentFile().mkdirs();
        Bitmap screen = getUiAutomation().takeScreenshot();
        try (java.io.FileOutputStream stream = new java.io.FileOutputStream(file)) {
            screen.compress(Bitmap.CompressFormat.PNG, 100, stream);
        } finally { screen.recycle(); }
    }
    private void picker(String action, String uri, Runnable click) throws Exception {
        IntentFilter filter = new IntentFilter(action);
        filter.addCategory(Intent.CATEGORY_OPENABLE); filter.addCategory(Intent.CATEGORY_DEFAULT);
        filter.addDataType("*/*");
        ActivityMonitor monitor = addMonitor(filter, new ActivityResult(Activity.RESULT_OK,
            new Intent().setData(Uri.parse(uri))), true);
        try { click.run(); } finally { removeMonitor(monitor); }
    }
    private void image() throws Exception {
        launch(); click("Compress image");
        picker(Intent.ACTION_OPEN_DOCUMENT, "content://com.example.contentstudio.test.fixtures/noise.jpg", () -> click("Choose image"));
        click(pixels(1280)); click("Generate preview");
        find("Save new image", 30000, true);
        String destination = "content://com.example.contentstudio.test.fixtures/export.jpg";
        picker(Intent.ACTION_CREATE_DOCUMENT, destination, () -> click("Save new image"));
        find("New image saved", 20000, true);
        Bitmap bitmap = readBitmap(destination);
        if (bitmap == null || bitmap.getWidth() != 1280 || bitmap.getHeight() != 960) throw new AssertionError("Image output dimensions");
        bitmap.recycle(); outputActions("image/jpeg"); close(); log.append("Image import/compress/export: passed\n");
        guardedCopy("Compress image", "Choose image", "tiny.png", "Generate preview", "Save new image",
            "content://com.example.contentstudio.test.fixtures/", "export.jpg");
    }
    private Bitmap readBitmap(String uri) throws Exception {
        try (java.io.InputStream input = getTargetContext().getContentResolver().openInputStream(Uri.parse(uri))) {
            return BitmapFactory.decodeStream(input);
        }
    }
    private void formats() throws Exception {
        for (String format : new String[] {"PNG", "WEBP"}) {
            launch(); click("Compress image");
            picker(Intent.ACTION_OPEN_DOCUMENT, "content://com.example.contentstudio.test.fixtures/noise.jpg", () -> click("Choose image"));
            click(pixels(1280)); click(format); click("Generate preview");
            find("Save new image", 30000, true);
            String extension = format.equals("PNG") ? "png" : "webp";
            String destination = "content://com.example.contentstudio.test.fixtures/export." + extension;
            AccessibilityNodeInfo save = find("Save new image", 10000, true);
            boolean enabled = true;
            for (AccessibilityNodeInfo node = save; node != null; node = node.getParent()) {
                if (!node.isEnabled()) { enabled = false; break; }
            }
            if (!enabled) {
                click("Save anyway"); find("File size did not decrease", 10000, false);
                picker(Intent.ACTION_CREATE_DOCUMENT, destination, () -> click("Save anyway"));
            } else picker(Intent.ACTION_CREATE_DOCUMENT, destination, () -> click("Save new image"));
            find("New image saved", 20000, true);
            BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
            try (java.io.InputStream input = getTargetContext().getContentResolver().openInputStream(Uri.parse(destination))) {
                BitmapFactory.decodeStream(input, null, bounds);
            }
            if (!("image/" + extension).equals(bounds.outMimeType)) throw new AssertionError("Output image MIME mismatch");
            outputActions("image/" + extension); close();
        }
        log.append("PNG/WebP generate/save/open/share: passed\n");
    }
    private void photos() throws Exception {
        launch(); click("Photos to PDF");
        IntentFilter filter = new IntentFilter(Intent.ACTION_OPEN_DOCUMENT);
        filter.addCategory(Intent.CATEGORY_OPENABLE); filter.addCategory(Intent.CATEGORY_DEFAULT); filter.addDataType("*/*");
        android.content.ClipData clip = android.content.ClipData.newRawUri("photos", Uri.parse("content://com.example.contentstudio.test.fixtures/orientation-6.jpg"));
        clip.addItem(new android.content.ClipData.Item(Uri.parse("content://com.example.contentstudio.test.fixtures/alpha.webp")));
        Intent picked = new Intent().addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); picked.setClipData(clip);
        ActivityMonitor monitor = addMonitor(filter, new ActivityResult(Activity.RESULT_OK, picked), true);
        try { click("Choose images"); } finally { removeMonitor(monitor); }
        find("Page 1 / 2", 20000, true); click("Move down"); click("Create PDF");
        find("Save new PDF", 30000, true);
        String destination = "content://com.example.contentstudio.test.pdf/export.pdf";
        picker(Intent.ACTION_CREATE_DOCUMENT, destination, () -> click("Save new PDF"));
        find("New PDF saved", 20000, true);
        try (PdfRenderer renderer = new PdfRenderer(getTargetContext().getContentResolver().openFileDescriptor(Uri.parse(destination), "r"))) {
            if (renderer.getPageCount() != 2) throw new AssertionError("Photo PDF page count");
            try (PdfRenderer.Page page = renderer.openPage(1)) {
                if (page.getWidth() != 595 || page.getHeight() != 842) throw new AssertionError("Photo PDF orientation/order");
                Bitmap rendered = Bitmap.createBitmap(page.getWidth(), page.getHeight(), Bitmap.Config.ARGB_8888);
                try {
                    page.render(rendered, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    int color = rendered.getPixel(160, 210);
                    if (android.graphics.Color.blue(color) <= android.graphics.Color.red(color))
                        throw new AssertionError("Photo PDF reorder/EXIF pixels");
                } finally { rendered.recycle(); }
            }
        }
        outputActions("application/pdf"); close();
        log.append("Photo PDF multiple import/order/A4/save/open/share: passed\n");
    }
    private void video() throws Exception {
        launch(); click("Edit video");
        picker(Intent.ACTION_OPEN_DOCUMENT, "content://com.example.contentstudio.test.videos/audio.mp4", () -> click("Choose video"));
        click("640 px"); click("Generate output");
        find("Save new video", 120000, true);
        String destination = "content://com.example.contentstudio.test.videos/export.mp4";
        picker(Intent.ACTION_CREATE_DOCUMENT, destination, () -> click("Save new video"));
        find("New video saved", 20000, true);
        MediaExtractor extractor = new MediaExtractor();
        try {
            try (android.os.ParcelFileDescriptor descriptor = getTargetContext().getContentResolver().openFileDescriptor(Uri.parse(destination), "r")) {
                extractor.setDataSource(descriptor.getFileDescriptor());
            }
            List<String> mimes = new ArrayList<>();
            for (int i=0; i<extractor.getTrackCount(); i++) mimes.add(extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME));
            if (!mimes.contains("video/avc") || !mimes.contains("audio/mp4a-latm")) throw new AssertionError("Video/audio output tracks");
        } finally { extractor.release(); }
        outputActions("video/mp4"); close(); log.append("Video import/process/export with audio: passed\n");
        guardedCopy("Compress video", "Choose video", "low-bitrate.mp4", "Generate output", "Save new video",
            "content://com.example.contentstudio.test.videos/", "export.mp4");
    }
    private void guardedCopy(String entry, String choose, String input, String process, String save,
            String base, String output) throws Exception {
        byte[] original = readBytes(base + input);
        launch(); click(entry);
        picker(Intent.ACTION_OPEN_DOCUMENT, base + input, () -> click(choose));
        find(input, 20000, true);
        click(process);
        find("Save anyway", 120000, true);
        AccessibilityNodeInfo button = find(save, 120000, true);
        // Compose can expose a text child plus a disabled semantics ancestor, without a Button class.
        boolean disabled = false;
        StringBuilder ancestry = new StringBuilder();
        while (button != null) {
            ancestry.append(button.getClassName()).append(" enabled=").append(button.isEnabled()).append("; ");
            if (!button.isEnabled()) { disabled = true; break; }
            button = button.getParent();
        }
        if (!disabled) throw new AssertionError("Non-saving export must be disabled: " + entry + "; " + ancestry);
        click("Save anyway"); find("File size did not decrease", 10000, false); click("Keep original");
        click("Save anyway");
        java.io.File screenshot = new java.io.File(getTargetContext().getExternalFilesDir(null), "qa/larger-" + input + ".png");
        screenshot.getParentFile().mkdirs();
        Bitmap screen = getUiAutomation().takeScreenshot();
        try (java.io.FileOutputStream stream = new java.io.FileOutputStream(screenshot)) {
            screen.compress(Bitmap.CompressFormat.PNG, 100, stream);
        } finally { screen.recycle(); }
        picker(Intent.ACTION_CREATE_DOCUMENT, base + output, () -> click("Save anyway"));
        find(input.endsWith(".mp4") ? "New video saved" : "New image saved", 20000, true);
        byte[] saved = readBytes(base + output);
        if (saved.length <= original.length) throw new AssertionError("Fixture must actually grow");
        if (!java.util.Arrays.equals(original, readBytes(base + input))) throw new AssertionError("Original changed");
        close(); log.append(entry + " larger-copy guard, keep original, explicit save: passed (source=" +
            original.length + " bytes, copy=" + saved.length + " bytes)\n");
    }
    private byte[] readBytes(String uri) throws Exception {
        try (java.io.InputStream input = getTargetContext().getContentResolver().openInputStream(Uri.parse(uri));
             java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[65536]; int size;
            while ((size = input.read(buffer)) >= 0) output.write(buffer, 0, size);
            return output.toByteArray();
        }
    }
    private void pdf() throws Exception {
        launch(); click("Create presentation"); setText("Project name", "Release PDF"); setText("Heading", "A clear plan");
        // The targeted output diagnostic uses the existing theme; the full suite checks switching.
        if (!pdfOnly) { click("Classic"); click("Ocean"); }
        click("Preview PDF");
        find("Save new PDF", 20000, true);
        String destination = "content://com.example.contentstudio.test.pdf/export.pdf";
        picker(Intent.ACTION_CREATE_DOCUMENT, destination, () -> click("Save new PDF"));
        find("New PDF saved", 20000, true);
        try (PdfRenderer renderer = new PdfRenderer(getTargetContext().getContentResolver().openFileDescriptor(Uri.parse(destination), "r"))) {
            if (renderer.getPageCount() != 1) throw new AssertionError("PDF page count");
        }
        outputActions("application/pdf"); close(); log.append("Presentation theme/preview/PDF export: passed\n");
    }
    private void outputActions(String mime) throws Exception {
        IntentFilter open = new IntentFilter(Intent.ACTION_VIEW); open.addDataType(mime);
        ActivityMonitor viewer = addMonitor(open, new ActivityResult(Activity.RESULT_CANCELED, null), true);
        try { click("Open file"); awaitMonitor(viewer); }
        finally { removeMonitor(viewer); }
        IntentFilter share = new IntentFilter(Intent.ACTION_CHOOSER);
        ActivityMonitor chooser = addMonitor(share, new ActivityResult(Activity.RESULT_CANCELED, null), true);
        try { click("Share file"); awaitMonitor(chooser); }
        finally { removeMonitor(chooser); }
        java.io.File root = new java.io.File(getTargetContext().getCacheDir(), "shared-outputs");
        java.io.File snapshot = null;
        for (java.io.File folder : root.listFiles()) for (java.io.File candidate : folder.listFiles())
            if (!candidate.getName().endsWith(".part") && (snapshot == null || candidate.lastModified() > snapshot.lastModified())) snapshot = candidate;
        if (snapshot == null || snapshot.length() == 0) throw new AssertionError("Missing output snapshot");
        Uri uri = new Uri.Builder().scheme("content").authority("studio.birlevo.android.outputs")
            .appendPath("shared").appendPath(snapshot.getParentFile().getName()).appendPath(snapshot.getName()).build();
        byte[] expected = readBytes(uri.toString());
        String received = "content://com.example.contentstudio.test.fixtures/export.jpg";
        getTargetContext().getContentResolver().delete(Uri.parse(received), null, null);
        Intent receiver = new Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
            .setClassName(getContext().getPackageName(), "com.example.contentstudio.OutputReceiverActivity")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        receiver.setClipData(android.content.ClipData.newRawUri("output", uri));
        getTargetContext().startActivity(receiver);
        long deadline = SystemClock.uptimeMillis() + 15000;
        boolean equal = false;
        while (SystemClock.uptimeMillis() < deadline) {
            try { equal = java.util.Arrays.equals(expected, readBytes(received)); } catch (Exception ignored) { }
            if (equal) break;
            SystemClock.sleep(100);
        }
        if (!equal) throw new AssertionError("Cross-application read grant or output bytes changed: " + mime);
        waitForIdleSync();
        find("Share file", 10000, true);
        java.io.File screenshot = new java.io.File(getTargetContext().getExternalFilesDir(null), "qa/output-" + mime.replace('/', '-') + ".png");
        screenshot.getParentFile().mkdirs();
        Bitmap screen = getUiAutomation().takeScreenshot();
        try (java.io.FileOutputStream stream = new java.io.FileOutputStream(screenshot)) {
            screen.compress(Bitmap.CompressFormat.PNG, 100, stream);
        } finally { screen.recycle(); }
        log.append("Open/share MIME and cross-application snapshot read: " + mime + " passed\n");
    }
    private void awaitMonitor(ActivityMonitor monitor) {
        long deadline = SystemClock.uptimeMillis() + 20000;
        while (monitor.getHits() == 0 && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100);
        if (monitor.getHits() != 1) throw new AssertionError("Missing or repeated output intent");
    }
    private void transfer() throws Exception {
        launch(); click("Compress image");
        picker(Intent.ACTION_OPEN_DOCUMENT, "content://com.example.contentstudio.test.fixtures/noise.jpg", () -> click("Choose image"));
        click(pixels(1280)); click("Generate preview"); find("Save new image", 30000, true);
        click("Add to presentation"); click("New presentation"); find("Heading", 20000, true);
        setText("Project name", "Transferred image"); click("Save"); find("Create presentation", 15000, true);
        close();
        // Only this QA app's disposable working copies are removed. Owned project images stay.
        erase(new java.io.File(getTargetContext().getCacheDir(), "shared-outputs"));
        erase(new java.io.File(getTargetContext().getNoBackupFilesDir(), "image-compression"));
        reopenTransferred();
        log.append("Compressed image -> owned presentation -> restart/cache cleanup -> PDF: passed\n");
    }
    private void reopenTransferred() throws Exception {
        launch(); click("Transferred image"); click("Preview PDF");
        find("Save new PDF", 20000, true);
        String destination = "content://com.example.contentstudio.test.pdf/export.pdf";
        picker(Intent.ACTION_CREATE_DOCUMENT, destination, () -> click("Save new PDF"));
        find("New PDF saved", 20000, true);
        try (PdfRenderer renderer = new PdfRenderer(getTargetContext().getContentResolver().openFileDescriptor(Uri.parse(destination), "r"))) {
            if (renderer.getPageCount() != 1) throw new AssertionError("Transferred PDF pages");
        }
        close(); log.append("Stored presentation image/PDF: passed\n");
    }
    private void erase(java.io.File file) {
        if (file.isDirectory()) for (java.io.File child : file.listFiles()) erase(child);
        file.delete();
    }
    private void click(String text) {
        long deadline = SystemClock.uptimeMillis() + 20000;
        // A lazy row can replace its accessibility node after scrolling; reacquire it until ready.
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo node = find(text, Math.max(1, deadline - SystemClock.uptimeMillis()), true);
            while (node != null && !node.isClickable()) node = node.getParent();
            if (node != null && node.refresh() && node.isEnabled()) {
                // A large-font card can be partially visible while its center is clipped. Invoke
                // its platform accessibility click instead of tapping a moving/clipped center.
                if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    waitForIdleSync(); SystemClock.sleep(250); return;
                }
                android.graphics.Rect bounds = new android.graphics.Rect(); node.getBoundsInScreen(bounds);
                AccessibilityNodeInfo root = activeRoot();
                android.graphics.Rect viewport = new android.graphics.Rect();
                if (root != null) root.getBoundsInScreen(viewport);
                if (bounds.intersect(viewport) && bounds.width() > 0 && bounds.height() > 0) {
                    shellInput("input touchscreen tap " + bounds.centerX() + " " + bounds.centerY());
                    waitForIdleSync(); SystemClock.sleep(250); return;
                }
            }
            waitForIdleSync(); SystemClock.sleep(180);
        }
        throw new AssertionError("Cannot click: " + text);
    }
    private void setText(String label, String text) {
        Bundle args = new Bundle(); args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        long deadline = SystemClock.uptimeMillis() + 20000;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo node = find(label, Math.max(1, deadline - SystemClock.uptimeMillis()), true);
            while (node != null && (node.getActions() & AccessibilityNodeInfo.ACTION_SET_TEXT) == 0) node = node.getParent();
            if (node != null && node.refresh()) {
                // A visible floating label can belong to an input clipped by a large-font viewport.
                node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId());
                waitForIdleSync();
            }
            if (node != null && node.refresh() && node.isEnabled() && node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                // Android reports dispatch before Compose has applied the edit. Verify the
                // actual value rather than accepting a stale "Saved" status from the prior revision.
                waitForIdleSync(); find(text, 10000, true); return;
            }
            AccessibilityNodeInfo root = activeRoot();
            if (root != null) scroll(root, true);
            waitForIdleSync(); SystemClock.sleep(180);
        }
        throw new AssertionError("Cannot edit: " + label);
    }
    private AccessibilityNodeInfo find(String text, long timeout, boolean scroll) {
        long end = SystemClock.uptimeMillis() + timeout;
        boolean forward = true;
        int unchanged = 0;
        while (SystemClock.uptimeMillis() < end) {
            AccessibilityNodeInfo root = activeRoot();
            if (root != null) {
                // Compose menus are separate windows and need not be the active accessibility root.
                List<android.view.accessibility.AccessibilityWindowInfo> windows = new ArrayList<>(getUiAutomation().getWindows());
                windows.sort((a, b) -> Integer.compare(b.getLayer(), a.getLayer()));
                AccessibilityNodeInfo matching;
                for (android.view.accessibility.AccessibilityWindowInfo window : windows) {
                    AccessibilityNodeInfo windowRoot = window.getRoot();
                    matching = windowRoot == null ? null : textNode(windowRoot, text);
                    if (matching != null) return matching;
                }
                matching = textNode(root, text);
                if (matching != null) return matching;
                if (scroll) {
                    String before = visibleContent(root);
                    boolean moved = scroll(root, forward);
                    AccessibilityNodeInfo after = activeRoot();
                    unchanged = after != null && before.equals(visibleContent(after)) ? unchanged + 1 : 0;
                    // Large-font video screens can span far more than eight half-screen swipes.
                    // Reverse only at an unchanged edge rather than at an arbitrary swipe count.
                    if (!moved || unchanged >= 2) { forward = !forward; unchanged = 0; }
                }
            }
            SystemClock.sleep(180);
        }
        throw new AssertionError("UI text not found: " + text);
    }
    private AccessibilityNodeInfo activeRoot() {
        // Compose replaces semantics between screens. Do not search a cached prior activity tree.
        if (android.os.Build.VERSION.SDK_INT >= 33) getUiAutomation().clearCache();
        return getUiAutomation().getRootInActiveWindow();
    }
    private String visibleContent(AccessibilityNodeInfo node) {
        StringBuilder signature = new StringBuilder();
        if (node.isVisibleToUser() && node.getText() != null) {
            android.graphics.Rect bounds = new android.graphics.Rect(); node.getBoundsInScreen(bounds);
            signature.append(node.getText()).append(bounds);
        }
        for (int i=0; i<node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) signature.append(visibleContent(child));
        }
        return signature.toString();
    }
    private AccessibilityNodeInfo textNode(AccessibilityNodeInfo node, String text) {
        String value = node.getText() == null ? "" : node.getText().toString();
        for (String line : value.split("\n")) if (text.equals(line) && node.isVisibleToUser()) return node;
        for (int i=0; i<node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo found = textNode(child, text);
                if (found != null) return found;
            }
        }
        return null;
    }
    private boolean scroll(AccessibilityNodeInfo node, boolean forward) {
        // Never swipe the launcher, notification shade or another system window during a transition.
        if (!"studio.birlevo.android".contentEquals(node.getPackageName() == null ? "" : node.getPackageName())) return false;
        AccessibilityNodeInfo target = largestScrollable(node);
        android.graphics.Rect bounds = new android.graphics.Rect();
        (target == null ? node : target).getBoundsInScreen(bounds);
        android.graphics.Rect viewport = new android.graphics.Rect(); node.getBoundsInScreen(viewport);
        if (!bounds.intersect(viewport)) return false;
        if (bounds.width() < 100 || bounds.height() < 100) return false;
        int x = bounds.centerX();
        int start = bounds.top + (int)(bounds.height() * (forward ? .78f : .28f));
        int end = bounds.top + (int)(bounds.height() * (forward ? .28f : .78f));
        // Use Android's input command just like the manually verified adb swipe. In contrast,
        // synthesized accessibility actions can report success on a replaced Compose node.
        shellInput("input touchscreen swipe " + x + " " + start + " " + x + " " + end + " 350");
        waitForIdleSync();
        return true;
    }
    private void shellInput(String command) {
        try (android.os.ParcelFileDescriptor pipe = getUiAutomation().executeShellCommand(command);
             java.io.InputStream stream = new android.os.ParcelFileDescriptor.AutoCloseInputStream(pipe)) {
            byte[] buffer = new byte[1024];
            while (stream.read(buffer) != -1) { }
        } catch (java.io.IOException error) { throw new AssertionError("QA input command failed", error); }
    }
    private AccessibilityNodeInfo largestScrollable(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo best = node.isScrollable() && node.isVisibleToUser() ? node : null;
        android.graphics.Rect bestBounds = new android.graphics.Rect();
        if (best != null) best.getBoundsInScreen(bestBounds);
        for (int i=0; i<node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo candidate = child == null ? null : largestScrollable(child);
            if (candidate != null) {
                android.graphics.Rect bounds = new android.graphics.Rect(); candidate.getBoundsInScreen(bounds);
                if (best == null || bounds.width() * bounds.height() > bestBounds.width() * bestBounds.height()) {
                    best = candidate; bestBounds = bounds;
                }
            }
        }
        return best;
    }
}
