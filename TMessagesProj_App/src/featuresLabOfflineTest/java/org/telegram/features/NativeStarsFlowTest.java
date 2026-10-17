package org.telegram.features;

import android.Manifest;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.MainTabsActivity;
import org.telegram.ui.SettingsActivity;
import org.telegram.ui.Stars.StarsIntroActivity;
import java.io.File;
import java.io.FileOutputStream;
import static org.junit.Assert.*;

/** Navigation through actual production screens. No replacement screen or direct fragment launch. */
@RunWith(AndroidJUnit4.class)
@androidx.test.filters.SdkSuppress(minSdkVersion = 30)
public class NativeStarsFlowTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private FeaturesLabActivity activity;
    @org.junit.Rule public final org.junit.rules.TestWatcher diagnostics = new org.junit.rules.TestWatcher() {
        @Override protected void failed(Throwable error, org.junit.runner.Description description) {
            if (activity != null) try { screenshot("failure-" + description.getMethodName() + ".png"); }
            catch (Exception captureError) { android.util.Log.e("NativeFlow", "Could not capture failure", captureError); }
        }
    };

    @Test public void settingsToHistory() throws Exception {
        launch();
        screenshot("01-chats.png");
        click("Settings");
        instrumentation.runOnMainSync(() -> assertTrue(((MainTabsActivity) activity.navigation().getLastFragment()).getCurrentVisibleFragment() instanceof SettingsActivity));
        screenshot("02-settings.png");
        click("Telegram Stars");
        instrumentation.runOnMainSync(() -> assertTrue(activity.navigation().getLastFragment() instanceof StarsIntroActivity));
        screenshot("03-my-stars.png");
        swipe(true); // Keep the privacy control above its temporary bottom bulletin.
        click("Make my Stars private");
        instrumentation.runOnMainSync(() -> assertFalse(org.telegram.ui.Stars.StarsPrivacy.isEnabled(0)));
        click("Make my Stars private");
        instrumentation.runOnMainSync(() -> assertTrue(org.telegram.ui.Stars.StarsPrivacy.isEnabled(0)));
        screenshot("03a-privacy-enabled.png");
        scrollBack();
        click("Buy More Stars");
        screenshot("04-purchase.png");
        back();
        click("Make my Stars private");
        instrumentation.runOnMainSync(() -> {
            assertFalse(org.telegram.ui.Stars.StarsPrivacy.isEnabled(0));
            for (org.telegram.tgnet.tl.TL_stars.StarsTransaction receipt : org.telegram.ui.Stars.StarsController.getInstance(0).transactions[0]) {
                assertTrue("Opt-out must not reveal an existing receipt", receipt instanceof org.telegram.ui.Stars.StarsPrivacyMockApi.RedactedTransaction);
            }
        });
        screenshot("05-history-privacy-notice.png");
        click("FL-20261017-0001");
        assertWindowText("Transaction ID");
        screenshot("06-private-receipt.png");
        back();
        click("FL-20261017-0001", true);
        assertWindowText("cannot be undone");
        screenshot("06b-anonymise-one-receipt.png");
        back();
        scrollBack();
        click("Buy More Stars");
        screenshot("04b-purchase-privacy-off.png");
        final int[] privacyLink = new int[2];
        instrumentation.runOnMainSync(() -> {
            org.telegram.ui.Stars.StarsPurchasePrivacyView notice = null;
            for (View window : android.view.inspector.WindowInspector.getGlobalWindowViews()) {
                notice = find(window, org.telegram.ui.Stars.StarsPurchasePrivacyView.class);
                if (notice != null) break;
            }
            assertNotNull(notice);
            android.widget.TextView text = notice.getTextView();
            int offset = text.getText().toString().indexOf("Make my Stars private") + 2;
            assertTrue(offset >= 2);
            android.text.Layout layout = text.getLayout();
            int line = layout.getLineForOffset(offset);
            text.getLocationOnScreen(privacyLink);
            privacyLink[0] += text.getTotalPaddingLeft() + layout.getPrimaryHorizontal(offset);
            privacyLink[1] += text.getTotalPaddingTop() + (layout.getLineTop(line) + layout.getLineBottom(line)) / 2;
        });
        press(privacyLink, false);
        instrumentation.runOnMainSync(() -> assertTrue(org.telegram.ui.Stars.StarsPrivacy.isEnabled(0)));
        screenshot("04c-purchase-privacy-enabled.png");
        back();
        back();
        scrollBack();
        click("Privacy & Security");
        screenshot("07-privacy-security.png");
        click("Stars Privacy");
        instrumentation.runOnMainSync(() -> assertTrue(activity.navigation().getLastFragment() instanceof org.telegram.ui.Stars.StarsPrivacyActivity));
        screenshot("08-stars-privacy-settings.png");
        click("Anonymise history");
        screenshot("09-anonymise-history.png");
        final int[] confirm = new int[2];
        instrumentation.runOnMainSync(() -> {
            org.telegram.ui.ActionBar.AlertDialog dialog = (org.telegram.ui.ActionBar.AlertDialog) activity.navigation().getLastFragment().getVisibleDialog();
            View button = dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
            button.getLocationOnScreen(confirm);
            confirm[0] += button.getWidth() / 2;
            confirm[1] += button.getHeight() / 2;
        });
        press(confirm, false);
        screenshot("09a-history-anonymised.png");
        assertFalse(UserConfig.getInstance(0).isClientActivated());
        assertEquals(PackageManager.PERMISSION_DENIED, activity.checkSelfPermission(Manifest.permission.INTERNET));
        assertEquals(PackageManager.PERMISSION_DENIED, activity.checkSelfPermission("com.android.vending.BILLING"));
    }

    @Test public void channelReactionAndAdmin() throws Exception {
        launch();
        instrumentation.runOnMainSync(() -> org.telegram.ui.Stars.StarsPrivacyMockApi.setChannelPrivate(0, -OfflineTelegramData.CHANNEL, false));
        final int[] chat = new int[2];
        instrumentation.runOnMainSync(() -> {
            View row = find(activity.navigation().getLastFragment().getFragmentView(), org.telegram.ui.Cells.DialogCell.class);
            assertNotNull("Native channel row", row);
            row.getLocationOnScreen(chat);
            chat[0] += row.getWidth() / 2;
            chat[1] += row.getHeight() / 2;
        });
        press(chat, false);
        screenshot("10-channel.png");
        final int[] before = new int[1];
        instrumentation.runOnMainSync(() -> before[0] = org.telegram.ui.Stars.StarsController.getInstance(0).transactions[0].size());
        final int[] point = postStarPoint();
        press(point, true);
        instrumentation.runOnMainSync(() -> assertNotNull("A hold opens the native post sheet", starsSheetButton()));
        screenshot("11-post-reaction-sheet.png");
        back();
        press(point, false);
        screenshot("12-post-undo.png");
        Thread.sleep(21000);
        instrumentation.runOnMainSync(() -> {
            java.util.ArrayList<org.telegram.tgnet.tl.TL_stars.StarsTransaction> history = org.telegram.ui.Stars.StarsController.getInstance(0).transactions[0];
            assertEquals("Exactly one post receipt", before[0] + 1, history.size());
            assertEquals(-1, history.get(0).amount.amount);
        });
        screenshot("13-post-sent.png");
        click("Design Notes");
        screenshot("14-channel-profile.png");
        click("Edit");
        screenshot("15-edit-channel.png");
        click("Reactions");
        instrumentation.runOnMainSync(() -> assertTrue(activity.navigation().getLastFragment() instanceof org.telegram.ui.Components.Reactions.ChatCustomReactionsEditActivity));
        back(); // Close the production emoji keyboard before scrolling the settings.
        screenshot("16-channel-reactions.png");
        click("Keep Stars anonymous");
        instrumentation.runOnMainSync(() -> assertFalse("Draft must not change channel policy", org.telegram.ui.Stars.StarsPrivacyMockApi.isChannelPrivate(0, -OfflineTelegramData.CHANNEL)));
        back();
        assertWindowText("Discard");
        click("Discard");
        instrumentation.runOnMainSync(() -> assertFalse("Discard keeps the saved policy", org.telegram.ui.Stars.StarsPrivacyMockApi.isChannelPrivate(0, -OfflineTelegramData.CHANNEL)));
        click("Reactions");
        back();
        click("Keep Stars anonymous");
        screenshot("17-channel-privacy.png");
        click("Update Reactions");
        instrumentation.runOnMainSync(() -> {
            assertTrue(org.telegram.ui.Stars.StarsPrivacyMockApi.isChannelPrivate(0, -OfflineTelegramData.CHANNEL));
            assertTrue(org.telegram.ui.Stars.StarsPrivacyMockApi.showsPercentile(0, -OfflineTelegramData.CHANNEL));
        });
        click("Reactions");
        back();
        instrumentation.runOnMainSync(() -> {
            org.telegram.ui.Cells.TextCheckCell control = activity.navigation().getLastFragment().getFragmentView().findViewWithTag("stars_channel_private");
            assertNotNull(control);
            assertTrue("Reopening the native admin panel keeps the saved setting", control.isChecked());
        });
        click("Enable Paid Reactions");
        instrumentation.runOnMainSync(() -> {
            org.telegram.ui.Cells.TextCheckCell control = activity.navigation().getLastFragment().getFragmentView().findViewWithTag("stars_channel_private");
            assertFalse("Privacy cannot be edited while Stars are disabled", control.isEnabled());
            assertTrue("Disabling Stars preserves the privacy preference", control.isChecked());
            control.performClick();
            assertTrue("Disabled control ignores programmatic clicks", control.isChecked());
        });
        click("Enable Paid Reactions");
        back(); // Edit
        back(); // Profile
        back(); // Channel
        final boolean[] keyboard = new boolean[1];
        instrumentation.runOnMainSync(() -> keyboard[0] = activity.getWindow().getDecorView().getRootWindowInsets().isVisible(android.view.WindowInsets.Type.ime()));
        if (keyboard[0]) back();
        instrumentation.runOnMainSync(() -> org.telegram.ui.Stars.StarsPrivacy.setEnabled(0, false));
        press(postStarPoint(), true);
        sendStarsFromSheet(); // Native slider's default 50 Stars, with the same 20-second Undo.
        Thread.sleep(21000);
        instrumentation.runOnMainSync(() -> {
            java.util.ArrayList<org.telegram.tgnet.tl.TL_stars.StarsTransaction> history = org.telegram.ui.Stars.StarsController.getInstance(0).transactions[0];
            assertEquals(before[0] + 2, history.size());
            assertEquals(-50, history.get(0).amount.amount);
            assertTrue("Channel default protects receipts with personal privacy off", history.get(0) instanceof org.telegram.ui.Stars.StarsPrivacyMockApi.RedactedTransaction);
            org.telegram.ui.Cells.ChatMessageCell cell = find(activity.navigation().getLastFragment().getFragmentView(), org.telegram.ui.Cells.ChatMessageCell.class);
            for (org.telegram.tgnet.TLRPC.ReactionCount reaction : cell.getMessageObject().messageOwner.reactions.results) {
                if (reaction.reaction instanceof org.telegram.tgnet.TLRPC.TL_reactionPaid)
                    assertFalse(org.telegram.ui.Stars.StarsPrivacy.showSelectedState(0, cell.getMessageObject(), reaction));
            }
            org.telegram.ui.Stars.StarsPrivacy.setEnabled(0, true);
        });
        press(postStarPoint(), true);
        instrumentation.runOnMainSync(() -> {
            org.telegram.ui.Stars.StarsDonorPercentileView percentile = null;
            for (View window : android.view.inspector.WindowInspector.getGlobalWindowViews()) {
                assertNull("Private channels have no named leaderboard", find(window, org.telegram.ui.Stars.StarsReactionsSheet.TopSendersView.class));
                org.telegram.ui.Stars.StarsDonorPercentileView candidate = find(window, org.telegram.ui.Stars.StarsDonorPercentileView.class);
                if (candidate != null) percentile = candidate;
            }
            assertNotNull("Own percentile is visible with personal privacy on", percentile);
            assertEquals(50, percentile.getPercentile());
        });
        screenshot("18-channel-percentile.png");
    }

    @Test public void liveStoryStars() throws Exception {
        launch();
        instrumentation.runOnMainSync(() -> org.telegram.ui.Stars.StarsPrivacyMockApi.setChannelPrivate(0, -OfflineTelegramData.CHANNEL, true));
        final int[] avatar = new int[2];
        instrumentation.runOnMainSync(() -> {
            View row = find(activity.navigation().getLastFragment().getFragmentView(), org.telegram.ui.Cells.DialogCell.class);
            assertNotNull(row);
            row.getLocationOnScreen(avatar);
            avatar[0] += org.telegram.messenger.AndroidUtilities.dp(36);
            avatar[1] += row.getHeight() / 2;
        });
        press(avatar, false);
        final org.telegram.ui.Stories.StoryViewer[] viewer = new org.telegram.ui.Stories.StoryViewer[1];
        final int[] point = new int[2];
        instrumentation.runOnMainSync(() -> {
            BaseFragment visible = ((MainTabsActivity) activity.navigation().getLastFragment()).getCurrentVisibleFragment();
            viewer[0] = visible.getLastStoryViewer();
            assertNotNull("Real story viewer opened from the story strip", viewer[0]);
        });
        final int[] introPoint = {-1, -1};
        instrumentation.runOnMainSync(() -> {
            View intro = find(viewer[0].getWindowView(), org.telegram.ui.Stories.StoriesIntro.class);
            if (intro != null) {
                intro.getLocationOnScreen(introPoint);
                introPoint[0] += intro.getWidth() / 2;
                introPoint[1] += intro.getHeight() / 2;
            }
        });
        if (introPoint[0] >= 0) press(introPoint, false);
        instrumentation.runOnMainSync(() -> {
            View star = find(viewer[0].getWindowView(), org.telegram.ui.Stories.PaidReactionButton.class);
            assertNotNull("Real live Star button", star);
            star.getLocationOnScreen(point);
            point[0] += star.getWidth() / 2;
            point[1] += star.getHeight() / 2;
        });
        screenshot("20-live-story.png");
        final int[] before = new int[1];
        final long[] totalBefore = new long[1];
        instrumentation.runOnMainSync(() -> before[0] = org.telegram.ui.Stars.StarsController.getInstance(0).transactions[0].size());
        instrumentation.runOnMainSync(() -> totalBefore[0] = find(viewer[0].getWindowView(), org.telegram.ui.Stories.LiveCommentsView.class).getStarsCount());
        press(point, true);
        instrumentation.runOnMainSync(() -> assertNotNull("A hold opens the native live sheet", starsSheetButton()));
        screenshot("21-live-stars-sheet.png");
        back();
        press(point, false);
        screenshot("22-live-undo.png");
        Thread.sleep(21000);
        Thread.sleep(6000); // Include the real five-second totals refresh after commit.
        screenshot("23-live-sent.png");
        instrumentation.runOnMainSync(() -> {
            java.util.ArrayList<org.telegram.tgnet.tl.TL_stars.StarsTransaction> history = org.telegram.ui.Stars.StarsController.getInstance(0).transactions[0];
            assertEquals("Exactly one live receipt", before[0] + 1, history.size());
            assertEquals(-1, history.get(0).amount.amount);
            assertEquals("A refresh keeps the completed reaction in the aggregate", totalBefore[0] + 1,
                find(viewer[0].getWindowView(), org.telegram.ui.Stories.LiveCommentsView.class).getStarsCount());
        });
        instrumentation.runOnMainSync(() -> {
            View star = find(viewer[0].getWindowView(), org.telegram.ui.Stories.PaidReactionButton.class);
            assertNotNull(star);
            star.getLocationOnScreen(point);
            point[0] += star.getWidth() / 2;
            point[1] += star.getHeight() / 2;

        });
        press(point, true);
        instrumentation.runOnMainSync(() -> {
            org.telegram.ui.Stars.StarsDonorPercentileView percentile = null;
            for (View window : android.view.inspector.WindowInspector.getGlobalWindowViews()) {
                org.telegram.ui.Stars.StarsDonorPercentileView candidate = find(window, org.telegram.ui.Stars.StarsDonorPercentileView.class);
                if (candidate != null) percentile = candidate;
            }
            assertNotNull("Live percentile must be rendered in the sheet", percentile);
            assertEquals(100, percentile.getPercentile());
            assertEquals("Opening the live sheet must not send another reaction", before[0] + 1, org.telegram.ui.Stars.StarsController.getInstance(0).transactions[0].size());
        });
        screenshot("24-live-percentile.png");
        instrumentation.runOnMainSync(() -> assertEquals(100, org.telegram.ui.Stars.StarsPrivacyMockApi.donorPercentile(0, -OfflineTelegramData.CHANNEL, 0)));
        instrumentation.runOnMainSync(() -> assertFalse(UserConfig.getInstance(0).isClientActivated()));
    }

    @Test public void darkSettings() throws Exception {
        launch("Dark Blue");
        click("Settings");
        click("Telegram Stars");
        screenshot("30-my-stars-dark.png");
        swipe(true);
        screenshot("31-history-dark.png");
    }

    private View starsSheetButton() {
        View button = null;
        for (View window : android.view.inspector.WindowInspector.getGlobalWindowViews()) {
            if (window == activity.getWindow().getDecorView()) continue;
            View candidate = find(window, org.telegram.ui.Stories.recorder.ButtonWithCounterView.class);
            if (candidate != null) button = candidate;
        }
        return button;
    }

    private void sendStarsFromSheet() throws Exception {
        final int[] point = new int[2];
        instrumentation.runOnMainSync(() -> {
            View button = starsSheetButton();
            assertNotNull("Native Stars sheet send button", button);
            button.getLocationOnScreen(point);
            point[0] += button.getWidth() / 2;
            point[1] += button.getHeight() / 2;
        });
        press(point, false);
    }

    private int[] postStarPoint() {
        final int[] point = new int[2];
        instrumentation.runOnMainSync(() -> {
            assertTrue(activity.navigation().getLastFragment() instanceof org.telegram.ui.ChatActivity);
            org.telegram.ui.Cells.ChatMessageCell cell = find(activity.navigation().getLastFragment().getFragmentView(), org.telegram.ui.Cells.ChatMessageCell.class);
            assertNotNull("Real channel post", cell);
            org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble layout = cell.reactionsLayoutInBubble;
            org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble.ReactionButton star = layout.getReactionButton(org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble.VisibleReaction.asStar());
            assertNotNull("Paid reaction in the real post", star);
            cell.getLocationOnScreen(point);
            point[0] += layout.x + star.x + star.width / 2;
            point[1] += layout.y + star.y + star.height / 2;
        });
        return point;
    }

    private void press(int[] point, boolean hold) throws Exception {
        assertTrue("Tap must be on screen", point[0] >= 0 && point[0] < activity.getResources().getDisplayMetrics().widthPixels
            && point[1] >= 0 && point[1] < activity.getResources().getDisplayMetrics().heightPixels);
        int initialWindow = -1;
        if (hold) {
            AccessibilityNodeInfo root = instrumentation.getUiAutomation().getRootInActiveWindow();
            assertNotNull(root);
            initialWindow = root.getWindowId();
            root.recycle();
        }
        long down = SystemClock.uptimeMillis();
        android.view.MotionEvent event = android.view.MotionEvent.obtain(down, down, android.view.MotionEvent.ACTION_DOWN, point[0], point[1], 0);
        event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        assertTrue(instrumentation.getUiAutomation().injectInputEvent(event, true));
        event.recycle();
        boolean opened = !hold;
        if (hold) {
            // Keep holding until Android opens the native sheet/dialog. Live rendering
            // can delay its long-press callback; releasing on a fixed timer can add a Star.
            long deadline = SystemClock.uptimeMillis() + 8000;
            while (!opened && SystemClock.uptimeMillis() < deadline) {
                Thread.sleep(100);
                AccessibilityNodeInfo root = instrumentation.getUiAutomation().getRootInActiveWindow();
                if (root != null) {
                    opened = root.getWindowId() != initialWindow;
                    root.recycle();
                }
            }
        } else {
            Thread.sleep(70);
        }
        event = android.view.MotionEvent.obtain(down, SystemClock.uptimeMillis(), opened ? android.view.MotionEvent.ACTION_UP : android.view.MotionEvent.ACTION_CANCEL, point[0], point[1], 0);
        event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        assertTrue(instrumentation.getUiAutomation().injectInputEvent(event, true));
        event.recycle();
        assertTrue("Held control must open its native sheet or dialog", opened);
        settle();
    }

    private static <T> T find(View view, Class<T> type) {
        if (!view.isShown() || !view.getGlobalVisibleRect(new android.graphics.Rect())) return null;
        if (type.isInstance(view)) return type.cast(view);
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            T found = find(((ViewGroup) view).getChildAt(i), type);
            if (found != null) return found;
        }
        return null;
    }

    private void back() throws Exception { instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK); settle(); }
    private void scrollBack() throws Exception { for (int i = 0; i < 3; i++) swipe(false); }
    private void swipe(boolean forward) throws Exception {
        int width = activity.getResources().getDisplayMetrics().widthPixels;
        int height = activity.getResources().getDisplayMetrics().heightPixels;
        float start = height * (forward ? .72f : .3f);
        float end = height * (forward ? .3f : .72f);
        long down = SystemClock.uptimeMillis();
        for (int step = 0; step <= 16; step++) {
            int action = step == 0 ? android.view.MotionEvent.ACTION_DOWN : step == 16 ? android.view.MotionEvent.ACTION_UP : android.view.MotionEvent.ACTION_MOVE;
            android.view.MotionEvent event = android.view.MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, width / 2f, start + (end - start) * step / 16, 0);
            instrumentation.getUiAutomation().injectInputEvent(event, true);
            event.recycle();
            Thread.sleep(20);
        }
        settle();
    }

    private void launch() throws Exception { launch("Blue"); }
    private void launch(String theme) throws Exception {
        instrumentation.runOnMainSync(() -> Theme.applyTheme(Theme.getTheme(theme)));
        Intent intent = new Intent(instrumentation.getTargetContext(), FeaturesLabActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity = (FeaturesLabActivity) instrumentation.startActivitySync(intent);
        settle();
        instrumentation.runOnMainSync(() -> org.telegram.ui.Stars.StarsPrivacy.setEnabled(0, true));
    }

    private void assertWindowText(String text) {
        AccessibilityNodeInfo root = instrumentation.getUiAutomation().getRootInActiveWindow();
        assertNotNull(root);
        assertFalse("Visible window must contain: " + text, root.findAccessibilityNodeInfosByText(text).isEmpty());
    }

    private void click(String label) throws Exception { click(label, false); }
    private void click(String label, boolean hold) throws Exception {
        android.util.Log.i("NativeFlow", "click " + label);
        for (int attempt = 0; attempt < 6; attempt++) {
            final int[] match = { -1, -1 };
            instrumentation.runOnMainSync(() -> {
                View view = label(activity.getWindow().getDecorView(), label);
                if (view != null) {
                    android.graphics.Rect bounds = new android.graphics.Rect();
                    if (view.getGlobalVisibleRect(bounds) && bounds.height() >= 20 && !behindTabs(label, bounds)) {
                        match[0] = bounds.centerX();
                        match[1] = bounds.centerY();
                    }
                }
            });
            if (match[0] >= 0) {
                if (label.equals("Telegram Stars") && !Theme.isCurrentThemeDark()) screenshot("02a-settings-stars-entry.png");
                if (label.startsWith("FL-")) screenshot("05a-transaction-history.png");
                press(match, hold); return;
            }

            AccessibilityNodeInfo root = instrumentation.getUiAutomation().getRootInActiveWindow();
            if (root != null) for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(label)) {
                if (!node.isVisibleToUser()) continue;
                android.graphics.Rect bounds = new android.graphics.Rect();
                node.getBoundsInScreen(bounds);
                if (bounds.height() < 20 || behindTabs(label, bounds)) continue;
                press(new int[]{bounds.centerX(), bounds.centerY()}, hold);
                return;
            }
            swipe(true);
            settle();
        }
        screenshot("failure.png");
        fail("No visible native control: " + label);
    }

    private boolean behindTabs(String label, android.graphics.Rect bounds) {
        if (label.equals("Settings") || label.equals("Chats") || label.equals("Update Reactions")) return false;
        BaseFragment fragment = activity.navigation().getLastFragment();
        View overlay = null;
        if (fragment instanceof MainTabsActivity) overlay = find(fragment.getFragmentView(), org.telegram.ui.MainTabsLayout.class);
        else if (fragment instanceof org.telegram.ui.Components.Reactions.ChatCustomReactionsEditActivity)
            overlay = find(fragment.getFragmentView(), org.telegram.ui.Components.Reactions.UpdateReactionsButton.class);
        android.graphics.Rect covering = new android.graphics.Rect();
        return overlay != null && overlay.getGlobalVisibleRect(covering) && bounds.bottom >= covering.top;
    }

    private View label(View view, String label) {
        if (!view.isShown()) return null;
        CharSequence text = view.getContentDescription();
        if (view instanceof android.widget.TextView) text = ((android.widget.TextView) view).getText();
        else if (view instanceof org.telegram.ui.ActionBar.SimpleTextView) text = ((org.telegram.ui.ActionBar.SimpleTextView) view).getText();
        else if (view instanceof org.telegram.ui.Components.AnimatedTextView) text = ((org.telegram.ui.Components.AnimatedTextView) view).getText();
        android.graphics.Rect bounds = new android.graphics.Rect();
        if (text != null && text.toString().contains(label) && view.getGlobalVisibleRect(bounds) && bounds.height() >= 20) return view;
        if (view instanceof ViewGroup) for (int i = ((ViewGroup) view).getChildCount() - 1; i >= 0; i--) {
            View match = label(((ViewGroup) view).getChildAt(i), label);
            if (match != null) return match;
        }
        return null;
    }

    private void settle() throws Exception { instrumentation.runOnMainSync(() -> {}); Thread.sleep(1100); }
    private void screenshot(String name) throws Exception {
        android.util.Log.i("NativeFlow", "screenshot " + name);
        Bitmap bitmap = instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        File directory = new File(activity.getExternalFilesDir(null), "native-ui");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        try (FileOutputStream stream = new FileOutputStream(new File(directory, name))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        }
        bitmap.recycle();
    }
}
