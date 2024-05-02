package org.telegram.ui.Components.Premium.boosts.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Paint;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Components.ListView.AdapterWithDiffUtils;
import org.telegram.ui.Components.Premium.boosts.BoostRepository;
import org.telegram.ui.Components.Premium.boosts.cells.selector.SelectorCountryCell;
import org.telegram.ui.Components.Premium.boosts.cells.selector.SelectorLetterCell;
import org.telegram.ui.Components.Premium.boosts.cells.selector.SelectorUserCell;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.StickerEmptyView;
import org.telegram.ui.DialogsActivity;

import java.util.HashMap;
import java.util.List;

public class SelectorAdapter extends AdapterWithDiffUtils {

    public static final int VIEW_TYPE_PAD = -1;
    public static final int VIEW_TYPE_USER = 3;
    public static final int VIEW_TYPE_NO_USERS = 5;
    public static final int VIEW_TYPE_COUNTRY = 6;
    public static final int VIEW_TYPE_LETTER = 7;
    public static final int VIEW_TYPE_TOP_SECTION = 8;
    public static final int VIEW_TYPE_BUTTON = 9;

    private final Theme.ResourcesProvider resourcesProvider;
    private final Context context;
    private RecyclerListView listView;
    private List<Item> items;
    private HashMap<Long, Integer> chatsParticipantsCount = new HashMap<>();
    private View.OnClickListener topSectionClickListener;
    private boolean isGreenSelector;
    private GraySectionCell topSectionCell;

    public SelectorAdapter(Context context, Theme.ResourcesProvider resourcesProvider) {
        this.context = context;
        this.resourcesProvider = resourcesProvider;
        BoostRepository.loadParticipantsCount(result -> {
            chatsParticipantsCount.clear();
            chatsParticipantsCount.putAll(result);
        });
    }

    public void setData(List<Item> items, RecyclerListView listView) {
        this.items = items;
        this.listView = listView;
    }

    public void setTopSectionClickListener(View.OnClickListener topSectionClickListener) {
        this.topSectionClickListener = topSectionClickListener;
        if (topSectionCell != null) {
            if (topSectionClickListener == null) {
                topSectionCell.setRightText(null);
            } else {
                topSectionCell.setRightText(LocaleController.getString(R.string.UsersDeselectAll), true, topSectionClickListener);
            }
        }
    }

    public void setGreenSelector(boolean isGreenSelector) {
        this.isGreenSelector = isGreenSelector;
    }

    @Override
    public boolean isEnabled(RecyclerView.ViewHolder holder) {
        return (holder.getItemViewType() == VIEW_TYPE_USER || holder.getItemViewType() == VIEW_TYPE_COUNTRY || holder.getItemViewType() == VIEW_TYPE_BUTTON);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        if (viewType == VIEW_TYPE_PAD) {
            view = new View(context);
        } else if (viewType == VIEW_TYPE_USER) {
            view = new SelectorUserCell(context, resourcesProvider, isGreenSelector);
        } else if (viewType == VIEW_TYPE_NO_USERS) {
            StickerEmptyView searchEmptyView = new StickerEmptyView(context, null, StickerEmptyView.STICKER_TYPE_SEARCH, resourcesProvider);
            searchEmptyView.title.setText(LocaleController.getString("NoResult", R.string.NoResult));
            searchEmptyView.subtitle.setText(LocaleController.getString("SearchEmptyViewFilteredSubtitle2", R.string.SearchEmptyViewFilteredSubtitle2));
            searchEmptyView.linearLayout.setTranslationY(AndroidUtilities.dp(24));
            view = searchEmptyView;
        } else if (viewType == VIEW_TYPE_LETTER) {
            view = new SelectorLetterCell(context, resourcesProvider);
        } else if (viewType == VIEW_TYPE_COUNTRY) {
            view = new SelectorCountryCell(context, resourcesProvider);
        } else if (viewType == VIEW_TYPE_TOP_SECTION) {
            view = new GraySectionCell(context, resourcesProvider);
        } else if (viewType == VIEW_TYPE_BUTTON) {
            TextCell cell = new TextCell(context, resourcesProvider);
            cell.leftPadding = 23 - 7;
            cell.imageLeft = 19;
            view = cell;
        } else {
            view = new View(context);
        }
        return new RecyclerListView.Holder(view);
    }

