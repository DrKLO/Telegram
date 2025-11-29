package org.telegram.messenger;

import android.text.TextUtils;
import android.util.LongSparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_payments;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.Gifts.AuctionBidSheet;
import org.telegram.ui.Stars.StarsController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import me.vkryl.core.reference.ReferenceList;
import me.vkryl.core.reference.ReferenceMap;

public class GiftAuctionController extends BaseController {
    private final ReferenceMap<Long, OnAuctionUpdateListener> listeners = new ReferenceMap<>(false);

    private final LongSparseArray<AuctionInternal> auctions = new LongSparseArray<>();
    private final ArrayList<Auction> activeAuctions = new ArrayList<>();


    private boolean wasRequestedActiveAuctions;
    public boolean hasActiveAuctions() {
        if (!wasRequestedActiveAuctions && getMessagesController().giftAuctionUpdateWasRecently()) {
            wasRequestedActiveAuctions = true;
            requestUserAuctions();
        }

        return !activeAuctions.isEmpty();
    }

    public ArrayList<Auction> getActiveAuctions() {
        return activeAuctions;
    }

    public Auction subscribeToGiftAuction(long giftId, OnAuctionUpdateListener listener) {
        listeners.add(giftId, listener);
        subscribeToGiftAuctionStateInternal(giftId);

        return getAuction(giftId);
    }

    public void unsubscribeFromGiftAuction(long giftId, OnAuctionUpdateListener listener) {
        listeners.remove(giftId, listener);

        final AuctionInternal auction = auctions.get(giftId);
        if (auction == null) {
            return;
        }

        auction.subscription = listeners.has(giftId);
        if (auction.resubscribe != null) {
            AndroidUtilities.cancelRunOnUIThread(auction.resubscribe);
            auction.resubscribe = null;
        }
    }

    private void subscribeToGiftAuctionStateInternal(long giftId) {
        final AuctionInternal auction = getOrCreateAuction(giftId);

        auction.subscription = true;
        if (auction.resubscribe != null) {
            AndroidUtilities.cancelRunOnUIThread(auction.resubscribe);
            auction.resubscribe = null;
        }

        final TL_payments.TL_getStarGiftAuctionState req = new TL_payments.TL_getStarGiftAuctionState();
        final TL_stars.TL_inputStarGiftAuction inputStarGiftAuction = new TL_stars.TL_inputStarGiftAuction();
        inputStarGiftAuction.gift_id = auction.giftId;
        req.auction = inputStarGiftAuction;
        req.version = auction.getVersion();

        getConnectionsManager().sendRequestTyped(req, AndroidUtilities::runOnUIThread, (res, err) -> {
            if (res == null || err != null) {
                return;
            }
            getMessagesController().putUsers(res.users, false);
            onGiftAuctionStateReceivedInternal(giftId, res);
        });
    }

    private void onGiftAuctionStateReceivedInternal(long giftId, TL_payments.TL_StarGiftAuctionState state) {
        applyGiftAuctionStateAndPerformUpdate(state.gift, state.state, state.user_state);

        final AuctionInternal auction = auctions.get(giftId);
        if (auction != null && auction.subscription) {
            auction.resubscribe = () -> {
                auction.resubscribe = null;
                subscribeToGiftAuctionStateInternal(giftId);
            };
            AndroidUtilities.runOnUIThread(auction.resubscribe, state.timeout * 1000L);
        }
    }


    public int requestGiftAuctionById(long giftId, Utilities.Callback2<TL_payments.TL_StarGiftAuctionState, TLRPC.TL_error> callback) {
        TL_stars.TL_inputStarGiftAuction auction = new TL_stars.TL_inputStarGiftAuction();
        auction.gift_id = giftId;
        return requestGiftAuctionInternal(auction, callback);
    }

    public int requestGiftAuctionBySlug(String slug, Utilities.Callback2<TL_payments.TL_StarGiftAuctionState, TLRPC.TL_error> callback) {
        TL_stars.TL_inputStarGiftAuctionSlug auction = new TL_stars.TL_inputStarGiftAuctionSlug();
        auction.slug = slug;
        return requestGiftAuctionInternal(auction, callback);
    }

    private int requestGiftAuctionInternal(TL_stars.InputStarGiftAuction auction, Utilities.Callback2<TL_payments.TL_StarGiftAuctionState, TLRPC.TL_error> callback) {
        final TL_payments.TL_getStarGiftAuctionState req = new TL_payments.TL_getStarGiftAuctionState();
        req.auction = auction;
        req.version = 0;

        return getConnectionsManager().sendRequestTyped(req, AndroidUtilities::runOnUIThread, (res, err) -> {
            if (res != null) {
                onGiftAuctionStateReceivedInternal(res.gift.id, res);
            }
            callback.run(res, err);
        });
    }


