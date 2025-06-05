package org.telegram.ui.Components.Premium.boosts.adapters;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Components.ListView.AdapterWithDiffUtils;
import org.telegram.ui.Components.Premium.boosts.BoostRepository;
import org.telegram.ui.Components.Premium.boosts.cells.AddChannelCell;
import org.telegram.ui.Components.Premium.boosts.cells.BoostTypeCell;
import org.telegram.ui.Components.Premium.boosts.cells.BoostTypeSingleCell;
import org.telegram.ui.Components.Premium.boosts.cells.ChatCell;
import org.telegram.ui.Components.Premium.boosts.cells.DateEndCell;
import org.telegram.ui.Components.Premium.boosts.cells.EnterPrizeCell;
import org.telegram.ui.Components.Premium.boosts.cells.HeaderCell;
import org.telegram.ui.Components.Premium.boosts.cells.ParticipantsTypeCell;
import org.telegram.ui.Components.Premium.boosts.cells.DurationCell;
import org.telegram.ui.Components.Premium.boosts.cells.SliderCell;
import org.telegram.ui.Components.Premium.boosts.cells.StarGiveawayOptionCell;
import org.telegram.ui.Components.Premium.boosts.cells.SubtitleWithCounterCell;
import org.telegram.ui.Components.Premium.boosts.cells.SwitcherCell;
import org.telegram.ui.Components.Premium.boosts.cells.TextInfoCell;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SlideChooseView;
import org.telegram.ui.Stars.StarsIntroActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class BoostAdapter extends AdapterWithDiffUtils {

    public static final int
            HOLDER_TYPE_HEADER = 0,
            HOLDER_TYPE_BOOST_TYPE = 2,
            HOLDER_TYPE_EMPTY = 3,
            HOLDER_TYPE_SIMPLE_DIVIDER = 4,
            HOLDER_TYPE_SLIDER = 5,
            HOLDER_TYPE_SUBTITLE = 6,
            HOLDER_TYPE_TEXT_DIVIDER = 7,
            HOLDER_TYPE_ADD_CHANNEL = 8,
            HOLDER_TYPE_CHAT = 9,
            HOLDER_TYPE_DATE_END = 10,
            HOLDER_TYPE_PARTICIPANTS = 11,
            HOLDER_TYPE_DURATION = 12,
            HOLDER_TYPE_SUBTITLE_WITH_COUNTER = 13,
            HOLDER_TYPE_SINGLE_BOOST_TYPE = 14,
            HOLDER_TYPE_SWITCHER = 15,
            HOLDER_TYPE_ENTER_PRIZE = 16,
            HOLDER_TYPE_STAR_OPTION = 17,
            HOLDER_TYPE_EXPAND_OPTIONS = 18;

    private final Theme.ResourcesProvider resourcesProvider;
    private List<Item> items = new ArrayList<>();
    private RecyclerListView recyclerListView;
    private SlideChooseView.Callback sliderCallback;
    private ChatCell.ChatDeleteListener chatDeleteListener;
    private HeaderCell headerCell;
    private EnterPrizeCell.AfterTextChangedListener afterTextChangedListener;
    private TLRPC.Chat currentChat;
    private HashMap<Long, Integer> chatsParticipantsCount = new HashMap<>();

    public BoostAdapter(Theme.ResourcesProvider resourcesProvider) {
        this.resourcesProvider = resourcesProvider;
        BoostRepository.loadParticipantsCount(result -> {
            chatsParticipantsCount.clear();
            chatsParticipantsCount.putAll(result);
        });
    }

    public void setItems(TLRPC.Chat currentChat, List<Item> items, RecyclerListView recyclerListView, SlideChooseView.Callback sliderCallback, ChatCell.ChatDeleteListener chatDeleteListener, EnterPrizeCell.AfterTextChangedListener afterTextChangedListener) {
        this.items = items;
        this.currentChat = currentChat;
        this.recyclerListView = recyclerListView;
        this.sliderCallback = sliderCallback;
        this.chatDeleteListener = chatDeleteListener;
        this.afterTextChangedListener = afterTextChangedListener;
    }

    private int getParticipantsCount(TLRPC.Chat chat) {
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

    public void updateBoostCounter(int value) {
        for (int i = 0; i < recyclerListView.getChildCount(); ++i) {
            View child = recyclerListView.getChildAt(i);
            if (child instanceof SubtitleWithCounterCell) {
                ((SubtitleWithCounterCell) child).updateCounter(true, value);
            }
            if (child instanceof ChatCell) {
                ChatCell chatCell = ((ChatCell) child);
                chatCell.setCounter(value, getParticipantsCount(chatCell.getChat()));
            }
        }
        notifyItemChanged(8); //update main channel
        notifyItemRangeChanged(items.size() - 12, 12); //updates all prices
    }

    public void notifyAllVisibleTextDividers() {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).viewType == HOLDER_TYPE_TEXT_DIVIDER) {
                notifyItemChanged(i);
            }
        }
    }

    public void notifyAdditionalPrizeItem(boolean checked) {
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            if (item.viewType == HOLDER_TYPE_SWITCHER && item.subType == SwitcherCell.TYPE_ADDITION_PRIZE) {
                if (checked) {
                    notifyItemInserted(i + 1);
                } else {
                    notifyItemRemoved(i + 1);
                }
                break;
            }
        }
    }

    public void setPausedStars(boolean paused) {
        if (headerCell != null) {
            headerCell.setPaused(paused);
        }
    }

    private RecyclerListView.Adapter realAdapter() {
        return recyclerListView.getAdapter();
    }

