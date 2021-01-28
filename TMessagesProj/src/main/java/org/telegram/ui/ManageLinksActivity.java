package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ManageChatTextCell;
import org.telegram.ui.Cells.ManageChatUserCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.DotDividerSpan;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.InviteLinkBottomSheet;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkActionView;
import org.telegram.ui.Components.LoadingStickerDrawable;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.TimerParticles;

import java.util.ArrayList;
import java.util.HashMap;

public class ManageLinksActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private ListAdapter listViewAdapter;
    private RecyclerListView listView;

    private TLRPC.Chat currentChat;
    private TLRPC.ChatFull info;
    private TLRPC.TL_chatInviteExported invite;
    private boolean isChannel;

    private int currentChatId;

    private int helpRow;
    private int permanentLinkHeaderRow;
    private int permanentLinkRow;
    private int dividerRow;
    private int createNewLinkRow;
    private int linksStartRow;
    private int linksEndRow;
    private int linksLoadingRow;
    private int revokedLinksStartRow;
    private int revokedLinksEndRow;
    private int revokedDivider;
    private int lastDivider;
    private int revokedHeader;
    private int revokeAllDivider;
    private int revokeAllRow;
    private int createLinkHelpRow;
    boolean linksLoading;

    private int rowCount;

    Drawable linkIcon;
    Drawable linkIconRevoked;

    boolean hasMore;
    boolean deletingRevokedLinks;

    private ArrayList<TLRPC.TL_chatInviteExported> invites = new ArrayList<>();
    private ArrayList<TLRPC.TL_chatInviteExported> revokedInvites = new ArrayList<>();
    private HashMap<Integer, TLRPC.User> users = new HashMap<>();
    private InviteLinkBottomSheet inviteLinkBottomSheet;


    long timeDif;

    private boolean isPublic;

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {

    }


    private static class EmptyView extends LinearLayout implements NotificationCenter.NotificationCenterDelegate {

        private BackupImageView stickerView;
        private LoadingStickerDrawable drawable;

        private int currentAccount = UserConfig.selectedAccount;

        private static final String stickerSetName = "UtyaDuck";

        public EmptyView(Context context) {
            super(context);

            setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12));
            setOrientation(LinearLayout.VERTICAL);

            stickerView = new BackupImageView(context);
            drawable = new LoadingStickerDrawable(stickerView, "M476.1,397.4c25.8-47.2,0.3-105.9-50.9-120c-2.5-6.9-7.8-12.7-15-16.4l0.4-229.4c0-12.3-10-22.4-22.4-22.4" +
                    "H128.5c-12.3,0-22.4,10-22.4,22.4l-0.4,229.8v0c0,6.7,2.9,12.6,7.6,16.7c-51.6,15.9-79.2,77.2-48.1,116.4" +
                    "c-8.7,11.7-13.4,27.5-14,47.2c-1.7,34.5,21.6,45.8,55.9,45.8c52.3,0,99.1,4.6,105.1-36.2c16.5,0.9,7.1-37.3-6.5-53.3" +
                    "c18.4-22.4,18.3-52.9,4.9-78.2c-0.7-5.3-3.8-9.8-8.1-12.6c-1.5-2-1.6-2-2.1-2.7c0.2-1,1.2-11.8-3.4-20.9h138.5" +
                    "c-4.8,8.8-4.7,17-2.9,22.1c-5.3,4.8-6.8,12.3-5.2,17c-11.4,24.9-10,53.8,4.3,77.5c-6.8,9.7-11.2,21.7-12.6,31.6" +
                    "c-0.2-0.2-0.4-0.3-0.6-0.5c0.8-3.3,0.4-6.4-1.3-7.8c9.3-12.1-4.5-29.2-17-21.7c-3.8-2.8-10.6-3.2-18.1-0.5" +
                    "c-2.4-10.6-21.1-10.6-28.6-1c-1.3,0.3-2.9,0.8-4.5,1.9c-5.2-0.9-10.9,0.1-14.1,4.4c-6.9,3-9.5,10.4-7.8,17c-0.9,1.8-1.1,4-0.8,6.3" +
                    "c-1.6,1.2-2.3,3.1-2,4.9c0.1,0.6,10.4,56.6,11.2,62c0.3,1.8,1.5,3.2,3.1,3.9c8.7,3.4,12,3.8,30.1,9.4c2.7,0.8,2.4,0.8,6.7-0.1" +
                    "c16.4-3.5,30.2-8.9,30.8-9.2c1.6-0.6,2.7-2,3.1-3.7c0.1-0.4,6.8-36.5,10-53.2c0.9,4.2,3.3,7.3,7.4,7.5c1.2,7.8,4.4,14.5,9.5,19.9" +
                    "c16.4,17.3,44.9,15.7,64.9,16.1c38.3,0.8,74.5,1.5,84.4-24.4C488.9,453.5,491.3,421.3,476.1,397.4z", AndroidUtilities.dp(104), AndroidUtilities.dp(104));
            stickerView.setImageDrawable(drawable);
            addView(stickerView, LayoutHelper.createLinear(104, 104, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 2, 0, 0));
        }

        private void setSticker() {
            TLRPC.TL_messages_stickerSet set = MediaDataController.getInstance(currentAccount).getStickerSetByName(stickerSetName);
            if (set == null) {
                set = MediaDataController.getInstance(currentAccount).getStickerSetByEmojiOrName(stickerSetName);
            }
            if (set != null && set.documents.size() >= 34) {
                TLRPC.Document document = set.documents.get(33);
                ImageLocation imageLocation = ImageLocation.getForDocument(document);
                stickerView.setImage(imageLocation, "104_104", "tgs", drawable, set);
            } else {
                MediaDataController.getInstance(currentAccount).loadStickersByEmojiOrName(stickerSetName, false, set == null);
                stickerView.setImageDrawable(drawable);
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            setSticker();
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.diceStickersDidLoad);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.diceStickersDidLoad);
        }

        @Override
        public void didReceivedNotification(int id, int account, Object... args) {
            if (id == NotificationCenter.diceStickersDidLoad) {
                String name = (String) args[0];
                if (stickerSetName.equals(name)) {
                    setSticker();
                }
            }
        }
    }

    public ManageLinksActivity(int chatId) {
        super();

        currentChatId = chatId;
        currentChat = MessagesController.getInstance(currentAccount).getChat(chatId);
        isChannel = ChatObject.isChannel(currentChat) && !currentChat.megagroup;
    }

    boolean loadRevoked = false;

    private void loadLinks() {
        TLRPC.TL_messages_getExportedChatInvites req = new TLRPC.TL_messages_getExportedChatInvites();
        req.peer = getMessagesController().getInputPeer(-currentChatId);
        req.admin_id = getMessagesController().getInputUser(getUserConfig().getCurrentUser());

        boolean revoked = loadRevoked;
        if (loadRevoked) {
            req.revoked = true;
            if (!revokedInvites.isEmpty()) {
                req.flags |= 4;
                req.offset_link = revokedInvites.get(revokedInvites.size() - 1).link;
            }
        } else {
            if (!invites.isEmpty()) {
                req.flags |= 4;
                req.offset_link = invites.get(invites.size() - 1).link;
            }
        }

        linksLoading = true;
        TLRPC.TL_chatInviteExported inviteFinal = isPublic ? null : invite;
        getConnectionsManager().sendRequest(req, (response, error) -> {

            TLRPC.TL_chatInviteExported permanentLink = null;
            if (error == null) {
                TLRPC.TL_messages_exportedChatInvites invites = (TLRPC.TL_messages_exportedChatInvites) response;
                if (invites.invites.size() > 0 && inviteFinal != null) {
                    for (int i = 0; i < invites.invites.size(); i++) {
                        if (((TLRPC.TL_chatInviteExported) invites.invites.get(i)).link.equals(inviteFinal.link)) {
                            permanentLink = (TLRPC.TL_chatInviteExported) invites.invites.remove(i);
                            break;
                        }
                    }
                }
            }

            TLRPC.TL_chatInviteExported finalPermanentLink = permanentLink;
            AndroidUtilities.runOnUIThread(() -> {
                getNotificationCenter().doOnIdle(() -> {
                    linksLoading = false;
                    hasMore = false;
                    if (finalPermanentLink != null) {
                        invite = finalPermanentLink;
                        if (info != null) {
                            info.exported_invite = finalPermanentLink;
                        }
                    }
                    if (error == null) {
                        TLRPC.TL_messages_exportedChatInvites invites = (TLRPC.TL_messages_exportedChatInvites) response;

                        if (revoked) {
                            for (int i = 0; i < invites.invites.size(); i++) {
                                TLRPC.TL_chatInviteExported in = (TLRPC.TL_chatInviteExported) invites.invites.get(i);
                                fixDate(in);
                                this.revokedInvites.add(in);
                            }
                        } else {
                            for (int i = 0; i < invites.invites.size(); i++) {
                                TLRPC.TL_chatInviteExported in = (TLRPC.TL_chatInviteExported) invites.invites.get(i);
                                fixDate(in);
                                this.invites.add(in);
                            }
                        }

                        for (int i = 0; i < invites.users.size(); i++) {
                            users.put(invites.users.get(i).id, invites.users.get(i));
                        }
                        int oldRowsCount = rowCount;
                        if (invites.invites.size() == 0) {
                            hasMore = false;
                        } else if (revoked) {
                            hasMore = this.revokedInvites.size() + 1 < invites.count;
                        } else {
                            hasMore = this.invites.size() + 1 < invites.count;
                        }
                        if (invites.invites.size() > 0 || loadRevoked) {
                            showItemsAnimated(oldRowsCount - 1);
                        }

                        if (!hasMore && !loadRevoked) {
                            hasMore = true;
                            loadRevoked = true;
                            loadLinks();
                        }
                        updateRows(true);
                        if (info != null && !revoked) {
                            info.invitesCount = invites.count;
                            getMessagesStorage().saveChatLinksCount(currentChatId, info.invitesCount);
                        }
                    }
                });
            });
        });
    }

    private void updateRows(boolean notify) {
        currentChat = MessagesController.getInstance(currentAccount).getChat(currentChatId);
        if (currentChat == null) {
            return;
        }

        linksStartRow = -1;
        linksEndRow = -1;
        linksLoadingRow = -1;
        revokedLinksStartRow = -1;
        revokedLinksEndRow = -1;
        revokedHeader = -1;
        revokedDivider = -1;
        lastDivider = -1;
        revokeAllRow = -1;
        revokeAllDivider = -1;
        createLinkHelpRow = -1;

        rowCount = 0;

        helpRow = rowCount++;
        permanentLinkHeaderRow = rowCount++;
        permanentLinkRow = rowCount++;
        dividerRow = rowCount++;
        createNewLinkRow = rowCount++;

        if (!invites.isEmpty()) {
            linksStartRow = rowCount;
            rowCount += invites.size();
            linksEndRow = rowCount;
        }

        if (!revokedInvites.isEmpty()) {
            revokedDivider = rowCount++;
            revokedHeader = rowCount++;
            revokedLinksStartRow = rowCount;
            rowCount += revokedInvites.size();
            revokedLinksEndRow = rowCount;
            revokeAllDivider = rowCount++;
            revokeAllRow = rowCount++;
        }

        if (linksLoading || hasMore) {
            linksLoadingRow = rowCount++;
        }

        if (invites.isEmpty() && revokedInvites.isEmpty() && !linksLoading) {
            createLinkHelpRow = rowCount++;
        }

        if (!invites.isEmpty() || !revokedInvites.isEmpty()) {
            lastDivider = rowCount++;
        }

        if (listViewAdapter != null && notify) {
            listViewAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("InviteLinks", R.string.InviteLinks));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView.setTag(Theme.key_windowBackgroundGray);
        FrameLayout frameLayout = (FrameLayout) fragmentView;


        listView = new RecyclerListView(context);
        LinearLayoutManager layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false);
        listView.setLayoutManager(layoutManager);
        listView.setAdapter(listViewAdapter = new ListAdapter(context));
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (hasMore && !linksLoading) {
                    int lastPosition = layoutManager.findLastVisibleItemPosition();
                    if (rowCount - lastPosition < 10) {
                        loadLinks();
                    }
                }
            }
        });
        DefaultItemAnimator defaultItemAnimator = new DefaultItemAnimator();
        defaultItemAnimator.setDelayAnimations(false);
        defaultItemAnimator.setSupportsChangeAnimations(false);
        listView.setItemAnimator(defaultItemAnimator);
        listView.setVerticalScrollbarPosition(LocaleController.isRTL ? RecyclerListView.SCROLLBAR_POSITION_LEFT : RecyclerListView.SCROLLBAR_POSITION_RIGHT);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView.setOnItemClickListener((view, position) -> {
            if (position == createNewLinkRow) {
                LinkEditActivity linkEditActivity = new LinkEditActivity(LinkEditActivity.CREATE_TYPE, currentChatId);
                linkEditActivity.setCallback(linkEditActivityCallback);
                presentFragment(linkEditActivity);
            } else if (position >= linksStartRow && position < linksEndRow) {
                TLRPC.TL_chatInviteExported invite = invites.get(position - linksStartRow);
                inviteLinkBottomSheet = new InviteLinkBottomSheet(context, invite, info, users, this, currentChatId, false);
                inviteLinkBottomSheet.show();
            } else if (position >= revokedLinksStartRow && position < revokedLinksEndRow) {
                TLRPC.TL_chatInviteExported invite = revokedInvites.get(position - revokedLinksStartRow);
                inviteLinkBottomSheet = new InviteLinkBottomSheet(context, invite, info, users, this, currentChatId, false);
                inviteLinkBottomSheet.show();
            } else if (position == revokeAllRow) {
                if (deletingRevokedLinks) {
                    return;
                }

                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString("DeleteAllRevokedLinks", R.string.DeleteAllRevokedLinks));
                builder.setMessage(LocaleController.getString("DeleteAllRevokedLinkHelp", R.string.DeleteAllRevokedLinkHelp));
                builder.setPositiveButton(LocaleController.getString("Delete", R.string.Delete), (dialogInterface2, i2) -> {
                    TLRPC.TL_messages_deleteRevokedExportedChatInvites req = new TLRPC.TL_messages_deleteRevokedExportedChatInvites();
                    req.peer = getMessagesController().getInputPeer(-currentChatId);
                    deletingRevokedLinks = true;
                    getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                        deletingRevokedLinks = false;
                        if (error == null) {
                            DiffCallback callback = saveListState();
                            revokedInvites.clear();
                            updateRows(false);
                            callback.fillPositions(callback.newPositionToItem);
                            DiffUtil.calculateDiff(callback).dispatchUpdatesTo(listViewAdapter);
                        }
                    }));
                });
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                showDialog(builder.create());
            }
        });
        listView.setOnItemLongClickListener((view, position) -> {
            if ((position >= linksStartRow && position < linksEndRow) || (position >= revokedLinksStartRow && position < revokedLinksEndRow)) {
                LinkCell cell = (LinkCell) view;
                cell.optionsView.callOnClick();
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                return true;
            }
            return false;
        });

        linkIcon = ContextCompat.getDrawable(context, R.drawable.msg_link_1);
        linkIconRevoked = ContextCompat.getDrawable(context, R.drawable.msg_link_2);
        linkIcon.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.MULTIPLY));
        updateRows(true);

        timeDif = getConnectionsManager().getCurrentTime() - (System.currentTimeMillis() / 1000L);
        return fragmentView;
    }

    public void setInfo(TLRPC.ChatFull chatFull, TLRPC.ExportedChatInvite invite) {
        info = chatFull;
        this.invite = (TLRPC.TL_chatInviteExported) invite;

        isPublic = !TextUtils.isEmpty(currentChat.username);
        loadLinks();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listViewAdapter != null) {
            listViewAdapter.notifyDataSetChanged();
        }
    }

    @SuppressWarnings("FieldCanBeLocal")
    public class HintInnerCell extends FrameLayout {

        private EmptyView emptyView;
        private TextView messageTextView;

        public HintInnerCell(Context context) {
            super(context);

            emptyView = new EmptyView(context);
            addView(emptyView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 10, 0, 0));

            messageTextView = new TextView(context);
            messageTextView.setTextColor(Theme.getColor(Theme.key_chats_message));
            messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            messageTextView.setGravity(Gravity.CENTER);
            messageTextView.setText(LocaleController.getString("PrimaryLinkHelp", R.string.PrimaryLinkHelp));

            addView(messageTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 52, 143, 52, 18));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), heightMeasureSpec);
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            if (createNewLinkRow == position) {
                return true;
            } else if (position >= linksStartRow && position < linksEndRow) {
                return true;
            } else if (position >= revokedLinksStartRow && position < revokedLinksEndRow) {
                return true;
            } else if (position == revokeAllRow) {
                return true;
            }
            return false;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                default:
                    view = new HintInnerCell(mContext);
                    view.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundWhite));
                    break;
                case 1:
                    view = new HeaderCell(mContext, 23);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 2:
                    LinkActionView linkActionView = new LinkActionView(mContext, ManageLinksActivity.this, null, currentChatId, true);
                    linkActionView.setPermanent(true);
                    linkActionView.setDelegate(new LinkActionView.Delegate() {
                        @Override
                        public void revokeLink() {
                            revokePermanent();
                        }

                        @Override
                        public void showUsersForPermanentLink() {
                            inviteLinkBottomSheet = new InviteLinkBottomSheet(linkActionView.getContext(), invite, info, users, ManageLinksActivity.this, currentChatId, true);
                            inviteLinkBottomSheet.show();
                        }
                    });
                    view = linkActionView;
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 3:
                    view = new TextCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 4:
                    view = new ShadowSectionCell(mContext);
                    break;
                case 5:
                    view = new LinkCell(mContext);
                    break;
                case 6:
                    FlickerLoadingView flickerLoadingView = new FlickerLoadingView(mContext);
                    flickerLoadingView.setIsSingleCell(true);
                    flickerLoadingView.setViewType(FlickerLoadingView.INVITE_LINKS_TYPE);
                    flickerLoadingView.showDate(false);
                    view = flickerLoadingView;
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 7:
                    view = new ShadowSectionCell(mContext);
                    view.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    break;
                case 8:
                    TextSettingsCell revokeAll = new TextSettingsCell(mContext);
                    revokeAll.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    revokeAll.setText(LocaleController.getString("DeleteAllRevokedLinks", R.string.DeleteAllRevokedLinks), false);
                    revokeAll.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText5));
                    view = revokeAll;
                    break;
                case 9:
                    TextInfoPrivacyCell cell = new TextInfoPrivacyCell(mContext);
                    cell.setText(LocaleController.getString("CreateNewLinkHelp", R.string.CreateNewLinkHelp));
                    cell.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    view = cell;
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 2:
                    LinkActionView linkActionView = (LinkActionView) holder.itemView;
                    if (isPublic) {
                        if (info != null) {
                            linkActionView.setLink("https://t.me/" + currentChat.username);
                            linkActionView.setUsers(0, null);
                            linkActionView.setPublic(true);
                        }
                    } else {
                        linkActionView.setPublic(false);
                        if (invite != null) {
                            TLRPC.TL_chatInviteExported inviteExported = invite;
                            linkActionView.setLink(inviteExported.link);
                            linkActionView.loadUsers(inviteExported, currentChatId);
                        } else {
                            linkActionView.setLink(null);
                        }
                    }
                    break;
                case 1:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == permanentLinkHeaderRow) {
                        headerCell.setText(LocaleController.getString("ChannelLinkTitle", R.string.ChannelLinkTitle));
                    } else {
                        headerCell.setText(LocaleController.getString("RevokedLinks", R.string.RevokedLinks));
                    }
                    break;
                case 3:
                    TextCell textCell = (TextCell) holder.itemView;
                    Drawable drawable1 = mContext.getResources().getDrawable(R.drawable.poll_add_circle);
                    Drawable drawable2 = mContext.getResources().getDrawable(R.drawable.poll_add_plus);
                    drawable1.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_switchTrackChecked), PorterDuff.Mode.MULTIPLY));
                    drawable2.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_checkboxCheck), PorterDuff.Mode.MULTIPLY));
                    CombinedDrawable combinedDrawable = new CombinedDrawable(drawable1, drawable2);

                    textCell.setTextAndIcon(LocaleController.getString("CreateNewLink", R.string.CreateNewLink), combinedDrawable, false);
                    break;
                case 5:
                    TLRPC.TL_chatInviteExported invite;
                    if (position >= linksStartRow && position < linksEndRow) {
                        invite = invites.get(position - linksStartRow);
                    } else {
                        invite = revokedInvites.get(position - revokedLinksStartRow);
                    }
                    LinkCell cell = (LinkCell) holder.itemView;
                    cell.setLink(invite, position - linksStartRow);
                    break;
            }
        }

        @Override
        public void onViewRecycled(RecyclerView.ViewHolder holder) {
            if (holder.itemView instanceof ManageChatUserCell) {
                ((ManageChatUserCell) holder.itemView).recycle();
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == helpRow) {
                return 0;
            } else if (position == permanentLinkHeaderRow || position == revokedHeader) {
                return 1;
            } else if (position == permanentLinkRow) {
                return 2;
            } else if (position == createNewLinkRow) {
                return 3;
            } else if (position == dividerRow || position == revokedDivider || position == revokeAllDivider) {
                return 4;
            } else if ((position >= linksStartRow && position < linksEndRow) || (position >= revokedLinksStartRow && position < revokedLinksEndRow)) {
                return 5;
            } else if (position == linksLoadingRow) {
                return 6;
            } else if (position == lastDivider) {
                return 7;
            } else if (position == revokeAllRow) {
                return 8;
            } else if (position == createLinkHelpRow) {
                return 9;
            }
            return 1;
        }
    }

    private void revokePermanent() {
        TLRPC.TL_messages_exportChatInvite req = new TLRPC.TL_messages_exportChatInvite();
        req.peer = getMessagesController().getInputPeer(-currentChatId);
        //req.legacy_revoke_permanent = true; TODO layer 124
        TLRPC.TL_chatInviteExported oldInvite = invite;
        invite = null;
        info.exported_invite = null;
        listViewAdapter.notifyItemChanged(permanentLinkRow);
        final int reqId = getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error == null) {
                invite = (TLRPC.TL_chatInviteExported) response;
                if (info != null) {
                    info.exported_invite = invite;
                }

                if (getParentActivity() == null) {
                    return;
                }

                listViewAdapter.notifyItemChanged(permanentLinkRow);
                oldInvite.revoked = true;
                DiffCallback callback = saveListState();
                revokedInvites.add(0, oldInvite);
                updateRows(false);
                callback.fillPositions(callback.newPositionToItem);
                DiffUtil.calculateDiff(callback).dispatchUpdatesTo(listViewAdapter);
                AndroidUtilities.updateVisibleRows(listView);
            }

        }));
        getConnectionsManager().bindRequestToGuid(reqId, classGuid);
    }

    public static class TextCell extends FrameLayout {

        private SimpleTextView textView;
        private ImageView imageView;

        public TextCell(Context context) {
            super(context);

            textView = new SimpleTextView(context);
            textView.setTextSize(16);
            textView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText2));
            textView.setTag(Theme.key_windowBackgroundWhiteBlueText2);
            addView(textView);

            imageView = new ImageView(context);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            addView(imageView);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = AndroidUtilities.dp(48);

            textView.measure(MeasureSpec.makeMeasureSpec(width - AndroidUtilities.dp(71 + 23), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(20), MeasureSpec.EXACTLY));
            imageView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(50), MeasureSpec.EXACTLY));
            setMeasuredDimension(width, AndroidUtilities.dp(50));
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int height = bottom - top;
            int width = right - left;

            int viewLeft;
            int viewTop = (height - textView.getTextHeight()) / 2;
            if (LocaleController.isRTL) {
                viewLeft = getMeasuredWidth() - textView.getMeasuredWidth() - AndroidUtilities.dp(imageView.getVisibility() == VISIBLE ? 70 : 25);
            } else {
                viewLeft = AndroidUtilities.dp(imageView.getVisibility() == VISIBLE ? 70 : 25);
            }
            textView.layout(viewLeft, viewTop, viewLeft + textView.getMeasuredWidth(), viewTop + textView.getMeasuredHeight());

            viewLeft = !LocaleController.isRTL ? (AndroidUtilities.dp(70) - imageView.getMeasuredWidth()) / 2 : width - imageView.getMeasuredWidth() - AndroidUtilities.dp(25);
            imageView.layout(viewLeft, 0, viewLeft + imageView.getMeasuredWidth(), imageView.getMeasuredHeight());
        }

        public void setTextAndIcon(String text, Drawable icon, boolean divider) {
            textView.setText(text);
            imageView.setImageDrawable(icon);
        }
    }

    private class LinkCell extends FrameLayout {

        private final static int LINK_STATE_BLUE = 0;
        private final static int LINK_STATE_GREEN = 1;
        private final static int LINK_STATE_YELLOW = 2;
        private final static int LINK_STATE_RED = 3;
        private final static int LINK_STATE_GRAY = 4;

        int lastDrawingState;

        TextView titleView;
        TextView subtitleView;
        TLRPC.TL_chatInviteExported invite;
        int position;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint paint2 = new Paint(Paint.ANTI_ALIAS_FLAG);
        RectF rectF = new RectF();

        ImageView optionsView;

        int animateFromState;
        float animateToStateProgress = 1f;
        float lastDrawExpringProgress;
        boolean animateHideExpiring;


        public LinkCell(@NonNull Context context) {
            super(context);

            paint2.setStyle(Paint.Style.STROKE);
            paint2.setStrokeCap(Paint.Cap.ROUND);

            LinearLayout linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(LinearLayout.VERTICAL);

            addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 70, 0, 30, 0));

            titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            titleView.setLines(1);
            titleView.setEllipsize(TextUtils.TruncateAt.END);

            subtitleView = new TextView(context);
            subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));

            linearLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            linearLayout.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 0));

            optionsView = new ImageView(context);
            optionsView.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_ab_other));
            optionsView.setScaleType(ImageView.ScaleType.CENTER);
            optionsView.setColorFilter(Theme.getColor(Theme.key_dialogTextGray3));
            optionsView.setOnClickListener(view -> {
                if (invite == null) {
                    return;
                }
                ArrayList<String> items = new ArrayList<>();
                ArrayList<Integer> icons = new ArrayList<>();
                final ArrayList<Integer> actions = new ArrayList<>();

                if (invite.revoked) {
                    items.add(LocaleController.getString("Delete", R.string.Delete));
                    icons.add(R.drawable.msg_delete);
                    actions.add(4);
                } else {
                    items.add(LocaleController.getString("Copy", R.string.Copy));
                    icons.add(R.drawable.msg_copy);
                    actions.add(0);

                    items.add(LocaleController.getString("Share", R.string.ShareLink));
                    icons.add(R.drawable.msg_share);
                    actions.add(1);

                    items.add(LocaleController.getString("Edit", R.string.Edit));
                    icons.add(R.drawable.msg_edit);
                    actions.add(2);

                    items.add(LocaleController.getString("RevokeLink", R.string.RevokeLink));
                    icons.add(R.drawable.msg_delete);
                    actions.add(3);
                }

                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setItems(items.toArray(new CharSequence[0]), AndroidUtilities.toIntArray(icons), (dialogInterface, i) -> {
                    switch (actions.get(i)) {
                        case 0:
                            try {
                                if (invite.link == null) {
                                    return;
                                }
                                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                                android.content.ClipData clip = android.content.ClipData.newPlainText("label", invite.link);
                                clipboard.setPrimaryClip(clip);
                                BulletinFactory.createCopyLinkBulletin(ManageLinksActivity.this).show();
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                            break;
                        case 1:
                            try {
                                if (invite.link == null) {
                                    return;
                                }
                                Intent intent = new Intent(Intent.ACTION_SEND);
                                intent.setType("text/plain");
                                intent.putExtra(Intent.EXTRA_TEXT, invite.link);
                                startActivityForResult(Intent.createChooser(intent, LocaleController.getString("InviteToGroupByLink", R.string.InviteToGroupByLink)), 500);
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                            break;
                        case 2:
                            editLink(invite);
                            break;
                        case 3:
                            TLRPC.TL_chatInviteExported inviteFinal = invite;
                            AlertDialog.Builder builder2 = new AlertDialog.Builder(getParentActivity());
                            builder2.setMessage(LocaleController.getString("RevokeAlert", R.string.RevokeAlert));
                            builder2.setTitle(LocaleController.getString("RevokeLink", R.string.RevokeLink));
                            builder2.setPositiveButton(LocaleController.getString("RevokeButton", R.string.RevokeButton), (dialogInterface2, i2) -> {
                                revokeLink(inviteFinal);
                            });
                            builder2.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                            showDialog(builder2.create());
                            break;
                        case 4:
                            inviteFinal = invite;
                            builder2 = new AlertDialog.Builder(getParentActivity());
                            builder2.setTitle(LocaleController.getString("DeleteLink", R.string.DeleteLink));
                            builder2.setMessage(LocaleController.getString("DeleteLinkHelp", R.string.DeleteLinkHelp));
                            builder2.setPositiveButton(LocaleController.getString("Delete", R.string.Delete), (dialogInterface2, i2) -> {
                                deleteLink(inviteFinal);
                            });
                            builder2.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                            showDialog(builder2.create());
                            break;
                    }
                });
                builder.setTitle(LocaleController.getString("InviteLink", R.string.InviteLink));
                AlertDialog alert = builder.create();
                builder.show();
                alert.setItemColor(items.size() - 1, Theme.getColor(Theme.key_dialogTextRed2), Theme.getColor(Theme.key_dialogRedIcon));
            });
            optionsView.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 1));
            addView(optionsView, LayoutHelper.createFrame(40, 48, Gravity.RIGHT | Gravity.CENTER_VERTICAL));


            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            setWillNotDraw(false);
        }


        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(64), MeasureSpec.EXACTLY));
            paint2.setStrokeWidth(AndroidUtilities.dp(2));
        }


        private TimerParticles timerParticles = new TimerParticles();

        @Override
        protected void onDraw(Canvas canvas) {
            if (invite == null) {
                return;
            }
            int cX = AndroidUtilities.dp(36);
            int cY = getMeasuredHeight() / 2;

            int drawState;

            float progress = 0;
            if (invite.expired || invite.revoked) {
                drawState = invite.revoked ? LINK_STATE_GRAY : LINK_STATE_RED;
            } else if (invite.expire_date > 0) {
                long currentTime = System.currentTimeMillis() + timeDif * 1000L;
                long expireTime = invite.expire_date * 1000L;
                long date = (invite.start_date <= 0 ? invite.date : invite.start_date) * 1000L;
                long from = currentTime - date;
                long to = expireTime - date;
                progress = (1f - from / (float) to);
                if (progress <= 0) {
                    invite.expired = true;
                    drawState = LINK_STATE_RED;
                } else {
                    drawState = LINK_STATE_GREEN;
                }
            } else {
                drawState = LINK_STATE_BLUE;
            }

            if (drawState != lastDrawingState && lastDrawingState >= 0) {
                animateFromState = lastDrawingState;
                animateToStateProgress = 0f;
                if (hasProgress(animateFromState) && !hasProgress(drawState)) {
                    animateHideExpiring = true;
                } else {
                    animateHideExpiring = false;
                }
            }

            lastDrawingState = drawState;

            if (animateToStateProgress != 1f) {
                animateToStateProgress += 16f / 250f;
                if (animateToStateProgress >= 1f) {
                    animateToStateProgress = 1f;
                    animateHideExpiring = false;
                } else {
                    invalidate();
                }
            }
            int color;
            if (animateToStateProgress != 1f) {
                color = ColorUtils.blendARGB(getColor(animateFromState, progress), getColor(drawState, progress), animateToStateProgress);
            } else {
                color = getColor(drawState, progress);
            }

            paint.setColor(color);
            canvas.drawCircle(cX, cY, AndroidUtilities.dp(32) / 2f, paint);
            if (animateHideExpiring || (!invite.expired && invite.expire_date > 0 && !invite.revoked)) {
                if (animateHideExpiring) {
                    progress = lastDrawExpringProgress;
                }

                paint2.setColor(color);
                rectF.set(cX - AndroidUtilities.dp(20), cY - AndroidUtilities.dp(20), cX + AndroidUtilities.dp(20), cY + AndroidUtilities.dp(20));

                if (animateToStateProgress != 1f && (!hasProgress(animateFromState) || animateHideExpiring)) {
                    canvas.save();
                    float a = (animateHideExpiring ? (1f - animateToStateProgress) : animateToStateProgress);
                    float s = (float) (0.7 + 0.3f * a);
                    canvas.scale(s, s, rectF.centerX(), rectF.centerY());
                    canvas.drawArc(rectF, -90, -progress * 360, false, paint2);
                    timerParticles.draw(canvas, paint2, rectF, -progress * 360 , a);
                    canvas.restore();
                } else {
                    canvas.drawArc(rectF, -90, -progress * 360, false, paint2);
                    timerParticles.draw(canvas, paint2, rectF, -progress * 360, 1f);
                }
                if (!isPaused) {
                    invalidate();
                }
                lastDrawExpringProgress = progress;
            }

            if (invite.revoked) {
                linkIconRevoked.setBounds(cX - AndroidUtilities.dp(12), cY - AndroidUtilities.dp(12), cX + AndroidUtilities.dp(12), cY + AndroidUtilities.dp(12));
                linkIconRevoked.draw(canvas);
            } else {
                linkIcon.setBounds(cX - AndroidUtilities.dp(12), cY - AndroidUtilities.dp(12), cX + AndroidUtilities.dp(12), cY + AndroidUtilities.dp(12));
                linkIcon.draw(canvas);
            }

        }

        private boolean hasProgress(int state) {
            return state == LINK_STATE_YELLOW || state == LINK_STATE_GREEN;
        }

        private int getColor(int state, float progress) {
            if (state == LINK_STATE_RED) {
                return Theme.getColor(Theme.key_chat_attachAudioBackground);
            } else if (state == LINK_STATE_GREEN) {
                if (progress > 0.5f) {
                    float p = (progress - 0.5f) / 0.5f;
                    return ColorUtils.blendARGB(Theme.getColor(Theme.key_chat_attachLocationBackground), Theme.getColor(Theme.key_chat_attachPollBackground), (1f - p));
                } else {
                    float p =  progress /0.5f;
                    return ColorUtils.blendARGB(Theme.getColor(Theme.key_chat_attachPollBackground),Theme.getColor(Theme.key_chat_attachAudioBackground), (1f - p));
                }
            } else if (state == LINK_STATE_YELLOW) {
                return Theme.getColor(Theme.key_chat_attachPollBackground);
            } else if (state == LINK_STATE_GRAY) {
                return Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon);
            } else {
                return Theme.getColor(Theme.key_featuredStickers_addButton);
            }
        }

        public void setLink(TLRPC.TL_chatInviteExported invite, int position) {
            if (this.invite == null || invite == null || !this.invite.link.equals(invite.link)) {
                lastDrawingState = -1;
                animateToStateProgress = 1f;
            }
            this.invite = invite;
            this.position = position;

            if (invite.link.startsWith("https://")) {
                titleView.setText(invite.link.substring("https://".length()));
            } else {
                titleView.setText(invite.link);
            }

            String joinedString = invite.usage == 0 ? LocaleController.getString("NoOneJoinedYet", R.string.NoOneJoinedYet) : LocaleController.formatPluralString("PeopleJoined", invite.usage);
            if (invite.expired || invite.revoked) {
                if (invite.revoked && invite.usage == 0) {
                    joinedString = LocaleController.getString("NoOneJoined", R.string.NoOneJoined);
                }
                SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(joinedString);
                DotDividerSpan dotDividerSpan = new DotDividerSpan();
                dotDividerSpan.setTopPadding(AndroidUtilities.dp(1.5f));
                spannableStringBuilder.append("  .  ").setSpan(dotDividerSpan, spannableStringBuilder.length() - 3, spannableStringBuilder.length() - 2, 0);
                if (!invite.revoked && invite.usage_limit > 0 && invite.usage >= invite.usage_limit) {
                    spannableStringBuilder.append(LocaleController.getString("LinkLimitReached", R.string.LinkLimitReached));
                } else {
                    spannableStringBuilder.append(invite.revoked ? LocaleController.getString("Revoked", R.string.Revoked) : LocaleController.getString("Expired", R.string.Expired));
                }
                subtitleView.setText(spannableStringBuilder);
            } else {
                subtitleView.setText(joinedString);
            }

//            if (invite.revoked) {
//                optionsView.setVisibility(View.GONE);
//            } else {
//                optionsView.setVisibility(View.VISIBLE);
//            }
        }
    }

    public void deleteLink(TLRPC.TL_chatInviteExported invite) {
        TLRPC.TL_messages_deleteExportedChatInvite req = new TLRPC.TL_messages_deleteExportedChatInvite();
        req.link = invite.link;
        req.peer = getMessagesController().getInputPeer(-currentChatId);
        TLRPC.TL_chatInviteExported inviteFinal = invite;
        getConnectionsManager().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (error == null) {
                        linkEditActivityCallback.onLinkRemoved(inviteFinal);
                    }
                });
            }
        });
    }

    public void editLink(TLRPC.TL_chatInviteExported invite) {
        LinkEditActivity activity = new LinkEditActivity(LinkEditActivity.EDIT_TYPE, currentChatId);
        activity.setCallback(linkEditActivityCallback);
        activity.setInviteToEdit(invite);
        presentFragment(activity);
    }

    public void revokeLink(TLRPC.TL_chatInviteExported invite) {
        TLRPC.TL_messages_editExportedChatInvite req = new TLRPC.TL_messages_editExportedChatInvite();
        req.link = invite.link;
        req.revoked = true;
        req.peer = getMessagesController().getInputPeer(-currentChatId);
        TLRPC.TL_chatInviteExported inviteFinal = invite;
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error == null) {
                linkEditActivityCallback.onLinkEdited(inviteFinal, response);
                if (info != null) {
                    info.invitesCount--;
                    if (info.invitesCount < 0) {
                        info.invitesCount = 0;
                    }
                    getMessagesStorage().saveChatLinksCount(currentChatId, info.invitesCount);
                }
            }
        }));
    }

    private void showItemsAnimated(int from) {
        if (isPaused || listView == null) {
            return;
        }
        View progressView = null;
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            if (listView.getChildAdapterPosition(child) >= 0 && child instanceof FlickerLoadingView) {
                progressView = child;
            }
        }
        final View finalProgressView = progressView;
        if (finalProgressView != null) {
            listView.removeView(finalProgressView);
        }

        listView.invalidate();
        if (finalProgressView != null && finalProgressView.getParent() == null) {
            RecyclerView.LayoutManager layoutManager = listView.getLayoutManager();
            listView.addView(finalProgressView);
            if (layoutManager != null) {
                layoutManager.ignoreView(finalProgressView);
                Animator animator = ObjectAnimator.ofFloat(finalProgressView, View.ALPHA, finalProgressView.getAlpha(), 0);
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        finalProgressView.setAlpha(1f);
                        layoutManager.stopIgnoringView(finalProgressView);
                        listView.removeView(finalProgressView);
                    }
                });
                animator.start();
            }
        }
        listView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                listView.getViewTreeObserver().removeOnPreDrawListener(this);
                int n = listView.getChildCount();
                AnimatorSet animatorSet = new AnimatorSet();
                for (int i = 0; i < n; i++) {
                    View child = listView.getChildAt(i);
                    if (child == finalProgressView || listView.getChildAdapterPosition(child) < from) {
                        continue;
                    }
                    child.setAlpha(0);
                    int s = Math.min(listView.getMeasuredHeight(), Math.max(0, child.getTop()));
                    int delay = (int) ((s / (float) listView.getMeasuredHeight()) * 100);
                    ObjectAnimator a = ObjectAnimator.ofFloat(child, View.ALPHA, 0, 1f);
                    a.setStartDelay(delay);
                    a.setDuration(200);
                    animatorSet.playTogether(a);
                }

                animatorSet.start();
                return false;
            }
        });
    }

    private final LinkEditActivity.Callback linkEditActivityCallback = new LinkEditActivity.Callback() {
        @Override
        public void onLinkCreated(TLObject response) {
            if (response instanceof TLRPC.TL_chatInviteExported) {
                AndroidUtilities.runOnUIThread(() -> {
                    DiffCallback callback = saveListState();
                    invites.add(0, (TLRPC.TL_chatInviteExported) response);
                    updateRows(false);
                    callback.fillPositions(callback.newPositionToItem);
                    DiffUtil.calculateDiff(callback).dispatchUpdatesTo(listViewAdapter);
                    AndroidUtilities.updateVisibleRows(listView);
                    if (info != null) {
                        info.invitesCount++;
                        getMessagesStorage().saveChatLinksCount(currentChatId, info.invitesCount);
                    }
                }, 200);
            }
        }

        @Override
        public void onLinkEdited(TLRPC.TL_chatInviteExported inviteToEdit, TLObject response) {
            if (response instanceof TLRPC.TL_messages_exportedChatInvite) {
                TLRPC.TL_chatInviteExported edited = (TLRPC.TL_chatInviteExported) ((TLRPC.TL_messages_exportedChatInvite) response).invite;
                fixDate(edited);
                for (int i = 0; i < invites.size(); i++) {
                    if (invites.get(i).link.equals(inviteToEdit.link)) {
                        if (edited.revoked) {
                            DiffCallback callback = saveListState();
                            invites.remove(i);
                            revokedInvites.add(0, edited);
                            updateRows(false);
                            callback.fillPositions(callback.newPositionToItem);
                            DiffUtil.calculateDiff(callback).dispatchUpdatesTo(listViewAdapter);
                            AndroidUtilities.updateVisibleRows(listView);
                        } else {
                            invites.set(i, edited);
                            listViewAdapter.notifyDataSetChanged();
                        }
                        return;
                    }
                }
            }
        }

        @Override
        public void onLinkRemoved(TLRPC.TL_chatInviteExported removedInvite) {
            for (int i = 0; i < revokedInvites.size(); i++) {
                if (revokedInvites.get(i).link.equals(removedInvite.link)) {
                    DiffCallback callback = saveListState();
                    revokedInvites.remove(i);
                    updateRows(false);
                    callback.fillPositions(callback.newPositionToItem);
                    DiffUtil.calculateDiff(callback).dispatchUpdatesTo(listViewAdapter);
                    AndroidUtilities.updateVisibleRows(listView);
                    return;
                }
            }
        }

        @Override
        public void revokeLink(TLRPC.TL_chatInviteExported inviteFinal) {
            ManageLinksActivity.this.revokeLink(inviteFinal);
        }
    };


    private class DiffCallback extends DiffUtil.Callback {

        int oldRowCount;
        int oldLinksStartRow;
        int oldLinksEndRow;
        int oldRevokedLinksStartRow;
        int oldRevokedLinksEndRow;

        SparseIntArray oldPositionToItem = new SparseIntArray();
        SparseIntArray newPositionToItem = new SparseIntArray();
        ArrayList<TLRPC.TL_chatInviteExported> oldLinks = new ArrayList<>();
        ArrayList<TLRPC.TL_chatInviteExported> oldRevokedLinks = new ArrayList<>();


        @Override
        public int getOldListSize() {
            return oldRowCount;
        }

        @Override
        public int getNewListSize() {
            return rowCount;
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            if (oldItemPosition >= oldLinksStartRow && oldItemPosition < oldLinksEndRow || oldItemPosition >= oldRevokedLinksStartRow && oldItemPosition < oldRevokedLinksEndRow) {
                if (newItemPosition >= linksStartRow && newItemPosition < linksEndRow || newItemPosition >= revokedLinksStartRow && newItemPosition < revokedLinksEndRow) {
                    TLRPC.TL_chatInviteExported newItem;
                    TLRPC.TL_chatInviteExported oldItem;
                    if (newItemPosition >= linksStartRow && newItemPosition < linksEndRow) {
                        newItem = invites.get(newItemPosition - linksStartRow);
                    } else {
                        newItem = revokedInvites.get(newItemPosition - revokedLinksStartRow);
                    }
                    if (oldItemPosition >= oldLinksStartRow && oldItemPosition < oldLinksEndRow) {
                        oldItem = oldLinks.get(oldItemPosition - oldLinksStartRow);
                    } else {
                        oldItem = oldRevokedLinks.get(oldItemPosition - oldRevokedLinksStartRow);
                    }
                    return oldItem.link.equals(newItem.link);
                }
            }
            int oldItem = oldPositionToItem.get(oldItemPosition, -1);
            int newItem = newPositionToItem.get(newItemPosition, -1);
            return oldItem >= 0 && oldItem == newItem;
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            return areItemsTheSame(oldItemPosition, newItemPosition);
        }

        public void fillPositions(SparseIntArray sparseIntArray) {
            sparseIntArray.clear();
            int pointer = 0;

            put(++pointer, helpRow, sparseIntArray);
            put(++pointer, permanentLinkHeaderRow, sparseIntArray);
            put(++pointer, permanentLinkRow, sparseIntArray);
            put(++pointer, dividerRow, sparseIntArray);
            put(++pointer, createNewLinkRow, sparseIntArray);
            put(++pointer, revokedHeader, sparseIntArray);
            put(++pointer, revokedDivider, sparseIntArray);
            put(++pointer, lastDivider, sparseIntArray);
            put(++pointer, revokeAllDivider, sparseIntArray);
            put(++pointer, revokeAllRow, sparseIntArray);
            put(++pointer, createLinkHelpRow, sparseIntArray);
        }

        private void put(int id, int position, SparseIntArray sparseIntArray) {
            if (position >= 0) {
                sparseIntArray.put(position, id);
            }
        }
    }

    private DiffCallback saveListState() {
        DiffCallback callback = new DiffCallback();
        callback.fillPositions(callback.oldPositionToItem);
        callback.oldLinksStartRow = linksStartRow;
        callback.oldLinksEndRow = linksEndRow;
        callback.oldRevokedLinksStartRow = revokedLinksStartRow;
        callback.oldRevokedLinksEndRow = revokedLinksEndRow;
        callback.oldRowCount = rowCount;
        callback.oldLinks.clear();
        callback.oldLinks.addAll(invites);

        callback.oldRevokedLinks.clear();
        callback.oldRevokedLinks.addAll(revokedInvites);

        return callback;
    }

    public void fixDate(TLRPC.TL_chatInviteExported edited) {
        if (edited.expire_date > 0) {
            edited.expired = getConnectionsManager().getCurrentTime() >= edited.expire_date;
        } else if (edited.usage_limit > 0) {
            edited.expired = edited.usage >= edited.usage_limit;
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        ThemeDescription.ThemeDescriptionDelegate cellDelegate = () -> {
            if (listView != null) {
                int count = listView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View child = listView.getChildAt(a);
                    if (child instanceof ManageChatUserCell) {
                        ((ManageChatUserCell) child).update(0);
                    }
                    if (child instanceof LinkActionView) {
                        ((LinkActionView) child).updateColors();
                    }
                }
            }
            if (inviteLinkBottomSheet != null) {
                inviteLinkBottomSheet.updateColors();
            }
        };

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{HeaderCell.class, TextCell.class, LinkActionView.class, LinkCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_windowBackgroundGray));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_windowBackgroundWhite));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{ManageChatUserCell.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{ManageChatUserCell.class}, new String[]{"statusColor"}, null, null, cellDelegate, Theme.key_windowBackgroundWhiteGrayText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{ManageChatUserCell.class}, new String[]{"statusOnlineColor"}, null, null, cellDelegate, Theme.key_windowBackgroundWhiteBlueText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{ManageChatUserCell.class}, null, Theme.avatarDrawables, null, Theme.key_avatar_text));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundRed));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundOrange));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundViolet));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundGreen));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundCyan));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundBlue));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundPink));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HintInnerCell.class}, new String[]{"messageTextView"}, null, null, null, Theme.key_chats_message));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{ManageChatTextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{ManageChatTextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayIcon));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{ManageChatTextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueButton));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{ManageChatTextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueIcon));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueText2));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_switchTrackChecked));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_checkboxCheck));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{LinkCell.class}, new String[]{"titleView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{LinkCell.class}, new String[]{"subtitleView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{LinkCell.class}, new String[]{"optionsView"}, null, null, null, Theme.key_dialogTextGray3));
        return themeDescriptions;
    }
}
