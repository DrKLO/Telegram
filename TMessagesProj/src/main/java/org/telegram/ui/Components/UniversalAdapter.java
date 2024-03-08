package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Business.QuickRepliesActivity;
import org.telegram.ui.Business.QuickRepliesController;
import org.telegram.ui.Cells.DialogRadioCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.NotificationsCheckCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.Components.ListView.AdapterWithDiffUtils;

import java.util.ArrayList;

public class UniversalAdapter extends AdapterWithDiffUtils {

    public static final int VIEW_TYPE_CUSTOM = -1;

    public static final int VIEW_TYPE_HEADER = 0;
    public static final int VIEW_TYPE_TOPVIEW = 1;

    public static final int VIEW_TYPE_TEXT = 2;
    public static final int VIEW_TYPE_CHECK = 3;
    public static final int VIEW_TYPE_TEXT_CHECK = 4;
    public static final int VIEW_TYPE_ICON_TEXT_CHECK = 5;
    public static final int VIEW_TYPE_SHADOW = 6;
    public static final int VIEW_TYPE_CHECKRIPPLE = 7;
    public static final int VIEW_TYPE_RADIO = 8;
    public static final int VIEW_TYPE_FILTER_CHAT = 9;
    public static final int VIEW_TYPE_USER_ADD = 10;
    public static final int VIEW_TYPE_SLIDE = 11;
    public static final int VIEW_TYPE_QUICK_REPLY = 12;
    public static final int VIEW_TYPE_LARGE_QUICK_REPLY = 13;

    private final Context context;
    private final int currentAccount;
    private final Utilities.Callback2<ArrayList<UItem>, UniversalAdapter> fillItems;
    private final Theme.ResourcesProvider resourcesProvider;

    private final ArrayList<UItem> oldItems = new ArrayList<>();
    private final ArrayList<UItem> items = new ArrayList<>();

    public UniversalAdapter(Context context, int currentAccount, Utilities.Callback2<ArrayList<UItem>, UniversalAdapter> fillItems, Theme.ResourcesProvider resourcesProvider) {
        super();
        this.context = context;
        this.currentAccount = currentAccount;
        this.fillItems = fillItems;
        this.resourcesProvider = resourcesProvider;
        update(false);
    }


    private static class Section {
        public int start, end;
        public boolean contains(int position) {
            return position >= start && position <= end;
        }
    }
    private final ArrayList<Section> whiteSections = new ArrayList<>();
    private final ArrayList<Section> reorderSections = new ArrayList<>();
    private Section currentWhiteSection, currentReorderSection;
    public void whiteSectionStart() {
        currentWhiteSection = new Section();
        currentWhiteSection.start = items.size();
        currentWhiteSection.end = -1;
        whiteSections.add(currentWhiteSection);
    }
    public void whiteSectionEnd() {
        if (currentWhiteSection != null) {
            currentWhiteSection.end = Math.max(0, items.size() - 1);
        }
    }

    public int reorderSectionStart() {
        currentReorderSection = new Section();
        currentReorderSection.start = items.size();
        currentReorderSection.end = -1;
        reorderSections.add(currentReorderSection);
        return reorderSections.size() - 1;
    }
    public void reorderSectionEnd() {
        if (currentReorderSection != null) {
            currentReorderSection.end = Math.max(0, items.size() - 1);
        }
    }

    public boolean isReorderItem(int position) {
        return getReorderSectionId(position) >= 0;
    }
    public int getReorderSectionId(int position) {
        for (int i = 0; i < reorderSections.size(); ++i) {
            if (reorderSections.get(i).contains(position))
                return i;
        }
        return -1;
    }

    private int orderChangedId;
    private boolean orderChanged;
    public void swapElements(int fromPosition, int toPosition) {
        if (onReordered == null) return;
        int fromPositionReorderId = getReorderSectionId(fromPosition);
        int toPositionReorderId = getReorderSectionId(toPosition);
        if (fromPositionReorderId < 0 || fromPositionReorderId != toPositionReorderId) {
            return;
        }
        UItem fromItem = items.get(fromPosition);
        UItem toItem = items.get(toPosition);
        boolean fromItemHadDivider = hasDivider(fromPosition);
        boolean toItemHadDivider = hasDivider(toPosition);
        items.set(fromPosition, toItem);
        items.set(toPosition, fromItem);
        notifyItemMoved(fromPosition, toPosition);
        if (hasDivider(toPosition) != fromItemHadDivider) {
            notifyItemChanged(toPosition, 3);
        }
        if (hasDivider(fromPosition) != toItemHadDivider) {
            notifyItemChanged(fromPosition, 3);
        }
        if (orderChanged && orderChangedId != fromPositionReorderId) {
            callReorder(orderChangedId);
        }
        orderChanged = true;
        orderChangedId = fromPositionReorderId;
    }

