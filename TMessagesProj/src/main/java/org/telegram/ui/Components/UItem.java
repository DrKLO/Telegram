package org.telegram.ui.Components;


import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.LongSparseArray;
import android.util.SparseIntArray;
import android.util.SparseLongArray;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stats;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Business.BusinessLinksActivity;
import org.telegram.ui.Business.QuickRepliesController;
import org.telegram.ui.Cells.SlideIntChooseView;
import org.telegram.ui.ChannelMonetizationLayout;
import org.telegram.ui.Components.ListView.AdapterWithDiffUtils;
import org.telegram.ui.StatisticActivity;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

public class UItem extends AdapterWithDiffUtils.Item {

    public View view;
    public int id;
    public boolean checked;
    public boolean collapsed;
    public boolean enabled = true;
    public int pad;
    public boolean hideDivider;
    public int iconResId;
    public CharSequence text, subtext, textValue;
    public CharSequence animatedText;
    public String[] texts;
    public boolean accent, red, transparent, locked;

    public boolean include;
    public long dialogId;
    public String chatType;
    public int flags;

    public int intValue;
    public long longValue;
    public Utilities.Callback<Integer> intCallback;

    public View.OnClickListener clickCallback;

    public Object object;
    public Object object2;

    public boolean withUsername = true;

    public UItem(int viewType, boolean selectable) {
        super(viewType, selectable);
    }

    public UItem(int viewType) {
        super(viewType, false);
    }

    public UItem(int viewType, Object object) {
        super(viewType, false);
        this.object = object;
    }

    public static UItem asCustom(int id, View view) {
        UItem i = new UItem(UniversalAdapter.VIEW_TYPE_CUSTOM, false);
        i.id = id;
        i.view = view;
        return i;
    }
    public static UItem asCustom(View view) {
        UItem i = new UItem(UniversalAdapter.VIEW_TYPE_CUSTOM, false);
        i.view = view;
        return i;
    }

    public static UItem asFullyCustom(View view) {
        UItem i = new UItem(UniversalAdapter.VIEW_TYPE_FULLY_CUSTOM, false);
        i.view = view;
        return i;
    }

    public static UItem asFullscreenCustom(View view, int minusHeight) {
        UItem i = new UItem(UniversalAdapter.VIEW_TYPE_FULLSCREEN_CUSTOM, false);
        i.view = view;
        i.intValue = minusHeight;
        return i;
    }

    public static UItem asHeader(CharSequence text) {
        UItem i = new UItem(UniversalAdapter.VIEW_TYPE_HEADER, false);
        i.text = text;
        return i;
    }

    public static UItem asAnimatedHeader(int id, CharSequence text) {
        UItem i = new UItem(UniversalAdapter.VIEW_TYPE_ANIMATED_HEADER, false);
        i.id = id;
        i.animatedText = text;
        return i;
    }

    public static UItem asLargeHeader(CharSequence text) {
        UItem i = new UItem(UniversalAdapter.VIEW_TYPE_LARGE_HEADER, false);
        i.text = text;
        return i;
    }

    public static UItem asBlackHeader(CharSequence text) {
        UItem i = new UItem(UniversalAdapter.VIEW_TYPE_BLACK_HEADER, false);
        i.text = text;
        return i;
    }

    public static UItem asTopView(CharSequence text, String setName, String emoji) {
        UItem i = new UItem(UniversalAdapter.VIEW_TYPE_TOPVIEW, false);
        i.text = text;
        i.subtext = setName;
        i.textValue = emoji;
        return i;
    }

    public static UItem asTopView(CharSequence text, int lottieResId) {
        UItem i = new UItem(UniversalAdapter.VIEW_TYPE_TOPVIEW, false);
        i.text = text;
        i.iconResId = lottieResId;
        return i;
    }

    public static UItem asButton(int id, CharSequence text) {
        UItem i = new UItem(UniversalAdapter.VIEW_TYPE_TEXT, false);
        i.id = id;
        i.text = text;
        return i;
    }

    public static UItem asButton(int id, int iconResId, CharSequence text) {
        UItem i = new UItem(UniversalAdapter.VIEW_TYPE_TEXT, false);
        i.id = id;
        i.iconResId = iconResId;
        i.text = text;
        return i;
    }