    public void sendBid(long giftId, AuctionBidSheet.Params params, long amount, Utilities.Callback2<Boolean, String> whenDone) {
        final AuctionInternal auction = auctions.get(giftId);
        if (auction == null || auction.pendingBid) {
            whenDone.run(false, null);
            return;
        }

        if (!StarsController.getInstance(currentAccount).balanceAvailable()) {
            StarsController.getInstance(currentAccount).getBalance(() -> {
                if (!StarsController.getInstance(currentAccount).balanceAvailable()) {
                    if (whenDone != null) {
                        whenDone.run(false, "NO_BALANCE");
                    }
                    return;
                }
                sendBid(giftId, params, amount, whenDone);
            });
            return;
        }

        final boolean hasBid = auction.hasBid();
        auction.pendingBid = true;


        final TLRPC.TL_payments_getPaymentForm req = new TLRPC.TL_payments_getPaymentForm();
        final TLRPC.TL_inputInvoiceStarGiftAuctionBid invoice = new TLRPC.TL_inputInvoiceStarGiftAuctionBid();
        invoice.gift_id = giftId;
        invoice.bid_amount = amount;
        invoice.update_bid = hasBid;

        if (params != null)  {
            if (params.dialogId == 0) {
                invoice.peer = new TLRPC.TL_inputPeerSelf();
            } else {
                invoice.peer = getMessagesController().getInputPeer(params.dialogId);
            }
            invoice.message = params.message;
            invoice.hide_name = params.hideName;
        } else if (!hasBid) {
            // default params
            invoice.peer = new TLRPC.TL_inputPeerSelf();
            invoice.hide_name = false;
        }

        req.invoice = invoice;

        getConnectionsManager().sendRequestTyped(req, AndroidUtilities::runOnUIThread, (res, err) -> {
            if (err != null) {
                whenDone.run(false, err.text);
                auction.pendingBid = false;
                return;
            } else if (!(res instanceof TLRPC.TL_payments_paymentFormStarGift)) {
                whenDone.run(false, "NO_PAYMENT_FORM");
                auction.pendingBid = false;
                return;
            }

            final TLRPC.TL_payments_paymentFormStarGift form = (TLRPC.TL_payments_paymentFormStarGift) res;
            TL_stars.TL_payments_sendStarsForm req2 = new TL_stars.TL_payments_sendStarsForm();
            req2.form_id = form.form_id;
            req2.invoice = req.invoice;
            getConnectionsManager().sendRequestTyped(req2, AndroidUtilities::runOnUIThread, (res2, err2) -> {
                auction.pendingBid = false;
                if (res2 instanceof TLRPC.TL_payments_paymentResult) {
                    final TLRPC.TL_payments_paymentResult paymentResult = (TLRPC.TL_payments_paymentResult) res2;
                    Utilities.stageQueue.postRunnable(() -> {
                        MessagesController.getInstance(currentAccount).processUpdates(paymentResult.updates, false);
                    });

                    whenDone.run(true, null);
                } else if (err2 != null) {
                    whenDone.run(false, err2.text);
                } else {
                    whenDone.run(false, null);
                }
            });
        });
    }







    private long calculateUserAuctionsHash() {
        List<Long> userAuctions = new ArrayList<>();

        final int count = auctions.size();
        for (int a = 0; a < count; a++) {
            final AuctionInternal auction = auctions.valueAt(a);
            if (auction.internalState == null || auction.internalState.isFinished() || auction.internalState.auctionStateActive == null || auction.internalState.auctionUserState.bid_date <= 0) {
                continue;
            }

            long r = ((long) auction.internalState.auctionUserState.bid_date << 32) | auction.internalState.auctionStateActive.version;
            userAuctions.add(r);
        }

        Collections.sort(userAuctions);

        long hash = 0;
        for (Long m: userAuctions) {
            hash = MediaDataController.calcHash(hash, m & 0xFFFFFFFFL);
            hash = MediaDataController.calcHash(hash, (m >> 32));
        }
        return hash;
    }

