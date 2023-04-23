package org.telegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
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
import org.telegram.ui.Cells.RadioCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SimpleThemeDescription;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class ChatReactionsEditActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
    private final static int TYPE_INFO = 0, TYPE_HEADER = 1, TYPE_REACTION = 2, TYPE_CONTROLS_CONTAINER = 3;

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
    LinearLayout contorlsLayout;

    public final static int SELECT_TYPE_NONE = 2, SELECT_TYPE_SOME = 1, SELECT_TYPE_ALL = 0;
    int selectedType = -1;
    int startFromType;
    private RadioCell allReactions;
    private RadioCell someReactions;
    private RadioCell disableReactions;
    ArrayList<RadioCell> radioCells = new ArrayList();
    boolean isChannel;


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
        isChannel = ChatObject.isChannelAndNotMegaGroup(chatId, currentAccount);

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

        if (isChannel) {
            enableReactionsCell = new TextCheckCell(context);
            enableReactionsCell.setHeight(56);
            enableReactionsCell.setTextAndCheck(LocaleController.getString("EnableReactions", R.string.EnableReactions), !chatReactions.isEmpty(), false);
            enableReactionsCell.setBackgroundColor(Theme.getColor(enableReactionsCell.isChecked() ? Theme.key_windowBackgroundChecked : Theme.key_windowBackgroundUnchecked));
            enableReactionsCell.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            enableReactionsCell.setOnClickListener(v -> {
                setCheckedEnableReactionCell(enableReactionsCell.isChecked() ? SELECT_TYPE_NONE : SELECT_TYPE_SOME, true);
            });
            ll.addView(enableReactionsCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        HeaderCell headerCell = new HeaderCell(context);
        headerCell.setText(LocaleController.getString("AvailableReactions", R.string.AvailableReactions));

        contorlsLayout = new LinearLayout(context);
        contorlsLayout.setOrientation(LinearLayout.VERTICAL);
        allReactions = new RadioCell(context);
        allReactions.setText(LocaleController.getString("AllReactions", R.string.AllReactions), false, true);
        someReactions = new RadioCell(context);
        someReactions.setText(LocaleController.getString("SomeReactions", R.string.SomeReactions), false, true);
        disableReactions = new RadioCell(context);
        disableReactions.setText(LocaleController.getString("NoReactions", R.string.NoReactions), false, false);
        contorlsLayout.addView(headerCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        contorlsLayout.addView(allReactions, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        contorlsLayout.addView(someReactions, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        contorlsLayout.addView(disableReactions, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        radioCells.clear();
        radioCells.add(allReactions);
        radioCells.add(someReactions);
        radioCells.add(disableReactions);
        allReactions.setOnClickListener(v -> AndroidUtilities.runOnUIThread(() -> setCheckedEnableReactionCell(SELECT_TYPE_ALL, true)));
        someReactions.setOnClickListener(v -> AndroidUtilities.runOnUIThread(() -> setCheckedEnableReactionCell(SELECT_TYPE_SOME, true)));
        disableReactions.setOnClickListener(v -> AndroidUtilities.runOnUIThread(() -> setCheckedEnableReactionCell(SELECT_TYPE_NONE, true)));

        headerCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        allReactions.setBackground(Theme.createSelectorWithBackgroundDrawable(Theme.getColor(Theme.key_windowBackgroundWhite), Theme.getColor(Theme.key_listSelector)));
        someReactions.setBackground(Theme.createSelectorWithBackgroundDrawable(Theme.getColor(Theme.key_windowBackgroundWhite), Theme.getColor(Theme.key_listSelector)));
        disableReactions.setBackground(Theme.createSelectorWithBackgroundDrawable(Theme.getColor(Theme.key_windowBackgroundWhite), Theme.getColor(Theme.key_listSelector)));


        setCheckedEnableReactionCell(startFromType, false);

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setAdapter(listAdapter = new RecyclerView.Adapter() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                switch (viewType) {
                    default:
                    case TYPE_REACTION: {
                        return new RecyclerListView.Holder(new AvailableReactionCell(context, false, false));
                    }
                    case TYPE_INFO: {
                        TextInfoPrivacyCell infoCell = new TextInfoPrivacyCell(context);
                        return new RecyclerListView.Holder(infoCell);
                    }
                    case TYPE_HEADER: {
                        return new RecyclerListView.Holder(new HeaderCell(context, 23));
                    }
                    case TYPE_CONTROLS_CONTAINER:
                        FrameLayout frameLayout = new FrameLayout(context);
                        if (contorlsLayout.getParent() != null) {
                            ((ViewGroup) contorlsLayout.getParent()).removeView(contorlsLayout);
                        }
                        frameLayout.addView(contorlsLayout);
                        frameLayout.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                        return new RecyclerListView.Holder(frameLayout);

                }
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                switch (getItemViewType(position)) {
                    case TYPE_INFO:
                        TextInfoPrivacyCell infoCell = (TextInfoPrivacyCell) holder.itemView;
                        infoCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4));
                        if (isChannel) {
                            infoCell.setText(ChatObject.isChannelAndNotMegaGroup(currentChat) ? LocaleController.getString("EnableReactionsChannelInfo", R.string.EnableReactionsChannelInfo) :
                                    LocaleController.getString("EnableReactionsGroupInfo", R.string.EnableReactionsGroupInfo));
                        } else {
                            if (selectedType == SELECT_TYPE_SOME) {
                                infoCell.setText(LocaleController.getString("EnableSomeReactionsInfo", R.string.EnableSomeReactionsInfo));
                            } else if (selectedType == SELECT_TYPE_ALL) {
                                infoCell.setText(LocaleController.getString("EnableAllReactionsInfo", R.string.EnableAllReactionsInfo));
                            } else if (selectedType == SELECT_TYPE_NONE) {
                                infoCell.setText(LocaleController.getString("DisableReactionsInfo", R.string.DisableReactionsInfo));
                            }
                        }
                        break;
                    case TYPE_HEADER:
                        HeaderCell headerCell = (HeaderCell) holder.itemView;
                        headerCell.setText(LocaleController.getString("OnlyAllowThisReactions", R.string.OnlyAllowThisReactions));
                        headerCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                        break;
                    case TYPE_REACTION:
                        AvailableReactionCell reactionCell = (AvailableReactionCell) holder.itemView;
                        TLRPC.TL_availableReaction react = availableReactions.get(position - (isChannel ? 2 : 3));
                        reactionCell.bind(react, chatReactions.contains(react.reaction), currentAccount);
                        break;
                }
            }

            @Override
            public int getItemCount() {
                if (isChannel) {
                    return 1 + (!chatReactions.isEmpty() ? 1 + availableReactions.size() : 0);
                }
                return 1 + 1 + (!chatReactions.isEmpty() ? 1 + availableReactions.size() : 0);
            }

            @Override
            public int getItemViewType(int position) {
                if (isChannel) {
                    return position == 0 ? TYPE_INFO : position == 1 ? TYPE_HEADER : TYPE_REACTION;
                }
                if (position == 0) {
                    return TYPE_CONTROLS_CONTAINER;
                }
                return position == 1 ? TYPE_INFO : position == 2 ? TYPE_HEADER : TYPE_REACTION;
            }
        });
        listView.setOnItemClickListener((view, position) -> {
            if (position <= (isChannel ? 1 : 2)) return;

            AvailableReactionCell cell = (AvailableReactionCell) view;
            TLRPC.TL_availableReaction react = availableReactions.get(position - (isChannel ? 2 : 3));
            boolean nc = !chatReactions.contains(react.reaction);
            if (nc) {
                chatReactions.add(react.reaction);
            } else {
                chatReactions.remove(react.reaction);
                if (chatReactions.isEmpty()) {
                    if (listAdapter != null) {
                        listAdapter.notifyItemRangeRemoved((isChannel ? 1 : 2), 1 + availableReactions.size());
                    }
                    setCheckedEnableReactionCell(SELECT_TYPE_NONE, true);
                }
            }

            cell.setChecked(nc, true);
        });
        ll.addView(listView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));
        fragmentView = contentView = ll;

        updateColors();

        return contentView;
    }

    private void setCheckedEnableReactionCell(int selectType, boolean animated) {
        if (selectedType == selectType) {
            return;
        }
        if (enableReactionsCell != null) {
            boolean checked = selectType == SELECT_TYPE_SOME;
            enableReactionsCell.setChecked(checked);
            int clr = Theme.getColor(checked ? Theme.key_windowBackgroundChecked : Theme.key_windowBackgroundUnchecked);
            if (checked) {
                enableReactionsCell.setBackgroundColorAnimated(checked, clr);
            } else {
                enableReactionsCell.setBackgroundColorAnimatedReverse(clr);
            }
        }
        this.selectedType = selectType;
        for (int i = 0; i < radioCells.size(); i++) {
            radioCells.get(i).setChecked(selectType == i, animated);
        }

        if (selectType == SELECT_TYPE_SOME) {
            if (animated) {
                chatReactions.clear();
                for (TLRPC.TL_availableReaction a : availableReactions) {
                    if (a.reaction.equals("\uD83D\uDC4D") || a.reaction.equals("\uD83D\uDC4E")) {
                        chatReactions.add(a.reaction);
                    }
                }
                if (chatReactions.isEmpty() && availableReactions.size() >= 2) {
                    chatReactions.add(availableReactions.get(0).reaction);
                    chatReactions.add(availableReactions.get(1).reaction);
                }
            }
            if (listAdapter != null && animated) {
                listAdapter.notifyItemRangeInserted((isChannel ? 1 : 2), 1 + availableReactions.size());
            }
        } else {
            if (!chatReactions.isEmpty()) {
                chatReactions.clear();
                if (listAdapter != null && animated) {
                    listAdapter.notifyItemRangeRemoved((isChannel ? 1 : 2), 1 + availableReactions.size());
                }
            }
        }
        if (!isChannel && listAdapter != null && animated) {
            listAdapter.notifyItemChanged(1);
        }
        if (listAdapter != null && !animated) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        getMessagesController().setChatReactions(chatId, selectedType, chatReactions);
        getNotificationCenter().removeObserver(this, NotificationCenter.reactionsDidLoad);
    }


    /**
     * Sets chat full info
     *
     * @param info Info to use
     */
    public void setInfo(TLRPC.ChatFull info) {
        this.info = info;
        if (info != null) {
            if (currentChat == null) {
                currentChat = getMessagesController().getChat(chatId);
            }
            chatReactions = new ArrayList<>();
            if (info.available_reactions instanceof TLRPC.TL_chatReactionsAll) {
                startFromType = SELECT_TYPE_ALL;
            } else if (info.available_reactions instanceof TLRPC.TL_chatReactionsNone) {
                startFromType = SELECT_TYPE_NONE;
            } else if (info.available_reactions instanceof TLRPC.TL_chatReactionsSome) {
                TLRPC.TL_chatReactionsSome reactionsSome = (TLRPC.TL_chatReactionsSome) info.available_reactions;
                for (int i = 0; i < reactionsSome.reactions.size(); i++) {
                    if (reactionsSome.reactions.get(i) instanceof TLRPC.TL_reactionEmoji) {
                        chatReactions.add(((TLRPC.TL_reactionEmoji) reactionsSome.reactions.get(i)).emoticon);
                    }
                }
                startFromType = SELECT_TYPE_SOME;
            }

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
                Theme.key_text_RedRegular,
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
        if (enableReactionsCell != null) {
            enableReactionsCell.setColors(Theme.key_windowBackgroundCheckText, Theme.key_switchTrackBlue, Theme.key_switchTrackBlueChecked, Theme.key_switchTrackBlueThumb, Theme.key_switchTrackBlueThumbChecked);
        }
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