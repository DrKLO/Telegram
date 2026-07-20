package org.telegram.ui.community;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.replaceTags;
import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.content.DialogInterface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.collection.LongSparseArray;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_communities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.community.cells.CommunityPendingRequestCell;
import org.telegram.ui.community.sheet.CommunityAddOptionsSheet;

import java.util.ArrayList;
import java.util.List;

public class CommunityUtils {
    public static final boolean COLLAPSED_SUPPORT = true;

    private CommunityUtils() {

    }

    public static void fillLinkedPeers(int currentAccount, ArrayList<UItem> items, long communityId, boolean withGap) {
        MessagesController.CommunityPeersDialog communityPeersDialog = MessagesController.getInstance(currentAccount).buildCommunityPeers(communityId);
        if (communityPeersDialog == null) {
            return;
        }

        boolean needGap = false;
        if (!communityPeersDialog.chatsYouAreIn.isEmpty()) {
            needGap = withGap;
            items.add(UItem.asHeader(21, getString(R.string.CommunitySectionChatsYouAreIn)));
            for (MessagesController.CommunityPeerDialog chat : communityPeersDialog.chatsYouAreIn) {
                items.add(DialogCellFactory.asCell(chat));
            }
        }
        if (!communityPeersDialog.chatsYouCanView.isEmpty()) {
            if (needGap) {
                items.add(UItem.asSpace(22, dp(12)));
            }
            needGap = withGap;
            items.add(UItem.asHeader(23, getString(R.string.CommunitySectionChatsYouCanView)));
            for (MessagesController.CommunityPeerDialog chat : communityPeersDialog.chatsYouCanView) {
                items.add(DialogCellFactory.asCell(chat));
            }
        }
        if (!communityPeersDialog.chatsYouCanJoin.isEmpty()) {
            if (needGap) {
                items.add(UItem.asSpace(24, dp(12)));
            }
            needGap = withGap;
            items.add(UItem.asHeader(25, getString(R.string.CommunitySectionChatsYouCanRequestToJoin)));
            for (MessagesController.CommunityPeerDialog chat : communityPeersDialog.chatsYouCanJoin) {
                items.add(DialogCellFactory.asCell(chat));
            }
        }
        if (!communityPeersDialog.chatsOther.isEmpty()) {
            if (needGap) {
                items.add(UItem.asSpace(26, dp(12)));
            }
            needGap = withGap;
            items.add(UItem.asHeader(27, getString(R.string.CommunitySectionHiddenChats)));
            for (MessagesController.CommunityPeerDialog chat : communityPeersDialog.chatsOther) {
                items.add(DialogCellFactory.asCell(chat));
            }
        }
    }

    public static void fillPendingRequests(int currentAccount,
                                           ArrayList<UItem> items,
                                           ArrayList<TL_communities.CommunityPeerRequest> pendingRequests,
                                           LongSparseArray<Void> hiddenJoinRequests,
                                           CommunityPendingRequestCell.ClickDelegate delegate
    ) {
        if (pendingRequests == null || pendingRequests.isEmpty()) {
            return;
        }

        for (int a = 0, N = pendingRequests.size(); a < N; a++) {
            final TL_communities.CommunityPeerRequest request = pendingRequests.get(a);
            final long dialogId = DialogObject.getPeerDialogId(request.peer);
            if (hiddenJoinRequests != null && hiddenJoinRequests.containsKey(dialogId)) {
                continue;
            }

            final TLRPC.User requestedBy = MessagesController.getInstance(currentAccount).getUser(request.requested_by);
            items.add(CommunityPendingRequestCell.Factory.asPendingRequest(dialogId, requestedBy, !request.visible, delegate, a < N - 1));
        }
    }

    public static class PendingRequests implements CommunityPendingRequestCell.ClickDelegate {
        private final Context context;
        private final Theme.ResourcesProvider resourcesProvider;
        private final BulletinFactory bulletinFactory;
        private final int currentAccount;
        private final long communityId;
        private TLRPC.Chat community;

