package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatThemeController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.ChatThemeBottomSheet;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.Easings;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RecyclerListView;

import java.util.Locale;

public class ChannelWallpaperActivity extends BaseFragment {

    public final long dialogId;
    public int currentLevel;
    public TL_stories.TL_premium_boostsStatus boostsStatus;
    public TLRPC.WallPaper galleryWallpaper;
    public TLRPC.WallPaper currentWallpaper, selectedWallpaper;

    public ChannelWallpaperActivity(long dialogId, TL_stories.TL_premium_boostsStatus boostsStatus) {
        super();
        this.dialogId = dialogId;
        TLRPC.Chat chat = getMessagesController().getChat(-dialogId);
        if (chat != null) {
            currentLevel = chat.level;
        }
        this.boostsStatus = boostsStatus;
        if (boostsStatus == null) {
            MessagesController.getInstance(currentAccount).getBoostsController().getBoostsStats(dialogId, loadedBoostsStatus -> {
                this.boostsStatus = loadedBoostsStatus;
                if (boostsStatus != null) {
                    this.currentLevel = boostsStatus.level;
                    if (chat != null) {
                        chat.flags |= 1024;
                        chat.level = currentLevel;
                    }
                }
            });
        } else {
            currentLevel = boostsStatus.level;
        }

        TLRPC.ChatFull chatFull = getMessagesController().getChatFull(-dialogId);
        if (chatFull != null) {
            currentWallpaper = selectedWallpaper = chatFull.wallpaper;
            if (ChatThemeController.isNotEmoticonWallpaper(selectedWallpaper)) {
                galleryWallpaper = selectedWallpaper;
            }
        }
    }

    public void setSelectedWallpaper(TLRPC.WallPaper wallpaper, TLRPC.WallPaper galleryWallpaper) {
        selectedWallpaper = wallpaper;
        this.galleryWallpaper = galleryWallpaper;
    }

    private Utilities.Callback3<TLRPC.WallPaper, TLRPC.WallPaper, TLRPC.WallPaper> onSelectedWallpaperChange;
    public void setOnSelectedWallpaperChange(Utilities.Callback3<TLRPC.WallPaper, TLRPC.WallPaper, TLRPC.WallPaper> listener) {
        onSelectedWallpaperChange = listener;
    }

    public FrameLayout contentView;
    public RecyclerListView listView;
    public Adapter adapter;
    public boolean isDark() {
        return resourceProvider != null ? resourceProvider.isDark() : Theme.isCurrentThemeDark();
    }

