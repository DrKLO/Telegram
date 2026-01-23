/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.DividerCell;
import org.telegram.ui.Cells.PhotoAttachPhotoCell;
import org.telegram.ui.Cells.SharedDocumentCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.EditTextEmoji;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.RecyclerViewItemRangeSelector;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.StickerEmptyView;

import java.util.ArrayList;
import java.util.HashMap;

public class PhotoPickerActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    public interface PhotoPickerActivityDelegate {
        void selectedPhotosChanged();
        void actionButtonPressed(boolean canceled, boolean notify, int scheduleDate, int scheduleRepeatPeriod);
        void onCaptionChanged(CharSequence caption);
        default void onOpenInPressed() {

        }

        default boolean canFinishFragment() {
            return true;
        }
    }

    public interface PhotoPickerActivitySearchDelegate {
        void shouldSearchText(String text);
        void shouldClearRecentSearch();
    }

    private int type;
    private HashMap<Object, Object> selectedPhotos;
    private ArrayList<Object> selectedPhotosOrder;
    private CharSequence caption;
    private boolean allowIndices;

    private ArrayList<MediaController.SearchImage> searchResult = new ArrayList<>();
    private HashMap<String, MediaController.SearchImage> searchResultKeys = new HashMap<>();
    private HashMap<String, MediaController.SearchImage> searchResultUrls = new HashMap<>();

    private ArrayList<String> recentSearches = new ArrayList<>();

    private boolean searching;
    private boolean imageSearchEndReached = true;
    private String lastSearchString;
    private String nextImagesSearchOffset;
    private int imageReqId;
    private int lastSearchToken;
    private boolean allowCaption;
    private boolean searchingUser;
    private String lastSearchImageString;

    private int maxSelectedPhotos;
    private boolean allowOrder = true;

    private MediaController.AlbumEntry selectedAlbum;

    private RecyclerListView listView;
    private ListAdapter listAdapter;
    private GridLayoutManager layoutManager;
    private StickerEmptyView emptyView;
    private FlickerLoadingView flickerView;
    private ActionBarMenuItem searchItem;
    private ActionBarMenuSubItem showAsListItem;
    private int itemSize = 100;
    private boolean sendPressed;
    private int selectPhotoType;
    private ChatActivity chatActivity;
    private RecyclerViewItemRangeSelector itemRangeSelector;
    private int alertOnlyOnce;
    private boolean shouldSelect;

    private boolean listSort;

    protected FrameLayout frameLayout2;
    protected FrameLayout writeButtonContainer;
    protected View selectedCountView;
    protected View shadow;
    protected EditTextEmoji commentTextView;
    private ImageView writeButton;
    private Drawable writeButtonDrawable;
    private SizeNotifierFrameLayout sizeNotifierFrameLayout;
    private int itemsPerRow = 3;
    private TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private RectF rect = new RectF();
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private AnimatorSet animatorSet;

    private boolean isDocumentsPicker;

    private ActionBarPopupWindow sendPopupWindow;
    private ActionBarPopupWindow.ActionBarPopupWindowLayout sendPopupLayout;
    private ActionBarMenuSubItem[] itemCells;

    private String initialSearchString;

    private boolean needsBottomLayout = true;
    private final boolean forceDarckTheme;

    private final static int change_sort = 1;
    private final static int open_in = 2;

    private PhotoPickerActivityDelegate delegate;
    private PhotoPickerActivitySearchDelegate searchDelegate;

    private final int dialogBackgroundKey;
    private final int textKey;
    private final int selectorKey;

    private PhotoViewer.PhotoViewerProvider provider = new PhotoViewer.EmptyPhotoViewerProvider() {
        @Override
        public boolean scaleToFill() {
            return false;
        }

        @Override
        public PhotoViewer.PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index, boolean needPreview, boolean closing) {
            PhotoAttachPhotoCell cell = getCellForIndex(index);
            if (cell != null) {
                BackupImageView imageView = cell.getImageView();
                int[] coords = new int[2];
                imageView.getLocationInWindow(coords);
                PhotoViewer.PlaceProviderObject object = new PhotoViewer.PlaceProviderObject();
                object.viewX = coords[0];
                object.viewY = coords[1] - (Build.VERSION.SDK_INT >= 21 ? 0 : AndroidUtilities.statusBarHeight);
                object.parentView = listView;
                object.imageReceiver = imageView.getImageReceiver();
                object.thumb = object.imageReceiver.getBitmapSafe();
                object.scale = cell.getScale();
                cell.showCheck(false);
                return object;
            }
            return null;
        }

        @Override
        public void updatePhotoAtIndex(int index) {
            PhotoAttachPhotoCell cell = getCellForIndex(index);
            if (cell != null) {
                if (selectedAlbum != null) {
                    BackupImageView imageView = cell.getImageView();
                    imageView.setOrientation(0, true);
                    MediaController.PhotoEntry photoEntry = selectedAlbum.photos.get(index);
                    if (photoEntry.thumbPath != null) {
                        imageView.setImage(photoEntry.thumbPath, null, Theme.chat_attachEmptyDrawable);
                    } else if (photoEntry.path != null) {
                        imageView.setOrientation(photoEntry.orientation, photoEntry.invert, true);
                        if (photoEntry.isVideo) {
                            imageView.setImage("vthumb://" + photoEntry.imageId + ":" + photoEntry.path, null, Theme.chat_attachEmptyDrawable);
                        } else {
                            imageView.setImage("thumb://" + photoEntry.imageId + ":" + photoEntry.path, null, Theme.chat_attachEmptyDrawable);
                        }
                    } else {
                        imageView.setImageDrawable(Theme.chat_attachEmptyDrawable);
                    }
                } else {
                    cell.setPhotoEntry(searchResult.get(index), true, false);
                }
            }
        }

        @Override
        public boolean allowCaption() {
            return allowCaption;
        }

        @Override
        public ImageReceiver.BitmapHolder getThumbForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) {
            PhotoAttachPhotoCell cell = getCellForIndex(index);
            if (cell != null) {
                return cell.getImageView().getImageReceiver().getBitmapSafe();
            }
            return null;
        }

        @Override
        public void willSwitchFromPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) {
            int count = listView.getChildCount();
            for (int a = 0; a < count; a++) {
                View view = listView.getChildAt(a);
                if (view.getTag() == null) {
                    continue;
                }
                PhotoAttachPhotoCell cell = (PhotoAttachPhotoCell) view;
                int num = (Integer) view.getTag();
                if (selectedAlbum != null) {
                    if (num < 0 || num >= selectedAlbum.photos.size()) {
                        continue;
                    }
                } else {
                    if (num < 0 || num >= searchResult.size()) {
                        continue;
                    }
                }
                if (num == index) {
                    cell.showCheck(true);
                    break;
                }
            }
        }

        @Override
        public void willHidePhotoViewer() {
            int count = listView.getChildCount();
            for (int a = 0; a < count; a++) {
                View view = listView.getChildAt(a);
                if (view instanceof PhotoAttachPhotoCell) {
                    PhotoAttachPhotoCell cell = (PhotoAttachPhotoCell) view;
                    cell.showCheck(true);
                }
            }
        }

        @Override
        public boolean isPhotoChecked(int index) {
            if (selectedAlbum != null) {
                return !(index < 0 || index >= selectedAlbum.photos.size()) && selectedPhotos.containsKey(selectedAlbum.photos.get(index).imageId);
            } else {
                return !(index < 0 || index >= searchResult.size()) && selectedPhotos.containsKey(searchResult.get(index).id);
            }
        }

        @Override
        public int setPhotoUnchecked(Object object) {
            Object key = null;
            if (object instanceof MediaController.PhotoEntry) {
                key = ((MediaController.PhotoEntry) object).imageId;
            } else if (object instanceof MediaController.SearchImage) {
                key = ((MediaController.SearchImage) object).id;
            }
            if (key == null) {
                return -1;
            }
            if (selectedPhotos.containsKey(key)) {
                selectedPhotos.remove(key);
                int position = selectedPhotosOrder.indexOf(key);
                if (position >= 0) {
                    selectedPhotosOrder.remove(position);
                }
                if (allowIndices) {
                    updateCheckedPhotoIndices();
                }
                return position;
            }
            return -1;
        }

        @Override
        public int setPhotoChecked(int index, VideoEditedInfo videoEditedInfo) {
            boolean add = true;
            int num;
            if (selectedAlbum != null) {
                if (index < 0 || index >= selectedAlbum.photos.size()) {
                    return -1;
                }
                MediaController.PhotoEntry photoEntry = selectedAlbum.photos.get(index);
                if ((num = addToSelectedPhotos(photoEntry, -1)) == -1) {
                    photoEntry.editedInfo = videoEditedInfo;
                    num = selectedPhotosOrder.indexOf(photoEntry.imageId);
                } else {
                    add = false;
                    photoEntry.editedInfo = null;
                }
            } else {
                if (index < 0 || index >= searchResult.size()) {
                    return -1;
                }
                MediaController.SearchImage photoEntry = searchResult.get(index);
                if ((num = addToSelectedPhotos(photoEntry, -1)) == -1) {
                    photoEntry.editedInfo = videoEditedInfo;
                    num = selectedPhotosOrder.indexOf(photoEntry.id);
                } else {
                    add = false;
                    photoEntry.editedInfo = null;
                }
            }
            int count = listView.getChildCount();
            for (int a = 0; a < count; a++) {
                View view = listView.getChildAt(a);
                int tag = (Integer) view.getTag();
                if (tag == index) {
                    ((PhotoAttachPhotoCell) view).setChecked(allowIndices ? num : -1, add, false);
                    break;
                }
            }
            updatePhotosButton(add ? 1 : 2);
            delegate.selectedPhotosChanged();
            return num;
        }

        @Override
        public boolean cancelButtonPressed() {
            delegate.actionButtonPressed(true, true, 0, 0);
            finishFragment();
            return true;
        }

        @Override
        public int getSelectedCount() {
            return selectedPhotos.size();
        }

        @Override
        public void sendButtonPressed(int index, VideoEditedInfo videoEditedInfo, boolean notify, int scheduleDate, int scheduleRepeatPeriod, boolean forceDocument) {
            if (selectedPhotos.isEmpty()) {
                if (selectedAlbum != null) {
                    if (index < 0 || index >= selectedAlbum.photos.size()) {
                        return;
                    }
                    MediaController.PhotoEntry photoEntry = selectedAlbum.photos.get(index);
                    photoEntry.editedInfo = videoEditedInfo;
                    addToSelectedPhotos(photoEntry, -1);
                } else {
                    if (index < 0 || index >= searchResult.size()) {
                        return;
                    }
                    MediaController.SearchImage searchImage = searchResult.get(index);
                    searchImage.editedInfo = videoEditedInfo;
                    addToSelectedPhotos(searchImage, -1);
                }
            }
            sendSelectedPhotos(notify, scheduleDate, 0);
        }

        @Override
        public ArrayList<Object> getSelectedPhotosOrder() {
            return selectedPhotosOrder;
        }

        @Override
        public HashMap<Object, Object> getSelectedPhotos() {
            return selectedPhotos;
        }
    };

    public PhotoPickerActivity(int type, MediaController.AlbumEntry selectedAlbum, HashMap<Object, Object> selectedPhotos, ArrayList<Object> selectedPhotosOrder, int selectPhotoType, boolean allowCaption, ChatActivity chatActivity, boolean forceDarkTheme) {
        super();
        this.selectedAlbum = selectedAlbum;
        this.selectedPhotos = selectedPhotos;
        this.selectedPhotosOrder = selectedPhotosOrder;
        this.type = type;
        this.selectPhotoType = selectPhotoType;
        this.chatActivity = chatActivity;
        this.allowCaption = allowCaption;
        this.forceDarckTheme = forceDarkTheme;

        if (selectedAlbum == null) {
            loadRecentSearch();
        }

        if (forceDarkTheme) {
            dialogBackgroundKey = Theme.key_voipgroup_dialogBackground;
            textKey = Theme.key_voipgroup_actionBarItems;
            selectorKey = Theme.key_voipgroup_actionBarItemsSelector;
        } else {
            dialogBackgroundKey = Theme.key_dialogBackground;
            textKey = Theme.key_dialogTextBlack;
            selectorKey = Theme.key_dialogButtonSelector;
        }
    }

    public void setDocumentsPicker(boolean value) {
        isDocumentsPicker = value;
    }

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.closeChats);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.closeChats);
        if (imageReqId != 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(imageReqId, true);
            imageReqId = 0;
        }
        if (commentTextView != null) {
            commentTextView.onDestroy();
        }
        super.onFragmentDestroy();
    }

    @SuppressWarnings("unchecked")
    @Override
    public View createView(Context context) {
        listSort = false;

        actionBar.setBackgroundColor(Theme.getColor(dialogBackgroundKey));
        actionBar.setTitleColor(Theme.getColor(textKey));
        actionBar.setItemsColor(Theme.getColor(textKey), false);
        actionBar.setItemsBackgroundColor(Theme.getColor(selectorKey), false);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        if (selectedAlbum != null) {
            actionBar.setTitle(selectedAlbum.bucketName);
        } else if (type == 0) {
            actionBar.setTitle(LocaleController.getString(R.string.SearchImagesTitle));
        } else if (type == 1) {
            actionBar.setTitle(LocaleController.getString(R.string.SearchGifsTitle));
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == change_sort) {
                    listSort = !listSort;
                    if (listSort) {
                        listView.setPadding(0, 0, 0, AndroidUtilities.dp(48));
                    } else {
                        listView.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(8), AndroidUtilities.dp(6), AndroidUtilities.dp(50));
                    }
                    listView.stopScroll();
                    layoutManager.scrollToPositionWithOffset(0, 0);
                    listAdapter.notifyDataSetChanged();
                } else if (id == open_in) {
                    if (delegate != null) {
                        delegate.onOpenInPressed();
                    }
                    finishFragment();
                }
            }
        });

        if (isDocumentsPicker) {
            ActionBarMenu menu = actionBar.createMenu();
            ActionBarMenuItem menuItem = menu.addItem(0, R.drawable.ic_ab_other);
            menuItem.setSubMenuDelegate(new ActionBarMenuItem.ActionBarSubMenuItemDelegate() {
                @Override
                public void onShowSubMenu() {
                    showAsListItem.setText(listSort ? LocaleController.getString(R.string.ShowAsGrid) : LocaleController.getString(R.string.ShowAsList));
                    showAsListItem.setIcon(listSort ? R.drawable.msg_media : R.drawable.msg_list);
                }

                @Override
                public void onHideSubMenu() {
                }
            });
            showAsListItem = menuItem.addSubItem(change_sort, R.drawable.msg_list, LocaleController.getString(R.string.ShowAsList));
            menuItem.addSubItem(open_in, R.drawable.msg_openin, LocaleController.getString(R.string.OpenInExternalApp));
        }

        if (selectedAlbum == null) {
            ActionBarMenu menu = actionBar.createMenu();
            searchItem = menu.addItem(0, R.drawable.ic_ab_search).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                @Override
                public void onSearchExpand() {

                }

                @Override
                public boolean canCollapseSearch() {
                    finishFragment();
                    return false;
                }

                @Override
                public void onTextChanged(EditText editText) {
                    if (editText.getText().length() == 0) {
                        searchResult.clear();
                        searchResultKeys.clear();
                        lastSearchString = null;
                        imageSearchEndReached = true;
                        searching = false;
                        if (imageReqId != 0) {
                            ConnectionsManager.getInstance(currentAccount).cancelRequest(imageReqId, true);
                            imageReqId = 0;
                        }
                        emptyView.title.setText(LocaleController.getString(R.string.NoRecentSearches));
                        emptyView.showProgress(false);
                        updateSearchInterface();
                    } else {
                        AndroidUtilities.cancelRunOnUIThread(updateSearch);
                        AndroidUtilities.runOnUIThread(updateSearch, 1200);
                    }
                }

                Runnable updateSearch = () -> processSearch(searchItem.getSearchField());

                @Override
                public void onSearchPressed(EditText editText) {
                    processSearch(editText);
                }
            });
            EditTextBoldCursor editText = searchItem.getSearchField();
            editText.setTextColor(Theme.getColor(textKey));
            editText.setCursorColor(Theme.getColor(textKey));
            editText.setHintTextColor(Theme.getColor(Theme.key_chat_messagePanelHint));
        }

        if (selectedAlbum == null) {
            if (type == 0) {
                searchItem.setSearchFieldHint(LocaleController.getString(R.string.SearchImagesTitle));
            } else if (type == 1) {
                searchItem.setSearchFieldHint(LocaleController.getString(R.string.SearchGifsTitle));
            }
        }

        sizeNotifierFrameLayout = new SizeNotifierFrameLayout(context) {

            private int lastNotifyWidth;
            private boolean ignoreLayout;
            private int lastItemSize;

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int totalHeight = MeasureSpec.getSize(heightMeasureSpec);

                int availableWidth = MeasureSpec.getSize(widthMeasureSpec);
                if (AndroidUtilities.isTablet()) {
                    itemsPerRow = 4;
                } else if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
                    itemsPerRow = 4;
                } else {
                    itemsPerRow = 3;
                }

                ignoreLayout = true;
                itemSize = (availableWidth - AndroidUtilities.dp(6 * 2) - AndroidUtilities.dp(5 * 2)) / itemsPerRow;

                if (lastItemSize != itemSize) {
                    lastItemSize = itemSize;
                    AndroidUtilities.runOnUIThread(() -> listAdapter.notifyDataSetChanged());
                }
                if (listSort) {
                    layoutManager.setSpanCount(1);
                } else {
                    layoutManager.setSpanCount(itemSize * itemsPerRow + AndroidUtilities.dp(5) * (itemsPerRow - 1));
                }

                ignoreLayout = false;
                onMeasureInternal(widthMeasureSpec, MeasureSpec.makeMeasureSpec(totalHeight, MeasureSpec.EXACTLY));
            }

            private void onMeasureInternal(int widthMeasureSpec, int heightMeasureSpec) {
                int widthSize = MeasureSpec.getSize(widthMeasureSpec);
                int heightSize = MeasureSpec.getSize(heightMeasureSpec);

                setMeasuredDimension(widthSize, heightSize);

                int kbHeight = measureKeyboardHeight();
                int keyboardSize = 0;
                if (keyboardSize <= AndroidUtilities.dp(20)) {
                    if (!AndroidUtilities.isInMultiwindow && commentTextView != null && frameLayout2.getParent() == this) {
                        heightSize -= commentTextView.getEmojiPadding();
                        heightMeasureSpec = MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.EXACTLY);
                    }
                }
                if (kbHeight > AndroidUtilities.dp(20) && commentTextView != null) {
                    ignoreLayout = true;
                    commentTextView.hideEmojiView();
                    ignoreLayout = false;
                }

                if (commentTextView != null && commentTextView.isPopupShowing()) {
                    fragmentView.setTranslationY(0);
                    listView.setTranslationY(0);
                    emptyView.setTranslationY(0);
                }

                int childCount = getChildCount();
                for (int i = 0; i < childCount; i++) {
                    View child = getChildAt(i);
                    if (child == null || child.getVisibility() == GONE) {
                        continue;
                    }
                    if (commentTextView != null && commentTextView.isPopupView(child)) {
                        if (AndroidUtilities.isInMultiwindow || AndroidUtilities.isTablet()) {
                            if (AndroidUtilities.isTablet()) {
                                child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Math.min(AndroidUtilities.dp(AndroidUtilities.isTablet() ? 200 : 320), heightSize - AndroidUtilities.statusBarHeight + getPaddingTop()), MeasureSpec.EXACTLY));
                            } else {
                                child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(heightSize - AndroidUtilities.statusBarHeight + getPaddingTop(), MeasureSpec.EXACTLY));
                            }
                        } else {
                            child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(child.getLayoutParams().height, MeasureSpec.EXACTLY));
                        }
                    } else {
                        measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                    }
                }
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                if (lastNotifyWidth != r - l) {
                    lastNotifyWidth = r - l;
                    if (listAdapter != null) {
                        listAdapter.notifyDataSetChanged();
                    }
                    if (sendPopupWindow != null && sendPopupWindow.isShowing()) {
                        sendPopupWindow.dismiss();
                    }
                }
                final int count = getChildCount();

                int keyboardSize = 0;
                int paddingBottom = commentTextView != null && frameLayout2.getParent() == this && keyboardSize <= AndroidUtilities.dp(20) && !AndroidUtilities.isInMultiwindow && !AndroidUtilities.isTablet() ? commentTextView.getEmojiPadding() : 0;
                setBottomClip(paddingBottom);

                for (int i = 0; i < count; i++) {
                    final View child = getChildAt(i);
                    if (child.getVisibility() == GONE) {
                        continue;
                    }
                    final LayoutParams lp = (LayoutParams) child.getLayoutParams();

                    final int width = child.getMeasuredWidth();
                    final int height = child.getMeasuredHeight();

                    int childLeft;
                    int childTop;

                    int gravity = lp.gravity;
                    if (gravity == -1) {
                        gravity = Gravity.TOP | Gravity.LEFT;
                    }

                    final int absoluteGravity = gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
                    final int verticalGravity = gravity & Gravity.VERTICAL_GRAVITY_MASK;

                    switch (absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
                        case Gravity.CENTER_HORIZONTAL:
                            childLeft = (r - l - width) / 2 + lp.leftMargin - lp.rightMargin;
                            break;
                        case Gravity.RIGHT:
                            childLeft = (r - l) - width - lp.rightMargin - getPaddingRight();
                            break;
                        case Gravity.LEFT:
                        default:
                            childLeft = lp.leftMargin + getPaddingLeft();
                    }

                    switch (verticalGravity) {
                        case Gravity.TOP:
                            childTop = lp.topMargin + getPaddingTop();
                            break;
                        case Gravity.CENTER_VERTICAL:
                            childTop = ((b - paddingBottom) - t - height) / 2 + lp.topMargin - lp.bottomMargin;
                            break;
                        case Gravity.BOTTOM:
                            childTop = ((b - paddingBottom) - t) - height - lp.bottomMargin;
                            break;
                        default:
                            childTop = lp.topMargin;
                    }

                    if (commentTextView != null && commentTextView.isPopupView(child)) {
                        if (AndroidUtilities.isTablet()) {
                            childTop = getMeasuredHeight() - child.getMeasuredHeight();
                        } else {
                            childTop = getMeasuredHeight() + keyboardSize - child.getMeasuredHeight();
                        }
                    }
                    child.layout(childLeft, childTop, childLeft + width, childTop + height);
                }

                notifyHeightChanged();
            }

            @Override
            public void requestLayout() {
                if (ignoreLayout) {
                    return;
                }
                super.requestLayout();
            }
        };
        sizeNotifierFrameLayout.setBackgroundColor(Theme.getColor(dialogBackgroundKey));
        fragmentView = sizeNotifierFrameLayout;

        listView = new RecyclerListView(context);
        listView.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(8), AndroidUtilities.dp(6), AndroidUtilities.dp(50));
        listView.setClipToPadding(false);
        listView.setHorizontalScrollBarEnabled(false);
        listView.setVerticalScrollBarEnabled(false);
        listView.setItemAnimator(null);
        listView.setLayoutAnimation(null);
        listView.setLayoutManager(layoutManager = new GridLayoutManager(context, 4) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }
        });
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                if (listAdapter.getItemViewType(position) == 1 || listSort || selectedAlbum == null && TextUtils.isEmpty(lastSearchString)) {
                    return layoutManager.getSpanCount();
                } else {
                    return itemSize + (position % itemsPerRow != itemsPerRow - 1 ? AndroidUtilities.dp(5) : 0);
                }
            }
        });
        sizeNotifierFrameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));
        listView.setAdapter(listAdapter = new ListAdapter(context));
        listView.setGlowColor(Theme.getColor(dialogBackgroundKey));
        listView.setOnItemClickListener((view, position) -> {
            if (selectedAlbum == null && searchResult.isEmpty()) {
                if (position < recentSearches.size()) {
                    String text = recentSearches.get(position);
                    if (searchDelegate != null) {
                        searchDelegate.shouldSearchText(text);
                    } else {
                        searchItem.getSearchField().setText(text);
                        searchItem.getSearchField().setSelection(text.length());
                        processSearch(searchItem.getSearchField());
                    }
                } else if (position == recentSearches.size() + 1) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setTitle(LocaleController.getString(R.string.ClearSearchAlertTitle));
                    builder.setMessage(LocaleController.getString(R.string.ClearSearchAlert));
                    builder.setPositiveButton(LocaleController.getString(R.string.ClearButton), (dialogInterface, i) -> {
                        if (searchDelegate != null) {
                            searchDelegate.shouldClearRecentSearch();
                        } else {
                            clearRecentSearch();
                        }
                    });
                    builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
                    AlertDialog dialog = builder.create();
                    showDialog(dialog);
                    TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                    if (button != null) {
                        button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
                    }
                    return;
                }
                return;
            }
            ArrayList<Object> arrayList;
            if (selectedAlbum != null) {
                arrayList = (ArrayList) selectedAlbum.photos;
            } else {
                arrayList = (ArrayList) searchResult;
            }
            if (position < 0 || position >= arrayList.size()) {
                return;
            }
            if (searchItem != null) {
                AndroidUtilities.hideKeyboard(searchItem.getSearchField());
            }
            if (listSort) {
                onListItemClick(view, arrayList.get(position));
            } else {
                int type;
                if (selectPhotoType == PhotoAlbumPickerActivity.SELECT_TYPE_AVATAR || selectPhotoType == PhotoAlbumPickerActivity.SELECT_TYPE_AVATAR_VIDEO) {
                    type = PhotoViewer.SELECT_TYPE_AVATAR;
                } else if (selectPhotoType == PhotoAlbumPickerActivity.SELECT_TYPE_WALLPAPER) {
                    type = PhotoViewer.SELECT_TYPE_WALLPAPER;
                } else if (selectPhotoType == PhotoAlbumPickerActivity.SELECT_TYPE_QR) {
                    type = PhotoViewer.SELECT_TYPE_QR;
                } else if (chatActivity == null) {
                    type = 4;
                } else {
                    type = 0;
                }
                PhotoViewer.getInstance().setParentActivity(PhotoPickerActivity.this);
                PhotoViewer.getInstance().setMaxSelectedPhotos(maxSelectedPhotos, allowOrder);
                PhotoViewer.getInstance().openPhotoForSelect(arrayList, position, type, isDocumentsPicker, provider, chatActivity);
            }
        });
        if (maxSelectedPhotos != 1) {
            listView.setOnItemLongClickListener((view, position) -> {
                if (listSort) {
                    onListItemClick(view, selectedAlbum.photos.get(position));
                    return true;
                } else {
                    if (view instanceof PhotoAttachPhotoCell) {
                        PhotoAttachPhotoCell cell = (PhotoAttachPhotoCell) view;
                        itemRangeSelector.setIsActive(view, true, position, shouldSelect = !cell.isChecked());
                    }
                }
                return false;
            });
        }
        itemRangeSelector = new RecyclerViewItemRangeSelector(new RecyclerViewItemRangeSelector.RecyclerViewItemRangeSelectorDelegate() {
            @Override
            public int getItemCount() {
                return listAdapter.getItemCount();
            }

            @Override
            public void setSelected(View view, int index, boolean selected) {
                if (selected != shouldSelect || !(view instanceof PhotoAttachPhotoCell)) {
                    return;
                }
                PhotoAttachPhotoCell cell = (PhotoAttachPhotoCell) view;
                cell.callDelegate();
            }

            @Override
            public boolean isSelected(int index) {
                Object key;
                if (selectedAlbum != null) {
                    MediaController.PhotoEntry photoEntry = selectedAlbum.photos.get(index);
                    key = photoEntry.imageId;
                } else {
                    MediaController.SearchImage photoEntry = searchResult.get(index);
                    key = photoEntry.id;
                }
                return selectedPhotos.containsKey(key);
            }

            @Override
            public boolean isIndexSelectable(int index) {
                return listAdapter.getItemViewType(index) == 0;
            }

            @Override
            public void onStartStopSelection(boolean start) {
                alertOnlyOnce = start ? 1 : 0;
                if (start) {
                    parentLayout.getView().requestDisallowInterceptTouchEvent(true);
                }
                listView.hideSelector(true);
            }
        });
        if (maxSelectedPhotos != 1) {
            listView.addOnItemTouchListener(itemRangeSelector);
        }

        flickerView = new FlickerLoadingView(context, getResourceProvider()) {
            @Override
            public int getViewType() {
                return PHOTOS_TYPE;
            }

            @Override
            public int getColumnsCount() {
                return 3;
            }
        };
        flickerView.setAlpha(0);
        flickerView.setVisibility(View.GONE);

        emptyView = new StickerEmptyView(context, flickerView, StickerEmptyView.STICKER_TYPE_SEARCH, getResourceProvider());
        emptyView.setAnimateLayoutChange(true);
        emptyView.title.setTypeface(Typeface.DEFAULT);
        emptyView.title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        emptyView.title.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        emptyView.addView(flickerView, 0);
        if (selectedAlbum != null) {
//            emptyView.setShowAtCenter(false);
            emptyView.title.setText(LocaleController.getString(R.string.NoPhotos));
        } else {
//            emptyView.setShowAtTop(true);
//            emptyView.setPadding(0, AndroidUtilities.dp(200), 0, 0);
            emptyView.title.setText(LocaleController.getString(R.string.NoRecentSearches));
        }
        emptyView.showProgress(false, false);
        sizeNotifierFrameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 0, 126, 0, 0));

        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
                }
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (selectedAlbum == null) {
                    int firstVisibleItem = layoutManager.findFirstVisibleItemPosition();
                    int visibleItemCount = firstVisibleItem == RecyclerView.NO_POSITION ? 0 : Math.abs(layoutManager.findLastVisibleItemPosition() - firstVisibleItem) + 1;
                    if (visibleItemCount > 0) {
                        int totalItemCount = layoutManager.getItemCount();
                        if (firstVisibleItem + visibleItemCount > totalItemCount - 2 && !searching) {
                            if (!imageSearchEndReached) {
                                searchImages(type == 1, lastSearchString, nextImagesSearchOffset, true);
                            }
                        }
                    }
                }
            }
        });

        if (selectedAlbum == null) {
            updateSearchInterface();
        }

        if (needsBottomLayout) {
            shadow = new View(context);
            shadow.setBackgroundResource(R.drawable.header_shadow_reverse);
            shadow.setTranslationY(AndroidUtilities.dp(48));
            sizeNotifierFrameLayout.addView(shadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 3, Gravity.BOTTOM | Gravity.LEFT, 0, 0, 0, 48));

            frameLayout2 = new FrameLayout(context);
            frameLayout2.setBackgroundColor(Theme.getColor(dialogBackgroundKey));
            frameLayout2.setVisibility(View.INVISIBLE);
            frameLayout2.setTranslationY(AndroidUtilities.dp(48));
            sizeNotifierFrameLayout.addView(frameLayout2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.BOTTOM));
            frameLayout2.setOnTouchListener((v, event) -> true);

            if (commentTextView != null) {
                commentTextView.onDestroy();
            }
            commentTextView = new EditTextEmoji(context, sizeNotifierFrameLayout, null, EditTextEmoji.STYLE_DIALOG, false);
            InputFilter[] inputFilters = new InputFilter[1];
            inputFilters[0] = new InputFilter.LengthFilter(MessagesController.getInstance(UserConfig.selectedAccount).maxCaptionLength);
            commentTextView.setFilters(inputFilters);
            commentTextView.setHint(LocaleController.getString(R.string.AddCaption));
            commentTextView.onResume();
            EditTextBoldCursor editText = commentTextView.getEditText();
            editText.setMaxLines(1);
            editText.setSingleLine(true);
            frameLayout2.addView(commentTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 0, 84, 0));
            if (caption != null) {
                commentTextView.setText(caption);
            }
            commentTextView.getEditText().addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {

                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (delegate != null) {
                        delegate.onCaptionChanged(s);
                    }
                }
            });

            writeButtonContainer = new FrameLayout(context) {
                @Override
                public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(info);
                    info.setText(LocaleController.formatPluralString("AccDescrSendPhotos", selectedPhotos.size()));
                    info.setClassName(Button.class.getName());
                    info.setLongClickable(true);
                    info.setClickable(true);
                }
            };
            writeButtonContainer.setFocusable(true);
            writeButtonContainer.setFocusableInTouchMode(true);
            writeButtonContainer.setVisibility(View.INVISIBLE);
            writeButtonContainer.setScaleX(0.2f);
            writeButtonContainer.setScaleY(0.2f);
            writeButtonContainer.setAlpha(0.0f);
            sizeNotifierFrameLayout.addView(writeButtonContainer, LayoutHelper.createFrame(60, 60, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 12, 10));

            writeButton = new ImageView(context);
            writeButtonDrawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(56), Theme.getColor(Theme.key_dialogFloatingButton), Theme.getColor(Build.VERSION.SDK_INT >= 21 ? Theme.key_dialogFloatingButtonPressed : Theme.key_dialogFloatingButton));
            if (Build.VERSION.SDK_INT < 21) {
                Drawable shadowDrawable = context.getResources().getDrawable(R.drawable.floating_shadow_profile).mutate();
                shadowDrawable.setColorFilter(new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.MULTIPLY));
                CombinedDrawable combinedDrawable = new CombinedDrawable(shadowDrawable, writeButtonDrawable, 0, 0);
                combinedDrawable.setIconSize(AndroidUtilities.dp(56), AndroidUtilities.dp(56));
                writeButtonDrawable = combinedDrawable;
            }
            writeButton.setBackgroundDrawable(writeButtonDrawable);
            writeButton.setImageResource(R.drawable.attach_send);
            writeButton.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            writeButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogFloatingIcon), PorterDuff.Mode.MULTIPLY));
            writeButton.setScaleType(ImageView.ScaleType.CENTER);
            if (Build.VERSION.SDK_INT >= 21) {
                writeButton.setOutlineProvider(new ViewOutlineProvider() {
                    @SuppressLint("NewApi")
                    @Override
                    public void getOutline(View view, Outline outline) {
                        outline.setOval(0, 0, AndroidUtilities.dp(56), AndroidUtilities.dp(56));
                    }
                });
            }
            writeButtonContainer.addView(writeButton, LayoutHelper.createFrame(Build.VERSION.SDK_INT >= 21 ? 56 : 60, Build.VERSION.SDK_INT >= 21 ? 56 : 60, Gravity.LEFT | Gravity.TOP, Build.VERSION.SDK_INT >= 21 ? 2 : 0, 0, 0, 0));
            writeButton.setOnClickListener(v -> {
                if (chatActivity != null && chatActivity.isInScheduleMode()) {
                    AlertsCreator.createScheduleDatePickerDialog(getParentActivity(), chatActivity.getDialogId(), (notify, scheduleDate, scheduleRepeatPeriod) -> sendSelectedPhotos(notify, scheduleDate, 0));
                } else {
                    sendSelectedPhotos(true, 0, 0);
                }
            });
            writeButton.setOnLongClickListener(view -> {
                if (chatActivity == null || maxSelectedPhotos == 1) {
                    return false;
                }
                TLRPC.Chat chat = chatActivity.getCurrentChat();
                TLRPC.User user = chatActivity.getCurrentUser();

                if (sendPopupLayout == null) {
                    sendPopupLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(getParentActivity());
                    sendPopupLayout.setAnimationEnabled(false);
                    sendPopupLayout.setOnTouchListener(new View.OnTouchListener() {

                        private android.graphics.Rect popupRect = new android.graphics.Rect();

                        @Override
                        public boolean onTouch(View v, MotionEvent event) {
                            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                                if (sendPopupWindow != null && sendPopupWindow.isShowing()) {
                                    v.getHitRect(popupRect);
                                    if (!popupRect.contains((int) event.getX(), (int) event.getY())) {
                                        sendPopupWindow.dismiss();
                                    }
                                }
                            }
                            return false;
                        }
                    });
                    sendPopupLayout.setDispatchKeyEventListener(keyEvent -> {
                        if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_BACK && keyEvent.getRepeatCount() == 0 && sendPopupWindow != null && sendPopupWindow.isShowing()) {
                            sendPopupWindow.dismiss();
                        }
                    });
                    sendPopupLayout.setShownFromBottom(false);

                    itemCells = new ActionBarMenuSubItem[2];
                    for (int a = 0; a < 2; a++) {
                        if (a == 0 && !chatActivity.canScheduleMessage() || a == 1 && UserObject.isUserSelf(user)) {
                            continue;
                        }
                        int num = a;
                        itemCells[a] = new ActionBarMenuSubItem(getParentActivity(), a == 0, a == 1);
                        if (num == 0) {
                            if (UserObject.isUserSelf(user)) {
                                itemCells[a].setTextAndIcon(LocaleController.getString(R.string.SetReminder), R.drawable.msg_calendar2);
                            } else {
                                itemCells[a].setTextAndIcon(LocaleController.getString(R.string.ScheduleMessage), R.drawable.msg_calendar2);
                            }
                        } else {
                            itemCells[a].setTextAndIcon(LocaleController.getString(R.string.SendWithoutSound), R.drawable.input_notify_off);
                        }
                        itemCells[a].setMinimumWidth(AndroidUtilities.dp(196));

                        sendPopupLayout.addView(itemCells[a], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
                        itemCells[a].setOnClickListener(v -> {
                            if (sendPopupWindow != null && sendPopupWindow.isShowing()) {
                                sendPopupWindow.dismiss();
                            }
                            if (num == 0) {
                                AlertsCreator.createScheduleDatePickerDialog(getParentActivity(), chatActivity.getDialogId(), (notify, scheduleDate, scheduleRepeatPeriod) -> sendSelectedPhotos(notify, scheduleDate, 0));
                            } else {
                                sendSelectedPhotos(true, 0, 0);
                            }
                        });
                    }
                    sendPopupLayout.setupRadialSelectors(Theme.getColor(selectorKey));

                    sendPopupWindow = new ActionBarPopupWindow(sendPopupLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
                    sendPopupWindow.setAnimationEnabled(false);
                    sendPopupWindow.setAnimationStyle(R.style.PopupContextAnimation2);
                    sendPopupWindow.setOutsideTouchable(true);
                    sendPopupWindow.setClippingEnabled(true);
                    sendPopupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
                    sendPopupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
                    sendPopupWindow.getContentView().setFocusableInTouchMode(true);
                }

                sendPopupLayout.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST));
                sendPopupWindow.setFocusable(true);
                int[] location = new int[2];
                view.getLocationInWindow(location);
                sendPopupWindow.showAtLocation(view, Gravity.LEFT | Gravity.TOP, location[0] + view.getMeasuredWidth() - sendPopupLayout.getMeasuredWidth() + AndroidUtilities.dp(8), location[1] - sendPopupLayout.getMeasuredHeight() - AndroidUtilities.dp(2));
                sendPopupWindow.dimBehind();
                try {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                } catch (Exception ignored) {}

                return false;
            });

            textPaint.setTextSize(AndroidUtilities.dp(12));
            textPaint.setTypeface(AndroidUtilities.bold());

            selectedCountView = new View(context) {
                @Override
                protected void onDraw(Canvas canvas) {
                    String text = String.format("%d", Math.max(1, selectedPhotosOrder.size()));
                    int textSize = (int) Math.ceil(textPaint.measureText(text));
                    int size = Math.max(AndroidUtilities.dp(16) + textSize, AndroidUtilities.dp(24));
                    int cx = getMeasuredWidth() / 2;
                    int cy = getMeasuredHeight() / 2;

                    textPaint.setColor(Theme.getColor(Theme.key_dialogRoundCheckBoxCheck));
                    paint.setColor(Theme.getColor(dialogBackgroundKey));
                    rect.set(cx - size / 2, 0, cx + size / 2, getMeasuredHeight());
                    canvas.drawRoundRect(rect, AndroidUtilities.dp(12), AndroidUtilities.dp(12), paint);

                    paint.setColor(Theme.getColor(Theme.key_dialogRoundCheckBox));
                    rect.set(cx - size / 2 + AndroidUtilities.dp(2), AndroidUtilities.dp(2), cx + size / 2 - AndroidUtilities.dp(2), getMeasuredHeight() - AndroidUtilities.dp(2));
                    canvas.drawRoundRect(rect, AndroidUtilities.dp(10), AndroidUtilities.dp(10), paint);

                    canvas.drawText(text, cx - textSize / 2, AndroidUtilities.dp(16.2f), textPaint);
                }
            };
            selectedCountView.setAlpha(0.0f);
            selectedCountView.setScaleX(0.2f);
            selectedCountView.setScaleY(0.2f);
            sizeNotifierFrameLayout.addView(selectedCountView, LayoutHelper.createFrame(42, 24, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, -2, 9));
            if (selectPhotoType != PhotoAlbumPickerActivity.SELECT_TYPE_ALL) {
                commentTextView.setVisibility(View.GONE);
            }
        }
        allowIndices = (selectedAlbum != null || type == 0 || type == 1) && allowOrder;

        listView.setEmptyView(emptyView);
        listView.setAnimateEmptyView(true, RecyclerListView.EMPTY_VIEW_ANIMATION_TYPE_ALPHA);
        updatePhotosButton(0);

        return fragmentView;
    }

    @Override
    protected void onPanTranslationUpdate(float y) {
        if (listView == null) {
            return;
        }
        if (commentTextView.isPopupShowing()) {
            fragmentView.setTranslationY(y);
            listView.setTranslationY(0);
//            emptyView.setTranslationY(0);
        } else {
            listView.setTranslationY(y);
//            emptyView.setTranslationY(y);
        }
    }

    public void setLayoutViews(FrameLayout f2, FrameLayout button, View count, View s, EditTextEmoji emoji) {
        frameLayout2 = f2;
        writeButtonContainer = button;
        commentTextView = emoji;
        selectedCountView = count;
        shadow = s;
        needsBottomLayout = false;
    }

    private void applyCaption() {
        if (commentTextView == null || commentTextView.length() <= 0) {
            return;
        }
        Object imageId = selectedPhotosOrder.get(0);
        Object entry = selectedPhotos.get(imageId);
        if (entry instanceof MediaController.PhotoEntry) {
            MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) entry;
            photoEntry.caption = commentTextView.getText().toString();
        } else if (entry instanceof MediaController.SearchImage) {
            MediaController.SearchImage searchImage = (MediaController.SearchImage) entry;
            searchImage.caption = commentTextView.getText().toString();
        }
    }

    private void onListItemClick(View view, Object item) {
        boolean add;
        if (addToSelectedPhotos(item, -1) == -1) {
            add = true;
        } else {
            add = false;
        }
        if (view instanceof SharedDocumentCell) {
            Integer index = (Integer) view.getTag();
            MediaController.PhotoEntry photoEntry = selectedAlbum.photos.get(index);
            SharedDocumentCell cell = (SharedDocumentCell) view;
            cell.setChecked(selectedPhotosOrder.contains(photoEntry.imageId), true);
        }
        updatePhotosButton(add ? 1 : 2);
        delegate.selectedPhotosChanged();
    }

    public void clearRecentSearch() {
        recentSearches.clear();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
        emptyView.showProgress(false);
        saveRecentSearch();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
        if (commentTextView != null) {
            commentTextView.onResume();
        }
        if (searchItem != null) {
            searchItem.openSearch(true);
            if (!TextUtils.isEmpty(initialSearchString)) {
                searchItem.setSearchFieldText(initialSearchString, false);
                initialSearchString = null;
                processSearch(searchItem.getSearchField());
            }
            getParentActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.closeChats) {
            removeSelfFromStack(true);
        }
    }

    public RecyclerListView getListView() {
        return listView;
    }

    public void setCaption(CharSequence text) {
        caption = text;
        if (commentTextView != null) {
            commentTextView.setText(caption);
        }
    }

    public void setInitialSearchString(String text) {
        initialSearchString = text;
    }

    private void saveRecentSearch() {
        SharedPreferences.Editor editor = ApplicationLoader.applicationContext.getSharedPreferences("web_recent_search", Activity.MODE_PRIVATE).edit();
        editor.clear();
        editor.putInt("count", recentSearches.size());
        for (int a = 0, N = recentSearches.size(); a < N; a++) {
            editor.putString("recent" + a, recentSearches.get(a));
        }
        editor.commit();
    }

    private void loadRecentSearch() {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("web_recent_search", Activity.MODE_PRIVATE);
        int count = preferences.getInt("count", 0);
        for (int a = 0; a < count; a++) {
            String str = preferences.getString("recent" + a, null);
            if (str == null) {
                break;
            }
            recentSearches.add(str);
        }
    }

    private void addToRecentSearches(String query) {
        for (int a = 0, N = recentSearches.size(); a < N; a++) {
            String str = recentSearches.get(a);
            if (str.equalsIgnoreCase(query)) {
                recentSearches.remove(a);
                break;
            }
        }
        recentSearches.add(0, query);
        while (recentSearches.size() > 20) {
            recentSearches.remove(recentSearches.size() - 1);
        }
        saveRecentSearch();
    }

    private void processSearch(EditText editText) {
        if (editText.getText().length() == 0) {
            return;
        }
        String text = editText.getText().toString();
        searchResult.clear();
        searchResultKeys.clear();
        imageSearchEndReached = true;
        searchImages(type == 1, text, "", true);
        lastSearchString = text;
        if (lastSearchString.length() == 0) {
            lastSearchString = null;
            emptyView.title.setText(LocaleController.getString(R.string.NoRecentSearches));
        } else {
            emptyView.title.setText(LocaleController.formatString("NoResultFoundFor", R.string.NoResultFoundFor, lastSearchString));
        }
        updateSearchInterface();
    }

    private boolean showCommentTextView(boolean show, boolean animated) {
        if (commentTextView == null) {
            return false;
        }
        if (show == (frameLayout2.getTag() != null)) {
            return false;
        }
        if (animatorSet != null) {
            animatorSet.cancel();
        }
        frameLayout2.setTag(show ? 1 : null);
        if (commentTextView.getEditText().isFocused()) {
            AndroidUtilities.hideKeyboard(commentTextView.getEditText());
        }
        commentTextView.hidePopup(true);
        if (show) {
            frameLayout2.setVisibility(View.VISIBLE);
            writeButtonContainer.setVisibility(View.VISIBLE);
        }
        if (animated) {
            animatorSet = new AnimatorSet();
            ArrayList<Animator> animators = new ArrayList<>();
            animators.add(ObjectAnimator.ofFloat(writeButtonContainer, View.SCALE_X, show ? 1.0f : 0.2f));
            animators.add(ObjectAnimator.ofFloat(writeButtonContainer, View.SCALE_Y, show ? 1.0f : 0.2f));
            animators.add(ObjectAnimator.ofFloat(writeButtonContainer, View.ALPHA, show ? 1.0f : 0.0f));
            animators.add(ObjectAnimator.ofFloat(selectedCountView, View.SCALE_X, show ? 1.0f : 0.2f));
            animators.add(ObjectAnimator.ofFloat(selectedCountView, View.SCALE_Y, show ? 1.0f : 0.2f));
            animators.add(ObjectAnimator.ofFloat(selectedCountView, View.ALPHA, show ? 1.0f : 0.0f));
            animators.add(ObjectAnimator.ofFloat(frameLayout2, View.TRANSLATION_Y, show ? 0 : AndroidUtilities.dp(48)));
            animators.add(ObjectAnimator.ofFloat(shadow, View.TRANSLATION_Y, show ? 0 : AndroidUtilities.dp(48)));

            animatorSet.playTogether(animators);
            animatorSet.setInterpolator(new DecelerateInterpolator());
            animatorSet.setDuration(180);
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (animation.equals(animatorSet)) {
                        if (!show) {
                            frameLayout2.setVisibility(View.INVISIBLE);
                            writeButtonContainer.setVisibility(View.INVISIBLE);
                        }
                        animatorSet = null;
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    if (animation.equals(animatorSet)) {
                        animatorSet = null;
                    }
                }
            });
            animatorSet.start();
        } else {
            writeButtonContainer.setScaleX(show ? 1.0f : 0.2f);
            writeButtonContainer.setScaleY(show ? 1.0f : 0.2f);
            writeButtonContainer.setAlpha(show ? 1.0f : 0.0f);
            selectedCountView.setScaleX(show ? 1.0f : 0.2f);
            selectedCountView.setScaleY(show ? 1.0f : 0.2f);
            selectedCountView.setAlpha(show ? 1.0f : 0.0f);
            frameLayout2.setTranslationY(show ? 0 : AndroidUtilities.dp(48));
            shadow.setTranslationY(show ? 0 : AndroidUtilities.dp(48));
            if (!show) {
                frameLayout2.setVisibility(View.INVISIBLE);
                writeButtonContainer.setVisibility(View.INVISIBLE);
            }
        }
        return true;
    }

    public void setMaxSelectedPhotos(int value, boolean order) {
        maxSelectedPhotos = value;
        allowOrder = order;
        if (value > 0 && type == 1) {
            maxSelectedPhotos = 1;
        }
    }

    private void updateCheckedPhotoIndices() {
        if (!allowIndices) {
            return;
        }
        int count = listView.getChildCount();
        for (int a = 0; a < count; a++) {
            View view = listView.getChildAt(a);
            if (view instanceof PhotoAttachPhotoCell) {
                PhotoAttachPhotoCell cell = (PhotoAttachPhotoCell) view;
                Integer index = (Integer) view.getTag();
                if (selectedAlbum != null) {
                    MediaController.PhotoEntry photoEntry = selectedAlbum.photos.get(index);
                    cell.setNum(allowIndices ? selectedPhotosOrder.indexOf(photoEntry.imageId) : -1);
                } else {
                    MediaController.SearchImage photoEntry = searchResult.get(index);
                    cell.setNum(allowIndices ? selectedPhotosOrder.indexOf(photoEntry.id) : -1);
                }
            } else if (view instanceof SharedDocumentCell) {
                Integer index = (Integer) view.getTag();
                MediaController.PhotoEntry photoEntry = selectedAlbum.photos.get(index);
                SharedDocumentCell cell = (SharedDocumentCell) view;
                cell.setChecked(selectedPhotosOrder.indexOf(photoEntry.imageId) != 0, false);
            }
        }
    }

    private PhotoAttachPhotoCell getCellForIndex(int index) {
        int count = listView.getChildCount();

        for (int a = 0; a < count; a++) {
            View view = listView.getChildAt(a);
            if (view instanceof PhotoAttachPhotoCell) {
                PhotoAttachPhotoCell cell = (PhotoAttachPhotoCell) view;
                int num = (Integer) cell.getTag();
                if (selectedAlbum != null) {
                    if (num < 0 || num >= selectedAlbum.photos.size()) {
                        continue;
                    }
                } else {
                    if (num < 0 || num >= searchResult.size()) {
                        continue;
                    }
                }
                if (num == index) {
                    return cell;
                }
            }
        }
        return null;
    }

    private int addToSelectedPhotos(Object object, int index) {
        Object key = null;
        if (object instanceof MediaController.PhotoEntry) {
            key = ((MediaController.PhotoEntry) object).imageId;
        } else if (object instanceof MediaController.SearchImage) {
            key = ((MediaController.SearchImage) object).id;
        }
        if (key == null) {
            return -1;
        }
        if (selectedPhotos.containsKey(key)) {
            selectedPhotos.remove(key);
            int position = selectedPhotosOrder.indexOf(key);
            if (position >= 0) {
                selectedPhotosOrder.remove(position);
            }
            if (allowIndices) {
                updateCheckedPhotoIndices();
            }
            if (index >= 0) {
                if (object instanceof MediaController.PhotoEntry) {
                    ((MediaController.PhotoEntry) object).reset();
                } else if (object instanceof MediaController.SearchImage) {
                    ((MediaController.SearchImage) object).reset();
                }
                provider.updatePhotoAtIndex(index);
            }
            return position;
        } else {
            selectedPhotos.put(key, object);
            selectedPhotosOrder.add(key);
            return -1;
        }
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen && searchItem != null) {
            AndroidUtilities.showKeyboard(searchItem.getSearchField());
        }
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (commentTextView != null && commentTextView.isPopupShowing()) {
            if (invoked) commentTextView.hidePopup(true);
            return false;
        }
        return super.onBackPressed(invoked);
    }

    public void updatePhotosButton(int animated) {
        int count = selectedPhotos.size();

        if (count == 0) {
            selectedCountView.setPivotX(0);
            selectedCountView.setPivotY(0);
            showCommentTextView(false, animated != 0);
        } else {
            selectedCountView.invalidate();
            if (!showCommentTextView(true, animated != 0) && animated != 0) {
                selectedCountView.setPivotX(AndroidUtilities.dp(21));
                selectedCountView.setPivotY(AndroidUtilities.dp(12));
                AnimatorSet animatorSet = new AnimatorSet();
                animatorSet.playTogether(
                        ObjectAnimator.ofFloat(selectedCountView, View.SCALE_X, animated == 1 ? 1.1f : 0.9f, 1.0f),
                        ObjectAnimator.ofFloat(selectedCountView, View.SCALE_Y, animated == 1 ? 1.1f : 0.9f, 1.0f));
                animatorSet.setInterpolator(new OvershootInterpolator());
                animatorSet.setDuration(180);
                animatorSet.start();
            } else {
                selectedCountView.setPivotX(0);
                selectedCountView.setPivotY(0);
            }
        }
    }

    private void updateSearchInterface() {
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
        if (searching || recentSearches.size() > 0 && (lastSearchString == null || TextUtils.isEmpty(lastSearchString))) {
            emptyView.showProgress(true);
        } else {
            emptyView.showProgress(false);
        }
    }

    private void searchBotUser(boolean gif) {
        if (searchingUser) {
            return;
        }
        searchingUser = true;
        TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
        req.username = gif ? MessagesController.getInstance(currentAccount).gifSearchBot : MessagesController.getInstance(currentAccount).imageSearchBot;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            if (response != null) {
                AndroidUtilities.runOnUIThread(() -> {
                    TLRPC.TL_contacts_resolvedPeer res = (TLRPC.TL_contacts_resolvedPeer) response;
                    MessagesController.getInstance(currentAccount).putUsers(res.users, false);
                    MessagesController.getInstance(currentAccount).putChats(res.chats, false);
                    MessagesStorage.getInstance(currentAccount).putUsersAndChats(res.users, res.chats, true, true);
                    String str = lastSearchImageString;
                    lastSearchImageString = null;
                    searchImages(gif, str, "", false);
                });
            }
        });
    }

    private void searchImages(boolean gif, final String query, final String offset, boolean searchUser) {
        if (searching) {
            searching = false;
            if (imageReqId != 0) {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(imageReqId, true);
                imageReqId = 0;
            }
        }

        lastSearchImageString = query;
        searching = true;

        TLObject object = MessagesController.getInstance(currentAccount).getUserOrChat(gif ? MessagesController.getInstance(currentAccount).gifSearchBot : MessagesController.getInstance(currentAccount).imageSearchBot);
        if (!(object instanceof TLRPC.User)) {
            if (searchUser) {
                searchBotUser(gif);
            }
            return;
        }
        TLRPC.User user = (TLRPC.User) object;

        TLRPC.TL_messages_getInlineBotResults req = new TLRPC.TL_messages_getInlineBotResults();
        req.query = query == null ? "" : query;
        req.bot = MessagesController.getInstance(currentAccount).getInputUser(user);
        req.offset = offset;

        if (chatActivity != null) {
            long dialogId = chatActivity.getDialogId();
            if (DialogObject.isEncryptedDialog(dialogId)) {
                req.peer = new TLRPC.TL_inputPeerEmpty();
            } else {
                req.peer = getMessagesController().getInputPeer(dialogId);
            }
        } else {
            req.peer = new TLRPC.TL_inputPeerEmpty();
        }

        final int token = ++lastSearchToken;
        imageReqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            addToRecentSearches(query);
            if (token != lastSearchToken) {
                return;
            }
            int addedCount = 0;
            int oldCount = searchResult.size();
            if (response != null) {

                TLRPC.messages_BotResults res = (TLRPC.messages_BotResults) response;
                nextImagesSearchOffset = res.next_offset;

                for (int a = 0, count = res.results.size(); a < count; a++) {
                    TLRPC.BotInlineResult result = res.results.get(a);
                    if (!gif && !"photo".equals(result.type) || gif && !"gif".equals(result.type)) {
                        continue;
                    }
                    if (searchResultKeys.containsKey(result.id)) {
                        continue;
                    }

                    MediaController.SearchImage image = new MediaController.SearchImage();

                    if (gif && result.document != null) {
                        for (int b = 0; b < result.document.attributes.size(); b++) {
                            TLRPC.DocumentAttribute attribute = result.document.attributes.get(b);
                            if (attribute instanceof TLRPC.TL_documentAttributeImageSize || attribute instanceof TLRPC.TL_documentAttributeVideo) {
                                image.width = attribute.w;
                                image.height = attribute.h;
                                break;
                            }
                        }
                        image.document = result.document;
                        image.size = 0;
                        if (result.photo != null) {
                            TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(result.photo.sizes, itemSize, true);
                            if (size != null) {
                                result.document.thumbs.add(size);
                                result.document.flags |= 1;
                            }
                        }
                    } else if (!gif && result.photo != null) {
                        TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(result.photo.sizes, AndroidUtilities.getPhotoSize());
                        TLRPC.PhotoSize size2 = FileLoader.getClosestPhotoSizeWithSize(result.photo.sizes, 320);
                        if (size == null) {
                            continue;
                        }
                        image.width = size.w;
                        image.height = size.h;
                        image.photoSize = size;
                        image.photo = result.photo;
                        image.size = size.size;
                        image.thumbPhotoSize = size2;
                    } else {
                        if (result.content == null) {
                            continue;
                        }
                        for (int b = 0; b < result.content.attributes.size(); b++) {
                            TLRPC.DocumentAttribute attribute = result.content.attributes.get(b);
                            if (attribute instanceof TLRPC.TL_documentAttributeImageSize) {
                                image.width = attribute.w;
                                image.height = attribute.h;
                                break;
                            }
                        }
                        if (result.thumb != null) {
                            image.thumbUrl = result.thumb.url;
                        } else {
                            image.thumbUrl = null;
                        }
                        image.imageUrl = result.content.url;
                        image.size = gif ? 0 : result.content.size;
                    }

                    image.id = result.id;
                    image.type = gif ? 1 : 0;
                    image.inlineResult = result;
                    image.params = new HashMap<>();
                    image.params.put("id", result.id);
                    image.params.put("query_id", "" + res.query_id);
                    image.params.put("bot_name", UserObject.getPublicUsername(user));

                    searchResult.add(image);

                    searchResultKeys.put(image.id, image);
                    addedCount++;
                }
                imageSearchEndReached = oldCount == searchResult.size() || nextImagesSearchOffset == null;
            }
            searching = false;
            if (addedCount != 0) {
                listAdapter.notifyItemRangeInserted(oldCount, addedCount);
            } else if (imageSearchEndReached) {
                listAdapter.notifyItemRemoved(searchResult.size() - 1);
            }
            if (searchResult.size() <= 0) {
                emptyView.showProgress(false);
            }
        }));
        ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(imageReqId, classGuid);
    }

    public void setDelegate(PhotoPickerActivityDelegate photoPickerActivityDelegate) {
        delegate = photoPickerActivityDelegate;
    }

    public void setSearchDelegate(PhotoPickerActivitySearchDelegate photoPickerActivitySearchDelegate) {
        searchDelegate = photoPickerActivitySearchDelegate;
    }

    private void sendSelectedPhotos(boolean notify, int scheduleDate, int scheduleRepeatPeriod) {
        if (selectedPhotos.isEmpty() || delegate == null || sendPressed) {
            return;
        }
        applyCaption();
        sendPressed = true;
        delegate.actionButtonPressed(false, notify, scheduleDate, scheduleRepeatPeriod);
        if (selectPhotoType != PhotoAlbumPickerActivity.SELECT_TYPE_WALLPAPER && (delegate == null || delegate.canFinishFragment())) {
            finishFragment();
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            if (selectedAlbum == null) {
                if (TextUtils.isEmpty(lastSearchString)) {
                    return holder.getItemViewType() == 3;
                } else {
                    return holder.getAdapterPosition() < searchResult.size();
                }
            }
            return true;
        }

        @Override
        public int getItemCount() {
            if (selectedAlbum == null) {
                if (searchResult.isEmpty()) {
                    if (TextUtils.isEmpty(lastSearchString)) {
                        return recentSearches.isEmpty() ? 0 : recentSearches.size() + 2;
                    } else {
                        return 0;
                    }
                } else {
                    return searchResult.size() + (imageSearchEndReached ? 0 : 1);
                }
            }
            return selectedAlbum.photos.size();
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    PhotoAttachPhotoCell cell = new PhotoAttachPhotoCell(mContext, null);
                    cell.setDelegate(new PhotoAttachPhotoCell.PhotoAttachPhotoCellDelegate() {

                        private void checkSlowMode() {
                            if (allowOrder && chatActivity != null) {
                                TLRPC.Chat chat = chatActivity.getCurrentChat();
                                if (chat != null && !ChatObject.hasAdminRights(chat) && chat.slowmode_enabled) {
                                    if (alertOnlyOnce != 2) {
                                        AlertsCreator.showSimpleAlert(PhotoPickerActivity.this, LocaleController.getString(R.string.Slowmode), LocaleController.getString(R.string.SlowmodeSelectSendError));
                                        if (alertOnlyOnce == 1) {
                                            alertOnlyOnce = 2;
                                        }
                                    }
                                }
                            }
                        }

                        @Override
                        public void onCheckClick(PhotoAttachPhotoCell v) {
                            int index = (Integer) v.getTag();
                            boolean added;
                            if (selectedAlbum != null) {
                                MediaController.PhotoEntry photoEntry = selectedAlbum.photos.get(index);
                                added = !selectedPhotos.containsKey(photoEntry.imageId);
                                if (added && maxSelectedPhotos > 0 && selectedPhotos.size() >= maxSelectedPhotos) {
                                    checkSlowMode();
                                    return;
                                }
                                int num = allowIndices && added ? selectedPhotosOrder.size() : -1;
                                v.setChecked(num, added, true);
                                addToSelectedPhotos(photoEntry, index);
                            } else {
                                AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
                                MediaController.SearchImage photoEntry = searchResult.get(index);
                                added = !selectedPhotos.containsKey(photoEntry.id);
                                if (added && maxSelectedPhotos > 0 && selectedPhotos.size() >= maxSelectedPhotos) {
                                    checkSlowMode();
                                    return;
                                }
                                int num = allowIndices && added ? selectedPhotosOrder.size() : -1;
                                v.setChecked(num, added, true);
                                addToSelectedPhotos(photoEntry, index);
                            }
                            updatePhotosButton(added ? 1 : 2);
                            delegate.selectedPhotosChanged();
                        }
                    });
                    cell.getCheckFrame().setVisibility(selectPhotoType != PhotoAlbumPickerActivity.SELECT_TYPE_ALL ? View.GONE : View.VISIBLE);
                    view = cell;
                    break;
                case 1:
                    FrameLayout frameLayout = new FrameLayout(mContext);
                    view = frameLayout;
                    view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    RadialProgressView progressBar = new RadialProgressView(mContext);
                    progressBar.setProgressColor(0xff527da3);
                    frameLayout.addView(progressBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                    break;
                case 2:
                    view = new SharedDocumentCell(mContext, SharedDocumentCell.VIEW_TYPE_PICKER);
                    break;
                case 3: {
                    view = new TextCell(mContext, 23, true);

                    view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    if (forceDarckTheme) {
                        TextCell textCell = (TextCell) view;
                        textCell.textView.setTextColor(Theme.getColor(textKey));
                        textCell.imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_voipgroup_mutedIcon), PorterDuff.Mode.MULTIPLY));
                    }
                    break;
                }
                case 4:
                default: {
                    view = new DividerCell(mContext);
                    ((DividerCell) view).setForceDarkTheme(forceDarckTheme);
                    break;
                }
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    PhotoAttachPhotoCell cell = (PhotoAttachPhotoCell) holder.itemView;
                    cell.setItemSize(itemSize);

                    BackupImageView imageView = cell.getImageView();
                    cell.setTag(position);
                    boolean showing;
                    imageView.setOrientation(0, true);

                    if (selectedAlbum != null) {
                        MediaController.PhotoEntry photoEntry = selectedAlbum.photos.get(position);
                        cell.setPhotoEntry(photoEntry, selectedPhotosOrder.size() > 1, true, false);
                        cell.setChecked(allowIndices ? selectedPhotosOrder.indexOf(photoEntry.imageId) : -1, selectedPhotos.containsKey(photoEntry.imageId), false);
                        showing = PhotoViewer.isShowingImage(photoEntry.path);
                    } else {
                        MediaController.SearchImage photoEntry = searchResult.get(position);
                        cell.setPhotoEntry(photoEntry, true, false);
                        cell.getVideoInfoContainer().setVisibility(View.INVISIBLE);
                        cell.setChecked(allowIndices ? selectedPhotosOrder.indexOf(photoEntry.id) : -1, selectedPhotos.containsKey(photoEntry.id), false);
                        showing = PhotoViewer.isShowingImage(photoEntry.getPathToAttach());
                    }
                    imageView.getImageReceiver().setVisible(!showing, true);
                    cell.getCheckBox().setVisibility(selectPhotoType != PhotoAlbumPickerActivity.SELECT_TYPE_ALL || showing ? View.GONE : View.VISIBLE);
                    break;
                }
                case 1: {
                    ViewGroup.LayoutParams params = holder.itemView.getLayoutParams();
                    if (params != null) {
                        params.width = LayoutHelper.MATCH_PARENT;
                        params.height = itemSize;
                        holder.itemView.setLayoutParams(params);
                    }
                    break;
                }
                case 2: {
                    MediaController.PhotoEntry photoEntry = selectedAlbum.photos.get(position);
                    SharedDocumentCell documentCell = (SharedDocumentCell) holder.itemView;
                    documentCell.setPhotoEntry(photoEntry);
                    documentCell.setChecked(selectedPhotos.containsKey(photoEntry.imageId), false);
                    documentCell.setTag(position);
                    break;
                }
                case 3: {
                    TextCell cell = (TextCell) holder.itemView;
                    if (position < recentSearches.size()) {
                        cell.setTextAndIcon(recentSearches.get(position), R.drawable.msg_recent, false);
                    } else {
                        cell.setTextAndIcon(LocaleController.getString(R.string.ClearRecentHistory), R.drawable.msg_clear_recent, false);
                    }
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (listSort) {
                return 2;
            }
            if (selectedAlbum != null) {
                return 0;
            } else if (searchResult.isEmpty()) {
                return position == recentSearches.size() ? 4 : 3;
            } else if (position < searchResult.size()) {
                return 0;
            }
            return 1;
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(sizeNotifierFrameLayout, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, dialogBackgroundKey));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, dialogBackgroundKey));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, textKey));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, textKey));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, selectorKey));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCH, null, null, null, null, textKey));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCHPLACEHOLDER, null, null, null, null, Theme.key_chat_messagePanelHint));
        themeDescriptions.add(new ThemeDescription(searchItem != null ? searchItem.getSearchField() : null, ThemeDescription.FLAG_CURSORCOLOR, null, null, null, null, textKey));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, dialogBackgroundKey));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, null, new Drawable[]{Theme.chat_attachEmptyDrawable}, null, Theme.key_chat_attachEmptyImage));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, null, null, null, Theme.key_chat_attachPhotoBackground));

        return themeDescriptions;
    }

    @Override
    public boolean isLightStatusBar() {
        return AndroidUtilities.computePerceivedBrightness(Theme.getColor(Theme.key_windowBackgroundGray)) > 0.721f;
    }
}