        private final LongSparseArray<Void> hiddenJoinRequests = new LongSparseArray<>();
        private Delegate delegate;
        private Runnable doCommitRunnable;

        public interface Delegate {
            void updateAdapter();
            void close();
            void onClickGroupOwner(long dialogId);
        }

        public PendingRequests(Context context, Theme.ResourcesProvider resourcesProvider, BulletinFactory bulletinFactory, int currentAccount, long communityId) {
            this.context = context;
            this.resourcesProvider = resourcesProvider;
            this.bulletinFactory = bulletinFactory;
            this.currentAccount = currentAccount;
            this.communityId = communityId;
            this.community = MessagesController.getInstance(currentAccount).getChat(communityId);

            lastViewTime = MessagesController.getMainSettings(currentAccount).getLong("community_requests_last_view_time_" + communityId, 0);
        }

        public void setDelegate(Delegate delegate) {
            this.delegate = delegate;
        }

        public boolean isEmpty() {
            return finished && totalCount == 0;
        }

        public boolean isSingle() {
            return finished && totalCount == 1 && pendingRequests != null && pendingRequests.size() == 1;
        }

        public void fillItems(ArrayList<UItem> items) {
            if (pendingRequests == null || pendingRequests.isEmpty()) {
                return;
            }

            fillPendingRequests(currentAccount, items, pendingRequests, hiddenJoinRequests, this);
            if (!finished) {
                items.add(UItem.asFlicker(FlickerLoadingView.PROFILE_SEARCH_CELL));
            }
        }




        private ArrayList<TL_communities.CommunityPeerRequest> pendingRequests = new ArrayList<>();
        private String nextOffset;
        private int totalCount;
        private boolean loading;
        private boolean finished;

        public boolean isLoading() {
            return loading;
        }

        public boolean isFinished() {
            return finished;
        }

        public int getTotalCount() {
            return totalCount;
        }

        public ArrayList<TL_communities.CommunityPeerRequest> getPendingRequests() {
            return pendingRequests;
        }

        private long lastViewTime;
        private int unreadPendingRequests;

        public int getUnreadCount() {
            return unreadPendingRequests;
        }

        public void markAsViewed() {
            final long time = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
            lastViewTime = time;

            MessagesController.getMainSettings(currentAccount).edit()
                .putLong("community_requests_last_view_time_" + communityId, time)
                .apply();

            calcUnreadPendingRequests();
        }

        private void calcUnreadPendingRequests() {
            unreadPendingRequests = 0;
            if (pendingRequests == null) {
                return;
            }

            for (int a = 0, N = pendingRequests.size(); a < N; a++) {
                final TL_communities.CommunityPeerRequest request = pendingRequests.get(a);
                final long dialogId = DialogObject.getPeerDialogId(request.peer);
                if (hiddenJoinRequests.containsKey(dialogId)) {
                    continue;
                }

                if (request.date > lastViewTime) {
                    unreadPendingRequests++;
                } else {
                    break;
                }
            }
        }



        public void loadNext() {
            if (loading || finished || !ChatObject.canUserDoAdminAction(community, ChatObject.ACTION_MANAGE_LINKED_CHATS)) {
                return;
            }

            loading = true;
            MessagesController.getInstance(currentAccount).fetchCommunityPendingJoinRequests(communityId, nextOffset, (res, err) -> {
                loading = false;
                if (res != null) {
                    if (pendingRequests == null) {
                        pendingRequests = new ArrayList<>(res.requests);
                    } else {
                        pendingRequests.addAll(res.requests);
                    }
                    nextOffset = res.next_offset;
                    totalCount = res.total_count;
                    finished = res.next_offset == null;

                    calcUnreadPendingRequests();
                    if (delegate != null) {
                        delegate.updateAdapter();
                    }
                }
            });
        }

