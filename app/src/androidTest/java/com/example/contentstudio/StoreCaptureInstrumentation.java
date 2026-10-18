package com.example.contentstudio;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.LocaleManager;
import android.content.ClipData;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.LocaleList;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Captures real signed-app UI with owned synthetic inputs; never generates UI pixels. */
public final class StoreCaptureInstrumentation extends Instrumentation {
    private String language;
    private String scene;
    private Activity activity;
    private final StringBuilder log = new StringBuilder();
    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        language = args.getString("locale", "en-US"); scene = args.getString("scene", "home");
        if (!language.equals("en-US") && !language.equals("tr-TR")) throw new IllegalArgumentException("Unsupported capture locale");
        start();
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            if (getTargetContext().getPackageManager().getPackageInfo("studio.birlevo.android", 0).versionCode != 14)
                throw new AssertionError("Store captures require the frozen code 14 candidate");
            android.accessibilityservice.AccessibilityServiceInfo service = getUiAutomation().getServiceInfo();
            service.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            getUiAutomation().setServiceInfo(service);
            if (android.os.Build.VERSION.SDK_INT < 33) throw new AssertionError("Capture requires QA API 33+");
            getTargetContext().getSystemService(LocaleManager.class).setApplicationLocales(LocaleList.forLanguageTags(language));
            activity = startActivitySync(new Intent().setClassName("studio.birlevo.android", "com.example.contentstudio.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            find(t("home_start"), 20000, true);
            if (!activity.getResources().getConfiguration().getLocales().get(0).getLanguage().equals(Locale.forLanguageTag(language).getLanguage()))
                throw new AssertionError("Actual activity locale differs from capture locale");
            switch (scene) {
                case "home": break;
                case "image":
                    click(t("short_images")); pick("choose_image", "lake.png");
                    find("lake.png", 30000, true); click(t("create_result"));
                    find(t("image_export"), 60000, true); break;
                case "video":
                    click(t("video")); pick("choose_video", "lake-video.mp4");
                    find("lake-video.mp4", 30000, true); SystemClock.sleep(1500); break;
                case "audio":
                    click(t("audio_category")); pick("audio_choose", "lake-melody.wav");
                    find("lake-melody.wav", 30000, true); panel(t("output_format"));
                    find("MP3", 15000, true); break;
                case "pdf":
                    click(t("documents_category")); click(t("read_pdf")); pick("choose_pdf", "weekend-guide.pdf");
                    find("weekend-guide.pdf", 30000, true); SystemClock.sleep(1500); break;
                case "documents":
                    click(t("documents_category")); click(t("read_documents")); pick("choose_document", "travel-budget.csv");
                    find("travel-budget.csv", 30000, true); SystemClock.sleep(1000); break;
                case "presentation": presentation(); break;
                case "batch":
                    click(t("batch_tools")); click(t("batch_images")); pickMany("batch_add", "lake.png", "cover.png");
                    find("lake.png", 30000, true); find("cover.png", 30000, true);
                    click(t("create_result")); find(t("batch_zip", 2, 2), 60000, true); break;
                default: throw new IllegalArgumentException("Unknown scene");
            }
            SystemClock.sleep(1600); waitForIdleSync(); capture(scene);
            result.putString("stream", log + "OK (1 store " + scene + " " + language + " capture)\n");
            runOnMainSync(() -> activity.finish()); finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            try { capture("diagnostic-" + scene); describe(root(), 0); } catch (Throwable ignored) { }
            java.io.StringWriter stack = new java.io.StringWriter(); error.printStackTrace(new java.io.PrintWriter(stack));
            result.putString("stream", log + "\n" + stack); finish(Activity.RESULT_CANCELED, result);
        }
    }
    private String t(String name, Object... args) {
        int id = getTargetContext().getResources().getIdentifier(name, "string", "studio.birlevo.android");
        if (id == 0) throw new AssertionError("Unknown string: " + name);
        Configuration config = new Configuration(getTargetContext().getResources().getConfiguration());
        config.setLocales(LocaleList.forLanguageTags(language));
        return getTargetContext().createConfigurationContext(config).getString(id, args);
    }
    private void presentation() throws Exception {
        click(t("new_create")); click(t("create_presentation"));
        panel(t("slide_number", 1));
        setText(t("heading"), language.equals("tr-TR") ? "Küçük bir mola" : "A little escape");
        setText(t("body"), language.equals("tr-TR") ? "Güzel anları bir araya getir.\nKendi hikayeni oluştur." : "Bring your favorite moments together.\nMake the story your own.");
        pick("choose_image", "lake.png"); find(t("remove_image"), 30000, true); closePanel();
        panel(t("presentation_theme")); click(t("theme_classic")); click(t("theme_ocean")); closePanel();
        find(t("preview_pdf"), 15000, true);
    }
    private void pick(String key, String file) throws Exception { pickMany(key, file); }
    private void pickMany(String key, String... files) throws Exception {
        IntentFilter filter = new IntentFilter(Intent.ACTION_OPEN_DOCUMENT);
        filter.addCategory(Intent.CATEGORY_OPENABLE); filter.addCategory(Intent.CATEGORY_DEFAULT); filter.addDataType("*/*");
        Intent picked = new Intent().addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (files.length == 1) picked.setData(Uri.parse("content://com.example.contentstudio.test.store/" + files[0]));
        else {
            ClipData data = ClipData.newRawUri("Synthetic store inputs", Uri.parse("content://com.example.contentstudio.test.store/" + files[0]));
            for (int i = 1; i < files.length; i++) data.addItem(new ClipData.Item(Uri.parse("content://com.example.contentstudio.test.store/" + files[i])));
            picked.setClipData(data);
        }
        ActivityMonitor monitor = addMonitor(filter, new ActivityResult(Activity.RESULT_OK, picked), true);
        try {
            String label = t(key);
            if (findNow(label) == null && findNow(t("change_file")) != null) label = t("change_file");
            click(label); long deadline = SystemClock.uptimeMillis() + 15000;
            while (monitor.getHits() == 0 && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100);
            if (monitor.getHits() == 0) throw new AssertionError("Picker was not used");
            waitForIdleSync();
        } finally { removeMonitor(monitor); }
    }
    private AccessibilityNodeInfo root() {
        if (android.os.Build.VERSION.SDK_INT >= 33) getUiAutomation().clearCache();
        return getUiAutomation().getRootInActiveWindow();
    }
    private AccessibilityNodeInfo textNode(AccessibilityNodeInfo node, String text) {
        if (node == null) return null;
        if (node.isVisibleToUser() && (text.contentEquals(node.getText() == null ? "" : node.getText()) || text.contentEquals(node.getContentDescription() == null ? "" : node.getContentDescription()))) return node;
        for (int i = 0; i < node.getChildCount(); i++) { AccessibilityNodeInfo hit = textNode(node.getChild(i), text); if (hit != null) return hit; }
        return null;
    }
    private AccessibilityNodeInfo findNow(String text) {
        List<AccessibilityWindowInfo> windows = new ArrayList<>(getUiAutomation().getWindows());
        windows.sort((a, b) -> Integer.compare(b.getLayer(), a.getLayer()));
        for (AccessibilityWindowInfo window : windows) { AccessibilityNodeInfo hit = textNode(window.getRoot(), text); if (hit != null) return hit; }
        return textNode(root(), text);
    }
    private boolean scroll(AccessibilityNodeInfo node, boolean forward) {
        if (node == null) return false;
        if (node.isVisibleToUser() && node.isScrollable() && !"android.widget.HorizontalScrollView".contentEquals(node.getClassName()) && node.performAction(forward ? AccessibilityNodeInfo.ACTION_SCROLL_FORWARD : AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) return true;
        for (int i = 0; i < node.getChildCount(); i++) if (scroll(node.getChild(i), forward)) return true;
        return false;
    }
    private AccessibilityNodeInfo find(String text, long timeout, boolean scrolling) {
        long end = SystemClock.uptimeMillis() + timeout; boolean forward = true;
        while (SystemClock.uptimeMillis() < end) {
            AccessibilityNodeInfo node = findNow(text); if (node != null) return node;
            if (scrolling && !scroll(root(), forward)) forward = !forward;
            SystemClock.sleep(200);
        }
        throw new AssertionError("Visible UI text missing: " + text);
    }
    private void click(String text) {
        AccessibilityNodeInfo node = find(text, 20000, true);
        while (node != null && !node.isClickable()) node = node.getParent();
        if (node == null || !node.isEnabled() || !node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) throw new AssertionError("Cannot click: " + text);
        waitForIdleSync(); SystemClock.sleep(350);
    }
    private AccessibilityNodeInfo field(AccessibilityNodeInfo node, String label) {
        if (node == null) return null;
        if (node.isVisibleToUser() && node.isEditable() && textNode(node, label) != null) return node;
        for (int i = 0; i < node.getChildCount(); i++) { AccessibilityNodeInfo found = field(node.getChild(i), label); if (found != null) return found; }
        return null;
    }
    private void setText(String label, String value) {
        long deadline = SystemClock.uptimeMillis() + 15000;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo target = field(root(), label);
            if (target != null) {
                Bundle args = new Bundle(); args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
                if (target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) { waitForIdleSync(); SystemClock.sleep(350); return; }
            }
            scroll(root(), true); SystemClock.sleep(150);
        }
        throw new AssertionError("Cannot edit: " + label);
    }
    private void panel(String label) {
        AccessibilityNodeInfo row = horizontal(root());
        for (int i = 0; i < 10; i++) {
            AccessibilityNodeInfo hit = textNode(row, label);
            if (hit != null) { click(label); find(t("close"), 5000, false); return; }
            if (row != null) row.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
            SystemClock.sleep(200); row = horizontal(root());
        }
        throw new AssertionError("Panel unavailable: " + label);
    }
    private AccessibilityNodeInfo horizontal(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isVisibleToUser() && "android.widget.HorizontalScrollView".contentEquals(node.getClassName())) return node;
        for (int i = 0; i < node.getChildCount(); i++) { AccessibilityNodeInfo found = horizontal(node.getChild(i)); if (found != null) return found; }
        return null;
    }
    private void closePanel() { click(t("close")); SystemClock.sleep(500); }
    private void capture(String name) throws Exception {
        Bitmap image = getUiAutomation().takeScreenshot();
        if (image == null || image.getWidth() != 1080 || image.getHeight() != 1920) throw new AssertionError("Unexpected phone screenshot dimensions");
        File folder = new File(getTargetContext().getExternalFilesDir(null), "store-captures/" + language); folder.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(folder, name + ".png"))) { image.compress(Bitmap.CompressFormat.PNG, 100, out); }
        finally { image.recycle(); }
        log.append("Captured actual signed code 14 UI: ").append(name).append(" / ").append(language).append("\n");
    }
    private void describe(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 18) return;
        if (node.getText() != null || node.isEditable()) { Rect box = new Rect(); node.getBoundsInScreen(box); log.append(node.getClassName()).append(" text=").append(node.getText()).append(" bounds=").append(box).append("\n"); }
        for (int i = 0; i < node.getChildCount(); i++) describe(node.getChild(i), depth + 1);
    }
}