    public static UItem asButton(int id, Drawable icon, CharSequence text) {
        UItem i = new UItem(UniversalAdapter.VIEW_TYPE_TEXT, false);
        i.id = id;
        i.object = icon;
        i.text = text;
        return i;
    }

    public static UItem asButton(int id, CharSequence text, CharSequence value) {
        UItem i = new UItem(UniversalAdapter.VIEW_TYPE_TEXT, false);
        i.id = id;
        i.text = text;
        i.textValue = value;
        return i;
    }

    public static UItem asButton(int id, int iconResId, CharSequence text, CharSequence value) {
        UItem i = new UItem(UniversalAdapter.VIEW_TYPE_TEXT, false);
        i.id = id;
        i.iconResId = iconResId;
        i.text = text;
        i.textValue = value;
        return i;
    }

    public static UItem asStickerButton(int id, CharSequence text, TLRPC.Document sticker) {
        UItem i = new UItem(UniversalAdapter.VIEW_TYPE_TEXT, false);
        i.id = id;
        i.text = text;
        i.object = sticker;
        return i;
    }
    public static UItem asStickerButton(int id, CharSequence text, String stickerPath) {
        UItem i = new UItem(UniversalAdapter.VIEW_TYPE_TEXT, false);
        i.id = id;
        i.text = text;
        i.object = stickerPath;
        return i;
    }

    public static UItem asRippleCheck(int id, CharSequence text) {
        UItem i = new UItem(UniversalAdapter.VIEW_TYPE_CHECKRIPPLE, false);
        i.id = id;
        i.text = text;
        return i;
    }

    public static UItem asCheck(int id, CharSequence text) {
        UItem i = new UItem(UniversalAdapter.VIEW_TYPE_CHECK, false);
        i.id = id;
        i.text = text;
        return i;
    }

    public static UItem asRadio(int id, CharSequence text) {
        UItem i = new UItem(UniversalAdapter.VIEW_TYPE_RADIO, false);
        i.id = id;
        i.text = text;
        return i;
    }

    public static UItem asRadio(int id, CharSequence text, CharSequence value) {
        UItem i = new UItem(UniversalAdapter.VIEW_TYPE_RADIO, false);
        i.id = id;
        i.text = text;
        i.textValue = value;
        return i;
    }

    public static UItem asButtonCheck(int id, CharSequence text, CharSequence subtext) {
        UItem i = new UItem(UniversalAdapter.VIEW_TYPE_TEXT_CHECK, false);
        i.id = id;
        i.text = text;
        i.subtext = subtext;
        return i;
    }

    public static UItem asShadow(CharSequence text) {
        UItem i = new UItem(UniversalAdapter.VIEW_TYPE_SHADOW, false);
        i.text = text;
        return i;
    }

    public static UItem asLargeShadow(CharSequence text) {
        UItem i = new UItem(UniversalAdapter.VIEW_TYPE_LARGE_SHADOW, false);
        i.text = text;
        return i;
    }

    public static UItem asCenterShadow(CharSequence text) {
        UItem i = new UItem(UniversalAdapter.VIEW_TYPE_SHADOW, false);
        i.text = text;
        i.accent = true;
        return i;
    }

    public static UItem asProceedOverview(ChannelMonetizationLayout.ProceedOverview value) {
        UItem i = new UItem(UniversalAdapter.VIEW_TYPE_PROCEED_OVERVIEW, false);
        i.object = value;
        return i;
    }

    public static UItem asShadow(int id, CharSequence text) {
        UItem i = new UItem(UniversalAdapter.VIEW_TYPE_SHADOW, false);
        i.id = id;
        i.text = text;
        return i;
    }

    public static UItem asFilterChat(boolean include, long dialogId) {
        UItem item = new UItem(UniversalAdapter.VIEW_TYPE_FILTER_CHAT, false);
        item.include = include;
        item.dialogId = dialogId;
        return item;
    }

    public static UItem asFilterChat(boolean include, CharSequence name, String chatType, int flags) {
        UItem item = new UItem(UniversalAdapter.VIEW_TYPE_FILTER_CHAT, false);
        item.include = include;
        item.text = name;
        item.chatType = chatType;
        item.flags = flags;
        return item;
    }