    private void callReorder(int id) {
        if (id < 0 || id >= reorderSections.size()) return;
        Section section = reorderSections.get(id);
        onReordered.run(id, new ArrayList(items.subList(section.start, section.end + 1)));
        orderChanged = false;
    }

    public void reorderDone() {
        if (orderChanged) {
            callReorder(orderChangedId);
        }
    }

    private Utilities.Callback2<Integer, ArrayList<UItem>> onReordered;
    public void listenReorder(Utilities.Callback2<Integer, ArrayList<UItem>> onReordered) {
        this.onReordered = onReordered;
    }

    private boolean allowReorder;
    public void updateReorder(boolean allowReorder) {
        this.allowReorder = allowReorder;
    }

    public void drawWhiteSections(Canvas canvas, RecyclerListView listView) {
        for (int i = 0; i < whiteSections.size(); ++i) {
            Section section = whiteSections.get(i);
            if (section.end < 0) continue;
            listView.drawSectionBackground(canvas, section.start, section.end, getThemedColor(Theme.key_windowBackgroundWhite));
        }
    }

    public void update(boolean animated) {
        oldItems.clear();
        oldItems.addAll(items);
        items.clear();
        whiteSections.clear();
        reorderSections.clear();
        fillItems.run(items, this);
        if (animated) {
            setItems(oldItems, items);
        } else {
            notifyDataSetChanged();
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        switch (viewType) {
            case VIEW_TYPE_HEADER:
                view = new HeaderCell(context, resourcesProvider);
                view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                break;
            case VIEW_TYPE_TOPVIEW:
                view = new TopViewCell(context, resourcesProvider);
                break;
            case VIEW_TYPE_TEXT:
                view = new TextCell(context, resourcesProvider);
                view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                break;
            case VIEW_TYPE_CHECK:
            case VIEW_TYPE_CHECKRIPPLE:
                TextCheckCell cell = new TextCheckCell(context, resourcesProvider);
                if (viewType == VIEW_TYPE_CHECKRIPPLE) {
                    cell.setDrawCheckRipple(true);
                    cell.setColors(Theme.key_windowBackgroundCheckText, Theme.key_switchTrackBlue, Theme.key_switchTrackBlueChecked, Theme.key_switchTrackBlueThumb, Theme.key_switchTrackBlueThumbChecked);
                    cell.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
                    cell.setHeight(56);
                }
                cell.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                view = cell;
                break;
            case VIEW_TYPE_RADIO:
                view = new DialogRadioCell(context);
                view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                break;
            case VIEW_TYPE_TEXT_CHECK:
            case VIEW_TYPE_ICON_TEXT_CHECK:
                view = new NotificationsCheckCell(context, 21, 60, viewType == VIEW_TYPE_ICON_TEXT_CHECK, resourcesProvider);
                view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                break;
            case VIEW_TYPE_CUSTOM:
                view = new FrameLayout(context) {
                    @Override
                    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), heightMeasureSpec);
                    }
                };
                break;
            case VIEW_TYPE_FILTER_CHAT:
                UserCell userCell = new UserCell(context, 6, 0, false);
                userCell.setSelfAsSavedMessages(true);
                userCell.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                view = userCell;
                break;
            case VIEW_TYPE_USER_ADD:
                UserCell userCell2 = new UserCell(context, 6, 0, false, true);
                userCell2.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                view = userCell2;
                break;
            case VIEW_TYPE_SLIDE:
                view = new SlideChooseView(context, resourcesProvider);
                view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                break;
            case VIEW_TYPE_QUICK_REPLY:
                view = new QuickRepliesActivity.QuickReplyView(context, onReordered != null, resourcesProvider);
                view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                break;
            case VIEW_TYPE_LARGE_QUICK_REPLY:
                view = new QuickRepliesActivity.LargeQuickReplyView(context, resourcesProvider);
                view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                break;
            default:
            case VIEW_TYPE_SHADOW:
                view = new TextInfoPrivacyCell(context, resourcesProvider);
                break;
        }
        return new RecyclerListView.Holder(view);
    }

    @Override
    public int getItemViewType(int position) {
        UItem item = getItem(position);
        if (item == null) return 0;
        return item.viewType;
    }

    private boolean hasDivider(int position) {
        UItem item = getItem(position);
        UItem nextItem = getItem(position + 1);
        return item != null && nextItem != null && (nextItem.viewType != VIEW_TYPE_SHADOW) == (item.viewType != VIEW_TYPE_SHADOW);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        UItem item = getItem(position);
        UItem nextItem = getItem(position + 1);
        UItem prevItem = getItem(position - 1);
        if (item == null) return;
        final int viewType = holder.getItemViewType();
        final boolean divider = hasDivider(position);
        switch (viewType) {
            case VIEW_TYPE_HEADER:
                ((HeaderCell) holder.itemView).setText(item.text);
                break;
            case VIEW_TYPE_TOPVIEW:
                TopViewCell topCell = (TopViewCell) holder.itemView;
                if (item.iconResId != 0) {
                    topCell.setEmoji(item.iconResId);
                } else {
                    topCell.setEmoji(item.subtext.toString(), item.textValue.toString());
                }
                topCell.setText(item.text);
                break;
            case VIEW_TYPE_TEXT:
                TextCell cell = (TextCell) holder.itemView;
                if (TextUtils.isEmpty(item.textValue)) {
                    if (item.iconResId == 0) {
                        cell.setText(item.text, divider);
                    } else {
                        cell.setTextAndIcon(item.text, item.iconResId, divider);
                    }
                } else {
                    if (item.iconResId == 0) {
                        cell.setTextAndValue(item.text, item.textValue, divider);
                    } else {
                        cell.setTextAndValueAndIcon(item.text, item.textValue, item.iconResId, divider);
                    }
                }
                if (item.accent) {
                    cell.setColors(Theme.key_windowBackgroundWhiteBlueText4, Theme.key_windowBackgroundWhiteBlueText4);
                } else if (item.red) {
                    cell.setColors(Theme.key_text_RedBold, Theme.key_text_RedRegular);
                } else {
                    cell.setColors(Theme.key_windowBackgroundWhiteGrayIcon, Theme.key_windowBackgroundWhiteBlackText);
                }
                break;
            case VIEW_TYPE_CHECK:
            case VIEW_TYPE_CHECKRIPPLE:
                TextCheckCell checkCell = (TextCheckCell) holder.itemView;
                if (checkCell.itemId == item.id) {
                    checkCell.setChecked(item.checked);
                }
                checkCell.setTextAndCheck(item.text, item.checked, divider);
                checkCell.itemId = item.id;
                if (viewType == VIEW_TYPE_CHECKRIPPLE) {
                    holder.itemView.setBackgroundColor(Theme.getColor(item.checked ? Theme.key_windowBackgroundChecked : Theme.key_windowBackgroundUnchecked));
                }
                break;
            case VIEW_TYPE_RADIO:
                DialogRadioCell radioCell = (DialogRadioCell) holder.itemView;
                if (radioCell.itemId == item.id) {
                    radioCell.setChecked(item.checked, true);
                    radioCell.setEnabled(item.enabled, true);
                } else {
                    radioCell.setEnabled(item.enabled, false);
                }
                if (TextUtils.isEmpty(item.textValue)) {
                    radioCell.setText(item.text, item.checked, divider);
                } else {
                    radioCell.setTextAndValue(item.text, item.textValue, item.checked, divider);
                }
                radioCell.itemId = item.id;
                break;
            case VIEW_TYPE_TEXT_CHECK:
                NotificationsCheckCell checkCell1 = (NotificationsCheckCell) holder.itemView;
                checkCell1.setTextAndValueAndCheck(item.text, item.subtext, item.checked, divider);
                checkCell1.setMultiline(item.subtext != null && item.subtext.toString().contains("\n"));
                break;
            case VIEW_TYPE_ICON_TEXT_CHECK:
                // TODO: image
                ((NotificationsCheckCell) holder.itemView).setTextAndValueAndCheck(item.text, item.subtext, item.checked, divider);
                break;
            case VIEW_TYPE_SHADOW:
                TextInfoPrivacyCell cell2 = (TextInfoPrivacyCell) holder.itemView;
                if (TextUtils.isEmpty(item.text)) {
                    cell2.setFixedSize(12);
                    cell2.setText("");
                } else {
                    cell2.setFixedSize(0);
                    cell2.setText(item.text);
                }
                final boolean prev = prevItem != null && prevItem.viewType != viewType;
                final boolean next = nextItem != null && nextItem.viewType != viewType;
                int drawable;
                if (prev && next) {
                    drawable = R.drawable.greydivider;
                } else if (prev) {
                    drawable = R.drawable.greydivider_bottom;
                } else if (next) {
                    drawable = R.drawable.greydivider_top;
                } else {
                    drawable = R.drawable.field_carret_empty;
                }
                cell2.setBackground(Theme.getThemedDrawableByKey(context, drawable, Theme.key_windowBackgroundGrayShadow, resourcesProvider));
                break;
            case VIEW_TYPE_CUSTOM:
                FrameLayout frameLayout = (FrameLayout) holder.itemView;
                if (frameLayout.getChildCount() != (item.view == null ? 0 : 1) || frameLayout.getChildAt(0) != item.view) {
                    frameLayout.removeAllViews();
                    if (item.view != null) {
                        AndroidUtilities.removeFromParent(item.view);
                        frameLayout.addView(item.view, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                    }
                }
                break;
            case VIEW_TYPE_FILTER_CHAT:
                UserCell userCell = (UserCell) holder.itemView;
                userCell.setFromUItem(currentAccount, item, divider);
                break;
            case VIEW_TYPE_USER_ADD:
                UserCell userCell2 = (UserCell) holder.itemView;
                userCell2.setFromUItem(currentAccount, item, divider);
                userCell2.setAddButtonVisible(!item.checked);
                userCell2.setCloseIcon(item.clickCallback);
                break;
            case VIEW_TYPE_SLIDE:
                SlideChooseView slideView = (SlideChooseView) holder.itemView;
                slideView.setOptions(item.intValue, item.texts);
                slideView.setCallback(index -> {
                    if (item.intCallback != null) {
                        item.intCallback.run(index);
                    }
                });
                break;
            case VIEW_TYPE_QUICK_REPLY:
                QuickRepliesActivity.QuickReplyView replyView = (QuickRepliesActivity.QuickReplyView) holder.itemView;
                replyView.setChecked(item.checked, false);
                replyView.setReorder(allowReorder);
                if (item.object instanceof QuickRepliesController.QuickReply) {
                    replyView.set((QuickRepliesController.QuickReply) item.object, null, divider);
                }
                break;
            case VIEW_TYPE_LARGE_QUICK_REPLY:
                QuickRepliesActivity.LargeQuickReplyView replyView2 = (QuickRepliesActivity.LargeQuickReplyView) holder.itemView;
                replyView2.setChecked(item.checked, false);
                if (item.object instanceof QuickRepliesController.QuickReply) {
                    replyView2.set((QuickRepliesController.QuickReply) item.object, divider);
                }
                break;
        }
    }

    @Override
    public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
        updateReorder(holder, allowReorder);
    }

    public void updateReorder(RecyclerView.ViewHolder holder, boolean allowReorder) {
        if (holder == null) return;
        final int viewType = holder.getItemViewType();
        switch (viewType) {
            case VIEW_TYPE_QUICK_REPLY:
                ((QuickRepliesActivity.QuickReplyView) holder.itemView).setReorder(allowReorder);
                break;
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public boolean isEnabled(RecyclerView.ViewHolder holder) {
        final int viewType = holder.getItemViewType();
        UItem item = getItem(holder.getAdapterPosition());
        return (
            viewType == VIEW_TYPE_TEXT ||
            viewType == VIEW_TYPE_TEXT_CHECK ||
            viewType == VIEW_TYPE_ICON_TEXT_CHECK ||
            viewType == VIEW_TYPE_CHECK ||
            viewType == VIEW_TYPE_RADIO ||
            viewType == VIEW_TYPE_FILTER_CHAT ||
            viewType == VIEW_TYPE_LARGE_QUICK_REPLY ||
            viewType == VIEW_TYPE_QUICK_REPLY
        ) && (item == null || item.enabled);
    }

    public UItem getItem(int position) {
        if (position < 0 || position >= items.size()) return null;
        return items.get(position);
    }

    public UItem findItem(int itemId) {
        for (int i = 0; i < items.size(); ++i) {
            UItem item = items.get(i);
            if (item != null && item.id == itemId) {
                return item;
            }
        }
        return null;
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

}
