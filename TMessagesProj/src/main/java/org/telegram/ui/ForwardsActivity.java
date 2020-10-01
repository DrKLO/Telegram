/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.LoadingCell;
import org.telegram.ui.Cells.ManageChatUserCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

public class ForwardsActivity extends BaseFragment {

    private ListAdapter listViewAdapter;
    private EmptyTextProgressView emptyView;
    private RecyclerListView listView;
    private LinearLayoutManager layoutManager;

    private MessageObject messageObject;

    private ArrayList<TLRPC.Message> messages = new ArrayList<>();
    private boolean loading;
    private boolean firstLoaded;

    private int headerRow;
    private int startRow;
    private int endRow;
    private int privateRow;
    private int sectionRow;
    private int loadingRow;
    private int rowCount;

    private int nextRate;
    private int publicChats;
    private boolean endReached;

    public ForwardsActivity(MessageObject message) {
        messageObject = message;
    }

    private void updateRows() {
        sectionRow = -1;
        headerRow = -1;
        startRow = -1;
        endRow = -1;
        loadingRow = -1;
        privateRow = -1;

        rowCount = 0;
        if (firstLoaded) {
            headerRow = rowCount++;
            if (messageObject.messageOwner.forwards - publicChats > 0) {
                privateRow = rowCount++;
            }
            if (!messages.isEmpty()) {
                startRow = rowCount;
                rowCount += messages.size();
                endRow = rowCount;
                if (!endReached) {
                    loadingRow = rowCount++;
                }
            }
            sectionRow = rowCount++;
        }
        if (listViewAdapter != null) {
            listViewAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        loadChats(100);
        return true;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("Shares", R.string.Shares));
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
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        emptyView = new EmptyTextProgressView(context);
        emptyView.setText(LocaleController.getString("NoResult", R.string.NoResult));
        emptyView.setVisibility(View.GONE);
        frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        ((SimpleItemAnimator) listView.getItemAnimator()).setSupportsChangeAnimations(false);
        listView.setAdapter(listViewAdapter = new ListAdapter(context));
        listView.setVerticalScrollbarPosition(LocaleController.isRTL ? RecyclerListView.SCROLLBAR_POSITION_LEFT : RecyclerListView.SCROLLBAR_POSITION_RIGHT);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView.setOnItemClickListener((view, position) -> {
            if (position >= startRow && position < endRow) {
                TLRPC.Message message = messages.get(position - startRow);
                int did = (int) MessageObject.getDialogId(message);
                Bundle args = new Bundle();
                if (did > 0) {
                    args.putInt("user_id", did);
                } else {
                    args.putInt("chat_id", -did);
                }
                args.putInt("message_id", message.id);
                if (getMessagesController().checkCanOpenChat(args, this)) {
                    presentFragment(new ChatActivity(args));
                }
            }
        });

        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {

            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                int firstVisibleItem = layoutManager.findFirstVisibleItemPosition();
                int visibleItemCount = firstVisibleItem == RecyclerView.NO_POSITION ? 0 : Math.abs(layoutManager.findLastVisibleItemPosition() - firstVisibleItem) + 1;
                int totalItemCount = recyclerView.getAdapter().getItemCount();

                if (visibleItemCount > 0) {
                    if (!endReached && !loading && !messages.isEmpty() && firstVisibleItem + visibleItemCount >= totalItemCount - 5) {
                        loadChats(100);
                    }
                }
            }
        });

        if (loading) {
            emptyView.showProgress();
        } else {
            emptyView.showTextView();
        }
        updateRows();

        listView.setEmptyView(emptyView);

        return fragmentView;
    }

    private void loadChats(int count) {
        if (loading) {
            return;
        }
        loading = true;
        if (emptyView != null && messages.isEmpty()) {
            emptyView.showProgress();
        }
        if (listViewAdapter != null) {
            listViewAdapter.notifyDataSetChanged();
        }
        TLRPC.TL_stats_getMessagePublicForwards req = new TLRPC.TL_stats_getMessagePublicForwards();
        req.msg_id = messageObject.getId();
        req.limit = count;
        req.channel = getMessagesController().getInputChannel((int) -messageObject.getDialogId());
        if (!messages.isEmpty()) {
            TLRPC.Message message = messages.get(messages.size());
            req.offset_id = message.id;
            req.offset_peer = getMessagesController().getInputPeer((int) MessageObject.getDialogId(message));
            req.offset_rate = nextRate;
        } else {
            req.offset_peer = new TLRPC.TL_inputPeerEmpty();
        }
        int reqId = getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error == null) {
                TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                if ((res.flags & 1) != 0) {
                    nextRate = res.next_rate;
                }
                if (res.count != 0) {
                    publicChats = res.count;
                } else if (publicChats == 0) {
                    publicChats = res.messages.size();
                }
                endReached = !(res instanceof TLRPC.TL_messages_messagesSlice);
                getMessagesController().putChats(res.chats, false);
                getMessagesController().putUsers(res.users, false);
                messages.addAll(res.messages);
                if (emptyView != null) {
                    emptyView.showTextView();
                }
            }
            firstLoaded = true;
            loading = false;
            updateRows();
        }));
        getConnectionsManager().bindRequestToGuid(reqId, classGuid);
    }

    @Override
    public void onResume() {
        super.onResume();
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
        if (listViewAdapter != null) {
            listViewAdapter.notifyDataSetChanged();
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            if (type == 0) {
                ManageChatUserCell cell = (ManageChatUserCell) holder.itemView;
                return cell.getCurrentObject() instanceof TLObject;
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
                    view = new ManageChatUserCell(mContext, 6, 2, false);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 1:
                    view = new ShadowSectionCell(mContext);
                    break;
                case 2:
                    HeaderCell headerCell = new HeaderCell(mContext, Theme.key_windowBackgroundWhiteBlueHeader, 21, 11, false);
                    headerCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    headerCell.setHeight(43);
                    view = headerCell;
                    break;
                case 3:
                default:
                    view = new LoadingCell(mContext, AndroidUtilities.dp(40), AndroidUtilities.dp(120));
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0:
                    ManageChatUserCell userCell = (ManageChatUserCell) holder.itemView;
                    if (position == privateRow) {
                        userCell.setData(1, LocaleController.formatPluralString("Shared", messageObject.messageOwner.forwards - publicChats), LocaleController.getString("SharedToPrivateMessagesAndGroups", R.string.SharedToPrivateMessagesAndGroups), startRow != -1);
                    } else {
                        TLRPC.Message item = getItem(position);
                        int did = (int) MessageObject.getDialogId(item);
                        TLObject object;
                        String status = null;
                        if (did > 0) {
                            object = getMessagesController().getUser(did);
                        } else {
                            object = getMessagesController().getChat(-did);
                            TLRPC.Chat chat = (TLRPC.Chat) object;
                            if (chat.participants_count != 0) {
                                if (ChatObject.isChannel(chat) && !chat.megagroup) {
                                    status = LocaleController.formatPluralString("Subscribers", chat.participants_count);
                                } else {
                                    status = LocaleController.formatPluralString("Members", chat.participants_count);
                                }
                                status = String.format("%1$s, %2$s", status, LocaleController.formatDateAudio(item.date, false));
                            }
                        }
                        if (object != null) {
                            userCell.setData(object, null, status, position != endRow - 1);
                        }
                    }
                    break;
                case 1:
                    holder.itemView.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    break;
                case 2:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    headerCell.setText(LocaleController.formatPluralString("Shares", messageObject.messageOwner.forwards));
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
            if (position == sectionRow) {
                return 1;
            } else if (position == headerRow) {
                return 2;
            } else if (position == loadingRow) {
                return 3;
            }
            return 0;
        }

        public TLRPC.Message getItem(int position) {
            if (position >= startRow && position < endRow) {
                return messages.get(position - startRow);
            }
            return null;
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
                }
            }
        };

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{HeaderCell.class, ManageChatUserCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));

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

        return themeDescriptions;
    }
}