        public void checkLoadNext(UniversalRecyclerView listView) {
            if (loading || finished) {
                return;
            }

            final int position = listView.layoutManager.findLastVisibleItemPosition();
            if (position + 10 > listView.adapter.getItemCount()) {
                loadNext();
            }
        }

        public void commit() {
            if (doCommitRunnable != null) {
                doCommitRunnable.run();
            }
            doCommitRunnable = null;
        }

        private void onResolveJoinRequest(long dialogId, boolean add) {
            hiddenJoinRequests.put(dialogId, null);
            totalCount--;
            calcUnreadPendingRequests();

            if (delegate != null) {
                delegate.updateAdapter();
            }

            final CharSequence bulletinText = AndroidUtilities.replaceTags(formatString(add ?
                            R.string.CommunityRequestApprovedToast :
                            R.string.CommunityRequestDeclinedToast,
                    DialogObject.getShortName(currentAccount, dialogId)));

            commit();

            doCommitRunnable = () -> {
                doCommitRunnable = null;

                hiddenJoinRequests.remove(dialogId);
                if (pendingRequests != null) {
                    for (int N = pendingRequests.size(), a = N - 1; a >= 0; a--) {
                        final TL_communities.CommunityPeerRequest request = pendingRequests.get(a);
                        if (DialogObject.getPeerDialogId(request.peer) == dialogId) {
                            pendingRequests.remove(a);
                        }
                    }
                }

                calcUnreadPendingRequests();
                if (delegate != null) {
                    delegate.updateAdapter();
                }

                MessagesController.getInstance(currentAccount).resolveCommunityJoinPendingRequest(communityId, dialogId, !add, (res, err) -> {
                    if (err != null) {
                        bulletinFactory.showForError(err);
                    }
                });
            };



            final Bulletin.UsersLayout layout = new Bulletin.UsersLayout(context, false, resourcesProvider);
            int count = 0;
            TLObject object = MessagesController.getInstance(currentAccount).getUserOrChat(dialogId);
            if (object != null) {
                layout.avatarsImageView.setCount(++count);
                layout.avatarsImageView.setObject(0, UserConfig.selectedAccount, object);
            }

            layout.avatarsImageView.setTranslationX(dp(7));
            layout.avatarsImageView.setScaleX(1.333f);
            layout.avatarsImageView.setScaleY(1.333f);
            layout.avatarsImageView.commitTransition(false);

            layout.textView.setSingleLine(false);
            layout.textView.setMaxLines(2);
            layout.textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            layout.textView.setText(bulletinText);
            if (layout.textView.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                int margin = dp(12 + 56 + 6 - (3 - count) * 12);
                if (LocaleController.isRTL) {
                    ((ViewGroup.MarginLayoutParams) layout.textView.getLayoutParams()).rightMargin = margin;
                } else {
                    ((ViewGroup.MarginLayoutParams) layout.textView.getLayoutParams()).leftMargin = margin;
                }
            }
            if (LocaleController.isRTL) {
                layout.avatarsImageView.setTranslationX(dp(32 - (count - 1) * 12));
            }
            layout.setButton(new Bulletin.UndoButton(context, true, true, resourcesProvider).setText(LocaleController.getString(R.string.UndoNoCaps)).setUndoAction(() -> {
                doCommitRunnable = null;
                hiddenJoinRequests.remove(dialogId);
                totalCount++;

                calcUnreadPendingRequests();
                if (delegate != null) {
                    delegate.updateAdapter();
                }
            }).setDelayedAction(doCommitRunnable));

            bulletinFactory.create(layout, Bulletin.DURATION_PROLONG).show();
        }


        private AlertDialog progressDialog;
        private int reqId;

        public void onResolveAllJoinRequests(boolean add) {
            onResolveAllJoinRequests(add, true);
        }

