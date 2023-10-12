package org.telegram.messenger;

import com.google.android.exoplayer2.util.Consumer;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;

public class ChannelBoostsController {

    private final int currentAccount;
    private final MessagesController messagesController;
    private final ConnectionsManager connectionsManager;

    public final static int BOOSTS_FOR_LEVEL_1 = 1;
    public final static int BOOSTS_FOR_LEVEL_2 = 1;

    public ChannelBoostsController(int currentAccount) {
        this.currentAccount = currentAccount;
        messagesController = MessagesController.getInstance(currentAccount);
        connectionsManager = ConnectionsManager.getInstance(currentAccount);
    }


    public void getBoostsStats(long dialogId, Consumer<TLRPC.TL_stories_boostsStatus> consumer) {
        TLRPC.TL_stories_getBoostsStatus req = new TLRPC.TL_stories_getBoostsStatus();
        req.peer = messagesController.getInputPeer(dialogId);
        connectionsManager.sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response != null) {
                consumer.accept((TLRPC.TL_stories_boostsStatus) response);
            } else {
                BulletinFactory.showForError(error);
                consumer.accept(null);
            }
        }));

    }

    public void userCanBoostChannel(long dialogId, Consumer<CanApplyBoost> consumer) {
        TLRPC.TL_stories_canApplyBoost req = new TLRPC.TL_stories_canApplyBoost();
        req.peer = messagesController.getInputPeer(dialogId);
        connectionsManager.sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            CanApplyBoost canApplyBoost = new CanApplyBoost();
            if (response != null) {
                canApplyBoost.canApply = true;
                if (response instanceof TLRPC.TL_stories_canApplyBoostReplace) {
                    TLRPC.TL_stories_canApplyBoostReplace canApplyBoostReplace = (TLRPC.TL_stories_canApplyBoostReplace) response;
                    messagesController.putChats(canApplyBoostReplace.chats, false);
                    canApplyBoost.replaceDialogId = DialogObject.getPeerDialogId(canApplyBoostReplace.current_boost);
                    if (canApplyBoost.replaceDialogId == 0 && canApplyBoostReplace.chats.size() > 0) {
                        canApplyBoost.replaceDialogId = -canApplyBoostReplace.chats.get(0).id;
                    }
                }
            } else {
                if (error != null) {
                    if (error.text.equals("SAME_BOOST_ALREADY_ACTIVE") || error.text.equals("BOOST_NOT_MODIFIED")) {
                        canApplyBoost.alreadyActive = true;
                    } else if (error.text.equals("PREMIUM_GIFTED_NOT_ALLOWED")) {
                        canApplyBoost.giftedPremium = true;
                    } else if (error.text.startsWith("FLOOD_WAIT")) {
                        canApplyBoost.floodWait = Utilities.parseInt(error.text);
                        canApplyBoost.lastCheckTime = System.currentTimeMillis();
                    }
                }
            }
            consumer.accept(canApplyBoost);
        }), ConnectionsManager.RequestFlagDoNotWaitFloodWait);
    }

    public void applyBoost(long dialogId) {
        TLRPC.TL_stories_applyBoost req = new TLRPC.TL_stories_applyBoost();
        req.peer = messagesController.getInputPeer(dialogId);
        connectionsManager.sendRequest(req, (response, error) -> {

        });
    }

    public int getTotalBooststToLevel(int level) {
        int count = 0;
        if (level >= 1) {
            count += BOOSTS_FOR_LEVEL_1;
        }
        if (level >= 2) {
            count += BOOSTS_FOR_LEVEL_2;
        }
        return count;
    }

    public static class CanApplyBoost {
        public boolean canApply;
        public long replaceDialogId;

        public boolean alreadyActive;
        public int floodWait;
        public boolean giftedPremium;
        private long lastCheckTime;

        public void checkTime() {
            floodWait -= (System.currentTimeMillis() - lastCheckTime) / 1000;
            lastCheckTime = System.currentTimeMillis();
            if (floodWait < 0) {
                floodWait = 0;
                canApply = true;
            }
        }
    }
}