    public static UItem asAddChat(Long dialogId) {
        UItem item = new UItem(UniversalAdapter.VIEW_TYPE_USER_ADD, false);
        item.dialogId = dialogId;
        return item;
    }

    public static UItem asSlideView(String[] choices, int chosen, Utilities.Callback<Integer> whenChose) {
        UItem item = new UItem(UniversalAdapter.VIEW_TYPE_SLIDE, false);
        item.texts = choices;
        item.intValue = chosen;
        item.intCallback = whenChose;
        return item;
    }

    public static UItem asIntSlideView(
        int style,
        int minStringResId, int min,
        int valueMinStringResId, int valueStringResId, int valueMaxStringResId, int value,
        int maxStringResId, int max,
        Utilities.Callback<Integer> whenChose
    ) {
        UItem item = new UItem(UniversalAdapter.VIEW_TYPE_INTSLIDE, false);
        item.intValue = value;
        item.intCallback = whenChose;
        item.object = SlideIntChooseView.Options.make(style, min, minStringResId, valueMinStringResId, valueStringResId, valueMaxStringResId, max, maxStringResId);
        return item;
    }

    public static UItem asQuickReply(QuickRepliesController.QuickReply quickReply) {
        UItem item = new UItem(UniversalAdapter.VIEW_TYPE_QUICK_REPLY, false);
        item.object = quickReply;
        return item;
    }

    public static UItem asLargeQuickReply(QuickRepliesController.QuickReply quickReply) {
        UItem item = new UItem(UniversalAdapter.VIEW_TYPE_LARGE_QUICK_REPLY, false);
        item.object = quickReply;
        return item;
    }

    public static UItem asBusinessChatLink(BusinessLinksActivity.BusinessLinkWrapper businessLink) {
        UItem item = new UItem(UniversalAdapter.VIEW_TYPE_BUSINESS_LINK, false);
        item.object = businessLink;
        return item;
    }

    public static UItem asChart(int type, int stats_dc, StatisticActivity.ChartViewData data) {
        UItem item = new UItem(UniversalAdapter.VIEW_TYPE_CHART_LINEAR + type, false);
        item.intValue = stats_dc;
        item.object = data;
        return item;
    }

    public static UItem asTransaction(TL_stats.BroadcastRevenueTransaction transaction) {
        UItem item = new UItem(UniversalAdapter.VIEW_TYPE_TRANSACTION, false);
        item.object = transaction;
        return item;
    }

    public static UItem asRadioUser(Object object) {
        UItem item = new UItem(UniversalAdapter.VIEW_TYPE_RADIO_USER, false);
        item.object = object;
        return item;
    }

    public static UItem asSpace(int height) {
        UItem item = new UItem(UniversalAdapter.VIEW_TYPE_SPACE, false);
        item.intValue = height;
        return item;
    }

    public static UItem asRoundCheckbox(int id, CharSequence text) {
        UItem item = new UItem(UniversalAdapter.VIEW_TYPE_ROUND_CHECKBOX, false);
        item.id = id;
        item.text = text;
        return item;
    }

    public static UItem asRoundGroupCheckbox(int id, CharSequence text, CharSequence subtext) {
        UItem item = new UItem(UniversalAdapter.VIEW_TYPE_ROUND_GROUP_CHECKBOX, false);
        item.id = id;
        item.text = text;
        item.animatedText = subtext;
        return item;
    }

    public static UItem asUserGroupCheckbox(int id, CharSequence text, CharSequence subtext) {
        UItem item = new UItem(UniversalAdapter.VIEW_TYPE_USER_GROUP_CHECKBOX, false);
        item.id = id;
        item.text = text;
        item.animatedText = subtext;
        return item;
    }

    public static UItem asUserCheckbox(int id, TLObject user) {
        UItem item = new UItem(UniversalAdapter.VIEW_TYPE_USER_CHECKBOX, false);
        item.id = id;
        item.object = user;
        return item;
    }

    public static UItem asShadowCollapseButton(int id, CharSequence text) {
        UItem item = new UItem(UniversalAdapter.VIEW_TYPE_SHADOW_COLLAPSE_BUTTON, false);
        item.id = id;
        item.animatedText = text;
        return item;
    }

