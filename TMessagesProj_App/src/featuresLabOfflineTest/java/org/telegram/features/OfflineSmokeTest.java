package org.telegram.features;

import android.Manifest;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.ParcelFileDescriptor;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.viewpager.widget.ViewPager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;

import java.io.File;
import java.io.FileOutputStream;

import static org.junit.Assert.*;

/** Runs only against the offline package; no credentials or external services. */
@RunWith(AndroidJUnit4.class)
public class OfflineSmokeTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private Activity activity;

    @Test
    public void offlinePlaygroundRendersWithoutAccount() throws Exception {
        assertEquals("org.telegram.features.offline.beta", instrumentation.getTargetContext().getPackageName());
        assertEquals(PackageManager.PERMISSION_DENIED,
                instrumentation.getTargetContext().checkSelfPermission(Manifest.permission.INTERNET));
        assertEquals(0, BuildConfig.FEATURES_LAB_API_ID);
        assertEquals("", BuildConfig.FEATURES_LAB_API_HASH);
        launch();
        assertFalse(UserConfig.getInstance(0).isClientActivated());
        click("Use dark theme");
        assertEquals("Dark Blue", Theme.getCurrentTheme().getKey());
        screenshot("offline-dark-home.png");
        instrumentation.runOnMainSync(() -> activity.finish());
        launch();
        assertEquals("Dark Blue", Theme.getCurrentTheme().getKey());
        click("Open chat & message preview");
        screenshot("offline-dark-messages.png");
        ViewPager pager = find(activity.getWindow().getDecorView(), ViewPager.class);
        assertNotNull("Telegram's preview pager must be visible", pager);
        instrumentation.runOnMainSync(() -> pager.setCurrentItem(1, false));
        screenshot("offline-dark-dialogs.png");
        instrumentation.runOnMainSync(() -> activity.onBackPressed());
        instrumentation.waitForIdleSync();
        Thread.sleep(500);
        click("Use light theme");
        assertEquals("Blue", Theme.getCurrentTheme().getKey());
        click("Open chat & message preview");
        screenshot("offline-light-messages.png");
        ViewPager lightPager = find(activity.getWindow().getDecorView(), ViewPager.class);
        instrumentation.runOnMainSync(() -> lightPager.setCurrentItem(1, false));
        screenshot("offline-light-dialogs.png");
        assertFalse(UserConfig.getInstance(0).isClientActivated());
        instrumentation.runOnMainSync(() -> activity.onBackPressed());
        instrumentation.waitForIdleSync();
        Thread.sleep(500);
        click("Use dark theme");
    }

    @Test
    public void themeSurvivesProcessRestart() throws Exception {
        // The helper runs this in a new process after the rendering test saves Dark Blue.
        launch();
        assertEquals("Dark Blue", Theme.getCurrentTheme().getKey());
        assertFalse(UserConfig.getInstance(0).isClientActivated());
        screenshot("offline-restored-home.png");
    }

    private void launch() throws Exception {
        // First-boot system notifications must not cover the screenshots under test.
        try (ParcelFileDescriptor.AutoCloseInputStream stream = new ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.getUiAutomation().executeShellCommand("cmd statusbar collapse"))) {
            while (stream.read() != -1) { }
        }
        Intent intent = new Intent(instrumentation.getTargetContext(), FeaturesLabActivity.class);
        intent.putExtra("component_tests", true);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity = instrumentation.startActivitySync(intent);
        instrumentation.waitForIdleSync();
        Thread.sleep(500);
        instrumentation.runOnMainSync(() -> assertTrue("Playground must be visible in the foreground", activity.hasWindowFocus()));
    }

    private void click(String label) {
        instrumentation.runOnMainSync(() -> {
            Button button = findButton(activity.getWindow().getDecorView(), label);
            assertNotNull("Missing button: " + label, button);
            assertTrue(button.performClick());
        });
        instrumentation.waitForIdleSync();
    }

    private void screenshot(String name) throws Exception {
        instrumentation.waitForIdleSync();
        Thread.sleep(750); // Allow Telegram's navigation animation to finish.
        Bitmap bitmap = instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        File directory = new File(instrumentation.getTargetContext().getExternalFilesDir(null), "smoke");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        try (FileOutputStream out = new FileOutputStream(new File(directory, name))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
        }
        bitmap.recycle();
    }

    private static Button findButton(View view, String label) {
        if (view instanceof Button && label.contentEquals(((Button) view).getText())) return (Button) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                Button match = findButton(group.getChildAt(i), label);
                if (match != null) return match;
            }
        }
        return null;
    }

    private static <T extends View> T find(View view, Class<T> type) {
        if (type.isInstance(view)) return type.cast(view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                T match = find(group.getChildAt(i), type);
                if (match != null) return match;
            }
        }
        return null;
    }
}
