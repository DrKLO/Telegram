package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.provider.MediaStore;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.LruCache;
import android.util.Pair;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScrollerCustom;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AdjustPanLayoutHelper;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.StickerEmptyView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

public class GalleryListView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    private final int currentAccount;
    private final Theme.ResourcesProvider resourcesProvider;
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public final RecyclerListView listView;
    public final GridLayoutManager layoutManager;
    public final Adapter adapter;

    private final FrameLayout searchContainer;
    private final RecyclerListView searchListView;
    private final GridLayoutManager searchLayoutManager;
    private final SearchAdapter searchAdapterImages;
    private final StickerEmptyView searchEmptyView;
    private final KeyboardNotifier keyboardNotifier;

    private boolean actionBarShown;
    private final ActionBar actionBar;

    private final TextView dropDown;
    private final Drawable dropDownDrawable;
    private final ActionBarMenuItem dropDownContainer;
    private final ActionBarMenuItem searchItem;

    public boolean ignoreScroll;
    public final boolean onlyPhotos;

    public GalleryListView(int currentAccount, Context context, Theme.ResourcesProvider resourcesProvider, MediaController.AlbumEntry startAlbum, boolean onlyPhotos) {
        super(context);
        this.currentAccount = currentAccount;
        this.resourcesProvider = resourcesProvider;
        this.onlyPhotos = onlyPhotos;

        backgroundPaint.setColor(0xff1f1f1f);
        backgroundPaint.setShadowLayer(dp(2.33f), 0, dp(-.4f), 0x08000000);

        listView = new RecyclerListView(context, resourcesProvider) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent e) {
                if (ignoreScroll) {
                    return false;
                }
                return super.onInterceptTouchEvent(e);
            }

            @Override
            public boolean dispatchTouchEvent(MotionEvent e) {
                if (ignoreScroll) {
                    return false;
                }
                return super.dispatchTouchEvent(e);
            }
        };
        listView.setItemSelectorColorProvider(pos -> 0);
        listView.setAdapter(adapter = new Adapter());
        listView.setLayoutManager(layoutManager = new GridLayoutManager(context, 3) {
            @Override
            public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
                super.onLayoutChildren(recycler, state);
                if (firstLayout) {
                    firstLayout = false;
                    firstLayout();
                }
            }
        });
        listView.setFastScrollEnabled(RecyclerListView.FastScroll.DATE_TYPE);
        listView.setFastScrollVisible(true);
        listView.getFastScroll().setAlpha(0f);