    public static UItem asSwitch(int id, CharSequence text) {
        UItem item = new UItem(UniversalAdapter.VIEW_TYPE_SWITCH, false);
        item.id = id;
        item.text = text;
        return item;
    }

    public static UItem asExpandableSwitch(int id, CharSequence text, CharSequence subText) {
        UItem item = new UItem(UniversalAdapter.VIEW_TYPE_EXPANDABLE_SWITCH, false);
        item.id = id;
        item.text = text;
        item.animatedText = subText;
        return item;
    }

    public static UItem asGraySection(CharSequence text) {
        UItem item = new UItem(UniversalAdapter.VIEW_TYPE_GRAY_SECTION, false);
        item.text = text;
        return item;
    }

    public static UItem asGraySection(CharSequence text, CharSequence button, View.OnClickListener onClickListener) {
        UItem item = new UItem(UniversalAdapter.VIEW_TYPE_GRAY_SECTION, false);
        item.text = text;
        item.subtext = button;
        item.clickCallback = onClickListener;
        return item;
    }

    public static UItem asProfileCell(TLObject obj) {
        UItem item = new UItem(UniversalAdapter.VIEW_TYPE_PROFILE_CELL, false);
        item.object = obj;
        return item;
    }

    public static UItem asSearchMessage(MessageObject messageObject) {
        UItem item = new UItem(UniversalAdapter.VIEW_TYPE_SEARCH_MESSAGE, false);
        item.object = messageObject;
        return item;
    }

    public static UItem asFlicker(int type) {
        UItem item = new UItem(UniversalAdapter.VIEW_TYPE_FLICKER, false);
        item.intValue = type;
        return item;
    }

    public static UItem asFlicker(int id, int type) {
        UItem item = new UItem(UniversalAdapter.VIEW_TYPE_FLICKER, false);
        item.id = id;
        item.intValue = type;
        return item;
    }


    public UItem withUsername(boolean value) {
        withUsername = value;
        return this;
    }

    public UItem setCloseIcon(View.OnClickListener onCloseClick) {
        clickCallback = onCloseClick;
        return this;
    }

    public UItem setClickCallback(View.OnClickListener clickCallback) {
        this.clickCallback = clickCallback;
        return this;
    }

    public UItem setChecked(boolean checked) {
        this.checked = checked;
        if (viewType == UniversalAdapter.VIEW_TYPE_FILTER_CHAT) {
            viewType = UniversalAdapter.VIEW_TYPE_FILTER_CHAT_CHECK;
        }
        return this;
    }

