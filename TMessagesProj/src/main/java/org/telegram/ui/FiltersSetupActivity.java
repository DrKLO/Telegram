package org.telegram.ui;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.GenericProvider;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CircularProgressDrawable;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.CrossfadeDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.FolderBottomSheet;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ListView.AdapterWithDiffUtils;
import org.telegram.ui.Components.LoadingDrawable;
import org.telegram.ui.Components.Premium.LimitReachedBottomSheet;
import org.telegram.ui.Components.Premium.PremiumFeatureBottomSheet;
import org.telegram.ui.Components.ProgressButton;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UndoView;

import java.util.ArrayList;
import java.util.Objects;

public class FiltersSetupActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private RecyclerListView listView;
    private ListAdapter adapter;
    private ItemTouchHelper itemTouchHelper;
    private UndoView undoView;

    private boolean orderChanged;

    private boolean ignoreUpdates;

    public static class TextCell extends FrameLayout {

        private SimpleTextView textView;
        private ImageView imageView;

        public TextCell(Context context) {
            super(context);

            textView = new SimpleTextView(context);
            textView.setTextSize(16);
            textView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText2));
            textView.setTag(Theme.key_windowBackgroundWhiteBlueText2);
            addView(textView);

            imageView = new ImageView(context);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            addView(imageView);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = AndroidUtilities.dp(48);

            textView.measure(MeasureSpec.makeMeasureSpec(width - AndroidUtilities.dp(71 + 23), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(20), MeasureSpec.EXACTLY));
            imageView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(50), MeasureSpec.EXACTLY));
            setMeasuredDimension(width, AndroidUtilities.dp(50));
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int height = bottom - top;
            int width = right - left;

            int viewLeft;
            int viewTop = (height - textView.getTextHeight()) / 2;
            if (LocaleController.isRTL) {
                viewLeft = getMeasuredWidth() - textView.getMeasuredWidth() - AndroidUtilities.dp(imageView.getVisibility() == VISIBLE ? 64 : 23);
            } else {
                viewLeft = AndroidUtilities.dp(imageView.getVisibility() == VISIBLE ? 64 : 23);
            }
            textView.layout(viewLeft, viewTop, viewLeft + textView.getMeasuredWidth(), viewTop + textView.getMeasuredHeight());

            viewLeft = !LocaleController.isRTL ? AndroidUtilities.dp(20) : width - imageView.getMeasuredWidth() - AndroidUtilities.dp(20);
            imageView.layout(viewLeft, 0, viewLeft + imageView.getMeasuredWidth(), imageView.getMeasuredHeight());
        }

        public void setTextAndIcon(String text, Drawable icon, boolean divider) {
            textView.setText(text);
            imageView.setImageDrawable(icon);
        }
    }

    public static class SuggestedFilterCell extends FrameLayout {

        private TextView textView;
        private TextView valueTextView;
        private ProgressButton addButton;
        private boolean needDivider;
        private TLRPC.TL_dialogFilterSuggested suggestedFilter;

        public SuggestedFilterCell(Context context) {
            super(context);

            textView = new TextView(context);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            textView.setLines(1);
            textView.setMaxLines(1);
            textView.setSingleLine(true);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 22, 10, 22, 0));

            valueTextView = new TextView(context);
            valueTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            valueTextView.setLines(1);
            valueTextView.setMaxLines(1);
            valueTextView.setSingleLine(true);
            valueTextView.setEllipsize(TextUtils.TruncateAt.END);
            valueTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            addView(valueTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 22, 35, 22, 0));

            addButton = new ProgressButton(context);
            addButton.setText(LocaleController.getString("Add", R.string.Add));
            addButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
            addButton.setProgressColor(Theme.getColor(Theme.key_featuredStickers_buttonProgress));
            addButton.setBackgroundRoundRect(Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_addButtonPressed));
            addView(addButton, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, 28, Gravity.TOP | Gravity.END, 0, 18, 14, 0));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(64));
            measureChildWithMargins(addButton, widthMeasureSpec, 0, heightMeasureSpec, 0);
            measureChildWithMargins(textView, widthMeasureSpec, addButton.getMeasuredWidth(), heightMeasureSpec, 0);
            measureChildWithMargins(valueTextView, widthMeasureSpec, addButton.getMeasuredWidth(), heightMeasureSpec, 0);
        }

        public void setFilter(TLRPC.TL_dialogFilterSuggested filter, boolean divider) {
            needDivider = divider;
            suggestedFilter = filter;
            setWillNotDraw(!needDivider);

            textView.setText(filter.filter.title);
            valueTextView.setText(filter.description);
        }

        public TLRPC.TL_dialogFilterSuggested getSuggestedFilter() {
            return suggestedFilter;
        }

        public void setAddOnClickListener(OnClickListener onClickListener) {
            addButton.setOnClickListener(onClickListener);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (needDivider) {
                canvas.drawLine(0, getHeight() - 1, getWidth() - getPaddingRight(), getHeight() - 1, Theme.dividerPaint);
            }
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setEnabled(true);
            info.setText(addButton.getText());
            info.setClassName("android.widget.Button");
        }
    }

    @SuppressWarnings("FieldCanBeLocal")
    public static class HintInnerCell extends FrameLayout {

        private RLottieImageView imageView;
        private TextView messageTextView;

        public HintInnerCell(Context context, int resId, CharSequence text) {
            super(context);

            imageView = new RLottieImageView(context);
            imageView.setAnimation(resId, 90, 90);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            imageView.playAnimation();
            imageView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(imageView, LayoutHelper.createFrame(90, 90, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 14, 0, 0));
            imageView.setOnClickListener(v -> {
                if (!imageView.isPlaying()) {
                    imageView.setProgress(0.0f);
                    imageView.playAnimation();
                }
            });

            messageTextView = new TextView(context);
            messageTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4));
            messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            messageTextView.setGravity(Gravity.CENTER);
            messageTextView.setText(text);
            addView(messageTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 40, 121, 40, 24));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), heightMeasureSpec);
        }
    }

    public class FilterCell extends FrameLayout {

        private final SimpleTextView textView;
        private final TextView valueTextView;
        @SuppressWarnings("FieldCanBeLocal")
        private final ImageView moveImageView;
        @SuppressWarnings("FieldCanBeLocal")
        private final ImageView optionsImageView;
        private final ImageView shareImageView;
        private boolean shareLoading = false;
        private final LoadingDrawable shareLoadingDrawable;
        private boolean needDivider;
        float progressToLock;

        private MessagesController.DialogFilter currentFilter;

        public FilterCell(Context context) {
            super(context);
            setWillNotDraw(false);

            moveImageView = new ImageView(context);
            moveImageView.setFocusable(false);
            moveImageView.setScaleType(ImageView.ScaleType.CENTER);
            moveImageView.setImageResource(R.drawable.list_reorder);
            moveImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_stickers_menu), PorterDuff.Mode.MULTIPLY));
            moveImageView.setContentDescription(LocaleController.getString("FilterReorder", R.string.FilterReorder));
            moveImageView.setClickable(true);
            addView(moveImageView, LayoutHelper.createFrame(48, 48, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, 6, 0, 6, 0));

            textView = new SimpleTextView(context);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            textView.setTextSize(16);
            textView.setMaxLines(1);
            textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
            Drawable drawable = ContextCompat.getDrawable(getContext(), R.drawable.other_lockedfolders2);
            drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_stickers_menu), PorterDuff.Mode.MULTIPLY));
            textView.setRightDrawable(drawable);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 80 : 64, 14, LocaleController.isRTL ? 64 : 80, 0));

            valueTextView = new TextView(context);
            valueTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            valueTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            valueTextView.setLines(1);
            valueTextView.setMaxLines(1);
            valueTextView.setSingleLine(true);
            valueTextView.setPadding(0, 0, 0, 0);
            valueTextView.setEllipsize(TextUtils.TruncateAt.END);
            addView(valueTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 80 : 64, 35, LocaleController.isRTL ? 64 : 80, 0));
            valueTextView.setVisibility(GONE);

            shareLoadingDrawable = new LoadingDrawable();
            shareLoadingDrawable.setAppearByGradient(true);
            shareLoadingDrawable.setGradientScale(2f);
            int selector = Theme.getColor(Theme.key_listSelector);
            shareLoadingDrawable.setColors(
                Theme.multAlpha(selector, 0.4f),
                Theme.multAlpha(selector, 1),
                Theme.multAlpha(selector, 0.9f),
                Theme.multAlpha(selector, 1.7f)
            );
            int stroke = AndroidUtilities.dp(1);
            shareLoadingDrawable.strokePaint.setStrokeWidth(stroke);
            shareLoadingDrawable.setRadiiDp(40);
            shareImageView = new ImageView(context) {
                @Override
                protected void onDraw(Canvas canvas) {
                    super.onDraw(canvas);
                    if (shareLoading) {
                        shareLoadingDrawable.setBounds(stroke / 2, stroke / 2, getWidth() - stroke / 2, getHeight() - stroke / 2);
                        shareLoadingDrawable.draw(canvas);
                    }
                }

                @Override
                protected boolean verifyDrawable(@NonNull Drawable dr) {
                    return dr == shareLoadingDrawable || super.verifyDrawable(dr);
                }
            };
            shareLoadingDrawable.setCallback(shareImageView);
            shareImageView.setFocusable(false);
            shareImageView.setScaleType(ImageView.ScaleType.CENTER);
            shareImageView.setBackground(Theme.createSelectorDrawable(selector));
            shareImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_stickers_menu), PorterDuff.Mode.MULTIPLY));
            shareImageView.setContentDescription(LocaleController.getString("FilterShare", R.string.FilterShare));
            shareImageView.setVisibility(View.GONE);
            shareImageView.setImageResource(R.drawable.msg_link_folder);
            shareImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_stickers_menu), PorterDuff.Mode.MULTIPLY));
            addView(shareImageView, LayoutHelper.createFrame(40, 40, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, LocaleController.isRTL ? 52 : 6, 0, LocaleController.isRTL ? 6 : 52, 0));
            shareImageView.setOnClickListener(e -> {
                if (shareLoading && !shareLoadingDrawable.isDisappeared() || currentFilter == null) {
                    return;
                }
                shareLoading = true;
                shareLoadingDrawable.reset();
                shareLoadingDrawable.resetDisappear();
                shareImageView.invalidate();
                FilterCreateActivity.FilterInvitesBottomSheet.show(FiltersSetupActivity.this, currentFilter, () -> {
                    shareLoadingDrawable.disappear();
                    shareImageView.invalidate();
                    updateRows(true);
                });
            });

            optionsImageView = new ImageView(context);
            optionsImageView.setFocusable(false);
            optionsImageView.setScaleType(ImageView.ScaleType.CENTER);
            optionsImageView.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector)));
            optionsImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_stickers_menu), PorterDuff.Mode.MULTIPLY));
            optionsImageView.setImageResource(R.drawable.msg_actions);
            optionsImageView.setContentDescription(LocaleController.getString("AccDescrMoreOptions", R.string.AccDescrMoreOptions));
            addView(optionsImageView, LayoutHelper.createFrame(40, 40, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, 6, 0, 6, 0));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(50), MeasureSpec.EXACTLY));
        }

        public void setFilter(MessagesController.DialogFilter filter, boolean divider) {
            int oldId = currentFilter == null ? -1 : currentFilter.id;
            currentFilter = filter;
            int newId = currentFilter == null ? -1 : currentFilter.id;
            boolean animated = oldId != newId;

            shareImageView.setVisibility(filter.isChatlist() ? VISIBLE : GONE);

            StringBuilder info = new StringBuilder();
            if (filter.isDefault() || (filter.flags & MessagesController.DIALOG_FILTER_FLAG_ALL_CHATS) == MessagesController.DIALOG_FILTER_FLAG_ALL_CHATS) {
                info.append(LocaleController.getString("FilterAllChats", R.string.FilterAllChats));
            } else {
                if ((filter.flags & MessagesController.DIALOG_FILTER_FLAG_CONTACTS) != 0) {
                    if (info.length() != 0) {
                        info.append(", ");
                    }
                    info.append(LocaleController.getString("FilterContacts", R.string.FilterContacts));
                }
                if ((filter.flags & MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS) != 0) {
                    if (info.length() != 0) {
                        info.append(", ");
                    }
                    info.append(LocaleController.getString("FilterNonContacts", R.string.FilterNonContacts));
                }
                if ((filter.flags & MessagesController.DIALOG_FILTER_FLAG_GROUPS) != 0) {
                    if (info.length() != 0) {
                        info.append(", ");
                    }
                    info.append(LocaleController.getString("FilterGroups", R.string.FilterGroups));
                }
                if ((filter.flags & MessagesController.DIALOG_FILTER_FLAG_CHANNELS) != 0) {
                    if (info.length() != 0) {
                        info.append(", ");
                    }
                    info.append(LocaleController.getString("FilterChannels", R.string.FilterChannels));
                }
                if ((filter.flags & MessagesController.DIALOG_FILTER_FLAG_BOTS) != 0) {
                    if (info.length() != 0) {
                        info.append(", ");
                    }
                    info.append(LocaleController.getString("FilterBots", R.string.FilterBots));
                }
            }
            if (!filter.alwaysShow.isEmpty() || !filter.neverShow.isEmpty()) {
                if (info.length() != 0) {
                    info.append(", ");
                }
                info.append(LocaleController.formatPluralString("Exception", filter.alwaysShow.size() + filter.neverShow.size()));
            }
            if (info.length() == 0) {
                info.append(LocaleController.getString("FilterNoChats", R.string.FilterNoChats));
            }

            String name = filter.name;
            if (filter.isDefault()) {
                name = LocaleController.getString("FilterAllChats", R.string.FilterAllChats);
            }
            if (!animated) {
                progressToLock = currentFilter.locked ? 1f : 0;
            }
            textView.setText(Emoji.replaceEmoji(name, textView.getPaint().getFontMetricsInt(), AndroidUtilities.dp(20), false));

            valueTextView.setText(info);
            needDivider = divider;

            if (filter.isDefault()) {
                optionsImageView.setVisibility(View.GONE);
            } else {
                optionsImageView.setVisibility(View.VISIBLE);
            }
            invalidate();
        }

        public MessagesController.DialogFilter getCurrentFilter() {
            return currentFilter;
        }

        public void setOnOptionsClick(OnClickListener listener) {
            optionsImageView.setOnClickListener(listener);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (needDivider) {
                canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(62), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(62) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
            }
            if (currentFilter != null) {
                if (currentFilter.locked && progressToLock != 1f) {
                    progressToLock += 16 / 150f;
                    invalidate();
                } else if (!currentFilter.locked && progressToLock != 0) {
                    progressToLock -= 16 / 150f;
                    invalidate();
                }
            }
            progressToLock = Utilities.clamp(progressToLock, 1f, 0f);
            textView.setRightDrawableScale(progressToLock);
            textView.invalidate();
        }

        @SuppressLint("ClickableViewAccessibility")
        public void setOnReorderButtonTouchListener(OnTouchListener listener) {
            moveImageView.setOnTouchListener(listener);
        }
    }

    @Override
    public boolean onFragmentCreate() {
        updateRows(false);
        getMessagesController().loadRemoteFilters(true);
        getNotificationCenter().addObserver(this, NotificationCenter.dialogFiltersUpdated);
        getNotificationCenter().addObserver(this, NotificationCenter.suggestedFiltersLoaded);
        if (getMessagesController().suggestedFilters.isEmpty()) {
            getMessagesController().loadSuggestedFilters();
        }
        return super.onFragmentCreate();
    }

    private ArrayList<ItemInner> oldItems = new ArrayList<>();
    private ArrayList<ItemInner> items = new ArrayList<>();

    private int filtersStartPosition;
    private int filtersSectionStart = -1, filtersSectionEnd = -1;

    private void updateRows(boolean animated) {
        oldItems.clear();
        oldItems.addAll(items);
        items.clear();

        ArrayList<TLRPC.TL_dialogFilterSuggested> suggestedFilters = getMessagesController().suggestedFilters;
        ArrayList<MessagesController.DialogFilter> dialogFilters = getMessagesController().getDialogFilters();
        items.add(ItemInner.asHint());
        if (!suggestedFilters.isEmpty() && dialogFilters.size() < 10) {
            items.add(ItemInner.asHeader(LocaleController.getString("FilterRecommended", R.string.FilterRecommended)));
            for (int i = 0; i < suggestedFilters.size(); ++i) {
                items.add(ItemInner.asSuggested(suggestedFilters.get(i)));
            }
            items.add(ItemInner.asShadow(null));
        }
        if (!dialogFilters.isEmpty()) {
            filtersSectionStart = items.size();
            items.add(ItemInner.asHeader(LocaleController.getString("Filters", R.string.Filters)));
            filtersStartPosition = items.size();
            for (int i = 0; i < dialogFilters.size(); ++i) {
                items.add(ItemInner.asFilter(dialogFilters.get(i)));
            }
            filtersSectionEnd = items.size();
        } else {
            filtersSectionStart = filtersSectionEnd = -1;
        }
        if (dialogFilters.size() < getMessagesController().dialogFiltersLimitPremium) {
            items.add(ItemInner.asButton(LocaleController.getString("CreateNewFilter", R.string.CreateNewFilter)));
        }
        items.add(ItemInner.asShadow(null));

        if (adapter != null) {
            if (animated) {
                adapter.setItems(oldItems, items);
            } else {
                adapter.notifyDataSetChanged();
            }
        }
    }

    @Override
    public void onFragmentDestroy() {
        getNotificationCenter().removeObserver(this, NotificationCenter.dialogFiltersUpdated);
        getNotificationCenter().removeObserver(this, NotificationCenter.suggestedFiltersLoaded);
        if (orderChanged) {
            getNotificationCenter().postNotificationName(NotificationCenter.dialogFiltersUpdated);
            getMessagesStorage().saveDialogFiltersOrder();
            TLRPC.TL_messages_updateDialogFiltersOrder req = new TLRPC.TL_messages_updateDialogFiltersOrder();
            ArrayList<MessagesController.DialogFilter> filters = getMessagesController().getDialogFilters();
            for (int a = 0, N = filters.size(); a < N; a++) {
                MessagesController.DialogFilter filter = filters.get(a);
                req.order.add(filter.id);
            }
            getConnectionsManager().sendRequest(req, (response, error) -> {

            });
        }
        super.onFragmentDestroy();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("Filters", R.string.Filters));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new RecyclerListView(context) {
            @Override
            public boolean onTouchEvent(MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
                    AndroidUtilities.runOnUIThread(() -> {
                        getMessagesController().lockFiltersInternal();
                    }, 250);
                }
                return super.onTouchEvent(e);
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                drawSectionBackground(canvas, filtersSectionStart, filtersSectionEnd, Theme.getColor(Theme.key_windowBackgroundWhite));
                super.dispatchDraw(canvas);
            }
        };
        DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
        itemAnimator.setDurations(350);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setSupportsChangeAnimations(false);
        listView.setItemAnimator(itemAnimator);
        ((DefaultItemAnimator) listView.getItemAnimator()).setDelayAnimations(false);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        itemTouchHelper = new ItemTouchHelper(new TouchHelperCallback());
        itemTouchHelper.attachToRecyclerView(listView);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setAdapter(adapter = new ListAdapter(context));
        listView.setOnItemClickListener((view, position, x, y) -> {
            if (position < 0 || position >= items.size()) {
                return;
            }
            ItemInner item = items.get(position);
            if (item == null) {
                return;
            }
            if (item.viewType == VIEW_TYPE_FILTER) {
                MessagesController.DialogFilter filter = item.filter;
                if (filter == null || filter.isDefault()) {
                    return;
                }
                if (filter.locked) {
                    showDialog(new LimitReachedBottomSheet(this, context, LimitReachedBottomSheet.TYPE_FOLDERS, currentAccount));
                } else {
                    presentFragment(new FilterCreateActivity(filter));
                }
            } else if (item.viewType == VIEW_TYPE_BUTTON) {
                final int count = getMessagesController().getDialogFilters().size();
                if (
                    count - 1 >= getMessagesController().dialogFiltersLimitDefault && !getUserConfig().isPremium() ||
                    count >= getMessagesController().dialogFiltersLimitPremium
                ) {
                    showDialog(new LimitReachedBottomSheet(this, context, LimitReachedBottomSheet.TYPE_FOLDERS, currentAccount));
                } else {
                    presentFragment(new FilterCreateActivity());
                }
            }
        });

        return fragmentView;
    }

    public UndoView getUndoView() {
        if (getContext() == null) {
            return null;
        }
        if (undoView == null) {
            ((FrameLayout) fragmentView).addView(undoView = new UndoView(getContext()), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 8, 0, 8, 8));
        }
        return undoView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.dialogFiltersUpdated) {
            if (ignoreUpdates) {
                return;
            }
            updateRows(true);
        } else if (id == NotificationCenter.suggestedFiltersLoaded) {
            updateRows(true);
        }
    }

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_HINT = 1;
    private static final int VIEW_TYPE_FILTER = 2;
    private static final int VIEW_TYPE_SHADOW = 3;
    private static final int VIEW_TYPE_BUTTON = 4;
    private static final int VIEW_TYPE_FILTER_SUGGESTION = 5;

    private static class ItemInner extends AdapterWithDiffUtils.Item {
        public ItemInner(int viewType) {
            super(viewType, false);
        }

        CharSequence text;
        MessagesController.DialogFilter filter;
        TLRPC.TL_dialogFilterSuggested suggested;

        public static ItemInner asHeader(CharSequence text) {
            ItemInner i = new ItemInner(VIEW_TYPE_HEADER);
            i.text = text;
            return i;
        }
        public static ItemInner asHint() {
            return new ItemInner(VIEW_TYPE_HINT);
        }
        public static ItemInner asShadow(CharSequence text) {
            ItemInner i = new ItemInner(VIEW_TYPE_SHADOW);
            i.text = text;
            return i;
        }
        public static ItemInner asFilter(MessagesController.DialogFilter filter) {
            ItemInner i = new ItemInner(VIEW_TYPE_FILTER);
            i.filter = filter;
            return i;
        }
        public static ItemInner asButton(CharSequence text) {
            ItemInner i = new ItemInner(VIEW_TYPE_BUTTON);
            i.text = text;
            return i;
        }
        public static ItemInner asSuggested(TLRPC.TL_dialogFilterSuggested suggested) {
            ItemInner i = new ItemInner(VIEW_TYPE_FILTER_SUGGESTION);
            i.suggested = suggested;
            return i;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof ItemInner)) {
                return false;
            }
            ItemInner other = (ItemInner) obj;
            if (other.viewType != viewType) {
                return false;
            }
            if (viewType == VIEW_TYPE_HEADER || viewType == VIEW_TYPE_BUTTON || viewType == VIEW_TYPE_SHADOW) {
                if (!TextUtils.equals(text, other.text)) {
                    return false;
                }
            }
            if (viewType == VIEW_TYPE_FILTER) {
                if ((filter == null) != (other.filter == null)) {
                    return false;
                }
                if (filter != null && filter.id != other.filter.id) {
                    return false;
                }
            }
            if (viewType == VIEW_TYPE_FILTER_SUGGESTION) {
                if ((suggested == null) != (other.suggested == null)) {
                    return false;
                }
                if (suggested != null && suggested.filter.id != other.suggested.filter.id) {
                    return false;
                }
            }
            return true;
        }
    }

    private class ListAdapter extends AdapterWithDiffUtils {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return type != VIEW_TYPE_SHADOW && type != VIEW_TYPE_HEADER && type != VIEW_TYPE_FILTER_SUGGESTION && type != VIEW_TYPE_HINT;
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case VIEW_TYPE_HEADER:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_HINT:
                    view = new HintInnerCell(mContext, R.raw.filters, AndroidUtilities.replaceTags(LocaleController.formatString("CreateNewFilterInfo", R.string.CreateNewFilterInfo)));
                    view.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_top, Theme.key_windowBackgroundGrayShadow));
                    break;
                case VIEW_TYPE_FILTER:
                    FilterCell filterCell = new FilterCell(mContext);
                    filterCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    filterCell.setOnReorderButtonTouchListener((v, event) -> {
                        if (event.getAction() == MotionEvent.ACTION_DOWN) {
                            itemTouchHelper.startDrag(listView.getChildViewHolder(filterCell));
                        }
                        return false;
                    });
                    filterCell.setOnOptionsClick(v -> {
                        FilterCell cell = (FilterCell) v.getParent();
                        MessagesController.DialogFilter filter = cell.getCurrentFilter();
                        ItemOptions options = ItemOptions.makeOptions(FiltersSetupActivity.this, cell);
                        options.add(R.drawable.msg_edit, LocaleController.getString("FilterEditItem", R.string.FilterEditItem), () -> {
                            if (filter.locked) {
                                showDialog(new LimitReachedBottomSheet(FiltersSetupActivity.this, mContext, LimitReachedBottomSheet.TYPE_FOLDERS, currentAccount));
                            } else {
                                presentFragment(new FilterCreateActivity(filter));
                            }
                        });
                        options.add(R.drawable.msg_delete, LocaleController.getString("FilterDeleteItem", R.string.FilterDeleteItem), true, () -> {
                            if (filter.isChatlist()) {
                                FolderBottomSheet.showForDeletion(FiltersSetupActivity.this, filter.id, success -> {
                                    updateRows(true);
                                });
                                return;
                            }

                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                            builder.setTitle(LocaleController.getString("FilterDelete", R.string.FilterDelete));
                            builder.setMessage(LocaleController.getString("FilterDeleteAlert", R.string.FilterDeleteAlert));
                            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                            builder.setPositiveButton(LocaleController.getString("Delete", R.string.Delete), (dialog2, which2) -> {
                                AlertDialog progressDialog = null;
                                if (getParentActivity() != null) {
                                    progressDialog = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
                                    progressDialog.setCanCancel(false);
                                    progressDialog.show();
                                }
                                final AlertDialog progressDialogFinal = progressDialog;
                                TLRPC.TL_messages_updateDialogFilter req = new TLRPC.TL_messages_updateDialogFilter();
                                req.id = filter.id;
                                getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                                    try {
                                        if (progressDialogFinal != null) {
                                            progressDialogFinal.dismiss();
                                        }
                                    } catch (Exception e) {
                                        FileLog.e(e);
                                    }
                                    getMessagesController().removeFilter(filter);
                                    getMessagesStorage().deleteDialogFilter(filter);
                                }));
                            });
                            AlertDialog alertDialog = builder.create();
                            showDialog(alertDialog);
                            TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                            if (button != null) {
                                button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
                            }
                        });
                        if (LocaleController.isRTL) {
                            options.setGravity(Gravity.LEFT);
                        }
                        options.show();
                    });
                    view = filterCell;
                    break;
                case VIEW_TYPE_SHADOW:
                    view = new ShadowSectionCell(mContext);
                    break;
                case VIEW_TYPE_BUTTON:
                    view = new TextCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_FILTER_SUGGESTION:
                default:
                    SuggestedFilterCell suggestedFilterCell = new SuggestedFilterCell(mContext);
                    suggestedFilterCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    suggestedFilterCell.setAddOnClickListener(v -> {
                        TLRPC.TL_dialogFilterSuggested suggested = suggestedFilterCell.getSuggestedFilter();
                        MessagesController.DialogFilter filter = new MessagesController.DialogFilter();
                        filter.name = suggested.filter.title;
                        filter.id = 2;
                        while (getMessagesController().dialogFiltersById.get(filter.id) != null) {
                            filter.id++;
                        }
                        filter.order = getMessagesController().getDialogFilters().size();
                        filter.pendingUnreadCount = filter.unreadCount = -1;
                        for (int b = 0; b < 2; b++) {
                            ArrayList<TLRPC.InputPeer> fromArray = b == 0 ? suggested.filter.include_peers : suggested.filter.exclude_peers;
                            ArrayList<Long> toArray = b == 0 ? filter.alwaysShow : filter.neverShow;
                            for (int a = 0, N = fromArray.size(); a < N; a++) {
                                TLRPC.InputPeer peer = fromArray.get(a);
                                long lowerId;
                                if (peer.user_id != 0) {
                                    lowerId = peer.user_id;
                                } else if (peer.chat_id != 0) {
                                    lowerId = -peer.chat_id;
                                } else {
                                    lowerId = -peer.channel_id;
                                }
                                toArray.add(lowerId);
                            }
                        }
                        if (suggested.filter.groups) {
                            filter.flags |= MessagesController.DIALOG_FILTER_FLAG_GROUPS;
                        }
                        if (suggested.filter.bots) {
                            filter.flags |= MessagesController.DIALOG_FILTER_FLAG_BOTS;
                        }
                        if (suggested.filter.contacts) {
                            filter.flags |= MessagesController.DIALOG_FILTER_FLAG_CONTACTS;
                        }
                        if (suggested.filter.non_contacts) {
                            filter.flags |= MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS;
                        }
                        if (suggested.filter.broadcasts) {
                            filter.flags |= MessagesController.DIALOG_FILTER_FLAG_CHANNELS;
                        }
                        if (suggested.filter.exclude_archived) {
                            filter.flags |= MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED;
                        }
                        if (suggested.filter.exclude_read) {
                            filter.flags |= MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_READ;
                        }
                        if (suggested.filter.exclude_muted) {
                            filter.flags |= MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED;
                        }
                        FilterCreateActivity.saveFilterToServer(filter, filter.flags, filter.name, filter.alwaysShow, filter.neverShow, filter.pinnedDialogs, true, true, true, true, true, FiltersSetupActivity.this, () -> {
                            getMessagesController().suggestedFilters.remove(suggested);
                            getNotificationCenter().postNotificationName(NotificationCenter.dialogFiltersUpdated);
                        });
                    });
                    view = suggestedFilterCell;
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            ItemInner item = items.get(position);
            if (item == null) {
                return;
            }
            boolean divider = position + 1 < items.size() && items.get(position + 1).viewType != VIEW_TYPE_SHADOW;
            switch (holder.getItemViewType()) {
                case VIEW_TYPE_HEADER: {
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    headerCell.setText(item.text);
                    break;
                }
                case VIEW_TYPE_FILTER: {
                    FilterCell filterCell = (FilterCell) holder.itemView;
                    filterCell.setFilter(item.filter, divider);
                    break;
                }
                case VIEW_TYPE_SHADOW: {
                    holder.itemView.setBackground(Theme.getThemedDrawable(mContext, divider ? R.drawable.greydivider : R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    break;
                }
                case VIEW_TYPE_BUTTON: {
                    TextCell textCell = (TextCell) holder.itemView;

                    Drawable drawable1 = mContext.getResources().getDrawable(R.drawable.poll_add_circle);
                    Drawable drawable2 = mContext.getResources().getDrawable(R.drawable.poll_add_plus);
                    drawable1.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_switchTrackChecked), PorterDuff.Mode.MULTIPLY));
                    drawable2.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_checkboxCheck), PorterDuff.Mode.MULTIPLY));
                    CombinedDrawable combinedDrawable = new CombinedDrawable(drawable1, drawable2);

                    textCell.setTextAndIcon(item.text + "", combinedDrawable, false);
                    break;
                }
                case VIEW_TYPE_FILTER_SUGGESTION: {
                    SuggestedFilterCell filterCell = (SuggestedFilterCell) holder.itemView;
                    filterCell.setFilter(item.suggested, divider);
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position < 0 || position >= items.size()) {
                return VIEW_TYPE_SHADOW;
            }
            ItemInner item = items.get(position);
            if (item == null) {
                return VIEW_TYPE_SHADOW;
            }
            return item.viewType;
        }

        public void swapElements(int fromPosition, int toPosition) {
            if (fromPosition < filtersStartPosition || toPosition < filtersStartPosition) {
                return;
            }
            ItemInner from = items.get(fromPosition);
            ItemInner to = items.get(toPosition);
            if (from == null || to == null || from.filter == null || to.filter == null) {
                return;
            }
            int temp = from.filter.order;
            from.filter.order = to.filter.order;
            to.filter.order = temp;
            ArrayList<MessagesController.DialogFilter> filters = getMessagesController().dialogFilters;
            try {
                filters.set(fromPosition - filtersStartPosition, to.filter);
                filters.set(toPosition - filtersStartPosition, from.filter);
            } catch (Exception ignore) {}
            orderChanged = true;
            updateRows(true);
        }

        public void moveElementToStart(int index) {
            ArrayList<MessagesController.DialogFilter> filters = getMessagesController().dialogFilters;
            if (index < 0 || index >= filters.size()) {
                return;
            }
            filters.add(0, filters.remove(index));
            for (int i = 0; i <= index; ++i) {
                filters.get(i).order = i;
            }
            orderChanged = true;
            updateRows(true);
        }
    }

    public class TouchHelperCallback extends ItemTouchHelper.Callback {

        @Override
        public boolean isLongPressDragEnabled() {
            return true;
        }

        @Override
        public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            if (viewHolder.getItemViewType() != VIEW_TYPE_FILTER) {
                return makeMovementFlags(0, 0);
            }
            return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
        }

        @Override
        public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder source, RecyclerView.ViewHolder target) {
            if (source.getItemViewType() != target.getItemViewType()) {
                return false;
            }
            adapter.swapElements(source.getAdapterPosition(), target.getAdapterPosition());
            return true;
        }

        @Override
        public void onChildDraw(Canvas c, RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
        }

        private void resetDefaultPosition() {
            if (UserConfig.getInstance(UserConfig.selectedAccount).isPremium()) {
                return;
            }
            ArrayList<MessagesController.DialogFilter> filters = getMessagesController().getDialogFilters();
            for (int i = 0; i < filters.size(); ++i) {
                if (filters.get(i).isDefault() && i != 0) {
                    adapter.moveElementToStart(i);
                    listView.scrollToPosition(0);
                    onDefaultTabMoved();
                    break;
                }
            }
        }

        @Override
        public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
            if (actionState != ItemTouchHelper.ACTION_STATE_IDLE) {
                listView.cancelClickRunnables(false);
                viewHolder.itemView.setPressed(true);
            } else {
                AndroidUtilities.cancelRunOnUIThread(this::resetDefaultPosition);
                AndroidUtilities.runOnUIThread(this::resetDefaultPosition, 320);
            }
            super.onSelectedChanged(viewHolder, actionState);
        }

        @Override
        public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {

        }

        @Override
        public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            super.clearView(recyclerView, viewHolder);
            viewHolder.itemView.setPressed(false);
        }
    }

    protected void onDefaultTabMoved() {
        try {
            fragmentView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
        } catch (Exception ignore) {}
        BulletinFactory.of(this).createSimpleBulletin(R.raw.filter_reorder, AndroidUtilities.replaceTags(LocaleController.formatString("LimitReachedReorderFolder", R.string.LimitReachedReorderFolder, LocaleController.getString(R.string.FilterAllChats))), LocaleController.getString("PremiumMore", R.string.PremiumMore), Bulletin.DURATION_PROLONG, () -> {
            showDialog(new PremiumFeatureBottomSheet(FiltersSetupActivity.this, PremiumPreviewFragment.PREMIUM_FEATURE_ADVANCED_CHAT_MANAGEMENT, true));
        }).show();
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{HeaderCell.class, TextCell.class, FilterCell.class, SuggestedFilterCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{FilterCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{FilterCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{FilterCell.class}, new String[]{"moveImageView"}, null, null, null, Theme.key_stickers_menu));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{FilterCell.class}, new String[]{"optionsImageView"}, null, null, null, Theme.key_stickers_menu));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, new Class[]{FilterCell.class}, new String[]{"optionsImageView"}, null, null, null, Theme.key_stickers_menuSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueText2));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_switchTrackChecked));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_checkboxCheck));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));

        return themeDescriptions;
    }
}