//        listView.getFastScroll().usePadding = false;
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return position == 0 || position == 1 || position == adapter.getItemCount() - 1 ? 3 : 1;
            }
        });
        listView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                outRect.bottom = outRect.right = dp(5);
            }
        });
        listView.setClipToPadding(false);
        addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        listView.setOnItemClickListener((view, position) -> {
            if (position < 2 || onSelectListener == null || !(view instanceof Cell)) {
                return;
            }
            Cell cell = (Cell) view;
            int index = position - 2;
            if (containsDraftFolder) {
                if (index == 0) {
                    selectAlbum(draftsAlbum, true);
                    return;
                }
                index--;
            } else if (containsDrafts) {
                if (index >= 0 && index < drafts.size()) {
                    StoryEntry entry = drafts.get(index);
                    onSelectListener.run(entry, entry.isVideo ? prepareBlurredThumb(cell) : null);
                    return;
                }
                index -= drafts.size();
            }

            if (index >= 0 && index < selectedPhotos.size()) {
                MediaController.PhotoEntry entry = selectedPhotos.get(index);
                onSelectListener.run(entry, entry.isVideo ? prepareBlurredThumb(cell) : null);
            }
        });
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                onScroll();
                invalidate();
            }
        });

        actionBar = new ActionBar(context, resourcesProvider);
        actionBar.setBackgroundColor(0xff1f1f1f);
        actionBar.setTitleColor(0xFFFFFFFF);
        actionBar.setAlpha(0f);
        actionBar.setVisibility(View.GONE);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setItemsBackgroundColor(436207615, false);
        actionBar.setItemsColor(0xFFFFFFFF, false);
        actionBar.setItemsColor(0xFFFFFFFF, true);
        addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (onBackClickListener != null) {
                        onBackClickListener.run();
                    }
                } else if (id >= 10) {
                    selectAlbum(dropDownAlbums.get(id - 10), false);
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        dropDownContainer = new ActionBarMenuItem(context, menu, 0, 0, resourcesProvider) {
            @Override
            public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(info);
                info.setText(dropDown.getText());
            }
        };
        dropDownContainer.setSubMenuOpenSide(1);
        actionBar.addView(dropDownContainer, 0, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, AndroidUtilities.isTablet() ? 64 : 56, 0, 40, 0));
        dropDownContainer.setOnClickListener(view -> dropDownContainer.toggleSubMenu());

        dropDown = new TextView(context);
        dropDown.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        dropDown.setGravity(Gravity.LEFT);
        dropDown.setSingleLine(true);
        dropDown.setLines(1);
        dropDown.setMaxLines(1);
        dropDown.setEllipsize(TextUtils.TruncateAt.END);
        dropDown.setTextColor(0xFFFFFFFF);
        dropDown.setTypeface(AndroidUtilities.bold());
        dropDownDrawable = context.getResources().getDrawable(R.drawable.ic_arrow_drop_down).mutate();
        dropDownDrawable.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.MULTIPLY));
        dropDown.setCompoundDrawablePadding(AndroidUtilities.dp(4));
        dropDown.setPadding(0, AndroidUtilities.statusBarHeight, AndroidUtilities.dp(10), 0);
        dropDownContainer.addView(dropDown, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 16, 0, 0, 0));

        searchContainer = new FrameLayout(context);
        searchContainer.setVisibility(View.GONE);
        searchContainer.setAlpha(0f);
        addView(searchContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        searchListView = new RecyclerListView(context, resourcesProvider);
        searchListView.setLayoutManager(searchLayoutManager = new GridLayoutManager(context, 3));
        searchListView.setAdapter(searchAdapterImages = new SearchAdapter() {
            @Override
            protected void onLoadingUpdate(boolean loading) {
                if (searchItem != null) {
                    searchItem.setShowSearchProgress(loading);
                }
                searchEmptyView.showProgress(loading, true);
            }

            @Override
            public void notifyDataSetChanged() {
                super.notifyDataSetChanged();
                if (TextUtils.isEmpty(query)) {
                    searchEmptyView.setStickerType(StickerEmptyView.STICKER_TYPE_ALBUM);
                    searchEmptyView.title.setText(LocaleController.getString(R.string.SearchImagesType));
                } else {
                    searchEmptyView.setStickerType(StickerEmptyView.STICKER_TYPE_SEARCH);
                    searchEmptyView.title.setText(LocaleController.formatString(R.string.NoResultFoundFor, query));
                }
            }
        });
        searchListView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (searchListView.scrollingByUser && searchItem != null && searchItem.getSearchField() != null) {
                    AndroidUtilities.hideKeyboard(searchItem.getSearchContainer());
                }
            }
        });
        searchListView.setClipToPadding(true);
        searchListView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                final int sz = AndroidUtilities.dp(4);
                outRect.top = 0;
                outRect.left = outRect.right = outRect.bottom = sz;
                if ((parent.getChildAdapterPosition(view) % 3) != 2) {
                    outRect.right = 0;
                }
            }
        });
        searchContainer.addView(searchListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        FlickerLoadingView flickerLoadingView = new FlickerLoadingView(context, resourcesProvider) {
            @Override
            public int getColumnsCount() {
                return 3;
            }
        };
        flickerLoadingView.setViewType(FlickerLoadingView.PHOTOS_TYPE);
        flickerLoadingView.setAlpha(0f);
        flickerLoadingView.setVisibility(View.GONE);
        searchContainer.addView(flickerLoadingView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        searchEmptyView = new StickerEmptyView(context, flickerLoadingView, StickerEmptyView.STICKER_TYPE_ALBUM, resourcesProvider);
        searchEmptyView.title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        searchEmptyView.title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
        searchEmptyView.title.setTypeface(null);
        searchEmptyView.title.setText(LocaleController.getString(R.string.SearchImagesType));
        keyboardNotifier = new KeyboardNotifier(this, h -> searchEmptyView.animate().translationY(-h / 2f + dp(80)).setDuration(AdjustPanLayoutHelper.keyboardDuration).setInterpolator(AdjustPanLayoutHelper.keyboardInterpolator).start());
        searchContainer.addView(searchEmptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        searchListView.setEmptyView(searchEmptyView);

        searchItem = menu.addItem(0, R.drawable.ic_ab_search).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {

            private AnimatorSet animatorSet;

            @Override
            public void onSearchCollapse() {
                if (animatorSet != null) {
                    animatorSet.cancel();
                }
                ArrayList<Animator> animators = new ArrayList<>();

                dropDownContainer.setVisibility(View.VISIBLE);
                animators.add(ObjectAnimator.ofFloat(dropDownContainer, SCALE_X, 1f));
                animators.add(ObjectAnimator.ofFloat(dropDownContainer, SCALE_Y, 1f));
                animators.add(ObjectAnimator.ofFloat(dropDownContainer, ALPHA, 1f));

                final View searchField = searchItem.getSearchField();
                if (searchField != null) {
                    animators.add(ObjectAnimator.ofFloat(searchField, SCALE_X, .8f));
                    animators.add(ObjectAnimator.ofFloat(searchField, SCALE_Y, .8f));
                    animators.add(ObjectAnimator.ofFloat(searchField, ALPHA, 0f));
                }

                listView.setVisibility(View.VISIBLE);
                animators.add(ObjectAnimator.ofFloat(listView, ALPHA, 1f));
                listView.setFastScrollVisible(true);
                animators.add(ObjectAnimator.ofFloat(searchContainer, ALPHA, 0f));

                ValueAnimator va = ValueAnimator.ofFloat(0, 1);
                va.addUpdateListener(anm -> GalleryListView.this.invalidate());
                animators.add(va);

                animatorSet = new AnimatorSet();
                animatorSet.setDuration(320);
                animatorSet.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                animatorSet.playTogether(animators);
                animatorSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (searchField != null) {
                            searchField.setVisibility(View.INVISIBLE);
                        }
                        searchContainer.setVisibility(View.GONE);
                    }
                });
                animatorSet.start();
            }

            @Override
            public void onSearchExpand() {
                if (animatorSet != null) {
                    animatorSet.cancel();
                }
                ArrayList<Animator> animators = new ArrayList<>();

                animators.add(ObjectAnimator.ofFloat(dropDownContainer, SCALE_X, .8f));
                animators.add(ObjectAnimator.ofFloat(dropDownContainer, SCALE_Y, .8f));
                animators.add(ObjectAnimator.ofFloat(dropDownContainer, ALPHA, 0f));

                final EditTextBoldCursor searchField = searchItem.getSearchField();
                if (searchField != null) {
                    searchField.setVisibility(View.VISIBLE);
                    searchField.setHandlesColor(0xffffffff);
                    animators.add(ObjectAnimator.ofFloat(searchField, SCALE_X, 1f));
                    animators.add(ObjectAnimator.ofFloat(searchField, SCALE_Y, 1f));
                    animators.add(ObjectAnimator.ofFloat(searchField, ALPHA, 1f));
                }

                searchContainer.setVisibility(View.VISIBLE);
                animators.add(ObjectAnimator.ofFloat(listView, ALPHA, 0f));
                listView.setFastScrollVisible(false);
                animators.add(ObjectAnimator.ofFloat(searchContainer, ALPHA, 1f));
                searchEmptyView.setVisibility(View.VISIBLE);

                ValueAnimator va = ValueAnimator.ofFloat(0, 1);
                va.addUpdateListener(anm -> GalleryListView.this.invalidate());
                animators.add(va);

                animatorSet = new AnimatorSet();
                animatorSet.setDuration(320);
                animatorSet.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                animatorSet.playTogether(animators);
                animatorSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        dropDownContainer.setVisibility(View.GONE);
                        listView.setVisibility(View.GONE);
                    }
                });
                animatorSet.start();
            }

            @Override
            public void onTextChanged(EditText editText) {
                final String query = editText.getText().toString();
                searchAdapterImages.load(query);
            }
        });
        searchItem.setVisibility(View.GONE);
        searchItem.setSearchFieldHint(LocaleController.getString(R.string.SearchImagesTitle));

        searchListView.setOnItemClickListener((view, position) -> {
            if (searchItem != null) {
                AndroidUtilities.hideKeyboard(searchItem.getSearchContainer());
            }
            if (position < 0 || position >= searchAdapterImages.results.size()) {
                return;
            }
            if (onSelectListener != null) {
                onSelectListener.run(searchAdapterImages.results.get(position), null);
            }
        });

        drafts.clear();
        if (!onlyPhotos) {
            ArrayList<StoryEntry> draftArray = MessagesController.getInstance(currentAccount).getStoriesController().getDraftsController().drafts;
            for (StoryEntry draft : draftArray) {
                if (!draft.isEdit && !draft.isError) {
                    drafts.add(draft);
                }
            }
        }

        updateAlbumsDropDown();
        if (startAlbum != null && (startAlbum != draftsAlbum || drafts.size() > 0)) {
            selectedAlbum = startAlbum;
        } else {
            if (dropDownAlbums == null || dropDownAlbums.isEmpty()) {
                selectedAlbum = MediaController.allMediaAlbumEntry;
            } else {
                selectedAlbum = dropDownAlbums.get(0);
            }
        }
        selectedPhotos = getPhotoEntries(selectedAlbum);
        updateContainsDrafts();
        if (selectedAlbum == MediaController.allMediaAlbumEntry) {
            dropDown.setText(LocaleController.getString(R.string.ChatGallery));
        } else if (selectedAlbum == draftsAlbum) {
            dropDown.setText(LocaleController.getString("StoryDraftsAlbum"));
        } else {
            dropDown.setText(selectedAlbum.bucketName);
        }
    }

    public void allowSearch(boolean allow) {
        searchItem.setVisibility(allow ? View.VISIBLE : View.GONE);
    }

    private ArrayList<MediaController.PhotoEntry> getPhotoEntries(MediaController.AlbumEntry album) {
        if (album == null) {
            return new ArrayList<>();
        }
        if (!onlyPhotos) {
            return album.photos;
        }
        ArrayList<MediaController.PhotoEntry> photos = new ArrayList<>();
        for (int i = 0; i < album.photos.size(); ++i) {
            MediaController.PhotoEntry entry = album.photos.get(i);
            if (!entry.isVideo) {
                photos.add(entry);
            }
        }
        return photos;
    }

    public void openSearch() {
        actionBar.onSearchFieldVisibilityChanged(searchItem.toggleSearch(true));
    }

    public boolean onBackPressed() {
        if (searchItem != null && searchItem.isSearchFieldVisible()) {
            EditTextBoldCursor editText = searchItem.getSearchField();
            if (keyboardNotifier.keyboardVisible()) {
                AndroidUtilities.hideKeyboard(editText);
                return true;
            }
            actionBar.onSearchFieldVisibilityChanged(searchItem.toggleSearch(true));
            return true;
        }
        return false;
    }

    private final AnimatedFloat actionBarT = new AnimatedFloat(this, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);

    @Override
    protected void dispatchDraw(Canvas canvas) {
        float top = top();
        final boolean shouldActionBarBeShown = top <= Math.max(0, AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight() - AndroidUtilities.dp(32));
        float actionBarT = this.actionBarT.set(shouldActionBarBeShown);
        top = AndroidUtilities.lerp(top, 0, actionBarT);
        if (shouldActionBarBeShown != actionBarShown) {
            onFullScreen(actionBarShown = shouldActionBarBeShown);
            listView.getFastScroll().animate().alpha(actionBarShown ? 1f : 0).start();
        }
        if (actionBar != null) {
            actionBar.setAlpha(actionBarT);
            int visibility = actionBarT <= 0 ? View.GONE : View.VISIBLE;
            if (actionBar.getVisibility() != visibility) {
                actionBar.setVisibility(visibility);
            }
        }
        if (headerView != null) {
            headerView.setAlpha(1f - actionBarT);
        }
        AndroidUtilities.rectTmp.set(0, top, getWidth(), getHeight() + dp(14));
        canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(14), dp(14), backgroundPaint);
        canvas.save();
        canvas.clipRect(0, top, getWidth(), getHeight());
        super.dispatchDraw(canvas);
        canvas.restore();
    }

    public MediaController.AlbumEntry getSelectedAlbum() {
        return selectedAlbum;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        listView.setPinnedSectionOffsetY(AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight());
        listView.setPadding(dp(6), AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight(), dp(1), AndroidUtilities.navigationBarHeight);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) searchContainer.getLayoutParams();
        lp.leftMargin = 0;
        lp.topMargin = AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight();
        lp.rightMargin = 0;
        lp.bottomMargin = AndroidUtilities.navigationBarHeight;
        dropDown.setPadding(0, AndroidUtilities.statusBarHeight, AndroidUtilities.dp(10), 0);
        dropDown.setTextSize(!AndroidUtilities.isTablet() && AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y ? 18 : 20);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private Bitmap prepareBlurredThumb(Cell cell) {
        Bitmap bitmap = cell.bitmap;
        if (bitmap != null && !bitmap.isRecycled()) {
            return Utilities.stackBlurBitmapWithScaleFactor(bitmap, 6f);
        }
        return null;
    }

    public boolean firstLayout = true;
    protected void firstLayout() {}
    protected void onScroll() {}
    protected void onFullScreen(boolean isFullscreen) {}

    private Runnable onBackClickListener;
    public void setOnBackClickListener(Runnable onBackClickListener) {
        this.onBackClickListener = onBackClickListener;
    }

    private Utilities.Callback2<Object, Bitmap> onSelectListener;
    public void setOnSelectListener(Utilities.Callback2<Object, Bitmap> onSelectListener) {
        this.onSelectListener = onSelectListener;
    }

    private static final MediaController.AlbumEntry draftsAlbum = new MediaController.AlbumEntry(-1, null, null);
    private final ArrayList<StoryEntry> drafts = new ArrayList<>();
    private boolean containsDraftFolder, containsDrafts;

    public MediaController.AlbumEntry selectedAlbum;
    public ArrayList<MediaController.PhotoEntry> selectedPhotos;
    private ArrayList<MediaController.AlbumEntry> dropDownAlbums;

    private void updateAlbumsDropDown() {
        dropDownContainer.removeAllSubItems();
        ArrayList<MediaController.AlbumEntry> albums = MediaController.allMediaAlbums;
        dropDownAlbums = new ArrayList<>(albums);
        Collections.sort(dropDownAlbums, (o1, o2) -> {
            if (o1.bucketId == 0 && o2.bucketId != 0) {
                return -1;
            } else if (o1.bucketId != 0 && o2.bucketId == 0) {
                return 1;
            }
            int index1 = albums.indexOf(o1);
            int index2 = albums.indexOf(o2);
            if (index1 > index2) {
                return 1;
            } else if (index1 < index2) {
                return -1;
            } else {
                return 0;
            }
        });
        if (!drafts.isEmpty()) {
            dropDownAlbums.add(dropDownAlbums.isEmpty() ? 0 : 1, draftsAlbum);
        }
        if (dropDownAlbums.isEmpty()) {
            dropDown.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
        } else {
            dropDown.setCompoundDrawablesWithIntrinsicBounds(null, null, dropDownDrawable, null);
            for (int a = 0, N = dropDownAlbums.size(); a < N; a++) {
                MediaController.AlbumEntry album = dropDownAlbums.get(a);
                AlbumButton button;
                if (album == draftsAlbum) {
                    button = new AlbumButton(getContext(), album.coverPhoto, LocaleController.getString("StoryDraftsAlbum"), drafts.size(), resourcesProvider);
                } else {
                    ArrayList<MediaController.PhotoEntry> photoEntries = getPhotoEntries(album);
                    if (photoEntries.isEmpty()) {
                        continue;
                    }
                    button = new AlbumButton(getContext(), album.coverPhoto, album.bucketName, photoEntries.size(), resourcesProvider);
                }
                dropDownContainer.getPopupLayout().addView(button);
                button.setOnClickListener(e -> {
                    selectAlbum(album, false);
                    dropDownContainer.closeSubMenu();
                });
            }
        }
    }

    private void selectAlbum(MediaController.AlbumEntry album, boolean scrollAnimated) {
        selectedAlbum = album;
        selectedPhotos = getPhotoEntries(selectedAlbum);
        updateContainsDrafts();
        if (selectedAlbum == MediaController.allMediaAlbumEntry) {
            dropDown.setText(LocaleController.getString(R.string.ChatGallery));
        } else if (selectedAlbum == draftsAlbum) {
            dropDown.setText(LocaleController.getString("StoryDraftsAlbum"));
        } else {
            dropDown.setText(selectedAlbum.bucketName);
        }
        adapter.notifyDataSetChanged();
        if (scrollAnimated) {
            LinearSmoothScrollerCustom linearSmoothScroller = new LinearSmoothScrollerCustom(getContext(), LinearSmoothScrollerCustom.POSITION_TOP);
            linearSmoothScroller.setTargetPosition(1);
            linearSmoothScroller.setOffset(-ActionBar.getCurrentActionBarHeight() + AndroidUtilities.dp(16));
            layoutManager.startSmoothScroll(linearSmoothScroller);
        } else {
            layoutManager.scrollToPositionWithOffset(1, -ActionBar.getCurrentActionBarHeight() + AndroidUtilities.dp(16));
        }
    }

    private static final int VIEW_TYPE_PADDING = 0;
    private static final int VIEW_TYPE_HEADER = 1;
    private static final int VIEW_TYPE_ENTRY = 2;

    private static final float ASPECT_RATIO = 1.39f;

    private static class Cell extends View {

        private Bitmap bitmap;
        private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private LinearGradient gradient;
        private final Matrix bitmapMatrix = new Matrix();
        private final Matrix gradientMatrix = new Matrix();

        private final Paint durationBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint durationTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint draftTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Drawable durationPlayDrawable;

        private boolean drawDurationPlay;
        private StaticLayout durationLayout;
        private float durationLayoutWidth, durationLayoutLeft;

        private StaticLayout draftLayout;
        private float draftLayoutWidth, draftLayoutLeft;

        public Cell(Context context) {
            super(context);

            bgPaint.setColor(0x10ffffff);

            durationBackgroundPaint.setColor(0x4c000000);
            durationTextPaint.setTypeface(AndroidUtilities.bold());
            durationTextPaint.setTextSize(dpf2(12.66f));
            durationTextPaint.setColor(0xffffffff);
            draftTextPaint.setTextSize(AndroidUtilities.dp(11.33f));
            draftTextPaint.setColor(0xffffffff);
            durationPlayDrawable = context.getResources().getDrawable(R.drawable.play_mini_video).mutate();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            AndroidUtilities.cancelRunOnUIThread(unload);
            if (currentObject != null) {
                loadBitmap(currentObject);
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            AndroidUtilities.runOnUIThread(unload, 250);
        }

        private final Runnable unload = () -> loadBitmap(null);

        public void set(StoryEntry storyEntry, int draftsCount) {
            currentObject = storyEntry;
            if (draftsCount > 0) {
                setDraft(false);
                setDuration(LocaleController.formatPluralString("StoryDrafts", draftsCount));
                drawDurationPlay = false;
            } else {
                setDraft(storyEntry != null && storyEntry.isDraft);
                setDuration(storyEntry != null && storyEntry.isVideo ? AndroidUtilities.formatShortDuration((int) Math.max(0L, storyEntry.duration * (storyEntry.right - storyEntry.left) / 1000L)) : null);
            }
            loadBitmap(storyEntry);
        }

        public void set(MediaController.PhotoEntry photoEntry) {
            currentObject = photoEntry;
            setDuration(photoEntry != null && photoEntry.isVideo ? AndroidUtilities.formatShortDuration(photoEntry.duration) : null);
            setDraft(false);
            loadBitmap(photoEntry);
            invalidate();
        }

        private DispatchQueue myQueue;
        private static ArrayList<DispatchQueue> allQueues = new ArrayList<>();
        private static int allQueuesIndex;

        private static final HashMap<String, Integer> bitmapsUseCounts = new HashMap<>();
        private static final LruCache<String, Bitmap> bitmapsCache = new LruCache<String, Bitmap>(45) {
            @Override
            protected void entryRemoved(boolean evicted, String key, Bitmap oldValue, Bitmap newValue) {
                if (oldValue.isRecycled() || bitmapsUseCounts.containsKey(key)) {
                    return;
                }
                oldValue.recycle();
            }
        };

        private static Bitmap getBitmap(String key) {
            if (key == null) {
                return null;
            }
            Bitmap bitmap = bitmapsCache.get(key);
            if (bitmap != null) {
                Integer count = bitmapsUseCounts.get(key);
                bitmapsUseCounts.put(key, count == null ? 1 : count + 1);
            }
            return bitmap;
        }

        private static void releaseBitmap(String key) {
            if (key == null) {
                return;
            }
            Integer count = bitmapsUseCounts.get(key);
            if (count != null) {
                count--;
                if (count <= 0) {
                    bitmapsUseCounts.remove(key);
                } else {
                    bitmapsUseCounts.put(key, count);
                }
            }
        }

        private static void putBitmap(String key, Bitmap bitmap) {
            if (key == null || bitmap == null) {
                return;
            }
            bitmapsCache.put(key, bitmap);
            Integer count = bitmapsUseCounts.get(key);
            if (count != null) {
                bitmapsUseCounts.put(key, count + 1);
            } else {
                bitmapsUseCounts.put(key, 1);
            }
        }

        private static void releaseAllBitmaps() {
            bitmapsUseCounts.clear();
            bitmapsCache.evictAll();
        }

        public static void cleanupQueues() {
            releaseAllBitmaps();
            for (int i = 0; i < allQueues.size(); ++i) {
                allQueues.get(i).cleanupQueue();
                allQueues.get(i).recycle();
            }
            allQueues.clear();
        }

        private DispatchQueue getQueue() {
            if (myQueue != null) {
                return myQueue;
            }
            if (allQueues.size() < 4) {
                allQueues.add(myQueue = new DispatchQueue("gallery_load_" + allQueues.size()));
            } else {
                allQueuesIndex++;
                if (allQueuesIndex >= allQueues.size()) {
                    allQueuesIndex = 0;
                }
                myQueue = allQueues.get(allQueuesIndex);
            }
            return myQueue;
        }

        private String currentKey;
        private Object currentObject;

        private Pair<Bitmap, int[]> getThumbnail(Object entry) {
            if (entry == null) {
                return null;
            }

            int rw = (int) Math.min(AndroidUtilities.displaySize.x / 3f, dp(330));
            int rh = (int) (rw * ASPECT_RATIO);

            int[] colors = null;
            Bitmap bitmap = null;
            if (entry instanceof MediaController.PhotoEntry) {
                MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) entry;

                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                readBitmap(photoEntry, opts);

                StoryEntry.setupScale(opts, rw, rh);
                opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
                opts.inDither = true;
                opts.inJustDecodeBounds = false;
                bitmap = readBitmap(photoEntry, opts);

                final boolean needGradient = bitmap != null && ((float) bitmap.getHeight() / bitmap.getWidth()) < ASPECT_RATIO;
                if (needGradient) {
                    if (photoEntry.gradientTopColor == 0 && photoEntry.gradientBottomColor == 0 && bitmap != null && !bitmap.isRecycled()) {
                        colors = DominantColors.getColorsSync(true, bitmap, true);
                        photoEntry.gradientTopColor = colors[0];
                        photoEntry.gradientBottomColor = colors[1];
                    } else if (photoEntry.gradientTopColor != 0 && photoEntry.gradientBottomColor != 0) {
                        colors = new int[] {photoEntry.gradientTopColor, photoEntry.gradientBottomColor};
                    }
                }
            } else if (entry instanceof StoryEntry) {
                File file = ((StoryEntry) entry).draftThumbFile;

                if (file != null) {
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(file.getPath(), opts);

                    StoryEntry.setupScale(opts, rw, rh);
                    opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    opts.inDither = true;
                    opts.inJustDecodeBounds = false;
                    bitmap = BitmapFactory.decodeFile(file.getPath(), opts);
                }
            }

            return new Pair<>(bitmap, colors);
        }

        private Runnable loadingBitmap;

        private void loadBitmap(Object entry) {
            if (entry == null) {
                releaseBitmap(currentKey);
                currentKey = null;
                bitmap = null;
                invalidate();
                return;
            }

            final String key;
            if (entry instanceof MediaController.PhotoEntry) {
                key = key((MediaController.PhotoEntry) entry);
            } else if (entry instanceof StoryEntry) {
                key = "d" + ((StoryEntry) entry).draftId;
            } else {
                key = null;
            }
            if (TextUtils.equals(key, currentKey)) {
                return;
            }
            if (currentKey != null) {
                bitmap = null;
                releaseBitmap(currentKey);
                invalidate();
            }
            currentKey = key;
            gradientPaint.setShader(null);
            gradient = null;
            if (entry instanceof MediaController.PhotoEntry) {
                MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) entry;
                if (photoEntry.gradientTopColor != 0 && photoEntry.gradientBottomColor != 0) {
                    gradientPaint.setShader(gradient = new LinearGradient(0, 0, 0, 1, new int[] { photoEntry.gradientTopColor, photoEntry.gradientBottomColor }, new float[] { 0, 1 }, Shader.TileMode.CLAMP));
                    updateMatrix();
                }
            }
            if ((bitmap = getBitmap(key)) != null) {
                invalidate();
                return;
            }
            if (loadingBitmap != null) {
                getQueue().cancelRunnable(loadingBitmap);
                loadingBitmap = null;
            }
            getQueue().postRunnable(loadingBitmap = () -> {
                Pair<Bitmap, int[]> result = getThumbnail(entry);
                AndroidUtilities.runOnUIThread(() -> afterLoad(key, result.first, result.second));
            });
        }

        private void afterLoad(String key, Bitmap bitmap, int[] colors) {
            if (bitmap == null) {
                return;
            }
            putBitmap(key, bitmap);
            if (!TextUtils.equals(key, currentKey)) {
                releaseBitmap(key);
                return;
            }
            this.bitmap = bitmap;
            if (colors == null) {
                gradientPaint.setShader(null);
                gradient = null;
            } else {
                gradientPaint.setShader(gradient = new LinearGradient(0, 0, 0, 1, colors, new float[]{0, 1}, Shader.TileMode.CLAMP));
            }
            updateMatrix();
            invalidate();
        }

        private void updateMatrix() {
            if (getMeasuredWidth() > 0 && getMeasuredHeight() > 0 && bitmap != null) {
                float s;
                if ((float) bitmap.getHeight() / bitmap.getWidth() > ASPECT_RATIO - .1f) {
                    s = Math.max(getMeasuredWidth() / (float) bitmap.getWidth(), getMeasuredHeight() / (float) bitmap.getHeight());
                } else {
                    s = getMeasuredWidth() / (float) bitmap.getWidth();
                }
                bitmapMatrix.reset();
                bitmapMatrix.postScale(s, s);
                bitmapMatrix.postTranslate((getMeasuredWidth() - s * bitmap.getWidth()) / 2f, (getMeasuredHeight() - s * bitmap.getHeight()) / 2f);
            }
            if (getMeasuredHeight() > 0) {
                gradientMatrix.reset();
                gradientMatrix.postScale(1, getMeasuredHeight());
                if (gradient != null) {
                    gradient.setLocalMatrix(gradientMatrix);
                }
            }
        }

        private Bitmap readBitmap(MediaController.PhotoEntry photoEntry, BitmapFactory.Options options) {
            if (photoEntry == null) {
                return null;
            }

            if (photoEntry.thumbPath != null) {
                return BitmapFactory.decodeFile(photoEntry.thumbPath, options);
            } else if (photoEntry.isVideo) {
                return MediaStore.Video.Thumbnails.getThumbnail(getContext().getContentResolver(), photoEntry.imageId, MediaStore.Video.Thumbnails.MINI_KIND, options);
            } else {
//                Uri uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, photoEntry.imageId);
//
//                Bitmap bitmap = null;
//                InputStream is = null;
//                try {
//                    is = getContext().getContentResolver().openInputStream(uri);
//                    bitmap = BitmapFactory.decodeStream(is, null, options);
//                } catch (Exception e) {
//                    FileLog.e(e, false);
//                } finally {
//                    if (is != null) {
//                        try {
//                            is.close();
//                        } catch (Exception e2) {}
//                    }
//                }
//                if (bitmap == null && options != null && !options.inJustDecodeBounds) {
//                    return BitmapFactory.decodeFile(photoEntry.path, options);
//                } else {
//                    return bitmap;
//                }
                return MediaStore.Images.Thumbnails.getThumbnail(getContext().getContentResolver(), photoEntry.imageId, MediaStore.Video.Thumbnails.MINI_KIND, options);
            }
        }

        private String key(MediaController.PhotoEntry photoEntry) {
            if (photoEntry == null) {
                return "";
            }
            if (photoEntry.thumbPath != null) {
                return photoEntry.thumbPath;
            } else if (photoEntry.isVideo) {
                return "" + photoEntry.imageId;
            } else {
                return photoEntry.path;
            }
        }

        public void setRounding(boolean topLeft, boolean topRight) {
            this.topLeft = topLeft;
            this.topRight = topRight;
        }

        private boolean topLeft, topRight;
        private final Path clipPath = new Path();
        private final float[] radii = new float[8];

        @Override
        public void draw(Canvas canvas) {

            boolean restore = false;
            if (topLeft || topRight) {
                canvas.save();
                clipPath.rewind();
                AndroidUtilities.rectTmp.set(0, 0, getWidth(), getHeight());
                radii[0] = radii[1] = topLeft ? dp(6) : 0;
                radii[2] = radii[3] = topRight ? dp(6) : 0;
                clipPath.addRoundRect(AndroidUtilities.rectTmp, radii, Path.Direction.CW);
                canvas.clipPath(clipPath);
                restore = true;
            }

            super.draw(canvas);

            canvas.drawRect(0, 0, getWidth(), getHeight(), bgPaint);
            if (gradient != null) {
                canvas.drawRect(0, 0, getWidth(), getHeight(), gradientPaint);
            }

            if (bitmap != null && !bitmap.isRecycled()) {
                canvas.drawBitmap(bitmap, bitmapMatrix, bitmapPaint);
            }

            if (draftLayout != null) {
                AndroidUtilities.rectTmp.set(dp(4), dp(4), dp(4 + 6) + draftLayoutWidth + dp(6), dp(5) + draftLayout.getHeight() + dp(2));
                canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(10), dp(10), durationBackgroundPaint);

                canvas.save();
                canvas.translate(AndroidUtilities.rectTmp.left + dp(6) - draftLayoutLeft, AndroidUtilities.rectTmp.top + dp(1.33f));
                draftLayout.draw(canvas);
                canvas.restore();
            }

            if (durationLayout != null) {
                AndroidUtilities.rectTmp.set(dp(4), getHeight() - dp(4) - durationLayout.getHeight() - dp(2), dp(4) + (drawDurationPlay ? dp(16) : dp(4)) + durationLayoutWidth + dp(5), getHeight() - dp(4));
                canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(10), dp(10), durationBackgroundPaint);

                if (drawDurationPlay) {
                    durationPlayDrawable.setBounds(
                        (int) (AndroidUtilities.rectTmp.left + dp(6)),
                        (int) (AndroidUtilities.rectTmp.centerY() - dp(8) / 2),
                        (int) (AndroidUtilities.rectTmp.left + dp(13)),
                        (int) (AndroidUtilities.rectTmp.centerY() + dp(8) / 2)
                    );
                    durationPlayDrawable.draw(canvas);
                }

                canvas.save();
                canvas.translate(AndroidUtilities.rectTmp.left + (drawDurationPlay ? dp(16) : dp(5)) - durationLayoutLeft, AndroidUtilities.rectTmp.top + dp(1));
                durationLayout.draw(canvas);
                canvas.restore();
            }

            if (restore) {
                canvas.restore();
            }
        }

        private void setDuration(String duration) {
            if (!TextUtils.isEmpty(duration)) {
                durationLayout = new StaticLayout(duration, durationTextPaint, getMeasuredWidth() > 0 ? getMeasuredWidth() : AndroidUtilities.displaySize.x, Layout.Alignment.ALIGN_NORMAL, 1, 0, false);
                durationLayoutWidth = durationLayout.getLineCount() > 0 ? durationLayout.getLineWidth(0) : 0;
                durationLayoutLeft = durationLayout.getLineCount() > 0 ? durationLayout.getLineLeft(0) : 0;
            } else {
                durationLayout = null;
            }
            drawDurationPlay = true;
        }

        private void setDraft(boolean draft) {
            if (draft) {
                draftLayout = new StaticLayout(LocaleController.getString("StoryDraft"), draftTextPaint, getMeasuredWidth() > 0 ? getMeasuredWidth() : AndroidUtilities.displaySize.x, Layout.Alignment.ALIGN_NORMAL, 1, 0, false);
                draftLayoutWidth = draftLayout.getLineCount() > 0 ? draftLayout.getLineWidth(0) : 0;
                draftLayoutLeft = draftLayout.getLineCount() > 0 ? draftLayout.getLineLeft(0) : 0;
            } else {
                draftLayout = null;
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            final int w = MeasureSpec.getSize(widthMeasureSpec);
            setMeasuredDimension(w, (int) (w * ASPECT_RATIO));
            updateMatrix();
        }
    }

    private class EmptyView extends View {

        int height;

        public EmptyView(Context context) {
            super(context);
        }

        public void setHeight(int height) {
            this.height = height;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            final int width = MeasureSpec.getSize(widthMeasureSpec);
            if (height == -1) {
                final int photosCount;
                if (selectedAlbum == draftsAlbum) {
                    photosCount = drafts.size();
                } else if (selectedPhotos != null) {
                    photosCount = selectedPhotos.size() + (containsDraftFolder ? 1 : 0) + (containsDrafts ? drafts.size() : 0);
                } else {
                    photosCount = 0;
                }
                final int rowsCount = (int) Math.ceil(photosCount / (float) layoutManager.getSpanCount());
                final int fullHeight = AndroidUtilities.displaySize.y - AndroidUtilities.dp(62);
                final int itemWidth = (int) (width / (float) layoutManager.getSpanCount());
                final int itemHeight = (int) (itemWidth * ASPECT_RATIO);
                final int height = Math.max(0, fullHeight - itemHeight * rowsCount);
                setMeasuredDimension(width, height);
            } else {
                setMeasuredDimension(width, height);
            }
        }
    }

    private class HeaderView extends FrameLayout {

        public TextView textView;
        public ImageView searchButton;

        public HeaderView(Context context, boolean onlyPhotos) {
            super(context);
            setPadding(dp(onlyPhotos ? 14 : 16), dp(16), dp(8), dp(10));

            if (onlyPhotos && false) {
                searchButton = new ImageView(context);
                searchButton.setImageResource(R.drawable.ic_ab_search);
                searchButton.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
                searchButton.setBackground(Theme.createSelectorDrawable(436207615));
                searchButton.setOnClickListener(view -> openSearch());
                addView(searchButton, LayoutHelper.createFrame(24, 24, Gravity.RIGHT | Gravity.CENTER_VERTICAL));
            }

            textView = new TextView(context);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            textView.setTextColor(0xFFFFFFFF);
            textView.setTypeface(AndroidUtilities.bold());
            textView.setText(LocaleController.getString(onlyPhotos ? R.string.AddImage : R.string.ChoosePhotoOrVideo));
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 0, 0, onlyPhotos ? 32 : 0, 0));
        }
    }

    private HeaderView headerView;

    private class Adapter extends RecyclerListView.FastScrollAdapter {

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == VIEW_TYPE_ENTRY;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            if (viewType == VIEW_TYPE_PADDING) {
                view = new EmptyView(getContext());
            } else if (viewType == VIEW_TYPE_HEADER) {
                view = headerView = new HeaderView(getContext(), onlyPhotos);
            } else {
                view = new Cell(getContext());
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            final int viewType = holder.getItemViewType();
            if (viewType == VIEW_TYPE_PADDING) {
                ((EmptyView) holder.itemView).setHeight(position == 0 ? getPadding() : -1);
            } else if (viewType == VIEW_TYPE_ENTRY) {
                Cell cell = (Cell) holder.itemView;
                cell.setRounding(position == 2, position == 4);

                int index = position - 2;
                if (containsDraftFolder) {
                    if (index == 0) {
                        cell.set(drafts.get(0), drafts.size());
                        return;
                    }
                    index--;
                } else if (containsDrafts) {
                    if (index >= 0 && index < drafts.size()) {
                        cell.set(drafts.get(index), 0);
                        return;
                    }
                    index -= drafts.size();
                }

                if (selectedPhotos == null || index < 0 || index >= selectedPhotos.size()) {
                    return;
                }
                cell.set(selectedPhotos.get(index));
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0 || position == getItemCount() - 1) {
                return VIEW_TYPE_PADDING;
            } else if (position == 1) {
                return VIEW_TYPE_HEADER;
            } else {
                return VIEW_TYPE_ENTRY;
            }
        }

        @Override
        public int getItemCount() {
            return 2 + getTotalItemsCount() + 1;
        }

        @Override
        public String getLetter(int position) {
            position -= 2;
            if (containsDraftFolder) {
                if (position == 0) {
                    return null;
                }
                position--;
            } else if (containsDrafts) {
                if (position >= 0 && position < drafts.size()) {
                    StoryEntry draft = drafts.get(position);
                    return LocaleController.formatYearMont(draft.draftDate / 1000L, true);
                }
                position -= drafts.size();
            }
            if (selectedPhotos != null && position >= 0 && position < selectedPhotos.size()) {
                MediaController.PhotoEntry entry = selectedPhotos.get(position);
                if (entry != null) {
                    long date = entry.dateTaken;
                    if (Build.VERSION.SDK_INT <= 28) {
                        date /= 1000;
                    }
                    return LocaleController.formatYearMont(date, true);
                }
            }
            return null;
        }

        @Override
        public int getTotalItemsCount() {
            int count = selectedPhotos == null ? 0 : selectedPhotos.size();
            if (containsDraftFolder) {
                count++;
            } else if (containsDrafts) {
                count += drafts.size();
            }
            return count;
        }

        @Override
        public void getPositionForScrollProgress(RecyclerListView listView, float progress, int[] position) {
            final int itemsCount = getTotalItemsCount();
            final int itemWidth = (int) ((listView.getWidth() - listView.getPaddingLeft() - listView.getPaddingRight()) / (float) layoutManager.getSpanCount());
            final int itemHeight = (int) (itemWidth * ASPECT_RATIO);
            final int rowsCount = (int) Math.ceil(itemsCount / (float) layoutManager.getSpanCount());
            final int totalItemsHeight = rowsCount * itemHeight;
            final int viewHeight = AndroidUtilities.displaySize.y - listView.getPaddingTop() - listView.getPaddingBottom();
            final int top = AndroidUtilities.lerp(0, Math.max(0, totalItemsHeight - viewHeight), progress);
//            final int bottom = AndroidUtilities.lerp(viewHeight, totalItemsHeight, progress);
            final float topRow = top / (float) totalItemsHeight * rowsCount;
            final int topRowInt = Math.round(topRow);
//            final int bottomRow = Math.round(bottom / (float) totalItemsHeight * rowsCount);

            position[0] = 2 + Math.max(0, topRowInt * layoutManager.getSpanCount());
            position[1] = listView.getPaddingTop() + (int) ((topRow - topRowInt) * itemHeight);
        }

        @Override
        public float getScrollProgress(RecyclerListView listView) {
            final int itemsCount = getTotalItemsCount();
            final int itemWidth = (int) ((listView.getWidth() - listView.getPaddingLeft() - listView.getPaddingRight()) / (float) layoutManager.getSpanCount());
            final int itemHeight = (int) (itemWidth * ASPECT_RATIO);
            final int rowsCount = (int) Math.ceil(itemsCount / (float) layoutManager.getSpanCount());
            final int totalItemsHeight = rowsCount * itemHeight;
            final int viewHeight = AndroidUtilities.displaySize.y - listView.getPaddingTop();
            return (Math.max(0, listView.computeVerticalScrollOffset() - getPadding()) - listView.getPaddingTop()) / (float) (totalItemsHeight - viewHeight);
        }
    }

    public int getPadding() {
        return (int) (AndroidUtilities.displaySize.y * 0.35f);
    }

    public int top() {
        int resultTop;
        if (listView == null || listView.getChildCount() <= 0) {
            resultTop = getPadding();
        } else {
            int top = Integer.MAX_VALUE;
            if (listView != null) {
                for (int i = 0; i < listView.getChildCount(); ++i) {
                    View child = listView.getChildAt(i);
                    int position = listView.getChildAdapterPosition(child);
                    if (position > 0) {
                        top = Math.min(top, (int) child.getY());
                    }
                }
            }
            resultTop = Math.max(0, Math.min(top, getHeight()));
        }
        if (listView == null) {
            return resultTop;
        }
        return AndroidUtilities.lerp(0, resultTop, listView.getAlpha());
    }

    @Override
    protected void onAttachedToWindow() {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.albumsDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.storiesDraftsUpdated);
        super.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.albumsDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.storiesDraftsUpdated);

        Cell.cleanupQueues();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.albumsDidLoad) {
            updateAlbumsDropDown();
            if (selectedAlbum == null) {
                if (dropDownAlbums == null || dropDownAlbums.isEmpty()) {
                    selectedAlbum = MediaController.allMediaAlbumEntry;
                } else {
                    selectedAlbum = dropDownAlbums.get(0);
                }
            } else {
                for (int a = 0; a < MediaController.allMediaAlbums.size(); a++) {
                    MediaController.AlbumEntry entry = MediaController.allMediaAlbums.get(a);
                    if (entry.bucketId == selectedAlbum.bucketId && entry.videoOnly == selectedAlbum.videoOnly) {
                        selectedAlbum = entry;
                        break;
                    }
                }
            }
            selectedPhotos = getPhotoEntries(selectedAlbum);
            updateContainsDrafts();
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        } else if (id == NotificationCenter.storiesDraftsUpdated) {
            updateDrafts();
        }
    }

    public void updateDrafts() {
        drafts.clear();
        if (!onlyPhotos) {
            ArrayList<StoryEntry> draftArray = MessagesController.getInstance(currentAccount).getStoriesController().getDraftsController().drafts;
            for (StoryEntry draft : draftArray) {
                if (!draft.isEdit && !draft.isError) {
                    drafts.add(draft);
                }
            }
        }
        updateAlbumsDropDown();
        updateContainsDrafts();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void updateContainsDrafts() {
        containsDraftFolder = dropDownAlbums != null && !dropDownAlbums.isEmpty() && dropDownAlbums.get(0) == selectedAlbum && drafts.size() > 2;
        containsDrafts = !containsDraftFolder && (selectedAlbum == draftsAlbum || dropDownAlbums != null && !dropDownAlbums.isEmpty() && dropDownAlbums.get(0) == selectedAlbum);
    }

    public static final int SEARCH_TYPE_IMAGES = 0;
    public static final int SEARCH_TYPE_GIFS = 1;

    private class SearchAdapter extends RecyclerListView.SelectionAdapter {

        public int type;
        public ArrayList<TLObject> results = new ArrayList<TLObject>();
        private boolean full;
        private boolean loading;

        private int currentReqId = -1;
        public String query;
        private String lastOffset;

        private TLRPC.User bot;
        private boolean triedResolvingBot;

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(new BackupImageView(getContext()) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    final int size = MeasureSpec.getSize(widthMeasureSpec);
                    setMeasuredDimension(size, size);
                }
            });
        }

        private Drawable loadingDrawable = new ColorDrawable(0x10ffffff);

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            BackupImageView imageView = ((BackupImageView) holder.itemView);
            TLObject obj = results.get(position);
            if (obj instanceof TLRPC.Document) {
                imageView.setImage(ImageLocation.getForDocument((TLRPC.Document) obj), "200_200", loadingDrawable, null);
            } else if (obj instanceof TLRPC.Photo) {
                TLRPC.Photo photo = (TLRPC.Photo) obj;
                TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, 320);
                imageView.setImage(ImageLocation.getForPhoto(photoSize, photo), "200_200", loadingDrawable, null);
            } else if (obj instanceof TLRPC.BotInlineResult) {
                TLRPC.BotInlineResult res = (TLRPC.BotInlineResult) obj;
                if (res.thumb != null) {
                    ImageLocation location = ImageLocation.getForPath(res.thumb.url);
                    imageView.setImage(location, "200_200", loadingDrawable, res);
                } else {
                    imageView.clearImage();
                }
            } else {
                imageView.clearImage();
            }
        }

        @Override
        public int getItemCount() {
            return results.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        public void load(String query) {
            if (!TextUtils.equals(this.query, query)) {
                if (currentReqId != -1) {
                    ConnectionsManager.getInstance(currentAccount).cancelRequest(currentReqId, true);
                    currentReqId = -1;
                }
                loading = false;
                lastOffset = null;
            }
            this.query = query;
            AndroidUtilities.cancelRunOnUIThread(searchRunnable);
            if (TextUtils.isEmpty(query)) {
                this.results.clear();
                onLoadingUpdate(false);
                notifyDataSetChanged();
            } else {
                onLoadingUpdate(true);
                AndroidUtilities.runOnUIThread(searchRunnable, 1500);
            }
        }

        private final Runnable searchRunnable = this::loadInternal;

        private void loadInternal() {
            if (loading) {
                return;
            }

            onLoadingUpdate(loading = true);

            final MessagesController messagesController = MessagesController.getInstance(currentAccount);

            final String botUsername = type == SEARCH_TYPE_GIFS ? messagesController.gifSearchBot : messagesController.imageSearchBot;
            if (bot == null) {
                TLObject c = messagesController.getUserOrChat(botUsername);
                if (c instanceof TLRPC.User) {
                    bot = (TLRPC.User) c;
                }
            }
            if (bot == null && !triedResolvingBot) {
                TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
                req.username = botUsername;
                currentReqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                    triedResolvingBot = true;
                    loading = false;
                    if (res instanceof TLRPC.TL_contacts_resolvedPeer) {
                        TLRPC.TL_contacts_resolvedPeer response = (TLRPC.TL_contacts_resolvedPeer) res;
                        messagesController.putUsers(response.users, false);
                        messagesController.putChats(response.chats, false);
                        MessagesStorage.getInstance(currentAccount).putUsersAndChats(response.users, response.chats, true, true);
                        loadInternal();
                    }
                }));
                return;
            }
            if (bot == null) {
                return;
            }

            TLRPC.TL_messages_getInlineBotResults req = new TLRPC.TL_messages_getInlineBotResults();
            req.bot = messagesController.getInputUser(bot);
            req.query = query == null ? "" : query;
            req.peer = new TLRPC.TL_inputPeerEmpty();
            req.offset = lastOffset == null ? "" : lastOffset;
            final boolean emptyOffset = TextUtils.isEmpty(req.offset);
            currentReqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                if (res instanceof TLRPC.messages_BotResults) {
                    TLRPC.messages_BotResults response = (TLRPC.messages_BotResults) res;
                    lastOffset = response.next_offset;

                    if (emptyOffset) {
                        results.clear();
                    }

                    for (int i = 0; i < response.results.size(); ++i) {
                        TLRPC.BotInlineResult result = response.results.get(i);
                        if (result.document != null) {
                            results.add(result.document);
                        } else if (result.photo != null) {
                            results.add(result.photo);
                        } else if (result.content != null) {
                            results.add(result);
                        }
                    }

                    onLoadingUpdate(loading = false);

                    notifyDataSetChanged();
                }
            }));
        }

        protected void onLoadingUpdate(boolean loading) {

        }
    }
}
