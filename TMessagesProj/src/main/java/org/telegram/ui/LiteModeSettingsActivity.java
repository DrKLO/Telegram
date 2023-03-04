package org.telegram.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ListView.AdapterWithDiffUtils;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SlideChooseView;
import org.telegram.ui.Components.Switch;

import java.util.ArrayList;

public class LiteModeSettingsActivity extends BaseFragment {

    FrameLayout contentView;

    RecyclerListView listView;
    LinearLayoutManager layoutManager;
    Adapter adapter;

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
                if (item.viewType == VIEW_TYPE_SWITCH && item.getFlagsCount() > 1 && (LocaleController.isRTL ? x > AndroidUtilities.dp(19 + 37 + 19) : x < view.getMeasuredWidth() - AndroidUtilities.dp(19 + 37 + 19))) {
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
                } else if (item.type == SWITCH_TYPE_LOW_BATTERY) {
                    LiteMode.setPowerSaverEnabled(!LiteMode.isPowerSaverEnabled());
                    ((TextCell) view).setChecked(LiteMode.isPowerSaverEnabled());
                }
            }

        });

        fragmentView = contentView;

        updateItems();

        return fragmentView;
    }

    private boolean[] expanded = new boolean[3];
    private int getExpandedIndex(int flags) {
        if (flags == LiteMode.FLAGS_ANIMATED_STICKERS) {
            return 0;
        } else if (flags == LiteMode.FLAGS_ANIMATED_EMOJI) {
            return 1;
        } else if (flags == LiteMode.FLAGS_CHAT) {
            return 2;
        }
        return -1;
    }


    private ArrayList<Item> oldItems = new ArrayList<>();
    private ArrayList<Item> items = new ArrayList<>();

    private void updateItems() {
        oldItems.clear();
        oldItems.addAll(items);

        items.clear();
        items.add(Item.asHeader(LocaleController.getString("LitePresetTitle")));
        items.add(Item.asSlider());
        items.add(Item.asInfo(LocaleController.getString("LitePresetInfo")));

        items.add(Item.asHeader(LocaleController.getString("LiteOptionsTitle")));
        items.add(Item.asSwitch(R.drawable.msg2_sticker, LocaleController.getString("AnimatedStickers", R.string.AnimatedStickers), LiteMode.FLAGS_ANIMATED_STICKERS));
        if (expanded[0]) {
            items.add(Item.asCheckbox(LocaleController.getString("LiteOptionsAutoplayKeyboard"), LiteMode.FLAG_ANIMATED_STICKERS_KEYBOARD));
            items.add(Item.asCheckbox(LocaleController.getString("LiteOptionsAutoplayChat"), LiteMode.FLAG_ANIMATED_STICKERS_CHAT));
        }
        items.add(Item.asSwitch(R.drawable.msg2_smile_status, LocaleController.getString("PremiumPreviewEmoji", R.string.PremiumPreviewEmoji), LiteMode.FLAGS_ANIMATED_EMOJI));
        if (expanded[1]) {
            items.add(Item.asCheckbox(LocaleController.getString("LiteOptionsAutoplayKeyboard"), LiteMode.FLAG_ANIMATED_EMOJI_KEYBOARD));
            items.add(Item.asCheckbox(LocaleController.getString("LiteOptionsAutoplayReactions"), LiteMode.FLAG_ANIMATED_EMOJI_REACTIONS));
            items.add(Item.asCheckbox(LocaleController.getString("LiteOptionsAutoplayChat"), LiteMode.FLAG_ANIMATED_EMOJI_CHAT));
        }
        items.add(Item.asSwitch(R.drawable.msg2_ask_question, LocaleController.getString("LiteOptionsChat"), LiteMode.FLAGS_CHAT));
        if (expanded[2]) {
            items.add(Item.asCheckbox(LocaleController.getString("LiteOptionsBackground"), LiteMode.FLAG_CHAT_BACKGROUND));
            items.add(Item.asCheckbox(LocaleController.getString("LiteOptionsTopics"), LiteMode.FLAG_CHAT_FORUM_TWOCOLUMN));
            items.add(Item.asCheckbox(LocaleController.getString("LiteOptionsSpoiler"), LiteMode.FLAG_CHAT_SPOILER));
            items.add(Item.asCheckbox(LocaleController.getString("LiteOptionsBlur"), LiteMode.FLAG_CHAT_BLUR));
        }
        items.add(Item.asSwitch(R.drawable.msg2_call_earpiece, LocaleController.getString("LiteOptionsCalls"), LiteMode.FLAG_CALLS_ANIMATIONS));
        items.add(Item.asSwitch(R.drawable.msg2_videocall, LocaleController.getString("LiteOptionsAutoplayVideo"), LiteMode.FLAG_AUTOPLAY_VIDEOS));
        items.add(Item.asSwitch(R.drawable.msg2_gif, LocaleController.getString("LiteOptionsAutoplayGifs"), LiteMode.FLAG_AUTOPLAY_GIFS));
        items.add(Item.asInfo(""));

        items.add(Item.asSwitch(LocaleController.getString("LiteSmoothTransitions"), SWITCH_TYPE_SMOOTH_TRANSITIONS));
        items.add(Item.asInfo(LocaleController.getString("LiteSmoothTransitionsInfo")));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            items.add(Item.asSwitch(LocaleController.getString("LitePowerSaver"), SWITCH_TYPE_LOW_BATTERY));
            items.add(Item.asInfo(LocaleController.getString("LitePowerSaverInfo")));
        }

        adapter.setItems(oldItems, items);
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
                updateSlider((SlideChooseView) child);
            }
        }
    }

    private int customIndex = -1;
    private int lastCustomSettings;

    private String[] optionsArray;
    private String[] optionsArrayFull;

    private void updateSlider(SlideChooseView slideChooseView) {
        int selectedPreset = -1;
        if (LiteMode.getValue() == LiteMode.PRESET_LOW) {
            selectedPreset = 0;
        } else if (LiteMode.getValue() == LiteMode.PRESET_MEDIUM) {
            selectedPreset = 1;
        } else if (LiteMode.getValue() == LiteMode.PRESET_HIGH) {
            selectedPreset = 2;
        }
        if (selectedPreset != -1) {
            customIndex = -1;
            if (optionsArray == null) {
                optionsArray = new String[] {
                    LocaleController.getString("AutoDownloadLow", R.string.AutoDownloadLow),
                    LocaleController.getString("AutoDownloadMedium", R.string.AutoDownloadMedium),
                    LocaleController.getString("AutoDownloadHigh", R.string.AutoDownloadHigh)
                };
            }
            slideChooseView.setOptions(selectedPreset, optionsArray);
        } else if (Integer.bitCount(LiteMode.getValue()) <= Integer.bitCount(LiteMode.PRESET_MEDIUM)) {
            customIndex = 1;
            lastCustomSettings = LiteMode.getValue();
            if (optionsArrayFull == null) {
                optionsArrayFull = new String[] {
                    LocaleController.getString("AutoDownloadLow", R.string.AutoDownloadLow),
                    LocaleController.getString("AutoDownloadCustom", R.string.AutoDownloadCustom),
                    LocaleController.getString("AutoDownloadMedium", R.string.AutoDownloadMedium),
                    LocaleController.getString("AutoDownloadHigh", R.string.AutoDownloadHigh)
                };
            } else {
                optionsArrayFull[1] = LocaleController.getString("AutoDownloadCustom", R.string.AutoDownloadCustom);
                optionsArrayFull[2] = LocaleController.getString("AutoDownloadMedium", R.string.AutoDownloadMedium);
            }
            slideChooseView.setOptions(1, optionsArrayFull);
        } else {
            customIndex = 2;
            lastCustomSettings = LiteMode.getValue();
            if (optionsArrayFull == null) {
                optionsArrayFull = new String[] {
                        LocaleController.getString("AutoDownloadLow", R.string.AutoDownloadLow),
                        LocaleController.getString("AutoDownloadMedium", R.string.AutoDownloadMedium),
                        LocaleController.getString("AutoDownloadCustom", R.string.AutoDownloadCustom),
                        LocaleController.getString("AutoDownloadHigh", R.string.AutoDownloadHigh)
                };
            } else {
                optionsArrayFull[1] = LocaleController.getString("AutoDownloadMedium", R.string.AutoDownloadMedium);
                optionsArrayFull[2] = LocaleController.getString("AutoDownloadCustom", R.string.AutoDownloadCustom);
            }
            slideChooseView.setOptions(2, optionsArrayFull);
        }
    }

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_SLIDER = 1;
    private static final int VIEW_TYPE_INFO = 2;
    private static final int VIEW_TYPE_SWITCH = 3;
    private static final int VIEW_TYPE_CHECKBOX = 4;
    private static final int VIEW_TYPE_SWITCH2 = 5;

    private static final int SWITCH_TYPE_SMOOTH_TRANSITIONS = 0;
    private static final int SWITCH_TYPE_LOW_BATTERY = 1;

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
                SlideChooseView slideChooseView = new SlideChooseView(context);
                slideChooseView.setCallback(which -> {
                    if (which == customIndex) {
                        LiteMode.setAllFlags(lastCustomSettings);
                        updateValues();
                    } else if (which == 0) {
                        LiteMode.setAllFlags(LiteMode.PRESET_LOW);
                        updateValues();
                    } else if (which == 1 && (customIndex < 0 || customIndex > 1) || which == 2 && customIndex == 1) {
                        LiteMode.setAllFlags(LiteMode.PRESET_MEDIUM);
                        updateValues();
                    } else if (which == (customIndex < 0 ? 2 : 3)) {
                        LiteMode.setAllFlags(LiteMode.PRESET_HIGH);
                        updateValues();
                    }
                });
                view = slideChooseView;
                view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            } else if (viewType == VIEW_TYPE_INFO) {
                view = new TextInfoPrivacyCell(context);
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
                SlideChooseView slideChooseView = (SlideChooseView) holder.itemView;
                updateSlider(slideChooseView);
            } else if (viewType == VIEW_TYPE_INFO) {
                TextInfoPrivacyCell textInfoPrivacyCell = (TextInfoPrivacyCell) holder.itemView;
                if (TextUtils.isEmpty(item.text)) {
                    textInfoPrivacyCell.setFixedSize(12);
                } else {
                    textInfoPrivacyCell.setFixedSize(0);
                }
                textInfoPrivacyCell.setText(item.text);
                boolean top = position > 0 && items.get(position - 1).viewType != VIEW_TYPE_INFO;
                boolean bottom = position + 1 < items.size() && items.get(position + 1).viewType != VIEW_TYPE_INFO;
                if (top && bottom) {
                    textInfoPrivacyCell.setBackground(Theme.getThemedDrawable(getContext(), R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                } else if (top) {
                    textInfoPrivacyCell.setBackground(Theme.getThemedDrawable(getContext(), R.drawable.greydivider_top, Theme.key_windowBackgroundGrayShadow));
                } else if (bottom) {
                    textInfoPrivacyCell.setBackground(Theme.getThemedDrawable(getContext(), R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
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
                } else if (item.type == SWITCH_TYPE_LOW_BATTERY) {
                    textCell.setTextAndCheck(item.text, LiteMode.isPowerSaverEnabled(), false);
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

            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

            imageView = new ImageView(context);
            imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.MULTIPLY));
            imageView.setVisibility(View.GONE);
            addView(imageView, LayoutHelper.createFrame(24, 24, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 20, 0, 20, 0));

            textView = new TextView(context);
            textView.setLines(1);
            textView.setSingleLine(true);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));

            countTextView = new AnimatedTextView(context, false, true, true);
            countTextView.setAnimationProperties(.35f, 0, 200, CubicBezierInterpolator.EASE_OUT_QUINT);
            countTextView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            countTextView.setTextSize(AndroidUtilities.dp(14));
            countTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));

            arrowView = new ImageView(context);
            arrowView.setVisibility(GONE);
            arrowView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.MULTIPLY));
            arrowView.setImageResource(R.drawable.arrow_more);

            textViewLayout = new LinearLayout(context);
            textViewLayout.setOrientation(LinearLayout.HORIZONTAL);
            textViewLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
            textViewLayout.addView(countTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 6, 0, 0, 0));
            textViewLayout.addView(arrowView, LayoutHelper.createLinear(16, 16, Gravity.CENTER_VERTICAL, 2, 0, 0, 0));
            addView(textViewLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 64, 0, 64, 0));

            switchView = new Switch(context);
            switchView.setVisibility(GONE);
            switchView.setColors(Theme.key_switchTrack, Theme.key_switchTrackChecked, Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
            addView(switchView, LayoutHelper.createFrame(37, 50, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT), 19, 0, 19, 0));

            checkBoxView = new CheckBox2(context, 21);
            checkBoxView.setColor(Theme.key_radioBackgroundChecked, Theme.key_checkboxDisabled, Theme.key_checkboxCheck);
            checkBoxView.setDrawUnchecked(true);
            checkBoxView.setChecked(true, false);
            checkBoxView.setDrawBackgroundAsArc(10);
            checkBoxView.setVisibility(GONE);
            addView(checkBoxView, LayoutHelper.createFrame(21, 21, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), LocaleController.isRTL ? 0 : 64, 0, LocaleController.isRTL ? 64 : 0, 0));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(50), MeasureSpec.EXACTLY)
            );
        }

        public void set(Item item, boolean divider) {
            if (item.viewType == VIEW_TYPE_SWITCH) {
                checkBoxView.setVisibility(GONE);
                imageView.setVisibility(VISIBLE);
                imageView.setImageResource(item.iconResId);
                textView.setText(item.text);
                if (item.getFlagsCount() > 1) {
                    countTextView.setText(String.format("%d/%d", Integer.bitCount(LiteMode.getValue() & item.flags), item.getFlagsCount()), false);
                    countTextView.setVisibility(VISIBLE);
                    arrowView.setVisibility(VISIBLE);
                } else {
                    countTextView.setVisibility(GONE);
                    arrowView.setVisibility(GONE);
                }
                textView.setTranslationX(0);
                switchView.setVisibility(VISIBLE);
                switchView.setChecked(LiteMode.isEnabledSetting(item.flags), false);
                needLine = item.getFlagsCount() > 1;
            } else {
                checkBoxView.setVisibility(VISIBLE);
                checkBoxView.setChecked(LiteMode.isEnabledSetting(item.flags), false);
                imageView.setVisibility(GONE);
                switchView.setVisibility(GONE);
                countTextView.setVisibility(GONE);
                arrowView.setVisibility(GONE);
                textView.setText(item.text);
                textView.setTranslationX(AndroidUtilities.dp(41));
                needLine = false;
            }

            setWillNotDraw(!((needDivider = divider) || needLine));
        }

        public void update(Item item) {
            if (item.viewType == VIEW_TYPE_SWITCH) {
                if (item.getFlagsCount() > 1) {
                    countTextView.setText(String.format("%d/%d", Integer.bitCount(LiteMode.getValue() & item.flags), item.getFlagsCount()), true);
                    int index = getExpandedIndex(item.flags);
                    arrowView.clearAnimation();
                    arrowView.animate().rotation(index >= 0 && expanded[index] ? 180 : 0).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).setDuration(240).start();
                }
                switchView.setChecked(LiteMode.isEnabledSetting(item.flags), true);
            } else {
                checkBoxView.setChecked(LiteMode.isEnabledSetting(item.flags), true);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (LocaleController.isRTL) {

            } else {
                if (needLine) {
                    float x = getMeasuredWidth() - AndroidUtilities.dp(19 + 37 + 19);
                    canvas.drawRect(x - AndroidUtilities.dp(0.66f), (getMeasuredHeight() - AndroidUtilities.dp(20)) / 2f, x, (getMeasuredHeight() + AndroidUtilities.dp(20)) / 2f, Theme.dividerPaint);
                }
                if (needDivider) {
                    canvas.drawLine(AndroidUtilities.dp(64), getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, Theme.dividerPaint);
                }
            }
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
        Theme.reloadWallpaper();
    }
}
