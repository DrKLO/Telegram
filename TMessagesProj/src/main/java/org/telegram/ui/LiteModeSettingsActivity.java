package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ImageSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.BatteryDrawable;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.IntSeekBarAccessibilityDelegate;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ListView.AdapterWithDiffUtils;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SeekBarAccessibilityDelegate;
import org.telegram.ui.Components.SeekBarView;
import org.telegram.ui.Components.Switch;

import java.util.ArrayList;

public class LiteModeSettingsActivity extends BaseFragment {

    FrameLayout contentView;

    RecyclerListView listView;
    LinearLayoutManager layoutManager;
    Adapter adapter;

    Bulletin restrictBulletin;

    private int FLAGS_CHAT;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("PowerUsage", R.string.PowerUsage));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        contentView = new FrameLayout(context);
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context));
        listView.setAdapter(adapter = new Adapter());
        DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
        itemAnimator.setDurations(350);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setSupportsChangeAnimations(false);
        listView.setItemAnimator(itemAnimator);
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setOnItemClickListener((view, position, x, y) -> {
            if (view == null || position < 0 || position >= items.size()) {
                return;
            }
            final Item item = items.get(position);

            if (item.viewType == VIEW_TYPE_SWITCH || item.viewType == VIEW_TYPE_CHECKBOX) {
                if (LiteMode.isPowerSaverApplied()) {
                    restrictBulletin = BulletinFactory.of(this).createSimpleBulletin(new BatteryDrawable(.1f, Color.WHITE, Theme.getColor(Theme.key_dialogSwipeRemove), 1.3f), LocaleController.getString("LiteBatteryRestricted", R.string.LiteBatteryRestricted)).show();
                    return;
                }
                if (item.viewType == VIEW_TYPE_SWITCH && item.getFlagsCount() > 1 && (LocaleController.isRTL ? x > dp(19 + 37 + 19) : x < view.getMeasuredWidth() - dp(19 + 37 + 19))) {
                    int index = getExpandedIndex(item.flags);
                    if (index != -1) {
                        expanded[index] = !expanded[index];
                        updateValues();
                        updateItems();
                        return;
                    }
                }
                boolean value = LiteMode.isEnabledSetting(item.flags);
                LiteMode.toggleFlag(item.flags, !value);
                updateValues();
            } else if (item.viewType == VIEW_TYPE_SWITCH2) {
                if (item.type == SWITCH_TYPE_SMOOTH_TRANSITIONS) {
                    SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                    boolean animations = preferences.getBoolean("view_animations", true);
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putBoolean("view_animations", !animations);
                    SharedConfig.setAnimationsEnabled(!animations);
                    editor.commit();
                    ((TextCell) view).setChecked(!animations);
                }
            }
        });

        fragmentView = contentView;
        FLAGS_CHAT = AndroidUtilities.isTablet() ? (LiteMode.FLAGS_CHAT & ~LiteMode.FLAG_CHAT_FORUM_TWOCOLUMN) : LiteMode.FLAGS_CHAT;

        updateItems();

        return fragmentView;
    }

    @Override
    public void onBecomeFullyVisible() {
        super.onBecomeFullyVisible();
        LiteMode.addOnPowerSaverAppliedListener(onPowerAppliedChange);
    }

    @Override
    public void onBecomeFullyHidden() {
        super.onBecomeFullyHidden();
        LiteMode.removeOnPowerSaverAppliedListener(onPowerAppliedChange);
    }

    private Utilities.Callback<Boolean> onPowerAppliedChange = applied -> updateValues();

    private boolean[] expanded = new boolean[3];
    private int getExpandedIndex(int flags) {
        if (flags == LiteMode.FLAGS_ANIMATED_STICKERS) {
            return 0;
        } else if (flags == LiteMode.FLAGS_ANIMATED_EMOJI) {
            return 1;
        } else if (flags == FLAGS_CHAT) {
            return 2;
        }
        return -1;
    }

    public void setExpanded(int flags, boolean expand) {
        int i = getExpandedIndex(flags);
        if (i == -1) {
            return;
        }
        expanded[i] = expand;
        updateValues();
        updateItems();
    }

    public void scrollToType(int type) {
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            if (item.type == type) {
                highlightRow(i);
                break;
            }
        }
    }

    public void scrollToFlags(int flags) {
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            if (item.flags == flags) {
                highlightRow(i);
                break;
            }
        }
    }

    private void highlightRow(int index) {
        RecyclerListView.IntReturnCallback callback = () -> {
            layoutManager.scrollToPositionWithOffset(index, AndroidUtilities.dp(60));
            return index;
        };
        listView.highlightRow(callback);
    }

    private ArrayList<Item> oldItems = new ArrayList<>();
    private ArrayList<Item> items = new ArrayList<>();

    private void updateItems() {
        oldItems.clear();
        oldItems.addAll(items);

        items.clear();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            items.add(Item.asSlider());
            items.add(Item.asInfo(
                LiteMode.getPowerSaverLevel() <= 0 ?
                    LocaleController.getString(R.string.LiteBatteryInfoDisabled) :
                LiteMode.getPowerSaverLevel() >= 100 ?
                    LocaleController.getString(R.string.LiteBatteryInfoEnabled) :
                    LocaleController.formatString(R.string.LiteBatteryInfoBelow, String.format("%d%%", LiteMode.getPowerSaverLevel()))
            ));
        }

        items.add(Item.asHeader(LocaleController.getString("LiteOptionsTitle")));
        items.add(Item.asSwitch(R.drawable.msg2_sticker, LocaleController.getString("LiteOptionsStickers", R.string.LiteOptionsStickers), LiteMode.FLAGS_ANIMATED_STICKERS));
        if (expanded[0]) {
            items.add(Item.asCheckbox(LocaleController.getString("LiteOptionsAutoplayKeyboard"), LiteMode.FLAG_ANIMATED_STICKERS_KEYBOARD));
            items.add(Item.asCheckbox(LocaleController.getString("LiteOptionsAutoplayChat"), LiteMode.FLAG_ANIMATED_STICKERS_CHAT));
        }
        items.add(Item.asSwitch(R.drawable.msg2_smile_status, LocaleController.getString("LiteOptionsEmoji", R.string.LiteOptionsEmoji), LiteMode.FLAGS_ANIMATED_EMOJI));
        if (expanded[1]) {
            items.add(Item.asCheckbox(LocaleController.getString("LiteOptionsAutoplayKeyboard"), LiteMode.FLAG_ANIMATED_EMOJI_KEYBOARD));
            items.add(Item.asCheckbox(LocaleController.getString("LiteOptionsAutoplayReactions"), LiteMode.FLAG_ANIMATED_EMOJI_REACTIONS));
            items.add(Item.asCheckbox(LocaleController.getString("LiteOptionsAutoplayChat"), LiteMode.FLAG_ANIMATED_EMOJI_CHAT));
        }
        items.add(Item.asSwitch(R.drawable.msg2_ask_question, LocaleController.getString("LiteOptionsChat"), FLAGS_CHAT));
        if (expanded[2]) {
            items.add(Item.asCheckbox(LocaleController.getString("LiteOptionsBackground"), LiteMode.FLAG_CHAT_BACKGROUND));
            if (!AndroidUtilities.isTablet()) {
                items.add(Item.asCheckbox(LocaleController.getString("LiteOptionsTopics"), LiteMode.FLAG_CHAT_FORUM_TWOCOLUMN));
            }
            items.add(Item.asCheckbox(LocaleController.getString("LiteOptionsSpoiler"), LiteMode.FLAG_CHAT_SPOILER));
            if (SharedConfig.getDevicePerformanceClass() >= SharedConfig.PERFORMANCE_CLASS_AVERAGE) {
                items.add(Item.asCheckbox(LocaleController.getString("LiteOptionsBlur"), LiteMode.FLAG_CHAT_BLUR));
            }
            items.add(Item.asCheckbox(LocaleController.getString("LiteOptionsScale"), LiteMode.FLAG_CHAT_SCALE));
        }
        items.add(Item.asSwitch(R.drawable.msg2_call_earpiece, LocaleController.getString("LiteOptionsCalls"), LiteMode.FLAG_CALLS_ANIMATIONS));
        items.add(Item.asSwitch(R.drawable.msg2_videocall, LocaleController.getString("LiteOptionsAutoplayVideo"), LiteMode.FLAG_AUTOPLAY_VIDEOS));
        items.add(Item.asSwitch(R.drawable.msg2_gif, LocaleController.getString("LiteOptionsAutoplayGifs"), LiteMode.FLAG_AUTOPLAY_GIFS));
        items.add(Item.asInfo(""));

        items.add(Item.asSwitch(LocaleController.getString("LiteSmoothTransitions"), SWITCH_TYPE_SMOOTH_TRANSITIONS));
        items.add(Item.asInfo(LocaleController.getString("LiteSmoothTransitionsInfo")));

        adapter.setItems(oldItems, items);
    }

    private void updateInfo() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }

        if (items.isEmpty()) {
            updateItems();
        } else if (items.size() >= 2) {
            items.set(1, Item.asInfo(
                LiteMode.getPowerSaverLevel() <= 0 ?
                    LocaleController.getString(R.string.LiteBatteryInfoDisabled) :
                LiteMode.getPowerSaverLevel() >= 100 ?
                    LocaleController.getString(R.string.LiteBatteryInfoEnabled) :
                    LocaleController.formatString(R.string.LiteBatteryInfoBelow, String.format("%d%%", LiteMode.getPowerSaverLevel()))
            ));
            adapter.notifyItemChanged(1);
        }
    }

    private void updateValues() {
        if (listView == null) {
            return;
        }
        for (int i = 0; i < listView.getChildCount(); ++i) {
            View child = listView.getChildAt(i);
            if (child == null) {
                continue;
            }
            int position = listView.getChildAdapterPosition(child);
            if (position < 0 || position >= items.size()) {
                continue;
            }
            Item item = items.get(position);
            if (item.viewType == VIEW_TYPE_SWITCH || item.viewType == VIEW_TYPE_CHECKBOX) {
                ((SwitchCell) child).update(item);
            } else if (item.viewType == VIEW_TYPE_SLIDER) {
                ((PowerSaverSlider) child).update();
            }
        }

        if (restrictBulletin != null && !LiteMode.isPowerSaverApplied()) {
            restrictBulletin.hide();
            restrictBulletin = null;
        }
    }

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_SLIDER = 1;
    private static final int VIEW_TYPE_INFO = 2;
    private static final int VIEW_TYPE_SWITCH = 3;
    private static final int VIEW_TYPE_CHECKBOX = 4;
    private static final int VIEW_TYPE_SWITCH2 = 5;

    public static final int SWITCH_TYPE_SMOOTH_TRANSITIONS = 1;

    private class Adapter extends AdapterWithDiffUtils {

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            final Context context = parent.getContext();
            View view = null;
            if (viewType == VIEW_TYPE_HEADER) {
                view = new HeaderCell(context);
                view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            } else if (viewType == VIEW_TYPE_SLIDER) {
                PowerSaverSlider powerSaverSlider = new PowerSaverSlider(context);
                view = powerSaverSlider;
                view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            } else if (viewType == VIEW_TYPE_INFO) {
                view = new TextInfoPrivacyCell(context) {
                    @Override
                    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                        super.onInitializeAccessibilityNodeInfo(info);

                        info.setEnabled(true);
                    }

                    @Override
                    public void onPopulateAccessibilityEvent(AccessibilityEvent event) {
                        super.onPopulateAccessibilityEvent(event);

                        event.setContentDescription(getTextView().getText());
                        setContentDescription(getTextView().getText());
                    }
                };
            } else if (viewType == VIEW_TYPE_SWITCH || viewType == VIEW_TYPE_CHECKBOX) {
                view = new SwitchCell(context);
            } else if (viewType == VIEW_TYPE_SWITCH2) {
                view = new TextCell(context, 23, false, true, null);
                view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (position < 0 || position >= items.size()) {
                return;
            }

            final LiteModeSettingsActivity.Item item = items.get(position);
            final int viewType = holder.getItemViewType();
            if (viewType == VIEW_TYPE_HEADER) {
                HeaderCell headerCell = (HeaderCell) holder.itemView;
                headerCell.setText(item.text);
            } else if (viewType == VIEW_TYPE_SLIDER) {
                PowerSaverSlider powerSaverSlider = (PowerSaverSlider) holder.itemView;
                powerSaverSlider.update();
//                updateSlider(slideChooseView);
            } else if (viewType == VIEW_TYPE_INFO) {
                TextInfoPrivacyCell textInfoPrivacyCell = (TextInfoPrivacyCell) holder.itemView;
                if (TextUtils.isEmpty(item.text)) {
                    textInfoPrivacyCell.setFixedSize(12);
                } else {
                    textInfoPrivacyCell.setFixedSize(0);
                }
                textInfoPrivacyCell.setText(item.text);
                textInfoPrivacyCell.setContentDescription(item.text);
                boolean top = position > 0 && items.get(position - 1).viewType != VIEW_TYPE_INFO;
                boolean bottom = position + 1 < items.size() && items.get(position + 1).viewType != VIEW_TYPE_INFO;
                if (top && bottom) {
                    textInfoPrivacyCell.setBackground(Theme.getThemedDrawableByKey(getContext(), R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                } else if (top) {
                    textInfoPrivacyCell.setBackground(Theme.getThemedDrawableByKey(getContext(), R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                } else if (bottom) {
                    textInfoPrivacyCell.setBackground(Theme.getThemedDrawableByKey(getContext(), R.drawable.greydivider_top, Theme.key_windowBackgroundGrayShadow));
                } else {
                    textInfoPrivacyCell.setBackground(null);
                }
            } else if (viewType == VIEW_TYPE_SWITCH || viewType == VIEW_TYPE_CHECKBOX) {
                final boolean divider = position + 1 < items.size() && items.get(position + 1).viewType != VIEW_TYPE_INFO;
                SwitchCell switchCell = (SwitchCell) holder.itemView;
                switchCell.set(item, divider);
            } else if (viewType == VIEW_TYPE_SWITCH2) {
                TextCell textCell = (TextCell) holder.itemView;
                if (item.type == SWITCH_TYPE_SMOOTH_TRANSITIONS) {
                    SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                    boolean animations = preferences.getBoolean("view_animations", true);
                    textCell.setTextAndCheck(item.text, animations, false);
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position < 0 || position >= items.size()) {
                return VIEW_TYPE_INFO;
            }
            return items.get(position).viewType;
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == VIEW_TYPE_CHECKBOX || holder.getItemViewType() == VIEW_TYPE_SWITCH || holder.getItemViewType() == VIEW_TYPE_SWITCH2;
        }
    }

    private class SwitchCell extends FrameLayout {

        private ImageView imageView;
        private LinearLayout textViewLayout;
        private TextView textView;
        private AnimatedTextView countTextView;
        private ImageView arrowView;
        private Switch switchView;
        private CheckBox2 checkBoxView;

        private boolean needDivider, needLine;

        public SwitchCell(Context context) {
            super(context);

            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

            imageView = new ImageView(context);
            imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.MULTIPLY));
            imageView.setVisibility(View.GONE);
            addView(imageView, LayoutHelper.createFrame(24, 24, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 20, 0, 20, 0));

            textView = new TextView(context) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.AT_MOST) {
                        widthMeasureSpec = MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec) - AndroidUtilities.dp(52), MeasureSpec.AT_MOST);
                    }
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                }
            };
            textView.setLines(1);
            textView.setSingleLine(true);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            textView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            textView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);

            countTextView = new AnimatedTextView(context, false, true, true);
            countTextView.setAnimationProperties(.35f, 0, 200, CubicBezierInterpolator.EASE_OUT_QUINT);
            countTextView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            countTextView.setTextSize(dp(14));
            countTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            countTextView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);

            arrowView = new ImageView(context);
            arrowView.setVisibility(GONE);
            arrowView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.MULTIPLY));
            arrowView.setImageResource(R.drawable.arrow_more);

            textViewLayout = new LinearLayout(context);
            textViewLayout.setOrientation(LinearLayout.HORIZONTAL);
            textViewLayout.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            if (LocaleController.isRTL) {
                textViewLayout.addView(arrowView, LayoutHelper.createLinear(16, 16, 0, Gravity.CENTER_VERTICAL, 0, 0, 6, 0));
                textViewLayout.addView(countTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, Gravity.CENTER_VERTICAL, 0, 0, 6, 0));
                textViewLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
            } else {
                textViewLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
                textViewLayout.addView(countTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, Gravity.CENTER_VERTICAL, 6, 0, 0, 0));
                textViewLayout.addView(arrowView, LayoutHelper.createLinear(16, 16, 0, Gravity.CENTER_VERTICAL, 2, 0, 0, 0));
            }
            addView(textViewLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 64, 0, 8, 0));

            switchView = new Switch(context);
            switchView.setVisibility(GONE);
            switchView.setColors(Theme.key_switchTrack, Theme.key_switchTrackChecked, Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
            switchView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(switchView, LayoutHelper.createFrame(37, 50, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT), 19, 0, 19, 0));

            checkBoxView = new CheckBox2(context, 21);
            checkBoxView.setColor(Theme.key_radioBackgroundChecked, Theme.key_checkboxDisabled, Theme.key_checkboxCheck);
            checkBoxView.setDrawUnchecked(true);
            checkBoxView.setChecked(true, false);
            checkBoxView.setDrawBackgroundAsArc(10);
            checkBoxView.setVisibility(GONE);
            checkBoxView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(checkBoxView, LayoutHelper.createFrame(21, 21, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 0 : 64, 0, LocaleController.isRTL ? 64 : 0, 0));

            setFocusable(true);
        }

        private boolean disabled;
        public void setDisabled(boolean disabled, boolean animated) {
            if (this.disabled != disabled) {
                this.disabled = disabled;
                if (animated) {
                    imageView.animate().alpha(disabled ? .5f : 1f).setDuration(220).start();
                    textViewLayout.animate().alpha(disabled ? .5f : 1f).setDuration(220).start();
                    switchView.animate().alpha(disabled ? .5f : 1f).setDuration(220).start();
                    checkBoxView.animate().alpha(disabled ? .5f : 1f).setDuration(220).start();
                } else {
                    imageView.setAlpha(disabled ? .5f : 1f);
                    textViewLayout.setAlpha(disabled ? .5f : 1f);
                    switchView.setAlpha(disabled ? .5f : 1f);
                    checkBoxView.setAlpha(disabled ? .5f : 1f);
                }
                setEnabled(!disabled);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp(50), MeasureSpec.EXACTLY)
            );
        }

        public void set(Item item, boolean divider) {
            if (item.viewType == VIEW_TYPE_SWITCH) {
                checkBoxView.setVisibility(GONE);
                imageView.setVisibility(VISIBLE);
                imageView.setImageResource(item.iconResId);
                textView.setText(item.text);
                if (containing = item.getFlagsCount() > 1) {
                    updateCount(item, false);
                    countTextView.setVisibility(VISIBLE);
                    arrowView.setVisibility(VISIBLE);
                } else {
                    countTextView.setVisibility(GONE);
                    arrowView.setVisibility(GONE);
                }
                textView.setTranslationX(0);
                switchView.setVisibility(VISIBLE);
                switchView.setChecked(LiteMode.isEnabled(item.flags), false);
                needLine = item.getFlagsCount() > 1;
            } else {
                checkBoxView.setVisibility(VISIBLE);
                checkBoxView.setChecked(LiteMode.isEnabled(item.flags), false);
                imageView.setVisibility(GONE);
                switchView.setVisibility(GONE);
                countTextView.setVisibility(GONE);
                arrowView.setVisibility(GONE);
                textView.setText(item.text);
                textView.setTranslationX(dp(41) * (LocaleController.isRTL ? -2.2f : 1));
                containing = false;
                needLine = false;
            }

            ((MarginLayoutParams) textViewLayout.getLayoutParams()).rightMargin = AndroidUtilities.dp(item.viewType == VIEW_TYPE_SWITCH ? (LocaleController.isRTL ? 64 : 75) + 4 : 8);

            setWillNotDraw(!((needDivider = divider) || needLine));
            setDisabled(LiteMode.isPowerSaverApplied(), false);
        }

        public void update(Item item) {
            if (item.viewType == VIEW_TYPE_SWITCH) {
                if (containing = item.getFlagsCount() > 1) {
                    updateCount(item, true);
                    int index = getExpandedIndex(item.flags);
                    arrowView.clearAnimation();
                    arrowView.animate().rotation(index >= 0 && expanded[index] ? 180 : 0).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).setDuration(240).start();
                }
                switchView.setChecked(LiteMode.isEnabled(item.flags), true);
            } else {
                checkBoxView.setChecked(LiteMode.isEnabled(item.flags), true);
            }

            setDisabled(LiteMode.isPowerSaverApplied(), true);
        }

        private boolean containing;
        private int enabled, all;

        private void updateCount(Item item, boolean animated) {
            enabled = preprocessFlagsCount(LiteMode.getValue(true) & item.flags);
            all = preprocessFlagsCount(item.flags);
            countTextView.setText(String.format("%d/%d", enabled, all), animated && !LocaleController.isRTL);
        }

        private int preprocessFlagsCount(int flags) {
            boolean isPremium = getUserConfig().isPremium();
            int count = Integer.bitCount(flags);
            if (isPremium) {
                if ((flags & LiteMode.FLAG_ANIMATED_EMOJI_CHAT_NOT_PREMIUM) > 0)
                    count--;
                if ((flags & LiteMode.FLAG_ANIMATED_EMOJI_REACTIONS_NOT_PREMIUM) > 0)
                    count--;
                if ((flags & LiteMode.FLAG_ANIMATED_EMOJI_KEYBOARD_NOT_PREMIUM) > 0)
                    count--;
            } else {
                if ((flags & LiteMode.FLAG_ANIMATED_EMOJI_CHAT_PREMIUM) > 0)
                    count--;
                if ((flags & LiteMode.FLAG_ANIMATED_EMOJI_REACTIONS_PREMIUM) > 0)
                    count--;
                if ((flags & LiteMode.FLAG_ANIMATED_EMOJI_KEYBOARD_PREMIUM) > 0)
                    count--;
            }
            if (SharedConfig.getDevicePerformanceClass() < SharedConfig.PERFORMANCE_CLASS_AVERAGE && (flags & LiteMode.FLAG_CHAT_BLUR) > 0) {
                count--;
            }
            return count;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (LocaleController.isRTL) {
                if (needLine) {
                    float x = dp(19 + 37 + 19);
                    canvas.drawRect(x - dp(0.66f), (getMeasuredHeight() - dp(20)) / 2f, x, (getMeasuredHeight() + dp(20)) / 2f, Theme.dividerPaint);
                }
                if (needDivider) {
                    canvas.drawLine(getMeasuredWidth() - dp(64) + (textView.getTranslationX() < 0 ? dp(-32) : 0), getMeasuredHeight() - 1, 0, getMeasuredHeight() - 1, Theme.dividerPaint);
                }
            } else {
                if (needLine) {
                    float x = getMeasuredWidth() - dp(19 + 37 + 19);
                    canvas.drawRect(x - dp(0.66f), (getMeasuredHeight() - dp(20)) / 2f, x, (getMeasuredHeight() + dp(20)) / 2f, Theme.dividerPaint);
                }
                if (needDivider) {
                    canvas.drawLine(dp(64) + textView.getTranslationX(), getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, Theme.dividerPaint);
                }
            }
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setClassName(checkBoxView.getVisibility() == View.VISIBLE ? "android.widget.CheckBox" : "android.widget.Switch");
            info.setCheckable(true);
            info.setEnabled(true);
            if (checkBoxView.getVisibility() == View.VISIBLE) {
                info.setChecked(checkBoxView.isChecked());
            } else {
                info.setChecked(switchView.isChecked());
            }
            StringBuilder sb = new StringBuilder();
            sb.append(textView.getText());
            if (containing) {
                sb.append('\n');
                sb.append(LocaleController.formatString("Of", R.string.Of, enabled, all));
            }
            info.setContentDescription(sb);
        }
    }

    private class PowerSaverSlider extends FrameLayout {

        BatteryDrawable batteryIcon;
        SpannableStringBuilder batteryText;

        LinearLayout headerLayout;
        TextView headerTextView;
        AnimatedTextView headerOnView;
        FrameLayout valuesView;
        TextView leftTextView;
        AnimatedTextView middleTextView;
        TextView rightTextView;
        SeekBarView seekBarView;

        private SeekBarAccessibilityDelegate seekBarAccessibilityDelegate;

        public PowerSaverSlider(Context context) {
            super(context);

            headerLayout = new LinearLayout(context);
            headerLayout.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            headerLayout.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);

            headerTextView = new TextView(context);
            headerTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            headerTextView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            headerTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
            headerTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            headerTextView.setText(LocaleController.getString("LiteBatteryTitle"));
            headerLayout.addView(headerTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

            headerOnView = new AnimatedTextView(context, true, false, false) {
                Drawable backgroundDrawable = Theme.createRoundRectDrawable(AndroidUtilities.dp(4), Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader), 0.15f));

                @Override
                protected void onDraw(Canvas canvas) {
                    backgroundDrawable.setBounds(0, 0, (int) (getPaddingLeft() + getDrawable().getCurrentWidth() + getPaddingRight()), getMeasuredHeight());
                    backgroundDrawable.draw(canvas);

                    super.onDraw(canvas);
                }
            };
            headerOnView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            headerOnView.setPadding(AndroidUtilities.dp(5.33f), AndroidUtilities.dp(2), AndroidUtilities.dp(5.33f), AndroidUtilities.dp(2));
            headerOnView.setTextSize(AndroidUtilities.dp(12));
            headerOnView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
            headerLayout.addView(headerOnView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 17, Gravity.CENTER_VERTICAL, 6, 1, 0, 0));

            addView(headerLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 21, 17, 21, 0));

            seekBarView = new SeekBarView(context, true, null);
            seekBarView.setReportChanges(true);
            seekBarView.setDelegate(new SeekBarView.SeekBarViewDelegate() {
                @Override
                public void onSeekBarDrag(boolean stop, float progress) {
                    int newValue = Math.round(progress * 100F);
                    if (newValue != LiteMode.getPowerSaverLevel()) {
                        LiteMode.setPowerSaverLevel(newValue);
                        updateValues();
                        updateInfo();

                        if (newValue <= 0 || newValue >= 100) {
                            try {
                                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                            } catch (Exception e) {}
                        }
                    }
                }
                @Override
                public void onSeekBarPressed(boolean pressed) {}
                @Override
                public CharSequence getContentDescription() {
                    return " ";
                }
            });
            seekBarView.setProgress(LiteMode.getPowerSaverLevel() / 100F);
            seekBarView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(seekBarView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38 + 6, Gravity.TOP, 6, 68, 6, 0));

            valuesView = new FrameLayout(context);
            valuesView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);

            leftTextView = new TextView(context);
            leftTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            leftTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            leftTextView.setGravity(Gravity.LEFT);
            leftTextView.setText(LocaleController.getString("LiteBatteryDisabled", R.string.LiteBatteryDisabled));
            valuesView.addView(leftTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL));

            middleTextView = new AnimatedTextView(context, false, true, true) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    int fullWidth = MeasureSpec.getSize(widthMeasureSpec);
                    if (fullWidth <= 0) {
                        fullWidth = AndroidUtilities.displaySize.x - dp(20);
                    }
                    float leftTextViewWidth = leftTextView.getPaint().measureText(leftTextView.getText().toString());
                    float rightTextViewWidth = rightTextView.getPaint().measureText(rightTextView.getText().toString());
                    super.onMeasure(MeasureSpec.makeMeasureSpec((int) (fullWidth - leftTextViewWidth - rightTextViewWidth), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(24), MeasureSpec.EXACTLY));
                }
            };
            middleTextView.setAnimationProperties(.45f, 0, 240, CubicBezierInterpolator.EASE_OUT_QUINT);
            middleTextView.setGravity(Gravity.CENTER_HORIZONTAL);
            middleTextView.setTextSize(dp(13));
            middleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
            valuesView.addView(middleTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

            batteryText = new SpannableStringBuilder("b");
            batteryIcon = new BatteryDrawable();
            batteryIcon.colorFromPaint(middleTextView.getPaint());
            batteryIcon.setTranslationY(dp(1.5f));
            batteryIcon.setBounds(dp(3), dp(-20), dp(20 + 3), 0);
            batteryText.setSpan(new ImageSpan(batteryIcon, DynamicDrawableSpan.ALIGN_BOTTOM), 0, batteryText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            rightTextView = new TextView(context);
            rightTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            rightTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            rightTextView.setGravity(Gravity.RIGHT);
            rightTextView.setText(LocaleController.getString("LiteBatteryEnabled", R.string.LiteBatteryEnabled));
            valuesView.addView(rightTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL));

            addView(valuesView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 21, 52, 21, 0));

            seekBarAccessibilityDelegate = new IntSeekBarAccessibilityDelegate() {
                @Override
                protected int getProgress() {
                    return LiteMode.getPowerSaverLevel();
                }

                @Override
                protected void setProgress(int progress) {
                    seekBarView.delegate.onSeekBarDrag(true, progress / 100f);
                    seekBarView.setProgress(progress / 100f);
                }

                @Override
                protected int getMaxValue() {
                    return 100;
                }

                @Override
                protected int getDelta() {
                    return 5;
                }

                @Override
                public void onInitializeAccessibilityNodeInfoInternal(View host, AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfoInternal(host, info);

                    info.setEnabled(true);
                }

                @Override
                public void onPopulateAccessibilityEvent(@NonNull View host, @NonNull AccessibilityEvent event) {
                    super.onPopulateAccessibilityEvent(host, event);

                    StringBuilder sb = new StringBuilder(LocaleController.getString(R.string.LiteBatteryTitle)).append(", ");
                    int percent = LiteMode.getPowerSaverLevel();
                    if (percent <= 0) {
                        sb.append(LocaleController.getString(R.string.LiteBatteryAlwaysDisabled));
                    } else if (percent >= 100) {
                        sb.append(LocaleController.getString(R.string.LiteBatteryAlwaysEnabled));
                    } else {
                        sb.append(LocaleController.formatString(R.string.AccDescrLiteBatteryWhenBelow, Math.round(percent)));
                    }

                    event.setContentDescription(sb);
                    setContentDescription(sb);
                }
            };

            update();
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);

            seekBarAccessibilityDelegate.onInitializeAccessibilityNodeInfo(this, info);
        }

        @Override
        public void onPopulateAccessibilityEvent(AccessibilityEvent event) {
            super.onPopulateAccessibilityEvent(event);

            seekBarAccessibilityDelegate.onPopulateAccessibilityEvent(this, event);
        }

        @Override
        public boolean performAccessibilityAction(int action, @Nullable Bundle arguments) {
            return seekBarAccessibilityDelegate.performAccessibilityAction(this, action, arguments);
        }

        public void update() {
            final int percent = LiteMode.getPowerSaverLevel();

            middleTextView.cancelAnimation();
            if (percent <= 0) {
                middleTextView.setText(LocaleController.getString("LiteBatteryAlwaysDisabled", R.string.LiteBatteryAlwaysDisabled), !LocaleController.isRTL);
            } else if (percent >= 100) {
                middleTextView.setText(LocaleController.getString("LiteBatteryAlwaysEnabled", R.string.LiteBatteryAlwaysEnabled), !LocaleController.isRTL);
            } else {
                batteryIcon.setFillValue(percent / 100F, true);
                middleTextView.setText(AndroidUtilities.replaceCharSequence("%s", LocaleController.getString("LiteBatteryWhenBelow", R.string.LiteBatteryWhenBelow), TextUtils.concat(String.format("%d%% ", Math.round(percent)), batteryText)), !LocaleController.isRTL);
            }

            headerOnView.setText((LiteMode.isPowerSaverApplied() ? LocaleController.getString("LiteBatteryEnabled", R.string.LiteBatteryEnabled) : LocaleController.getString("LiteBatteryDisabled", R.string.LiteBatteryDisabled)).toUpperCase());
            updateHeaderOnVisibility(percent > 0 && percent < 100);

            updateOnActive(percent >= 100);
            updateOffActive(percent <= 0);
        }

        private boolean headerOnVisible;
        private void updateHeaderOnVisibility(boolean visible) {
            if (visible != headerOnVisible) {
                headerOnVisible = visible;
                headerOnView.clearAnimation();
                headerOnView.animate().alpha(visible ? 1f : 0f).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).setDuration(220).start();
            }
        }

        private float onActiveT;
        private ValueAnimator onActiveAnimator;
        private void updateOnActive(boolean active) {
            final float activeT = active ? 1f : 0f;
            if (onActiveT != activeT) {
                onActiveT = activeT;

                if (onActiveAnimator != null) {
                    onActiveAnimator.cancel();
                    onActiveAnimator = null;
                }

                onActiveAnimator = ValueAnimator.ofFloat(onActiveT, activeT);
                onActiveAnimator.addUpdateListener(anm -> {
                    rightTextView.setTextColor(ColorUtils.blendARGB(
                        Theme.getColor(Theme.key_windowBackgroundWhiteGrayText),
                        Theme.getColor(Theme.key_windowBackgroundWhiteBlueText),
                        onActiveT = (float) anm.getAnimatedValue()
                    ));
                });
                onActiveAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        rightTextView.setTextColor(ColorUtils.blendARGB(
                            Theme.getColor(Theme.key_windowBackgroundWhiteGrayText),
                            Theme.getColor(Theme.key_windowBackgroundWhiteBlueText),
                            onActiveT = (float) activeT
                        ));
                    }
                });
                onActiveAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                onActiveAnimator.setDuration(320);
                onActiveAnimator.start();
            }
        }

        private float offActiveT;
        private ValueAnimator offActiveAnimator;
        private void updateOffActive(boolean active) {
            final float activeT = active ? 1f : 0f;
            if (offActiveT != activeT) {
                offActiveT = activeT;

                if (offActiveAnimator != null) {
                    offActiveAnimator.cancel();
                    offActiveAnimator = null;
                }

                offActiveAnimator = ValueAnimator.ofFloat(offActiveT, activeT);
                offActiveAnimator.addUpdateListener(anm -> {
                    leftTextView.setTextColor(ColorUtils.blendARGB(
                            Theme.getColor(Theme.key_windowBackgroundWhiteGrayText),
                            Theme.getColor(Theme.key_windowBackgroundWhiteBlueText),
                            offActiveT = (float) anm.getAnimatedValue()
                    ));
                });
                offActiveAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        leftTextView.setTextColor(ColorUtils.blendARGB(
                            Theme.getColor(Theme.key_windowBackgroundWhiteGrayText),
                            Theme.getColor(Theme.key_windowBackgroundWhiteBlueText),
                            offActiveT = (float) activeT
                        ));
                    }
                });
                offActiveAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                offActiveAnimator.setDuration(320);
                offActiveAnimator.start();
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp(112), MeasureSpec.EXACTLY)
            );
        }
    }

    private static class Item extends AdapterWithDiffUtils.Item {
        public CharSequence text;
        public int iconResId;
        public int flags;
        public int type;

        private Item(int viewType, CharSequence text, int iconResId, int flags, int type) {
            super(viewType, false);
            this.text = text;
            this.iconResId = iconResId;
            this.flags = flags;
            this.type = type;
        }

        public static Item asHeader(CharSequence text) {
            return new Item(VIEW_TYPE_HEADER, text, 0, 0, 0);
        }
        public static Item asSlider() {
            return new Item(VIEW_TYPE_SLIDER, null, 0, 0, 0);
        }
        public static Item asInfo(CharSequence text) {
            return new Item(VIEW_TYPE_INFO, text, 0, 0, 0);
        }
        public static Item asSwitch(int iconResId, CharSequence text, int flags) {
            return new Item(VIEW_TYPE_SWITCH, text, iconResId, flags, 0);
        }
        public static Item asCheckbox(CharSequence text, int flags) {
            return new Item(VIEW_TYPE_CHECKBOX, text, 0, flags, 0);
        }
        public static Item asSwitch(CharSequence text, int type) {
            return new Item(VIEW_TYPE_SWITCH2, text, 0, 0, type);
        }

        public int getFlagsCount() {
            return Integer.bitCount(flags);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Item)) {
                return false;
            }
            Item item = (Item) o;
            if (item.viewType != viewType) {
                return false;
            }
            if (viewType == VIEW_TYPE_SWITCH) {
                if (item.iconResId != iconResId) {
                    return false;
                }
            }
            if (viewType == VIEW_TYPE_SWITCH2) {
                if (item.type != type) {
                    return false;
                }
            }
            if (viewType == VIEW_TYPE_SWITCH || viewType == VIEW_TYPE_CHECKBOX) {
                if (item.flags != flags) {
                    return false;
                }
            }
            if (viewType == VIEW_TYPE_HEADER || viewType == VIEW_TYPE_INFO || viewType == VIEW_TYPE_SWITCH || viewType == VIEW_TYPE_CHECKBOX || viewType == VIEW_TYPE_SWITCH2) {
                if (!TextUtils.equals(item.text, text)) {
                    return false;
                }
            }
            return true;
        }
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        LiteMode.savePreference();
        AnimatedEmojiDrawable.updateAll();
        Theme.reloadWallpaper(true);
    }
}
