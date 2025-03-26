package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stats;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Business.BusinessLinksActivity;
import org.telegram.ui.Business.QuickRepliesActivity;
import org.telegram.ui.Business.QuickRepliesController;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Cells.CollapseTextCell;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Cells.DialogRadioCell;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.NotificationsCheckCell;
import org.telegram.ui.Cells.ProfileSearchCell;
import org.telegram.ui.Cells.SlideIntChooseView;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextCheckCell2;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextRightIconCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.ChannelMonetizationLayout;
import org.telegram.ui.Charts.BaseChartView;
import org.telegram.ui.Components.ListView.AdapterWithDiffUtils;
import org.telegram.ui.StatisticActivity;
import org.telegram.ui.Stories.recorder.HintView2;
import org.telegram.ui.Stories.recorder.StoryPrivacyBottomSheet;

import java.util.ArrayList;

public class UniversalAdapter extends AdapterWithDiffUtils {

    public static final int VIEW_TYPE_FULLSCREEN_CUSTOM = -3;
    public static final int VIEW_TYPE_FULLY_CUSTOM = -2;
    public static final int VIEW_TYPE_CUSTOM = -1;

    public static final int VIEW_TYPE_HEADER = 0;
    public static final int VIEW_TYPE_BLACK_HEADER = 1;
    public static final int VIEW_TYPE_TOPVIEW = 2;

    public static final int VIEW_TYPE_TEXT = 3;
    public static final int VIEW_TYPE_CHECK = 4;
    public static final int VIEW_TYPE_TEXT_CHECK = 5;
    public static final int VIEW_TYPE_ICON_TEXT_CHECK = 6;
    public static final int VIEW_TYPE_SHADOW = 7;
    public static final int VIEW_TYPE_LARGE_SHADOW = 8;
    public static final int VIEW_TYPE_CHECKRIPPLE = 9;
    public static final int VIEW_TYPE_RADIO = 10;
    public static final int VIEW_TYPE_FILTER_CHAT = 11;
    public static final int VIEW_TYPE_FILTER_CHAT_CHECK = 12;
    public static final int VIEW_TYPE_USER_ADD = 13;
    public static final int VIEW_TYPE_SLIDE = 14;
    public static final int VIEW_TYPE_INTSLIDE = 15;
    public static final int VIEW_TYPE_QUICK_REPLY = 16;
    public static final int VIEW_TYPE_LARGE_QUICK_REPLY = 17;

    public static final int VIEW_TYPE_CHART_LINEAR = 18;
    public static final int VIEW_TYPE_CHART_DOUBLE_LINEAR = 19;
    public static final int VIEW_TYPE_CHART_STACK_BAR = 20;
    public static final int VIEW_TYPE_CHART_BAR = 21;
    public static final int VIEW_TYPE_CHART_STACK_LINEAR = 22;
    public static final int VIEW_TYPE_CHART_LINEAR_BAR = 23;

    public static final int VIEW_TYPE_PROCEED_OVERVIEW = 24;
    public static final int VIEW_TYPE_TRANSACTION = 25;

    public static final int VIEW_TYPE_LARGE_HEADER = 26;
    public static final int VIEW_TYPE_RADIO_USER = 27;
    public static final int VIEW_TYPE_SPACE = 28;

    public static final int VIEW_TYPE_BUSINESS_LINK = 29;

    public static final int VIEW_TYPE_RIGHT_ICON_TEXT = 30;

    public static final int VIEW_TYPE_GRAY_SECTION = 31;
    public static final int VIEW_TYPE_PROFILE_CELL = 32;
    public static final int VIEW_TYPE_SEARCH_MESSAGE = 33;
    public static final int VIEW_TYPE_FLICKER = 34;
    public static final int VIEW_TYPE_ROUND_CHECKBOX = 35;
    public static final int VIEW_TYPE_USER_GROUP_CHECKBOX = 36;
    public static final int VIEW_TYPE_USER_CHECKBOX = 37;
    public static final int VIEW_TYPE_SHADOW_COLLAPSE_BUTTON = 38;
    public static final int VIEW_TYPE_SWITCH = 39;
    public static final int VIEW_TYPE_EXPANDABLE_SWITCH = 40;
    public static final int VIEW_TYPE_ROUND_GROUP_CHECKBOX = 41;
    public static final int VIEW_TYPE_ANIMATED_HEADER = 42;

    protected final RecyclerListView listView;
    private final Context context;
    private final int currentAccount;
    private final int classGuid;
    private final boolean dialog;
    private boolean applyBackground = true;
    protected Utilities.Callback2<ArrayList<UItem>, UniversalAdapter> fillItems;
    private final Theme.ResourcesProvider resourcesProvider;

    private final ArrayList<UItem> oldItems = new ArrayList<>();
    private final ArrayList<UItem> items = new ArrayList<>();

    private BaseChartView.SharedUiComponents chartSharedUI;