        private void onResolveAllJoinRequests(boolean add, boolean ask) {
            if (progressDialog != null || reqId != 0) {
                return;
            }

            if (ask) {
                AlertDialog dialog = AlertsCreator.createSimpleConfirmAlert(context, resourcesProvider,
                    getString(add ? R.string.CommunityAddAllChatsTitle : R.string.CommunityDeclineAllTitle),
                    AndroidUtilities.replaceTags(formatPluralString(add ? "CommunityAddAllChatsMessage" : "CommunityDeclineAllMessage", totalCount)),
                    getString(add ? R.string.Add : R.string.Decline), () -> onResolveAllJoinRequests(add, false)
                );
                dialog.show();
                if (!add) {
                    TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                    if (button != null) {
                        button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
                    }
                }
                return;
            }

            commit();

            progressDialog = new AlertDialog(context, AlertDialog.ALERT_TYPE_SPINNER, resourcesProvider);
            progressDialog.setOnCancelListener((di) -> {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, true);
                progressDialog = null;
                reqId = 0;
            });
            progressDialog.showDelayed(500);

            reqId = MessagesController.getInstance(currentAccount).resolveCommunityAllJoinPendingRequests(communityId, !add, (res, err) -> {
                progressDialog.dismiss();
                progressDialog = null;
                reqId = 0;

                if (err != null) {
                    bulletinFactory.showForError(err);
                    return;
                }

                if (delegate != null) {
                    delegate.close();
                }
            });
        }

        @Override
        public void onClickApprove(long dialogId) {
            onResolveJoinRequest(dialogId, true);
        }

        @Override
        public void onClickDecline(long dialogId) {
            onResolveJoinRequest(dialogId, false);
        }