    public void requestUserAuctions() {
        final TL_payments.TL_getStarGiftActiveAuctions req = new TL_payments.TL_getStarGiftActiveAuctions();
        req.hash = calculateUserAuctionsHash();

        getConnectionsManager().sendRequestTyped(req, AndroidUtilities::runOnUIThread, (res, err) ->{
            if (res == null || err != null) {
                return;
            }

            if (res instanceof TL_payments.TL_starGiftActiveAuctions) {
                TL_payments.TL_starGiftActiveAuctions activeAuctions = (TL_payments.TL_starGiftActiveAuctions) res;
                getMessagesController().putUsers(activeAuctions.users, false);

                for (TL_stars.TL_StarGiftActiveAuctionState state : activeAuctions.auctions) {
                    applyGiftAuctionStateAndPerformUpdate(state.gift, state.state, state.user_state);
                }

            } else if (res instanceof TL_payments.TL_starGiftActiveAuctionsNotModified) {

            }
        });
    }

    public void getOrRequestAcquiredGifts(long giftId, Utilities.Callback<List<TL_stars.TL_StarGiftAuctionAcquiredGift>> callback) {
        final AuctionInternal auction = auctions.get(giftId);
        if (auction == null || auction.internalState == null) {
            callback.run(null);
            return;
        }

        if (auction.acquiredGifts == null || auction.internalState.auctionUserState.acquired_count != auction.acquiredGifts.size()) {
            TL_payments.TL_getStarGiftAuctionAcquiredGifts req = new TL_payments.TL_getStarGiftAuctionAcquiredGifts();
            req.gift_id = giftId;

            getConnectionsManager().sendRequestTyped(req, AndroidUtilities::runOnUIThread, (res, err) -> {
                if (res == null) {
                    callback.run(null);
                    return;
                }
                getMessagesController().putUsers(res.users, false);
                getMessagesController().putChats(res.chats, false);
                auction.acquiredGifts = res.gifts;
                callback.run(auction.acquiredGifts);
            });
            return;
        }

        callback.run(auction.acquiredGifts);
    }


    public void getOrRequestAuction(long giftId, Utilities.Callback2<Auction, TLRPC.TL_error> callback) {
        Auction auction = getAuction(giftId);
        if (auction != null) {
            callback.run(auction, null);
            return;
        } else {
            requestGiftAuctionById(giftId, (res, err) -> {
                callback.run(getAuction(giftId), err);
            });
        }
    }



    /* * */

    @UiThread
    public void processUpdate(TLRPC.TL_updateStarGiftAuctionState update) {
        final AuctionInternal auction = auctions.get(update.gift_id);
        if (auction == null || auction.internalState == null) {
            return;
        }

        final boolean changed = auction.internalState.applyAuctionState(update.state);
        if (changed) {
            updateActiveAuctions();
            performAuctionUpdate(auction.giftId);
        }
    }

    @UiThread
    public void processUpdate(TLRPC.TL_updateStarGiftAuctionUserState update) {
        final AuctionInternal auction = auctions.get(update.gift_id);
        if (auction == null || auction.internalState == null) {
            return;
        }

        final boolean changed = auction.internalState.applyUserState(update.user_state);
        if (changed) {
            updateActiveAuctions();
            performAuctionUpdate(auction.giftId);
        }
    }

    private void applyGiftAuctionStateAndPerformUpdate(TL_stars.StarGift gift,
                                                       TL_stars.StarGiftAuctionState state,
                                                       TL_stars.TL_StarGiftAuctionUserState user_state) {
        final AuctionInternal auction = getOrCreateAuction(gift.id);

        boolean changed = false;

        if (auction.internalState == null) {
            auction.internalState = new Auction(currentAccount, gift, state, user_state);
            changed = true;
        } else {
            changed |= auction.internalState.applyGift(gift);
            changed |= auction.internalState.applyAuctionState(state);
            changed |= auction.internalState.applyUserState(user_state);
        }

        if (changed) {
            updateActiveAuctions();
            performAuctionUpdate(gift.id);
        }
    }

    private void performAuctionUpdate(long giftId) {
        final Auction auction = getAuction(giftId);
        final Iterator<OnAuctionUpdateListener> list = listeners.iterator(giftId);
        if (auction == null || list == null) {
            return;
        }

        while (list.hasNext()) {
            OnAuctionUpdateListener listener = list.next();
            listener.onUpdate(auction);
        }
    }

    @Nullable
    public Auction getAuction(long giftId) {
        final AuctionInternal auctionInternal = auctions.get(giftId);
        return auctionInternal != null ? auctionInternal.internalState : null;
    }

    @Nullable
    private Auction findAuctionBySlug(String slug) {
        final int count = auctions.size();
        for (int a = 0; a < count; a++) {
            final AuctionInternal auction = auctions.valueAt(a);
            if (auction.internalState != null && TextUtils.equals(auction.internalState.gift.auction_slug, slug)) {
                return auction.internalState != null ? auction.internalState : null;
            }
        }

        return null;
    }