    public UItem setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
        return this;
    }

    public UItem setPad(int pad) {
        this.pad = pad;
        return this;
    }

    public UItem pad() {
        this.pad = 1;
        return this;
    }

    public UItem setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public UItem setLocked(boolean locked) {
        this.locked = locked;
        return this;
    }

    public UItem locked() {
        this.locked = true;
        return this;
    }

    public UItem red() {
        this.red = true;
        return this;
    }

    public UItem accent() {
        this.accent = true;
        return this;
    }

    public <F extends UItemFactory<?>> boolean instanceOf(Class<F> factoryClass) {
        if (viewType < factoryViewTypeStartsWith) return false;
        if (factoryInstances == null) return false;
        UItemFactory<?> factory = factoryInstances.get(factoryClass);
        if (factory == null) return false;
        return factory.viewType == viewType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UItem item = (UItem) o;
        if (viewType != item.viewType)
            return false;
        if (viewType == UniversalAdapter.VIEW_TYPE_USER_GROUP_CHECKBOX ||
                viewType == UniversalAdapter.VIEW_TYPE_ROUND_CHECKBOX) {
            return id == item.id;
        }
        if (viewType == UniversalAdapter.VIEW_TYPE_GRAY_SECTION) {
            return TextUtils.equals(text, item.text);
        }
        if (viewType >= UItem.factoryViewTypeStartsWith) {
            UItemFactory<?> factory = findFactory(viewType);
            if (factory != null) {
                return factory.equals(this, item);
            }
        }
        return itemEquals(item);
    }

    @Override
    protected boolean contentsEquals(AdapterWithDiffUtils.Item o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UItem item = (UItem) o;
        if (viewType != item.viewType)
            return false;
        if (viewType == UniversalAdapter.VIEW_TYPE_GRAY_SECTION) {
            return TextUtils.equals(text, item.text) && TextUtils.equals(subtext, item.subtext);
        }
        if (viewType == UniversalAdapter.VIEW_TYPE_ROUND_CHECKBOX ||
            viewType == UniversalAdapter.VIEW_TYPE_USER_CHECKBOX) {
            return id == item.id && TextUtils.equals(text, item.text) && checked == item.checked;
        }
        if (viewType >= UItem.factoryViewTypeStartsWith) {
            UItemFactory<?> factory = findFactory(viewType);
            if (factory != null) {
                return factory.contentsEquals(this, item);
            }
        }
        return itemContentEquals(item);
    }

    public boolean itemEquals(UItem item) {
        return (
            id == item.id &&
            pad == item.pad &&
            dialogId == item.dialogId &&
            iconResId == item.iconResId &&
            hideDivider == item.hideDivider &&
            transparent == item.transparent &&
            red == item.red &&
            locked == item.locked &&
            accent == item.accent &&
            TextUtils.equals(text, item.text) &&
            TextUtils.equals(subtext, item.subtext) &&
            TextUtils.equals(textValue, item.textValue) &&
            view == item.view &&
            intValue == item.intValue &&
            longValue == item.longValue &&
            Objects.equals(object, item.object) &&
            Objects.equals(object2, item.object2)
        );
    }

    public boolean itemContentEquals(UItem item) {
        return super.contentsEquals(item);
    }

    public static int factoryViewTypeStartsWith = 10_000;
    private static int factoryViewType = 10_000;
    public static abstract class UItemFactory<V extends View> {
        public static void setup(UItemFactory factory) {
            if (factoryInstances == null) factoryInstances = new HashMap<>();
            if (factories == null) factories = new LongSparseArray<>();
            final Class factoryClass = factory.getClass();
            if (!factoryInstances.containsKey(factoryClass)) {
                factoryInstances.put(factoryClass, factory);
                factories.put(factory.viewType, factory);
            }
        };

        public final int viewType;

        private ArrayList<V> cache;

        public UItemFactory() {
            viewType = factoryViewType++;
        }

        public void precache(BaseFragment fragment, int count) {
            precache(fragment.getContext(), fragment.getCurrentAccount(), fragment.getClassGuid(), fragment.getResourceProvider(), count);
        }

        public void precache(Context context, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider, int count) {
            if (context == null) return;
            if (cache == null) cache = new ArrayList<>();
            for (int i = 0; i < cache.size() - count; ++i) {
                cache.add(createView(context, currentAccount, classGuid, resourcesProvider));
            }
        }

        protected V getCached() {
            if (cache != null && !cache.isEmpty()) {
                return cache.remove(0);
            }
            return null;
        }

        public V createView(Context context, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return null;
        }

        public void bindView(View view, UItem item, boolean divider) {

        }

        public boolean isShadow() {
            return false;
        }

        public boolean isClickable() {
            return true;
        }

        public boolean equals(UItem a, UItem b) {
            return a.itemEquals(b);
        }

        public boolean contentsEquals(UItem a, UItem b) {
            return a.itemContentEquals(b);
        }
    }

    private static LongSparseArray<UItemFactory<?>> factories;
    private static HashMap<Class<? extends UItemFactory<?>>, UItemFactory<?>> factoryInstances;
    public static UItemFactory<?> findFactory(int viewType) {
        if (factories == null) return null;
        return factories.get(viewType);
    }

    public static <F extends UItemFactory<?>> UItem ofFactory(Class<F> factoryClass) {
        UItem item = new UItem(getFactory(factoryClass).viewType, false);
        return item;
    }

    public static <F extends UItemFactory<?>> UItemFactory<?> getFactory(Class<F> factoryClass) {
        if (factoryInstances == null) factoryInstances = new HashMap<>();
        if (factories == null) factories = new LongSparseArray<>();
        UItemFactory<?> factory = factoryInstances.get(factoryClass);
        if (factory == null) throw new RuntimeException("UItemFactory was not setuped: " + factoryClass);
        return factory;
    }
}
