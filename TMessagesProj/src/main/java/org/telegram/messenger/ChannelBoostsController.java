package org.telegram.messenger;

import com.google.android.exoplayer2.util.Consumer;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.Premium.boosts.BoostRepository;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public void getBoostsStats(long dialogId, Consumer<TL_stories.TL_premium_boostsStatus> consumer) {
        TL_stories.TL_premium_getBoostsStatus req = new TL_stories.TL_premium_getBoostsStatus();
        req.peer = messagesController.getInputPeer(dialogId);
        connectionsManager.sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response != null) {
                consumer.accept((TL_stories.TL_premium_boostsStatus) response);
            } else {
                BaseFragment fragment = LaunchActivity.getLastFragment();
                if (error != null && fragment != null && "CHANNEL_PRIVATE".equals(error.text)) {
                    if (!(LaunchActivity.instance != null && LaunchActivity.instance.isFinishing())) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getContext(), fragment.getResourceProvider());
                        builder.setTitle(LocaleController.getString(R.string.AppName));
                        Map<String, Integer> colorsReplacement = new HashMap<>();
                        colorsReplacement.put("info1.**", Theme.getColor(Theme.key_dialogTopBackground));
                        colorsReplacement.put("info2.**", Theme.getColor(Theme.key_dialogTopBackground));
                        builder.setTopAnimation(R.raw.not_available, AlertsCreator.NEW_DENY_DIALOG_TOP_ICON_SIZE, false, Theme.getColor(Theme.key_dialogTopBackground), colorsReplacement);
                        builder.setTopAnimationIsNew(true);
                        builder.setTitle(LocaleController.getString(R.string.ChannelPrivate));
                        builder.setMessage(LocaleController.getString(R.string.ChannelCantOpenPrivate2));
                        builder.setPositiveButton(LocaleController.getString(R.string.Close), null);
                        builder.show();
                    }
                } else {
                    BulletinFactory.global().showForError(error);
                }
                consumer.accept(null);
            }
        }));
    }

    public void userCanBoostChannel(long dialogId, TL_stories.TL_premium_boostsStatus boostsStatus, Consumer<CanApplyBoost> consumer) {
        CanApplyBoost canApplyBoost = new CanApplyBoost();
        canApplyBoost.currentPeer = messagesController.getPeer(dialogId);
        canApplyBoost.currentDialogId = dialogId;
        canApplyBoost.currentChat = messagesController.getChat(-dialogId);
        BoostRepository.getMyBoosts(myBoosts -> {
            canApplyBoost.isMaxLvl = boostsStatus.next_level_boosts <= 0;
            canApplyBoost.setMyBoosts(myBoosts);
            consumer.accept(canApplyBoost);
        }, error -> {
            if (error.text.startsWith("FLOOD_WAIT")) {
                canApplyBoost.floodWait = Utilities.parseInt(error.text);
            } else if (error.text.startsWith("BOOSTS_EMPTY")) {
                canApplyBoost.empty = true;
            }
            canApplyBoost.canApply = false;
            consumer.accept(canApplyBoost);
        });
    }

    public void applyBoost(long dialogId, int slot, Utilities.Callback<TL_stories.TL_premium_myBoosts> onDone, Utilities.Callback<TLRPC.TL_error> onError) {
        BoostRepository.applyBoost(-dialogId, Arrays.asList(slot), onDone, onError);
    }

    public static class CanApplyBoost {
        public boolean canApply;
        public boolean empty;
        public long replaceDialogId;

        public boolean alreadyActive;
        public boolean needSelector;
        public int floodWait;
        public int slot;
        public TL_stories.TL_premium_myBoosts myBoosts;
        public int boostCount = 0;
        public TLRPC.Peer currentPeer;
        public long currentDialogId;
        public TLRPC.Chat currentChat;
        public boolean boostedNow;
        public boolean isMaxLvl;

        public CanApplyBoost copy() {
            CanApplyBoost canApplyBoost = new CanApplyBoost();
            canApplyBoost.canApply = canApply;
            canApplyBoost.empty = empty;
            canApplyBoost.replaceDialogId = replaceDialogId;
            canApplyBoost.alreadyActive = alreadyActive;
            canApplyBoost.needSelector = needSelector;
            canApplyBoost.slot = slot;
            canApplyBoost.myBoosts = myBoosts;
            canApplyBoost.boostCount = boostCount;
            canApplyBoost.currentPeer = currentPeer;
            canApplyBoost.currentDialogId = currentDialogId;
            canApplyBoost.currentChat = currentChat;
            canApplyBoost.isMaxLvl = isMaxLvl;
            return canApplyBoost;
        }

        public void setMyBoosts(TL_stories.TL_premium_myBoosts myBoosts) {
            this.myBoosts = myBoosts;
            boostCount = 0;
            slot = 0;
            alreadyActive = false;
            canApply = false;
            needSelector = false;
            replaceDialogId = 0;

            if (myBoosts.my_boosts.isEmpty()) {
                empty = true;
            }

            //search boosted count
            for (TL_stories.TL_myBoost myBoost : myBoosts.my_boosts) {
                if (currentDialogId == DialogObject.getPeerDialogId(myBoost.peer)) {
                    boostCount++;
                }
            }

            if (boostCount > 0) {
                alreadyActive = true;
            }

            //search free slot
            for (TL_stories.TL_myBoost myBoost : myBoosts.my_boosts) {
                if (myBoost.peer == null) {
                    slot = myBoost.slot;
                    break;
                }
            }
            boolean noFreeSlot = slot == 0;
            if (noFreeSlot) {
                //only replacement
                List<TL_stories.TL_myBoost> replaceBoost = new ArrayList<>();
                for (TL_stories.TL_myBoost myBoost : myBoosts.my_boosts) {
                    if (myBoost.peer != null && DialogObject.getPeerDialogId(myBoost.peer) != -currentChat.id) {
                        replaceBoost.add(myBoost);
                    }
                }
                if (replaceBoost.size() == 1 && replaceBoost.get(0).cooldown_until_date == 0) {
                    TL_stories.TL_myBoost myBoost = replaceBoost.get(0);
                    replaceDialogId = DialogObject.getPeerDialogId(myBoost.peer);
                    slot = myBoost.slot;
                    canApply = true;
                } else if (replaceBoost.size() >= 1) {
                    needSelector = true;
                    if (!BoostRepository.isMultiBoostsAvailable()) {
                        TL_stories.TL_myBoost myBoost = replaceBoost.get(0);
                        replaceDialogId = DialogObject.getPeerDialogId(myBoost.peer);
                        slot = myBoost.slot;
                    }
                    canApply = true;
                } else {
                    canApply = false;
                }
            } else {
                canApply = true;
            }
            if (isMaxLvl) {
                canApply = false;
            }
        }
    }
}