    public UniversalAdapter(
        RecyclerListView listView,
        Context context,
        int currentAccount,
        int classGuid,
        Utilities.Callback2<ArrayList<UItem>, UniversalAdapter> fillItems,
        Theme.ResourcesProvider resourcesProvider
    ) {
        this(listView, context, currentAccount, classGuid, false, fillItems, resourcesProvider);
    }

    public UniversalAdapter(
        RecyclerListView listView,
        Context context,
        int currentAccount,
        int classGuid,
        boolean dialog,
        Utilities.Callback2<ArrayList<UItem>, UniversalAdapter> fillItems,
        Theme.ResourcesProvider resourcesProvider
    ) {
        super();
        this.listView = listView;
        this.context = context;
        this.currentAccount = currentAccount;
        this.classGuid = classGuid;
        this.dialog = dialog;
        this.fillItems = fillItems;
        this.resourcesProvider = resourcesProvider;
        update(false);
    }

    public void setApplyBackground(boolean applyBackground) {
        this.applyBackground = applyBackground;
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
            listView.drawSectionBackground(canvas, section.start, section.end, getThemedColor(dialog ? Theme.key_dialogBackground : Theme.key_windowBackgroundWhite));
        }
    }

    public void update(boolean animated) {
        oldItems.clear();
        oldItems.addAll(items);
        items.clear();
        whiteSections.clear();
        reorderSections.clear();
        if (fillItems != null) {
            fillItems.run(items, this);
            if (listView != null && listView.isComputingLayout()) {
                listView.post(() -> {
                    if (listView.isComputingLayout()) {
                        return;
                    }
                    if (animated) {
                        setItems(oldItems, items);
                    } else {
                        notifyDataSetChanged();
                    }
                });
            } else {
                if (animated) {
                    setItems(oldItems, items);
                } else {
                    notifyDataSetChanged();
                }
            }
        }
    }

    public void updateWithoutNotify() {
        oldItems.clear();
        oldItems.addAll(items);
        items.clear();
        whiteSections.clear();
        reorderSections.clear();
        if (fillItems != null) {
            fillItems.run(items, this);
        }
    }

    public boolean shouldApplyBackground(int viewType) {
        if (!applyBackground) return false;
        if (viewType >= UItem.factoryViewTypeStartsWith) {
            return true;
        }
        switch (viewType) {
            case VIEW_TYPE_HEADER:
            case VIEW_TYPE_ANIMATED_HEADER:
            case VIEW_TYPE_BLACK_HEADER:
            case VIEW_TYPE_TEXT:
            case VIEW_TYPE_CHECK:
            case VIEW_TYPE_CHECKRIPPLE:
            case VIEW_TYPE_RADIO:
            case VIEW_TYPE_TEXT_CHECK:
            case VIEW_TYPE_ICON_TEXT_CHECK:
            case VIEW_TYPE_FULLSCREEN_CUSTOM:
            case VIEW_TYPE_FILTER_CHAT:
            case VIEW_TYPE_FILTER_CHAT_CHECK:
            case VIEW_TYPE_USER_ADD:
            case VIEW_TYPE_RADIO_USER:
            case VIEW_TYPE_SLIDE:
            case VIEW_TYPE_INTSLIDE:
            case VIEW_TYPE_QUICK_REPLY:
            case VIEW_TYPE_LARGE_QUICK_REPLY:
            case VIEW_TYPE_CHART_LINEAR:
            case VIEW_TYPE_CHART_DOUBLE_LINEAR:
            case VIEW_TYPE_CHART_STACK_BAR:
            case VIEW_TYPE_CHART_BAR:
            case VIEW_TYPE_CHART_STACK_LINEAR:
            case VIEW_TYPE_CHART_LINEAR_BAR:
            case VIEW_TYPE_TRANSACTION:
            case VIEW_TYPE_PROCEED_OVERVIEW:
            case VIEW_TYPE_SPACE:
            case VIEW_TYPE_BUSINESS_LINK:
            case VIEW_TYPE_RIGHT_ICON_TEXT:
            case VIEW_TYPE_PROFILE_CELL:
            case VIEW_TYPE_SEARCH_MESSAGE:
            case VIEW_TYPE_FLICKER:
            case VIEW_TYPE_ROUND_CHECKBOX:
            case VIEW_TYPE_ROUND_GROUP_CHECKBOX:
            case VIEW_TYPE_USER_GROUP_CHECKBOX:
            case VIEW_TYPE_USER_CHECKBOX:
            case VIEW_TYPE_SWITCH:
            case VIEW_TYPE_EXPANDABLE_SWITCH:
                return true;
        }
        return false;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        final int key_background = dialog ? Theme.key_dialogBackground : Theme.key_windowBackgroundWhite;
        if (viewType >= UItem.factoryViewTypeStartsWith) {
            UItem.UItemFactory<?> factory = UItem.findFactory(viewType);
            if (factory != null) {
                view = factory.createView(context, currentAccount, classGuid, resourcesProvider);
            } else {
                view = new View(context);
            }
        } else switch (viewType) {
            case VIEW_TYPE_HEADER:
                if (dialog) {
                    view = new HeaderCell(context, Theme.key_windowBackgroundWhiteBlueHeader, 21, 15, 0, false, resourcesProvider);
                } else {
                    view = new HeaderCell(context, resourcesProvider);
                }
                break;
            case VIEW_TYPE_ANIMATED_HEADER:
                view = new HeaderCell(context, Theme.key_windowBackgroundWhiteBlueHeader, 21, 15, 0, false, true, resourcesProvider);
                break;
            case VIEW_TYPE_BLACK_HEADER:
                view = new HeaderCell(context, Theme.key_windowBackgroundWhiteBlackText, 17, 15, false, resourcesProvider);
                break;
            case VIEW_TYPE_LARGE_HEADER:
                HeaderCell headerCell = new HeaderCell(context, Theme.key_windowBackgroundWhiteBlackText, 23, 20, 0, false, resourcesProvider);
                headerCell.setTextSize(20);
                view = headerCell;
                break;
            case VIEW_TYPE_TOPVIEW:
                view = new TopViewCell(context, resourcesProvider);
                break;
            case VIEW_TYPE_TEXT:
                view = new TextCell(context, resourcesProvider);
                break;
            case VIEW_TYPE_CHECK:
            case VIEW_TYPE_CHECKRIPPLE:
                TextCheckCell cell = new TextCheckCell(context, resourcesProvider);
                if (viewType == VIEW_TYPE_CHECKRIPPLE) {
                    cell.setDrawCheckRipple(true);
                    cell.setColors(Theme.key_windowBackgroundCheckText, Theme.key_switchTrackBlue, Theme.key_switchTrackBlueChecked, Theme.key_switchTrackBlueThumb, Theme.key_switchTrackBlueThumbChecked);
                    cell.setTypeface(AndroidUtilities.bold());
                    cell.setHeight(56);
                }
                view = cell;
                break;
            case VIEW_TYPE_RADIO:
                view = new DialogRadioCell(context);
                break;
            case VIEW_TYPE_TEXT_CHECK:
            case VIEW_TYPE_ICON_TEXT_CHECK:
                view = new NotificationsCheckCell(context, 21, 60, viewType == VIEW_TYPE_ICON_TEXT_CHECK, resourcesProvider);
                break;
            case VIEW_TYPE_CUSTOM:
                view = new FrameLayout(context) {
                    @Override
                    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), heightMeasureSpec);
                    }
                };
                break;
            case VIEW_TYPE_FULLY_CUSTOM:
                view = new FrameLayout(context) {
                    @Override
                    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                        widthMeasureSpec = MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY);
                        measureChildren(widthMeasureSpec, heightMeasureSpec);
                        int height = 0;
                        for (int i = 0; i < getChildCount(); ++i) {
                            View child = getChildAt(i);
                            height = Math.max(height, child.getMeasuredHeight());
                        }
                        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
                    }
                };
                break;
            case VIEW_TYPE_FULLSCREEN_CUSTOM:
                view = new FullscreenCustomFrameLayout(context);
                break;
            case VIEW_TYPE_FILTER_CHAT:
            case VIEW_TYPE_FILTER_CHAT_CHECK:
                UserCell userCell = new UserCell(context, 6, viewType == VIEW_TYPE_FILTER_CHAT_CHECK ? 3 : 0, false);
                userCell.setSelfAsSavedMessages(true);
                view = userCell;
                break;
            case VIEW_TYPE_USER_ADD:
                UserCell userCell2 = new UserCell(context, 6, 0, false, true);
                view = userCell2;
                break;
            case VIEW_TYPE_RADIO_USER:
                StoryPrivacyBottomSheet.UserCell userCell3 = new StoryPrivacyBottomSheet.UserCell(context, resourcesProvider);
                userCell3.setIsSendAs(false, false);
                view = userCell3;
                break;
            case VIEW_TYPE_SLIDE:
                view = new SlideChooseView(context, resourcesProvider);
                break;
            case VIEW_TYPE_INTSLIDE:
                view = new SlideIntChooseView(context, resourcesProvider);
                break;
            case VIEW_TYPE_QUICK_REPLY:
                view = new QuickRepliesActivity.QuickReplyView(context, onReordered != null, resourcesProvider);
                break;
            case VIEW_TYPE_LARGE_QUICK_REPLY:
                view = new QuickRepliesActivity.LargeQuickReplyView(context, resourcesProvider);
                break;
            case VIEW_TYPE_CHART_LINEAR:
            case VIEW_TYPE_CHART_DOUBLE_LINEAR:
            case VIEW_TYPE_CHART_STACK_BAR:
            case VIEW_TYPE_CHART_BAR:
            case VIEW_TYPE_CHART_STACK_LINEAR:
            case VIEW_TYPE_CHART_LINEAR_BAR:
                if (chartSharedUI == null) {
                    chartSharedUI = new BaseChartView.SharedUiComponents();
                }
                view = new StatisticActivity.UniversalChartCell(context, currentAccount, viewType - VIEW_TYPE_CHART_LINEAR, chartSharedUI, classGuid);
                break;
            case VIEW_TYPE_TRANSACTION:
                view = new ChannelMonetizationLayout.TransactionCell(context, resourcesProvider);
                break;
            case VIEW_TYPE_PROCEED_OVERVIEW:
                view = new ChannelMonetizationLayout.ProceedOverviewCell(context, resourcesProvider);
                break;
            case VIEW_TYPE_SPACE:
                view = new View(context);
                break;
            case VIEW_TYPE_BUSINESS_LINK:
                view = new BusinessLinksActivity.BusinessLinkView(context, resourcesProvider);
                break;
            case VIEW_TYPE_RIGHT_ICON_TEXT:
                view = new TextRightIconCell(context, resourcesProvider);
                break;
            case VIEW_TYPE_GRAY_SECTION:
                view = new GraySectionCell(context, resourcesProvider);
                break;
            case VIEW_TYPE_PROFILE_CELL:
                view = new ProfileSearchCell(context);
                break;
            case VIEW_TYPE_SEARCH_MESSAGE:
                view = new DialogCell(null, context, false, true);
                break;
            case VIEW_TYPE_FLICKER:
                FlickerLoadingView flickerLoadingView = new FlickerLoadingView(context, resourcesProvider);
                flickerLoadingView.setIsSingleCell(true);
                view = flickerLoadingView;
                break;
            default:
            case VIEW_TYPE_SHADOW:
            case VIEW_TYPE_LARGE_SHADOW:
                view = new TextInfoPrivacyCell(context, resourcesProvider);
                break;
            case VIEW_TYPE_ROUND_CHECKBOX:
            case VIEW_TYPE_ROUND_GROUP_CHECKBOX:
            case VIEW_TYPE_USER_GROUP_CHECKBOX:
            case VIEW_TYPE_USER_CHECKBOX:
                int checkBoxType = 0;
                if (viewType == VIEW_TYPE_ROUND_CHECKBOX) {
                    checkBoxType = CheckBoxCell.TYPE_CHECK_BOX_ROUND;
                } else if (viewType == VIEW_TYPE_USER_GROUP_CHECKBOX) {
                    checkBoxType = CheckBoxCell.TYPE_CHECK_BOX_USER_GROUP;
                } else if (viewType == VIEW_TYPE_USER_CHECKBOX) {
                    checkBoxType = CheckBoxCell.TYPE_CHECK_BOX_USER;
                } else if (viewType == VIEW_TYPE_ROUND_GROUP_CHECKBOX) {
                    checkBoxType = CheckBoxCell.TYPE_CHECK_BOX_ROUND_GROUP;
                }
                CheckBoxCell checkBoxCell = new CheckBoxCell(context, checkBoxType, 21, true, resourcesProvider);
                checkBoxCell.getCheckBoxRound().setColor(Theme.key_switch2TrackChecked, Theme.key_radioBackground, Theme.key_checkboxCheck);
                view = checkBoxCell;
                break;
            case VIEW_TYPE_SHADOW_COLLAPSE_BUTTON:
                view = new CollapseTextCell(context, resourcesProvider);
                break;
            case VIEW_TYPE_SWITCH:
            case VIEW_TYPE_EXPANDABLE_SWITCH:
                view = new TextCheckCell2(context);
                break;
        }
        if (shouldApplyBackground(viewType)) {
            view.setBackgroundColor(getThemedColor(key_background));
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
        return item != null && !item.hideDivider && nextItem != null && isShadow(nextItem.viewType) == isShadow(item.viewType);
    }

    public boolean isShadow(int viewType) {
        if (viewType >= UItem.factoryViewTypeStartsWith) {
            UItem.UItemFactory<?> factory = UItem.findFactory(viewType);
            return factory != null && factory.isShadow();
        }
        return viewType == VIEW_TYPE_SHADOW || viewType == VIEW_TYPE_LARGE_SHADOW || viewType == VIEW_TYPE_SHADOW_COLLAPSE_BUTTON || viewType == VIEW_TYPE_GRAY_SECTION || viewType == VIEW_TYPE_FLICKER;
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        UItem item = getItem(position);
        UItem nextItem = getItem(position + 1);
        UItem prevItem = getItem(position - 1);
        if (item == null) return;
        final int viewType = holder.getItemViewType();
        final boolean divider = hasDivider(position);
        updateColors(holder);
        if (viewType >= UItem.factoryViewTypeStartsWith) {
            UItem.UItemFactory<?> factory = UItem.findFactory(viewType);
            if (factory != null) {
                factory.bindView(holder.itemView, item, divider);
            }
        } else switch (viewType) {
            case VIEW_TYPE_HEADER:
            case VIEW_TYPE_BLACK_HEADER:
            case VIEW_TYPE_LARGE_HEADER:
                ((HeaderCell) holder.itemView).setText(item.text);
                break;
            case VIEW_TYPE_ANIMATED_HEADER:
                HeaderCell animatedHeaderCell = (HeaderCell) holder.itemView;
                animatedHeaderCell.setText(item.animatedText, animatedHeaderCell.id == item.id);
                animatedHeaderCell.id = item.id;
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
                if (item.object instanceof TLRPC.Document) {
                    cell.setTextAndSticker(item.text, (TLRPC.Document) item.object, divider);
                } else if (item.object instanceof String) {
                    cell.setTextAndSticker(item.text, (String) item.object, divider);
                } else if (TextUtils.isEmpty(item.textValue)) {
                    if (item.object instanceof Drawable) {
                        cell.setTextAndIcon(item.text, (Drawable) item.object, divider);
                    } else if (item.iconResId == 0) {
                        cell.setText(item.text, divider);
                    } else {
                        cell.setTextAndIcon(item.text, item.iconResId, divider);
                    }
                } else {
                    if (item.object instanceof Drawable) {
                        cell.setTextAndValueAndIcon(item.text, item.textValue, (Drawable) item.object, divider);
                    } else if (item.iconResId == 0) {
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
                checkCell.setEnabled(item.enabled, null);
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
                final boolean multiline = item.subtext != null && item.subtext.toString().contains("\n");
                checkCell1.setTextAndValueAndCheck(item.text, item.subtext, item.checked, 0, multiline, divider);
                break;
            case VIEW_TYPE_ICON_TEXT_CHECK:
                // TODO: image
                ((NotificationsCheckCell) holder.itemView).setTextAndValueAndCheck(item.text, item.subtext, item.checked, divider);
                break;
            case VIEW_TYPE_SHADOW_COLLAPSE_BUTTON:
            case VIEW_TYPE_SHADOW:
            case VIEW_TYPE_LARGE_SHADOW:
                View cell3 = null;
                if (viewType == VIEW_TYPE_SHADOW || viewType == VIEW_TYPE_LARGE_SHADOW) {
                    TextInfoPrivacyCell cell2 = (TextInfoPrivacyCell) holder.itemView;
                    if (TextUtils.isEmpty(item.text)) {
                        cell2.setFixedSize(viewType == VIEW_TYPE_LARGE_SHADOW ? 220 : 12);
                        cell2.setText("");
                    } else {
                        cell2.setFixedSize(0);
                        cell2.setText(item.text);
                    }
                    if (item.accent) { // asCenterShadow
                        cell2.setTextGravity(Gravity.CENTER);
                        cell2.getTextView().setWidth(Math.min(HintView2.cutInFancyHalf(cell2.getText(), cell2.getTextView().getPaint()), AndroidUtilities.displaySize.x - dp(60)));
                        cell2.getTextView().setPadding(0, dp(17), 0, dp(17));
                    } else {
                        cell2.setTextGravity(Gravity.START);
                        cell2.getTextView().setMinWidth(0);
                        cell2.getTextView().setMaxWidth(AndroidUtilities.displaySize.x);
                        cell2.getTextView().setPadding(0, dp(10), 0, dp(17));
                    }
                    cell3 = cell2;
                } else if (viewType == VIEW_TYPE_SHADOW_COLLAPSE_BUTTON) {
                    CollapseTextCell btn = (CollapseTextCell) holder.itemView;
                    btn.set(item.animatedText, item.collapsed);
                    if (item.accent) {
                        btn.setColor(Theme.key_windowBackgroundWhiteBlueText4);
                    } else if (item.red) {
                        btn.setColor(Theme.key_text_RedRegular);
                    } else {
                        btn.setColor(Theme.key_windowBackgroundWhiteBlackText);
                    }
                    cell3 = btn;
                }
                final boolean prev = prevItem != null && !isShadow(prevItem.viewType);
                final boolean next = nextItem != null && !isShadow(nextItem.viewType);
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
                Drawable shadowDrawable = Theme.getThemedDrawableByKey(context, drawable, Theme.key_windowBackgroundGrayShadow, resourcesProvider);
                if (dialog) {
                    cell3.setBackground(new LayerDrawable(new Drawable[]{
                            new ColorDrawable(getThemedColor(Theme.key_dialogBackgroundGray)),
                            shadowDrawable
                    }));
                } else {
                    cell3.setBackground(shadowDrawable);
                }
                break;
            case VIEW_TYPE_CUSTOM:
            case VIEW_TYPE_FULLY_CUSTOM:
                FrameLayout frameLayout = (FrameLayout) holder.itemView;
                if (frameLayout.getChildCount() != (item.view == null ? 0 : 1) || frameLayout.getChildAt(0) != item.view) {
                    frameLayout.removeAllViews();
                    if (item.view != null) {
                        AndroidUtilities.removeFromParent(item.view);
                        FrameLayout.LayoutParams lp;
                        if (viewType == VIEW_TYPE_CUSTOM || viewType == VIEW_TYPE_FULLSCREEN_CUSTOM) {
                            lp = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT);
                        } else {
                            lp = LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
                        }
                        frameLayout.addView(item.view, lp);
                    }
                }
                break;
            case VIEW_TYPE_FULLSCREEN_CUSTOM:
                FullscreenCustomFrameLayout frameLayout2 = (FullscreenCustomFrameLayout) holder.itemView;
                frameLayout2.setMinusHeight(item.intValue);
                if (frameLayout2.getChildCount() != (item.view == null ? 0 : 1) || frameLayout2.getChildAt(0) != item.view) {
                    frameLayout2.removeAllViews();
                    if (item.view != null) {
                        AndroidUtilities.removeFromParent(item.view);
                        frameLayout2.addView(item.view, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                    }
                }
                break;
            case VIEW_TYPE_FILTER_CHAT:
            case VIEW_TYPE_FILTER_CHAT_CHECK:
                UserCell userCell = (UserCell) holder.itemView;
                userCell.setFromUItem(currentAccount, item, divider);
                if (viewType == VIEW_TYPE_FILTER_CHAT_CHECK) {
                    userCell.setChecked(item.checked, false);
                }
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
                slideView.setMinAllowedIndex((int) item.longValue);
                slideView.setCallback(index -> {
                    if (item.intCallback != null) {
                        item.intCallback.run(index);
                    }
                });
                break;
            case VIEW_TYPE_INTSLIDE:
                SlideIntChooseView slideIntChooseView = (SlideIntChooseView) holder.itemView;
                slideIntChooseView.set(item.intValue, (SlideIntChooseView.Options) item.object, item.intCallback);
                slideIntChooseView.setMinValueAllowed((int) item.longValue);
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
            case VIEW_TYPE_CHART_LINEAR:
            case VIEW_TYPE_CHART_DOUBLE_LINEAR:
            case VIEW_TYPE_CHART_STACK_BAR:
            case VIEW_TYPE_CHART_BAR:
            case VIEW_TYPE_CHART_STACK_LINEAR:
            case VIEW_TYPE_CHART_LINEAR_BAR:
                ((StatisticActivity.UniversalChartCell) holder.itemView).set(
                    item.intValue,
                    (StatisticActivity.ChartViewData) item.object,
                    () -> {
                        View view = findViewByItemObject(item.object);
                        if (view instanceof StatisticActivity.UniversalChartCell) {
                            return (StatisticActivity.UniversalChartCell) view;
                        }
                        return null;
                    }
                );
                break;
            case VIEW_TYPE_TRANSACTION:
                ((ChannelMonetizationLayout.TransactionCell) holder.itemView).set((TL_stats.BroadcastRevenueTransaction) item.object, divider);
                break;
            case VIEW_TYPE_PROCEED_OVERVIEW:
                ((ChannelMonetizationLayout.ProceedOverviewCell) holder.itemView).set((ChannelMonetizationLayout.ProceedOverview) item.object);
                break;
            case VIEW_TYPE_RADIO_USER:
                StoryPrivacyBottomSheet.UserCell userCell1 = (StoryPrivacyBottomSheet.UserCell) holder.itemView;
                final boolean animated = userCell1.dialogId == (item.object instanceof TLRPC.User ? ((TLRPC.User) item.object).id : (item.object instanceof TLRPC.Chat ? -((TLRPC.Chat) item.object).id : 0));
                userCell1.setIsSendAs(false, true);
                userCell1.set(item.object);
                userCell1.checkBox.setVisibility(View.GONE);
                userCell1.radioButton.setVisibility(View.VISIBLE);
                userCell1.setChecked(item.checked, animated);
                userCell1.setDivider(divider);
                break;
            case VIEW_TYPE_SPACE:
                if (item.transparent) {
                    holder.itemView.setBackgroundColor(0x00000000);
                }
                holder.itemView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, item.intValue));
                break;
            case VIEW_TYPE_BUSINESS_LINK:
                BusinessLinksActivity.BusinessLinkView businessLinkView = (BusinessLinksActivity.BusinessLinkView) holder.itemView;
                if (item.object instanceof BusinessLinksActivity.BusinessLinkWrapper) {
                    businessLinkView.set((BusinessLinksActivity.BusinessLinkWrapper) item.object, divider);
                }
                break;
            case VIEW_TYPE_RIGHT_ICON_TEXT:
                TextRightIconCell textCell = (TextRightIconCell) holder.itemView;
                textCell.setTextAndIcon(item.text, item.iconResId);
                textCell.setDivider(divider);
                textCell.setBackgroundColor(getThemedColor(Theme.key_dialogBackground));
                break;
            case VIEW_TYPE_GRAY_SECTION:
                GraySectionCell sectionCell = (GraySectionCell) holder.itemView;
                if (TextUtils.equals(sectionCell.getText(), item.text)) {
                    sectionCell.setRightText(item.subtext, true, item.clickCallback);
                } else {
                    sectionCell.setText(item.text, item.subtext, item.clickCallback);
                }
                break;
            case VIEW_TYPE_PROFILE_CELL:
                ProfileSearchCell profileCell = (ProfileSearchCell) holder.itemView;
                Object object = item.object;
                CharSequence s = "";
                if (item.accent && object instanceof TLRPC.User && ((TLRPC.User) object).bot_active_users != 0) { // show bot mau
                    TLRPC.User user = (TLRPC.User) object;
                    if (user.bot_active_users != 0) {
                        s = LocaleController.formatPluralStringSpaced("BotUsers", user.bot_active_users);
                    }
                } else if (item.withUsername) {
                    String username = null;
                    if (object instanceof TLRPC.User) {
                        username = UserObject.getPublicUsername((TLRPC.User) object);
                    } else if (object instanceof TLRPC.Chat) {
                        username = ChatObject.getPublicUsername((TLRPC.Chat) object);
                    }
                    if (username != null) {
                        s += "@" + username;
                    }
                }
                String title = "";
                if (object instanceof TLRPC.Chat) {
                    TLRPC.Chat chat = (TLRPC.Chat) object;
                    if (chat.participants_count != 0) {
                        String membersString;
                        if (ChatObject.isChannel(chat) && !chat.megagroup) {
                            membersString = LocaleController.formatPluralStringSpaced("Subscribers", chat.participants_count);
                        } else {
                            membersString = LocaleController.formatPluralStringSpaced("Members", chat.participants_count);
                        }
                        if (s instanceof SpannableStringBuilder) {
                            ((SpannableStringBuilder) s).append(", ").append(membersString);
                        } else if (!TextUtils.isEmpty(s)) {
                            s = TextUtils.concat(s, ", ", membersString);
                        } else {
                            s = membersString;
                        }
                    }
                    title = chat.title;
                } else if (object instanceof TLRPC.User) {
                    TLRPC.User user = (TLRPC.User) object;
                    // add status text
                    title = UserObject.getUserName(user);
                }
                profileCell.allowBotOpenButton(item.checked, item.object2 instanceof Utilities.Callback ? (Utilities.Callback) item.object2 : null);
                profileCell.setRectangularAvatar(item.red);
                profileCell.setData(object, null, title, s, false, false);
                profileCell.useSeparator = divider;
                break;
            case VIEW_TYPE_SEARCH_MESSAGE:
                DialogCell dialogCell = (DialogCell) holder.itemView;
                MessageObject messageObject = null;
                if (item.object instanceof MessageObject) {
                    messageObject = (MessageObject) item.object;
                }
                dialogCell.useSeparator = divider;
                if (messageObject == null) {
                    dialogCell.setDialog(0, null, 0, false, false);
                } else {
                    dialogCell.setDialog(messageObject.getDialogId(), messageObject, messageObject.messageOwner.date, false, false);
                }
                break;
            case VIEW_TYPE_FLICKER:
                FlickerLoadingView flickerLoadingView = (FlickerLoadingView) holder.itemView;
                flickerLoadingView.setViewType(item.intValue);
                break;
            case VIEW_TYPE_ROUND_CHECKBOX:
            case VIEW_TYPE_ROUND_GROUP_CHECKBOX:
            case VIEW_TYPE_USER_GROUP_CHECKBOX:
                CheckBoxCell checkBoxCell = (CheckBoxCell) holder.itemView;
                checkBoxCell.setPad(item.pad);
                checkBoxCell.setText(item.text, "", item.checked, divider, checkBoxCell.itemId == item.id);
                checkBoxCell.itemId = item.id;
                checkBoxCell.setIcon(item.locked ? R.drawable.permission_locked : 0);
                if (viewType == VIEW_TYPE_USER_GROUP_CHECKBOX || viewType == VIEW_TYPE_ROUND_GROUP_CHECKBOX) {
                    checkBoxCell.setCollapseButton(item.collapsed, item.animatedText, item.clickCallback);
                }
                break;
            case VIEW_TYPE_USER_CHECKBOX:
                CheckBoxCell userCheckBoxCell = (CheckBoxCell) holder.itemView;
                userCheckBoxCell.setPad(item.pad);
                userCheckBoxCell.setUserOrChat((TLObject) item.object);
                userCheckBoxCell.setChecked(item.checked, userCheckBoxCell.itemId == item.id);
                userCheckBoxCell.itemId = item.id;
                userCheckBoxCell.setNeedDivider(divider);
                break;
            case VIEW_TYPE_SWITCH:
            case VIEW_TYPE_EXPANDABLE_SWITCH:
                TextCheckCell2 switchCell = (TextCheckCell2) holder.itemView;
                switchCell.setTextAndCheck(item.text.toString(), item.checked, divider, switchCell.id == item.id);
                switchCell.id = item.id;
                switchCell.setIcon(item.locked ? R.drawable.permission_locked : 0);
                if (viewType == VIEW_TYPE_EXPANDABLE_SWITCH) {
                    if (TextUtils.isEmpty(item.animatedText)) {
                        switchCell.hideCollapseArrow();
                    } else {
                        switchCell.setCollapseArrow(item.animatedText.toString(), item.collapsed, () -> {
                            item.clickCallback.onClick(switchCell);
                        });
                    }
                }
                break;
        }
    }

    private View findViewByItemObject(Object object) {
        int position = -1;
        for (int i = 0; i < getItemCount(); ++i) {
            UItem item = getItem(i);
            if (item != null && item.object == object) {
                position = i;
                break;
            }
        }
        if (position == RecyclerListView.NO_POSITION) {
            return null;
        }
        for (int i = 0; i < listView.getChildCount(); ++i) {
            View child = listView.getChildAt(i);
            int childPosition = listView.getChildAdapterPosition(child);
            if (childPosition != RecyclerListView.NO_POSITION && childPosition == position) {
                return child;
            }
        }
        return null;
    }

    @Override
    public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
        updateReorder(holder, allowReorder);
        updateColors(holder);
    }

    private void updateColors(RecyclerView.ViewHolder holder) {
        if (holder.itemView instanceof Theme.Colorable) {
            ((Theme.Colorable) holder.itemView).updateColors();
            if (shouldApplyBackground(holder.getItemViewType())) {
                final int key_background = dialog ? Theme.key_dialogBackground : Theme.key_windowBackgroundWhite;
                holder.itemView.setBackgroundColor(getThemedColor(key_background));
            }
        }
    }

    public void updateReorder(RecyclerView.ViewHolder holder, boolean allowReorder) {
        if (holder == null) return;
        final int viewType = holder.getItemViewType();
        if (viewType >= UItem.factoryViewTypeStartsWith) {
            UItem.UItemFactory<?> factory = UItem.findFactory(viewType);
            if (factory != null) {
                factory.attachedView(holder.itemView, getItem(holder.getAdapterPosition()));
            }
        } else {
            switch (viewType) {
                case VIEW_TYPE_QUICK_REPLY:
                    ((QuickRepliesActivity.QuickReplyView) holder.itemView).setReorder(allowReorder);
                    break;
            }
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
        final boolean clickable;
        if (viewType >= UItem.factoryViewTypeStartsWith) {
            UItem.UItemFactory<?> factory = UItem.findFactory(viewType);
            clickable = factory != null && factory.isClickable();
        } else {
            clickable = (
                viewType == VIEW_TYPE_TEXT ||
                viewType == VIEW_TYPE_TEXT_CHECK ||
                viewType == VIEW_TYPE_ICON_TEXT_CHECK ||
                viewType == VIEW_TYPE_RIGHT_ICON_TEXT ||
                viewType == VIEW_TYPE_CHECK ||
                viewType == VIEW_TYPE_RADIO ||
                viewType == VIEW_TYPE_FILTER_CHAT ||
                viewType == VIEW_TYPE_FILTER_CHAT_CHECK ||
                viewType == VIEW_TYPE_LARGE_QUICK_REPLY ||
                viewType == VIEW_TYPE_QUICK_REPLY ||
                viewType == VIEW_TYPE_BUSINESS_LINK ||
                viewType == VIEW_TYPE_TRANSACTION ||
                viewType == VIEW_TYPE_RADIO_USER ||
                viewType == VIEW_TYPE_PROFILE_CELL ||
                viewType == VIEW_TYPE_SEARCH_MESSAGE ||
                viewType == VIEW_TYPE_ROUND_CHECKBOX ||
                viewType == VIEW_TYPE_USER_GROUP_CHECKBOX ||
                viewType == VIEW_TYPE_USER_CHECKBOX ||
                viewType == VIEW_TYPE_ROUND_GROUP_CHECKBOX ||
                viewType == VIEW_TYPE_SWITCH ||
                viewType == VIEW_TYPE_EXPANDABLE_SWITCH ||
                viewType == VIEW_TYPE_SHADOW_COLLAPSE_BUTTON
            );
        }
        return clickable && (item == null || item.enabled);
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

    protected int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }


    private class FullscreenCustomFrameLayout extends FrameLayout {
        private int minusHeight = 0;
        public FullscreenCustomFrameLayout(Context context) {
            super(context);
        }
        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            if (getParent() instanceof View && ((View) getParent()).getMeasuredHeight() > 0) {
                View parent = (View) getParent();
                super.onMeasure(
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(parent.getMeasuredHeight() - minusHeight, MeasureSpec.EXACTLY)
                );
            } else if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
                super.onMeasure(
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec) - minusHeight, MeasureSpec.EXACTLY)
                );
            } else {
                int maxHeight = View.MeasureSpec.getSize(heightMeasureSpec);
                widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(View.MeasureSpec.getSize(widthMeasureSpec), View.MeasureSpec.EXACTLY);
                measureChildren(widthMeasureSpec, heightMeasureSpec);
                int height = 0;
                for (int i = 0; i < getChildCount(); ++i) {
                    View child = getChildAt(i);
                    height = Math.max(height, child.getMeasuredHeight());
                }
                if (maxHeight > 0) {
                    maxHeight -= minusHeight;
                    height = Math.min(height, maxHeight);
                }
                super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            }
        }

        public void setMinusHeight(int minusHeight) {
            this.minusHeight = minusHeight;
        }
    }
}
