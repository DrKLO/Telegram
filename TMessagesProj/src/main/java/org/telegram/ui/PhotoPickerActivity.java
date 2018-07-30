/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.messenger.support.widget.GridLayoutManager;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.MessageObject;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Cells.PhotoPickerPhotoCell;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PickerBottomLayout;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.HashMap;

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

    private TextView hintTextView;
    private Runnable hintHideRunnable;
    private AnimatorSet hintAnimation;

    private boolean searching;
    private boolean bingSearchEndReached = true;
    private boolean giphySearchEndReached = true;
    private String lastSearchString;
    private String nextImagesSearchOffset;
    private boolean loadingRecent;
    private int nextGiphySearchOffset;
    private int giphyReqId;
    private int imageReqId;
    private int lastSearchToken;
    private boolean allowCaption;
    private boolean searchingUser;
    private String lastSearchImageString;

    private int maxSelectedPhotos = 100;

    private MediaController.AlbumEntry selectedAlbum;

    private RecyclerListView listView;
    private ListAdapter listAdapter;
    private GridLayoutManager layoutManager;
    private PickerBottomLayout pickerBottomLayout;
    private ImageView imageOrderToggleButton;
    private EmptyTextProgressView emptyView;
    private ActionBarMenuItem searchItem;
    private FrameLayout frameLayout;
    private int itemWidth = 100;
    private boolean sendPressed;
    private boolean singlePhoto;
    private ChatActivity chatActivity;

    private PhotoPickerActivityDelegate delegate;

    private PhotoViewer.PhotoViewerProvider provider = new PhotoViewer.EmptyPhotoViewerProvider() {
        @Override
        public boolean scaleToFill() {
            return false;
        }

        @Override
        public PhotoViewer.PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) {
            PhotoPickerPhotoCell cell = getCellForIndex(index);
            if (cell != null) {
                int coords[] = new int[2];
                cell.photoImage.getLocationInWindow(coords);
                PhotoViewer.PlaceProviderObject object = new PhotoViewer.PlaceProviderObject();
                object.viewX = coords[0];
                object.viewY = coords[1] - (Build.VERSION.SDK_INT >= 21 ? 0 : AndroidUtilities.statusBarHeight);
                object.parentView = listView;
                object.imageReceiver = cell.photoImage.getImageReceiver();
                object.thumb = object.imageReceiver.getBitmapSafe();
                object.scale = cell.photoImage.getScaleX();
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
                    cell.photoImage.setOrientation(0, true);
                    MediaController.PhotoEntry photoEntry = selectedAlbum.photos.get(index);
                    if (photoEntry.thumbPath != null) {
                        cell.photoImage.setImage(photoEntry.thumbPath, null, cell.getContext().getResources().getDrawable(R.drawable.nophotos));
                    } else if (photoEntry.path != null) {
                        cell.photoImage.setOrientation(photoEntry.orientation, true);
                        if (photoEntry.isVideo) {
                            cell.photoImage.setImage("vthumb://" + photoEntry.imageId + ":" + photoEntry.path, null, cell.getContext().getResources().getDrawable(R.drawable.nophotos));
                        } else {
                            cell.photoImage.setImage("thumb://" + photoEntry.imageId + ":" + photoEntry.path, null, cell.getContext().getResources().getDrawable(R.drawable.nophotos));
                        }
                    } else {
                        cell.photoImage.setImageResource(R.drawable.nophotos);
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
                return cell.photoImage.getImageReceiver().getBitmapSafe();
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
        public void toggleGroupPhotosEnabled() {
            if (imageOrderToggleButton != null) {
                imageOrderToggleButton.setColorFilter(SharedConfig.groupPhotosEnabled ? new PorterDuffColorFilter(0xff66bffa, PorterDuff.Mode.MULTIPLY) : null);
            }
        }

        @Override
        public ArrayList<Object> getSelectedPhotosOrder() {
            return selectedPhotosOrder;
        }

        @Override
        public HashMap<Object, Object> getSelectedPhotos() {
            return selectedPhotos;
        }

        @Override
        public boolean allowGroupPhotos() {
            return imageOrderToggleButton != null;
        }
    };

    public PhotoPickerActivity(int type, MediaController.AlbumEntry selectedAlbum, HashMap<Object, Object> selectedPhotos, ArrayList<Object> selectedPhotosOrder, ArrayList<MediaController.SearchImage> recentImages, boolean onlyOnePhoto, boolean allowCaption, ChatActivity chatActivity) {
        super();
        this.selectedAlbum = selectedAlbum;
        this.selectedPhotos = selectedPhotos;
        this.selectedPhotosOrder = selectedPhotosOrder;
        this.type = type;
        this.recentImages = recentImages;
        this.singlePhoto = onlyOnePhoto;
        this.chatActivity = chatActivity;
        this.allowCaption = allowCaption;
    }

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.closeChats);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.recentImagesDidLoaded);
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
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.recentImagesDidLoaded);
        if (giphyReqId != 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(giphyReqId, true);
            giphyReqId = 0;
        }
        if (imageReqId != 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(imageReqId, true);
            imageReqId = 0;
        }
        super.onFragmentDestroy();
    }

    @SuppressWarnings("unchecked")
    @Override
    public View createView(Context context) {
        actionBar.setBackgroundColor(Theme.ACTION_BAR_MEDIA_PICKER_COLOR);
        actionBar.setItemsBackgroundColor(Theme.ACTION_BAR_PICKER_SELECTOR_COLOR, false);
        actionBar.setTitleColor(0xffffffff);
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
                        bingSearchEndReached = true;
                        giphySearchEndReached = true;
                        searching = false;
                        if (imageReqId != 0) {
                            ConnectionsManager.getInstance(currentAccount).cancelRequest(imageReqId, true);
                            imageReqId = 0;
                        }
                        if (giphyReqId != 0) {
                            ConnectionsManager.getInstance(currentAccount).cancelRequest(giphyReqId, true);
                            giphyReqId = 0;
                        }
                        if (type == 0) {
                            emptyView.setText(LocaleController.getString("NoRecentPhotos", R.string.NoRecentPhotos));
                        } else if (type == 1) {
                            emptyView.setText(LocaleController.getString("NoRecentGIFs", R.string.NoRecentGIFs));
                        }
                        updateSearchInterface();
                    }
                }

                @Override
                public void onSearchPressed(EditText editText) {
                    if (editText.getText().toString().length() == 0) {
                        return;
                    }
                    searchResult.clear();
                    searchResultKeys.clear();
                    bingSearchEndReached = true;
                    giphySearchEndReached = true;
                    if (type == 0) {
                        searchImages(editText.getText().toString(), "", true);
                    } else if (type == 1) {
                        nextGiphySearchOffset = 0;
                        searchGiphyImages(editText.getText().toString(), 0);
                    }
                    lastSearchString = editText.getText().toString();
                    if (lastSearchString.length() == 0) {
                        lastSearchString = null;
                        if (type == 0) {
                            emptyView.setText(LocaleController.getString("NoRecentPhotos", R.string.NoRecentPhotos));
                        } else if (type == 1) {
                            emptyView.setText(LocaleController.getString("NoRecentGIFs", R.string.NoRecentGIFs));
                        }
                    } else {
                        emptyView.setText(LocaleController.getString("NoResult", R.string.NoResult));
                    }
                    updateSearchInterface();
                }
            });
        }

        if (selectedAlbum == null) {
            if (type == 0) {
                searchItem.getSearchField().setHint(LocaleController.getString("SearchImagesTitle", R.string.SearchImagesTitle));
            } else if (type == 1) {
                searchItem.getSearchField().setHint(LocaleController.getString("SearchGifsTitle", R.string.SearchGifsTitle));
            }
        }

        fragmentView = new FrameLayout(context);

        frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(0xff000000);

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
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 0, 0, 0, singlePhoto ? 0 : 48));
        listView.setAdapter(listAdapter = new ListAdapter(context));
        listView.setGlowColor(0xff333333);
        listView.setOnItemClickListener(new RecyclerListView.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
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
                if (singlePhoto) {
                    type = 1;
                } else if (chatActivity == null) {
                    type = 4;
                } else {
                    type = 0;
                }
                PhotoViewer.getInstance().setParentActivity(getParentActivity());
                PhotoViewer.getInstance().setMaxSelectedPhotos(maxSelectedPhotos);
                PhotoViewer.getInstance().openPhotoForSelect(arrayList, position, type, provider, chatActivity);
            }
        });

        if (selectedAlbum == null) {
            listView.setOnItemLongClickListener(new RecyclerListView.OnItemLongClickListener() {
                @Override
                public boolean onItemClick(View view, int position) {
                    if (searchResult.isEmpty() && lastSearchString == null) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                        builder.setMessage(LocaleController.getString("ClearSearch", R.string.ClearSearch));
                        builder.setPositiveButton(LocaleController.getString("ClearButton", R.string.ClearButton).toUpperCase(), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                recentImages.clear();
                                if (listAdapter != null) {
                                    listAdapter.notifyDataSetChanged();
                                }
                                MessagesStorage.getInstance(currentAccount).clearWebRecent(type);
                            }
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        showDialog(builder.create());
                        return true;
                    }
                    return false;
                }
            });
        }

        emptyView = new EmptyTextProgressView(context);
        emptyView.setTextColor(0xff808080);
        emptyView.setProgressBarColor(0xffffffff);
        emptyView.setShowAtCenter(true);
        if (selectedAlbum != null) {
            emptyView.setText(LocaleController.getString("NoPhotos", R.string.NoPhotos));
        } else {
            if (type == 0) {
                emptyView.setText(LocaleController.getString("NoRecentPhotos", R.string.NoRecentPhotos));
            } else if (type == 1) {
                emptyView.setText(LocaleController.getString("NoRecentGIFs", R.string.NoRecentGIFs));
            }
        }
        frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 0, 0, 0, singlePhoto ? 0 : 48));

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
                            if (type == 0 && !bingSearchEndReached) {
                                searchImages(lastSearchString, nextImagesSearchOffset, true);
                            } else if (type == 1 && !giphySearchEndReached) {
                                searchGiphyImages(searchItem.getSearchField().getText().toString(), nextGiphySearchOffset);
                            }
                        }
                    }
                }
            });

            updateSearchInterface();
        }

        pickerBottomLayout = new PickerBottomLayout(context);
        frameLayout.addView(pickerBottomLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM));
        pickerBottomLayout.cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                delegate.actionButtonPressed(true);
                finishFragment();
            }
        });
        pickerBottomLayout.doneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendSelectedPhotos();
            }
        });
        if (singlePhoto) {
            pickerBottomLayout.setVisibility(View.GONE);
        } else if ((selectedAlbum != null || type == 0) && chatActivity != null && chatActivity.allowGroupPhotos()) {
            imageOrderToggleButton = new ImageView(context);
            imageOrderToggleButton.setScaleType(ImageView.ScaleType.CENTER);
            imageOrderToggleButton.setImageResource(R.drawable.photos_group);
            pickerBottomLayout.addView(imageOrderToggleButton, LayoutHelper.createFrame(48, LayoutHelper.MATCH_PARENT, Gravity.CENTER));
            imageOrderToggleButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    SharedConfig.toggleGroupPhotosEnabled();
                    imageOrderToggleButton.setColorFilter(SharedConfig.groupPhotosEnabled ? new PorterDuffColorFilter(0xff66bffa, PorterDuff.Mode.MULTIPLY) : null);
                    showHint(false, SharedConfig.groupPhotosEnabled);
                    updateCheckedPhotoIndices();
                }
            });
            imageOrderToggleButton.setColorFilter(SharedConfig.groupPhotosEnabled ? new PorterDuffColorFilter(0xff66bffa, PorterDuff.Mode.MULTIPLY) : null);
        }
        allowIndices = (selectedAlbum != null || type == 0) && maxSelectedPhotos <= 0;

        listView.setEmptyView(emptyView);
        pickerBottomLayout.updateSelectedCount(selectedPhotos.size(), true);

        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
        if (searchItem != null) {
            searchItem.openSearch(true);
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
        } else if (id == NotificationCenter.recentImagesDidLoaded) {
            if (selectedAlbum == null && type == (Integer) args[0]) {
                recentImages = (ArrayList<MediaController.SearchImage>) args[1];
                loadingRecent = false;
                updateSearchInterface();
            }
        }
    }

    private void hideHint() {
        hintAnimation = new AnimatorSet();
        hintAnimation.playTogether(
                ObjectAnimator.ofFloat(hintTextView, "alpha", 0.0f)
        );
        hintAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animation.equals(hintAnimation)) {
                    hintAnimation = null;
                    hintHideRunnable = null;
                    if (hintTextView != null) {
                        hintTextView.setVisibility(View.GONE);
                    }
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (animation.equals(hintAnimation)) {
                    hintHideRunnable = null;
                    hintHideRunnable = null;
                }
            }
        });
        hintAnimation.setDuration(300);
        hintAnimation.start();
    }

    public void setMaxSelectedPhotos(int value) {
        maxSelectedPhotos = value;
    }

    private void showHint(boolean hide, boolean enabled) {
        if (getParentActivity() == null || fragmentView == null || hide && hintTextView == null) {
            return;
        }
        if (hintTextView == null) {
            hintTextView = new TextView(getParentActivity());
            hintTextView.setBackgroundDrawable(Theme.createRoundRectDrawable(AndroidUtilities.dp(3), Theme.getColor(Theme.key_chat_gifSaveHintBackground)));
            hintTextView.setTextColor(Theme.getColor(Theme.key_chat_gifSaveHintText));
            hintTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            hintTextView.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(7), AndroidUtilities.dp(8), AndroidUtilities.dp(7));
            hintTextView.setGravity(Gravity.CENTER_VERTICAL);
            hintTextView.setAlpha(0.0f);
            frameLayout.addView(hintTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 5, 0, 5, 48 + 3));
        }
        if (hide) {
            if (hintAnimation != null) {
                hintAnimation.cancel();
                hintAnimation = null;
            }
            AndroidUtilities.cancelRunOnUIThread(hintHideRunnable);
            hintHideRunnable = null;
            hideHint();
            return;
        }

        hintTextView.setText(enabled ? LocaleController.getString("GroupPhotosHelp", R.string.GroupPhotosHelp) : LocaleController.getString("SinglePhotosHelp", R.string.SinglePhotosHelp));

        if (hintHideRunnable != null) {
            if (hintAnimation != null) {
                hintAnimation.cancel();
                hintAnimation = null;
            } else {
                AndroidUtilities.cancelRunOnUIThread(hintHideRunnable);
                AndroidUtilities.runOnUIThread(hintHideRunnable = new Runnable() {
                    @Override
                    public void run() {
                        hideHint();
                    }
                }, 2000);
                return;
            }
        } else if (hintAnimation != null) {
            return;
        }

        hintTextView.setVisibility(View.VISIBLE);
        hintAnimation = new AnimatorSet();
        hintAnimation.playTogether(
                ObjectAnimator.ofFloat(hintTextView, "alpha", 1.0f)
        );
        hintAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animation.equals(hintAnimation)) {
                    hintAnimation = null;
                    AndroidUtilities.runOnUIThread(hintHideRunnable = new Runnable() {
                        @Override
                        public void run() {
                            hideHint();
                        }
                    }, 2000);
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (animation.equals(hintAnimation)) {
                    hintAnimation = null;
                }
            }
        });
        hintAnimation.setDuration(300);
        hintAnimation.start();
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
                int num = (Integer) cell.photoImage.getTag();
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

    private void searchGiphyImages(final String query, int offset) {
        if (searching) {
            searching = false;
            if (giphyReqId != 0) {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(giphyReqId, true);
                giphyReqId = 0;
            }
            if (imageReqId != 0) {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(imageReqId, true);
                imageReqId = 0;
            }
        }
        searching = true;
        TLRPC.TL_messages_searchGifs req = new TLRPC.TL_messages_searchGifs();
        req.q = query;
        req.offset = offset;
        final int token = ++lastSearchToken;
        giphyReqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate() {
            @Override
            public void run(final TLObject response, TLRPC.TL_error error) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (token != lastSearchToken) {
                            return;
                        }
                        int addedCount = 0;
                        if (response != null) {
                            boolean added = false;
                            TLRPC.TL_messages_foundGifs res = (TLRPC.TL_messages_foundGifs) response;
                            nextGiphySearchOffset = res.next_offset;
                            for (int a = 0; a < res.results.size(); a++) {
                                TLRPC.FoundGif gif = res.results.get(a);
                                if (searchResultKeys.containsKey(gif.url)) {
                                    continue;
                                }
                                added = true;
                                MediaController.SearchImage bingImage = new MediaController.SearchImage();
                                bingImage.id = gif.url;
                                if (gif.document != null) {
                                    for (int b = 0; b < gif.document.attributes.size(); b++) {
                                        TLRPC.DocumentAttribute attribute = gif.document.attributes.get(b);
                                        if (attribute instanceof TLRPC.TL_documentAttributeImageSize || attribute instanceof TLRPC.TL_documentAttributeVideo) {
                                            bingImage.width = attribute.w;
                                            bingImage.height = attribute.h;
                                            break;
                                        }
                                    }
                                } else {
                                    bingImage.width = gif.w;
                                    bingImage.height = gif.h;
                                }
                                bingImage.size = 0;
                                bingImage.imageUrl = gif.content_url;
                                bingImage.thumbUrl = gif.thumb_url;
                                bingImage.localUrl = gif.url + "|" + query;
                                bingImage.document = gif.document;
                                if (gif.photo != null && gif.document != null) {
                                    TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(gif.photo.sizes, itemWidth, true);
                                    if (size != null) {
                                        gif.document.thumb = size;
                                    }
                                }
                                bingImage.type = 1;
                                searchResult.add(bingImage);
                                addedCount++;
                                searchResultKeys.put(bingImage.id, bingImage);
                            }
                            giphySearchEndReached = !added;
                        }
                        searching = false;
                        if (addedCount != 0) {
                            listAdapter.notifyItemRangeInserted(searchResult.size(), addedCount);
                        } else if (giphySearchEndReached) {
                            listAdapter.notifyItemRemoved(searchResult.size() - 1);
                        }
                        if (searching && searchResult.isEmpty() || loadingRecent && lastSearchString == null) {
                            emptyView.showProgress();
                        } else {
                            emptyView.showTextView();
                        }
                    }
                });
            }
        });
        ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(giphyReqId, classGuid);
    }

    private void searchBotUser() {
        if (searchingUser) {
            return;
        }
        searchingUser = true;
        TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
        req.username = MessagesController.getInstance(currentAccount).imageSearchBot;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate() {
            @Override
            public void run(final TLObject response, TLRPC.TL_error error) {
                if (response != null) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            TLRPC.TL_contacts_resolvedPeer res = (TLRPC.TL_contacts_resolvedPeer) response;
                            MessagesController.getInstance(currentAccount).putUsers(res.users, false);
                            MessagesController.getInstance(currentAccount).putChats(res.chats, false);
                            MessagesStorage.getInstance(currentAccount).putUsersAndChats(res.users, res.chats, true, true);
                            String str = lastSearchImageString;
                            lastSearchImageString = null;
                            searchImages(str, "", false);
                        }
                    });
                }
            }
        });
    }

    private void searchImages(final String query, final String offset, boolean searchUser) {
        if (searching) {
            searching = false;
            if (giphyReqId != 0) {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(giphyReqId, true);
                giphyReqId = 0;
            }
            if (imageReqId != 0) {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(imageReqId, true);
                imageReqId = 0;
            }
        }

        lastSearchImageString = query;
        searching = true;

        TLObject object = MessagesController.getInstance(currentAccount).getUserOrChat(MessagesController.getInstance(currentAccount).imageSearchBot);
        if (!(object instanceof TLRPC.User)) {
            if (searchUser) {
                searchBotUser();
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
        imageReqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate() {
            @Override
            public void run(final TLObject response, TLRPC.TL_error error) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (token != lastSearchToken) {
                            return;
                        }
                        int addedCount = 0;
                        if (response != null) {
                            TLRPC.messages_BotResults res = (TLRPC.messages_BotResults) response;
                            nextImagesSearchOffset = res.next_offset;
                            boolean added = false;

                            for (int a = 0, count = res.results.size(); a < count; a++) {
                                TLRPC.BotInlineResult result = res.results.get(a);
                                if (!"photo".equals(result.type)) {
                                    continue;
                                }
                                if (searchResultKeys.containsKey(result.id)) {
                                    continue;
                                }

                                added = true;
                                MediaController.SearchImage bingImage = new MediaController.SearchImage();
                                if (result.photo != null) {
                                    TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(result.photo.sizes, AndroidUtilities.getPhotoSize());
                                    TLRPC.PhotoSize size2 = FileLoader.getClosestPhotoSizeWithSize(result.photo.sizes, 80);
                                    if (size == null) {
                                        continue;
                                    }
                                    bingImage.width = size.w;
                                    bingImage.height = size.h;
                                    bingImage.photoSize = size;
                                    bingImage.photo = result.photo;
                                    bingImage.size = size.size;
                                    bingImage.thumbPhotoSize = size2;
                                } else {
                                    if (result.content == null) {
                                        continue;
                                    }
                                    for (int b = 0; b < result.content.attributes.size(); b++) {
                                        TLRPC.DocumentAttribute attribute = result.content.attributes.get(b);
                                        if (attribute instanceof TLRPC.TL_documentAttributeImageSize) {
                                            bingImage.width = attribute.w;
                                            bingImage.height = attribute.h;
                                            break;
                                        }
                                    }
                                    if (result.thumb != null) {
                                        bingImage.thumbUrl = result.thumb.url;
                                    } else {
                                        bingImage.thumbUrl = null;
                                    }
                                    bingImage.imageUrl = result.content.url;
                                    bingImage.size = result.content.size;
                                }

                                bingImage.id = result.id;
                                bingImage.type = 0;
                                bingImage.localUrl = "";

                                searchResult.add(bingImage);

                                searchResultKeys.put(bingImage.id, bingImage);
                                addedCount++;
                                added = true;
                            }
                            bingSearchEndReached = !added || nextImagesSearchOffset == null;
                        }
                        searching = false;
                        if (addedCount != 0) {
                            listAdapter.notifyItemRangeInserted(searchResult.size(), addedCount);
                        } else if (bingSearchEndReached) {
                            listAdapter.notifyItemRemoved(searchResult.size() - 1);
                        }
                        if (searching && searchResult.isEmpty() || loadingRecent && lastSearchString == null) {
                            emptyView.showProgress();
                        } else {
                            emptyView.showTextView();
                        }
                    }
                });
            }
        });
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
        finishFragment();
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

        if (selectedAlbum == null) {
            emptyView.setPadding(0, 0, 0, (int) ((AndroidUtilities.displaySize.y - ActionBar.getCurrentActionBarHeight()) * 0.4f));
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
                if (searchResult.isEmpty() && lastSearchString == null) {
                    return recentImages.size();
                } else if (type == 0) {
                    return searchResult.size() + (bingSearchEndReached ? 0 : 1);
                } else if (type == 1) {
                    return searchResult.size() + (giphySearchEndReached ? 0 : 1);
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
                    cell.checkFrame.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            int index = (Integer) ((View) v.getParent()).getTag();
                            if (selectedAlbum != null) {
                                MediaController.PhotoEntry photoEntry = selectedAlbum.photos.get(index);
                                boolean added = !selectedPhotos.containsKey(photoEntry.imageId);
                                if (added && maxSelectedPhotos >= 0 && selectedPhotos.size() >= maxSelectedPhotos) {
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
                                if (added && maxSelectedPhotos >= 0 && selectedPhotos.size() >= maxSelectedPhotos) {
                                    return;
                                }
                                int num = allowIndices && added ? selectedPhotosOrder.size() : -1;
                                ((PhotoPickerPhotoCell) v.getParent()).setChecked(num, added, true);
                                addToSelectedPhotos(photoEntry, index);
                            }
                            pickerBottomLayout.updateSelectedCount(selectedPhotos.size(), true);
                            delegate.selectedPhotosChanged();
                        }
                    });
                    cell.checkFrame.setVisibility(singlePhoto ? View.GONE : View.VISIBLE);
                    view = cell;
                    break;
                case 1:
                default:
                    FrameLayout frameLayout = new FrameLayout(mContext);
                    view = frameLayout;
                    RadialProgressView progressBar = new RadialProgressView(mContext);
                    progressBar.setProgressColor(0xffffffff);
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
                    BackupImageView imageView = cell.photoImage;
                    imageView.setTag(position);
                    cell.setTag(position);
                    boolean showing;
                    imageView.setOrientation(0, true);

                    if (selectedAlbum != null) {
                        MediaController.PhotoEntry photoEntry = selectedAlbum.photos.get(position);
                        if (photoEntry.thumbPath != null) {
                            imageView.setImage(photoEntry.thumbPath, null, mContext.getResources().getDrawable(R.drawable.nophotos));
                        } else if (photoEntry.path != null) {
                            imageView.setOrientation(photoEntry.orientation, true);
                            if (photoEntry.isVideo) {
                                cell.videoInfoContainer.setVisibility(View.VISIBLE);
                                int minutes = photoEntry.duration / 60;
                                int seconds = photoEntry.duration - minutes * 60;
                                cell.videoTextView.setText(String.format("%d:%02d", minutes, seconds));
                                imageView.setImage("vthumb://" + photoEntry.imageId + ":" + photoEntry.path, null, mContext.getResources().getDrawable(R.drawable.nophotos));
                            } else {
                                cell.videoInfoContainer.setVisibility(View.INVISIBLE);
                                imageView.setImage("thumb://" + photoEntry.imageId + ":" + photoEntry.path, null, mContext.getResources().getDrawable(R.drawable.nophotos));
                            }
                        } else {
                            imageView.setImageResource(R.drawable.nophotos);
                        }
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
                    cell.checkBox.setVisibility(singlePhoto || showing ? View.GONE : View.VISIBLE);
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
}
