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
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble;
import org.telegram.ui.Stars.PaidReactionUndo;
import org.telegram.ui.Stars.StarsController;
import org.telegram.ui.Stars.StarsIntroActivity;
import org.telegram.ui.Stars.StarsPrivacy;
import org.telegram.ui.Stars.StarsPrivacyMockApi;
import org.telegram.ui.Stars.StarsHistoryPrivacy;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Stars.StarsReactionsSheet;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.*;

/** Runs real Telegram views and the production deadline model without a login or network. */
@RunWith(AndroidJUnit4.class)
public class StarsPrivacyTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private Activity activity;

    @Test public void nativePrivacyScreensAndRecycledRows() throws Exception {
        launch("Blue");
        StarsPrivacyPreview preview = preview();
        instrumentation.runOnMainSync(() -> {
            assertEquals(UserObject.ANONYMOUS, StarsPrivacy.resolvePeer(0, 1234));
            assertEquals(UserObject.ANONYMOUS, StarsController.getInstance(0).getPaidReactionsDialogId((org.telegram.messenger.MessageObject) null));
            StarsPrivacy.setEnabled(1, false);
            assertTrue(StarsPrivacy.isEnabled(0));
            assertFalse(StarsPrivacy.isEnabled(1));
            StarsPrivacy.setEnabled(1, true);
            assertFalse(reaction().choosen);
            assertTrue(messageCell().getMessageObject().messageOwner.reactions.results.get(0).chosen);
            View row = tagged("FL-20261017-0001");
            assertEquals(Arrays.asList("FL-20261017-0001", LocaleController.formatShortDateTime(1792227600)), texts(row));
        });
        screenshot("stars-private-light.png");

        final StarsReactionsSheet[] sheet = new StarsReactionsSheet[1];
        instrumentation.runOnMainSync(() -> sheet[0] = preview.showReactionSheet());
        settle();
        instrumentation.runOnMainSync(() -> {
            assertNull(find(sheet[0].getContainer(), StarsReactionsSheet.TopSendersView.class));
            assertEquals(UserObject.ANONYMOUS, sheet[0].peer);
        });
        screenshot("stars-private-reaction-sheet.png");
        instrumentation.runOnMainSync(() -> sheet[0].dismiss());
        settle();

        final BottomSheet[] details = new BottomSheet[1];
        instrumentation.runOnMainSync(() -> {
            TL_stars.TL_starsTransaction transaction = new TL_stars.TL_starsTransaction();
            transaction.id = "test-receipt-1";
            transaction.date = 1792227600;
            transaction.amount = TL_stars.StarsAmount.ofStars(-999);
            transaction.peer = new TL_stars.TL_starsTransactionPeer();
            transaction.peer.peer = new TLRPC.TL_peerChannel();
            transaction.peer.peer.channel_id = 7770001;
            transaction.title = "Sensitive transaction title";
            details[0] = StarsIntroActivity.showTransactionSheet(activity, false, 0, 0, transaction, null);
        });
        settle();
        instrumentation.runOnMainSync(() -> {
            String visible = texts(details[0].getContainer()).toString();
            assertTrue(visible.contains("test-receipt-1"));
            assertFalse(visible.contains("Sensitive"));
            assertFalse(visible.contains("999"));
            assertFalse(visible.contains("Design Notes"));
        });
        screenshot("stars-private-transaction.png");
        instrumentation.runOnMainSync(() -> details[0].dismiss());
        settle();

        clickTag("stars_privacy_settings");
        screenshot("stars-privacy-settings-light.png");
        clickTag("stars_privacy_toggle");
        instrumentation.runOnMainSync(() -> assertFalse(StarsPrivacy.isEnabled(0)));
        back();
        instrumentation.runOnMainSync(() -> {
            assertTrue(reaction().choosen);
            assertTrue(texts(tagged("FL-20261017-0001")).toString().contains("Design Notes"));
        });
        screenshot("stars-standard-light.png");
        instrumentation.runOnMainSync(() -> sheet[0] = preview.showReactionSheet());
        settle();
        instrumentation.runOnMainSync(() -> assertNotNull(find(sheet[0].getContainer(), StarsReactionsSheet.TopSendersView.class)));
        screenshot("stars-standard-reaction-sheet.png");
        instrumentation.runOnMainSync(() -> sheet[0].dismiss());
        settle();
        clickTag("stars_privacy_settings");
        clickTag("stars_privacy_toggle");
        back();
        instrumentation.runOnMainSync(() -> {
            assertFalse(reaction().choosen);
            assertEquals(2, texts(tagged("FL-20261017-0001")).size());
            assertFalse(texts(tagged("FL-20261017-0001")).toString().contains("Design Notes"));
        });
        launch("Dark Blue");
        screenshot("stars-private-dark.png");
        clickTag("stars_privacy_settings");
        screenshot("stars-privacy-settings-dark.png");
    }

    @Test public void twentySecondUndoAndLiveMock() throws Exception {
        launch("Blue");
        StarsPrivacyPreview preview = preview();
        instrumentation.runOnMainSync(() -> preview.sendSampleReaction(1));
        Thread.sleep(6500);
        instrumentation.runOnMainSync(() -> assertEquals("The old five-second deadline must not send", 0, preview.completedPayments));
        screenshot("stars-twenty-second-undo.png");
        Thread.sleep(9500);
        clickTag("stars_privacy_undo");
        Thread.sleep(4500);
        instrumentation.runOnMainSync(() -> assertEquals("Undo after 16 seconds cancels the payment", 0, preview.completedPayments));

        final StarsReactionsSheet[] live = new StarsReactionsSheet[1];
        instrumentation.runOnMainSync(() -> live[0] = preview.showLiveReactionSheet());
        settle();
        screenshot("stars-private-live-sheet.png");
        instrumentation.runOnMainSync(() -> {
            assertNull(find(live[0].getContainer(), StarsReactionsSheet.TopSendersView.class));
            live[0].setValue(25);
            ButtonWithCounterView send = find(live[0].getContainer(), ButtonWithCounterView.class);
            assertNotNull(send);
            assertTrue(send.performClick());
        });
        Thread.sleep(6500);
        instrumentation.runOnMainSync(() -> assertEquals(0, preview.completedPayments));
        Thread.sleep(14500);
        instrumentation.runOnMainSync(() -> {});
        instrumentation.runOnMainSync(() -> {
            assertEquals(1, preview.completedPayments);
            assertEquals(UserObject.ANONYMOUS, preview.lastPaymentPeer);
            assertTrue(texts(activity.getWindow().getDecorView()).toString().contains("preview-"));
            assertFalse(UserConfig.getInstance(0).isClientActivated());
        });
        screenshot("stars-private-live-receipt.png");
        instrumentation.runOnMainSync(() -> preview.sendSampleReaction(1));
        back(); // Leaving the reaction screen cancels instead of submitting early.
        Thread.sleep(21000);
        instrumentation.runOnMainSync(() -> assertEquals(1, preview.completedPayments));
    }

    @Test public void productionPendingReactionCancelsSafely() throws Exception {
        launch("Blue");
        final StarsController.PendingPaidReactions[] mock = new StarsController.PendingPaidReactions[1];
        instrumentation.runOnMainSync(() -> {
            StarsPrivacyPreview preview = preview();
            org.telegram.ui.ChatActivity chat = new org.telegram.ui.ChatActivity(new android.os.Bundle());
            chat.setParentLayout(preview.getParentLayout());
            chat.fragmentView = preview.fragmentView;
            org.telegram.messenger.MessageObject message = messageCell().getMessageObject();
            int originalCount = message.messageOwner.reactions.results.get(0).count;
            StarsController controller = StarsController.getInstance(0);
            StarsController.PendingPaidReactions pending = controller.new PendingPaidReactions(
                StarsController.MessageId.from(message), message, chat, 0, false);
            controller.currentPendingReactions = pending;
            pending.peer = 1234L; // Even an explicit public send-as cannot bypass privacy.
            assertEquals(UserObject.ANONYMOUS, pending.getPeerId());
            pending.add(3, true);
            assertEquals(3, controller.minus);
            controller.updateBalance(TL_stars.StarsAmount.ofStars(300));
            assertEquals("A balance refresh must retain the pending reservation", 3, controller.minus);
            pending.close();
            pending.commit();
            assertFalse("Opening a reaction sheet must not commit early", pending.committed);
            pending.add(2, false);
            pending.apply();
            assertEquals(originalCount + 5, message.messageOwner.reactions.results.get(0).count);
            pending.cancel();
            pending.cancel();
            pending.apply();
            pending.commit();
            assertTrue(pending.cancelled);
            assertFalse(pending.committed);
            assertNull(controller.currentPendingReactions);
            assertEquals(0, controller.minus);
            assertEquals(originalCount, message.messageOwner.reactions.results.get(0).count);
            assertFalse(UserConfig.getInstance(0).isClientActivated());
            StarsPrivacyMockApi.setChannelPrivate(0, message.getDialogId(), true);
            mock[0] = controller.new PendingPaidReactions(StarsController.MessageId.from(message), message, chat, 0, true);
            controller.currentPendingReactions = mock[0];
            mock[0].peer = 1234L;
            mock[0].add(4, true);
            // An in-flight Preview must never become a real payment when settings change.
            StarsPrivacyMockApi.setChannelPrivate(0, message.getDialogId(), false);
            StarsPrivacy.setEnabled(0, false);
            assertEquals(UserObject.ANONYMOUS, mock[0].getPeerId());
        });
        Thread.sleep(21000);
        instrumentation.runOnMainSync(() -> {
            StarsController controller = StarsController.getInstance(0);
            assertTrue(mock[0].committed);
            assertEquals(0, controller.minus);
            assertEquals(300, controller.getBalance(false));
            assertEquals(1, controller.transactions[StarsController.ALL_TRANSACTIONS].size());
            assertTrue(controller.transactions[StarsController.ALL_TRANSACTIONS].get(0) instanceof StarsPrivacyMockApi.RedactedTransaction);
            StarsPrivacy.setEnabled(0, true);
        });
    }

    @Test public void deadlineAndMockContract() {
        PaidReactionUndo pending = new PaidReactionUndo();
        assertFalse(pending.commit(50000));
        pending.restart(1000);
        assertFalse(pending.commit(20999));
        assertEquals(1, pending.remaining(20999));
        assertTrue(pending.commit(21000));
        assertFalse(pending.commit(21000));
        assertFalse(pending.cancel());
        PaidReactionUndo cancelled = new PaidReactionUndo();
        cancelled.restart(1000);
        cancelled.restart(15000); // Another tap gets a fresh twenty seconds.
        assertFalse(cancelled.commit(21000));
        assertTrue(cancelled.cancel());
        assertFalse(cancelled.commit(100000));
        cancelled.restart(200000);
        assertFalse(cancelled.commit(300000));
        StarsPrivacyMockApi.Receipt receipt = StarsPrivacyMockApi.sendAnonymousLiveReaction(25);
        assertEquals(UserObject.ANONYMOUS, receipt.sender);
        TL_stars.StarsTransaction transaction = receipt.asTransaction();
        assertTrue(transaction.id.startsWith("preview-"));
        assertTrue(transaction.date > 0);
        assertNull(transaction.peer.peer);
        assertNull(transaction.title);
        assertEquals(0, transaction.amount.amount);
    }

    @Test public void channelPolicyAndIrreversibleHistory() throws Exception {
        launch("Blue");
        clickTag("stars_privacy_settings");
        clickTag("stars_privacy_toggle");
        back();
        clickTag("stars_channel_settings");
        clickTag("stars_channel_private");
        screenshot("stars-channel-admin.png");
        instrumentation.runOnMainSync(() -> {
            assertTrue(StarsPrivacyMockApi.isChannelPrivate(0, -7770001));
            assertFalse(StarsPrivacyMockApi.isChannelPrivate(1, -7770001));
            assertEquals(UserObject.ANONYMOUS, StarsPrivacy.resolvePeer(0, -7770001, 1234));
            assertEquals(1234, StarsPrivacy.resolvePeer(0, -7770002, 1234));
        });
        clickTag("stars_channel_percentile");
        instrumentation.runOnMainSync(() -> assertFalse(StarsPrivacyMockApi.showsPercentile(0, -7770001)));
        clickTag("stars_channel_percentile");
        back();
        final StarsReactionsSheet[] sheet = new StarsReactionsSheet[1];
        instrumentation.runOnMainSync(() -> {
            assertFalse(reaction().choosen);
            sheet[0] = preview().showReactionSheet();
            assertEquals(UserObject.ANONYMOUS, sheet[0].peer);
            assertNull(find(sheet[0].getContainer(), StarsReactionsSheet.TopSendersView.class));
        });
        screenshot("stars-channel-private-sheet.png");
        instrumentation.runOnMainSync(() -> sheet[0].dismiss());
        settle();
        instrumentation.runOnMainSync(() -> {
            StarsPrivacyMockApi.Receipt receipt = StarsPrivacyMockApi.sendChannelReaction(0, -7770001, 101, 50);
            assertTrue(receipt.asTransaction() instanceof StarsPrivacyMockApi.RedactedTransaction);
            assertTrue(StarsPrivacyMockApi.showHighlight(0, -7770001, 101));
            assertEquals(50, StarsPrivacyMockApi.donorPercentile(0, -7770001, 101));
            assertTrue(StarsPrivacyMockApi.isHighlightActive(299999, 300000));
            assertFalse(StarsPrivacyMockApi.isHighlightActive(300000, 300000));
            assertFalse(StarsPrivacyMockApi.isHighlightActive(1, 0));
            assertEquals(0, StarsPrivacyMockApi.percentileBucket(5, new long[] {1, 2}));
            assertEquals(10, StarsPrivacyMockApi.percentileBucket(100, new long[] {5,10,15,20,25,30,40,50,75,100}));
        });
        settle();
        instrumentation.runOnMainSync(() -> assertTrue(reaction().choosen));
        screenshot("stars-private-donor-percentile.png");
        instrumentation.runOnMainSync(() -> StarsPrivacy.setEnabled(0, true));
        settle();
        instrumentation.runOnMainSync(() -> {
            assertFalse(reaction().choosen);
            assertEquals(View.GONE, tagged("stars_donor_percentile").getVisibility());
            StarsPrivacyMockApi.setChannelPrivate(0, -7770001, false);
            StarsPrivacy.setEnabled(0, false);
        });
        TL_stars.TL_starsTransaction record = new TL_stars.TL_starsTransaction();
        record.id = "persist-redaction-test";
        record.date = 1792227600;
        record.title = "Private recipient";
        record.amount = TL_stars.StarsAmount.ofStars(-125);
        record.peer = new TL_stars.TL_starsTransactionPeer();
        record.peer.peer = new TLRPC.TL_peerChannel();
        record.peer.peer.channel_id = 7770001;
        final AlertDialog[] confirmation = new AlertDialog[1];
        instrumentation.runOnMainSync(() -> confirmation[0] = StarsHistoryPrivacy.confirm(activity, 0, record, null, null));
        screenshot("stars-anonymise-receipt-confirmation.png");
        instrumentation.runOnMainSync(() -> confirmation[0].getButton(AlertDialog.BUTTON_POSITIVE).performClick());
        settle();
        instrumentation.runOnMainSync(() -> {
            TL_stars.StarsTransaction redacted = StarsPrivacyMockApi.project(0, record);
            assertEquals(record.id, redacted.id);
            assertEquals(record.date, redacted.date);
            assertNull(redacted.title);
            assertNull(redacted.peer.peer);
            assertEquals(0, redacted.amount.amount);
            assertTrue(StarsPrivacyMockApi.isAnonymised(0, record));
            assertTrue("Direction tabs must not reveal anonymised records", StarsPrivacyMockApi.hasPrivateHistory(0));
            assertFalse(StarsPrivacyMockApi.isAnonymised(1, record));
            confirmation[0] = StarsHistoryPrivacy.confirm(activity, 0, null, null, null);
        });
        screenshot("stars-anonymise-history-confirmation.png");
        instrumentation.runOnMainSync(() -> confirmation[0].getButton(AlertDialog.BUTTON_POSITIVE).performClick());
        settle();
        instrumentation.runOnMainSync(() -> {
            TL_stars.TL_starsTransaction unloaded = new TL_stars.TL_starsTransaction();
            unloaded.id = "unloaded-page";
            unloaded.date = 1792227600;
            assertTrue(StarsPrivacyMockApi.isAnonymised(0, unloaded));
            unloaded.date = (int) (System.currentTimeMillis() / 1000) + 10;
            assertFalse(StarsPrivacyMockApi.isAnonymised(0, unloaded));
            StarsPrivacy.setEnabled(0, true);
        });
    }

    @Test public void privacySurvivesProcessRestart() throws Exception {
        launch(null);
        instrumentation.runOnMainSync(() -> {
            assertTrue(StarsPrivacy.isEnabled(0));
            assertFalse(reaction().choosen);
            assertEquals(2, texts(tagged("FL-20261017-0001")).size());
        });
        instrumentation.runOnMainSync(() -> {
            TL_stars.TL_starsTransaction restored = new TL_stars.TL_starsTransaction();
            restored.id = "persist-redaction-test";
            restored.date = 1792227600;
            assertTrue("Redaction survives a fresh app process", StarsPrivacyMockApi.isAnonymised(0, restored));
        });
        screenshot("stars-privacy-restored.png");
    }

    /** Reproducible staged walkthrough for a recording; not part of the verification suite. */
    @Test public void demoWalkthrough() throws Exception {
        launch("Blue");
        Thread.sleep(1500);
        clickTag("stars_privacy_settings");
        Thread.sleep(2500);
        back();
        final StarsReactionsSheet[] sheet = new StarsReactionsSheet[1];
        instrumentation.runOnMainSync(() -> sheet[0] = preview().showReactionSheet());
        Thread.sleep(2000);
        instrumentation.runOnMainSync(() -> {
            sheet[0].setValue(25);
            find(sheet[0].getContainer(), ButtonWithCounterView.class).performClick();
        });
        Thread.sleep(7000);
        clickTag("stars_privacy_undo");
        Thread.sleep(1000);
        instrumentation.runOnMainSync(() -> sheet[0] = preview().showLiveReactionSheet());
        Thread.sleep(2500);
        instrumentation.runOnMainSync(() -> sheet[0].dismiss());
        settle();
        clickTag("stars_channel_settings");
        clickTag("stars_channel_private");
        Thread.sleep(3000);
        back();
        clickTag("stars_privacy_settings");
        clickTag("stars_privacy_toggle");
        back();
        instrumentation.runOnMainSync(() -> preview().sendSampleReaction(50));
        Thread.sleep(21000);
        instrumentation.runOnMainSync(() -> assertEquals(1, preview().completedPayments));
        Thread.sleep(2000);
        instrumentation.runOnMainSync(() -> sheet[0] = preview().showReactionSheet());
        Thread.sleep(2500);
        instrumentation.runOnMainSync(() -> sheet[0].dismiss());
        settle();
        clickTag("stars_privacy_settings");
        clickTag("stars_history_anonymise");
        Thread.sleep(3000);
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
        settle();
        clickTag("stars_privacy_toggle");
        back();
        Thread.sleep(2000);
    }

    private void launch(String theme) throws Exception {
        try (ParcelFileDescriptor.AutoCloseInputStream stream = new ParcelFileDescriptor.AutoCloseInputStream(instrumentation.getUiAutomation().executeShellCommand("cmd statusbar collapse"))) {
            while (stream.read() != -1) { }
        }
        if (activity != null) instrumentation.runOnMainSync(() -> activity.finish());
        Intent intent = new Intent(instrumentation.getTargetContext(), FeaturesLabActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity = instrumentation.startActivitySync(intent);
        settle();
        assertEquals(PackageManager.PERMISSION_DENIED, activity.checkSelfPermission(Manifest.permission.INTERNET));
        instrumentation.runOnMainSync(() -> {
            if (theme != null) {
                Theme.applyTheme(Theme.getTheme(theme));
                instrumentation.getTargetContext().getSharedPreferences("stars_privacy_mock_0", 0).edit().clear().commit();
                StarsPrivacy.setEnabled(0, true);
            }
            Button open = button(activity.getWindow().getDecorView(), "Open Stars Privacy preview");
            assertNotNull(open);
            open.performClick();
        });
        settle();
    }

    private ChatMessageCell messageCell() { return (ChatMessageCell) tagged("stars_privacy_message"); }
    private ReactionsLayoutInBubble.ReactionButton reaction() { return messageCell().reactionsLayoutInBubble.getReactionButton(ReactionsLayoutInBubble.VisibleReaction.asStar()); }
    private StarsPrivacyPreview preview() { return taggedPreview(activity.getWindow().getDecorView()); }
    private View tagged(String tag) { return activity.getWindow().getDecorView().findViewWithTag(tag); }
    private void clickTag(String tag) throws Exception {
        instrumentation.runOnMainSync(() -> { View view = tagged(tag); assertNotNull(tag, view); assertTrue(view.performClick()); });
        settle();
    }
    private void back() throws Exception { instrumentation.runOnMainSync(() -> activity.onBackPressed()); settle(); }
    private void settle() throws Exception { instrumentation.runOnMainSync(() -> {}); Thread.sleep(500); }

    private void screenshot(String name) throws Exception {
        settle();
        Bitmap bitmap = instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        File directory = new File(activity.getExternalFilesDir(null), "stars-privacy");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        try (FileOutputStream out = new FileOutputStream(new File(directory, name))) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); }
        bitmap.recycle();
    }

    private static ArrayList<String> texts(View view) {
        ArrayList<String> result = new ArrayList<>();
        if (view.getVisibility() != View.VISIBLE) return result;
        if (view instanceof TextView && ((TextView) view).getText().length() > 0) result.add(((TextView) view).getText().toString());
        if (view instanceof org.telegram.ui.ActionBar.SimpleTextView && ((org.telegram.ui.ActionBar.SimpleTextView) view).getText() != null) result.add(((org.telegram.ui.ActionBar.SimpleTextView) view).getText().toString());
        if (view instanceof org.telegram.ui.Components.AnimatedTextView && ((org.telegram.ui.Components.AnimatedTextView) view).getText() != null) result.add(((org.telegram.ui.Components.AnimatedTextView) view).getText().toString());
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) result.addAll(texts(((ViewGroup) view).getChildAt(i)));
        return result;
    }
    private static StarsPrivacyPreview taggedPreview(View view) {
        if (view.getTag() instanceof StarsPrivacyPreview) return (StarsPrivacyPreview) view.getTag();
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) { StarsPrivacyPreview match = taggedPreview(((ViewGroup) view).getChildAt(i)); if (match != null) return match; }
        return null;
    }
    private static Button button(View view, String label) {
        if (view instanceof Button && label.contentEquals(((Button) view).getText())) return (Button) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) { Button match = button(((ViewGroup) view).getChildAt(i), label); if (match != null) return match; }
        return null;
    }
    private static <T> T find(View view, Class<T> type) {
        if (type.isInstance(view)) return type.cast(view);
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) { T match = find(((ViewGroup) view).getChildAt(i), type); if (match != null) return match; }
        return null;
    }
}