        @Override
        public void onClickGroupOwner(long dialogId) {
            if (delegate != null) {
                delegate.onClickGroupOwner(dialogId);
            }
        }
    }



    public static void showChatsToAddToCommunity(AlertDialog[] progressDialog, BaseFragment baseFragment, int currentAccount, TLRPC.Chat community) {
        if (progressDialog[0] != null) {
            return;
        }

        int reqId = MessagesController.getInstance(currentAccount).fetchChatsToAddToCommunity((res, err) -> {
            if (progressDialog[0] != null) {
                progressDialog[0].dismiss();
                progressDialog[0] = null;
            }
            if (err != null) {
                BulletinFactory.of(baseFragment).showForError(err);
                return;
            }
            if (res != null) {
                if (res.isEmpty()) {
                    BulletinFactory.of(baseFragment).createSimpleBulletin(R.raw.info, getString(R.string.CommunityNoChatsToAdd)).show();
                } else {
                    CommunityUtils.showChatsToAddSheet(baseFragment, currentAccount, community, res);
                }
            }
        });
        ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(reqId, baseFragment.getClassGuid());

        progressDialog[0] = new AlertDialog(baseFragment.getContext(), AlertDialog.ALERT_TYPE_SPINNER);
        progressDialog[0].showDelayed(500);
        progressDialog[0].setOnCancelListener(d -> {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, true);
            progressDialog[0] = null;
        });
    }

    private static void showChatsToAddSheet(BaseFragment fragment, int currentAccount, TLRPC.Chat community, ArrayList<TLRPC.Chat> chats) {
        if (!chats.isEmpty()) {
            //fragment.showDialog(new CommunityChatsToAddSheet(fragment.getContext(), chats, (chat) -> {
            fragment.showDialog(new CommunitySheet(fragment, 0, chats, (chat) -> {
                fragment.showDialog(new CommunityAddOptionsSheet(fragment.getContext(), community, -chat.id, (isHidden) -> {
                    linkToCommunityAndConvertIfNeeded(fragment, currentAccount, chat, community.id, isHidden);
                }));
            }));
        } else {
            BulletinFactory.of(fragment).createSimpleBulletin(R.raw.info, "").show();
        }
    }

    public static void linkToCommunityAndConvertIfNeeded(BaseFragment baseFragment,
                                                         int currentAccount,
                                                         TLRPC.Chat chat,
                                                         long communityId,
                                                         boolean isHidden
    ) {
        if (!ChatObject.isChannel(chat)) {
            final AlertDialog progressDialog = new AlertDialog(baseFragment.getContext(), AlertDialog.ALERT_TYPE_SPINNER);
            progressDialog.showDelayed(250);
            MessagesController.getInstance(currentAccount).convertToMegaGroup(baseFragment.getParentActivity(), chat.id, baseFragment, param -> {
                progressDialog.dismiss();
                if (param == 0) {
                    return;
                }

                CommunityUtils.linkToCommunityWithoutConvert(baseFragment, currentAccount, param, communityId, isHidden);
            });
        } else {
            CommunityUtils.linkToCommunityWithoutConvert(baseFragment, currentAccount, chat.id, communityId, isHidden);
        }
    }

    public static void linkToCommunityWithoutConvert(BaseFragment baseFragment,
                                                     int currentAccount,
                                                     long chatId,
                                                     long communityId,
                                                     boolean isHidden
    ) {
        MessagesController.getInstance(currentAccount).linkCommunity(-chatId, communityId, isHidden, (res, err) -> {
            if (err != null) {
                if (TextUtils.equals("COMMUNITY_REQUEST_CREATED", err.text)) {
                    onCommunityLinkSuccess(baseFragment, -chatId, CommunityUtils.STATUS_PENDING);
                    return;
                }

                BulletinFactory.of(baseFragment).showForError(err);
                return;
            }
            onCommunityLinkSuccess(baseFragment, -chatId, CommunityUtils.STATUS_JOINED);
        });
    }

    public static final int STATUS_CREATED = 0;
    public static final int STATUS_JOINED = 1;
    public static final int STATUS_PENDING = 2;

    public static void onCommunityLinkSuccess(BaseFragment baseFragment, long groupDialogId, int status) {
        INavigationLayout layout = null;
        List<BaseFragment> fragments = null;
        ChatActivity foundChatActivity = null;
        int chatIndex = -1;

        if (!AndroidUtilities.isTablet()) {
            layout = baseFragment.getParentLayout();
            if (layout != null) {
                fragments = layout.getFragmentStack();
                for (int a = fragments.size() - 2; a >= 0; a--) {
                    final BaseFragment fragment = fragments.get(a);
                    if (fragment instanceof ChatActivity) {
                        ChatActivity chatActivity = (ChatActivity) fragment;
                        if (chatActivity.getDialogId() == groupDialogId) {
                            foundChatActivity = chatActivity;
                            chatIndex = a;
                            break;
                        }
                    }
                }
            }
        }

        final boolean isChannel = ChatObject.isChannelAndNotMegaGroup(groupDialogId, baseFragment.getCurrentAccount());
        if (chatIndex != -1) {
            for (int a = fragments.size() - 2; a > chatIndex; a--) {
                final BaseFragment fragment = fragments.get(a);
                layout.removeFragmentFromStack(fragment);
            }
            baseFragment.finishFragment();

            final ChatActivity chatActivityFinal = foundChatActivity;
            AndroidUtilities.runOnUIThread(() -> {
                if (status != STATUS_PENDING) {
                    chatActivityFinal.onPageDownClicked();
                    chatActivityFinal.startFireworks();
                }

                showCommunityLinkSuccessToast(BulletinFactory.of(chatActivityFinal), status, isChannel);
            }, 250);
        } else {
            if (!(baseFragment instanceof DialogsActivity)) {
                baseFragment.finishFragment();
            }
            showCommunityLinkSuccessToast(BulletinFactory.global(), status, isChannel);
        }
    }

    public static void showCommunityLinkSuccessToast(BulletinFactory bulletinFactory, int status, boolean isChannel) {
        final int icon = status == STATUS_PENDING ?
                R.raw.timer_toast : R.raw.contact_check;
        final int iconSize = status == STATUS_PENDING ? 24 : 36;

        final CharSequence text;
        if (status == STATUS_CREATED) {
            text = getString(R.string.CommunityCommunityCreated);
        } else if (status == STATUS_JOINED) {
            text = getString(isChannel ?
                R.string.CommunityCommunityJoinedChannel :
                R.string.CommunityCommunityJoinedGroup);
        } else {
            text = getString(R.string.CommunityCommunityPending);
        }

        bulletinFactory.createSimpleBulletin(icon, text, iconSize).show();
    }


    public static CommunityChatType getCommunityChatType(int currentAccount, long dialogId) {
        final long linkedCommunityId;
        TLRPC.User user = null;
        TLRPC.Chat chat = null;
        if (dialogId > 0) {
            user = MessagesController.getInstance(currentAccount).getUser(dialogId);
            if (user == null) {
                return null;
            }
            linkedCommunityId = user.linked_community_id;
        } else {
            chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
            if (chat == null) {
                return null;
            }
            linkedCommunityId = chat.linked_community_id;
        }
        if (linkedCommunityId == 0) {
            return null;
        }

        final TLRPC.ChatFull communityFull = MessagesController.getInstance(currentAccount).getChatFull(linkedCommunityId);
        if (communityFull == null || communityFull.linked_peers == null) {
            return null;
        }

        for (TL_communities.CommunityPeer communityPeer : communityFull.linked_peers) {
            if (DialogObject.getPeerDialogId(communityPeer.peer) == dialogId) {
                return getCommunityChatType(chat, user,
                    user != null ? MessagesController.getInstance(currentAccount).getDialog(user.id) : null, communityPeer);
            }
        }

        return null;
    }

    public static CommunityChatType getCommunityChatType(TLRPC.Chat chat, TLRPC.User user, TLRPC.Dialog dialog, TL_communities.CommunityPeer peer)  {
        if (peer == null) {
            return null;
        }

        if (user != null) {
            return dialog != null ? CommunityChatType.YouAreIn : CommunityChatType.YouCanView;
        } else if (chat != null) {
            if (ChatObject.isInChat(chat)) {
                return CommunityChatType.YouAreIn;
            }
            if (ChatObject.isPublic(chat) || peer.can_view_history) {
                return CommunityChatType.YouCanView;
            }
            if (ChatObject.isCommunityPeerHidden(peer)) {
                return CommunityChatType.HiddenUnavailable;
            }
            return CommunityChatType.YouCanSendJoinRequest;
        }
        return null;
    }

    public static CharSequence buildServiceMessageText(MessageObject messageObject,
                                                       String communityName,
                                                       String userName,
                                                       boolean isChannel,
                                                       boolean isBot
    ) {
        final TLRPC.Message message = messageObject.messageOwner;
        final TLRPC.TL_messageActionChangeCommunity action = (TLRPC.TL_messageActionChangeCommunity) message.action;
        final boolean isUnknown = DialogObject.getPeerDialogId(message.peer_id) == DialogObject.getPeerDialogId(message.from_id);
        final boolean isRemoved = action.community_id == 0;

        if (isUnknown) {
            if (isRemoved) {
                return replaceTags(getString(isBot ?
                    R.string.CommunityServiceMessageBotRemovedUnknown : isChannel ?
                    R.string.CommunityServiceMessageChannelRemovedUnknown :
                    R.string.CommunityServiceMessageGroupRemovedUnknown));
            } else {
                return replaceTags(formatString(isBot ?
                    R.string.CommunityServiceMessageBotAddedUnknown : isChannel ?
                    R.string.CommunityServiceMessageChannelAddedUnknown :
                    R.string.CommunityServiceMessageGroupAddedUnknown, communityName));
            }
        } else if (messageObject.isOut()) {
            if (isRemoved) {
                return replaceTags(getString(isBot ?
                    R.string.CommunityServiceMessageBotYouRemoved : isChannel ?
                    R.string.CommunityServiceMessageChannelYouRemoved :
                    R.string.CommunityServiceMessageGroupYouRemoved));
            } else {
                return replaceTags(formatString(isBot ?
                    R.string.CommunityServiceMessageBotYouAdded : isChannel ?
                    R.string.CommunityServiceMessageChannelYouAdded :
                    R.string.CommunityServiceMessageGroupYouAdded, communityName));
            }
        } else {
            if (isRemoved) {
                return replaceTags(formatString(isBot ?
                    R.string.CommunityServiceMessageBotRemoved : isChannel ?
                    R.string.CommunityServiceMessageChannelRemoved :
                    R.string.CommunityServiceMessageGroupRemoved, userName));
            } else {
                return replaceTags(formatString(isBot ?
                    R.string.CommunityServiceMessageBotAdded : isChannel ?
                    R.string.CommunityServiceMessageChannelAdded :
                    R.string.CommunityServiceMessageGroupAdded, userName, communityName));
            }
        }
    }

    public static class DialogCellFactory extends UItem.UItemFactory<DialogCell> {
        static {
            setup(new DialogCellFactory());
        }

        @Override
        public boolean equals(UItem a, UItem b) {
            return a.id == b.id; // todo
        }

        @Override
        public boolean contentsEquals(UItem a, UItem b) {
            return equals(a, b);
        }

        @Override
        public DialogCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            final DialogCell cell = new DialogCell(null, context, false, false, currentAccount, resourcesProvider);
            cell.insideCommunityList = true;
            return cell;
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            DialogCell cell = (DialogCell) view;

            if (item.object instanceof TLRPC.Chat) {
                TLRPC.Chat chat = (TLRPC.Chat) item.object;

                cell.isHiddenInCommunity = ChatObject.isHiddenInCommunity(UserConfig.selectedAccount, chat);
                TLRPC.Dialog dialog = MessagesController.getInstance(UserConfig.selectedAccount).getDialog(-chat.id);
                cell.insideCommunityListNoDialog = dialog == null;
                if (dialog != null) {
                    cell.setCustomMessageWithoutRebuild(null);
                    cell.setDialog(dialog, DialogsActivity.DIALOGS_TYPE_DEFAULT, 0);
                } else {
                    cell.setCustomMessageWithoutRebuild(formatPluralString("Members", chat.participants_count));
                    cell.setDialog(-chat.id, null, 0, false, false);
                }
            } else if (item.object instanceof TLRPC.User) {
                TLRPC.User user = (TLRPC.User) item.object;

                cell.isHiddenInCommunity = ChatObject.isHiddenInCommunity(UserConfig.selectedAccount, user);
                TLRPC.Dialog dialog = MessagesController.getInstance(UserConfig.selectedAccount).getDialog(user.id);
                cell.insideCommunityListNoDialog = dialog == null;
                if (dialog != null) {
                    cell.setCustomMessageWithoutRebuild(null);
                    cell.setDialog(dialog, DialogsActivity.DIALOGS_TYPE_DEFAULT, 0);
                } else {
                    cell.setCustomMessageWithoutRebuild(getString(R.string.Bot));
                    cell.setDialog(user.id, null, 0, false, false);
                }
            }
        }

        public static UItem asCell(MessagesController.CommunityPeerDialog peerDialog) {
            return peerDialog.user != null ? asCell(peerDialog.user) : asCell(peerDialog.chat);
        }

        public static UItem asCell(
                TLRPC.User user
        ) {
            final UItem item = UItem.ofFactory(DialogCellFactory.class);
            item.longValue = user != null ? user.id : 0;
            item.id = Long.hashCode(item.longValue);
            item.object = user;
            return item;
        }

        public static UItem asCell(
                TLRPC.Chat chat
        ) {
            final UItem item = UItem.ofFactory(DialogCellFactory.class);
            item.longValue = chat != null ?  -chat.id : 0;
            item.id = Long.hashCode(item.longValue);
            item.object = chat;
            return item;
        }
    }
}