//    @Override
//    public void notifyItemChanged(int position) {
//        realAdapter().notifyItemChanged(position + 1);
//    }
//
//    @Override
//    public void notifyItemChanged(int position, @Nullable Object payload) {
//        realAdapter().notifyItemChanged(position + 1, payload);
//    }
//
//    @Override
//    public void notifyItemInserted(int position) {
//        realAdapter().notifyItemInserted(position + 1);
//    }
//
//    @Override
//    public void notifyItemMoved(int fromPosition, int toPosition) {
//        realAdapter().notifyItemMoved(fromPosition + 1, toPosition);
//    }
//
//    @Override
//    public void notifyItemRangeChanged(int positionStart, int itemCount) {
//        realAdapter().notifyItemRangeChanged(positionStart + 1, itemCount);
//    }
//
//    @Override
//    public void notifyItemRangeChanged(int positionStart, int itemCount, @Nullable Object payload) {
//        realAdapter().notifyItemRangeChanged(positionStart + 1, itemCount, payload);
//    }
//
//    @Override
//    public void notifyItemRangeInserted(int positionStart, int itemCount) {
//        realAdapter().notifyItemRangeInserted(positionStart + 1, itemCount);
//    }
//
//    @Override
//    public void notifyItemRangeRemoved(int positionStart, int itemCount) {
//        realAdapter().notifyItemRangeRemoved(positionStart + 1, itemCount);
//    }
//
//    @Override
//    public void notifyItemRemoved(int position) {
//        realAdapter().notifyItemRemoved(position + 1);
//    }
//
//    @SuppressLint("NotifyDataSetChanged")
//    @Override
//    public void notifyDataSetChanged() {
//        realAdapter().notifyDataSetChanged();
//    }

    @Override
    public boolean isEnabled(RecyclerView.ViewHolder holder) {
        int itemViewType = holder.getItemViewType();
        return itemViewType == HOLDER_TYPE_BOOST_TYPE
                || itemViewType == HOLDER_TYPE_PARTICIPANTS
                || itemViewType == HOLDER_TYPE_ADD_CHANNEL
                || itemViewType == HOLDER_TYPE_DATE_END
                || itemViewType == HOLDER_TYPE_SWITCHER
                || itemViewType == HOLDER_TYPE_DURATION
                || itemViewType == HOLDER_TYPE_STAR_OPTION
                || itemViewType == HOLDER_TYPE_EXPAND_OPTIONS;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        Context context = parent.getContext();
        switch (viewType) {
            default:
            case HOLDER_TYPE_HEADER:
                view = new HeaderCell(context, resourcesProvider);
                break;
            case HOLDER_TYPE_BOOST_TYPE:
                view = new BoostTypeCell(context, resourcesProvider);
                break;
            case HOLDER_TYPE_SINGLE_BOOST_TYPE:
                view = new BoostTypeSingleCell(context, resourcesProvider);
                break;
            case HOLDER_TYPE_ENTER_PRIZE:
                view = new EnterPrizeCell(context, resourcesProvider);
                break;
            case HOLDER_TYPE_SWITCHER:
                SwitcherCell cell = new SwitcherCell(context, resourcesProvider);
                cell.setHeight(50);
                view = cell;
                break;
            case HOLDER_TYPE_EMPTY:
                view = new View(context);
                break;
            case HOLDER_TYPE_SIMPLE_DIVIDER:
                view = new ShadowSectionCell(context, 12, Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
                break;
            case HOLDER_TYPE_TEXT_DIVIDER:
                view = new TextInfoCell(context, resourcesProvider);
                break;
            case HOLDER_TYPE_ADD_CHANNEL:
                view = new AddChannelCell(context, resourcesProvider);
                break;
            case HOLDER_TYPE_EXPAND_OPTIONS:
                StarsIntroActivity.ExpandView expandView = new StarsIntroActivity.ExpandView(context, resourcesProvider);
                expandView.set(LocaleController.getString(R.string.NotifyMoreOptions), true, true, false);
                view = expandView;
                break;
            case HOLDER_TYPE_SLIDER:
                view = new SliderCell(context, resourcesProvider);
                break;
            case HOLDER_TYPE_SUBTITLE:
                view = new org.telegram.ui.Cells.HeaderCell(context, Theme.key_windowBackgroundWhiteBlueHeader, 21, 15, 3, false, resourcesProvider);
                view.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
                break;
            case HOLDER_TYPE_SUBTITLE_WITH_COUNTER:
                view = new SubtitleWithCounterCell(context, resourcesProvider);
                view.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
                break;
            case HOLDER_TYPE_CHAT:
                view = new ChatCell(context, resourcesProvider);
                break;
            case HOLDER_TYPE_DATE_END:
                view = new DateEndCell(context, resourcesProvider);
                break;
            case HOLDER_TYPE_PARTICIPANTS:
                view = new ParticipantsTypeCell(context, resourcesProvider);
                break;
            case HOLDER_TYPE_DURATION:
                view = new DurationCell(context, resourcesProvider);
                break;
            case HOLDER_TYPE_STAR_OPTION:
                view = new StarGiveawayOptionCell(context, resourcesProvider);
                break;
        }
        view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new RecyclerListView.Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        final int viewType = holder.getItemViewType();
        final BoostAdapter.Item item = items.get(position);
        switch (viewType) {
            case HOLDER_TYPE_HEADER: {
                headerCell = (HeaderCell) holder.itemView;
                headerCell.setBoostViaGifsText(currentChat);
                headerCell.setStars(item.boolValue);
                break;
            }
            case HOLDER_TYPE_BOOST_TYPE: {
                BoostTypeCell cell = (BoostTypeCell) holder.itemView;
                cell.setType(item.subType, item.intValue, (TLRPC.User) item.user, item.selectable);
                break;
            }
            case HOLDER_TYPE_SINGLE_BOOST_TYPE: {
                BoostTypeSingleCell cell = (BoostTypeSingleCell) holder.itemView;
                cell.setGiveaway((TL_stories.PrepaidGiveaway) item.user);
                break;
            }
            case HOLDER_TYPE_SLIDER: {
                SliderCell cell = (SliderCell) holder.itemView;
                cell.setValues(item.values, item.intValue);
                cell.setCallBack(sliderCallback);
                break;
            }
            case HOLDER_TYPE_SUBTITLE_WITH_COUNTER: {
                SubtitleWithCounterCell cell = (SubtitleWithCounterCell) holder.itemView;
                cell.setText(item.text);
                cell.updateCounter(true, item.intValue);
                break;
            }
            case HOLDER_TYPE_SUBTITLE: {
                org.telegram.ui.Cells.HeaderCell cell = (org.telegram.ui.Cells.HeaderCell) holder.itemView;
                cell.setText(item.text);
                break;
            }
            case HOLDER_TYPE_TEXT_DIVIDER: {
                TextInfoCell cell = (TextInfoCell) holder.itemView;
                cell.setText(item.text);
                cell.setBackground(item.boolValue);
                break;
            }
            case HOLDER_TYPE_CHAT: {
                ChatCell cell = (ChatCell) holder.itemView;
                if (item.peer != null) {
                    TLRPC.InputPeer peer = item.peer;
                    if (peer instanceof TLRPC.TL_inputPeerChat) {
                        TLRPC.Chat chat = MessagesController.getInstance(UserConfig.selectedAccount).getChat(peer.chat_id);
                        cell.setChat(chat, item.intValue, item.boolValue, getParticipantsCount(chat));
                    } else if (peer instanceof TLRPC.TL_inputPeerChannel) {
                        TLRPC.Chat chat = MessagesController.getInstance(UserConfig.selectedAccount).getChat(peer.channel_id);
                        cell.setChat(chat, item.intValue, item.boolValue, getParticipantsCount(chat));
                    }
                } else {
                    cell.setChat(item.chat, item.intValue, item.boolValue, getParticipantsCount(item.chat));
                }
                cell.setChatDeleteListener(chatDeleteListener);
                break;
            }
            case HOLDER_TYPE_PARTICIPANTS: {
                ParticipantsTypeCell cell = (ParticipantsTypeCell) holder.itemView;
                cell.setType(item.subType, item.selectable, item.boolValue, (List<TLRPC.TL_help_country>) item.user, currentChat);
                break;
            }
            case HOLDER_TYPE_DATE_END: {
                DateEndCell cell = (DateEndCell) holder.itemView;
                cell.setDate(item.longValue);
                break;
            }
            case HOLDER_TYPE_DURATION: {
                DurationCell cell = (DurationCell) holder.itemView;
                cell.setDuration(item.object, item.intValue, item.intValue2, item.longValue, item.text, item.boolValue, item.selectable);
                break;
            }
            case HOLDER_TYPE_STAR_OPTION: {
                StarGiveawayOptionCell cell = (StarGiveawayOptionCell) holder.itemView;
                cell.setOption(item.object == null ? null : (TL_stars.TL_starsGiveawayOption) item.object, item.intValue, item.longValue, item.selectable, item.boolValue);
                break;
            }
            case HOLDER_TYPE_SIMPLE_DIVIDER: {
                break;
            }
            case HOLDER_TYPE_ENTER_PRIZE: {
                EnterPrizeCell cell = (EnterPrizeCell) holder.itemView;
                cell.setCount(item.intValue);
                cell.setAfterTextChangedListener(afterTextChangedListener);
                break;
            }
            case HOLDER_TYPE_SWITCHER: {
                SwitcherCell cell = (SwitcherCell) holder.itemView;
                cell.setData(item.text, item.selectable, item.boolValue, item.subType);
                break;
            }
        }
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).viewType;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class Item extends AdapterWithDiffUtils.Item {
        public CharSequence text;
        public TLRPC.InputPeer peer;
        public TLRPC.Chat chat;
        public Object user;
        public boolean boolValue;
        public long longValue;
        public int intValue;
        public int intValue2;
        public int intValue3;
        public List<Integer> values;
        public float floatValue;
        public int subType;
        public Object object;

        private Item(int viewType, boolean selectable) {
            super(viewType, selectable);
        }

        public static Item asHeader() {
            return new Item(HOLDER_TYPE_HEADER, false);
        }

        public static Item asHeader(boolean golden) {
            Item item = new Item(HOLDER_TYPE_HEADER, false);
            item.boolValue = golden;
            return item;
        }

        public static Item asDivider() {
            return new Item(HOLDER_TYPE_SIMPLE_DIVIDER, false);
        }

        public static Item asDivider(CharSequence text, boolean onlyTopDivider) {
            Item item = new Item(HOLDER_TYPE_TEXT_DIVIDER, false);
            item.text = text;
            item.boolValue = onlyTopDivider;
            return item;
        }

        public static Item asChat(TLRPC.Chat chat, boolean removable, int count) {
            Item item = new Item(HOLDER_TYPE_CHAT, false);
            item.chat = chat;
            item.peer = null;
            item.boolValue = removable;
            item.intValue = count;
            return item;
        }

        public static Item asPeer(TLRPC.InputPeer peer, boolean removable, int count) {
            Item item = new Item(HOLDER_TYPE_CHAT, false);
            item.peer = peer;
            item.chat = null;
            item.boolValue = removable;
            item.intValue = count;
            return item;
        }

        public static Item asEnterPrize(int count) {
            Item item = new Item(HOLDER_TYPE_ENTER_PRIZE, false);
            item.intValue = count;
            return item;
        }

        public static Item asSwitcher(CharSequence text, boolean isSelected, boolean needDivider, int subType) {
            Item item = new Item(HOLDER_TYPE_SWITCHER, isSelected);
            item.text = text;
            item.boolValue = needDivider;
            item.subType = subType;
            return item;
        }

        public static Item asSingleBoost(Object user) {
            Item item = new Item(HOLDER_TYPE_SINGLE_BOOST_TYPE, false);
            item.user = user;
            return item;
        }

        public static Item asBoost(int subType, int count, Object user, int selectedSubType) {
            Item item = new Item(HOLDER_TYPE_BOOST_TYPE, selectedSubType == subType);
            item.subType = subType;
            item.intValue = count;
            item.user = user;
            return item;
        }

        public static Item asDateEnd(long time) {
            Item item = new Item(HOLDER_TYPE_DATE_END, false);
            item.longValue = time;
            return item;
        }

        public static Item asSlider(List<Integer> values, int selected) {
            Item item = new Item(HOLDER_TYPE_SLIDER, false);
            item.values = values;
            item.intValue = selected;
            return item;
        }

        public static Item asAddChannel() {
            return new Item(HOLDER_TYPE_ADD_CHANNEL, false);
        }

        public static Item asExpandOptions() {
            return new Item(HOLDER_TYPE_EXPAND_OPTIONS, false);
        }

        public static Item asSubTitle(CharSequence text) {
            Item item = new Item(HOLDER_TYPE_SUBTITLE, false);
            item.text = text;
            return item;
        }

        public static Item asSubTitleWithCounter(CharSequence text, int counter) {
            Item item = new Item(HOLDER_TYPE_SUBTITLE_WITH_COUNTER, false);
            item.text = text;
            item.intValue = counter;
            return item;
        }

        public static Item asDuration(Object code, int months, int count, long price, int selectedMonths, String currency, boolean needDivider) {
            Item item = new Item(HOLDER_TYPE_DURATION, months == selectedMonths);
            item.intValue = months;
            item.intValue2 = count;
            item.longValue = price;
            item.boolValue = needDivider;
            item.text = currency;
            item.object = code;
            return item;
        }

        public static Item asOption(TL_stars.TL_starsGiveawayOption option, int index, long starsPerUser, boolean selected, boolean needDivider) {
            Item item = new Item(HOLDER_TYPE_STAR_OPTION, selected);
            item.intValue = index;
            item.longValue = starsPerUser;
            item.object = option;
            item.boolValue = needDivider;
            return item;
        }

        public static Item asParticipants(int subType, int selectedSubType, boolean needDivider, List<TLObject> countries) {
            Item item = new Item(HOLDER_TYPE_PARTICIPANTS, selectedSubType == subType);
            item.subType = subType;
            item.boolValue = needDivider;
            item.user = countries;
            return item;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Item i = (Item) o;
            if (viewType != i.viewType) {
                return false;
            }
            if (viewType == HOLDER_TYPE_HEADER) {
                return true;
            }
            if (viewType == HOLDER_TYPE_STAR_OPTION) {
                return intValue == i.intValue && object == i.object;
            }
            if (viewType == HOLDER_TYPE_SLIDER) {
                return eq(values, i.values);
            }
            if (viewType == HOLDER_TYPE_SUBTITLE_WITH_COUNTER) {
                return TextUtils.equals(text, i.text);
            }
            if (chat != i.chat || user != i.user || peer != i.peer || object != i.object
                    || boolValue != i.boolValue
                    || intValue != i.intValue || intValue2 != i.intValue2 || intValue3 != i.intValue3
                    || longValue != i.longValue
                    || subType != i.subType
                    || floatValue != i.floatValue
                    || !TextUtils.equals(text, i.text)) {
                return false;
            }
            return true;
        }

        public static boolean eq(List<Integer> a, List<Integer> b) {
            if (a == null && b == null) return true;
            if (a == null || b == null) return false;
            if (a.size() != b.size()) return false;
            for (int i = 0; i < a.size(); ++i) {
                if ((int) a.get(i) != (int) b.get(i))
                    return false;
            }
            return true;
        }

        @Override
        protected boolean contentsEquals(AdapterWithDiffUtils.Item item) {
            if (this == item) return true;
            if (item == null || getClass() != item.getClass()) return false;
            Item i = (Item) item;
            if (i.viewType != viewType) return false;
            if (viewType == HOLDER_TYPE_HEADER) {
                return boolValue == i.boolValue;
            }
            if (i.viewType == HOLDER_TYPE_STAR_OPTION) {
                return intValue == i.intValue && longValue == i.longValue && object == i.object && boolValue == i.boolValue && selectable == i.selectable;
            }
            if (viewType == HOLDER_TYPE_SLIDER) {
                return intValue == i.intValue && eq(values, i.values);
            }
            if (viewType == HOLDER_TYPE_SUBTITLE_WITH_COUNTER) {
                return intValue == i.intValue && TextUtils.equals(text, i.text);
            }
            return false;
        }
    }
}