    @NonNull
    private AuctionInternal getOrCreateAuction(long giftId) {
        AuctionInternal auction = auctions.get(giftId);
        if (auction == null) {
            auction = new AuctionInternal(giftId);
            auctions.put(giftId, auction);
        }

        return auction;
    }

    private void updateActiveAuctions() {
        getMessagesController().putLastGiftAuctionUpdate();

        activeAuctions.clear();

        final int count = auctions.size();

        for (int a = 0; a < count; a++) {
            final AuctionInternal auction = auctions.valueAt(a);
            if (auction.internalState == null || auction.internalState.isFinished()) {
                continue;
            }

            if (auction.internalState.auctionUserState.bid_amount > 0) {
                activeAuctions.add(auction.internalState);
            }
        }

        activeAuctions.sort(Comparator.comparingInt(a -> a.auctionUserState.bid_date));

        performUpdateActiveAuctions();
    }

    private static class AuctionInternal {
        public final long giftId;

        private Auction internalState;

        private @Nullable Runnable resubscribe;
        private boolean subscription;
        private boolean pendingBid;

        private @Nullable ArrayList<TL_stars.TL_StarGiftAuctionAcquiredGift> acquiredGifts;

        private AuctionInternal(long giftId) {
            this.giftId = giftId;
        }

        public int getVersion() {
            return internalState != null ? internalState.getVersion() : 0;
        }

        public boolean hasBid() {
            return internalState != null && internalState.auctionUserState.bid_amount > 0;
        }
    }



    /* * */

    private final ReferenceList<OnActiveAuctionsUpdateListeners> onActiveAuctionsUpdateListeners = new ReferenceList<>(false);

    public void subscribeToActiveAuctionsUpdates(OnActiveAuctionsUpdateListeners listener) {
        onActiveAuctionsUpdateListeners.add(listener);
    }

    public void unsubscribeFromActiveAuctionsUpdates(OnActiveAuctionsUpdateListeners listener) {
        onActiveAuctionsUpdateListeners.remove(listener);
    }

