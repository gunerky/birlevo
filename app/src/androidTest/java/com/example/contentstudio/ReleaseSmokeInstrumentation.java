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
    private String expectedLameVersion;
    private boolean workspace;
    private String activePanel;
    private String currentTool;
    private boolean reuseReview;
    private final StringBuilder log = new StringBuilder();
    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        restoreOnly = args != null && "true".equals(args.getString("restoreOnly"));
        pdfOnly = args != null && "true".equals(args.getString("pdfOnly"));
        workflow = args == null ? null : args.getString("workflow");
        reuseReview = args != null && "true".equals(args.getString("reuseReview"));
        expectedLameVersion = args == null ? "4.0" : args.getString("lameVersion", "4.0");
        try { workspace = getTargetContext().getPackageManager().getPackageInfo("studio.birlevo.android", 0).versionCode >= 12; }
        catch (Exception error) { throw new IllegalStateException(error); }
        start();
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            android.accessibilityservice.AccessibilityServiceInfo service = getUiAutomation().getServiceInfo();
            service.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                | android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            getUiAutomation().setServiceInfo(service);
            if (android.os.Build.VERSION.SDK_INT >= 33) getTargetContext().getSystemService(LocaleManager.class)
                .setApplicationLocales(LocaleList.forLanguageTags("en-US"));
            // Standalone media fixtures with fresh QA data do not make an advertising age choice.
            // Advertising workflows exercise the real first-run choice themselves.
            if (workflow != null && !workflow.startsWith("ad-")) {
                android.content.SharedPreferences preferences = getTargetContext().getSharedPreferences("ad_privacy_preferences", 0);
                if (!preferences.contains("age_group") && !preferences.edit().putString("age_group", "UNDISCLOSED").commit())
                    throw new AssertionError("Cannot preserve non-advertising QA audience");
            }
            if (workflow != null) {
                switch (workflow) {
                    case "ad-isolation": adIsolation(); break;
                    case "ad-test-banner": sampleAdvertising("ADULT", true); break;
                    case "ad-test-protected": sampleAdvertising("AGE_13_15", false); break;
                    case "ad-test-blocked": blockedSampleAdvertising(); break;
                    case "ad-test-media": sampleAdvertising("ADULT", false); image(); video(); break;
                    case "office": office(); break;
                    case "image": image(); break;
                    case "video": video(); break;
                    case "pdf": pdf(); break;
                    case "transfer": transfer(); break;
                    case "formats": formats(); break;
                    case "photos": photos(); break;
                    case "support": support(); break;
                    case "video-targets": videoTargets(); break;
                    case "audio": audio(); break;
                    case "documents": documents(); break;
                    case "reader": reader(); break;
                    case "pages": pages(); break;
                    case "fgs-demo": foregroundDemo(); break;
                    case "expanded-audio": expandedAudio(); break;
                    case "video-edit": videoEdit(); break;
                    case "mp3-source": mp3Source(); break;
                    case "native-mp3": nativeMp3(); break;
                    case "pptx": pptx(); break;
                    case "slideshow": slideshow(); break;
                    case "presentation-mode": presentationMode(); break;
                    default: throw new IllegalArgumentException("Unknown workflow");
                }
                result.putString("stream", log + "\nOK (1 " + workflow + " workflow)\n");
            } else if (pdfOnly) {
                pdf(); result.putString("stream", log + "\nOK (1 PDF workflow)\n");
            } else if (restoreOnly) {
                reopenTransferred(); result.putString("stream", log + "\nOK (1 restored project workflow)\n");
            } else {
                progress("image"); image(); progress("video"); video();
                // Check rejected replacements immediately after a completed/restorable video.
                boolean compatibility = getTargetContext().getPackageManager().getPackageInfo("studio.birlevo.android", 0).versionCode >= 9;
                if (compatibility) { progress("support"); support(); }
                progress("PDF"); pdf(); progress("transfer"); transfer();
                boolean extended = getTargetContext().getPackageManager().getPackageInfo("studio.birlevo.android", 0).versionCode >= 8;
                if (extended) { progress("formats"); formats(); progress("photos"); photos(); }
                boolean media = getTargetContext().getPackageManager().getPackageInfo("studio.birlevo.android", 0).versionCode >= 10;
                if (media) { progress("video-targets"); videoTargets(); progress("audio"); audio(); progress("documents"); documents(); progress("reader"); reader(); progress("pages"); pages(); }
                boolean editing = getTargetContext().getPackageManager().getPackageInfo("studio.birlevo.android", 0).versionCode >= 11;
                if (editing) { progress("expanded-audio"); expandedAudio(); progress("video-edit"); videoEdit(); progress("mp3-source"); mp3Source(); }
                if (workspace) { progress("pptx"); pptx(); progress("slideshow"); slideshow(); progress("presentation-mode"); presentationMode(); }
                result.putString("stream", log + "\nOK (" + (workspace ? 18 : editing ? 15 : media ? 12 : compatibility ? 7 : extended ? 6 : 4) + " release workflows)\n");
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
    private void adIsolation() {
        android.content.SharedPreferences preferences = getTargetContext()
            .getSharedPreferences("ad_privacy_preferences", 0);
        String previous = preferences.getString("age_group", null);
        try {
            for (String group : new String[] {"UNDISCLOSED", "UNDER_13", "AGE_13_15", "AGE_16_17", "ADULT"}) {
                if (!preferences.edit().putString("age_group", group).commit())
                    throw new AssertionError("Could not seed QA audience");
                launch();
                SystemClock.sleep(2000);
                // These strings may be removed with their unreachable release UI by R8.
                if (visible("Ad privacy") || visible("Age group"))
                    throw new AssertionError("Disabled release exposed advertising UI: " + group);
                for (String database : getTargetContext().databaseList()) {
                    if (database.startsWith("androidx.work.workdb"))
                        throw new AssertionError("Disabled release started WorkManager: " + group);
                }
                log.append("Disabled release: no ad entry, age prompt or WorkManager database for ")
                    .append(group).append("\n");
                close();
            }
        } finally {
            android.content.SharedPreferences.Editor editor = preferences.edit();
            if (previous == null) editor.remove("age_group"); else editor.putString("age_group", previous);
            if (!editor.commit()) throw new AssertionError("Could not restore QA audience");
        }
    }
    private AccessibilityNodeInfo resourceNode(AccessibilityNodeInfo node, String id) {
        if (node == null) return null;
        if (node.isVisibleToUser() && id.equals(node.getViewIdResourceName())) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo hit = resourceNode(node.getChild(i), id);
            if (hit != null) return hit;
        }
        return null;
    }
    private boolean sampleBannerVisible() {
        if (resourceNode(activeRoot(), "home-test-banner-visible") != null) return true;
        // AndroidView interop need not expose Compose resource tags in accessibility.
        // Home has no other embedded WebView. Its banner parent starts INVISIBLE and
        // becomes visible only in GoogleBannerHandle.reveal after the SDK load callback.
        java.util.concurrent.atomic.AtomicBoolean visible = new java.util.concurrent.atomic.AtomicBoolean();
        runOnMainSync(() -> visible.set(visibleBannerWebView(activity.getWindow().getDecorView())));
        return visible.get();
    }
    private boolean visibleBannerWebView(android.view.View view) {
        android.graphics.Rect bounds = new android.graphics.Rect();
        if (view instanceof android.webkit.WebView && view.isShown() && view.getGlobalVisibleRect(bounds)
            && bounds.width() > 0 && bounds.height() > 0) return true;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++)
                if (visibleBannerWebView(group.getChildAt(i))) return true;
        }
        return false;
    }
    private void clearAdvertisingQaState() {
        for (String name : new String[] {"ad_privacy_preferences", "ad_test_attempts"}) {
            android.content.SharedPreferences preferences = getTargetContext().getSharedPreferences(name, 0);
            if (!preferences.edit().clear().commit()) throw new AssertionError("Cannot clear dedicated QA ad state: " + name);
        }
    }
    private void launchAdvertising() {
        activePanel = null; currentTool = null;
        activity = startActivitySync(new Intent().setClassName("studio.birlevo.android",
            "com.example.contentstudio.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        waitForIdleSync();
    }
    private void sampleAdvertising(String audience, boolean checkLifecycle) throws Exception {
        clearAdvertisingQaState();
        launchAdvertising();
        find(string("ad_age_title"), 15000, true);
        capture("sample-age-choices-" + audience.toLowerCase());
        click(string(audience.equals("ADULT") ? "ad_age_adult" : "ad_age_13_15"));
        long deadline = SystemClock.uptimeMillis() + 90000;
        boolean formHandled = false;
        while (SystemClock.uptimeMillis() < deadline && !sampleBannerVisible()) {
            if (visible("Consent")) {
                if (!audience.equals("ADULT")) throw new AssertionError("Protected audience received an adult form");
                capture("sample-provider-form"); click("Consent"); formHandled = true;
            }
            AccessibilityNodeInfo root = activeRoot();
            if (root != null) scroll(root, true);
            waitForIdleSync(); SystemClock.sleep(300);
        }
        if (!sampleBannerVisible()) throw new AssertionError("No successfully loaded sample banner: " + audience);
        find(string("ad_test_banner"), 5000, true);
        capture("sample-banner-" + audience.toLowerCase());
        log.append("Signed sample banner loaded and visible for ").append(audience)
            .append("; actual regional form handled=").append(formHandled).append("\n");
        click(string("ad_privacy"));
        if (sampleBannerVisible()) throw new AssertionError("Banner remained while preferences opened");
        click(string("ad_age_undisclosed"));
        SystemClock.sleep(1000);
        if (sampleBannerVisible()) throw new AssertionError("Banner remained after age withdrawal");
        close();
        if (checkLifecycle) {
            launch(); SystemClock.sleep(1500);
            if (sampleBannerVisible()) throw new AssertionError("Banner returned for undisclosed audience");
            capture("sample-withdrawn-home"); close();
        }
    }
    private void blockedSampleAdvertising() {
        for (String audience : new String[] {"UNDER_13", "UNDISCLOSED"}) {
            clearAdvertisingQaState();
            launchAdvertising(); find(string("ad_age_title"), 15000, true);
            click(string(audience.equals("UNDER_13") ? "ad_age_under_13" : "ad_age_undisclosed"));
            SystemClock.sleep(1500);
            if (sampleBannerVisible() || visible("Consent")) throw new AssertionError("Blocked audience received advertising");
            for (String database : getTargetContext().databaseList())
                if (database.startsWith("androidx.work.workdb")) throw new AssertionError("Blocked cold start initialized ad work");
            click("Compress image"); find("Choose image", 15000, true); close();
            log.append("Blocked signed sample audience keeps tools and no ad work: ").append(audience).append("\n");
        }
    }
    private void launch() {
        activePanel = null; currentTool = null;
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
        boolean expanded = getTargetContext().getPackageManager().getPackageInfo("studio.birlevo.android", 0).versionCode >= 10;
        find(string("files_input") + (expanded ? ": JPEG / PNG / WebP / BMP / HEIC / HEIF / AVIF" : ": JPEG / PNG / WebP"), 15000, true);
        find(string("files_output") + (expanded ? ": JPEG / PNG / WebP / BMP" : ": JPEG / PNG / WebP"), 15000, true);
        capture("supported-files");
        find(string("files_output") + ": PDF", 15000, true);
        find(string("files_pdf_creation"), 15000, true);
        find(string("files_pending"), 15000, true); click(string("close"));
        click("Compress image");
        picker(Intent.ACTION_OPEN_DOCUMENT, "content://com.example.contentstudio.test.fixtures/wrong-type.jpg", () -> click("Choose image"));
        find(string("error_image_format"), 15000, true); capture("unsupported-image"); close();
        String[][] videos = expanded ? new String[][] {{"phone-mpeg4.mp4", "error_video_codec"}, {"hdr.mp4", "error_video_hdr"}, {"tracks.mp4", "error_video_tracks"}}
            : new String[][] {{"hevc.mp4", "error_video_codec"}, {"hdr.mp4", "error_video_hdr"}, {"tracks.mp4", "error_video_tracks"}, {"container.mkv", "error_video_container"}};
        for (String[] video : videos) {
            launch(); click("Edit video");
            picker(Intent.ACTION_OPEN_DOCUMENT, "content://com.example.contentstudio.test.videos/" + video[0], () -> click("Choose video"));
            // A restored result leaves the new canvas/speed controls above the error.
            find(string(video[1]), 30000, true); close();
        }
        log.append("Supported files help and actual unsupported format/HDR/multiple-track errors: passed\n");
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
        try { click.run(); awaitMonitor(monitor); waitForIdleSync(); } finally { removeMonitor(monitor); }
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
        boolean expanded = getTargetContext().getPackageManager().getPackageInfo("studio.birlevo.android", 0).versionCode >= 10;
        for (String format : expanded ? new String[] {"PNG", "WEBP", "BMP"} : new String[] {"PNG", "WEBP"}) {
            launch(); click("Compress image");
            picker(Intent.ACTION_OPEN_DOCUMENT, "content://com.example.contentstudio.test.fixtures/noise.jpg", () -> click("Choose image"));
            click(pixels(1280)); click(format); click("Generate preview");
            find("Save new image", 30000, true);
            String extension = format.toLowerCase(java.util.Locale.ROOT);
            String destination = "content://com.example.contentstudio.test.fixtures/export." + extension;
            AccessibilityNodeInfo save = find("Save new image", 10000, true);
            boolean enabled = true;
            for (AccessibilityNodeInfo node = save; node != null; node = node.getParent()) {
                if (!node.isEnabled()) { enabled = false; break; }
            }
            if (workspace) enabled = !saveCurrent("Save new image", destination);
            else if (!enabled) {
                click("Save anyway"); find("File size did not decrease", 10000, false);
                picker(Intent.ACTION_CREATE_DOCUMENT, destination, () -> click("Save anyway"));
            } else picker(Intent.ACTION_CREATE_DOCUMENT, destination, () -> click("Save new image"));
            find("New image saved", 20000, true);
            BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
            try (java.io.InputStream input = getTargetContext().getContentResolver().openInputStream(Uri.parse(destination))) {
                BitmapFactory.decodeStream(input, null, bounds);
            }
            if (!("image/" + extension).equals(bounds.outMimeType)) throw new AssertionError("Output image MIME mismatch");
            if (!enabled) { click("Open file"); find("File size did not decrease", 10000, false); click("Keep original"); }
            outputActions("image/" + extension, !enabled); close();
        }
        log.append("Image targets generate/save/open/share: passed\n");
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
        guardedCopy("Edit video", "Choose video", "low-bitrate.mp4", "Generate output", "Save new video",
            "content://com.example.contentstudio.test.videos/", "export.mp4");
    }
    private boolean saveCurrent(String label, String destination) throws Exception {
        if (workspace) {
            long deadline = SystemClock.uptimeMillis() + 120000;
            while (!visible(label) && !visible("Save converted copy") && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(150);
            if (visible("Save converted copy")) {
                click("Save converted copy"); find("File size did not decrease", 10000, false);
                picker(Intent.ACTION_CREATE_DOCUMENT, destination, () -> click("Save anyway")); return true;
            }
            picker(Intent.ACTION_CREATE_DOCUMENT, destination, () -> click(label)); return false;
        }
        AccessibilityNodeInfo button = find(label, 120000, true);
        boolean approval = false;
        for (AccessibilityNodeInfo node = button; node != null; node = node.getParent()) {
            if (!node.isEnabled()) { approval = true; break; }
        }
        if (approval) {
            click("Save anyway"); find("File size did not decrease", 10000, false);
            picker(Intent.ACTION_CREATE_DOCUMENT, destination, () -> click("Save anyway"));
        } else picker(Intent.ACTION_CREATE_DOCUMENT, destination, () -> click(label));
        return approval;
    }
    private void tracks(String destination, String video, String audio) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        try (android.os.ParcelFileDescriptor descriptor = getTargetContext().getContentResolver().openFileDescriptor(Uri.parse(destination), "r")) {
            extractor.setDataSource(descriptor.getFileDescriptor());
            List<String> types = new ArrayList<>();
            for (int i = 0; i < extractor.getTrackCount(); i++) types.add(extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME));
            // Android can expose decoded PCM for native FLAC; the caller also checks fLaC bytes.
            boolean sound = types.contains(audio) || ("audio/flac".equals(audio) && types.contains("audio/raw"));
            if (types.size() != (video == null ? 1 : 2) || (video != null && !types.contains(video)) || !sound)
                throw new AssertionError("Output tracks: " + types);
        } finally { extractor.release(); }
    }
    private void videoTargets() throws Exception {
        for (boolean webm : new boolean[] {false, true}) {
            launch(); click("Edit video");
            picker(Intent.ACTION_OPEN_DOCUMENT, "content://com.example.contentstudio.test.videos/audio.mp4", () -> click("Choose video"));
            click(webm ? "WebM · VP8" : "MP4 · HEVC"); click(pixels(640)); click("Generate output");
            String destination = "content://com.example.contentstudio.test.videos/export." + (webm ? "webm" : "mp4");
            boolean approval = saveCurrent("Save new video", destination);
            find("New video saved", 20000, true);
            tracks(destination, webm ? "video/x-vnd.on2.vp8" : "video/hevc", webm ? "audio/opus" : "audio/mp4a-latm");
            capture(webm ? "webm-target" : "hevc-target");
            outputActions(webm ? "video/webm" : "video/mp4", approval);
            if (webm) {
                click("Audio tools"); if (approval) click("Continue");
                click("M4A"); click("Create audio");
                String sound = "content://com.example.contentstudio.test.audio/export.m4a";
                saveCurrent("Save audio", sound); find("New audio saved", 30000, true);
                tracks(sound, null, "audio/mp4a-latm");
            }
            close();
        }
        log.append("HEVC/WebM exact codecs, save/open/share and WebM owned audio handoff: passed\n");
    }
    private void foregroundDemo() throws Exception {
        launch(); SystemClock.sleep(2500); click("Edit video"); SystemClock.sleep(1500);
        picker(Intent.ACTION_OPEN_DOCUMENT, "content://com.example.contentstudio.test.videos/demo.mp4", () -> click("Choose video"));
        find("demo.mp4", 30000, true); SystemClock.sleep(2000);
        click("MP4 · HEVC"); click("High quality"); click(pixels(1280));
        SystemClock.sleep(2500); click("Generate output"); SystemClock.sleep(3000);
        android.app.ActivityManager manager = getTargetContext().getSystemService(android.app.ActivityManager.class);
        boolean foreground = false;
        for (android.app.ActivityManager.RunningServiceInfo service : manager.getRunningServices(100))
            if (service.foreground && getTargetContext().getPackageName().equals(service.service.getPackageName())) foreground = true;
        if (!foreground) throw new AssertionError("No real foreground processing service");
        shellInput("input keyevent KEYCODE_HOME"); SystemClock.sleep(3000);
        shellInput("cmd statusbar expand-notifications"); SystemClock.sleep(3500);
        // Notification actions may be collapsed. Expand the application's card if needed.
        AccessibilityNodeInfo cancel = textNode(activeRoot(), "Cancel", true);
        if (cancel == null) {
            AccessibilityNodeInfo heading = find("Compress video", 15000, false);
            AccessibilityNodeInfo expand = null;
            for (AccessibilityNodeInfo parent = heading; parent != null && expand == null; parent = parent.getParent())
                expand = notificationExpander(parent);
            if (expand == null || !expand.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                throw new AssertionError("Cannot expand processing notification");
        }
        SystemClock.sleep(2000); click("Cancel"); SystemClock.sleep(2000);
        shellInput("cmd statusbar collapse");
        shellInput("am start -n studio.birlevo.android/com.example.contentstudio.MainActivity");
        find("Generate output", 20000, true); SystemClock.sleep(3500);
        for (android.app.ActivityManager.RunningServiceInfo service : manager.getRunningServices(100))
            if (service.foreground && getTargetContext().getPackageName().equals(service.service.getPackageName()))
                throw new AssertionError("Cancelled foreground processing still active");
        log.append("Real 0.1.9 user-started HEVC task, foreground service, background notification and notification cancellation: passed\n");
        close();
    }
    private AccessibilityNodeInfo notificationExpander(AccessibilityNodeInfo node) {
        String id = node.getViewIdResourceName();
        String description = node.getContentDescription() == null ? "" : node.getContentDescription().toString();
        if (node.isVisibleToUser() && node.isClickable() &&
                ((id != null && id.endsWith("/expand_button")) || description.equals("Expand"))) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo found = child == null ? null : notificationExpander(child);
            if (found != null) return found;
        }
        return null;
    }

    private long duration(String uri) throws Exception {
        android.media.MediaMetadataRetriever reader = new android.media.MediaMetadataRetriever();
        try { reader.setDataSource(getTargetContext(), Uri.parse(uri)); return Long.parseLong(reader.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)); }
        finally { reader.release(); }
    }
    private void nativeMp3() throws Exception {
        // Only the explicitly preserved JNI ABI is reflected; no obfuscated implementation names.
        Class<?> type = Class.forName("com.example.contentstudio.audio.LameEncoder", true, getTargetContext().getClassLoader());
        java.lang.reflect.Method version = type.getDeclaredMethod("version"); version.setAccessible(true);
        String actual = (String) version.invoke(null);
        if (!expectedLameVersion.equals(actual)) throw new AssertionError("Native replacement version: " + actual);
        java.lang.reflect.Constructor<?> constructor = type.getDeclaredConstructor(int.class, int.class, int.class); constructor.setAccessible(true);
        Object encoder = constructor.newInstance(48000, 2, 128000);
        java.lang.reflect.Method encode = type.getDeclaredMethod("encode", short[].class, int.class, byte[].class);
        java.lang.reflect.Method flush = type.getDeclaredMethod("flush", byte[].class);
        java.lang.reflect.Method tag = type.getDeclaredMethod("tag", byte[].class);
        java.lang.reflect.Method closeEncoder = type.getDeclaredMethod("close");
        for (java.lang.reflect.Method m : new java.lang.reflect.Method[] {encode,flush,tag,closeEncoder}) m.setAccessible(true);
        java.io.ByteArrayOutputStream stream = new java.io.ByteArrayOutputStream(); byte[] buffer = new byte[12800];
        try {
            for (int start = 0; start < 48000; start += 4096) {
                int frames = Math.min(4096, 48000 - start); short[] pcm = new short[frames * 2];
                for (int i = 0; i < frames; i++) pcm[i*2] = pcm[i*2+1] = (short) (Math.sin((start+i) * 2 * Math.PI * 440 / 48000) * 12000);
                int size = (Integer) encode.invoke(encoder, pcm, frames, buffer); stream.write(buffer,0,size);
            }
            int size = (Integer) flush.invoke(encoder,(Object)buffer); stream.write(buffer,0,size);
            byte[] audio = stream.toByteArray(); int tagSize = (Integer) tag.invoke(encoder,(Object)buffer);
            System.arraycopy(buffer,0,audio,0,tagSize);
            java.io.File file = new java.io.File(getTargetContext().getExternalFilesDir(null),"qa/native-abi.mp3"); file.getParentFile().mkdirs();
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) { out.write(audio); }
            MediaExtractor extractor = new MediaExtractor();
            try { extractor.setDataSource(file.getPath()); MediaFormat track = extractor.getTrackFormat(0);
                if (extractor.getTrackCount()!=1 || !"audio/mpeg".equals(track.getString(MediaFormat.KEY_MIME)) || track.getInteger(MediaFormat.KEY_SAMPLE_RATE)!=48000 || track.getInteger(MediaFormat.KEY_CHANNEL_COUNT)!=2)
                    throw new AssertionError("Native MP3 ABI result profile");
            } finally { extractor.release(); }
        } finally { closeEncoder.invoke(encoder); closeEncoder.invoke(encoder); }
        log.append("Preserved JNI ABI, real MP3 encoding, exact rate/channels and native version: ").append(actual).append(" passed\n");
    }
    private void expandedAudio() throws Exception {
        nativeMp3();
        String sourceBase = "content://com.example.contentstudio.test.audio/";
        byte[] first = readBytes(sourceBase+"tone.wav"), second=readBytes(sourceBase+"mono44100.wav");
        for (String target : new String[] {"MP3","OGG","FLAC"}) {
            launch(); click("Audio tools");
            picker(Intent.ACTION_OPEN_DOCUMENT, sourceBase+"tone.wav", () -> click("Choose audio or video"));
            click("Trim range"); setText("Start (seconds)","0.2"); setText("End (seconds)","0.8"); click("Save");
            picker(Intent.ACTION_OPEN_DOCUMENT, sourceBase+"mono44100.wav", () -> click("Add second source"));
            find("2. mono44100.wav",20000,true); click("Swap order"); click("48000 Hz"); click("2 ch");
            click(target.equals("OGG") ? "OGG · Opus" : target); click("Create audio");
            String destination=sourceBase+"export."+target.toLowerCase(java.util.Locale.ROOT);
            boolean approval=saveCurrent("Save audio",destination); find("New audio saved",30000,true);
            tracks(destination,null,target.equals("MP3")?"audio/mpeg":target.equals("OGG")?"audio/opus":"audio/flac");
            if (target.equals("FLAC")) {
                byte[] encoded=readBytes(destination);
                if(encoded.length<42||encoded[0]!='f'||encoded[1]!='L'||encoded[2]!='a'||encoded[3]!='C') throw new AssertionError("Native FLAC container required");
            }
            if (Math.abs(duration(destination) - (duration(sourceBase+"mono44100.wav")+600))>150) throw new AssertionError("Joined trimmed audio duration");
            java.io.File file=new java.io.File(getTargetContext().getExternalFilesDir(null),"qa/release-joined."+target.toLowerCase(java.util.Locale.ROOT));file.getParentFile().mkdirs();
            try(java.io.FileOutputStream out=new java.io.FileOutputStream(file)){out.write(readBytes(destination));}
            outputActions(target.equals("MP3")?"audio/mpeg":target.equals("OGG")?"audio/ogg":"audio/flac",approval); close();
        }
        if(!java.util.Arrays.equals(first,readBytes(sourceBase+"tone.wav"))||!java.util.Arrays.equals(second,readBytes(sourceBase+"mono44100.wav"))) throw new AssertionError("Audio source changed");
        log.append("R8 MP3/OGG/FLAC, two sources, trim/order/profile, duration and output grants: passed\n");
    }
    private void videoEdit() throws Exception {
        String base="content://com.example.contentstudio.test.videos/";
        byte[] first=readBytes(base+"edit-first.mp4"),second=readBytes(base+"edit-second.mp4");
        launch(); click("Edit video"); picker(Intent.ACTION_OPEN_DOCUMENT,base+"edit-first.mp4",()->click("Choose video"));
        if (workspace) { find("edit-first.mp4", 30000, true); SystemClock.sleep(500); }
        picker(Intent.ACTION_OPEN_DOCUMENT,base+"edit-second.mp4",()->click("Add second source"));
        find("edit-second.mp4",20000,true); click("Swap order"); click("9:16"); click("Fill · center crop"); click("2×"); click(pixels(640)); click("Generate output");
        String destination=base+"export.mp4";boolean approval=saveCurrent("Save new video",destination);find("New video saved",30000,true);
        tracks(destination,"video/avc","audio/mp4a-latm");
        android.media.MediaMetadataRetriever reader=new android.media.MediaMetadataRetriever();
        try {
            reader.setDataSource(getTargetContext(),Uri.parse(destination));
            long expected=(duration(base+"edit-first.mp4")+duration(base+"edit-second.mp4"))/2;
            if(Math.abs(Long.parseLong(reader.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION))-expected)>250) throw new AssertionError("Joined fast video duration");
            Bitmap frame=reader.getFrameAtTime(200000,android.media.MediaMetadataRetriever.OPTION_CLOSEST);
            try { if(frame==null||frame.getWidth()*16!=frame.getHeight()*9) throw new AssertionError("Portrait output canvas");
                int color=frame.getPixel(frame.getWidth()/2,frame.getHeight()/2); if(android.graphics.Color.blue(color)<180) throw new AssertionError("Video source order");
            } finally { if(frame!=null)frame.recycle(); }
        } finally {reader.release();}
        java.io.File file=new java.io.File(getTargetContext().getExternalFilesDir(null),"qa/release-joined-video.mp4");file.getParentFile().mkdirs();
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(file)){out.write(readBytes(destination));}
        outputActions("video/mp4",approval);capture("release-video-edit");close();
        launch();click("Edit video");find("Save new video",30000,true);capture("release-video-restored");close();
        if(!java.util.Arrays.equals(first,readBytes(base+"edit-first.mp4"))||!java.util.Arrays.equals(second,readBytes(base+"edit-second.mp4"))) throw new AssertionError("Video source changed");
        log.append("R8 joined video, reorder, portrait crop, 2x speed, actual frame/duration and durable result: passed\n");
    }
    private void mp3Source() throws Exception {
        launch();click(string("supported_files"));
        // At 2x font the complete capability list needs more swipes to reach its footer.
        find(string("open_source_licenses"),60000,true);click(string("open_source_licenses"));
        String destination="content://com.example.contentstudio.test.audio/export.mp3";
        byte[] expected;try(java.io.InputStream in=getTargetContext().getAssets().open("licenses/birlevo-mp3-sources.zip")){expected=in.readAllBytes();}
        // This action is the first item, above a long license body. A restored/transitioning
        // dialog list must scroll toward its beginning, rather than searching forward into text.
        long sourceActionDeadline = SystemClock.uptimeMillis() + 30000;
        while (SystemClock.uptimeMillis() < sourceActionDeadline) {
            AccessibilityNodeInfo root = activeRoot();
            if (root != null && textNode(root, string("mp3_source_save"), true) != null) break;
            if (root != null) scroll(root, false);
            SystemClock.sleep(180);
        }
        picker(Intent.ACTION_CREATE_DOCUMENT,destination,()->click(string("mp3_source_save")));
        long deadline=SystemClock.uptimeMillis()+15000;boolean saved=false;
        while(SystemClock.uptimeMillis()<deadline){try{saved=java.util.Arrays.equals(expected,readBytes(destination));}catch(Exception ignored){}if(saved)break;SystemClock.sleep(100);}
        if(!saved)throw new AssertionError("Offline corresponding source export bytes");capture("release-mp3-sources");close();
        log.append("R8 offline LGPL corresponding-source save and exact bytes: passed\n");
    }

    private void audio() throws Exception {
        for (String target : new String[] {"WAV", "M4A"}) {
            launch(); click("Audio tools");
            picker(Intent.ACTION_OPEN_DOCUMENT, "content://com.example.contentstudio.test.audio/tone.wav", () -> click("Choose audio or video"));
            click(target); click("Create audio");
            String destination = "content://com.example.contentstudio.test.audio/export." + target.toLowerCase(java.util.Locale.ROOT);
            boolean approval = saveCurrent("Save audio", destination); find("New audio saved", 30000, true);
            if (target.equals("WAV")) {
                byte[] data = readBytes(destination);
                if (data.length < 44 || data[0] != 'R' || data[1] != 'I' || data[2] != 'F' || data[3] != 'F') throw new AssertionError("WAV header");
            } else tracks(destination, null, "audio/mp4a-latm");
            outputActions(target.equals("WAV") ? "audio/wav" : "audio/mp4", approval); close();
        }
        log.append("WAV/M4A encode, actual size approval, save/open/share: passed\n");
    }
    private void documents() throws Exception {
        launch(); click("Text and tables");
        picker(Intent.ACTION_OPEN_DOCUMENT, "content://com.example.contentstudio.test.documents/records.csv", () -> click("Choose document"));
        find("records.csv", 20000, true);
        // Input interpretation and output format both have a TSV chip. Select only
        // within the output section; a global text match changes the input kind.
        if (workspace) openPanel("Output format");
        AccessibilityNodeInfo heading = find("Output format", 20000, true);
        android.graphics.Rect headingBounds = new android.graphics.Rect(); heading.getBoundsInScreen(headingBounds);
        AccessibilityNodeInfo target = textNodeBelow(activeRoot(), "TSV", headingBounds.bottom);
        while (target != null && !target.isClickable()) target = target.getParent();
        if (target == null || !target.isEnabled() || !target.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            throw new AssertionError("Cannot select TSV output");
        waitForIdleSync(); click("Create output");
        String destination = "content://com.example.contentstudio.test.documents/export.tsv";
        picker(Intent.ACTION_CREATE_DOCUMENT, destination, () -> click("Save new file"));
        find("New file saved", 30000, true);
        String text = new String(readBytes(destination), java.nio.charset.StandardCharsets.UTF_8);
        if (!text.contains("\t") || !text.contains("İstanbul") || !text.contains("北京") || !text.contains("00123")) throw new AssertionError("TSV cells");
        close(); log.append("CSV reader / Unicode and quoted cells / TSV output: passed\n");
    }
    private void office() throws Exception {
        String base = "content://com.example.contentstudio.test.documents/";
        for (boolean sheet : new boolean[] {false, true}) {
            String source = sheet ? "shared-no-cache.xlsx" : "rich.docx";
            String target = sheet ? "XLSX" : "DOCX";
            byte[] original = readBytes(base + source);
            launch(); click("Text and tables");
            picker(Intent.ACTION_OPEN_DOCUMENT, base + source, () -> click("Choose document"));
            find(source, 30000, true);
            openPanel("Output format"); click(target); closePanel();
            click(string("office_consent")); click("Create output");
            String destination = base + "export." + target.toLowerCase(java.util.Locale.ROOT);
            picker(Intent.ACTION_CREATE_DOCUMENT, destination, () -> click("Save new file"));
            find("New file saved", 30000, true);
            byte[] output = readBytes(destination);
            String xml = null;
            String member = sheet ? "xl/worksheets/sheet1.xml" : "word/document.xml";
            try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(output))) {
                java.util.zip.ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (member.equals(entry.getName())) xml = new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
            }
            if (xml == null || !xml.contains("00123") || (sheet && xml.contains("<f>")))
                throw new AssertionError("Simplified Office output lost text or retained a live formula: " + target);
            if (!java.util.Arrays.equals(original, readBytes(base + source)))
                throw new AssertionError("Office input changed");
            java.io.File file = new java.io.File(getTargetContext().getExternalFilesDir(null), "qa/release-office." + target.toLowerCase(java.util.Locale.ROOT));
            file.getParentFile().mkdirs();
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) { out.write(output); }
            capture("release-office-" + target.toLowerCase(java.util.Locale.ROOT)); close();
            log.append("R8 Office: ").append(source).append(" -> ").append(target)
                .append(" with loss approval, saved XML, leading zeros and original preservation\n");
        }
    }
    private void reader() throws Exception {
        launch(); click("PDF reader");
        picker(Intent.ACTION_OPEN_DOCUMENT, "content://com.example.contentstudio.test.pdf/reader.pdf", () -> click("Choose PDF"));
        click("JPEG"); click(pixels(1280)); click("Create JPEG");
        String destination = "content://com.example.contentstudio.test.fixtures/export.jpg";
        picker(Intent.ACTION_CREATE_DOCUMENT, destination, () -> click("Save JPEG"));
        find("New file saved", 30000, true);
        Bitmap image = readBitmap(destination);
        if (image == null) throw new AssertionError("PDF raster export");
        try { if (Math.max(image.getWidth(), image.getHeight()) != 1280) throw new AssertionError("PDF raster edge"); }
        finally { image.recycle(); }
        outputActions("image/jpeg", false, "Save JPEG"); close(); log.append("Isolated PDF reader / JPEG raster / save/open/share: passed\n");
    }
    private void pages() throws Exception {
        launch(); click("PDF page tools");
        picker(Intent.ACTION_OPEN_DOCUMENT, "content://com.example.contentstudio.test.pdf/jpeg2000.pdf", () -> click("Add PDF files"));
        click("Create new PDF"); click("Create new PDF");
        String destination = "content://com.example.contentstudio.test.pdf/export.pdf";
        picker(Intent.ACTION_CREATE_DOCUMENT, destination, () -> click("Save PDF copy"));
        find("New file saved", 30000, true);
        try (PdfRenderer renderer = new PdfRenderer(getTargetContext().getContentResolver().openFileDescriptor(Uri.parse(destination), "r"))) {
            if (renderer.getPageCount() != 1) throw new AssertionError("JPX page count");
            try (PdfRenderer.Page page = renderer.openPage(0)) {
                Bitmap bitmap = Bitmap.createBitmap(256, 128, Bitmap.Config.ARGB_8888);
                try {
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    int left = bitmap.getPixel(40, 64), right = bitmap.getPixel(210, 64);
                    if (android.graphics.Color.red(left) < 180 || android.graphics.Color.blue(right) < 180)
                        throw new AssertionError("JPEG2000 image pixels lost during page cloning");
                } finally { bitmap.recycle(); }
            }
        }
        outputActions("application/pdf"); close();
        log.append("Isolated page cloning preserves JPEG2000 streams, platform rendering and output grants: passed\n");
    }
    private void guardedCopy(String entry, String choose, String input, String process, String save,
            String base, String output) throws Exception {
        byte[] original = readBytes(base + input);
        launch(); click(entry);
        picker(Intent.ACTION_OPEN_DOCUMENT, base + input, () -> click(choose));
        find(input, 20000, true);
        click(process);
        if (workspace) {
            find("Save converted copy", 120000, true);
            IntentFilter filter = new IntentFilter(Intent.ACTION_CREATE_DOCUMENT); filter.addDataType("*/*");
            ActivityMonitor noSave = addMonitor(filter, new ActivityResult(Activity.RESULT_CANCELED, null), true);
            try {
                click("Save converted copy"); find("File size did not decrease", 10000, false);
                if (noSave.getHits() != 0) throw new AssertionError("Copy saved before explicit approval");
                click("Keep original"); waitForIdleSync();
                if (noSave.getHits() != 0) throw new AssertionError("Cancelled copy started a save");
            } finally { removeMonitor(noSave); }
            click("Save converted copy");
            picker(Intent.ACTION_CREATE_DOCUMENT, base + output, () -> click("Save anyway"));
            find(input.endsWith(".mp4") ? "New video saved" : "New image saved", 20000, true);
            byte[] saved = readBytes(base + output);
            if (saved.length <= original.length) throw new AssertionError("Fixture must actually grow");
            if (!java.util.Arrays.equals(original, readBytes(base + input))) throw new AssertionError("Original changed");
            capture("larger-" + input); close();
            log.append(entry + " explicit converted-copy confirmation/cancel/source preservation passed\n");
            return;
        }
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
    private void outputActions(String mime) throws Exception { outputActions(mime, false); }
    private void outputActions(String mime, boolean approval) throws Exception {
        outputActions(mime, approval, null);
    }
    private void outputActions(String mime, boolean approval, String outputAnchor) throws Exception {
        IntentFilter open = new IntentFilter(Intent.ACTION_VIEW); open.addDataType(mime);
        ActivityMonitor viewer = addMonitor(open, new ActivityResult(Activity.RESULT_CANCELED, null), true);
        try { clickOutput(outputAnchor, "Open file"); if (approval) click("Continue"); awaitMonitor(viewer); }
        finally { removeMonitor(viewer); }
        IntentFilter share = new IntentFilter(Intent.ACTION_CHOOSER);
        ActivityMonitor chooser = addMonitor(share, new ActivityResult(Activity.RESULT_CANCELED, null), true);
        try { clickOutput(outputAnchor, "Share file"); if (approval) click("Continue"); awaitMonitor(chooser); }
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
        if (workspace) click("Other actions");
        find("Share file", 10000, true);
        java.io.File screenshot = new java.io.File(getTargetContext().getExternalFilesDir(null), "qa/output-" + mime.replace('/', '-') + ".png");
        screenshot.getParentFile().mkdirs();
        Bitmap screen = getUiAutomation().takeScreenshot();
        try (java.io.FileOutputStream stream = new java.io.FileOutputStream(screenshot)) {
            screen.compress(Bitmap.CompressFormat.PNG, 100, stream);
        } finally { screen.recycle(); }
        log.append("Open/share MIME and cross-application snapshot read: " + mime + " passed\n");
        if (workspace) click("Close");
    }
    private void awaitMonitor(ActivityMonitor monitor) {
        long deadline = SystemClock.uptimeMillis() + 20000;
        while (monitor.getHits() == 0 && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100);
        if (monitor.getHits() != 1) throw new AssertionError("Missing or repeated output intent");
    }
    private void clickOutput(String anchor, String text) {
        if (workspace) { click(text); return; }
        if (anchor == null) { click(text); return; }
        find(anchor, 20000, true);
        long deadline = SystemClock.uptimeMillis() + 20000;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo root = activeRoot();
            AccessibilityNodeInfo heading = root == null ? null : textNode(root, anchor, false);
            android.graphics.Rect bounds = new android.graphics.Rect();
            if (heading != null) heading.getBoundsInScreen(bounds);
            // After scrolling forward beyond the anchor, preceding source actions
            // are above the viewport. Source and output have identical action text.
            AccessibilityNodeInfo target = textNodeBelow(root, text, heading == null ? 0 : bounds.bottom);
            while (target != null && !target.isClickable()) target = target.getParent();
            if (target != null && target.refresh() && enabledPath(target)
                    && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                waitForIdleSync(); return;
            }
            if (root != null) scroll(root, true);
            SystemClock.sleep(180);
        }
        throw new AssertionError("Output action not found: " + text);
    }
    private void transfer() throws Exception {
        launch(); click("Compress image");
        picker(Intent.ACTION_OPEN_DOCUMENT, "content://com.example.contentstudio.test.fixtures/noise.jpg", () -> click("Choose image"));
        click(pixels(1280)); click("Generate preview"); find("Save new image", 30000, true);
        click("Add to presentation"); click("New presentation"); find("Heading", 20000, true);
        setText("Project name", "Transferred image"); click("Save");
        if (workspace) rawClick("Back"); // A transfer returns to its source tool; then return home.
        find("Create presentation", 15000, true);
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
    private boolean visible(String text) {
        AccessibilityNodeInfo root = activeRoot();
        if (root != null && textNode(root, text, false) != null) return true;
        for (android.view.accessibility.AccessibilityWindowInfo window : getUiAutomation().getWindows()) {
            AccessibilityNodeInfo windowRoot = window.getRoot();
            if (windowRoot != null && textNode(windowRoot, text, false) != null) return true;
        }
        return false;
    }
    private String alias(String text) {
        if (!workspace) return text;
        if (text.equals("Create output") && "documents".equals(currentTool)) return text;
        if ((text.equals("Save new image") || text.equals("Save new video") || text.equals("Save audio")) && visible("Save converted copy")) return "Save converted copy";
        if (text.equals("Generate preview") || text.equals("Generate output") || text.equals("Create audio")
                || text.equals("Create PDF") || text.equals("Create output")) return "Create result";
        if (text.equals("Create new PDF") && !visible("Create new PDF")) return "Create result";
        return text;
    }
    private void closePanel() {
        if (activePanel == null) return;
        if (visible("Close")) rawClick("Close");
        else rawClick(activePanel); // Wide workspace chips toggle the selected panel.
        activePanel = null;
        long deadline = SystemClock.uptimeMillis() + 3000;
        while (visible("Close") && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100);
        SystemClock.sleep(400); // Wait for the sheet's dismissal scrim before selecting another tab.
    }
    private void openPanel(String name) {
        if (name.equals(activePanel)) return;
        closePanel();
        long panelDeadline = SystemClock.uptimeMillis() + 15000;
        int i = 0;
        while (SystemClock.uptimeMillis() < panelDeadline) {
            AccessibilityNodeInfo row = horizontalScrollable(activeRoot());
            if (row == null) { SystemClock.sleep(180); continue; }
            android.graphics.Rect bounds = new android.graphics.Rect(); row.getBoundsInScreen(bounds);
            AccessibilityNodeInfo target = textNode(row, name, true);
            boolean forward = (i++ % 12) < 6;
            if (target != null) {
                android.graphics.Rect hit = new android.graphics.Rect(); target.getBoundsInScreen(hit);
                boolean usable = hit.width() >= Math.min(80, name.length() * 10)
                        && hit.centerX() >= bounds.left + 80 && hit.centerX() <= bounds.right - 80;
                if (usable && enabledPath(target)) {
                    shellInput("input touchscreen tap " + hit.centerX() + " " + hit.centerY());
                    waitForIdleSync(); SystemClock.sleep(300);
                    android.content.res.Configuration configuration = activity.getResources().getConfiguration();
                    if (configuration.screenWidthDp >= 840 && configuration.fontScale <= 1.3f) { activePanel = name; return; }
                    long opened = SystemClock.uptimeMillis() + 3000;
                    while (!visible("Close") && SystemClock.uptimeMillis() < opened) SystemClock.sleep(100);
                    if (visible("Close")) { activePanel = name; return; }
                    continue;
                }
                forward = hit.centerX() > bounds.centerX();
            }
            int start = bounds.left + (int)(bounds.width() * (forward ? .85f : .15f));
            int end = bounds.left + (int)(bounds.width() * (forward ? .15f : .85f));
            shellInput("input touchscreen swipe " + start + " " + bounds.centerY() + " " + end + " " + bounds.centerY() + " 250");
            waitForIdleSync(); SystemClock.sleep(300);
        }
        rawClick(name); activePanel = name;
    }
    private AccessibilityNodeInfo horizontalScrollable(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isVisibleToUser()
                && "android.widget.HorizontalScrollView".contentEquals(node.getClassName())) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = horizontalScrollable(node.getChild(i));
            if (found != null) return found;
        }
        return null;
    }
    private boolean prepareWorkspaceAction(String text) {
        // The home actions may be off-screen after dismissing scrolled help content.
        boolean home = visible("Birlevo") && visible("Language");
        if (home) {
            if (text.equals("Transferred image") && !visible(text)) rawClick("View all");
            if (text.equals("Compress image")) { currentTool = "image"; rawClick("Images"); return true; }
            if (text.equals("Edit video")) { currentTool = "video"; rawClick("Video"); return true; }
            if (text.equals("Audio tools")) { currentTool = "audio"; rawClick("Audio"); return true; }
            if (text.equals("Create presentation") || text.equals("Photos to PDF") || text.equals("Images / slides to video")) {
                currentTool = text.equals("Create presentation") ? "project" : text.equals("Photos to PDF") ? "photos" : "slideshow";
                rawClick("Create new");
            } else if (text.equals("PDF reader") || text.equals("PDF page tools") || text.equals("Text and tables")) {
                currentTool = text.equals("PDF reader") ? "reader" : text.equals("PDF page tools") ? "pages" : "documents";
                rawClick("Documents");
            }
        }
        if ((text.equals("Choose image") || text.equals("Choose video") || text.equals("Choose audio or video")
                || text.equals("Choose PDF") || text.equals("Choose document")) && !visible(text) && visible("Change")) {
            rawClick("Change"); return true;
        }
        if (text.equals("Supported files") && !visible(text)) rawClick("Details and help");
        if (text.equals("Classic") || text.equals("Ocean")) openPanel("Presentation theme");
        if (text.equals("Preview PDF")) { closePanel(); activePanel = null; }
        if (text.equals("Save") && "project".equals(currentTool)) closePanel();
        if (text.equals("Add second source") || text.equals("Swap order"))
            openPanel("audio".equals(currentTool) ? "Trim range" : "Clips");
        if (text.equals("Trim range") && "audio".equals(currentTool)) openPanel("Trim range");
        if (text.equals("9:16") || text.equals("16:9") || text.equals("Fill · center crop") || text.equals("2×"))
            openPanel("Canvas / Speed");
        if (text.matches("[0-9,]+ px") || java.util.Arrays.asList("JPEG","PNG","WEBP","BMP","WAV","M4A","MP3","FLAC","OGG · Opus",
                "MP4 · HEVC","WebM · VP8","High quality","48000 Hz","2 ch").contains(text)) {
            openPanel("reader".equals(currentTool) ? "Current page as image" : "video".equals(currentTool)
                || "slideshow".equals(currentTool) || "pptx".equals(currentTool) || "slide-image".equals(currentTool) ? "Output" : "Output format");
        }
        if (text.equals("Open file") || text.equals("Share file") || text.equals("Add to presentation")
                || text.equals("Audio tools") || text.equals("Export PPTX") || text.equals("Present fullscreen")
                || text.equals("Slide image") || (text.equals("Images / slides to video") && !home)) {
            if (!visible(text)) rawClick("Other actions");
        }
        if (text.equals("Audio tools")) currentTool = "audio";
        if (text.equals("New presentation")) currentTool = "project";
        if (text.equals("Export PPTX")) currentTool = "pptx";
        if (text.equals("Slide image")) currentTool = "slide-image";
        if (text.equals("Images / slides to video")) currentTool = "slideshow";
        if (alias(text).equals("Create result") || text.equals("Create JPEG")) activePanel = null;
        return false;
    }
    private void newReviewPresentation(String title) throws Exception {
        if (reuseReview) {
            launch(); rawClick("View all");
            long deadline = SystemClock.uptimeMillis() + 40000;
            boolean opened = false;
            while (SystemClock.uptimeMillis() < deadline) {
                AccessibilityNodeInfo root = activeRoot();
                AccessibilityNodeInfo card = reviewProjectCard(root);
                if (card != null && card.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { opened = true; break; }
                if (root != null) scroll(root, true);
                SystemClock.sleep(150);
            }
            if (!opened) throw new AssertionError("Saved two-slide presentation card not found");
            currentTool = "project";
            find("Preview PDF", 20000, true); return;
        }
        launch(); click("Create presentation"); setText("Project name", title); setText("Heading", "Release heading");
        setText("Speaker notes", "Private QA note"); closePanel();
        click("Add slide"); openPanel("Slide 2"); setText("Text", "Second slide"); closePanel();
    }
    private AccessibilityNodeInfo reviewProjectCard(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isClickable() && node.isVisibleToUser() && textNode(node, "Release presenting", false) != null
                && textNode(node, "Presentation", false) != null) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo card = reviewProjectCard(node.getChild(i));
            if (card != null) return card;
        }
        return null;
    }
    private void pptx() throws Exception {
        newReviewPresentation("Release PPTX");
        click("Export PPTX"); find("Source preview", 20000, true);
        for (boolean notes : new boolean[] {false, true}) {
            openPanel("Output");
            AccessibilityNodeInfo checkbox = find("Include speaker notes", 10000, true);
            while (checkbox != null && !checkbox.isCheckable()) checkbox = checkbox.getParent();
            if (checkbox == null) throw new AssertionError("Missing notes checkbox semantics");
            if (checkbox.isChecked() != notes) rawClick("Include speaker notes");
            closePanel();
            click("Create result"); find("Generated PPTX preview", 120000, true);
            String destination = "content://com.example.contentstudio.test.fixtures/export.jpg";
            picker(Intent.ACTION_CREATE_DOCUMENT, destination, () -> click("Save"));
            find("New file saved", 30000, true);
            byte[] data = readBytes(destination); int slides = 0, images = 0, noteParts = 0; boolean privateNote = false;
            try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(data))) {
                java.util.zip.ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (entry.getName().matches("ppt/slides/slide[0-9]+\\.xml")) slides++;
                    if (entry.getName().matches("ppt/media/image[0-9]+\\.png")) {
                        Bitmap bitmap = BitmapFactory.decodeStream(zip);
                        if (bitmap == null || bitmap.getWidth() != 2048 || bitmap.getHeight() != 1152) throw new AssertionError("PPTX resolution");
                        bitmap.recycle(); images++;
                    } else if (entry.getName().startsWith("ppt/notesSlides/notesSlide") && entry.getName().endsWith(".xml")) {
                        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream(); byte[] buffer = new byte[4096]; int count;
                        while ((count = zip.read(buffer)) != -1) bytes.write(buffer, 0, count);
                        privateNote |= new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8).contains("Private QA note"); noteParts++;
                    }
                }
            }
            if (slides != 2 || images != 2 || noteParts != (notes ? 2 : 0) || privateNote != notes)
                throw new AssertionError("PPTX notes/slides: requested=" + notes + " slides=" + slides + " images=" + images + " notes=" + noteParts + " privateNote=" + privateNote);
            java.io.File file = new java.io.File(getTargetContext().getExternalFilesDir(null), "qa/release-notes-" + notes + ".pptx"); file.getParentFile().mkdirs();
            try (java.io.FileOutputStream output = new java.io.FileOutputStream(file)) { output.write(data); }
            outputActions("application/vnd.openxmlformats-officedocument.presentationml.presentation");
        }
        capture("release-pptx"); close(); log.append("R8 PPTX: two slides, 2048px, explicit notes exclusion/inclusion, save/open/share passed\n");
    }
    private void slideshow() throws Exception {
        newReviewPresentation("Release slideshow"); click("Images / slides to video");
        long deadline = SystemClock.uptimeMillis() + 20000;
        while (!visible("Use presentation") && !visible("Create result") && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100);
        if (visible("Use presentation")) rawClick("Use presentation");
        click("Create result"); String destination = "content://com.example.contentstudio.test.videos/export.mp4";
        picker(Intent.ACTION_CREATE_DOCUMENT, destination, () -> click("Save new video")); find("New video saved", 30000, true);
        MediaExtractor extractor = new MediaExtractor();
        try (android.os.ParcelFileDescriptor fd = getTargetContext().getContentResolver().openFileDescriptor(Uri.parse(destination), "r")) {
            extractor.setDataSource(fd.getFileDescriptor());
            if (extractor.getTrackCount() != 1 || !"video/avc".equals(extractor.getTrackFormat(0).getString(MediaFormat.KEY_MIME))) throw new AssertionError("Silent slideshow AVC");
        } finally { extractor.release(); }
        if (duration(destination) < 1000) throw new AssertionError("Slideshow duration");
        java.io.File file = new java.io.File(getTargetContext().getExternalFilesDir(null), "qa/release-slideshow.mp4");
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(file)) { output.write(readBytes(destination)); }
        outputActions("video/mp4"); capture("release-slideshow"); close(); log.append("R8 presentation to silent MP4, save/open/share passed\n");
    }
    private void presentationMode() throws Exception {
        newReviewPresentation("Release presenting"); click("Present fullscreen");
        click("Speaker notes"); find("Private QA note", 20000, true); click("Start timer"); click("Pause timer");
        capture("release-presenting"); click("Back"); currentTool = "project";
        click("Slide image"); openPanel("Output"); click("PNG"); click("Create result");
        String destination = "content://com.example.contentstudio.test.fixtures/export.png";
        picker(Intent.ACTION_CREATE_DOCUMENT, destination, () -> click("Save")); find("New image saved", 30000, true);
        Bitmap image = readBitmap(destination);
        if (image == null || image.getWidth() * 9 != image.getHeight() * 16) throw new AssertionError("Slide image ratio");
        image.recycle(); outputActions("image/png"); close(); log.append("R8 fullscreen notes/timer and PNG slide export passed\n");
    }
    private void erase(java.io.File file) {
        if (file.isDirectory()) for (java.io.File child : file.listFiles()) erase(child);
        file.delete();
    }
    private void click(String text) {
        if (workspace && prepareWorkspaceAction(text)) return;
        rawClick(alias(text));
    }
    private void rawClick(String text) {
        long deadline = SystemClock.uptimeMillis() + 20000;
        // A lazy row can replace its accessibility node after scrolling; reacquire it until ready.
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo node = find(text, Math.max(1, deadline - SystemClock.uptimeMillis()), true, true);
            while (node != null && !node.isClickable()) node = node.getParent();
            if (node != null && node.refresh() && enabledPath(node)) {
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
        if (workspace) {
            if (label.equals("Project name")) openPanel("Project name");
            if (label.equals("Heading") || label.equals("Body text")) openPanel("Slide 1");
            if (label.equals("Speaker notes")) openPanel("Speaker notes");
        }
        Bundle args = new Bundle(); args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        long deadline = SystemClock.uptimeMillis() + 20000;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo node = editableField(activeRoot(), label);
            if (node == null) node = find(label, Math.max(1, deadline - SystemClock.uptimeMillis()), true);
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
    private AccessibilityNodeInfo editableField(AccessibilityNodeInfo node, String label) {
        if (node == null) return null;
        if (node.isEditable() && node.isVisibleToUser() && textNode(node, label, false) != null) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo field = editableField(node.getChild(i), label);
            if (field != null) return field;
        }
        return null;
    }
    private AccessibilityNodeInfo find(String text, long timeout, boolean scroll) {
        return find(text, timeout, scroll, false);
    }
    private AccessibilityNodeInfo find(String text, long timeout, boolean scroll, boolean clickable) {
        text = alias(text);
        boolean sourceChooser = workspace && java.util.Arrays.asList("Choose image", "Choose video",
            "Choose audio or video", "Choose PDF", "Choose document").contains(text);
        if (workspace && text.equals("Create presentation") && visible("Create new")) text = "Create new";
        if (workspace && text.equals("Heading") && !visible("Heading") && "project".equals(currentTool)) openPanel("Slide 1");
        long end = SystemClock.uptimeMillis() + timeout;
        boolean forward = true;
        int unchanged = 0;
        while (SystemClock.uptimeMillis() < end) {
            AccessibilityNodeInfo root = activeRoot();
            if (root != null) {
                // A restored source can replace the chooser after the initial navigation check.
                if (sourceChooser && textNode(root, text, clickable) == null) {
                    AccessibilityNodeInfo change = textNode(root, "Change", clickable);
                    if (change != null) return change;
                }
                // Compose menus are separate windows and need not be the active accessibility root.
                List<android.view.accessibility.AccessibilityWindowInfo> windows = new ArrayList<>(getUiAutomation().getWindows());
                windows.sort((a, b) -> Integer.compare(b.getLayer(), a.getLayer()));
                AccessibilityNodeInfo matching;
                for (android.view.accessibility.AccessibilityWindowInfo window : windows) {
                    AccessibilityNodeInfo windowRoot = window.getRoot();
                    matching = windowRoot == null ? null : textNode(windowRoot, text, clickable);
                    if (matching != null) return matching;
                }
                matching = textNode(root, text, clickable);
                if (matching != null) return matching;
                if (scroll) {
                    String before = visibleWindowsContent();
                    boolean moved = scroll(root, forward);
                    AccessibilityNodeInfo after = activeRoot();
                    unchanged = after != null && before.equals(visibleWindowsContent()) ? unchanged + 1 : 0;
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
        AccessibilityNodeInfo active = getUiAutomation().getRootInActiveWindow();
        if (active == null || !"studio.birlevo.android".contentEquals(active.getPackageName() == null ? "" : active.getPackageName())) return active;
        List<android.view.accessibility.AccessibilityWindowInfo> windows = new ArrayList<>(getUiAutomation().getWindows());
        windows.sort((a, b) -> Integer.compare(b.getLayer(), a.getLayer()));
        for (android.view.accessibility.AccessibilityWindowInfo window : windows) {
            AccessibilityNodeInfo root = window.getRoot();
            if (window.getType() == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION
                    && root != null && "studio.birlevo.android".contentEquals(root.getPackageName() == null ? "" : root.getPackageName())) return root;
        }
        return active;
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
    private String visibleWindowsContent() {
        StringBuilder signature = new StringBuilder();
        for (android.view.accessibility.AccessibilityWindowInfo window : getUiAutomation().getWindows()) {
            AccessibilityNodeInfo root = window.getRoot();
            if (root != null) signature.append(visibleContent(root));
        }
        return signature.toString();
    }
    private AccessibilityNodeInfo textNode(AccessibilityNodeInfo node, String text, boolean clickable) {
        String value = node.getText() == null ? "" : node.getText().toString();
        for (String line : value.split("\n")) if (text.equals(line) && node.isVisibleToUser() && (!clickable || clickableParent(node))) return node;
        for (int i=0; i<node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo found = textNode(child, text, clickable);
                if (found != null) return found;
            }
        }
        return null;
    }
    private boolean clickableParent(AccessibilityNodeInfo node) {
        while (node != null) { if (node.isClickable()) return true; node = node.getParent(); }
        return false;
    }
    private boolean enabledPath(AccessibilityNodeInfo node) {
        while (node != null) { if (!node.isEnabled()) return false; node = node.getParent(); }
        return true;
    }
    private AccessibilityNodeInfo textNodeBelow(AccessibilityNodeInfo node, String text, int top) {
        if (node == null) return null;
        android.graphics.Rect bounds = new android.graphics.Rect(); node.getBoundsInScreen(bounds);
        if (text.contentEquals(node.getText() == null ? "" : node.getText()) && node.isVisibleToUser()
                && bounds.top >= top && clickableParent(node)) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = textNodeBelow(node.getChild(i), text, top);
            if (found != null) return found;
        }
        return null;
    }
    private boolean scroll(AccessibilityNodeInfo node, boolean forward) {
        // Never swipe the launcher, notification shade or another system window during a transition.
        if (!"studio.birlevo.android".contentEquals(node.getPackageName() == null ? "" : node.getPackageName())) return false;
        AccessibilityNodeInfo target = largestScrollable(node);
        if (workspace && visible("Close")) {
            for (android.view.accessibility.AccessibilityWindowInfo window : getUiAutomation().getWindows()) {
                AccessibilityNodeInfo sheet = verticalScrollView(window.getRoot());
                if (sheet != null) { target = sheet; node = window.getRoot(); break; }
            }
        }
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
    private AccessibilityNodeInfo verticalScrollView(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isVisibleToUser() && node.isScrollable()
                && "android.widget.ScrollView".contentEquals(node.getClassName())) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = verticalScrollView(node.getChild(i));
            if (found != null) return found;
        }
        return null;
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