    public int getParticipantsCount(TLRPC.Chat chat) {
        TLRPC.ChatFull chatFull = MessagesController.getInstance(UserConfig.selectedAccount).getChatFull(chat.id);
        if (chatFull != null && chatFull.participants_count > 0) {
            return chatFull.participants_count;
        } else if (!chatsParticipantsCount.isEmpty()) {
            Integer count = chatsParticipantsCount.get(chat.id);
            if (count != null) {
                return count;
            }
        }
        return chat.participants_count;
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (items == null || position < 0) {
            return;
        }
        final Item item = items.get(position);
        final int viewType = holder.getItemViewType();
        if (viewType == VIEW_TYPE_USER) {
            SelectorUserCell userCell = (SelectorUserCell) holder.itemView;
            if (item.user != null) {
                userCell.setUser(item.user);
            } else if (item.chat != null) {
                userCell.setChat(item.chat, getParticipantsCount(item.chat));
            } else if (item.peer != null) {
                TLRPC.InputPeer peer = item.peer;
                if (peer instanceof TLRPC.TL_inputPeerSelf) {
                    userCell.setUser(UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser());
                } else if (peer instanceof TLRPC.TL_inputPeerUser) {
                    userCell.setUser(MessagesController.getInstance(UserConfig.selectedAccount).getUser(peer.user_id));
                } else if (peer instanceof TLRPC.TL_inputPeerChat) {
                    TLRPC.Chat chat = MessagesController.getInstance(UserConfig.selectedAccount).getChat(peer.chat_id);
                    userCell.setChat(chat, getParticipantsCount(chat));
                } else if (peer instanceof TLRPC.TL_inputPeerChannel) {
                    TLRPC.Chat chat = MessagesController.getInstance(UserConfig.selectedAccount).getChat(peer.channel_id);
                    userCell.setChat(chat, getParticipantsCount(chat));
                }
            }
            userCell.setChecked(item.checked, false);
            userCell.setCheckboxAlpha(1f, false);
            userCell.setDivider(position + 1 >= items.size() || items.get(position + 1).viewType == viewType);
            if ((position + 1 < items.size()) && items.get(position + 1).viewType == VIEW_TYPE_LETTER) {
                userCell.setDivider(false);
            }
            userCell.setOptions(item.options);
        } else if (viewType == VIEW_TYPE_COUNTRY) {
            SelectorCountryCell cell = (SelectorCountryCell) holder.itemView;
            boolean needDivider = (position < items.size() - 1) && (position + 1 < items.size() - 1) && (items.get(position + 1).viewType != VIEW_TYPE_LETTER);
            cell.setCountry(item.country, needDivider);
            cell.setChecked(item.checked, false);
        } else if (viewType == VIEW_TYPE_PAD) {
            int height;
            if (item.padHeight >= 0) {
                height = item.padHeight;
            } else {
                height = (int) (AndroidUtilities.displaySize.y * .3f);
            }
            holder.itemView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
        } else if (viewType == VIEW_TYPE_LETTER) {
            SelectorLetterCell cell = (SelectorLetterCell) holder.itemView;
            cell.setLetter(item.text);
        } else if (viewType == VIEW_TYPE_NO_USERS) {
            try {
                ((StickerEmptyView) holder.itemView).stickerView.getImageReceiver().startAnimation();
            } catch (Exception ignore) {
            }
        } else if (viewType == VIEW_TYPE_TOP_SECTION) {
            GraySectionCell cell = (GraySectionCell) holder.itemView;
            if (TextUtils.equals(cell.getText(), item.text)) {
                cell.setRightText(item.subtext == null ? "" : item.subtext, true, item.callback);
            } else {
                cell.setText(Emoji.replaceWithRestrictedEmoji(item.text, cell.getTextView(), null));
                if (!TextUtils.isEmpty(item.subtext)) {
                    cell.setRightText(item.subtext, item.callback);
                }
            }
            topSectionCell = cell;
        } else if (viewType == VIEW_TYPE_BUTTON) {
            TextCell cell = (TextCell) holder.itemView;
            cell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
            cell.setTextAndIcon(item.text, item.resId, false);
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (items == null || position < 0) {
            return VIEW_TYPE_PAD;
        }
        return items.get(position).viewType;
    }

    @Override
    public int getItemCount() {
        return items == null ? 0 : items.size();
    }

    private RecyclerListView.Adapter realAdapter() {
        return listView.getAdapter();
    }

    @Override
    public void notifyItemChanged(int position) {
        realAdapter().notifyItemChanged(position + 1);
    }

    @Override
    public void notifyItemChanged(int position, @Nullable Object payload) {
        realAdapter().notifyItemChanged(position + 1, payload);
    }

    @Override
    public void notifyItemInserted(int position) {
        realAdapter().notifyItemInserted(position + 1);
    }

    @Override
    public void notifyItemMoved(int fromPosition, int toPosition) {
        realAdapter().notifyItemMoved(fromPosition + 1, toPosition);
    }

    @Override
    public void notifyItemRangeChanged(int positionStart, int itemCount) {
        realAdapter().notifyItemRangeChanged(positionStart + 1, itemCount);
    }

    @Override
    public void notifyItemRangeChanged(int positionStart, int itemCount, @Nullable Object payload) {
        realAdapter().notifyItemRangeChanged(positionStart + 1, itemCount, payload);
    }

    @Override
    public void notifyItemRangeInserted(int positionStart, int itemCount) {
        realAdapter().notifyItemRangeInserted(positionStart + 1, itemCount);
    }

    @Override
    public void notifyItemRangeRemoved(int positionStart, int itemCount) {
        realAdapter().notifyItemRangeRemoved(positionStart + 1, itemCount);
    }

    @Override
    public void notifyItemRemoved(int position) {
        realAdapter().notifyItemRemoved(position + 1);
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void notifyDataSetChanged() {
        realAdapter().notifyDataSetChanged();
    }

    public void notifyChangedLast() {
        if (items == null || items.isEmpty()) {
            return;
        }
        notifyItemChanged(items.size() - 1);
    }

    public static class Item extends AdapterWithDiffUtils.Item {
        public TLRPC.User user;
        public TLRPC.InputPeer peer;
        public TLRPC.Chat chat;
        public TLRPC.TL_help_country country;
        public CharSequence text, subtext;
        public int type;
        public int id;
        public int resId;
        public boolean checked;
        public int padHeight = -1;
        public View.OnClickListener callback;
        public View.OnClickListener options;

        private Item(int viewType, boolean selectable) {
            super(viewType, selectable);
        }

        public static Item asPad(int padHeight) {
            Item item = new Item(VIEW_TYPE_PAD, false);
            item.padHeight = padHeight;
            return item;
        }

        public static Item asButton(int id, int resId, String text) {
            Item item = new Item(VIEW_TYPE_BUTTON, false);
            item.id = id;
            item.resId = resId;
            item.text = text;
            return item;
        }

        public static Item asUser(TLRPC.User user, boolean checked) {
            Item item = new Item(VIEW_TYPE_USER, true);
            item.user = user;
            item.peer = null;
            item.chat = null;
            item.checked = checked;
            return item;
        }

        public Item withOptions(View.OnClickListener onClickListener) {
            this.options = onClickListener;
            return this;
        }

        public static Item asLetter(String letter) {
            Item item = new Item(VIEW_TYPE_LETTER, false);
            item.text = letter;
            return item;
        }

        public static Item asTopSection(CharSequence text) {
            Item item = new Item(VIEW_TYPE_TOP_SECTION, false);
            item.text = text;
            return item;
        }

        public Item withRightText(String rightText, View.OnClickListener whenRightTextClicked) {
            subtext = rightText;
            callback = whenRightTextClicked;
            return this;
        }

        public static Item asCountry(TLRPC.TL_help_country tlHelpCountry, boolean checked) {
            Item item = new Item(VIEW_TYPE_COUNTRY, true);
            item.country = tlHelpCountry;
            item.checked = checked;
            return item;
        }

        public static Item asPeer(TLRPC.InputPeer peer, boolean checked) {
            Item item = new Item(VIEW_TYPE_USER, true);
            item.peer = peer;
            item.user = null;
            item.chat = null;
            item.checked = checked;
            return item;
        }

        public static Item asChat(TLRPC.Chat chat, boolean checked) {
            Item item = new Item(VIEW_TYPE_USER, true);
            item.chat = chat;
            item.user = null;
            item.peer = null;
            item.checked = checked;
            return item;
        }

        public long getDialogId() {
            if (user != null) return user.id;
            if (chat != null) return -chat.id;
            if (peer != null) return DialogObject.getPeerDialogId(peer);
            return 0;
        }

        public static Item asNoUsers() {
            return new Item(VIEW_TYPE_NO_USERS, false);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Item i = (Item) o;
            if (viewType != i.viewType) {
                return false;
            }
            if (viewType == VIEW_TYPE_PAD && (padHeight != i.padHeight)) {
                return false;
            } else if (viewType == VIEW_TYPE_USER && (getDialogId() != i.getDialogId() || type != i.type)) {
                return false;
            } else if (viewType == VIEW_TYPE_COUNTRY && (country != i.country)) {
                return false;
            } else if (viewType == VIEW_TYPE_LETTER && (!TextUtils.equals(text, i.text))) {
                return false;
            } else if (viewType == VIEW_TYPE_TOP_SECTION && (!TextUtils.equals(text, i.text))) {
                return false;
            } else if (viewType == VIEW_TYPE_BUTTON && (!TextUtils.equals(text, i.text) || id != i.id || resId != i.resId)) {
                return false;
            }
            return true;
        }

        @Override
        protected boolean contentsEquals(AdapterWithDiffUtils.Item o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Item i = (Item) o;
            if (checked != i.checked) {
                return false;
            }
            if (viewType == VIEW_TYPE_TOP_SECTION) {
                return TextUtils.equals(subtext, i.subtext) && (callback == null) == (i.callback == null);
            }
            return true;
        }
    }
}