    private void performUpdateActiveAuctions() {
        for (OnActiveAuctionsUpdateListeners listeners : onActiveAuctionsUpdateListeners) {
            listeners.onActiveAuctionsUpdate(activeAuctions);
        }
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.activeAuctionsUpdated);
    }



    /* Callbacks */

    public interface OnAuctionUpdateListener {
        void onUpdate(Auction auction);
    }

    public interface OnActiveAuctionsUpdateListeners {
        void onActiveAuctionsUpdate(List<Auction> auction);
    }

    public static class Auction {
        public final int currentAccount;
        public final long giftId;
        public final long giftDocumentId;
        public final String giftAuctionSlug;

        public @NonNull TL_stars.StarGift gift;
        public @NonNull TL_stars.StarGiftAuctionState auctionState;
        public @NonNull TL_stars.TL_StarGiftAuctionUserState auctionUserState;

        public @Nullable TL_stars.TL_starGiftAuctionState auctionStateActive;
        public @Nullable TL_stars.TL_starGiftAuctionStateFinished auctionStateFinished;

        private Auction(
                final int currentAccount,
                @NonNull TL_stars.StarGift gift,
                @NonNull TL_stars.StarGiftAuctionState auctionState,
                @NonNull TL_stars.TL_StarGiftAuctionUserState auctionUserState) {

            this.currentAccount = currentAccount;
            this.gift = gift;
            this.auctionState = auctionState;
            this.auctionUserState = auctionUserState;
            this.giftId = gift.id;
            this.giftDocumentId = gift.sticker != null ? gift.sticker.id : 0;
            this.giftAuctionSlug = gift.auction_slug;

            applyAuctionState(auctionState);
        }

        public BidStatus getBidStatus() {
            if (auctionUserState.returned) {
                return BidStatus.RETURNED;
            } else if (auctionUserState.bid_amount == 0) {
                return BidStatus.NO_BID;
            }else if (getApproximatedMyPlace() <= gift.gifts_per_round) {
                return BidStatus.WINNING;
            } else {
                return BidStatus.OUTBID;
            }
        }

        public enum BidStatus {
            WINNING,
            OUTBID,
            RETURNED,
            NO_BID;

            public boolean isOutbid() {
                return this == OUTBID || this == RETURNED;
            }
        }


        public boolean isFinished() {
            return auctionStateFinished != null;
        }

        public long getMinimumBid() {
            if (auctionStateActive != null && auctionUserState.min_bid_amount > 0) {
                return Math.max(auctionStateActive.min_bid_amount, auctionUserState.min_bid_amount);
            } else if (auctionUserState.min_bid_amount > 0) {
                return auctionUserState.min_bid_amount;
            } else if (auctionStateActive != null) {
                return auctionStateActive.min_bid_amount;
            }
            return 0;
        }

        public long getCurrentMyBid() {
            return auctionUserState.bid_amount;
        }

        public long getCurrentTopBid() {
            if (auctionStateActive != null && auctionStateActive.bid_levels != null && !auctionStateActive.bid_levels.isEmpty()) {
                return auctionStateActive.bid_levels.get(0).amount;
            }
            return 0;
        }

        public long getMaximumBid() {
            return Math.max(50000, getCurrentTopBid() * 3 / 2);
        }

        public int getApproximatedMyPlace() {
            return approximatedMyPlace;
        }

        public long approximateBidAmountFromPlace(int place) {
            if (auctionStateActive == null || auctionStateActive.bid_levels == null) {
                return getMinimumBid();
            }

            for (TL_stars.TL_AuctionBidLevel level : auctionStateActive.bid_levels) {
                if (place <= level.pos) {
                    return level.amount;
                }
            }
            return getMinimumBid();
        }

        public int approximatePlaceFromStars(long bidAmount) {
            return approximatePlaceFromStars(bidAmount, ConnectionsManager.getInstance(currentAccount).getCurrentTime());
        }

        public int approximatePlaceFromStars(long bidAmount, int bidDate) {
            if (auctionStateActive == null || auctionStateActive.bid_levels == null) {
                return -1;
            }

            int lastPos = 0;
            for (TL_stars.TL_AuctionBidLevel level : auctionStateActive.bid_levels) {
                if (bidAmount > level.amount || bidAmount == level.amount && bidDate <= level.date) {
                    return level.pos;
                }

                lastPos = level.pos;
            }

            return lastPos + 1;
        }







        /* * */

        private int getVersion() {
            if (isFinished()) {
                return Integer.MAX_VALUE;
            }
            return auctionStateActive != null ? auctionStateActive.version : 0;
        }

        private boolean applyGift(@NonNull TL_stars.StarGift gift) {
            this.gift = gift;
            return true;
        }

        private boolean applyAuctionState(@NonNull TL_stars.StarGiftAuctionState state) {
            if (state instanceof TL_stars.TL_starGiftAuctionState) {
                final TL_stars.TL_starGiftAuctionState updatedState = (TL_stars.TL_starGiftAuctionState) state;
                final int currentVersion = getVersion();
                if (updatedState.version > currentVersion) {
                    auctionState = state;
                    auctionStateActive = updatedState;
                    onUpdateUserOrAuctionState();
                    return true;
                }
            } else if (state instanceof TL_stars.TL_starGiftAuctionStateFinished) {
                if (!isFinished()) {
                    auctionState = state;
                    auctionStateFinished = (TL_stars.TL_starGiftAuctionStateFinished) state;
                    return true;
                }
            }
            return false;
        }

        private boolean applyUserState(@NonNull TL_stars.TL_StarGiftAuctionUserState state) {
            auctionUserState = state;
            onUpdateUserOrAuctionState();
            return true;
        }

        private void onUpdateUserOrAuctionState() {
            approximatedMyPlace = approximateMyPlace();
        }

        private int approximatedMyPlace;

        private int approximateMyPlace() {
            if (auctionStateActive == null) {
                return -1;
            }

            if (auctionStateActive.top_bidders != null) {
                final long myId = UserConfig.getInstance(currentAccount).getClientUserId();
                for (int a = 0; a < auctionStateActive.top_bidders.size(); a++) {
                    final long bidderId = auctionStateActive.top_bidders.get(a);
                    if (myId == bidderId) {
                        return a + 1;
                    }
                }
            }

            if (auctionUserState.bid_amount > 0 && !auctionUserState.returned) {
                return approximatePlaceFromStars(auctionUserState.bid_amount, auctionUserState.bid_date);
            }

            return -1;
        }
    }


    /* Instances */

    private GiftAuctionController(int num) {
        super(num);
    }

    private static volatile GiftAuctionController[] Instance = new GiftAuctionController[UserConfig.MAX_ACCOUNT_COUNT];

    public static GiftAuctionController getInstance(int num) {
        GiftAuctionController localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (GiftAuctionController.class) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new GiftAuctionController(num);
                }
            }
        }
        return localInstance;
    }
}
