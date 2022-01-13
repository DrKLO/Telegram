package org.telegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.AvailableReactionCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SimpleThemeDescription;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class ChatReactionsEditActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
    private final static int TYPE_INFO = 0, TYPE_HEADER = 1, TYPE_REACTION = 2;

    public final static String KEY_CHAT_ID = "chat_id";

    private TLRPC.Chat currentChat;
    private TLRPC.ChatFull info;
    private long chatId;

    private List<String> chatReactions = new ArrayList<>();

    private LinearLayout contentView;
    private RecyclerListView listView;
    private RecyclerView.Adapter listAdapter;

    private TextCheckCell enableReactionsCell;
    private ArrayList<TLRPC.TL_availableReaction> availableReactions = new ArrayList();

    public ChatReactionsEditActivity(Bundle args) {
        super(args);

        chatId = args.getLong(KEY_CHAT_ID, 0);
    }

    @Override
    public boolean onFragmentCreate() {
        currentChat = getMessagesController().getChat(chatId);
        if (currentChat == null) {
            currentChat = MessagesStorage.getInstance(currentAccount).getChatSync(chatId);
            if (currentChat != null) {
                getMessagesController().putChat(currentChat, true);
            } else {
                return false;
            }
            if (info == null) {
                info = MessagesStorage.getInstance(currentAccount).loadChatInfo(chatId, ChatObject.isChannel(currentChat), new CountDownLatch(1), false, false);
                if (info == null) {
                    return false;
                }
            }
        }
        getNotificationCenter().addObserver(this, NotificationCenter.reactionsDidLoad);
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        actionBar.setTitle(LocaleController.getString("Reactions", R.string.Reactions));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        LinearLayout ll = new LinearLayout(context);
        ll.setOrientation(LinearLayout.VERTICAL);

        availableReactions.addAll(getMediaDataController().getEnabledReactionsList());

        enableReactionsCell = new TextCheckCell(context);
        enableReactionsCell.setHeight(56);
        enableReactionsCell.setTextAndCheck(LocaleController.getString("EnableReactions", R.string.EnableReactions), !chatReactions.isEmpty(), false);
        enableReactionsCell.setBackgroundColor(Theme.getColor(enableReactionsCell.isChecked() ? Theme.key_windowBackgroundChecked : Theme.key_windowBackgroundUnchecked));
        enableReactionsCell.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        enableReactionsCell.setAnimatingToThumbInsteadOfTouch(true);
        enableReactionsCell.setOnClickListener(v -> {
            setCheckedEnableReactionCell(!enableReactionsCell.isChecked());
        });
        ll.addView(enableReactionsCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setAdapter(listAdapter = new RecyclerView.Adapter() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                switch (viewType) {
                    default:
                    case TYPE_REACTION: {
                        return new RecyclerListView.Holder(new AvailableReactionCell(context, false));
                    }
                    case TYPE_INFO: {
                        TextInfoPrivacyCell infoCell = new TextInfoPrivacyCell(context);
                        return new RecyclerListView.Holder(infoCell);
                    }
                    case TYPE_HEADER: {
                        return new RecyclerListView.Holder(new HeaderCell(context, 23));
                    }
                }
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                switch (getItemViewType(position)) {
                    case TYPE_INFO:
                        TextInfoPrivacyCell infoCell = (TextInfoPrivacyCell) holder.itemView;
                        infoCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4));
                        infoCell.setText(ChatObject.isChannelAndNotMegaGroup(currentChat) ? LocaleController.getString("EnableReactionsChannelInfo", R.string.EnableReactionsChannelInfo) :
                                LocaleController.getString("EnableReactionsGroupInfo", R.string.EnableReactionsGroupInfo));
                        break;
                    case TYPE_HEADER:
                        HeaderCell headerCell = (HeaderCell) holder.itemView;
                        headerCell.setText(LocaleController.getString("AvailableReactions", R.string.AvailableReactions));
                        headerCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                        break;
                    case TYPE_REACTION:
                        AvailableReactionCell reactionCell = (AvailableReactionCell) holder.itemView;
                        TLRPC.TL_availableReaction react = availableReactions.get(position - 2);
                        reactionCell.bind(react, chatReactions.contains(react.reaction));
                        break;
                }
            }

            @Override
            public int getItemCount() {
                return 1 + (!chatReactions.isEmpty() ? 1 + availableReactions.size() : 0);
            }

            @Override
            public int getItemViewType(int position) {
                return position == 0 ? TYPE_INFO : position == 1 ? TYPE_HEADER : TYPE_REACTION;
            }
        });
        listView.setOnItemClickListener((view, position) -> {
            if (position <= 1) return;

            AvailableReactionCell cell = (AvailableReactionCell) view;
            TLRPC.TL_availableReaction react = availableReactions.get(position - 2);
            boolean nc = !chatReactions.contains(react.reaction);
            if (nc) {
                chatReactions.add(react.reaction);
            } else {
                chatReactions.remove(react.reaction);
                if (chatReactions.isEmpty()) {
                    setCheckedEnableReactionCell(false);
                }
            }

            cell.setChecked(nc, true);
        });
        ll.addView(listView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));
        fragmentView = contentView = ll;

        updateColors();

        return contentView;
    }

    private void setCheckedEnableReactionCell(boolean c) {
        if (enableReactionsCell.isChecked() == c) {
            return;
        }
        enableReactionsCell.setChecked(c);
        int clr = Theme.getColor(c ? Theme.key_windowBackgroundChecked : Theme.key_windowBackgroundUnchecked);
        if (c) {
            enableReactionsCell.setBackgroundColorAnimated(c, clr);
        } else {
            enableReactionsCell.setBackgroundColorAnimatedReverse(clr);
        }
        if (c) {
            for (TLRPC.TL_availableReaction a : availableReactions) {
                chatReactions.add(a.reaction);
            }
            listAdapter.notifyItemRangeInserted(1, 1 + availableReactions.size());
        } else {
            chatReactions.clear();
            listAdapter.notifyItemRangeRemoved(1, 1 + availableReactions.size());
        }
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();

        boolean changed = true;
        if (info != null) {
            changed = !info.available_reactions.equals(chatReactions);
        }
        if (changed) {
            getMessagesController().setChatReactions(chatId, chatReactions);
        }
        getNotificationCenter().removeObserver(this, NotificationCenter.reactionsDidLoad);
    }


    /**
     * Sets chat full info
     * @param info Info to use
     */
    public void setInfo(TLRPC.ChatFull info) {
        this.info = info;
        if (info != null) {
            if (currentChat == null) {
                currentChat = getMessagesController().getChat(chatId);
            }

            chatReactions = new ArrayList<>(info.available_reactions);
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        return SimpleThemeDescription.createThemeDescriptions(this::updateColors,
                Theme.key_windowBackgroundWhite,
                Theme.key_windowBackgroundWhiteBlackText,
                Theme.key_windowBackgroundWhiteGrayText2,
                Theme.key_listSelector,
                Theme.key_windowBackgroundGray,
                Theme.key_windowBackgroundWhiteGrayText4,
                Theme.key_windowBackgroundWhiteRedText4,
                Theme.key_windowBackgroundChecked,
                Theme.key_windowBackgroundCheckText,
                Theme.key_switchTrackBlue,
                Theme.key_switchTrackBlueChecked,
                Theme.key_switchTrackBlueThumb,
                Theme.key_switchTrackBlueThumbChecked
        );
    }

    @SuppressLint("NotifyDataSetChanged")
    private void updateColors() {
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        enableReactionsCell.setColors(Theme.key_windowBackgroundCheckText, Theme.key_switchTrackBlue, Theme.key_switchTrackBlueChecked, Theme.key_switchTrackBlueThumb, Theme.key_switchTrackBlueThumbChecked);
        listAdapter.notifyDataSetChanged();
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (account != currentAccount) return;
        if (id == NotificationCenter.reactionsDidLoad) {
            availableReactions.clear();
            availableReactions.addAll(getMediaDataController().getEnabledReactionsList());
            listAdapter.notifyDataSetChanged();
        }
    }
}