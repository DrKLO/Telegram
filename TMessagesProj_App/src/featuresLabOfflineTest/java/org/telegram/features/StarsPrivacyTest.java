package org.telegram.features;

import android.Manifest;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.ParcelFileDescriptor;
import android.text.Spanned;
import android.text.style.ClickableSpan;
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
import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble;
import org.telegram.ui.Stars.PaidReactionUndo;
import org.telegram.ui.Stars.StarsController;
import org.telegram.ui.Stars.StarsIntroActivity;
import org.telegram.ui.Stars.StarsPrivacy;
import org.telegram.ui.Stars.StarsPurchasePrivacyView;
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
            assertEquals(Arrays.asList("FL-20261017-0001", LocaleController.formatShortDateTime(1792227600), "-25 ⭐️"), texts(row));
        });
        screenshot("stars-private-light.png");

        final StarsReactionsSheet[] sheet = new StarsReactionsSheet[1];
        instrumentation.runOnMainSync(() -> {
            TLRPC.TL_messageReactor privatePast = new TLRPC.TL_messageReactor();
            privatePast.my = true;
            privatePast.anonymous = true;
            privatePast.count = 999;
            messageCell().getMessageObject().messageOwner.reactions.top_reactors.add(privatePast);
            sheet[0] = preview.showReactionSheet();
        });
        settle();
        instrumentation.runOnMainSync(() -> {
            assertNull(find(sheet[0].getContainer(), StarsReactionsSheet.TopSendersView.class));
            assertEquals(UserObject.ANONYMOUS, sheet[0].peer);
            assertTrue(texts(sheet[0].getContainer()).toString().contains("Preview reaction. No Stars will be spent."));
            assertFalse(texts(sheet[0].getContainer()).toString().contains("Terms of Service"));
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
            assertTrue(visible.contains("-999"));
            assertFalse(visible.contains("Design Notes"));
        });
        screenshot("stars-private-transaction.png");
        instrumentation.runOnMainSync(() -> details[0].dismiss());
        settle();

        clickTag("stars_privacy_settings");
        screenshot("stars-privacy-settings-light.png");
        clickTag("stars_privacy_toggle");
        instrumentation.runOnMainSync(() -> {
            assertFalse(StarsPrivacy.isEnabled(0));
            assertTrue(texts(activity.getWindow().getDecorView()).toString().contains("Only new reactions can be public. Past private activity stays private."));
        });
        screenshot("stars-privacy-off-notice.png");
        back();
        instrumentation.runOnMainSync(() -> {
            assertFalse("Turning off cannot reveal an old chosen state", reaction().choosen);
            assertEquals(3, texts(tagged("FL-20261017-0001")).size());
            assertFalse(texts(tagged("FL-20261017-0001")).toString().contains("Design Notes"));
            TL_stars.TL_starsTransaction unloaded = new TL_stars.TL_starsTransaction();
            unloaded.id = "unloaded-private-receipt";
            unloaded.date = 1792227600;
            unloaded.amount = TL_stars.StarsAmount.ofStars(-100);
            assertTrue("Unloaded pages remain private", StarsPrivacyMockApi.project(0, unloaded) instanceof StarsPrivacyMockApi.RedactedTransaction);
            StarsIntroActivity.StarsTransactionView recycled = (StarsIntroActivity.StarsTransactionView) tagged("FL-20261017-0002");
            TL_stars.StarsTransaction fresh = StarsPrivacyMockApi.sendPublicReaction(0, -7770001, 101, 7);
            recycled.set(fresh, false, true);
            assertTrue("A new public receipt has ordinary native details", texts(recycled).toString().contains("Design Notes"));
            assertEquals("Only the new contribution enters rankings", 7, StarsPrivacyMockApi.publicContribution(0, -7770001, 101));
            // Re-fetch a protected ID through a reused row; do not retain its original content.
            unloaded.id = "FL-20261017-0001";
            recycled.set(unloaded, false, true);
            assertEquals(3, texts(recycled).size());
        });
        settle();
        instrumentation.runOnMainSync(() -> assertTrue("Only the fresh public reaction can highlight", reaction().choosen));
        screenshot("stars-private-past-public-future.png");
        instrumentation.runOnMainSync(() -> sheet[0] = preview.showReactionSheet());
        settle();
        instrumentation.runOnMainSync(() -> {
            assertNotNull(find(sheet[0].getContainer(), StarsReactionsSheet.TopSendersView.class));
            assertFalse("The sheet must not reveal the old private sent total", texts(sheet[0].getContainer()).toString().contains("999"));
            sheet[0].peer = 1234; // Selecting a new identity must not modify the old post on dismissal.
        });
        screenshot("stars-standard-reaction-sheet.png");
        instrumentation.runOnMainSync(() -> {
            sheet[0].dismiss();
            assertEquals(Long.valueOf(UserObject.ANONYMOUS), messageCell().getMessageObject().getMyPaidReactionPeer());
        });
        settle();
        clickTag("stars_privacy_settings");
        clickTag("stars_privacy_toggle");
        instrumentation.runOnMainSync(() -> assertTrue(texts(activity.getWindow().getDecorView()).toString().contains("Existing and new reactions are hidden. Turning this off affects only new reactions.")));
        back();
        instrumentation.runOnMainSync(() -> {
            assertFalse(reaction().choosen);
            assertEquals(3, texts(tagged("FL-20261017-0001")).size());
            assertFalse(texts(tagged("FL-20261017-0001")).toString().contains("Design Notes"));
        });
        launch("Dark Blue");
        screenshot("stars-private-dark.png");
        clickTag("stars_privacy_settings");
        screenshot("stars-privacy-settings-dark.png");
    }

    @Test public void retroactiveReactionPrivacy() throws Exception {
        launch("Blue");
        final StarsReactionsSheet[] sheet = new StarsReactionsSheet[1];
        instrumentation.runOnMainSync(() -> {
            StarsPrivacy.setEnabled(0, false);
            // Simulate an account which has not yet enabled retroactive protection.
            instrumentation.getTargetContext().getSharedPreferences("stars_privacy_mock_0", 0).edit().clear().commit();
            org.telegram.messenger.MessageObject post = messageCell().getMessageObject();
            TLRPC.TL_messageReactor mine = new TLRPC.TL_messageReactor();
            mine.my = true;
            mine.count = 999;
            mine.peer_id = new TLRPC.TL_peerUser();
            mine.peer_id.user_id = 1234;
            post.messageOwner.reactions.top_reactors.add(mine);
            messageCell().forceResetMessageObject();
        });
        settle();
        instrumentation.runOnMainSync(() -> {
            assertTrue("An existing public reaction starts selected", reaction().choosen);
            sheet[0] = preview().showReactionSheet();
        });
        settle();
        instrumentation.runOnMainSync(() -> {
            assertTrue(texts(sheet[0].getContainer()).toString().contains("999"));
            StarsPrivacy.setEnabled(0, true);
            assertEquals(View.GONE, find(sheet[0].getContainer(), StarsReactionsSheet.TopSendersView.class).getVisibility());
        });
        settle();
        instrumentation.runOnMainSync(() -> {
            assertFalse("Close stale identity controls when privacy changes", sheet[0].isShowing());
            assertFalse(reaction().choosen);
            assertEquals("Public aggregate is unchanged", 125, messageCell().getMessageObject().messageOwner.reactions.results.get(0).count);
            StarsPrivacy.setEnabled(0, false);
            assertTrue("Unloaded posts are covered by the bulk mock", StarsPrivacyMockApi.isProtectedPost(0, -9990001, 9999));
            assertFalse("Turning off cannot restore previous selected Stars", StarsPrivacy.showSelectedState(0,
                messageCell().getMessageObject(), messageCell().getMessageObject().messageOwner.reactions.results.get(0)));
            sheet[0] = preview().showReactionSheet();
            sheet[0].updateSenders(0);
            StarsReactionsSheet.TopSendersView top = find(sheet[0].getContainer(), StarsReactionsSheet.TopSendersView.class);
            for (StarsReactionsSheet.TopSendersView.Sender sender : top.senders) assertFalse("Old leaderboard position remains hidden", sender.my);
            assertFalse(texts(sheet[0].getContainer()).toString().contains("999"));
        });
        screenshot("stars-retroactive-hidden.png");
        instrumentation.runOnMainSync(() -> {
            sheet[0].dismiss();
            StarsPrivacyMockApi.sendPublicReaction(0, -7770001, 101, 75);
            assertTrue(StarsPrivacy.showSelectedState(0, messageCell().getMessageObject(),
                messageCell().getMessageObject().messageOwner.reactions.results.get(0)));
            sheet[0] = preview().showReactionSheet();
            sheet[0].updateSenders(0);
            StarsReactionsSheet.TopSendersView top = find(sheet[0].getContainer(), StarsReactionsSheet.TopSendersView.class);
            boolean found = false;
            for (StarsReactionsSheet.TopSendersView.Sender sender : top.senders) {
                if (sender.my) { assertEquals("⭐\u00a075", sender.starsText.getText().toString()); found = true; }
            }
            assertTrue("Only new public contributions can rank", found);
            StarsPrivacy.setEnabled(0, true);
            assertEquals(0, StarsPrivacyMockApi.publicContribution(0, -7770001, 101));
            StarsPrivacy.setEnabled(0, false);
            assertFalse(StarsPrivacy.showSelectedState(0, messageCell().getMessageObject(),
                messageCell().getMessageObject().messageOwner.reactions.results.get(0)));
        });
        settle();
        instrumentation.runOnMainSync(() -> {
            StarsPrivacyMockApi.setChannelPrivate(0, -7770001, true);
            StarsPrivacyMockApi.sendChannelReaction(0, -7770001, 101, 25);
            sheet[0] = preview().showReactionSheet();
        });
        settle();
        instrumentation.runOnMainSync(() -> {
            View percentile = sheet[0].getContainer().findViewWithTag("stars_donor_percentile");
            assertEquals(View.VISIBLE, percentile.getVisibility());
            StarsPrivacyMockApi.setShowsPercentile(0, -7770001, false);
            assertEquals("An open sheet responds to the same event as expiry", View.GONE, percentile.getVisibility());
            sheet[0].dismiss();
        });
    }

    @Test public void liveRetroactivePrivacy() throws Exception {
        launch("Blue");
        instrumentation.runOnMainSync(() -> {
            StarsPrivacy.setEnabled(0, false);
            instrumentation.getTargetContext().getSharedPreferences("stars_privacy_mock_0", 0).edit().clear().commit();
            android.widget.FrameLayout host = new android.widget.FrameLayout(activity);
            org.telegram.ui.Stories.LiveCommentsView live = new org.telegram.ui.Stories.LiveCommentsView(
                activity, null, host, new View(activity), host) {
                @Override protected boolean isMe(long id) { return id == 1234; }
                @Override protected TLRPC.Peer getDefaultSendAs() {
                    TLRPC.TL_peerUser peer = new TLRPC.TL_peerUser(); peer.user_id = 1234; return peer;
                }
            };
            live.setup(-7770001, null);
            host.addView(live, new android.widget.FrameLayout.LayoutParams(-1, 500));
            activity.addContentView(host, new ViewGroup.LayoutParams(-1, -1));
            TLRPC.TL_textWithEntities text = new TLRPC.TL_textWithEntities(); text.text = "";
            live.push(1792227600, 201, false, 1234, text, 25, true);
            live.push(1792227600, 202, false, 8880001, text, 10, true);
            try {
                java.lang.reflect.Field field = org.telegram.ui.Stories.LiveCommentsView.class.getDeclaredField("messages");
                field.setAccessible(true);
                @SuppressWarnings("unchecked") ArrayList<org.telegram.ui.Stories.LiveCommentsView.Message> messages =
                    (ArrayList<org.telegram.ui.Stories.LiveCommentsView.Message>) field.get(live);
                StarsPrivacy.setEnabled(0, true);
                StarsPrivacy.setEnabled(0, false);
                assertFalse(live.didSendStars());
                for (org.telegram.ui.Stories.LiveCommentsView.Message message : messages) {
                    if (message.id == 201) assertEquals(UserObject.ANONYMOUS, message.dialogId);
                    if (message.id == 202) assertEquals(8880001, message.dialogId);
                }
                live.push(1792227600, 203, false, 1234, text, 100, true);
                for (org.telegram.ui.Stories.LiveCommentsView.Message message : messages) {
                    if (message.id == 203) assertEquals("Unloaded live history stays anonymous", UserObject.ANONYMOUS, message.dialogId);
                }
                live.send(1234, text, 7);
                assertEquals(7, live.getPublicStarsSincePrivacy());
                assertTrue(live.didSendStars());
                assertEquals(-7, StarsController.getInstance(0).transactions[StarsController.ALL_TRANSACTIONS].get(0).amount.amount);
                StarsPrivacy.setEnabled(0, true);
                StarsPrivacy.setEnabled(0, false);
                assertEquals(0, live.getPublicStarsSincePrivacy());
                assertFalse(live.didSendStars());
                for (org.telegram.ui.Stories.LiveCommentsView.Message message : messages) assertNotEquals(1234, message.dialogId);
                assertFalse(UserConfig.getInstance(0).isClientActivated());
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            } finally {
                ((ViewGroup) host.getParent()).removeView(host);
            }
        });
    }

    @Test public void purchasePrivacyNotice() throws Exception {
        launch("Blue");
        final StarsIntroActivity.StarsOptionsSheet[] sheet = new StarsIntroActivity.StarsOptionsSheet[1];
        instrumentation.runOnMainSync(() -> {
            StarsPrivacy.setEnabled(0, false);
            sheet[0] = preview().showPurchaseSheet();
        });
        settle();
        screenshot("stars-purchase-privacy-light.png");
        instrumentation.runOnMainSync(() -> {
            StarsPurchasePrivacyView notice = find(sheet[0].getContainer(), StarsPurchasePrivacyView.class);
            assertNotNull(notice);
            assertTrue(notice.getText().toString().contains("Top Senders"));
            Spanned text = (Spanned) notice.getText();
            ClickableSpan[] links = text.getSpans(0, text.length(), ClickableSpan.class);
            assertEquals(1, links.length);
            links[0].onClick(notice);
            assertTrue(StarsPrivacy.isEnabled(0));
            assertTrue(notice.getText().toString().contains("is on"));
            assertTrue(StarsPrivacyMockApi.isProtectedPost(0, -9990001, 9999));
            assertFalse(UserConfig.getInstance(0).isClientActivated());
        });
        screenshot("stars-purchase-privacy-enabled.png");
        instrumentation.runOnMainSync(() -> sheet[0].dismiss());
        settle();
        final StarsIntroActivity.StarsNeededSheet[] needed = new StarsIntroActivity.StarsNeededSheet[1];
        instrumentation.runOnMainSync(() -> {
            StarsPrivacy.setEnabled(0, false);
            needed[0] = new StarsIntroActivity.StarsNeededSheet(activity, null, 5000,
                StarsIntroActivity.StarsNeededSheet.TYPE_REACTIONS, "Design Notes", () -> fail("No purchase in this test"), 0);
            needed[0].show();
        });
        settle();
        instrumentation.runOnMainSync(() -> {
            assertNotNull("Insufficient balance also shows the disclaimer", find(needed[0].getContainer(), StarsPurchasePrivacyView.class));
            needed[0].dismiss();
        });
        launch("Dark Blue");
        instrumentation.runOnMainSync(() -> { StarsPrivacy.setEnabled(0, false); sheet[0] = preview().showPurchaseSheet(); });
        screenshot("stars-purchase-privacy-dark.png");
        instrumentation.runOnMainSync(() -> sheet[0].dismiss());
    }

    @Test public void privateTransactionAmounts() throws Exception {
        launch("Blue");
        instrumentation.runOnMainSync(() -> {
            StarsIntroActivity.StarsTransactionView row = (StarsIntroActivity.StarsTransactionView) tagged("FL-20261017-0002");
            long[] amounts = {1234567, -25, 0, 25};
            String[] displayed = {"+1,234,567 ⭐️", "-25 ⭐️", "0 ⭐️", "+25.25 ⭐️"};
            for (int i = 0; i < amounts.length; i++) {
                TL_stars.TL_starsTransaction record = new TL_stars.TL_starsTransaction();
                record.id = "amount-check-" + i;
                record.date = 1792227600;
                record.amount = TL_stars.StarsAmount.ofStars(amounts[i]);
                record.amount.nanos = i == 3 ? 250000000 : 0;
                record.title = "Sensitive title";
                record.description = "Sensitive description";
                record.msg_id = 101;
                record.peer = new TL_stars.TL_starsTransactionPeer();
                record.peer.peer = new TLRPC.TL_peerChannel();
                record.peer.peer.channel_id = 7770001;
                TL_stars.StarsTransaction privateRecord = StarsPrivacyMockApi.project(0, record);
                assertEquals(record.amount.amount, privateRecord.amount.amount);
                assertEquals(record.amount.nanos, privateRecord.amount.nanos);
                assertNotSame(record.amount, privateRecord.amount);
                assertNull(privateRecord.title);
                assertNull(privateRecord.description);
                assertNull(privateRecord.peer.peer);
                assertEquals(0, privateRecord.msg_id);
                row.set(privateRecord, false, true);
                assertEquals(Arrays.asList(record.id, LocaleController.formatShortDateTime(record.date), displayed[i]), texts(row));
                StarsPrivacy.setEnabled(0, false);
                assertEquals(displayed[i], texts(row).get(2));
                assertNull(StarsPrivacyMockApi.project(0, record).title);
                StarsPrivacy.setEnabled(0, true);
                assertEquals(displayed[i], texts(row).get(2));
                record.amount.amount = 999;
                assertEquals("Projection must not retain the original amount object", amounts[i], privateRecord.amount.amount);
            }
        });
        screenshot("stars-private-amounts.png");
    }

    @Test public void anonymousChoiceAfterOptOut() throws Exception {
        launch("Blue");
        final StarsReactionsSheet[] sheet = new StarsReactionsSheet[1];
        instrumentation.runOnMainSync(() -> {
            StarsPrivacy.setEnabled(0, false);
            sheet[0] = preview().showReactionSheet();
        });
        settle();
        instrumentation.runOnMainSync(() -> {
            sheet[0].peer = UserObject.ANONYMOUS;
            sheet[0].setValue(9);
            find(sheet[0].getContainer(), ButtonWithCounterView.class).performClick();
        });
        Thread.sleep(21000);
        instrumentation.runOnMainSync(() -> {
            assertEquals(1, preview().completedPayments);
            assertEquals("The explicit sheet choice overrides the public default", UserObject.ANONYMOUS, preview().lastPaymentPeer);
            assertEquals(0, StarsPrivacyMockApi.publicContribution(0, -7770001, 101));
            assertFalse(reaction().choosen);
            ViewGroup history = (ViewGroup) tagged("FL-20261017-0001").getParent();
            View latest = history.getChildAt(history.getChildCount() - 1);
            assertTrue(latest.getTag().toString().startsWith("preview-"));
            assertTrue(texts(latest).contains("-9 ⭐️"));
            assertEquals(3, texts(latest).size());
        });
        screenshot("stars-anonymous-choice-after-opt-out.png");
    }

    @Test public void twentySecondUndoAndLiveMock() throws Exception {
        launch("Blue");
        StarsPrivacyPreview preview = preview();
        final long[] sentAt = new long[1];
        instrumentation.runOnMainSync(() -> {
            sentAt[0] = android.os.SystemClock.elapsedRealtime();
            preview.sendSampleReaction(1);
        });
        Thread.sleep(6500);
        instrumentation.runOnMainSync(() -> assertEquals("The old five-second deadline must not send", 0, preview.completedPayments));
        screenshot("stars-twenty-second-undo.png");
        // Screenshot capture can be slow on an emulator. Target the send's actual
        // monotonic deadline instead of adding capture time to the 16-second wait.
        Thread.sleep(Math.max(0, sentAt[0] + 16000 - android.os.SystemClock.elapsedRealtime()));
        instrumentation.runOnMainSync(() -> assertTrue("Undo check must run before its deadline",
            android.os.SystemClock.elapsedRealtime() - sentAt[0] < PaidReactionUndo.DURATION_MS));
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
            ViewGroup history = (ViewGroup) tagged("FL-20261017-0001").getParent();
            assertTrue(texts(history.getChildAt(history.getChildCount() - 1)).contains("-25 ⭐️"));
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
            assertEquals(-4, controller.transactions[StarsController.ALL_TRANSACTIONS].get(0).amount.amount);
            org.telegram.ui.ChatActivity chat = mock[0].chatActivity;
            org.telegram.messenger.MessageObject message = mock[0].messageObject;
            mock[0] = controller.new PendingPaidReactions(StarsController.MessageId.from(message), message, chat, 0, true);
            controller.currentPendingReactions = mock[0];
            mock[0].peer = 0L;
            mock[0].add(7, true);
            assertEquals(0, mock[0].getPeerId());
        });
        Thread.sleep(21000);
        instrumentation.runOnMainSync(() -> {
            StarsController controller = StarsController.getInstance(0);
            assertTrue(mock[0].committed);
            assertEquals("The split-contribution mock must not debit a balance", 300, controller.getBalance(false));
            assertEquals(2, controller.transactions[StarsController.ALL_TRANSACTIONS].size());
            TL_stars.StarsTransaction fresh = controller.transactions[StarsController.ALL_TRANSACTIONS].get(0);
            assertFalse(fresh instanceof StarsPrivacyMockApi.RedactedTransaction);
            assertFalse(StarsPrivacyMockApi.shouldRedact(0, fresh));
            assertEquals(-7, fresh.amount.amount);
            assertEquals(7, StarsPrivacyMockApi.publicContribution(0, -7770001, 101));
            assertTrue("The older private receipt stays minimal", controller.transactions[StarsController.ALL_TRANSACTIONS].get(1) instanceof StarsPrivacyMockApi.RedactedTransaction);
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
        assertEquals(-25, transaction.amount.amount);
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
            assertEquals(-50, receipt.asTransaction().amount.amount);
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
            assertEquals(-125, redacted.amount.amount);
            assertTrue(StarsPrivacyMockApi.isAnonymised(0, record));
            assertTrue("Private history keeps the simplified tab layout", StarsPrivacyMockApi.hasPrivateHistory(0));
            assertFalse(StarsPrivacyMockApi.isAnonymised(2, record));
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
            TL_stars.StarsTransaction future = StarsPrivacyMockApi.sendPublicReaction(0, -7770001, 101, 3);
            assertFalse("Exact provenance distinguishes a new receipt in the cutoff second", StarsPrivacyMockApi.isAnonymised(0, future));
            StarsPrivacyMockApi.anonymise(0, future.id);
            assertTrue("Explicit redaction overrides public provenance", StarsPrivacyMockApi.isAnonymised(0, future));
            StarsPrivacy.setEnabled(0, true);
            StarsPrivacy.setEnabled(0, false);
            assertFalse("Old highlights cannot return after toggling twice", StarsPrivacyMockApi.showHighlight(0, -7770001, 101));
        });
    }

    @Test public void privacySurvivesProcessRestart() throws Exception {
        launch(null);
        instrumentation.runOnMainSync(() -> {
            assertFalse("Disabled preference survives process restart", StarsPrivacy.isEnabled(0));
            assertFalse(reaction().choosen);
            assertEquals(3, texts(tagged("FL-20261017-0001")).size());
        });
        instrumentation.runOnMainSync(() -> {
            TL_stars.TL_starsTransaction restored = new TL_stars.TL_starsTransaction();
            restored.id = "persist-redaction-test";
            restored.date = 1792227600;
            restored.amount = TL_stars.StarsAmount.ofStars(-125);
            assertTrue("Redaction survives a fresh app process", StarsPrivacyMockApi.isAnonymised(0, restored));
            assertEquals(-125, StarsPrivacyMockApi.project(0, restored).amount.amount);
        });
        screenshot("stars-privacy-restored-disabled.png");
        instrumentation.runOnMainSync(() -> StarsPrivacy.setEnabled(0, true));
    }

    /** Reproducible staged walkthrough for a recording; not part of the verification suite. */
    @Test public void nativeSettingsPolish() throws Exception {
        launch("Blue");
        clickTag("stars_channel_settings");
        instrumentation.runOnMainSync(() -> {
            View percentile = tagged("stars_channel_percentile");
            assertFalse(percentile.isEnabled());
            boolean before = StarsPrivacyMockApi.showsPercentile(0, -7770001);
            percentile.performClick();
            assertEquals(before, StarsPrivacyMockApi.showsPercentile(0, -7770001));
            // Reverse while the native 200ms enabled-state animation is still running.
            tagged("stars_channel_private").performClick();
            tagged("stars_channel_private").performClick();
            tagged("stars_channel_private").performClick();
        });
        settle();
        instrumentation.runOnMainSync(() -> {
            assertTrue(tagged("stars_channel_percentile").isEnabled());
            assertTrue(StarsPrivacyMockApi.isChannelPrivate(0, -7770001));
            String visible = texts(activity.getWindow().getDecorView()).toString();
            assertTrue(visible.contains("Existing and new reactions are hidden."));
            assertFalse(visible.contains("Only new reactions can be public."));
            // Apply the same theme callbacks used by Telegram's animated theme transition.
            Theme.applyTheme(Theme.getTheme("Dark Blue"));
            BaseFragment fragment = find(activity.getWindow().getDecorView(), ActionBarLayout.class).getLastFragment();
            for (ThemeDescription description : fragment.getThemeDescriptions()) {
                description.setColor(Theme.getColor(description.getCurrentKey()), false, false);
            }
            assertEquals(Theme.getColor(Theme.key_windowBackgroundGray),
                ((android.graphics.drawable.ColorDrawable) fragment.getFragmentView().getBackground()).getColor());
        });
        screenshot("stars-channel-dark-polished.png");
        back();
        instrumentation.runOnMainSync(() -> assertFalse(texts(activity.getWindow().getDecorView()).toString().contains("Existing and new reactions are hidden.")));

        LocaleController.LocaleInfo original = LocaleController.getInstance().getCurrentLocaleInfo();
        try {
            instrumentation.runOnMainSync(() -> LocaleController.getInstance().applyLanguage(
                LocaleController.getInstance().languagesDict.get("ru"), true, false, 0));
            clickTag("stars_privacy_settings");
            screenshot("stars-privacy-settings-russian.png");
            instrumentation.runOnMainSync(() -> {
                assertNoEllipsis(tagged("stars_privacy_toggle"));
                assertNoEllipsis(tagged("stars_history_anonymise"));
                android.view.accessibility.AccessibilityNodeInfo node = tagged("stars_privacy_toggle").createAccessibilityNodeInfo();
                assertTrue(node.isCheckable());
                assertTrue(node.isChecked());
                node.recycle();
            });
            back();
            clickTag("stars_channel_settings");
            screenshot("stars-channel-settings-russian.png");
            instrumentation.runOnMainSync(() -> {
                assertNoEllipsis(tagged("stars_channel_private"));
                assertNoEllipsis(tagged("stars_channel_percentile"));
            });
        } finally {
            instrumentation.runOnMainSync(() -> LocaleController.getInstance().applyLanguage(original, true, false, 0));
        }
    }

    private static void assertNoEllipsis(View view) {
        if (view.getVisibility() != View.VISIBLE) return;
        if (view instanceof TextView) {
            android.text.Layout layout = ((TextView) view).getLayout();
            if (layout != null) for (int i = 0; i < layout.getLineCount(); i++) {
                assertEquals("Truncated settings label: " + ((TextView) view).getText(), 0, layout.getEllipsisCount(i));
            }
        }
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            assertNoEllipsis(((ViewGroup) view).getChildAt(i));
        }
    }

    @Test public void historyPrivacyControl() throws Exception {
        launch("Blue");
        revealHistoryControl();
        screenshot("stars-history-privacy-light.png");
        clickTag("stars_history_privacy");
        instrumentation.runOnMainSync(() -> {
            assertFalse(StarsPrivacy.isEnabled(0));
            assertEquals(3, texts(tagged("FL-20261017-0001")).size());
            assertFalse(reaction().choosen);
            assertTrue(texts(activity.getWindow().getDecorView()).toString().contains("Only new reactions can be public."));
            // Reverse during the settling animation; the final preference must win.
            tagged("stars_history_privacy").performClick();
            tagged("stars_history_privacy").performClick();
            tagged("stars_history_privacy").performClick();
            assertTrue(StarsPrivacy.isEnabled(0));
            assertEquals(UserObject.ANONYMOUS, StarsPrivacy.resolvePeer(0, 1234));
            assertEquals(3, texts(tagged("FL-20261017-0001")).size());
        });
        Thread.sleep(2000);
        instrumentation.runOnMainSync(() -> {
            android.view.accessibility.AccessibilityNodeInfo node = tagged("stars_history_privacy").createAccessibilityNodeInfo();
            assertTrue(node.isCheckable());
            assertTrue(node.isChecked());
            assertTrue(node.isClickable());
            node.recycle();
            assertNoEllipsis(tagged("stars_history_privacy"));
        });
        clickTag("stars_privacy_settings");
        clickTag("stars_privacy_toggle");
        back();
        instrumentation.runOnMainSync(() -> {
            android.view.accessibility.AccessibilityNodeInfo node = tagged("stars_history_privacy").createAccessibilityNodeInfo();
            assertFalse(node.isChecked());
            node.recycle();
            assertEquals(3, texts(tagged("FL-20261017-0001")).size());
        });
        launch("Dark Blue");
        revealHistoryControl();
        screenshot("stars-history-privacy-dark.png");
        LocaleController.LocaleInfo original = LocaleController.getInstance().getCurrentLocaleInfo();
        try {
            instrumentation.runOnMainSync(() -> LocaleController.getInstance().applyLanguage(
                LocaleController.getInstance().languagesDict.get("ru"), true, false, 0));
            launch("Blue");
            revealHistoryControl();
            screenshot("stars-history-privacy-russian.png");
            instrumentation.runOnMainSync(() -> assertNoEllipsis(tagged("stars_history_privacy")));
        } finally {
            instrumentation.runOnMainSync(() -> LocaleController.getInstance().applyLanguage(original, true, false, 0));
        }
    }

    private void revealHistoryControl() throws Exception {
        instrumentation.runOnMainSync(() -> {
            View control = tagged("stars_history_privacy");
            assertNotNull(control);
            control.requestRectangleOnScreen(new android.graphics.Rect(0, 0,
                control.getWidth(), control.getHeight() + org.telegram.messenger.AndroidUtilities.dp(140)), true);
        });
        settle();
    }

    @Test public void demoWalkthrough() throws Exception {
        launch("Blue");
        revealHistoryControl();
        Thread.sleep(1500);
        clickTag("stars_history_privacy");
        Thread.sleep(2000);
        final StarsIntroActivity.StarsOptionsSheet[] purchase = new StarsIntroActivity.StarsOptionsSheet[1];
        instrumentation.runOnMainSync(() -> purchase[0] = preview().showPurchaseSheet());
        Thread.sleep(3000);
        instrumentation.runOnMainSync(() -> {
            StarsPurchasePrivacyView notice = find(purchase[0].getContainer(), StarsPurchasePrivacyView.class);
            Spanned text = (Spanned) notice.getText();
            text.getSpans(0, text.length(), ClickableSpan.class)[0].onClick(notice);
        });
        Thread.sleep(2500);
        instrumentation.runOnMainSync(() -> purchase[0].dismiss());
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
        clickTag("FL-20261017-0001");
        Thread.sleep(2500);
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
        settle();
        instrumentation.runOnMainSync(() -> sheet[0] = preview().showLiveReactionSheet());
        Thread.sleep(2500);
        instrumentation.runOnMainSync(() -> {
            sheet[0].setValue(10);
            find(sheet[0].getContainer(), ButtonWithCounterView.class).performClick();
        });
        Thread.sleep(21000);
        instrumentation.runOnMainSync(() -> assertEquals(1, preview().completedPayments));
        settle();
        clickTag("stars_channel_settings");
        clickTag("stars_channel_private");
        Thread.sleep(3000);
        back();
        clickTag("stars_privacy_settings");
        clickTag("stars_privacy_toggle");
        Thread.sleep(3500);
        back();
        assertEquals(3, texts(tagged("FL-20261017-0001")).size());
        instrumentation.runOnMainSync(() -> preview().sendSampleReaction(50));
        Thread.sleep(21000);
        instrumentation.runOnMainSync(() -> assertEquals(2, preview().completedPayments));
        Thread.sleep(2000);
        instrumentation.runOnMainSync(() -> sheet[0] = preview().showReactionSheet());
        Thread.sleep(2500);
        instrumentation.runOnMainSync(() -> sheet[0].dismiss());
        settle();
        clickTag("stars_privacy_settings");
        clickTag("stars_history_anonymise");
        Thread.sleep(3000);
        instrumentation.runOnMainSync(() -> {
            BaseFragment fragment = find(activity.getWindow().getDecorView(), ActionBarLayout.class).getLastFragment();
            ((AlertDialog) fragment.getVisibleDialog()).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        });
        Thread.sleep(2500);
        settle();
        clickTag("stars_privacy_toggle");
        back();
        Thread.sleep(2000);
        launch("Dark Blue");
        revealHistoryControl();
        Thread.sleep(2000);
        clickTag("stars_privacy_settings");
        Thread.sleep(2500);
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
