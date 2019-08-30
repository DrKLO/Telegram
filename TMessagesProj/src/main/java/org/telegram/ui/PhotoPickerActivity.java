/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.MessageObject;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.PhotoPickerPhotoCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PickerBottomLayout;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.HashMap;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class PhotoPickerActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    public interface PhotoPickerActivityDelegate {
        void selectedPhotosChanged();
        void actionButtonPressed(boolean canceled);
    }

    private int type;
    private HashMap<Object, Object> selectedPhotos;
    private ArrayList<Object> selectedPhotosOrder;
    private boolean allowIndices;

    private ArrayList<MediaController.SearchImage> recentImages;
    private ArrayList<MediaController.SearchImage> searchResult = new ArrayList<>();
    private HashMap<String, MediaController.SearchImage> searchResultKeys = new HashMap<>();
    private HashMap<String, MediaController.SearchImage> searchResultUrls = new HashMap<>();

    private boolean searching;
    private boolean imageSearchEndReached = true;
    private String lastSearchString;
    private String nextImagesSearchOffset;
    private boolean loadingRecent;
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
    private PickerBottomLayout pickerBottomLayout;
    private EmptyTextProgressView emptyView;
    private View shadowView;
    private ActionBarMenuItem searchItem;
    private int itemWidth = 100;
    private boolean sendPressed;
    private int selectPhotoType;
    private ChatActivity chatActivity;

    private String initialSearchString;

    private boolean needsBottomLayout = true;

    private PhotoPickerActivityDelegate delegate;

    private PhotoViewer.PhotoViewerProvider provider = new PhotoViewer.EmptyPhotoViewerProvider() {
        @Override
        public boolean scaleToFill() {
            return false;
        }

        @Override
        public PhotoViewer.PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index, boolean needPreview) {
            PhotoPickerPhotoCell cell = getCellForIndex(index);
            if (cell != null) {
                int[] coords = new int[2];
                cell.imageView.getLocationInWindow(coords);
                PhotoViewer.PlaceProviderObject object = new PhotoViewer.PlaceProviderObject();
                object.viewX = coords[0];
                object.viewY = coords[1] - (Build.VERSION.SDK_INT >= 21 ? 0 : AndroidUtilities.statusBarHeight);
                object.parentView = listView;
                object.imageReceiver = cell.imageView.getImageReceiver();
                object.thumb = object.imageReceiver.getBitmapSafe();
                object.scale = cell.imageView.getScaleX();
                cell.showCheck(false);
                return object;
            }
            return null;
        }

        @Override
        public void updatePhotoAtIndex(int index) {
            PhotoPickerPhotoCell cell = getCellForIndex(index);
            if (cell != null) {
                if (selectedAlbum != null) {
                    cell.imageView.setOrientation(0, true);
                    MediaController.PhotoEntry photoEntry = selectedAlbum.photos.get(index);
                    if (photoEntry.thumbPath != null) {
                        cell.imageView.setImage(photoEntry.thumbPath, null, Theme.chat_attachEmptyDrawable);
                    } else if (photoEntry.path != null) {
                        cell.imageView.setOrientation(photoEntry.orientation, true);
                        if (photoEntry.isVideo) {
                            cell.imageView.setImage("vthumb://" + photoEntry.imageId + ":" + photoEntry.path, null, Theme.chat_attachEmptyDrawable);
                        } else {
                            cell.imageView.setImage("thumb://" + photoEntry.imageId + ":" + photoEntry.path, null, Theme.chat_attachEmptyDrawable);
                        }
                    } else {
                        cell.imageView.setImageDrawable(Theme.chat_attachEmptyDrawable);
                    }
                } else {
                    ArrayList<MediaController.SearchImage> array;
                    if (searchResult.isEmpty() && lastSearchString == null) {
                        array = recentImages;
                    } else {
                        array = searchResult;
                    }
                    cell.setImage(array.get(index));
                }
            }
        }

        @Override
        public boolean allowCaption() {
            return allowCaption;
        }

        @Override
        public ImageReceiver.BitmapHolder getThumbForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) {
            PhotoPickerPhotoCell cell = getCellForIndex(index);
            if (cell != null) {
                return cell.imageView.getImageReceiver().getBitmapSafe();
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
                PhotoPickerPhotoCell cell = (PhotoPickerPhotoCell) view;
                int num = (Integer) view.getTag();
                if (selectedAlbum != null) {
                    if (num < 0 || num >= selectedAlbum.photos.size()) {
                        continue;
                    }
                } else {
                    ArrayList<MediaController.SearchImage> array;
                    if (searchResult.isEmpty() && lastSearchString == null) {
                        array = recentImages;
                    } else {
                        array = searchResult;
                    }
                    if (num < 0 || num >= array.size()) {
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
                if (view instanceof PhotoPickerPhotoCell) {
                    PhotoPickerPhotoCell cell = (PhotoPickerPhotoCell) view;
                    cell.showCheck(true);
                }
            }
        }

        @Override
        public boolean isPhotoChecked(int index) {
            if (selectedAlbum != null) {
                return !(index < 0 || index >= selectedAlbum.photos.size()) && selectedPhotos.containsKey(selectedAlbum.photos.get(index).imageId);
            } else {
                ArrayList<MediaController.SearchImage> array;
                if (searchResult.isEmpty() && lastSearchString == null) {
                    array = recentImages;
                } else {
                    array = searchResult;
                }
                return !(index < 0 || index >= array.size()) && selectedPhotos.containsKey(array.get(index).id);
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
                ArrayList<MediaController.SearchImage> array;
                if (searchResult.isEmpty() && lastSearchString == null) {
                    array = recentImages;
                } else {
                    array = searchResult;
                }
                if (index < 0 || index >= array.size()) {
                    return -1;
                }
                MediaController.SearchImage photoEntry = array.get(index);
                if ((num = addToSelectedPhotos(photoEntry, -1)) == -1) {
                    num = selectedPhotosOrder.indexOf(photoEntry.id);
                } else {
                    add = false;
                }
            }
            int count = listView.getChildCount();
            for (int a = 0; a < count; a++) {
                View view = listView.getChildAt(a);
                int tag = (Integer) view.getTag();
                if (tag == index) {
                    ((PhotoPickerPhotoCell) view).setChecked(allowIndices ? num : -1, add, false);
                    break;
                }
            }
            pickerBottomLayout.updateSelectedCount(selectedPhotos.size(), true);
            delegate.selectedPhotosChanged();
            return num;
        }

        @Override
        public boolean cancelButtonPressed() {
            delegate.actionButtonPressed(true);
            finishFragment();
            return true;
        }

        @Override
        public int getSelectedCount() {
            return selectedPhotos.size();
        }

        @Override
        public void sendButtonPressed(int index, VideoEditedInfo videoEditedInfo) {
            if (selectedPhotos.isEmpty()) {
                if (selectedAlbum != null) {
                    if (index < 0 || index >= selectedAlbum.photos.size()) {
                        return;
                    }
                    MediaController.PhotoEntry photoEntry = selectedAlbum.photos.get(index);
                    photoEntry.editedInfo = videoEditedInfo;
                    addToSelectedPhotos(photoEntry, -1);
                } else {
                    ArrayList<MediaController.SearchImage> array;
                    if (searchResult.isEmpty() && lastSearchString == null) {
                        array = recentImages;
                    } else {
                        array = searchResult;
                    }
                    if (index < 0 || index >= array.size()) {
                        return;
                    }
                    addToSelectedPhotos(array.get(index), -1);
                }
            }
            sendSelectedPhotos();
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

    public PhotoPickerActivity(int type, MediaController.AlbumEntry selectedAlbum, HashMap<Object, Object> selectedPhotos, ArrayList<Object> selectedPhotosOrder, ArrayList<MediaController.SearchImage> recentImages, int selectPhotoType, boolean allowCaption, ChatActivity chatActivity) {
        super();
        this.selectedAlbum = selectedAlbum;
        this.selectedPhotos = selectedPhotos;
        this.selectedPhotosOrder = selectedPhotosOrder;
        this.type = type;
        this.recentImages = recentImages;
        this.selectPhotoType = selectPhotoType;
        this.chatActivity = chatActivity;
        this.allowCaption = allowCaption;
    }

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.closeChats);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.recentImagesDidLoad);
        if (selectedAlbum == null) {
            if (recentImages.isEmpty()) {
                MessagesStorage.getInstance(currentAccount).loadWebRecent(type);
                loadingRecent = true;
            }
        }
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.closeChats);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.recentImagesDidLoad);
        if (imageReqId != 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(imageReqId, true);
            imageReqId = 0;
        }
        super.onFragmentDestroy();
    }

    @SuppressWarnings("unchecked")
    @Override
    public View createView(Context context) {
        actionBar.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));
        actionBar.setTitleColor(Theme.getColor(Theme.key_dialogTextBlack));
        actionBar.setItemsColor(Theme.getColor(Theme.key_dialogTextBlack), false);
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_dialogButtonSelector), false);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        if (selectedAlbum != null) {
            actionBar.setTitle(selectedAlbum.bucketName);
        } else if (type == 0) {
            actionBar.setTitle(LocaleController.getString("SearchImagesTitle", R.string.SearchImagesTitle));
        } else if (type == 1) {
            actionBar.setTitle(LocaleController.getString("SearchGifsTitle", R.string.SearchGifsTitle));
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

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
                        emptyView.setText("");
                        /*if (type == 0) {
                            emptyView.setText(LocaleController.getString("NoRecentPhotos", R.string.NoRecentPhotos));
                        } else if (type == 1) {
                            emptyView.setText(LocaleController.getString("NoRecentGIFs", R.string.NoRecentGIFs));
                        }*/
                        updateSearchInterface();
                    }
                }

                @Override
                public void onSearchPressed(EditText editText) {
                    processSearch(editText);
                }
            });
            EditTextBoldCursor editText = searchItem.getSearchField();
            editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            editText.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack));
            editText.setHintTextColor(Theme.getColor(Theme.key_chat_messagePanelHint));
        }

        if (selectedAlbum == null) {
            if (type == 0) {
                searchItem.setSearchFieldHint(LocaleController.getString("SearchImagesTitle", R.string.SearchImagesTitle));
            } else if (type == 1) {
                searchItem.setSearchFieldHint(LocaleController.getString("SearchGifsTitle", R.string.SearchGifsTitle));
            }
        }

        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));
        fragmentView = frameLayout;

        listView = new RecyclerListView(context);
        listView.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4));
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
        listView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                super.getItemOffsets(outRect, view, parent, state);
                int total = state.getItemCount();
                int position = parent.getChildAdapterPosition(view);
                int spanCount = layoutManager.getSpanCount();
                int rowsCOunt = (int) Math.ceil(total / (float) spanCount);
                int row = position / spanCount;
                int col = position % spanCount;
                outRect.right = col != spanCount - 1 ? AndroidUtilities.dp(4) : 0;
                outRect.bottom = row != rowsCOunt - 1 ? AndroidUtilities.dp(4) : 0;
            }
        });
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 0, 0, 0, selectPhotoType != 0 ? 0 : 48));
        listView.setAdapter(listAdapter = new ListAdapter(context));
        listView.setGlowColor(Theme.getColor(Theme.key_dialogBackground));
        listView.setOnItemClickListener((view, position) -> {
            ArrayList<Object> arrayList;
            if (selectedAlbum != null) {
                arrayList = (ArrayList) selectedAlbum.photos;
            } else {
                if (searchResult.isEmpty() && lastSearchString == null) {
                    arrayList = (ArrayList) recentImages;
                } else {
                    arrayList = (ArrayList) searchResult;
                }
            }
            if (position < 0 || position >= arrayList.size()) {
                return;
            }
            if (searchItem != null) {
                AndroidUtilities.hideKeyboard(searchItem.getSearchField());
            }
            int type;
            if (selectPhotoType == 1) {
                type = PhotoViewer.SELECT_TYPE_AVATAR;
            } else if (selectPhotoType == 2) {
                type = PhotoViewer.SELECT_TYPE_WALLPAPER;
            } else if (chatActivity == null) {
                type = 4;
            } else {
                type = 0;
            }
            PhotoViewer.getInstance().setParentActivity(getParentActivity());
            PhotoViewer.getInstance().setMaxSelectedPhotos(maxSelectedPhotos, allowOrder);
            PhotoViewer.getInstance().openPhotoForSelect(arrayList, position, type, provider, chatActivity);
        });

        if (selectedAlbum == null) {
            listView.setOnItemLongClickListener((view, position) -> {
                if (searchResult.isEmpty() && lastSearchString == null) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                    builder.setMessage(LocaleController.getString("ClearSearch", R.string.ClearSearch));
                    builder.setPositiveButton(LocaleController.getString("ClearButton", R.string.ClearButton).toUpperCase(), (dialogInterface, i) -> {
                        recentImages.clear();
                        if (listAdapter != null) {
                            listAdapter.notifyDataSetChanged();
                        }
                        MessagesStorage.getInstance(currentAccount).clearWebRecent(type);
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    showDialog(builder.create());
                    return true;
                }
                return false;
            });
        }

        emptyView = new EmptyTextProgressView(context);
        emptyView.setTextColor(0xff808080);
        emptyView.setProgressBarColor(0xff527da3);
        emptyView.setShowAtCenter(false);
        if (selectedAlbum != null) {
            emptyView.setText(LocaleController.getString("NoPhotos", R.string.NoPhotos));
        } else {
            emptyView.setText("");
            /*if (type == 0) {
                emptyView.setText(LocaleController.getString("NoRecentPhotos", R.string.NoRecentPhotos));
            } else if (type == 1) {
                emptyView.setText(LocaleController.getString("NoRecentGIFs", R.string.NoRecentGIFs));
            }*/
        }
        frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 0, 0, 0, selectPhotoType != 0 ? 0 : 48));

        if (selectedAlbum == null) {
            listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                        AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
                    }
                }

                @Override
                public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                    int firstVisibleItem = layoutManager.findFirstVisibleItemPosition();
                    int visibleItemCount = firstVisibleItem == RecyclerView.NO_POSITION ? 0 : Math.abs(layoutManager.findLastVisibleItemPosition() - firstVisibleItem) + 1;
                    if (visibleItemCount > 0) {
                        int totalItemCount = layoutManager.getItemCount();
                        if (visibleItemCount != 0 && firstVisibleItem + visibleItemCount > totalItemCount - 2 && !searching) {
                            if (!imageSearchEndReached) {
                                searchImages(type == 1, lastSearchString, nextImagesSearchOffset, true);
                            }
                        }
                    }
                }
            });

            updateSearchInterface();
        }

        if (needsBottomLayout) {
            shadowView = new View(context);
            shadowView.setBackgroundColor(Theme.getColor(Theme.key_dialogShadowLine));
            frameLayout.addView(shadowView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1, Gravity.LEFT | Gravity.BOTTOM, 0, 0, 0, 48));

            pickerBottomLayout = new PickerBottomLayout(context);
            frameLayout.addView(pickerBottomLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM));
            pickerBottomLayout.cancelButton.setOnClickListener(view -> {
                delegate.actionButtonPressed(true);
                finishFragment();
            });
            pickerBottomLayout.doneButton.setOnClickListener(view -> sendSelectedPhotos());
            if (selectPhotoType != 0) {
                pickerBottomLayout.setVisibility(View.GONE);
                shadowView.setVisibility(View.GONE);
            }
        }
        allowIndices = (selectedAlbum != null || type == 0) && allowOrder;

        listView.setEmptyView(emptyView);
        pickerBottomLayout.updateSelectedCount(selectedPhotos.size(), true);

        return fragmentView;
    }

    public void setPickerBottomLayout(PickerBottomLayout bottomLayout) {
        pickerBottomLayout = bottomLayout;
        needsBottomLayout = false;
    }

    public PickerBottomLayout getPickerBottomLayout() {
        return pickerBottomLayout;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
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
        fixLayout();
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        fixLayout();
    }

    @SuppressWarnings("unchecked")
    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.closeChats) {
            removeSelfFromStack();
        } else if (id == NotificationCenter.recentImagesDidLoad) {
            if (selectedAlbum == null && type == (Integer) args[0]) {
                recentImages = (ArrayList<MediaController.SearchImage>) args[1];
                loadingRecent = false;
                updateSearchInterface();
            }
        }
    }

    public RecyclerListView getListView() {
        return listView;
    }

    public void setInitialSearchString(String text) {
        initialSearchString = text;
    }

    private void processSearch(EditText editText) {
        if (editText.getText().toString().length() == 0) {
            return;
        }
        searchResult.clear();
        searchResultKeys.clear();
        imageSearchEndReached = true;
        searchImages(type == 1, editText.getText().toString(), "", true);
        lastSearchString = editText.getText().toString();
        if (lastSearchString.length() == 0) {
            lastSearchString = null;
            emptyView.setText("");
            /*if (type == 0) {
                emptyView.setText(LocaleController.getString("NoRecentPhotos", R.string.NoRecentPhotos));
            } else if (type == 1) {
                emptyView.setText(LocaleController.getString("NoRecentGIFs", R.string.NoRecentGIFs));
            }*/
        } else {
            emptyView.setText(LocaleController.getString("NoResult", R.string.NoResult));
        }
        updateSearchInterface();
    }

    public void setMaxSelectedPhotos(int value, boolean order) {
        maxSelectedPhotos = value;
        allowOrder = order;
        if (value >= 0 && type == 1) {
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
            if (view instanceof PhotoPickerPhotoCell) {
                PhotoPickerPhotoCell cell = (PhotoPickerPhotoCell) view;
                Integer index = (Integer) cell.getTag();
                if (selectedAlbum != null) {
                    MediaController.PhotoEntry photoEntry = selectedAlbum.photos.get(index);
                    cell.setNum(allowIndices ? selectedPhotosOrder.indexOf(photoEntry.imageId) : -1);
                } else {
                    MediaController.SearchImage photoEntry;
                    if (searchResult.isEmpty() && lastSearchString == null) {
                        photoEntry = recentImages.get(index);
                    } else {
                        photoEntry = searchResult.get(index);
                    }
                    cell.setNum(allowIndices ? selectedPhotosOrder.indexOf(photoEntry.id) : -1);
                }
            }
        }
    }

    private PhotoPickerPhotoCell getCellForIndex(int index) {
        int count = listView.getChildCount();

        for (int a = 0; a < count; a++) {
            View view = listView.getChildAt(a);
            if (view instanceof PhotoPickerPhotoCell) {
                PhotoPickerPhotoCell cell = (PhotoPickerPhotoCell) view;
                int num = (Integer) cell.imageView.getTag();
                if (selectedAlbum != null) {
                    if (num < 0 || num >= selectedAlbum.photos.size()) {
                        continue;
                    }
                } else {
                    ArrayList<MediaController.SearchImage> array;
                    if (searchResult.isEmpty() && lastSearchString == null) {
                        array = recentImages;
                    } else {
                        array = searchResult;
                    }
                    if (num < 0 || num >= array.size()) {
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

    private void updateSearchInterface() {
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
        if (searching && searchResult.isEmpty() || loadingRecent && lastSearchString == null) {
            emptyView.showProgress();
        } else {
            emptyView.showTextView();
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
            int lower_id = (int) dialogId;
            int high_id = (int) (dialogId >> 32);
            if (lower_id != 0) {
                req.peer = MessagesController.getInstance(currentAccount).getInputPeer(lower_id);
            } else {
                req.peer = new TLRPC.TL_inputPeerEmpty();
            }
        } else {
            req.peer = new TLRPC.TL_inputPeerEmpty();
        }

        final int token = ++lastSearchToken;
        imageReqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
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
                        if (result.photo != null && result.document != null) {
                            TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(result.photo.sizes, itemWidth, true);
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
                    image.localUrl = "";

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
            if (searching && searchResult.isEmpty() || loadingRecent && lastSearchString == null) {
                emptyView.showProgress();
            } else {
                emptyView.showTextView();
            }
        }));
        ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(imageReqId, classGuid);
    }

    public void setDelegate(PhotoPickerActivityDelegate delegate) {
        this.delegate = delegate;
    }

    private void sendSelectedPhotos() {
        if (selectedPhotos.isEmpty() || delegate == null || sendPressed) {
            return;
        }
        sendPressed = true;
        delegate.actionButtonPressed(false);
        if (selectPhotoType != 2) {
            finishFragment();
        }
    }

    private void fixLayout() {
        if (listView != null) {
            ViewTreeObserver obs = listView.getViewTreeObserver();
            obs.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    fixLayoutInternal();
                    if (listView != null) {
                        listView.getViewTreeObserver().removeOnPreDrawListener(this);
                    }
                    return true;
                }
            });
        }
    }

    private void fixLayoutInternal() {
        if (getParentActivity() == null) {
            return;
        }
        int position = layoutManager.findFirstVisibleItemPosition();
        WindowManager manager = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Activity.WINDOW_SERVICE);
        int rotation = manager.getDefaultDisplay().getRotation();

        int columnsCount;
        if (AndroidUtilities.isTablet()) {
            columnsCount = 3;
        } else {
            if (rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90) {
                columnsCount = 5;
            } else {
                columnsCount = 3;
            }
        }
        layoutManager.setSpanCount(columnsCount);
        if (AndroidUtilities.isTablet()) {
            itemWidth = (AndroidUtilities.dp(490) - ((columnsCount + 1) * AndroidUtilities.dp(4))) / columnsCount;
        } else {
            itemWidth = (AndroidUtilities.displaySize.x - ((columnsCount + 1) * AndroidUtilities.dp(4))) / columnsCount;
        }

        listAdapter.notifyDataSetChanged();
        layoutManager.scrollToPosition(position);
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            if (selectedAlbum == null) {
                int position = holder.getAdapterPosition();
                if (searchResult.isEmpty() && lastSearchString == null) {
                    return position < recentImages.size();
                } else {
                    return position < searchResult.size();
                }
            }
            return true;
        }

        @Override
        public int getItemCount() {
            if (selectedAlbum == null) {
                if (searchResult.isEmpty()) {
                    return 0;
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
                    PhotoPickerPhotoCell cell = new PhotoPickerPhotoCell(mContext, true);
                    cell.checkFrame.setOnClickListener(v -> {
                        int index = (Integer) ((View) v.getParent()).getTag();
                        if (selectedAlbum != null) {
                            MediaController.PhotoEntry photoEntry = selectedAlbum.photos.get(index);
                            boolean added = !selectedPhotos.containsKey(photoEntry.imageId);
                            if (added && maxSelectedPhotos > 0 && selectedPhotos.size() >= maxSelectedPhotos) {
                                if (allowOrder && chatActivity != null) {
                                    TLRPC.Chat chat = chatActivity.getCurrentChat();
                                    if (chat != null && !ChatObject.hasAdminRights(chat) && chat.slowmode_enabled) {
                                        AlertsCreator.showSimpleAlert(PhotoPickerActivity.this, LocaleController.getString("Slowmode", R.string.Slowmode), LocaleController.getString("SlowmodeSelectSendError", R.string.SlowmodeSelectSendError));
                                    }
                                }
                                return;
                            }
                            int num = allowIndices && added ? selectedPhotosOrder.size() : -1;
                            ((PhotoPickerPhotoCell) v.getParent()).setChecked(num, added, true);
                            addToSelectedPhotos(photoEntry, index);
                        } else {
                            AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
                            MediaController.SearchImage photoEntry;
                            if (searchResult.isEmpty() && lastSearchString == null) {
                                photoEntry = recentImages.get((Integer) ((View) v.getParent()).getTag());
                            } else {
                                photoEntry = searchResult.get((Integer) ((View) v.getParent()).getTag());
                            }
                            boolean added = !selectedPhotos.containsKey(photoEntry.id);
                            if (added && maxSelectedPhotos > 0 && selectedPhotos.size() >= maxSelectedPhotos) {
                                if (allowOrder && chatActivity != null) {
                                    TLRPC.Chat chat = chatActivity.getCurrentChat();
                                    if (chat != null && !ChatObject.hasAdminRights(chat) && chat.slowmode_enabled) {
                                        AlertsCreator.showSimpleAlert(PhotoPickerActivity.this, LocaleController.getString("Slowmode", R.string.Slowmode), LocaleController.getString("SlowmodeSelectSendError", R.string.SlowmodeSelectSendError));
                                    }
                                }
                                return;
                            }
                            int num = allowIndices && added ? selectedPhotosOrder.size() : -1;
                            ((PhotoPickerPhotoCell) v.getParent()).setChecked(num, added, true);
                            addToSelectedPhotos(photoEntry, index);
                        }
                        pickerBottomLayout.updateSelectedCount(selectedPhotos.size(), true);
                        delegate.selectedPhotosChanged();
                    });
                    cell.checkFrame.setVisibility(selectPhotoType != 0 ? View.GONE : View.VISIBLE);
                    view = cell;
                    break;
                case 1:
                default:
                    FrameLayout frameLayout = new FrameLayout(mContext);
                    view = frameLayout;
                    RadialProgressView progressBar = new RadialProgressView(mContext);
                    progressBar.setProgressColor(0xff527da3);
                    frameLayout.addView(progressBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0:
                    PhotoPickerPhotoCell cell = (PhotoPickerPhotoCell) holder.itemView;
                    cell.itemWidth = itemWidth;
                    BackupImageView imageView = cell.imageView;
                    imageView.setTag(position);
                    cell.setTag(position);
                    boolean showing;
                    imageView.setOrientation(0, true);

                    if (selectedAlbum != null) {
                        MediaController.PhotoEntry photoEntry = selectedAlbum.photos.get(position);
                        cell.setImage(photoEntry);
                        cell.setChecked(allowIndices ? selectedPhotosOrder.indexOf(photoEntry.imageId) : -1, selectedPhotos.containsKey(photoEntry.imageId), false);
                        showing = PhotoViewer.isShowingImage(photoEntry.path);
                    } else {
                        MediaController.SearchImage photoEntry;
                        if (searchResult.isEmpty() && lastSearchString == null) {
                            photoEntry = recentImages.get(position);
                        } else {
                            photoEntry = searchResult.get(position);
                        }
                        cell.setImage(photoEntry);
                        cell.videoInfoContainer.setVisibility(View.INVISIBLE);
                        cell.setChecked(allowIndices ? selectedPhotosOrder.indexOf(photoEntry.id) : -1, selectedPhotos.containsKey(photoEntry.id), false);
                        showing = PhotoViewer.isShowingImage(photoEntry.getPathToAttach());
                    }
                    imageView.getImageReceiver().setVisible(!showing, true);
                    cell.checkBox.setVisibility(selectPhotoType != 0 || showing ? View.GONE : View.VISIBLE);
                    break;
                case 1:
                    ViewGroup.LayoutParams params = holder.itemView.getLayoutParams();
                    if (params != null) {
                        params.width = itemWidth;
                        params.height = itemWidth;
                        holder.itemView.setLayoutParams(params);
                    }
                    break;
            }
        }

        @Override
        public int getItemViewType(int i) {
            if (selectedAlbum != null || searchResult.isEmpty() && lastSearchString == null && i < recentImages.size() || i < searchResult.size()) {
                return 0;
            }
            return 1;
        }
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        return new ThemeDescription[]{
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_dialogBackground),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_dialogBackground),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_dialogTextBlack),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_dialogTextBlack),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_dialogButtonSelector),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCH, null, null, null, null, Theme.key_dialogTextBlack),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCHPLACEHOLDER, null, null, null, null, Theme.key_chat_messagePanelHint),
                new ThemeDescription(searchItem != null ? searchItem.getSearchField() : null, ThemeDescription.FLAG_CURSORCOLOR, null, null, null, null, Theme.key_dialogTextBlack),

                new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_dialogBackground),

                new ThemeDescription(shadowView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_dialogShadowLine),

                new ThemeDescription(pickerBottomLayout, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_dialogBackground),
                new ThemeDescription(pickerBottomLayout, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{PickerBottomLayout.class}, new String[]{"cancelButton"}, null, null, null, Theme.key_picker_enabledButton),
                new ThemeDescription(pickerBottomLayout, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{PickerBottomLayout.class}, new String[]{"doneButtonTextView"}, null, null, null, Theme.key_picker_enabledButton),
                new ThemeDescription(pickerBottomLayout, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{PickerBottomLayout.class}, new String[]{"doneButtonTextView"}, null, null, null, Theme.key_picker_disabledButton),
                new ThemeDescription(pickerBottomLayout, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{PickerBottomLayout.class}, new String[]{"doneButtonBadgeTextView"}, null, null, null, Theme.key_picker_badgeText),
                new ThemeDescription(pickerBottomLayout, ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE, new Class[]{PickerBottomLayout.class}, new String[]{"doneButtonBadgeTextView"}, null, null, null, Theme.key_picker_badge),

                new ThemeDescription(listView, 0, new Class[]{View.class}, null, new Drawable[]{Theme.chat_attachEmptyDrawable}, null, Theme.key_chat_attachEmptyImage),
                new ThemeDescription(listView, 0, new Class[]{View.class}, null, null, null, Theme.key_chat_attachPhotoBackground),
        };
    }
}
