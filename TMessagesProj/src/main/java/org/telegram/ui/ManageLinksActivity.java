package org.telegram.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
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
import org.telegram.messenger.AnimationNotificationsLocker;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.CreationTextCell;
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
import org.telegram.ui.Components.RecyclerItemsEnterAnimator;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.TimerParticles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

public class ManageLinksActivity extends BaseFragment {

    private ListAdapter listViewAdapter;
    private RecyclerListView listView;

    private TLRPC.Chat currentChat;
    private TLRPC.ChatFull info;
    private TLRPC.TL_chatInviteExported invite;
    private long adminId;

    private boolean isChannel;

    private long currentChatId;

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
    private int linksHeaderRow;

    private int creatorRow;
    private int creatorDividerRow;

    private int adminsHeaderRow;
    private int adminsDividerRow;
    private int adminsStartRow;
    private int adminsEndRow;

    boolean linksLoading;

    private int rowCount;

    Drawable linkIcon;
    Drawable linkIconRevoked;

    boolean hasMore;
    boolean deletingRevokedLinks;
    boolean loadAdmins;
    boolean adminsLoaded;

    private int invitesCount;
    private boolean isOpened;
    private boolean transitionFinished;

    private RecyclerItemsEnterAnimator recyclerItemsEnterAnimator;
    private ArrayList<TLRPC.TL_chatInviteExported> invites = new ArrayList<>();
    private ArrayList<TLRPC.TL_chatInviteExported> revokedInvites = new ArrayList<>();
    private HashMap<Long, TLRPC.User> users = new HashMap<>();
    private InviteLinkBottomSheet inviteLinkBottomSheet;

    private ArrayList<TLRPC.TL_chatAdminWithInvites> admins = new ArrayList<>();

    long timeDif;

    private boolean isPublic;
    private boolean canEdit;

    Runnable updateTimerRunnable = new Runnable() {
        @Override
        public void run() {
            if (listView == null) {
                return;
            }
            for (int i = 0; i < listView.getChildCount(); i++) {
                View child = listView.getChildAt(i);
                if (child instanceof LinkCell) {
                    LinkCell linkCell = (LinkCell) child;
                    if (linkCell.timerRunning) {
                        linkCell.setLink(linkCell.invite, linkCell.position);
                    }
                }

            }
            AndroidUtilities.runOnUIThread(this, 500);
        }
    };

    private static class EmptyView extends LinearLayout implements NotificationCenter.NotificationCenterDelegate {

        private BackupImageView stickerView;

        private final int currentAccount = UserConfig.selectedAccount;

        private static final String stickerSetName = AndroidUtilities.STICKERS_PLACEHOLDER_PACK_NAME;

        public EmptyView(Context context) {
            super(context);

            setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12));
            setOrientation(LinearLayout.VERTICAL);

