package org.telegram.ui.Components.Premium;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.AdminedChannelCell;
import org.telegram.ui.Cells.GroupCreateUserCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerItemsEnterAnimator;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.PremiumPreviewFragment;

import java.util.ArrayList;
import java.util.HashSet;

public class LimitReachedBottomSheet extends BottomSheetWithRecyclerListView {

    public static final int TYPE_PIN_DIALOGS = 0;
    public static final int TYPE_PUBLIC_LINKS = 2;
    public static final int TYPE_FOLDERS = 3;
    public static final int TYPE_CHATS_IN_FOLDER = 4;
    public static final int TYPE_TO0_MANY_COMMUNITIES = 5;
    public static final int TYPE_LARGE_FILE = 6;
    public static final int TYPE_ACCOUNTS = 7;

    public static final int TYPE_CAPTION = 8;
    public static final int TYPE_GIFS = 9;
    public static final int TYPE_STICKERS = 10;

    public static final int TYPE_ADD_MEMBERS_RESTRICTED = 11;
    public static final int TYPE_FOLDER_INVITES = 12;
    public static final int TYPE_SHARED_FOLDERS = 13;

    private boolean canSendLink;
    private TLRPC.TL_webPage linkPreview;

    public static String limitTypeToServerString(int type) {
        switch (type) {
            case TYPE_PIN_DIALOGS:
                return "double_limits__dialog_pinned";
            case TYPE_TO0_MANY_COMMUNITIES:
                return "double_limits__channels";
            case TYPE_PUBLIC_LINKS:
                return "double_limits__channels_public";
            case TYPE_FOLDERS:
                return "double_limits__dialog_filters";
            case TYPE_CHATS_IN_FOLDER:
                return "double_limits__dialog_filters_chats";
            case TYPE_LARGE_FILE:
                return "double_limits__upload_max_fileparts";
            case TYPE_CAPTION:
                return "double_limits__caption_length";
            case TYPE_GIFS:
                return "double_limits__saved_gifs";
            case TYPE_STICKERS:
                return "double_limits__stickers_faved";
            case TYPE_FOLDER_INVITES:
                return "double_limits__chatlist_invites";
            case TYPE_SHARED_FOLDERS:
                return "double_limits__chatlists_joined";
        }
        return null;
    }

    final int type;
    ArrayList<TLRPC.Chat> chats = new ArrayList<>();

    int rowCount;
    int headerRow = -1;
    int dividerRow = -1;
    int chatsTitleRow = -1;
    int chatStartRow = -1;
    int chatEndRow = -1;
    int loadingRow = -1;
    int emptyViewDividerRow = -1;

    public boolean parentIsChannel;
    private int currentValue = -1;
    LimitPreviewView limitPreviewView;
    HashSet<Object> selectedChats = new HashSet<>();

    private ArrayList<TLRPC.Chat> inactiveChats = new ArrayList<>();
    private ArrayList<String> inactiveChatsSignatures = new ArrayList<>();
    private ArrayList<TLRPC.User> restrictedUsers = new ArrayList<>();

    PremiumButtonView premiumButtonView;
    public Runnable onSuccessRunnable;
    public Runnable onShowPremiumScreenRunnable;
    private boolean loading = false;
    RecyclerItemsEnterAnimator enterAnimator;
    BaseFragment parentFragment;
    View divider;
    LimitParams limitParams;
    private boolean isVeryLargeFile;
    private TLRPC.Chat fromChat;

    public LimitReachedBottomSheet(BaseFragment fragment, Context context, int type, int currentAccount) {
        super(fragment, false, hasFixedSize(type));
        fixNavigationBar();
        parentFragment = fragment;
        this.type = type;
        updateTitle();
        this.currentAccount = currentAccount;
        updateRows();
        if (type == TYPE_PUBLIC_LINKS) {
            loadAdminedChannels();
        } else if (type == TYPE_TO0_MANY_COMMUNITIES) {
            loadInactiveChannels();
        }
        updatePremiumButtonText();
    }