    private RLottieDrawable sunDrawable;
    private ActionBarMenuItem dayNightItem;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(LocaleController.getString(R.string.ChannelWallpaper));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == 1) {
                    toggleTheme();
                }
            }
        });

        sunDrawable = new RLottieDrawable(R.raw.sun, "" + R.raw.sun, dp(28), dp(28), true, null);
        sunDrawable.setPlayInDirectionOfCustomEndFrame(true);
        if (!isDark()) {
            sunDrawable.setCustomEndFrame(0);
            sunDrawable.setCurrentFrame(0);
        } else {
            sunDrawable.setCurrentFrame(35);
            sunDrawable.setCustomEndFrame(36);
        }
        sunDrawable.beginApplyLayerColors();
        int color = Theme.getColor(Theme.key_chats_menuName, resourceProvider);
        sunDrawable.setLayerColor("Sunny.**", color);
        sunDrawable.setLayerColor("Path 6.**", color);
        sunDrawable.setLayerColor("Path.**", color);
        sunDrawable.setLayerColor("Path 5.**", color);
        if (resourceProvider instanceof ChannelColorActivity.ThemeDelegate) {
            dayNightItem = actionBar.createMenu().addItem(1, sunDrawable);
        }

        contentView = new FrameLayout(context);

        updateRows();
        listView = new RecyclerListView(context, resourceProvider);
        listView.setAdapter(adapter = new Adapter());
        listView.setLayoutManager(new LinearLayoutManager(context));
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        listView.setOnItemClickListener((view, position) -> {
            if (position == removeRow) {
                galleryWallpaper = null;
                selectedWallpaper = null;
                if (onSelectedWallpaperChange != null) {
                    onSelectedWallpaperChange.run(currentWallpaper, selectedWallpaper, galleryWallpaper);
                }

                View themesView = findChildAt(themesRow);
                if (themesView instanceof ChannelColorActivity.ThemeChooser) {
                    ((ChannelColorActivity.ThemeChooser) themesView).setGalleryWallpaper(galleryWallpaper);
                }
                updateRows();
            } else if (position == galleryRow) {
                ChatThemeBottomSheet.openGalleryForBackground(getParentActivity(), this, dialogId, resourceProvider, wallpaper -> {
                    galleryWallpaper = currentWallpaper = selectedWallpaper = wallpaper;
                    if (onSelectedWallpaperChange != null) {
                        onSelectedWallpaperChange.run(currentWallpaper, selectedWallpaper, galleryWallpaper);
                    }
                    finishFragment();
                }, toggleThemeDelegate, boostsStatus);
            }
        });
        DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
        itemAnimator.setDurations(350);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setSupportsChangeAnimations(false);
        listView.setItemAnimator(itemAnimator);

        updateColors();

        return fragmentView = contentView;
    }

    public View findChildAt(int position) {
        for (int i = 0; i < listView.getChildCount(); ++i) {
            View child = listView.getChildAt(i);
            if (listView.getChildAdapterPosition(child) == position) {
                return child;
            }
        }
        return null;
    }

    public static final int VIEW_TYPE_BUTTON = 0;
    public static final int VIEW_TYPE_INFO = 1;
    public static final int VIEW_TYPE_THEMES = 2;

    public int rowsCount = 0;
    public int galleryRow = -1;
    public int removeRow = -1;
    public int infoRow = -1;
    public int themesRow = -1;

    public void updateRows() {
        rowsCount = 0;
        galleryRow = rowsCount++;
        final int wasRemoveRow = removeRow;
        if (galleryWallpaper != null) {
            removeRow = rowsCount++;
        } else {
            removeRow = -1;
        }
        if (adapter != null) {
            if (removeRow != -1 && wasRemoveRow == -1) {
                adapter.notifyItemInserted(removeRow);
            }
            if (removeRow == -1 && wasRemoveRow != -1) {
                adapter.notifyItemRemoved(wasRemoveRow);
            }
        }
        infoRow = rowsCount++;
        themesRow = rowsCount++;
    }

    public class Adapter extends RecyclerListView.SelectionAdapter {

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            if (viewType == VIEW_TYPE_BUTTON) {
                TextCell textCell = new TextCell(getContext(), resourceProvider);
                textCell.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                view = textCell;
            } else if (viewType == VIEW_TYPE_THEMES) {
                ChannelColorActivity.ThemeChooser themesWallpaper = new ChannelColorActivity.ThemeChooser(getContext(), true, currentAccount, resourceProvider);
                themesWallpaper.setSelectedEmoticon(ChatThemeController.getWallpaperEmoticon(selectedWallpaper), false);
                themesWallpaper.setGalleryWallpaper(galleryWallpaper);
                themesWallpaper.setOnEmoticonSelected(emoticon -> {
                    if (emoticon == null) {
                        selectedWallpaper = galleryWallpaper;
                        themesWallpaper.setSelectedEmoticon(null, false);
                        if (onSelectedWallpaperChange != null) {
                            onSelectedWallpaperChange.run(currentWallpaper, selectedWallpaper, galleryWallpaper);
                        }
                        updateRows();
                        return;
                    }

                    ThemePreviewActivity themePreviewActivity = new ThemePreviewActivity(new WallpapersListActivity.EmojiWallpaper(emoticon), null) {
                        @Override
                        public boolean insideBottomSheet() {
                            return true;
                        }
                    };
                    themePreviewActivity.boostsStatus = boostsStatus;
                    themePreviewActivity.setOnSwitchDayNightDelegate(toggleThemeDelegate);
                    themePreviewActivity.setResourceProvider(resourceProvider);
                    themePreviewActivity.setInitialModes(false, false, .20f);
                    themePreviewActivity.setDialogId(dialogId);
                    themePreviewActivity.setDelegate(wallPaper -> {
                        selectedWallpaper = new TLRPC.TL_wallPaperNoFile();
                        selectedWallpaper.id = 0;
                        selectedWallpaper.flags |= 4;
                        selectedWallpaper.settings = new TLRPC.TL_wallPaperSettings();
                        selectedWallpaper.settings.emoticon = emoticon;
                        themesWallpaper.setSelectedEmoticon(emoticon, false);
                        if (onSelectedWallpaperChange != null) {
                            onSelectedWallpaperChange.run(currentWallpaper, selectedWallpaper, galleryWallpaper);
                        }
                        updateRows();
                        finishFragment();
                    });
                    BaseFragment.BottomSheetParams params = new BaseFragment.BottomSheetParams();
                    params.transitionFromLeft = true;
                    params.allowNestedScroll = false;
                    params.occupyNavigationBar = true;
                    showAsSheet(themePreviewActivity, params);
                });
                themesWallpaper.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                view = themesWallpaper;
            } else {
                view = new TextInfoPrivacyCell(getContext());
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == galleryRow || position == removeRow) {
                return VIEW_TYPE_BUTTON;
            }
            if (position == themesRow) {
                return VIEW_TYPE_THEMES;
            }
            return VIEW_TYPE_INFO;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (position == galleryRow) {
                ((TextCell) holder.itemView).setTextAndIcon(LocaleController.getString(R.string.ChooseFromGallery2), R.drawable.msg_background, removeRow != -1);
                ((TextCell) holder.itemView).setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
            } else if (position == removeRow) {
                ((TextCell) holder.itemView).setTextAndIcon(LocaleController.getString(R.string.ChannelWallpaperRemove), R.drawable.msg_delete, false);
                ((TextCell) holder.itemView).setColors(Theme.key_text_RedRegular, Theme.key_text_RedRegular);
            } else if (position == infoRow) {
                ((TextInfoPrivacyCell) holder.itemView).setText(LocaleController.getString(R.string.ChannelWallpaperInfo));
                ((TextInfoPrivacyCell) holder.itemView).setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
                ((TextInfoPrivacyCell) holder.itemView).setForeground(Theme.getThemedDrawableByKey(getContext(), R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow, resourceProvider));
            } else if (position == themesRow) {
                ((ChannelColorActivity.ThemeChooser) holder.itemView).setGalleryWallpaper(galleryWallpaper);
            }
        }

        @Override
        public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
            if (holder.itemView instanceof ChannelColorActivity.ThemeChooser) {
                ((ChannelColorActivity.ThemeChooser) holder.itemView).setGalleryWallpaper(galleryWallpaper);
            }
            super.onViewAttachedToWindow(holder);
        }

        @Override
        public int getItemCount() {
            return rowsCount;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == VIEW_TYPE_BUTTON;
        }
    }

    public void updateColors() {
        actionBar.setBackgroundColor(getThemedColor(Theme.key_actionBarDefault));
        actionBar.setTitleColor(getThemedColor(Theme.key_actionBarDefaultTitle));
        actionBar.setItemsColor(getThemedColor(Theme.key_actionBarDefaultIcon), false);
        actionBar.setItemsBackgroundColor(getThemedColor(Theme.key_actionBarDefaultSelector), false);
        listView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        adapter.notifyDataSetChanged();
        AndroidUtilities.forEachViews(listView, this::updateColors);
        setNavigationBarColor(getNavigationBarColor());
        contentView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
    }

    private void updateColors(View view) {
        if (view instanceof TextInfoPrivacyCell) {
            ((TextInfoPrivacyCell) view).setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
            ((TextInfoPrivacyCell) view).setForeground(Theme.getThemedDrawableByKey(getContext(), R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow, resourceProvider));
        } else {
            view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
            if (view instanceof TextCell) {
                ((TextCell) view).updateColors();
            } else if (view instanceof ChannelColorActivity.ThemeChooser) {
                ((ChannelColorActivity.ThemeChooser) view).updateColors();
            }
        }
    }

    public ThemePreviewActivity.DayNightSwitchDelegate toggleThemeDelegate = new ThemePreviewActivity.DayNightSwitchDelegate() {
        @Override
        public boolean isDark() {
            return ChannelWallpaperActivity.this.isDark();
        }

        @Override
        public void switchDayNight(boolean animated) {
            if (resourceProvider instanceof ChannelColorActivity.ThemeDelegate) {
                ((ChannelColorActivity.ThemeDelegate) resourceProvider).toggle();
            }
            setForceDark(isDark(), false);
            updateColors();
        }

        @Override
        public boolean supportsAnimation() {
            return false;
        }
    };

    private View changeDayNightView;
    private float changeDayNightViewProgress;
    private ValueAnimator changeDayNightViewAnimator;

    @SuppressLint("NotifyDataSetChanged")
    public void toggleTheme() {
        FrameLayout decorView1 = (FrameLayout) getParentActivity().getWindow().getDecorView();
        Bitmap bitmap = Bitmap.createBitmap(decorView1.getWidth(), decorView1.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas bitmapCanvas = new Canvas(bitmap);
        dayNightItem.setAlpha(0f);
        decorView1.draw(bitmapCanvas);
        dayNightItem.setAlpha(1f);

        Paint xRefPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        xRefPaint.setColor(0xff000000);
        xRefPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bitmapPaint.setFilterBitmap(true);
        int[] position = new int[2];
        dayNightItem.getLocationInWindow(position);
        float x = position[0];
        float y = position[1];
        float cx = x + dayNightItem.getMeasuredWidth() / 2f;
        float cy = y + dayNightItem.getMeasuredHeight() / 2f;

        float r = Math.max(bitmap.getHeight(), bitmap.getWidth()) + AndroidUtilities.navigationBarHeight;

        Shader bitmapShader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        bitmapPaint.setShader(bitmapShader);
        changeDayNightView = new View(getContext()) {
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                if (isDark()) {
                    if (changeDayNightViewProgress > 0f) {
                        bitmapCanvas.drawCircle(cx, cy, r * changeDayNightViewProgress, xRefPaint);
                    }
                    canvas.drawBitmap(bitmap, 0, 0, bitmapPaint);
                } else {
                    canvas.drawCircle(cx, cy, r * (1f - changeDayNightViewProgress), bitmapPaint);
                }
                canvas.save();
                canvas.translate(x, y);
                dayNightItem.draw(canvas);
                canvas.restore();
            }
        };
        changeDayNightView.setOnTouchListener((v, event) -> true);
        changeDayNightViewProgress = 0f;
        changeDayNightViewAnimator = ValueAnimator.ofFloat(0, 1f);
        changeDayNightViewAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            boolean changedNavigationBarColor = false;

            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                changeDayNightViewProgress = (float) valueAnimator.getAnimatedValue();
                changeDayNightView.invalidate();
                if (!changedNavigationBarColor && changeDayNightViewProgress > .5f) {
                    changedNavigationBarColor = true;
                }
            }
        });
        changeDayNightViewAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (changeDayNightView != null) {
                    if (changeDayNightView.getParent() != null) {
                        ((ViewGroup) changeDayNightView.getParent()).removeView(changeDayNightView);
                    }
                    changeDayNightView = null;
                }
                changeDayNightViewAnimator = null;
                super.onAnimationEnd(animation);
            }
        });
        changeDayNightViewAnimator.setDuration(400);
        changeDayNightViewAnimator.setInterpolator(Easings.easeInOutQuad);
        changeDayNightViewAnimator.start();

        decorView1.addView(changeDayNightView, new ViewGroup.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        AndroidUtilities.runOnUIThread(() -> {
            if (resourceProvider instanceof ChannelColorActivity.ThemeDelegate) {
                ((ChannelColorActivity.ThemeDelegate) resourceProvider).toggle();
            }
            setForceDark(isDark(), true);
            updateColors();
        });
    }

    public void setForceDark(boolean isDark, boolean playAnimation) {
        if (playAnimation) {
            sunDrawable.setCustomEndFrame(isDark ? sunDrawable.getFramesCount() : 0);
            if (sunDrawable != null) {
                sunDrawable.start();
            }
        } else {
            int frame = isDark ? sunDrawable.getFramesCount() - 1 : 0;
            sunDrawable.setCurrentFrame(frame, false, true);
            sunDrawable.setCustomEndFrame(frame);
            if (dayNightItem != null) {
                dayNightItem.invalidate();
            }
        }
    }
}