            stickerView = new BackupImageView(context);
            addView(stickerView, LayoutHelper.createLinear(104, 104, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 2, 0, 0));
        }

        private void setSticker() {
            TLRPC.TL_messages_stickerSet set = MediaDataController.getInstance(currentAccount).getStickerSetByName(stickerSetName);
            if (set == null) {
                set = MediaDataController.getInstance(currentAccount).getStickerSetByEmojiOrName(stickerSetName);
            }
            if (set != null && set.documents.size() >= 4) {
                TLRPC.Document document = set.documents.get(3);
                ImageLocation imageLocation = ImageLocation.getForDocument(document);
                SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(document, Theme.key_windowBackgroundGray, 1.0f);
                stickerView.setImage(imageLocation, "104_104", "tgs", svgThumb, set);
            } else {
                MediaDataController.getInstance(currentAccount).loadStickersByEmojiOrName(stickerSetName, false, set == null);
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

    public ManageLinksActivity(long chatId, long adminId, int invitesCount) {
        super();
        currentChatId = chatId;
        this.invitesCount = invitesCount;
        currentChat = MessagesController.getInstance(currentAccount).getChat(chatId);
        isChannel = ChatObject.isChannel(currentChat) && !currentChat.megagroup;
        if (adminId == 0) {
            this.adminId = getAccountInstance().getUserConfig().clientUserId;
        } else {
            this.adminId = adminId;
        }

        TLRPC.User user = getMessagesController().getUser(this.adminId);
        canEdit = (this.adminId == getAccountInstance().getUserConfig().clientUserId) || (user != null && !user.bot);
    }

    boolean loadRevoked = false;

    private void loadLinks(boolean notify) {
        if (loadAdmins && !adminsLoaded) {
            linksLoading = true;
            TLRPC.TL_messages_getAdminsWithInvites req = new TLRPC.TL_messages_getAdminsWithInvites();
            req.peer = getMessagesController().getInputPeer(-currentChatId);
            int reqId = getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> getNotificationCenter().doOnIdle(() -> {
                linksLoading = false;
                if (error == null) {
                    TLRPC.TL_messages_chatAdminsWithInvites adminsWithInvites = (TLRPC.TL_messages_chatAdminsWithInvites) response;
                    for (int i = 0; i < adminsWithInvites.admins.size(); i++) {
                        TLRPC.TL_chatAdminWithInvites admin = adminsWithInvites.admins.get(i);
                        if (admin.admin_id != getAccountInstance().getUserConfig().clientUserId) {
                            admins.add(admin);
                        }
                    }
                    for (int i = 0; i < adminsWithInvites.users.size(); i++) {
                        TLRPC.User user = adminsWithInvites.users.get(i);
                        users.put(user.id, user);
                    }

                }
                int oldRowsCount = rowCount;
                adminsLoaded = true;

                hasMore = false;
                if (admins.size() > 0) {
                    if (recyclerItemsEnterAnimator != null && !isPaused && isOpened) {
                        recyclerItemsEnterAnimator.showItemsAnimated(oldRowsCount + 1);
                    }
                }
                if (!hasMore || (invites.size() + revokedInvites.size() + admins.size()) >= 5) {
                    resumeDelayedFragmentAnimation();
                }

                if (!hasMore && !loadRevoked) {
                    hasMore = true;
                    loadRevoked = true;
                    loadLinks(false);
                }
                updateRows(true);
            })));
            getConnectionsManager().bindRequestToGuid(reqId, getClassGuid());
        } else {
            TLRPC.TL_messages_getExportedChatInvites req = new TLRPC.TL_messages_getExportedChatInvites();
            req.peer = getMessagesController().getInputPeer(-currentChatId);
            if (adminId == getUserConfig().getClientUserId()) {
                req.admin_id = getMessagesController().getInputUser(getUserConfig().getCurrentUser());
            } else {
                req.admin_id = getMessagesController().getInputUser(adminId);
            }

            boolean revoked = loadRevoked;
            if (loadRevoked) {
                req.revoked = true;
                if (!revokedInvites.isEmpty()) {
                    req.flags |= 4;
                    req.offset_link = revokedInvites.get(revokedInvites.size() - 1).link;
                    req.offset_date = revokedInvites.get(revokedInvites.size() - 1).date;
                }
            } else {
                if (!invites.isEmpty()) {
                    req.flags |= 4;
                    req.offset_link = invites.get(invites.size() - 1).link;
                    req.offset_date = invites.get(invites.size() - 1).date;
                }
            }

            linksLoading = true;
            TLRPC.TL_chatInviteExported inviteFinal = isPublic ? null : invite;
            int reqId = getConnectionsManager().sendRequest(req, (response, error) -> {

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
                AndroidUtilities.runOnUIThread(() -> getNotificationCenter().doOnIdle(() -> {
                    DiffCallback callback = saveListState();
                    linksLoading = false;
                    hasMore = false;
                    if (finalPermanentLink != null) {
                        invite = finalPermanentLink;
                        if (info != null) {
                            info.exported_invite = finalPermanentLink;
                        }
                    }
                    boolean updateByDiffUtils = false;

                    if (error == null) {
                        TLRPC.TL_messages_exportedChatInvites invites = (TLRPC.TL_messages_exportedChatInvites) response;

                        if (revoked) {
                            for (int i = 0; i < invites.invites.size(); i++) {
                                TLRPC.TL_chatInviteExported in = (TLRPC.TL_chatInviteExported) invites.invites.get(i);
                                fixDate(in);
                                this.revokedInvites.add(in);
                            }
                        } else {
                            if (adminId != getAccountInstance().getUserConfig().clientUserId && this.invites.size() == 0 && invites.invites.size() > 0) {
                                invite = (TLRPC.TL_chatInviteExported) invites.invites.get(0);
                                invites.invites.remove(0);
                            }
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
                        if (invites.invites.size() > 0 && isOpened) {
                            if (recyclerItemsEnterAnimator != null && !isPaused) {
                                recyclerItemsEnterAnimator.showItemsAnimated(oldRowsCount + 1);
                            }
                        } else {
                            updateByDiffUtils = true;
                        }
                        if (info != null && !revoked) {
                            info.invitesCount = invites.count;
                            getMessagesStorage().saveChatLinksCount(currentChatId, info.invitesCount);
                        }
                    } else {
                        hasMore = false;
                    }

                    boolean loadNext = false;
                    if (!hasMore && !loadRevoked && adminId == getAccountInstance().getUserConfig().clientUserId) {
                        hasMore = true;
                        loadAdmins = true;
                        loadNext = true;
                    } else if (!hasMore && !loadRevoked) {
                        hasMore = true;
                        loadRevoked = true;
                        loadNext = true;
                    }

                    if (!hasMore || (invites.size() + revokedInvites.size() + admins.size()) >= 5) {
                        resumeDelayedFragmentAnimation();
                    }

                    if (loadNext) {
                        loadLinks(false);
                    }

                    if (updateByDiffUtils && listViewAdapter != null && listView.getChildCount() > 0) {
                        updateRecyclerViewAnimated(callback);
                    } else {
                        updateRows(true);
                    }
                }));
            });
            getConnectionsManager().bindRequestToGuid(reqId, getClassGuid());
        }
        if (notify) {
            updateRows(true);
        }
    }

    private void updateRows(boolean notify) {
        currentChat = MessagesController.getInstance(currentAccount).getChat(currentChatId);
        if (currentChat == null) {
            return;
        }

        creatorRow = -1;
        creatorDividerRow = -1;
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
        helpRow = -1;
        createNewLinkRow = -1;
        adminsEndRow = -1;
        adminsStartRow = -1;
        adminsDividerRow = -1;
        adminsHeaderRow = -1;
        linksHeaderRow = -1;
        dividerRow = -1;

        rowCount = 0;

        boolean otherAdmin = adminId != getAccountInstance().getUserConfig().clientUserId;
        if (otherAdmin) {
            creatorRow = rowCount++;
            creatorDividerRow = rowCount++;
        } else {
            helpRow = rowCount++;
        }

        permanentLinkHeaderRow = rowCount++;
        permanentLinkRow = rowCount++;

        if (!otherAdmin) {
            dividerRow = rowCount++;
            createNewLinkRow = rowCount++;
        } else if (!invites.isEmpty()) {
            dividerRow = rowCount++;
            linksHeaderRow = rowCount++;
        }

        if (!invites.isEmpty()) {
            linksStartRow = rowCount;
            rowCount += invites.size();
            linksEndRow = rowCount;
        }

        if (!otherAdmin && invites.isEmpty() && createNewLinkRow >= 0 && (!linksLoading || loadAdmins || loadRevoked)) {
            createLinkHelpRow = rowCount++;
        }

        if (!otherAdmin && admins.size() > 0) {
            if ((!invites.isEmpty() || createNewLinkRow >= 0) && createLinkHelpRow == -1) {
                adminsDividerRow = rowCount++;
            }
            adminsHeaderRow = rowCount++;
            adminsStartRow = rowCount;
            rowCount += admins.size();
            adminsEndRow = rowCount;
        }

        if (!revokedInvites.isEmpty()) {
            if (adminsStartRow >= 0) {
                revokedDivider = rowCount++;
            } else if ((!invites.isEmpty() || createNewLinkRow >= 0) && createLinkHelpRow == -1) {
                revokedDivider = rowCount++;
            } else if (otherAdmin && linksStartRow == -1) {
                revokedDivider = rowCount++;
            }
            revokedHeader = rowCount++;
            revokedLinksStartRow = rowCount;
            rowCount += revokedInvites.size();
            revokedLinksEndRow = rowCount;
            revokeAllDivider = rowCount++;
            revokeAllRow = rowCount++;
        }

        if (!loadAdmins && !loadRevoked && (linksLoading || hasMore) && !otherAdmin) {
            linksLoadingRow = rowCount++;
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

        fragmentView = new FrameLayout(context) {
            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                AndroidUtilities.runOnUIThread(updateTimerRunnable, 500);
            }

            @Override
            protected void onDetachedFromWindow() {
                super.onDetachedFromWindow();
                AndroidUtilities.cancelRunOnUIThread(updateTimerRunnable);
            }
        };
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView.setTag(Theme.key_windowBackgroundGray);
        FrameLayout frameLayout = (FrameLayout) fragmentView;


        listView = new RecyclerListView(context);
        LinearLayoutManager layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }
        };
        listView.setLayoutManager(layoutManager);
        listView.setAdapter(listViewAdapter = new ListAdapter(context));
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (hasMore && !linksLoading) {
                    int lastPosition = layoutManager.findLastVisibleItemPosition();
                    if (rowCount - lastPosition < 10) {
                        loadLinks(true);
                    }
                }
            }
        });
        recyclerItemsEnterAnimator = new RecyclerItemsEnterAnimator(listView, false);
        DefaultItemAnimator defaultItemAnimator = new DefaultItemAnimator();
        defaultItemAnimator.setDelayAnimations(false);
        defaultItemAnimator.setSupportsChangeAnimations(false);
        listView.setItemAnimator(defaultItemAnimator);
        listView.setVerticalScrollbarPosition(LocaleController.isRTL ? RecyclerListView.SCROLLBAR_POSITION_LEFT : RecyclerListView.SCROLLBAR_POSITION_RIGHT);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView.setOnItemClickListener((view, position) -> {
            if (position == creatorRow) {
                TLRPC.User user = users.get(invite.admin_id);
                if (user != null) {
                    Bundle bundle = new Bundle();
                    bundle.putLong("user_id", user.id);
                    MessagesController.getInstance(UserConfig.selectedAccount).putUser(user, false);
                    ProfileActivity profileActivity = new ProfileActivity(bundle);
                    presentFragment(profileActivity);
                }
            } else if (position == createNewLinkRow) {
                LinkEditActivity linkEditActivity = new LinkEditActivity(LinkEditActivity.CREATE_TYPE, currentChatId);
                linkEditActivity.setCallback(linkEditActivityCallback);
                presentFragment(linkEditActivity);
            } else if (position >= linksStartRow && position < linksEndRow) {
                TLRPC.TL_chatInviteExported invite = invites.get(position - linksStartRow);
                inviteLinkBottomSheet = new InviteLinkBottomSheet(context, invite, info, users, this, currentChatId, false, isChannel);
                inviteLinkBottomSheet.setCanEdit(canEdit);
                inviteLinkBottomSheet.show();
            } else if (position >= revokedLinksStartRow && position < revokedLinksEndRow) {
                TLRPC.TL_chatInviteExported invite = revokedInvites.get(position - revokedLinksStartRow);
                inviteLinkBottomSheet = new InviteLinkBottomSheet(context, invite, info, users, this, currentChatId, false, isChannel);
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
                    if (adminId == getUserConfig().getClientUserId()) {
                        req.admin_id = getMessagesController().getInputUser(getUserConfig().getCurrentUser());
                    } else {
                        req.admin_id = getMessagesController().getInputUser(adminId);
                    }
                    deletingRevokedLinks = true;
                    getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                        deletingRevokedLinks = false;
                        if (error == null) {
                            DiffCallback callback = saveListState();
                            revokedInvites.clear();
                            updateRecyclerViewAnimated(callback);
                        }
                    }));
                });
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                showDialog(builder.create());
            } else if (position >= adminsStartRow && position < adminsEndRow) {
                int p = position - adminsStartRow;
                TLRPC.TL_chatAdminWithInvites admin = admins.get(p);
                if (users.containsKey(admin.admin_id)) {
                    getMessagesController().putUser(users.get(admin.admin_id), false);
                }
                ManageLinksActivity fragment = new ManageLinksActivity(currentChatId, admin.admin_id, admin.invites_count);
                fragment.setInfo(info, null);
                presentFragment(fragment);
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

        isPublic = ChatObject.isPublic(currentChat);
        loadLinks(true);
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
            messageTextView.setText(isChannel ? LocaleController.getString("PrimaryLinkHelpChannel", R.string.PrimaryLinkHelpChannel) : LocaleController.getString("PrimaryLinkHelp", R.string.PrimaryLinkHelp));

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
            if (creatorRow == position) {
                return true;
            } else if (createNewLinkRow == position) {
                return true;
            } else if (position >= linksStartRow && position < linksEndRow) {
                return true;
            } else if (position >= revokedLinksStartRow && position < revokedLinksEndRow) {
                return true;
            } else if (position == revokeAllRow) {
                return true;
            } else if (position >= adminsStartRow && position < adminsEndRow) {
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
                    view.setBackgroundDrawable(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundWhite));
                    break;
                case 1:
                    view = new HeaderCell(mContext, 23);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 2:
                    LinkActionView linkActionView = new LinkActionView(mContext, ManageLinksActivity.this, null, currentChatId, true, isChannel);
                    linkActionView.setPermanent(true);
                    linkActionView.setDelegate(new LinkActionView.Delegate() {
                        @Override
                        public void revokeLink() {
                            revokePermanent();
                        }

                        @Override
                        public void showUsersForPermanentLink() {
                            inviteLinkBottomSheet = new InviteLinkBottomSheet(linkActionView.getContext(), invite, info, users, ManageLinksActivity.this, currentChatId, true, isChannel);
                            inviteLinkBottomSheet.show();
                        }
                    });
                    view = linkActionView;
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 3:
                    view = new CreationTextCell(mContext);
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
                    view.setBackground(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    break;
                case 8:
                    TextSettingsCell revokeAll = new TextSettingsCell(mContext);
                    revokeAll.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    revokeAll.setText(LocaleController.getString("DeleteAllRevokedLinks", R.string.DeleteAllRevokedLinks), false);
                    revokeAll.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
                    view = revokeAll;
                    break;
                case 9:
                    TextInfoPrivacyCell cell = new TextInfoPrivacyCell(mContext);
                    cell.setText(LocaleController.getString("CreateNewLinkHelp", R.string.CreateNewLinkHelp));
                    cell.setBackground(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    view = cell;
                    break;
                case 10:
                    ManageChatUserCell userCell = new ManageChatUserCell(mContext, 8, 6, false);
                    userCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    view = userCell;
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
                    linkActionView.setCanEdit(adminId == getAccountInstance().getUserConfig().clientUserId);
                    if (isPublic && adminId == getAccountInstance().getUserConfig().clientUserId) {
                        if (info != null) {
                            linkActionView.setLink("https://t.me/" + ChatObject.getPublicUsername(currentChat));
                            linkActionView.setUsers(0, null);
                            linkActionView.hideRevokeOption(true);
                        }
                    } else {
                        linkActionView.hideRevokeOption(!canEdit);
                        if (invite != null) {
                            TLRPC.TL_chatInviteExported inviteExported = invite;
                            linkActionView.setLink(inviteExported.link);
                            linkActionView.loadUsers(inviteExported, currentChatId);
                        } else {
                            linkActionView.setLink(null);
                            linkActionView.loadUsers(null, currentChatId);
                        }
                    }
                    break;
                case 1:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == permanentLinkHeaderRow) {
                        if (isPublic && adminId == getAccountInstance().getUserConfig().clientUserId) {
                            headerCell.setText(LocaleController.getString("PublicLink", R.string.PublicLink));
                        } else if (adminId == getAccountInstance().getUserConfig().clientUserId) {
                            headerCell.setText(LocaleController.getString("ChannelInviteLinkTitle", R.string.ChannelInviteLinkTitle));
                        } else {
                            headerCell.setText(LocaleController.getString("PermanentLinkForThisAdmin", R.string.PermanentLinkForThisAdmin));
                        }
                    } else if (position == revokedHeader) {
                        headerCell.setText(LocaleController.getString("RevokedLinks", R.string.RevokedLinks));
                    } else if (position == linksHeaderRow) {
                        headerCell.setText(LocaleController.getString("LinksCreatedByThisAdmin", R.string.LinksCreatedByThisAdmin));
                    } else if (position == adminsHeaderRow) {
                        headerCell.setText(LocaleController.getString("LinksCreatedByOtherAdmins", R.string.LinksCreatedByOtherAdmins));
                    }
                    break;
                case 3:
                    CreationTextCell textCell = (CreationTextCell) holder.itemView;
                    Drawable drawable1 = mContext.getResources().getDrawable(R.drawable.poll_add_circle);
                    Drawable drawable2 = mContext.getResources().getDrawable(R.drawable.poll_add_plus);
                    drawable1.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_switchTrackChecked), PorterDuff.Mode.MULTIPLY));
                    drawable2.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_checkboxCheck), PorterDuff.Mode.MULTIPLY));
                    CombinedDrawable combinedDrawable = new CombinedDrawable(drawable1, drawable2);

                    textCell.setTextAndIcon(LocaleController.getString("CreateNewLink", R.string.CreateNewLink), combinedDrawable, !invites.isEmpty());
                    break;
                case 5:
                    TLRPC.TL_chatInviteExported invite;
                    boolean drawDivider = true;
                    if (position >= linksStartRow && position < linksEndRow) {
                        invite = invites.get(position - linksStartRow);
                        if (position == linksEndRow - 1) {
                            drawDivider = false;
                        }
                    } else {
                        invite = revokedInvites.get(position - revokedLinksStartRow);
                        if (position == revokedLinksEndRow - 1) {
                            drawDivider = false;
                        }
                    }
                    LinkCell cell = (LinkCell) holder.itemView;
                    cell.setLink(invite, position - linksStartRow);
                    cell.drawDivider = drawDivider;
                    break;
                case 10:
                    ManageChatUserCell userCell = (ManageChatUserCell) holder.itemView;
                    TLRPC.User user;
                    int count;
                    drawDivider = true;
                    if (position == creatorRow) {
                        user = getMessagesController().getUser(adminId);
                        count = invitesCount;
                        drawDivider = false;
                    } else {
                        int p = position - adminsStartRow;
                        TLRPC.TL_chatAdminWithInvites admin = admins.get(p);
                        user = users.get(admin.admin_id);
                        count = admin.invites_count;
                        if (position == adminsEndRow - 1) {
                            drawDivider = false;
                        }
                    }

                    if (user != null) {
                        userCell.setData(user, ContactsController.formatName(user.first_name, user.last_name), LocaleController.formatPluralString("InviteLinkCount", count), drawDivider);
                    }
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
            } else if (position == permanentLinkHeaderRow || position == revokedHeader || position == adminsHeaderRow || position == linksHeaderRow) {
                return 1;
            } else if (position == permanentLinkRow) {
                return 2;
            } else if (position == createNewLinkRow) {
                return 3;
            } else if (position == dividerRow || position == revokedDivider || position == revokeAllDivider || position == creatorDividerRow || position == adminsDividerRow) {
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
            } else if (position == creatorRow || (position >= adminsStartRow && position < adminsEndRow)) {
                return 10;
            }
            return 1;
        }
    }

    private void revokePermanent() {
        if (adminId == getAccountInstance().getUserConfig().clientUserId) {
            TLRPC.TL_messages_exportChatInvite req = new TLRPC.TL_messages_exportChatInvite();
            req.peer = getMessagesController().getInputPeer(-currentChatId);
            req.legacy_revoke_permanent = true;
            TLRPC.TL_chatInviteExported oldInvite = invite;
            invite = null;
            info.exported_invite = null;
            final int reqId = getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (error == null) {
                    invite = (TLRPC.TL_chatInviteExported) response;
                    if (info != null) {
                        info.exported_invite = invite;
                    }

                    if (getParentActivity() == null) {
                        return;
                    }

                    oldInvite.revoked = true;
                    DiffCallback callback = saveListState();
                    revokedInvites.add(0, oldInvite);
                    updateRecyclerViewAnimated(callback);
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.linkbroken, LocaleController.getString("InviteRevokedHint", R.string.InviteRevokedHint)).show();
                }

            }));
            AndroidUtilities.updateVisibleRows(listView);
            getConnectionsManager().bindRequestToGuid(reqId, classGuid);
        } else {
            revokeLink(invite);
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
        boolean drawDivider;


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
            optionsView.setColorFilter(Theme.getColor(Theme.key_stickers_menu));
            optionsView.setOnClickListener(view -> {
                if (invite == null) {
                    return;
                }
                ArrayList<String> items = new ArrayList<>();
                ArrayList<Integer> icons = new ArrayList<>();
                final ArrayList<Integer> actions = new ArrayList<>();

                boolean redLastItem = false;
                if (invite.revoked) {
                    items.add(LocaleController.getString("Delete", R.string.Delete));
                    icons.add(R.drawable.msg_delete);
                    actions.add(4);
                    redLastItem = true;
                } else {
                    items.add(LocaleController.getString("CopyLink", R.string.CopyLink));
                    icons.add(R.drawable.msg_copy);
                    actions.add(0);

                    items.add(LocaleController.getString("ShareLink", R.string.ShareLink));
                    icons.add(R.drawable.msg_share);
                    actions.add(1);

                    if (!invite.permanent && canEdit) {
                        items.add(LocaleController.getString("EditLink", R.string.EditLink));
                        icons.add(R.drawable.msg_edit);
                        actions.add(2);
                    }

                    if (canEdit) {
                        items.add(LocaleController.getString("RevokeLink", R.string.RevokeLink));
                        icons.add(R.drawable.msg_delete);
                        actions.add(3);
                        redLastItem = true;
                    }
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
                            builder2.setPositiveButton(LocaleController.getString("RevokeButton", R.string.RevokeButton), (dialogInterface2, i2) -> revokeLink(inviteFinal));
                            builder2.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                            showDialog(builder2.create());
                            break;
                        case 4:
                            inviteFinal = invite;
                            builder2 = new AlertDialog.Builder(getParentActivity());
                            builder2.setTitle(LocaleController.getString("DeleteLink", R.string.DeleteLink));
                            builder2.setMessage(LocaleController.getString("DeleteLinkHelp", R.string.DeleteLinkHelp));
                            builder2.setPositiveButton(LocaleController.getString("Delete", R.string.Delete), (dialogInterface2, i2) -> deleteLink(inviteFinal));
                            builder2.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                            showDialog(builder2.create());
                            break;
                    }
                });
                builder.setTitle(LocaleController.getString("InviteLink", R.string.InviteLink));
                AlertDialog alert = builder.create();
                builder.show();
                if (redLastItem) {
                    alert.setItemColor(items.size() - 1, Theme.getColor(Theme.key_text_RedBold), Theme.getColor(Theme.key_text_RedRegular));
                }
            });
            optionsView.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 1));
            addView(optionsView, LayoutHelper.createFrame(40, 48, Gravity.RIGHT | Gravity.CENTER_VERTICAL));


            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            setWillNotDraw(false);
        }
        boolean timerRunning;

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
            float timeProgress = 1f;
            if (invite.expired || invite.revoked) {
                drawState = invite.revoked ? LINK_STATE_GRAY : LINK_STATE_RED;
            } else if (invite.expire_date > 0 || invite.usage_limit > 0) {
                float usageProgress = 1f;
                if (invite.expire_date > 0) {
                    long currentTime = System.currentTimeMillis() + timeDif * 1000L;
                    long expireTime = invite.expire_date * 1000L;
                    long date = (invite.start_date <= 0 ? invite.date : invite.start_date) * 1000L;
                    long from = currentTime - date;
                    long to = expireTime - date;
                    timeProgress = (1f - from / (float) to);
                }
                if (invite.usage_limit > 0) {
                    usageProgress = (invite.usage_limit - invite.usage) / (float) invite.usage_limit;
                }
                progress = Math.min(timeProgress, usageProgress);
                if (progress <= 0) {
                    invite.expired = true;
                    drawState = LINK_STATE_RED;
                    AndroidUtilities.updateVisibleRows(listView);
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
                    timeProgress = lastDrawExpringProgress;
                }

                paint2.setColor(color);
                rectF.set(cX - AndroidUtilities.dp(20), cY - AndroidUtilities.dp(20), cX + AndroidUtilities.dp(20), cY + AndroidUtilities.dp(20));

                if (animateToStateProgress != 1f && (!hasProgress(animateFromState) || animateHideExpiring)) {
                    canvas.save();
                    float a = (animateHideExpiring ? (1f - animateToStateProgress) : animateToStateProgress);
                    float s = (float) (0.7 + 0.3f * a);
                    canvas.scale(s, s, rectF.centerX(), rectF.centerY());
                    canvas.drawArc(rectF, -90, -timeProgress * 360, false, paint2);
                    timerParticles.draw(canvas, paint2, rectF, -timeProgress * 360, a);
                    canvas.restore();
                } else {
                    canvas.drawArc(rectF, -90, -timeProgress * 360, false, paint2);
                    timerParticles.draw(canvas, paint2, rectF, -timeProgress * 360, 1f);
                }
                if (!isPaused) {
                    invalidate();
                }
                lastDrawExpringProgress = timeProgress;
            }

            if (invite.revoked) {
                linkIconRevoked.setBounds(cX - AndroidUtilities.dp(12), cY - AndroidUtilities.dp(12), cX + AndroidUtilities.dp(12), cY + AndroidUtilities.dp(12));
                linkIconRevoked.draw(canvas);
            } else {
                linkIcon.setBounds(cX - AndroidUtilities.dp(12), cY - AndroidUtilities.dp(12), cX + AndroidUtilities.dp(12), cY + AndroidUtilities.dp(12));
                linkIcon.draw(canvas);
            }

            if (drawDivider) {
                canvas.drawLine(AndroidUtilities.dp(70), getMeasuredHeight() - 1, getMeasuredWidth() + AndroidUtilities.dp(23), getMeasuredHeight(), Theme.dividerPaint);
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
                    float p = progress / 0.5f;
                    return ColorUtils.blendARGB(Theme.getColor(Theme.key_chat_attachPollBackground), Theme.getColor(Theme.key_chat_attachAudioBackground), (1f - p));
                }
            } else if (state == LINK_STATE_YELLOW) {
                return Theme.getColor(Theme.key_chat_attachPollBackground);
            } else if (state == LINK_STATE_GRAY) {
                return Theme.getColor(Theme.key_chats_unreadCounterMuted);
            } else {
                return Theme.getColor(Theme.key_featuredStickers_addButton);
            }
        }

        public void setLink(TLRPC.TL_chatInviteExported invite, int position) {
            timerRunning = false;
            if (this.invite == null || invite == null || !this.invite.link.equals(invite.link)) {
                lastDrawingState = -1;
                animateToStateProgress = 1f;
            }
            this.invite = invite;
            this.position = position;

            if (invite == null) {
                return;
            }

            if (!TextUtils.isEmpty(invite.title)) {
                SpannableStringBuilder builder = new SpannableStringBuilder(invite.title);
                Emoji.replaceEmoji(builder, titleView.getPaint().getFontMetricsInt(), (int) titleView.getPaint().getTextSize(), false);
                titleView.setText(builder);
            } else if (invite.link.startsWith("https://t.me/+")) {
                titleView.setText(invite.link.substring("https://t.me/+".length()));
            } else if (invite.link.startsWith("https://t.me/joinchat/")) {
                titleView.setText(invite.link.substring("https://t.me/joinchat/".length()));
            } else if (invite.link.startsWith("https://")) {
                titleView.setText(invite.link.substring("https://".length()));
            } else {
                titleView.setText(invite.link);
            }

            String joinedString = "";
            if (invite.usage == 0 && invite.usage_limit == 0 && invite.requested == 0) {
                joinedString = LocaleController.getString("NoOneJoinedYet", R.string.NoOneJoinedYet);
            } else {
                if (invite.usage_limit > 0 && invite.usage == 0 && !invite.expired && !invite.revoked) {
                    joinedString = LocaleController.formatPluralString("CanJoin", invite.usage_limit);
                } else if (invite.usage_limit > 0 && invite.expired && invite.revoked) {
                    joinedString = LocaleController.formatPluralString("PeopleJoined", invite.usage) + ", " + LocaleController.formatPluralString("PeopleJoinedRemaining", (invite.usage_limit - invite.usage));
                } else {
                    if (invite.usage > 0) {
                        joinedString = LocaleController.formatPluralString("PeopleJoined", invite.usage);
                    }
                    if (invite.requested > 0) {
                        if (invite.usage > 0) {
                            joinedString = joinedString + ", ";
                        }
                        joinedString = joinedString + LocaleController.formatPluralString("JoinRequests", invite.requested);
                    }
                }
            }
            if (invite.permanent && !invite.revoked) {
                SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(joinedString);
                DotDividerSpan dotDividerSpan = new DotDividerSpan();
                dotDividerSpan.setTopPadding(AndroidUtilities.dp(1.5f));
                spannableStringBuilder.append("  .  ").setSpan(dotDividerSpan, spannableStringBuilder.length() - 3, spannableStringBuilder.length() - 2, 0);
                spannableStringBuilder.append(LocaleController.getString("Permanent", R.string.Permanent));
                subtitleView.setText(spannableStringBuilder);
            } else if (invite.expired || invite.revoked) {
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
            } else if (invite.expire_date > 0) {
                SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(joinedString);
                DotDividerSpan dotDividerSpan = new DotDividerSpan();
                dotDividerSpan.setTopPadding(AndroidUtilities.dp(1.5f));
                spannableStringBuilder.append("  .  ").setSpan(dotDividerSpan, spannableStringBuilder.length() - 3, spannableStringBuilder.length() - 2, 0);

                long currentTime = System.currentTimeMillis() + timeDif * 1000L;
                long expireTime = invite.expire_date * 1000L;
                long timeLeft = expireTime - currentTime;
                if (timeLeft < 0) {
                    timeLeft = 0;
                }
                if (timeLeft > 86400000L) {
                    spannableStringBuilder.append(LocaleController.formatPluralString("DaysLeft", (int) (timeLeft / 86400000L)));
                } else {
                    int s = (int) ((timeLeft / 1000) % 60);
                    int m = (int) ((timeLeft / 1000 / 60) % 60);
                    int h = (int) ((timeLeft / 1000 / 60 / 60));
                    spannableStringBuilder.append(String.format(Locale.ENGLISH, "%02d", h)).append(String.format(Locale.ENGLISH, ":%02d", m)).append(String.format(Locale.ENGLISH, ":%02d", s));
                    timerRunning = true;
                }
                subtitleView.setText(spannableStringBuilder);
            } else {
                subtitleView.setText(joinedString);
            }
        }
    }

    public void deleteLink(TLRPC.TL_chatInviteExported invite) {
        TLRPC.TL_messages_deleteExportedChatInvite req = new TLRPC.TL_messages_deleteExportedChatInvite();
        req.link = invite.link;
        req.peer = getMessagesController().getInputPeer(-currentChatId);
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error == null) {
                linkEditActivityCallback.onLinkRemoved(invite);
            }
        }));
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
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error == null) {
                if (response instanceof TLRPC.TL_messages_exportedChatInviteReplaced) {
                    TLRPC.TL_messages_exportedChatInviteReplaced replaced = (TLRPC.TL_messages_exportedChatInviteReplaced) response;
                    if (!isPublic) {
                        ManageLinksActivity.this.invite = (TLRPC.TL_chatInviteExported) replaced.new_invite;
                    }

                    invite.revoked = true;
                    DiffCallback callback = saveListState();
                    if (isPublic && adminId == getAccountInstance().getUserConfig().getClientUserId()) {
                        invites.remove(invite);
                        invites.add(0, (TLRPC.TL_chatInviteExported) replaced.new_invite);
                    } else if (this.invite != null) {
                        this.invite = (TLRPC.TL_chatInviteExported) replaced.new_invite;
                    }
                    revokedInvites.add(0, invite);
                    updateRecyclerViewAnimated(callback);
                } else {
                    linkEditActivityCallback.onLinkEdited(invite, response);
                    if (info != null) {
                        info.invitesCount--;
                        if (info.invitesCount < 0) {
                            info.invitesCount = 0;
                        }
                        getMessagesStorage().saveChatLinksCount(currentChatId, info.invitesCount);
                    }
                }
                if (getParentActivity() != null) {
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.linkbroken, LocaleController.getString("InviteRevokedHint", R.string.InviteRevokedHint)).show();
                }
            }
        }));
    }

    private final LinkEditActivity.Callback linkEditActivityCallback = new LinkEditActivity.Callback() {
        @Override
        public void onLinkCreated(TLObject response) {
            if (response instanceof TLRPC.TL_chatInviteExported) {
                AndroidUtilities.runOnUIThread(() -> {
                    DiffCallback callback = saveListState();
                    invites.add(0, (TLRPC.TL_chatInviteExported) response);
                    if (info != null) {
                        info.invitesCount++;
                        getMessagesStorage().saveChatLinksCount(currentChatId, info.invitesCount);
                    }
                    updateRecyclerViewAnimated(callback);
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
                            updateRecyclerViewAnimated(callback);
                        } else {
                            invites.set(i, edited);
                            updateRows(true);
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
                    updateRecyclerViewAnimated(callback);
                    return;
                }
            }
        }

        @Override
        public void revokeLink(TLRPC.TL_chatInviteExported inviteFinal) {
            ManageLinksActivity.this.revokeLink(inviteFinal);
        }
    };

    private void updateRecyclerViewAnimated(DiffCallback callback) {
        if (isPaused || listViewAdapter == null || listView == null) {
            updateRows(true);
            return;
        }
        updateRows(false);
        callback.fillPositions(callback.newPositionToItem);
        DiffUtil.calculateDiff(callback).dispatchUpdatesTo(listViewAdapter);
        AndroidUtilities.updateVisibleRows(listView);
    }


    private class DiffCallback extends DiffUtil.Callback {

        int oldRowCount;
        int oldLinksStartRow;
        int oldLinksEndRow;
        int oldRevokedLinksStartRow;
        int oldRevokedLinksEndRow;
        int oldAdminsStartRow;
        int oldAdminsEndRow;

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
            if (oldItemPosition >= oldAdminsStartRow && oldItemPosition < oldAdminsEndRow && newItemPosition >= adminsStartRow && newItemPosition < adminsEndRow) {
                return (oldItemPosition - oldAdminsStartRow) == (newItemPosition - adminsStartRow);
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
            put(++pointer, revokeAllRow, sparseIntArray);
            put(++pointer, createLinkHelpRow, sparseIntArray);
            put(++pointer, creatorRow, sparseIntArray);
            put(++pointer, creatorDividerRow, sparseIntArray);
            put(++pointer, adminsHeaderRow, sparseIntArray);
            put(++pointer, linksHeaderRow, sparseIntArray);
            put(++pointer, linksLoadingRow, sparseIntArray);
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
        callback.oldAdminsStartRow = adminsStartRow;
        callback.oldAdminsEndRow = adminsEndRow;
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

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{HeaderCell.class, CreationTextCell.class, LinkActionView.class, LinkCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
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
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{ManageChatTextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_chats_unreadCounterMuted));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{ManageChatTextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueButton));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{ManageChatTextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueIcon));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{CreationTextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueText2));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{CreationTextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_switchTrackChecked));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{CreationTextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_checkboxCheck));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{LinkCell.class}, new String[]{"titleView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{LinkCell.class}, new String[]{"subtitleView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{LinkCell.class}, new String[]{"optionsView"}, null, null, null, Theme.key_stickers_menu));
        return themeDescriptions;
    }

    @Override
    public boolean needDelayOpenAnimation() {
        return true;
    }

    AnimationNotificationsLocker notificationsLocker = new AnimationNotificationsLocker();

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        super.onTransitionAnimationEnd(isOpen, backward);
        if (isOpen) {
            isOpened = true;
            if (backward && inviteLinkBottomSheet != null && inviteLinkBottomSheet.isNeedReopen) {
                inviteLinkBottomSheet.show();
            }
        }
        notificationsLocker.unlock();
    }

    @Override
    public void onTransitionAnimationStart(boolean isOpen, boolean backward) {
        super.onTransitionAnimationStart(isOpen, backward);
        notificationsLocker.lock();
    }
}
