/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.MenuDrawable;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Cells.LoadingCell;
import org.telegram.ui.Components.ColorPicker;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ThemePreviewActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    public static final int SCREEN_TYPE_PREVIEW = 0;
    public static final int SCREEN_TYPE_ACCENT_COLOR = 1;

    private final int screenType;
    private boolean useDefaultThemeForButtons = true;

    private ViewPager viewPager;

    private FrameLayout page1;
    private RecyclerListView listView;
    private DialogsAdapter dialogsAdapter;
    private ImageView floatingButton;

    private ActionBar actionBar2;
    private SizeNotifierFrameLayout page2;
    private RecyclerListView listView2;
    private MessagesAdapter messagesAdapter;

    private ColorPicker colorPicker;
    private int lastPickedColor;
    private Runnable applyAccentAction = () -> {
        applyAccentScheduled = false;
        applyAccent(lastPickedColor);
    };
    private boolean applyAccentScheduled;

    private View dotsContainer;
    private FrameLayout buttonsContainer;
    private TextView doneButton;
    private TextView cancelButton;

    private Theme.ThemeInfo applyingTheme;
    private boolean nightTheme;
    private boolean deleteOnCancel;
    private List<ThemeDescription> themeDescriptions;

    public ThemePreviewActivity(Theme.ThemeInfo themeInfo) {
        this(themeInfo, false, SCREEN_TYPE_PREVIEW, false);
    }

    public ThemePreviewActivity(Theme.ThemeInfo themeInfo, boolean deleteFile, int screenType, boolean night) {
        super();
        this.screenType = screenType;
        swipeBackEnabled = false;
        nightTheme = night;
        applyingTheme = themeInfo;
        deleteOnCancel = deleteFile;

        if (screenType == SCREEN_TYPE_ACCENT_COLOR) {
            Theme.applyThemeTemporary(new Theme.ThemeInfo(applyingTheme));
            useDefaultThemeForButtons = false;
        }
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.goingToPreviewTheme);
    }

    @Override
    public View createView(Context context) {
        page1 = new FrameLayout(context);
        ActionBarMenu menu = actionBar.createMenu();
        final ActionBarMenuItem item = menu.addItem(0, R.drawable.ic_ab_search).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
            @Override
            public void onSearchExpand() {

            }

            @Override
            public boolean canCollapseSearch() {
                return true;
            }

            @Override
            public void onSearchCollapse() {

            }

            @Override
            public void onTextChanged(EditText editText) {

            }
        });
        item.setSearchFieldHint(LocaleController.getString("Search", R.string.Search));

        actionBar.setBackButtonDrawable(new MenuDrawable());
        actionBar.setAddToContainer(false);
        actionBar.setTitle(LocaleController.getString("ThemePreview", R.string.ThemePreview));

        page1 = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int widthSize = MeasureSpec.getSize(widthMeasureSpec);
                int heightSize = MeasureSpec.getSize(heightMeasureSpec);

                setMeasuredDimension(widthSize, heightSize);

                measureChildWithMargins(actionBar, widthMeasureSpec, 0, heightMeasureSpec, 0);
                int actionBarHeight = actionBar.getMeasuredHeight();
                if (actionBar.getVisibility() == VISIBLE) {
                    heightSize -= actionBarHeight;
                }
                FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) listView.getLayoutParams();
                layoutParams.topMargin = actionBarHeight;
                listView.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.EXACTLY));

                measureChildWithMargins(floatingButton, widthMeasureSpec, 0, heightMeasureSpec, 0);
            }

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                boolean result = super.drawChild(canvas, child, drawingTime);
                if (child == actionBar && parentLayout != null) {
                    parentLayout.drawHeaderShadow(canvas, actionBar.getVisibility() == VISIBLE ? actionBar.getMeasuredHeight() : 0);
                }
                return result;
            }
        };
        page1.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        listView = new RecyclerListView(context);
        listView.setVerticalScrollBarEnabled(true);
        listView.setItemAnimator(null);
        listView.setLayoutAnimation(null);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollbarPosition(LocaleController.isRTL ? RecyclerListView.SCROLLBAR_POSITION_LEFT : RecyclerListView.SCROLLBAR_POSITION_RIGHT);
        page1.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));

        floatingButton = new ImageView(context);
        floatingButton.setScaleType(ImageView.ScaleType.CENTER);

        Drawable drawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(56), Theme.getColor(Theme.key_chats_actionBackground), Theme.getColor(Theme.key_chats_actionPressedBackground));
        if (Build.VERSION.SDK_INT < 21) {
            Drawable shadowDrawable = context.getResources().getDrawable(R.drawable.floating_shadow).mutate();
            shadowDrawable.setColorFilter(new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.MULTIPLY));
            CombinedDrawable combinedDrawable = new CombinedDrawable(shadowDrawable, drawable, 0, 0);
            combinedDrawable.setIconSize(AndroidUtilities.dp(56), AndroidUtilities.dp(56));
            drawable = combinedDrawable;
        }
        floatingButton.setBackgroundDrawable(drawable);
        floatingButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_actionIcon), PorterDuff.Mode.MULTIPLY));
        floatingButton.setImageResource(R.drawable.floating_pencil);
        if (Build.VERSION.SDK_INT >= 21) {
            StateListAnimator animator = new StateListAnimator();
            animator.addState(new int[]{android.R.attr.state_pressed}, ObjectAnimator.ofFloat(floatingButton, "translationZ", AndroidUtilities.dp(2), AndroidUtilities.dp(4)).setDuration(200));
            animator.addState(new int[]{}, ObjectAnimator.ofFloat(floatingButton, "translationZ", AndroidUtilities.dp(4), AndroidUtilities.dp(2)).setDuration(200));
            floatingButton.setStateListAnimator(animator);
            floatingButton.setOutlineProvider(new ViewOutlineProvider() {
                @SuppressLint("NewApi")
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, AndroidUtilities.dp(56), AndroidUtilities.dp(56));
                }
            });
        }
        page1.addView(floatingButton, LayoutHelper.createFrame(Build.VERSION.SDK_INT >= 21 ? 56 : 60, Build.VERSION.SDK_INT >= 21 ? 56 : 60, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM, LocaleController.isRTL ? 14 : 0, 0, LocaleController.isRTL ? 0 : 14, 14));

        dialogsAdapter = new DialogsAdapter(context);
        listView.setAdapter(dialogsAdapter);

        page2 = new SizeNotifierFrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int widthSize = MeasureSpec.getSize(widthMeasureSpec);
                int heightSize = MeasureSpec.getSize(heightMeasureSpec);

                setMeasuredDimension(widthSize, heightSize);

                measureChildWithMargins(actionBar2, widthMeasureSpec, 0, heightMeasureSpec, 0);
                int actionBarHeight = actionBar2.getMeasuredHeight();
                if (actionBar2.getVisibility() == VISIBLE) {
                    heightSize -= actionBarHeight;
                }
                FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) listView2.getLayoutParams();
                layoutParams.topMargin = actionBarHeight;
                listView2.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.EXACTLY));
            }

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                boolean result = super.drawChild(canvas, child, drawingTime);
                if (child == actionBar2 && parentLayout != null) {
                    parentLayout.drawHeaderShadow(canvas, actionBar2.getVisibility() == VISIBLE ? actionBar2.getMeasuredHeight() : 0);
                }
                return result;
            }
        };
        page2.setBackgroundImage(Theme.getCachedWallpaper(), Theme.isWallpaperMotion());

        messagesAdapter = new MessagesAdapter(context);

        actionBar2 = createActionBar(context);
        actionBar2.setBackButtonDrawable(new BackDrawable(false));
        page2.addView(actionBar2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        actionBar2.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    cancelThemeApply();
                }
            }
        });

        if (messagesAdapter.showSecretMessages) {
            actionBar2.setTitle("Telegram Beta Chat");
            actionBar2.setSubtitle(LocaleController.formatPluralString("Members", 505));
        } else {
            String name = applyingTheme.info != null ? applyingTheme.info.title : applyingTheme.getName();
            int index = name.lastIndexOf(".attheme");
            if (index >= 0) {
                name = name.substring(0, index);
            }
            actionBar2.setTitle(name);
            if (applyingTheme.info != null && applyingTheme.info.installs_count > 0) {
                actionBar2.setSubtitle(LocaleController.formatPluralString("ThemeInstallCount", applyingTheme.info.installs_count));
            } else {
                actionBar2.setSubtitle(LocaleController.formatDateOnline(System.currentTimeMillis() / 1000 - 60 * 60));
            }
        }

        listView2 = new RecyclerListView(context) {
            @Override
            public boolean drawChild(Canvas canvas, View child, long drawingTime) {
                boolean result = super.drawChild(canvas, child, drawingTime);
                if (child instanceof ChatMessageCell) {
                    ChatMessageCell chatMessageCell = (ChatMessageCell) child;
                    MessageObject message = chatMessageCell.getMessageObject();
                    ImageReceiver imageReceiver = chatMessageCell.getAvatarImage();
                    if (imageReceiver != null) {
                        int top = child.getTop();
                        if (chatMessageCell.isPinnedBottom()) {
                            ViewHolder holder = listView2.getChildViewHolder(child);
                            if (holder != null) {
                                int p = holder.getAdapterPosition();
                                int nextPosition;
                                nextPosition = p - 1;
                                holder = listView2.findViewHolderForAdapterPosition(nextPosition);
                                if (holder != null) {
                                    imageReceiver.setImageY(-AndroidUtilities.dp(1000));
                                    imageReceiver.draw(canvas);
                                    return result;
                                }
                            }
                        }
                        float tx = chatMessageCell.getTranslationX();
                        int y = child.getTop() + chatMessageCell.getLayoutHeight();
                        int maxY = listView2.getMeasuredHeight() - listView2.getPaddingBottom();
                        if (y > maxY) {
                            y = maxY;
                        }
                        if (chatMessageCell.isPinnedTop()) {
                            ViewHolder holder = listView2.getChildViewHolder(child);
                            if (holder != null) {
                                int tries = 0;
                                while (true) {
                                    if (tries >= 20) {
                                        break;
                                    }
                                    tries++;
                                    int p = holder.getAdapterPosition();
                                    int prevPosition = p + 1;
                                    holder = listView2.findViewHolderForAdapterPosition(prevPosition);
                                    if (holder != null) {
                                        top = holder.itemView.getTop();
                                        if (y - AndroidUtilities.dp(48) < holder.itemView.getBottom()) {
                                            tx = Math.min(holder.itemView.getTranslationX(), tx);
                                        }
                                        if (holder.itemView instanceof ChatMessageCell) {
                                            ChatMessageCell cell = (ChatMessageCell) holder.itemView;
                                            if (!cell.isPinnedTop()) {
                                                break;
                                            }
                                        } else {
                                            break;
                                        }
                                    } else {
                                        break;
                                    }
                                }
                            }
                        }
                        if (y - AndroidUtilities.dp(48) < top) {
                            y = top + AndroidUtilities.dp(48);
                        }
                        if (tx != 0) {
                            canvas.save();
                            canvas.translate(tx, 0);
                        }
                        imageReceiver.setImageY(y - AndroidUtilities.dp(44));
                        imageReceiver.draw(canvas);
                        if (tx != 0) {
                            canvas.restore();
                        }
                    }
                }
                return result;
            }
        };
        listView2.setVerticalScrollBarEnabled(true);
        listView2.setItemAnimator(null);
        listView2.setLayoutAnimation(null);
        listView2.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4));
        listView2.setClipToPadding(false);
        listView2.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, true));
        listView2.setVerticalScrollbarPosition(LocaleController.isRTL ? RecyclerListView.SCROLLBAR_POSITION_LEFT : RecyclerListView.SCROLLBAR_POSITION_RIGHT);
        page2.addView(listView2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));

        listView2.setAdapter(messagesAdapter);

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        fragmentView = linearLayout;

        viewPager = new ViewPager(context);
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                dotsContainer.invalidate();
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });
        viewPager.setAdapter(new PagerAdapter() {

            @Override
            public int getCount() {
                return 2;
            }

            @Override
            public boolean isViewFromObject(View view, Object object) {
                return object == view;
            }

            @Override
            public int getItemPosition(Object object) {
                return POSITION_UNCHANGED;
            }

            @Override
            public Object instantiateItem(ViewGroup container, int position) {
                View view = position == 0 ? page2 : page1;
                container.addView(view);
                return view;
            }

            @Override
            public void destroyItem(ViewGroup container, int position, Object object) {
                container.removeView((View) object);
            }

            @Override
            public void unregisterDataSetObserver(DataSetObserver observer) {
                if (observer != null) {
                    super.unregisterDataSetObserver(observer);
                }
            }
        });
        AndroidUtilities.setViewPagerEdgeEffectColor(viewPager, Theme.getColor(Theme.key_actionBarDefault));
        linearLayout.addView(viewPager, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        View shadow = new View(context);
        shadow.setBackgroundResource(R.drawable.header_shadow_reverse);
        linearLayout.addView(shadow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 3, Gravity.NO_GRAVITY, 0, -3, 0, 0));

        if (screenType == SCREEN_TYPE_ACCENT_COLOR) {
            FrameLayout colorPickerFrame = new FrameLayout(context);
            linearLayout.addView(colorPickerFrame, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

            colorPicker = new ColorPicker(context, this::scheduleApplyAccent);

            if (applyingTheme.isDark()) {
                colorPicker.setMinBrightness((r, g, b) -> 255f / (0.5f * r + 0.8f * g + 0.1f * b + 500f));
            } else {
                colorPicker.setMaxBrightness((r, g, b) -> 255f / (0.1f * r + 1.0f * g + 0.1f * b + 50f));
            }

            colorPicker.setColor(applyingTheme.accentColor);
            colorPickerFrame.addView(colorPicker, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 342, Gravity.CENTER_HORIZONTAL));

            View shadow2 = new View(context);
            shadow2.setBackgroundColor(0x12000000);
            linearLayout.addView(shadow2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 2, Gravity.NO_GRAVITY, 0, -2, 0, 0));
        }

        buttonsContainer = new FrameLayout(context);
        buttonsContainer.setBackgroundColor(getButtonsColor(Theme.key_windowBackgroundWhite));
        linearLayout.addView(buttonsContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

        dotsContainer = new View(context) {

            private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void onDraw(Canvas canvas) {
                int selected = viewPager.getCurrentItem();
                paint.setColor(getButtonsColor(Theme.key_chat_fieldOverlayText));
                for (int a = 0; a < 2; a++) {
                    paint.setAlpha(a == selected ? 255 : 127);
                    canvas.drawCircle(AndroidUtilities.dp(3 + 15 * a), AndroidUtilities.dp(4), AndroidUtilities.dp(3), paint);
                }
            }
        };
        buttonsContainer.addView(dotsContainer, LayoutHelper.createFrame(22, 8, Gravity.CENTER));

        cancelButton = new TextView(context);
        cancelButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        cancelButton.setTextColor(getButtonsColor(Theme.key_chat_fieldOverlayText));
        cancelButton.setGravity(Gravity.CENTER);
        cancelButton.setBackgroundDrawable(Theme.createSelectorDrawable(0x2f000000, 0));
        cancelButton.setPadding(AndroidUtilities.dp(29), 0, AndroidUtilities.dp(29), 0);
        cancelButton.setText(LocaleController.getString("Cancel", R.string.Cancel).toUpperCase());
        cancelButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        buttonsContainer.addView(cancelButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        cancelButton.setOnClickListener(v -> cancelThemeApply());

        doneButton = new TextView(context);
        doneButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        doneButton.setTextColor(getButtonsColor(Theme.key_chat_fieldOverlayText));
        doneButton.setGravity(Gravity.CENTER);
        doneButton.setBackgroundDrawable(Theme.createSelectorDrawable(0x2f000000, 0));
        doneButton.setPadding(AndroidUtilities.dp(29), 0, AndroidUtilities.dp(29), 0);
        doneButton.setText(LocaleController.getString("ApplyTheme", R.string.ApplyTheme).toUpperCase());
        doneButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        buttonsContainer.addView(doneButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.RIGHT));
        doneButton.setOnClickListener(v -> {
            if (screenType == SCREEN_TYPE_PREVIEW) {
                parentLayout.rebuildAllFragmentViews(false, false);
                Theme.applyThemeFile(new File(applyingTheme.pathToFile), applyingTheme.name, applyingTheme.info, false);
                getMessagesController().saveTheme(applyingTheme, false, false);
            } else if (screenType == SCREEN_TYPE_ACCENT_COLOR) {
                Theme.saveThemeAccent(applyingTheme, colorPicker.getColor());
                Theme.applyPreviousTheme();
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, applyingTheme, nightTheme);
            }
            finishFragment();
        });

        themeDescriptions = getThemeDescriptionsInternal();

        return fragmentView;
    }

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiDidLoad);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewWallpapper);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiDidLoad);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewWallpapper);
        super.onFragmentDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (dialogsAdapter != null) {
            dialogsAdapter.notifyDataSetChanged();
        }
        if (messagesAdapter != null) {
            messagesAdapter.notifyDataSetChanged();
        }
        if (page2 != null) {
            page2.onResume();
        }
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
        AndroidUtilities.removeAdjustResize(getParentActivity(), classGuid);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (page2 != null) {
            page2.onResume();
        }
    }

    @Override
    public boolean onBackPressed() {
        Theme.applyPreviousTheme();
        if (screenType != SCREEN_TYPE_ACCENT_COLOR) {
            parentLayout.rebuildAllFragmentViews(false, false);
        }
        if (deleteOnCancel && applyingTheme.pathToFile != null && !Theme.isThemeInstalled(applyingTheme)) {
            new File(applyingTheme.pathToFile).delete();
        }
        return super.onBackPressed();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.emojiDidLoad) {
            if (listView == null) {
                return;
            }
            int count = listView.getChildCount();
            for (int a = 0; a < count; a++) {
                View child = listView.getChildAt(a);
                if (child instanceof DialogCell) {
                    DialogCell cell = (DialogCell) child;
                    cell.update(0);
                }
            }
        } else if (id == NotificationCenter.didSetNewWallpapper) {
            if (page2 != null) {
                page2.setBackgroundImage(Theme.getCachedWallpaper(), Theme.isWallpaperMotion());
            }
        }
    }

    private void cancelThemeApply() {
        Theme.applyPreviousTheme();
        if (screenType != SCREEN_TYPE_ACCENT_COLOR) {
            parentLayout.rebuildAllFragmentViews(false, false);
        }
        if (deleteOnCancel && applyingTheme.pathToFile != null && !Theme.isThemeInstalled(applyingTheme)) {
            new File(applyingTheme.pathToFile).delete();
        }
        finishFragment();
    }

    private int getButtonsColor(String key) {
        return useDefaultThemeForButtons ? Theme.getDefaultColor(key) : Theme.getColor(key);
    }

    private void scheduleApplyAccent(int accent) {
        lastPickedColor = accent;
        if (!applyAccentScheduled) {
            applyAccentScheduled = true;
            fragmentView.postDelayed(applyAccentAction, 16L); // To not apply accent color too often
        }
    }

    private void applyAccent(int accent) {
        Theme.applyCurrentThemeAccent(accent);

        for (int i = 0, size = themeDescriptions.size(); i < size; i++) {
            ThemeDescription description = themeDescriptions.get(i);
            description.setColor(Theme.getColor(description.getCurrentKey()), false, false);
        }

        listView.invalidateViews();
        listView2.invalidateViews();
        dotsContainer.invalidate();
    }

    public class DialogsAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        private ArrayList<DialogCell.CustomDialog> dialogs;

        public DialogsAdapter(Context context) {
            mContext = context;
            dialogs = new ArrayList<>();

            int date = (int) (System.currentTimeMillis() / 1000);
            DialogCell.CustomDialog customDialog = new DialogCell.CustomDialog();
            customDialog.name = LocaleController.getString("ThemePreviewDialog1", R.string.ThemePreviewDialog1);
            customDialog.message = LocaleController.getString("ThemePreviewDialogMessage1", R.string.ThemePreviewDialogMessage1);
            customDialog.id = 0;
            customDialog.unread_count = 0;
            customDialog.pinned = true;
            customDialog.muted = false;
            customDialog.type = 0;
            customDialog.date = date;
            customDialog.verified = false;
            customDialog.isMedia = false;
            customDialog.sent = true;
            dialogs.add(customDialog);

            customDialog = new DialogCell.CustomDialog();
            customDialog.name = LocaleController.getString("ThemePreviewDialog2", R.string.ThemePreviewDialog2);
            customDialog.message = LocaleController.getString("ThemePreviewDialogMessage2", R.string.ThemePreviewDialogMessage2);
            customDialog.id = 1;
            customDialog.unread_count = 2;
            customDialog.pinned = false;
            customDialog.muted = false;
            customDialog.type = 0;
            customDialog.date = date - 60 * 60;
            customDialog.verified = false;
            customDialog.isMedia = false;
            customDialog.sent = false;
            dialogs.add(customDialog);

            customDialog = new DialogCell.CustomDialog();
            customDialog.name = LocaleController.getString("ThemePreviewDialog3", R.string.ThemePreviewDialog3);
            customDialog.message = LocaleController.getString("ThemePreviewDialogMessage3", R.string.ThemePreviewDialogMessage3);
            customDialog.id = 2;
            customDialog.unread_count = 3;
            customDialog.pinned = false;
            customDialog.muted = true;
            customDialog.type = 0;
            customDialog.date = date - 60 * 60 * 2;
            customDialog.verified = false;
            customDialog.isMedia = true;
            customDialog.sent = false;
            dialogs.add(customDialog);

            customDialog = new DialogCell.CustomDialog();
            customDialog.name = LocaleController.getString("ThemePreviewDialog4", R.string.ThemePreviewDialog4);
            customDialog.message = LocaleController.getString("ThemePreviewDialogMessage4", R.string.ThemePreviewDialogMessage4);
            customDialog.id = 3;
            customDialog.unread_count = 0;
            customDialog.pinned = false;
            customDialog.muted = false;
            customDialog.type = 2;
            customDialog.date = date - 60 * 60 * 3;
            customDialog.verified = false;
            customDialog.isMedia = false;
            customDialog.sent = false;
            dialogs.add(customDialog);

            customDialog = new DialogCell.CustomDialog();
            customDialog.name = LocaleController.getString("ThemePreviewDialog5", R.string.ThemePreviewDialog5);
            customDialog.message = LocaleController.getString("ThemePreviewDialogMessage5", R.string.ThemePreviewDialogMessage5);
            customDialog.id = 4;
            customDialog.unread_count = 0;
            customDialog.pinned = false;
            customDialog.muted = false;
            customDialog.type = 1;
            customDialog.date = date - 60 * 60 * 4;
            customDialog.verified = false;
            customDialog.isMedia = false;
            customDialog.sent = true;
            dialogs.add(customDialog);

            customDialog = new DialogCell.CustomDialog();
            customDialog.name = LocaleController.getString("ThemePreviewDialog6", R.string.ThemePreviewDialog6);
            customDialog.message = LocaleController.getString("ThemePreviewDialogMessage6", R.string.ThemePreviewDialogMessage6);
            customDialog.id = 5;
            customDialog.unread_count = 0;
            customDialog.pinned = false;
            customDialog.muted = false;
            customDialog.type = 0;
            customDialog.date = date - 60 * 60 * 5;
            customDialog.verified = false;
            customDialog.isMedia = false;
            customDialog.sent = false;
            dialogs.add(customDialog);

            customDialog = new DialogCell.CustomDialog();
            customDialog.name = LocaleController.getString("ThemePreviewDialog7", R.string.ThemePreviewDialog7);
            customDialog.message = LocaleController.getString("ThemePreviewDialogMessage7", R.string.ThemePreviewDialogMessage7);
            customDialog.id = 6;
            customDialog.unread_count = 0;
            customDialog.pinned = false;
            customDialog.muted = false;
            customDialog.type = 0;
            customDialog.date = date - 60 * 60 * 6;
            customDialog.verified = true;
            customDialog.isMedia = false;
            customDialog.sent = false;
            dialogs.add(customDialog);

            customDialog = new DialogCell.CustomDialog();
            customDialog.name = LocaleController.getString("ThemePreviewDialog8", R.string.ThemePreviewDialog8);
            customDialog.message = LocaleController.getString("ThemePreviewDialogMessage8", R.string.ThemePreviewDialogMessage8);
            customDialog.id = 0;
            customDialog.unread_count = 0;
            customDialog.pinned = false;
            customDialog.muted = false;
            customDialog.type = 0;
            customDialog.date = date - 60 * 60 * 7;
            customDialog.verified = true;
            customDialog.isMedia = false;
            customDialog.sent = false;
            dialogs.add(customDialog);
        }

        @Override
        public int getItemCount() {
            return dialogs.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() != 1;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            View view = null;
            if (viewType == 0) {
                view = new DialogCell(mContext, false, false);
            } else if (viewType == 1) {
                view = new LoadingCell(mContext);
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int i) {
            if (viewHolder.getItemViewType() == 0) {
                DialogCell cell = (DialogCell) viewHolder.itemView;
                cell.useSeparator = (i != getItemCount() - 1);
                cell.setDialog(dialogs.get(i));
            }
        }

        @Override
        public int getItemViewType(int i) {
            if (i == dialogs.size()) {
                return 1;
            }
            return 0;
        }
    }

    public class MessagesAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        private ArrayList<MessageObject> messages;
        private boolean showSecretMessages = Utilities.random.nextInt(100) <= 1;

        public MessagesAdapter(Context context) {
            mContext = context;
            messages = new ArrayList<>();

            int date = (int) (System.currentTimeMillis() / 1000) - 60 * 60;

            TLRPC.Message message;
            MessageObject messageObject;
            if (showSecretMessages) {
                TLRPC.TL_user user1 = new TLRPC.TL_user();
                user1.id = Integer.MAX_VALUE;
                user1.first_name = "Me";

                TLRPC.TL_user user2 = new TLRPC.TL_user();
                user2.id = Integer.MAX_VALUE - 1;
                user2.first_name = "Serj";

                ArrayList<TLRPC.User> users = new ArrayList<>();
                users.add(user1);
                users.add(user2);
                MessagesController.getInstance(currentAccount).putUsers(users, true);

                message = new TLRPC.TL_message();
                message.message = "Guess why Half-Life 3 was never released.";
                message.date = date + 960;
                message.dialog_id = -1;
                message.flags = 259;
                message.id = Integer.MAX_VALUE - 1;
                message.media = new TLRPC.TL_messageMediaEmpty();
                message.out = false;
                message.to_id = new TLRPC.TL_peerChat();
                message.to_id.chat_id = 1;
                message.from_id = user2.id;
                messages.add(new MessageObject(currentAccount, message, true));

                message = new TLRPC.TL_message();
                message.message = "No.\n" +
                        "And every unnecessary ping of the dev delays the release for 10 days.\n" +
                        "Every request for ETA delays the release for 2 weeks.";
                message.date = date + 960;
                message.dialog_id = -1;
                message.flags = 259;
                message.id = 1;
                message.media = new TLRPC.TL_messageMediaEmpty();
                message.out = false;
                message.to_id = new TLRPC.TL_peerChat();
                message.to_id.chat_id = 1;
                message.from_id = user2.id;
                messages.add(new MessageObject(currentAccount, message, true));

                message = new TLRPC.TL_message();
                message.message = "Is source code for Android coming anytime soon?";
                message.date = date + 600;
                message.dialog_id = -1;
                message.flags = 259;
                message.id = 1;
                message.media = new TLRPC.TL_messageMediaEmpty();
                message.out = false;
                message.to_id = new TLRPC.TL_peerChat();
                message.to_id.chat_id = 1;
                message.from_id = user1.id;
                messages.add(new MessageObject(currentAccount, message, true));
            } else {
                message = new TLRPC.TL_message();
                message.message = LocaleController.getString("ThemePreviewLine1", R.string.ThemePreviewLine1);
                message.date = date + 60;
                message.dialog_id = 1;
                message.flags = 259;
                message.from_id = UserConfig.getInstance(currentAccount).getClientUserId();
                message.id = 1;
                message.media = new TLRPC.TL_messageMediaEmpty();
                message.out = true;
                message.to_id = new TLRPC.TL_peerUser();
                message.to_id.user_id = 0;
                MessageObject replyMessageObject = new MessageObject(currentAccount, message, true);

                message = new TLRPC.TL_message();
                message.message = LocaleController.getString("ThemePreviewLine2", R.string.ThemePreviewLine2);
                message.date = date + 960;
                message.dialog_id = 1;
                message.flags = 259;
                message.from_id = UserConfig.getInstance(currentAccount).getClientUserId();
                message.id = 1;
                message.media = new TLRPC.TL_messageMediaEmpty();
                message.out = true;
                message.to_id = new TLRPC.TL_peerUser();
                message.to_id.user_id = 0;
                messages.add(new MessageObject(currentAccount, message, true));

                message = new TLRPC.TL_message();
                message.date = date + 130;
                message.dialog_id = 1;
                message.flags = 259;
                message.from_id = 0;
                message.id = 5;
                message.media = new TLRPC.TL_messageMediaDocument();
                message.media.flags |= 3;
                message.media.document = new TLRPC.TL_document();
                message.media.document.mime_type = "audio/mp4";
                message.media.document.file_reference = new byte[0];
                TLRPC.TL_documentAttributeAudio audio = new TLRPC.TL_documentAttributeAudio();
                audio.duration = 243;
                audio.performer = LocaleController.getString("ThemePreviewSongPerformer", R.string.ThemePreviewSongPerformer);
                audio.title = LocaleController.getString("ThemePreviewSongTitle", R.string.ThemePreviewSongTitle);
                message.media.document.attributes.add(audio);
                message.out = false;
                message.to_id = new TLRPC.TL_peerUser();
                message.to_id.user_id = UserConfig.getInstance(currentAccount).getClientUserId();
                messages.add(new MessageObject(currentAccount, message, true));

                message = new TLRPC.TL_message();
                message.message = LocaleController.getString("ThemePreviewLine3", R.string.ThemePreviewLine3);
                message.date = date + 60;
                message.dialog_id = 1;
                message.flags = 257 + 8;
                message.from_id = 0;
                message.id = 1;
                message.reply_to_msg_id = 5;
                message.media = new TLRPC.TL_messageMediaEmpty();
                message.out = false;
                message.to_id = new TLRPC.TL_peerUser();
                message.to_id.user_id = UserConfig.getInstance(currentAccount).getClientUserId();
                messageObject = new MessageObject(currentAccount, message, true);
                messageObject.customReplyName = LocaleController.getString("ThemePreviewLine3Reply", R.string.ThemePreviewLine3Reply);
                messageObject.replyMessageObject = replyMessageObject;
                messages.add(messageObject);

                message = new TLRPC.TL_message();
                message.date = date + 120;
                message.dialog_id = 1;
                message.flags = 259;
                message.from_id = UserConfig.getInstance(currentAccount).getClientUserId();
                message.id = 1;
                message.media = new TLRPC.TL_messageMediaDocument();
                message.media.flags |= 3;
                message.media.document = new TLRPC.TL_document();
                message.media.document.mime_type = "audio/ogg";
                message.media.document.file_reference = new byte[0];
                audio = new TLRPC.TL_documentAttributeAudio();
                audio.flags = 1028;
                audio.duration = 3;
                audio.voice = true;
                audio.waveform = new byte[]{0, 4, 17, -50, -93, 86, -103, -45, -12, -26, 63, -25, -3, 109, -114, -54, -4, -1,
                        -1, -1, -1, -29, -1, -1, -25, -1, -1, -97, -43, 57, -57, -108, 1, -91, -4, -47, 21, 99, 10, 97, 43,
                        45, 115, -112, -77, 51, -63, 66, 40, 34, -122, -116, 48, -124, 16, 66, -120, 16, 68, 16, 33, 4, 1};
                message.media.document.attributes.add(audio);
                message.out = true;
                message.to_id = new TLRPC.TL_peerUser();
                message.to_id.user_id = 0;
                messageObject = new MessageObject(currentAccount, message, true);
                messageObject.audioProgressSec = 1;
                messageObject.audioProgress = 0.3f;
                messageObject.useCustomPhoto = true;
                messages.add(messageObject);

                messages.add(replyMessageObject);

                message = new TLRPC.TL_message();
                message.date = date + 10;
                message.dialog_id = 1;
                message.flags = 257;
                message.from_id = 0;
                message.id = 1;
                message.media = new TLRPC.TL_messageMediaPhoto();
                message.media.flags |= 3;
                message.media.photo = new TLRPC.TL_photo();
                message.media.photo.file_reference = new byte[0];
                message.media.photo.has_stickers = false;
                message.media.photo.id = 1;
                message.media.photo.access_hash = 0;
                message.media.photo.date = date;
                TLRPC.TL_photoSize photoSize = new TLRPC.TL_photoSize();
                photoSize.size = 0;
                photoSize.w = 500;
                photoSize.h = 302;
                photoSize.type = "s";
                photoSize.location = new TLRPC.TL_fileLocationUnavailable();
                message.media.photo.sizes.add(photoSize);
                message.message = LocaleController.getString("ThemePreviewLine4", R.string.ThemePreviewLine4);
                message.out = false;
                message.to_id = new TLRPC.TL_peerUser();
                message.to_id.user_id = UserConfig.getInstance(currentAccount).getClientUserId();
                messageObject = new MessageObject(currentAccount, message, true);
                messageObject.useCustomPhoto = true;
                messages.add(messageObject);
            }

            message = new TLRPC.TL_message();
            message.message = LocaleController.formatDateChat(date);
            message.id = 0;
            message.date = date;
            messageObject = new MessageObject(currentAccount, message, false);
            messageObject.type = 10;
            messageObject.contentType = 1;
            messageObject.isDateObject = true;
            messages.add(messageObject);
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            View view = null;
            if (viewType == 0) {
                view = new ChatMessageCell(mContext);
                ChatMessageCell chatMessageCell = (ChatMessageCell) view;
                chatMessageCell.setDelegate(new ChatMessageCell.ChatMessageCellDelegate() {

                });
            } else if (viewType == 1) {
                view = new ChatActionCell(mContext);
                ((ChatActionCell) view).setDelegate(new ChatActionCell.ChatActionCellDelegate() {

                });
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            MessageObject message = messages.get(position);
            View view = holder.itemView;

            if (view instanceof ChatMessageCell) {
                ChatMessageCell messageCell = (ChatMessageCell) view;
                messageCell.isChat = false;
                int nextType = getItemViewType(position - 1);
                int prevType = getItemViewType(position + 1);
                boolean pinnedBotton;
                boolean pinnedTop;
                if (!(message.messageOwner.reply_markup instanceof TLRPC.TL_replyInlineMarkup) && nextType == holder.getItemViewType()) {
                    MessageObject nextMessage = messages.get(position - 1);
                    pinnedBotton = nextMessage.isOutOwner() == message.isOutOwner() && Math.abs(nextMessage.messageOwner.date - message.messageOwner.date) <= 5 * 60;
                } else {
                    pinnedBotton = false;
                }
                if (prevType == holder.getItemViewType()) {
                    MessageObject prevMessage = messages.get(position + 1);
                    pinnedTop = !(prevMessage.messageOwner.reply_markup instanceof TLRPC.TL_replyInlineMarkup) && prevMessage.isOutOwner() == message.isOutOwner() && Math.abs(prevMessage.messageOwner.date - message.messageOwner.date) <= 5 * 60;
                } else {
                    pinnedTop = false;
                }
                messageCell.isChat = showSecretMessages;
                messageCell.setFullyDraw(true);
                messageCell.setMessageObject(message, null, pinnedBotton, pinnedTop);
            } else if (view instanceof ChatActionCell) {
                ChatActionCell actionCell = (ChatActionCell) view;
                actionCell.setMessageObject(message);
                actionCell.setAlpha(1.0f);
            }
        }

        @Override
        public int getItemViewType(int i) {
            if (i >= 0 && i < messages.size()) {
                return messages.get(i).contentType;
            }
            return 4;
        }
    }


    private List<ThemeDescription> getThemeDescriptionsInternal() {
        List<ThemeDescription> items = new ArrayList<>();
        items.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
        items.add(new ThemeDescription(viewPager, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));

        items.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        items.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));
        items.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        items.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCH, null, null, null, null, Theme.key_actionBarDefaultSearch));
        items.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCHPLACEHOLDER, null, null, null, null, Theme.key_actionBarDefaultSearchPlaceholder));

        items.add(new ThemeDescription(actionBar2, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        items.add(new ThemeDescription(actionBar2, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        items.add(new ThemeDescription(actionBar2, ThemeDescription.FLAG_AB_SUBTITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultSubtitle));
        items.add(new ThemeDescription(actionBar2, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        items.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        items.add(new ThemeDescription(listView2, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));

        items.add(new ThemeDescription(floatingButton, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_chats_actionIcon));
        items.add(new ThemeDescription(floatingButton, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_chats_actionBackground));
        items.add(new ThemeDescription(floatingButton, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_chats_actionPressedBackground));

        if (!useDefaultThemeForButtons) {
            items.add(new ThemeDescription(buttonsContainer, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
            items.add(new ThemeDescription(cancelButton, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_chat_fieldOverlayText));
            items.add(new ThemeDescription(doneButton, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_chat_fieldOverlayText));
        }

        if (colorPicker != null) {
            colorPicker.provideThemeDescriptions(items);
        }

        return items;
    }

}