    @Override
    public void onViewCreated(FrameLayout containerView) {
        super.onViewCreated(containerView);
        Context context = containerView.getContext();

        premiumButtonView = new PremiumButtonView(context, true);

        if (!hasFixedSize) {
            divider = new View(context) {
                @Override
                protected void onDraw(Canvas canvas) {
                    super.onDraw(canvas);
                    if (chatEndRow - chatStartRow > 1) {
                        canvas.drawRect(0, 0, getMeasuredWidth(), 1, Theme.dividerPaint);
                    }
                }
            };
            divider.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));
            containerView.addView(divider, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 72, Gravity.BOTTOM, 0, 0, 0, 0));
        }
        containerView.addView(premiumButtonView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM, 16, 0, 16, 12));
        recyclerListView.setPadding(0, 0, 0, AndroidUtilities.dp(72));
        recyclerListView.setOnItemClickListener((view, position) -> {
            if (view instanceof AdminedChannelCell) {
                AdminedChannelCell adminedChannelCell = ((AdminedChannelCell) view);
                TLRPC.Chat chat = adminedChannelCell.getCurrentChannel();
                if (selectedChats.contains(chat)) {
                    selectedChats.remove(chat);
                } else {
                    selectedChats.add(chat);
                }
                adminedChannelCell.setChecked(selectedChats.contains(chat), true);
                updateButton();
            } else if (view instanceof GroupCreateUserCell) {
                if (!canSendLink && type == TYPE_ADD_MEMBERS_RESTRICTED) {
                    return;
                }
                GroupCreateUserCell cell = (GroupCreateUserCell) view;
                Object object = cell.getObject();
                if (selectedChats.contains(object)) {
                    selectedChats.remove(object);
                } else {
                    selectedChats.add(object);
                }
                cell.setChecked(selectedChats.contains(object), true);
                updateButton();
            }
        });
        recyclerListView.setOnItemLongClickListener((view, position) -> {
            recyclerListView.getOnItemClickListener().onItemClick(view, position);
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            return false;
        });
        premiumButtonView.buttonLayout.setOnClickListener(v -> {
            if (type == TYPE_ADD_MEMBERS_RESTRICTED) {
                return;
            }
            if (UserConfig.getInstance(currentAccount).isPremium() || MessagesController.getInstance(currentAccount).premiumLocked || isVeryLargeFile) {
                dismiss();
                return;
            }
            if (parentFragment == null) {
                return;
            }
            if (parentFragment.getVisibleDialog() != null) {
                parentFragment.getVisibleDialog().dismiss();
            }
            parentFragment.presentFragment(new PremiumPreviewFragment(limitTypeToServerString(type)));
            if (onShowPremiumScreenRunnable != null) {
                onShowPremiumScreenRunnable.run();
            }
            dismiss();
        });
        premiumButtonView.overlayTextView.setOnClickListener(v -> {
            if (type == TYPE_ADD_MEMBERS_RESTRICTED) {
                if (selectedChats.isEmpty()) {
                    dismiss();
                    return;
                }
                sendInviteMessages();
                return;
            }
            if (selectedChats.isEmpty()) {
                return;
            }
            if (type == TYPE_PUBLIC_LINKS) {
                revokeSelectedLinks();
            } else if (type == TYPE_TO0_MANY_COMMUNITIES) {
                leaveFromSelectedGroups();
            }
        });
        enterAnimator = new RecyclerItemsEnterAnimator(recyclerListView, true);
    }

    private void sendInviteMessages() {
        String link = null;
        TLRPC.ChatFull chatFull = MessagesController.getInstance(currentAccount).getChatFull(fromChat.id);
        if (chatFull == null) {
            dismiss();
            return;
        }
        if (fromChat.username != null) {
            link = "@" + fromChat.username;
        } else if (chatFull.exported_invite != null) {
            link = chatFull.exported_invite.link;
        } else {
            dismiss();
            return;
        }
        for (Object obj : selectedChats) {
            TLRPC.User user = (TLRPC.User) obj;
            SendMessagesHelper.getInstance(currentAccount).sendMessage(link, user.id, null, null, linkPreview, false, null, null, null, false, 0, null, false);
        }
        AndroidUtilities.runOnUIThread(() -> {
            BulletinFactory factory = BulletinFactory.global();
            if (factory != null)  {
                if (selectedChats.size() == 1) {
                    TLRPC.User user = (TLRPC.User) selectedChats.iterator().next();
                    factory.createSimpleBulletin(R.raw.voip_invite,
                            AndroidUtilities.replaceTags(LocaleController.formatString("InviteLinkSentSingle", R.string.InviteLinkSentSingle, ContactsController.formatName(user)))
                    ).show();
                } else {
                    factory.createSimpleBulletin(R.raw.voip_invite,
                            AndroidUtilities.replaceTags(LocaleController.formatPluralString("InviteLinkSent", selectedChats.size(), selectedChats.size()))
                    ).show();
                }
            }
        });
        dismiss();
    }

    public void updatePremiumButtonText() {
        if (UserConfig.getInstance(currentAccount).isPremium() || MessagesController.getInstance(currentAccount).premiumLocked || isVeryLargeFile) {
            premiumButtonView.buttonTextView.setText(LocaleController.getString(R.string.OK));
            premiumButtonView.hideIcon();
        } else {
            premiumButtonView.buttonTextView.setText(LocaleController.getString("IncreaseLimit", R.string.IncreaseLimit));
            if (limitParams != null) {
                if (limitParams.defaultLimit + 1 == limitParams.premiumLimit) {
                    premiumButtonView.setIcon(R.raw.addone_icon);
                } else if (
                    limitParams.defaultLimit != 0 && limitParams.premiumLimit != 0 &&
                    limitParams.premiumLimit / (float) limitParams.defaultLimit >= 1.6f &&
                    limitParams.premiumLimit / (float) limitParams.defaultLimit <= 2.5f
                ) {
                    premiumButtonView.setIcon(R.raw.double_icon);
                } else {
                    premiumButtonView.hideIcon();
                }
            } else {
                premiumButtonView.hideIcon();
            }
        }
    }

    private void leaveFromSelectedGroups() {
        TLRPC.User currentUser = MessagesController.getInstance(currentAccount).getUser(UserConfig.getInstance(currentAccount).getClientUserId());
        ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        for (Object obj : selectedChats) {
            chats.add((TLRPC.Chat) obj);
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(LocaleController.formatPluralString("LeaveCommunities", chats.size()));
        if (chats.size() == 1) {
            TLRPC.Chat channel = chats.get(0);
            builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("ChannelLeaveAlertWithName", R.string.ChannelLeaveAlertWithName, channel.title)));
        } else {
            builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("ChatsLeaveAlert", R.string.ChatsLeaveAlert)));
        }
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        builder.setPositiveButton(LocaleController.getString("RevokeButton", R.string.RevokeButton), (dialogInterface, interface2) -> {
            dismiss();
            for (int i = 0; i < chats.size(); i++) {
                TLRPC.Chat chat = chats.get(i);
                MessagesController.getInstance(currentAccount).putChat(chat, false);
                MessagesController.getInstance(currentAccount).deleteParticipantFromChat(chat.id, currentUser);
            }
        });
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
        TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
        }
    }

    private void updateButton() {
        if (type == TYPE_ADD_MEMBERS_RESTRICTED) {
            premiumButtonView.checkCounterView();
            if (!canSendLink) {
                premiumButtonView.setOverlayText(LocaleController.getString("Close", R.string.Close), true, true);
            } else if (selectedChats.size() > 0) {
                premiumButtonView.setOverlayText(LocaleController.getString("SendInviteLink", R.string.SendInviteLink), true, true);
            } else {
                premiumButtonView.setOverlayText(LocaleController.getString("ActionSkip", R.string.ActionSkip), true, true);
            }
            premiumButtonView.counterView.setCount(selectedChats.size(), true);
            premiumButtonView.invalidate();
        } else {
            if (selectedChats.size() > 0) {
                String str = null;
                if (type == TYPE_PUBLIC_LINKS) {
                    str = LocaleController.formatPluralString("RevokeLinks", selectedChats.size());
                } else if (type == TYPE_TO0_MANY_COMMUNITIES) {
                    str = LocaleController.formatPluralString("LeaveCommunities", selectedChats.size());
                }
                premiumButtonView.setOverlayText(str, true, true);
            } else {
                premiumButtonView.clearOverlayText();
            }
        }
    }

    private static boolean hasFixedSize(int type) {
        if (type == TYPE_PIN_DIALOGS || type == TYPE_FOLDERS || type == TYPE_CHATS_IN_FOLDER || type == TYPE_LARGE_FILE || type == TYPE_ACCOUNTS || type == TYPE_FOLDER_INVITES || type == TYPE_SHARED_FOLDERS) {
            return true;
        }
        return false;
    }

    @Override
    public CharSequence getTitle() {
        if (type == TYPE_ADD_MEMBERS_RESTRICTED) {
            return LocaleController.getString("ChannelInviteViaLink", R.string.ChannelInviteViaLink);
        }
        return LocaleController.getString("LimitReached", R.string.LimitReached);
    }

    @Override
    public RecyclerListView.SelectionAdapter createAdapter() {
        return new RecyclerListView.SelectionAdapter() {
            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                if (type == TYPE_ADD_MEMBERS_RESTRICTED && !canSendLink) {
                    return false;
                }
                return holder.getItemViewType() == 1 || holder.getItemViewType() == 4;
            }

            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view;
                Context context = parent.getContext();
                switch (viewType) {
                    default:
                    case 0:
                        view = new HeaderView(context);
                        break;
                    case 1:
                        view = new AdminedChannelCell(context, new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                AdminedChannelCell cell = (AdminedChannelCell) v.getParent();
                                final ArrayList<TLRPC.Chat> channels = new ArrayList<>();
                                channels.add(cell.getCurrentChannel());
                                revokeLinks(channels);
                            }
                        }, true, 9);
                        break;
                    case 2:
                        view = new ShadowSectionCell(context, 12, Theme.getColor(Theme.key_windowBackgroundGray));
                        break;
                    case 3:
                        view = new HeaderCell(context);
                        view.setPadding(0, 0, 0, AndroidUtilities.dp(8));
                        break;
                    case 4:
                        view = new GroupCreateUserCell(context, 1, 8, false);
                        break;
                    case 5:
                        FlickerLoadingView flickerLoadingView = new FlickerLoadingView(context, null);
                        flickerLoadingView.setViewType(type == TYPE_PUBLIC_LINKS ? FlickerLoadingView.LIMIT_REACHED_LINKS : FlickerLoadingView.LIMIT_REACHED_GROUPS);
                        flickerLoadingView.setIsSingleCell(true);
                        flickerLoadingView.setIgnoreHeightCheck(true);
                        flickerLoadingView.setItemsCount(10);
                        view = flickerLoadingView;
                        break;
                    case 6:
                        view = new View(getContext()) {
                            @Override
                            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(16), MeasureSpec.EXACTLY));
                            }
                        };
                        break;
                }
                view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return new RecyclerListView.Holder(view);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                if (holder.getItemViewType() == 4) {
                    GroupCreateUserCell cell = (GroupCreateUserCell) holder.itemView;
                    if (type == TYPE_TO0_MANY_COMMUNITIES) {
                        TLRPC.Chat chat = inactiveChats.get(position - chatStartRow);
                        String signature = inactiveChatsSignatures.get(position - chatStartRow);
                        cell.setObject(chat, chat.title, signature, position != chatEndRow - 1f);
                        cell.setChecked(selectedChats.contains(chat), false);
                    } else if (type == TYPE_ADD_MEMBERS_RESTRICTED) {
                        TLRPC.User user = restrictedUsers.get(position - chatStartRow);
                        String signature = LocaleController.formatUserStatus(currentAccount, user, null, null);
                        cell.setObject(user, ContactsController.formatName(user.first_name, user.last_name), signature, position != chatEndRow - 1f);
                        cell.setChecked(selectedChats.contains(user), false);
                    }
                } else if (holder.getItemViewType() == 1) {
                    TLRPC.Chat chat = chats.get(position - chatStartRow);
                    AdminedChannelCell adminedChannelCell = (AdminedChannelCell) holder.itemView;
                    TLRPC.Chat oldChat = adminedChannelCell.getCurrentChannel();
                    adminedChannelCell.setChannel(chat, false);
                    adminedChannelCell.setChecked(selectedChats.contains(chat), oldChat == chat);
                } else if (holder.getItemViewType() == 3) {
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (type == TYPE_ADD_MEMBERS_RESTRICTED) {
                        if (canSendLink) {
                            headerCell.setText(LocaleController.getString("ChannelInviteViaLink", R.string.ChannelInviteViaLink));
                        } else {
                            if (restrictedUsers.size() == 1) {
                                headerCell.setText(LocaleController.getString("ChannelInviteViaLinkRestricted2", R.string.ChannelInviteViaLinkRestricted2));
                            } else {
                                headerCell.setText(LocaleController.getString("ChannelInviteViaLinkRestricted3", R.string.ChannelInviteViaLinkRestricted3));
                            }
                        }
                    } else if (type == TYPE_PUBLIC_LINKS) {
                        headerCell.setText(LocaleController.getString("YourPublicCommunities", R.string.YourPublicCommunities));
                    } else {
                        headerCell.setText(LocaleController.getString("LastActiveCommunities", R.string.LastActiveCommunities));
                    }
                }
            }

            @Override
            public int getItemViewType(int position) {
                if (headerRow == position) {
                    return 0;
                } else if (dividerRow == position) {
                    return 2;
                } else if (chatsTitleRow == position) {
                    return 3;
                } else if (loadingRow == position) {
                    return 5;
                } else if (emptyViewDividerRow == position) {
                    return 6;
                }
                if (type == TYPE_TO0_MANY_COMMUNITIES || type == TYPE_ADD_MEMBERS_RESTRICTED) {
                    return 4;
                } else {
                    return 1;
                }
            }

            @Override
            public int getItemCount() {
                return rowCount;
            }
        };
    }

    public void setCurrentValue(int currentValue) {
        this.currentValue = currentValue;
    }

    public void setVeryLargeFile(boolean b) {
        isVeryLargeFile = b;
        updatePremiumButtonText();
    }

    public void setRestrictedUsers(TLRPC.Chat chat, ArrayList<TLRPC.User> userRestrictedPrivacy) {
        fromChat = chat;
        canSendLink = ChatObject.canUserDoAdminAction(chat, ChatObject.ACTION_INVITE);
        restrictedUsers = new ArrayList<>(userRestrictedPrivacy);
        selectedChats.clear();
        if (canSendLink) {
            selectedChats.addAll(restrictedUsers);
        }
        updateRows();
        updateButton();

        TLRPC.ChatFull chatFull = MessagesController.getInstance(currentAccount).getChatFull(fromChat.id);
        String link;
        if (fromChat.username == null && chatFull != null && chatFull.exported_invite != null) {
            link = chatFull.exported_invite.link;

            TLRPC.TL_messages_getWebPage webPagePreview = new TLRPC.TL_messages_getWebPage();
            webPagePreview.url = link;
            ConnectionsManager.getInstance(currentAccount).sendRequest(webPagePreview,(response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (response != null) {
                    if (response instanceof TLRPC.TL_webPage) {
                        linkPreview = (TLRPC.TL_webPage) response;
                    }
                }
            }));
        }

    }


    private class HeaderView extends LinearLayout {

        @SuppressLint("SetTextI18n")
        public HeaderView(Context context) {
            super(context);
            setOrientation(LinearLayout.VERTICAL);
            setPadding(AndroidUtilities.dp(6), 0, AndroidUtilities.dp(6), 0);

            limitParams = getLimitParams(type, currentAccount);
            int icon = limitParams.icon;
            String descriptionStr;
            boolean premiumLocked = MessagesController.getInstance(currentAccount).premiumLocked;
            if (type == TYPE_ADD_MEMBERS_RESTRICTED) {
                premiumLocked = true;
                if (!canSendLink) {
                    if (ChatObject.isChannelAndNotMegaGroup(fromChat)) {
                        if (restrictedUsers.size() == 1) {
                            descriptionStr = LocaleController.formatString("InviteChannelRestrictedUsers2One", R.string.InviteChannelRestrictedUsers2One, ContactsController.formatName(restrictedUsers.get(0)));
                        } else {
                            descriptionStr = LocaleController.formatPluralString("InviteChannelRestrictedUsers2", restrictedUsers.size(), restrictedUsers.size());
                        }
                    } else {
                        if (restrictedUsers.size() == 1) {
                            descriptionStr = LocaleController.formatString("InviteRestrictedUsers2One", R.string.InviteRestrictedUsers2One, ContactsController.formatName(restrictedUsers.get(0)));
                        } else {
                            descriptionStr = LocaleController.formatPluralString("InviteRestrictedUsers2", restrictedUsers.size(), restrictedUsers.size());
                        }
                    }
                } else {
                    if (ChatObject.isChannelAndNotMegaGroup(fromChat)) {
                        if (restrictedUsers.size() == 1) {
                            descriptionStr = LocaleController.formatString("InviteChannelRestrictedUsersOne", R.string.InviteChannelRestrictedUsersOne, ContactsController.formatName(restrictedUsers.get(0)));
                        } else {
                            descriptionStr = LocaleController.formatPluralString("InviteChannelRestrictedUsers", restrictedUsers.size(), restrictedUsers.size());
                        }
                    } else {
                        if (restrictedUsers.size() == 1) {
                            descriptionStr = LocaleController.formatString("InviteRestrictedUsersOne", R.string.InviteRestrictedUsersOne, ContactsController.formatName(restrictedUsers.get(0)));
                        } else {
                            descriptionStr = LocaleController.formatPluralString("InviteRestrictedUsers", restrictedUsers.size(), restrictedUsers.size());
                        }
                    }
                }
            } else {
                if (premiumLocked) {
                    descriptionStr = limitParams.descriptionStrLocked;
                } else {
                    descriptionStr = (UserConfig.getInstance(currentAccount).isPremium() || isVeryLargeFile) ? limitParams.descriptionStrPremium : limitParams.descriptionStr;
                }
            }
            int defaultLimit = limitParams.defaultLimit;
            int premiumLimit = limitParams.premiumLimit;
            int currentValue = LimitReachedBottomSheet.this.currentValue;
            float position = 0.5f;

            if (type == TYPE_FOLDERS) {
                currentValue = MessagesController.getInstance(currentAccount).dialogFilters.size() - 1;
            } else if (type == TYPE_ACCOUNTS) {
                currentValue = UserConfig.getActivatedAccountsCount();
            }
            if (type == TYPE_PIN_DIALOGS) {
                int pinnedCount = 0;
                ArrayList<TLRPC.Dialog> dialogs = MessagesController.getInstance(currentAccount).getDialogs(0);
                for (int a = 0, N = dialogs.size(); a < N; a++) {
                    TLRPC.Dialog dialog = dialogs.get(a);
                    if (dialog instanceof TLRPC.TL_dialogFolder) {
                        continue;
                    }
                    if (dialog.pinned) {
                        pinnedCount++;
                    }
                }
                currentValue = pinnedCount;
            }

            if (UserConfig.getInstance(currentAccount).isPremium() || isVeryLargeFile) {
                currentValue = premiumLimit;
                position = 1f;
            } else {
                if (currentValue < 0) {
                    currentValue = defaultLimit;
                }
                if (type == TYPE_ACCOUNTS) {
                    if (currentValue > defaultLimit) {
                        position = (float) (currentValue - defaultLimit) / (float) (premiumLimit - defaultLimit);
                    }
                } else {
                    position = currentValue / (float) premiumLimit;
                }
            }

            limitPreviewView = new LimitPreviewView(context, icon, currentValue, premiumLimit, position);
            limitPreviewView.setBagePosition(position);
            limitPreviewView.setType(type);
            limitPreviewView.defaultCount.setVisibility(View.GONE);
            if (premiumLocked) {
                limitPreviewView.setPremiumLocked();
            } else {
                if (UserConfig.getInstance(currentAccount).isPremium() || isVeryLargeFile) {
                    limitPreviewView.premiumCount.setVisibility(View.GONE);
                    if (type == TYPE_LARGE_FILE) {
                        limitPreviewView.defaultCount.setText("2 GB");
                    } else {
                        limitPreviewView.defaultCount.setText(Integer.toString(defaultLimit));
                    }
                    limitPreviewView.defaultCount.setVisibility(View.VISIBLE);
                }
            }

            if (type == TYPE_PUBLIC_LINKS || type == TYPE_TO0_MANY_COMMUNITIES) {
                limitPreviewView.setDelayedAnimation();
            }


            addView(limitPreviewView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0, 0, 0));

            TextView title = new TextView(context);
            title.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            if (type == TYPE_ADD_MEMBERS_RESTRICTED) {
                if (canSendLink) {
                    title.setText(LocaleController.getString("ChannelInviteViaLink", R.string.ChannelInviteViaLink));
                } else {
                    title.setText(LocaleController.getString("ChannelInviteViaLinkRestricted", R.string.ChannelInviteViaLinkRestricted));
                }
            } else if (type == TYPE_LARGE_FILE) {
                title.setText(LocaleController.getString("FileTooLarge", R.string.FileTooLarge));
            } else {
                title.setText(LocaleController.getString("LimitReached", R.string.LimitReached));
            }
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, premiumLocked ? 8 : 22, 0, 10));

            TextView description = new TextView(context);
            description.setText(AndroidUtilities.replaceTags(descriptionStr));
            description.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            description.setGravity(Gravity.CENTER_HORIZONTAL);
            description.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            addView(description, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 24, 0, 24, 24));

            updatePremiumButtonText();
        }
    }

    private static LimitParams getLimitParams(int type, int currentAccount) {
        LimitParams limitParams = new LimitParams();
        if (type == TYPE_PIN_DIALOGS) {
            limitParams.defaultLimit = MessagesController.getInstance(currentAccount).dialogFiltersPinnedLimitDefault;
            limitParams.premiumLimit = MessagesController.getInstance(currentAccount).dialogFiltersPinnedLimitPremium;
            limitParams.icon = R.drawable.msg_limit_pin;
            limitParams.descriptionStr = LocaleController.formatString("LimitReachedPinDialogs", R.string.LimitReachedPinDialogs, limitParams.defaultLimit, limitParams.premiumLimit);
            limitParams.descriptionStrPremium = LocaleController.formatString("LimitReachedPinDialogsPremium", R.string.LimitReachedPinDialogsPremium, limitParams.premiumLimit);
            limitParams.descriptionStrLocked = LocaleController.formatString("LimitReachedPinDialogsLocked", R.string.LimitReachedPinDialogsLocked, limitParams.defaultLimit);
        } else if (type == TYPE_PUBLIC_LINKS) {
            limitParams.defaultLimit = MessagesController.getInstance(currentAccount).publicLinksLimitDefault;
            limitParams.premiumLimit = MessagesController.getInstance(currentAccount).publicLinksLimitPremium;
            limitParams.icon = R.drawable.msg_limit_links;
            limitParams.descriptionStr = LocaleController.formatString("LimitReachedPublicLinks", R.string.LimitReachedPublicLinks, limitParams.defaultLimit, limitParams.premiumLimit);
            limitParams.descriptionStrPremium = LocaleController.formatString("LimitReachedPublicLinksPremium", R.string.LimitReachedPublicLinksPremium, limitParams.premiumLimit);
            limitParams.descriptionStrLocked = LocaleController.formatString("LimitReachedPublicLinksLocked", R.string.LimitReachedPublicLinksLocked, limitParams.defaultLimit);
        } else if (type == TYPE_FOLDER_INVITES) {
            limitParams.defaultLimit = MessagesController.getInstance(currentAccount).chatlistInvitesLimitDefault;
            limitParams.premiumLimit = MessagesController.getInstance(currentAccount).chatlistInvitesLimitPremium;
            limitParams.icon = R.drawable.msg_limit_links;
            limitParams.descriptionStr = LocaleController.formatString("LimitReachedFolderLinks", R.string.LimitReachedFolderLinks, limitParams.defaultLimit, limitParams.premiumLimit);
            limitParams.descriptionStrPremium = LocaleController.formatString("LimitReachedFolderLinksPremium", R.string.LimitReachedFolderLinksPremium, limitParams.premiumLimit);
            limitParams.descriptionStrLocked = LocaleController.formatString("LimitReachedFolderLinksLocked", R.string.LimitReachedFolderLinksLocked, limitParams.defaultLimit);
        } else if (type == TYPE_SHARED_FOLDERS) {
            limitParams.defaultLimit = MessagesController.getInstance(currentAccount).chatlistJoinedLimitDefault;
            limitParams.premiumLimit = MessagesController.getInstance(currentAccount).chatlistJoinedLimitPremium;
            limitParams.icon = R.drawable.msg_limit_folder;
            limitParams.descriptionStr = LocaleController.formatString("LimitReachedSharedFolders", R.string.LimitReachedSharedFolders, limitParams.defaultLimit, limitParams.premiumLimit);
            limitParams.descriptionStrPremium = LocaleController.formatString("LimitReachedSharedFoldersPremium", R.string.LimitReachedSharedFoldersPremium, limitParams.premiumLimit);
            limitParams.descriptionStrLocked = LocaleController.formatString("LimitReachedSharedFoldersLocked", R.string.LimitReachedSharedFoldersLocked, limitParams.defaultLimit);
        } else if (type == TYPE_FOLDERS) {
            limitParams.defaultLimit = MessagesController.getInstance(currentAccount).dialogFiltersLimitDefault;
            limitParams.premiumLimit = MessagesController.getInstance(currentAccount).dialogFiltersLimitPremium;
            limitParams.icon = R.drawable.msg_limit_folder;
            limitParams.descriptionStr = LocaleController.formatString("LimitReachedFolders", R.string.LimitReachedFolders, limitParams.defaultLimit, limitParams.premiumLimit);
            limitParams.descriptionStrPremium = LocaleController.formatString("LimitReachedFoldersPremium", R.string.LimitReachedFoldersPremium, limitParams.premiumLimit);
            limitParams.descriptionStrLocked = LocaleController.formatString("LimitReachedFoldersLocked", R.string.LimitReachedFoldersLocked, limitParams.defaultLimit);
        } else if (type == TYPE_CHATS_IN_FOLDER) {
            limitParams.defaultLimit = MessagesController.getInstance(currentAccount).dialogFiltersChatsLimitDefault;
            limitParams.premiumLimit = MessagesController.getInstance(currentAccount).dialogFiltersChatsLimitPremium;
            limitParams.icon = R.drawable.msg_limit_chats;
            limitParams.descriptionStr = LocaleController.formatString("LimitReachedChatInFolders", R.string.LimitReachedChatInFolders, limitParams.defaultLimit, limitParams.premiumLimit);
            limitParams.descriptionStrPremium = LocaleController.formatString("LimitReachedChatInFoldersPremium", R.string.LimitReachedChatInFoldersPremium, limitParams.premiumLimit);
            limitParams.descriptionStrLocked = LocaleController.formatString("LimitReachedChatInFoldersLocked", R.string.LimitReachedChatInFoldersLocked, limitParams.defaultLimit);
        } else if (type == TYPE_TO0_MANY_COMMUNITIES) {
            limitParams.defaultLimit = MessagesController.getInstance(currentAccount).channelsLimitDefault;
            limitParams.premiumLimit = MessagesController.getInstance(currentAccount).channelsLimitPremium;
            limitParams.icon = R.drawable.msg_limit_groups;
            limitParams.descriptionStr = LocaleController.formatString("LimitReachedCommunities", R.string.LimitReachedCommunities, limitParams.defaultLimit, limitParams.premiumLimit);
            limitParams.descriptionStrPremium = LocaleController.formatString("LimitReachedCommunitiesPremium", R.string.LimitReachedCommunitiesPremium, limitParams.premiumLimit);
            limitParams.descriptionStrLocked = LocaleController.formatString("LimitReachedCommunitiesLocked", R.string.LimitReachedCommunitiesLocked, limitParams.defaultLimit);
        } else if (type == TYPE_LARGE_FILE) {
            limitParams.defaultLimit = 100;
            limitParams.premiumLimit = 200;
            limitParams.icon = R.drawable.msg_limit_folder;
            limitParams.descriptionStr = LocaleController.formatString("LimitReachedFileSize", R.string.LimitReachedFileSize, "2 GB", "4 GB");
            limitParams.descriptionStrPremium = LocaleController.formatString("LimitReachedFileSizePremium", R.string.LimitReachedFileSizePremium, "4 GB");
            limitParams.descriptionStrLocked = LocaleController.formatString("LimitReachedFileSizeLocked", R.string.LimitReachedFileSizeLocked, "2 GB");
        } else if (type == TYPE_ACCOUNTS) {
            limitParams.defaultLimit = 3;
            limitParams.premiumLimit = 4;
            limitParams.icon = R.drawable.msg_limit_accounts;
            limitParams.descriptionStr = LocaleController.formatString("LimitReachedAccounts", R.string.LimitReachedAccounts, limitParams.defaultLimit, limitParams.premiumLimit);
            limitParams.descriptionStrPremium = LocaleController.formatString("LimitReachedAccountsPremium", R.string.LimitReachedAccountsPremium, limitParams.premiumLimit);
            limitParams.descriptionStrLocked = LocaleController.formatString("LimitReachedAccountsPremium", R.string.LimitReachedAccountsPremium, limitParams.defaultLimit);
        } else if (type == TYPE_ADD_MEMBERS_RESTRICTED) {
            limitParams.defaultLimit = 0;
            limitParams.premiumLimit = 0;
            limitParams.icon = R.drawable.msg_limit_links;
            limitParams.descriptionStr = LocaleController.formatString("LimitReachedAccounts", R.string.LimitReachedAccounts, limitParams.defaultLimit, limitParams.premiumLimit);
            limitParams.descriptionStrPremium = "";
            limitParams.descriptionStrLocked = "";
        }
        return limitParams;
    }

    boolean loadingAdminedChannels;

    private void loadAdminedChannels() {
        loadingAdminedChannels = true;
        loading = true;
        updateRows();
        TLRPC.TL_channels_getAdminedPublicChannels req = new TLRPC.TL_channels_getAdminedPublicChannels();
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            loadingAdminedChannels = false;
            if (response != null) {
                TLRPC.TL_messages_chats res = (TLRPC.TL_messages_chats) response;
                chats.clear();
                chats.addAll(res.chats);
                loading = false;
                enterAnimator.showItemsAnimated(chatsTitleRow + 4);
                int savedTop = 0;
                for (int i = 0; i < recyclerListView.getChildCount(); i++) {
                    if (recyclerListView.getChildAt(i) instanceof HeaderView) {
                        savedTop = recyclerListView.getChildAt(i).getTop();
                        break;
                    }
                }
                updateRows();
                if (headerRow >= 0 && savedTop != 0) {
                    ((LinearLayoutManager) recyclerListView.getLayoutManager()).scrollToPositionWithOffset(headerRow + 1, savedTop);
                }
            }

            int currentValue = Math.max(chats.size(), limitParams.defaultLimit);
            limitPreviewView.setIconValue(currentValue);
            limitPreviewView.setBagePosition(currentValue / (float) limitParams.premiumLimit);
            limitPreviewView.startDelayedAnimation();
        }));
    }

    private void updateRows() {
        rowCount = 0;
        dividerRow = -1;
        chatStartRow = -1;
        chatEndRow = -1;
        loadingRow = -1;
        emptyViewDividerRow = -1;
        headerRow = rowCount++;
        if (!hasFixedSize(type)) {
            dividerRow = rowCount++;
            chatsTitleRow = rowCount++;
            if (loading) {
                loadingRow = rowCount++;
            } else {
                chatStartRow = rowCount;
                if (type == TYPE_ADD_MEMBERS_RESTRICTED) {
                    rowCount += restrictedUsers.size();
                } else if (type == TYPE_TO0_MANY_COMMUNITIES) {
                    rowCount += inactiveChats.size();
                } else {
                    rowCount += chats.size();
                }
                chatEndRow = rowCount;
                if (chatEndRow - chatStartRow > 1) {
                    emptyViewDividerRow = rowCount++;
                }
            }
        }
        notifyDataSetChanged();
    }


    private void revokeSelectedLinks() {
        final ArrayList<TLRPC.Chat> channels = new ArrayList<>();
        for (Object obj : selectedChats) {
            chats.add((TLRPC.Chat) obj);
        }
        revokeLinks(channels);
    }

    private void revokeLinks(ArrayList<TLRPC.Chat> channels) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(LocaleController.formatPluralString("RevokeLinks", channels.size()));
        if (channels.size() == 1) {
            TLRPC.Chat channel = channels.get(0);
            if (parentIsChannel) {
                builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("RevokeLinkAlertChannel", R.string.RevokeLinkAlertChannel, MessagesController.getInstance(currentAccount).linkPrefix + "/" + ChatObject.getPublicUsername(channel), channel.title)));
            } else {
                builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("RevokeLinkAlert", R.string.RevokeLinkAlert, MessagesController.getInstance(currentAccount).linkPrefix + "/" + ChatObject.getPublicUsername(channel), channel.title)));
            }
        } else {
            if (parentIsChannel) {
                builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("RevokeLinksAlertChannel", R.string.RevokeLinksAlertChannel)));
            } else {
                builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("RevokeLinksAlert", R.string.RevokeLinksAlert)));
            }
        }
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        builder.setPositiveButton(LocaleController.getString("RevokeButton", R.string.RevokeButton), (dialogInterface, interface2) -> {
            dismiss();
            for (int i = 0; i < channels.size(); i++) {
                TLRPC.TL_channels_updateUsername req1 = new TLRPC.TL_channels_updateUsername();
                TLRPC.Chat channel = channels.get(i);
                req1.channel = MessagesController.getInputChannel(channel);
                req1.username = "";
                ConnectionsManager.getInstance(currentAccount).sendRequest(req1, (response1, error1) -> {
                    if (response1 instanceof TLRPC.TL_boolTrue) {
                        AndroidUtilities.runOnUIThread(onSuccessRunnable);
                    }
                }, ConnectionsManager.RequestFlagInvokeAfter);
            }
        });
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
        TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
        }
    }

    private void loadInactiveChannels() {
        loading = true;
        updateRows();
        TLRPC.TL_channels_getInactiveChannels inactiveChannelsRequest = new TLRPC.TL_channels_getInactiveChannels();
        ConnectionsManager.getInstance(currentAccount).sendRequest(inactiveChannelsRequest, ((response, error) -> {
            if (error == null) {
                final TLRPC.TL_messages_inactiveChats chats = (TLRPC.TL_messages_inactiveChats) response;
                final ArrayList<String> signatures = new ArrayList<>();
                for (int i = 0; i < chats.chats.size(); i++) {
                    TLRPC.Chat chat = chats.chats.get(i);
                    int currentDate = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
                    int date = chats.dates.get(i);
                    int daysDif = (currentDate - date) / 86400;

                    String dateFormat;
                    if (daysDif < 30) {
                        dateFormat = LocaleController.formatPluralString("Days", daysDif);
                    } else if (daysDif < 365) {
                        dateFormat = LocaleController.formatPluralString("Months", daysDif / 30);
                    } else {
                        dateFormat = LocaleController.formatPluralString("Years", daysDif / 365);
                    }
                    if (ChatObject.isMegagroup(chat)) {
                        String members = LocaleController.formatPluralString("Members", chat.participants_count);
                        signatures.add(LocaleController.formatString("InactiveChatSignature", R.string.InactiveChatSignature, members, dateFormat));
                    } else if (ChatObject.isChannel(chat)) {
                        signatures.add(LocaleController.formatString("InactiveChannelSignature", R.string.InactiveChannelSignature, dateFormat));
                    } else {
                        String members = LocaleController.formatPluralString("Members", chat.participants_count);
                        signatures.add(LocaleController.formatString("InactiveChatSignature", R.string.InactiveChatSignature, members, dateFormat));
                    }
                }
                AndroidUtilities.runOnUIThread(() -> {
                    inactiveChatsSignatures.clear();
                    inactiveChats.clear();
                    inactiveChatsSignatures.addAll(signatures);
                    inactiveChats.addAll(chats.chats);
                    loading = false;
                    enterAnimator.showItemsAnimated(chatsTitleRow + 4);
                    int savedTop = 0;
                    for (int i = 0; i < recyclerListView.getChildCount(); i++) {
                        if (recyclerListView.getChildAt(i) instanceof HeaderView) {
                            savedTop = recyclerListView.getChildAt(i).getTop();
                            break;
                        }
                    }
                    updateRows();
                    if (headerRow >= 0 && savedTop != 0) {
                        ((LinearLayoutManager) recyclerListView.getLayoutManager()).scrollToPositionWithOffset(headerRow + 1, savedTop);
                    }

                    if (limitParams == null) {
                        limitParams = getLimitParams(type, currentAccount);
                    }
                    int currentValue = Math.max(inactiveChats.size(), limitParams.defaultLimit);
                    if (limitPreviewView != null) {
                        limitPreviewView.setIconValue(currentValue);
                        limitPreviewView.setBagePosition(currentValue / (float) limitParams.premiumLimit);
                        limitPreviewView.startDelayedAnimation();
                    }
                });
            }
        }));
    }

    public static class LimitParams {
        int icon = 0;
        String descriptionStr = null;
        String descriptionStrPremium = null;
        String descriptionStrLocked = null;
        int defaultLimit = 0;
        int premiumLimit = 0;
    }

}
