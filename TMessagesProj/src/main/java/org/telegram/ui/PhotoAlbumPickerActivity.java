/*
 * This is the source code of Telegram for Android v. 2.0.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Adapters.BaseFragmentAdapter;
import org.telegram.ui.Cells.PhotoPickerAlbumsCell;
import org.telegram.ui.Cells.PhotoPickerSearchCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PickerBottomLayout;

import java.util.ArrayList;
import java.util.HashMap;

public class PhotoAlbumPickerActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    public interface PhotoAlbumPickerActivityDelegate {
        void didSelectPhotos(ArrayList<String> photos, ArrayList<String> captions, ArrayList<MediaController.SearchImage> webPhotos);
        boolean didSelectVideo(String path);
        void startPhotoSelectActivity();
    }

    private ArrayList<MediaController.AlbumEntry> albumsSorted = null;
    private ArrayList<MediaController.AlbumEntry> videoAlbumsSorted = null;
    private HashMap<Integer, MediaController.PhotoEntry> selectedPhotos = new HashMap<>();
    private HashMap<String, MediaController.SearchImage> selectedWebPhotos = new HashMap<>();
    private HashMap<String, MediaController.SearchImage> recentImagesWebKeys = new HashMap<>();
    private HashMap<String, MediaController.SearchImage> recentImagesGifKeys = new HashMap<>();
    private ArrayList<MediaController.SearchImage> recentWebImages = new ArrayList<>();
    private ArrayList<MediaController.SearchImage> recentGifImages = new ArrayList<>();
    private boolean loading = false;

    private int columnsCount = 2;
    private ListView listView;
    private ListAdapter listAdapter;
    private FrameLayout progressView;
    private TextView emptyView;
    private TextView dropDown;
    private ActionBarMenuItem dropDownContainer;
    private PickerBottomLayout pickerBottomLayout;
    private boolean sendPressed;
    private boolean singlePhoto;
    private boolean allowGifs;
    private int selectedMode;
    private ChatActivity chatActivity;

    private PhotoAlbumPickerActivityDelegate delegate;

    private final static int item_photos = 2;
    private final static int item_video = 3;

    public PhotoAlbumPickerActivity(boolean singlePhoto, boolean allowGifs, ChatActivity chatActivity) {
        super();
        this.chatActivity = chatActivity;
        this.singlePhoto = singlePhoto;
        this.allowGifs = allowGifs;
    }

    @Override
    public boolean onFragmentCreate() {
        loading = true;
        MediaController.loadGalleryPhotosAlbums(classGuid);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.albumsDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.recentImagesDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.closeChats);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.albumsDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.recentImagesDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.closeChats);
        super.onFragmentDestroy();
    }

    @SuppressWarnings("unchecked")
    @Override
    public View createView(Context context) {
        actionBar.setBackgroundColor(Theme.ACTION_BAR_MEDIA_PICKER_COLOR);
        actionBar.setItemsBackgroundColor(Theme.ACTION_BAR_PICKER_SELECTOR_COLOR);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (Build.VERSION.SDK_INT < 11) {
                        listView.setAdapter(null);
                        listView = null;
                        listAdapter = null;
                    }
                    finishFragment();
                } else if (id == 1) {
                    if (delegate != null) {
                        finishFragment(false);
                        delegate.startPhotoSelectActivity();
                    }
                } else if (id == item_photos) {
                    if (selectedMode == 0) {
                        return;
                    }
                    selectedMode = 0;
                    dropDown.setText(LocaleController.getString("PickerPhotos", R.string.PickerPhotos));
                    emptyView.setText(LocaleController.getString("NoPhotos", R.string.NoPhotos));
                    listAdapter.notifyDataSetChanged();
                } else if (id == item_video) {
                    if (selectedMode == 1) {
                        return;
                    }
                    selectedMode = 1;
                    dropDown.setText(LocaleController.getString("PickerVideo", R.string.PickerVideo));
                    emptyView.setText(LocaleController.getString("NoVideo", R.string.NoVideo));
                    listAdapter.notifyDataSetChanged();
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        menu.addItem(1, R.drawable.ic_ab_other);

        fragmentView = new FrameLayout(context);

        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(0xff000000);

        if (!singlePhoto) {
            selectedMode = 0;

            dropDownContainer = new ActionBarMenuItem(context, menu, 0);
            dropDownContainer.setSubMenuOpenSide(1);
            dropDownContainer.addSubItem(item_photos, LocaleController.getString("PickerPhotos", R.string.PickerPhotos), 0);
            dropDownContainer.addSubItem(item_video, LocaleController.getString("PickerVideo", R.string.PickerVideo), 0);
            actionBar.addView(dropDownContainer);
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) dropDownContainer.getLayoutParams();
            layoutParams.height = LayoutHelper.MATCH_PARENT;
            layoutParams.width = LayoutHelper.WRAP_CONTENT;
            layoutParams.rightMargin = AndroidUtilities.dp(40);
            layoutParams.leftMargin = AndroidUtilities.isTablet() ? AndroidUtilities.dp(64) : AndroidUtilities.dp(56);
            layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
            dropDownContainer.setLayoutParams(layoutParams);
            dropDownContainer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    dropDownContainer.toggleSubMenu();
                }
            });

            dropDown = new TextView(context);
            dropDown.setGravity(Gravity.LEFT);
            dropDown.setSingleLine(true);
            dropDown.setLines(1);
            dropDown.setMaxLines(1);
            dropDown.setEllipsize(TextUtils.TruncateAt.END);
            dropDown.setTextColor(0xffffffff);
            dropDown.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            dropDown.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_drop_down, 0);
            dropDown.setCompoundDrawablePadding(AndroidUtilities.dp(4));
            dropDown.setPadding(0, 0, AndroidUtilities.dp(10), 0);
            dropDown.setText(LocaleController.getString("PickerPhotos", R.string.PickerPhotos));
            dropDownContainer.addView(dropDown);
            layoutParams = (FrameLayout.LayoutParams) dropDown.getLayoutParams();
            layoutParams.width = LayoutHelper.WRAP_CONTENT;
            layoutParams.height = LayoutHelper.WRAP_CONTENT;
            layoutParams.leftMargin = AndroidUtilities.dp(16);
            layoutParams.gravity = Gravity.CENTER_VERTICAL;
            dropDown.setLayoutParams(layoutParams);
        } else {
            actionBar.setTitle(LocaleController.getString("Gallery", R.string.Gallery));
        }

        listView = new ListView(context);
        listView.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), AndroidUtilities.dp(4));
        listView.setClipToPadding(false);
        listView.setHorizontalScrollBarEnabled(false);
        listView.setVerticalScrollBarEnabled(false);
        listView.setSelector(new ColorDrawable(0));
        listView.setDividerHeight(0);
        listView.setDivider(null);
        listView.setDrawingCacheEnabled(false);
        listView.setScrollingCacheEnabled(false);
        frameLayout.addView(listView);
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) listView.getLayoutParams();
        layoutParams.width = LayoutHelper.MATCH_PARENT;
        layoutParams.height = LayoutHelper.MATCH_PARENT;
        layoutParams.bottomMargin = AndroidUtilities.dp(48);
        listView.setLayoutParams(layoutParams);
        listView.setAdapter(listAdapter = new ListAdapter(context));
        AndroidUtilities.setListViewEdgeEffectColor(listView, 0xff333333);

        emptyView = new TextView(context);
        emptyView.setTextColor(0xff808080);
        emptyView.setTextSize(20);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setVisibility(View.GONE);
        emptyView.setText(LocaleController.getString("NoPhotos", R.string.NoPhotos));
        frameLayout.addView(emptyView);
        layoutParams = (FrameLayout.LayoutParams) emptyView.getLayoutParams();
        layoutParams.width = LayoutHelper.MATCH_PARENT;
        layoutParams.height = LayoutHelper.MATCH_PARENT;
        layoutParams.bottomMargin = AndroidUtilities.dp(48);
        emptyView.setLayoutParams(layoutParams);
        emptyView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        progressView = new FrameLayout(context);
        progressView.setVisibility(View.GONE);
        frameLayout.addView(progressView);
        layoutParams = (FrameLayout.LayoutParams) progressView.getLayoutParams();
        layoutParams.width = LayoutHelper.MATCH_PARENT;
        layoutParams.height = LayoutHelper.MATCH_PARENT;
        layoutParams.bottomMargin = AndroidUtilities.dp(48);
        progressView.setLayoutParams(layoutParams);

        ProgressBar progressBar = new ProgressBar(context);
        progressView.addView(progressBar);
        layoutParams = (FrameLayout.LayoutParams) progressView.getLayoutParams();
        layoutParams.width = LayoutHelper.WRAP_CONTENT;
        layoutParams.height = LayoutHelper.WRAP_CONTENT;
        layoutParams.gravity = Gravity.CENTER;
        progressView.setLayoutParams(layoutParams);

        pickerBottomLayout = new PickerBottomLayout(context);
        frameLayout.addView(pickerBottomLayout);
        layoutParams = (FrameLayout.LayoutParams) pickerBottomLayout.getLayoutParams();
        layoutParams.width = LayoutHelper.MATCH_PARENT;
        layoutParams.height = AndroidUtilities.dp(48);
        layoutParams.gravity = Gravity.BOTTOM;
        pickerBottomLayout.setLayoutParams(layoutParams);
        pickerBottomLayout.cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finishFragment();
            }
        });
        pickerBottomLayout.doneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendSelectedPhotos();
                finishFragment();
            }
        });

        if (loading && (albumsSorted == null || albumsSorted != null && albumsSorted.isEmpty())) {
            progressView.setVisibility(View.VISIBLE);
            listView.setEmptyView(null);
        } else {
            progressView.setVisibility(View.GONE);
            listView.setEmptyView(emptyView);
        }
        pickerBottomLayout.updateSelectedCount(selectedPhotos.size() + selectedWebPhotos.size(), true);

        return fragmentView;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (dropDownContainer != null) {
            dropDownContainer.closeSubMenu();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
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
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.albumsDidLoaded) {
            int guid = (Integer) args[0];
            if (classGuid == guid) {
                albumsSorted = (ArrayList<MediaController.AlbumEntry>) args[1];
                videoAlbumsSorted = (ArrayList<MediaController.AlbumEntry>) args[3];
                if (progressView != null) {
                    progressView.setVisibility(View.GONE);
                }
                if (listView != null && listView.getEmptyView() == null) {
                    listView.setEmptyView(emptyView);
                }
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }
                loading = false;
            }
        } else if (id == NotificationCenter.closeChats) {
            removeSelfFromStack();
        } else if (id == NotificationCenter.recentImagesDidLoaded) {
            int type = (Integer) args[0];
            if (type == 0) {
                recentWebImages = (ArrayList<MediaController.SearchImage>) args[1];
                recentImagesWebKeys.clear();
                for (MediaController.SearchImage searchImage : recentWebImages) {
                    recentImagesWebKeys.put(searchImage.id, searchImage);
                }
            } else if (type == 1) {
                recentGifImages = (ArrayList<MediaController.SearchImage>) args[1];
                recentImagesGifKeys.clear();
                for (MediaController.SearchImage searchImage : recentGifImages) {
                    recentImagesGifKeys.put(searchImage.id, searchImage);
                }
            }
        }
    }

    public void setDelegate(PhotoAlbumPickerActivityDelegate delegate) {
        this.delegate = delegate;
    }

    private void sendSelectedPhotos() {
        if (selectedPhotos.isEmpty() && selectedWebPhotos.isEmpty() || delegate == null || sendPressed) {
            return;
        }
        sendPressed = true;
        ArrayList<String> photos = new ArrayList<>();
        ArrayList<String> captions = new ArrayList<>();
        for (HashMap.Entry<Integer, MediaController.PhotoEntry> entry : selectedPhotos.entrySet()) {
            MediaController.PhotoEntry photoEntry = entry.getValue();
            if (photoEntry.imagePath != null) {
                photos.add(photoEntry.imagePath);
                captions.add(photoEntry.caption != null ? photoEntry.caption.toString() : null);
            } else if (photoEntry.path != null) {
                photos.add(photoEntry.path);
                captions.add(photoEntry.caption != null ? photoEntry.caption.toString() : null);
            }
        }
        ArrayList<MediaController.SearchImage> webPhotos = new ArrayList<>();
        boolean gifChanged = false;
        boolean webChange = false;
        for (HashMap.Entry<String, MediaController.SearchImage> entry : selectedWebPhotos.entrySet()) {
            MediaController.SearchImage searchImage = entry.getValue();
            if (searchImage.imagePath != null) {
                photos.add(searchImage.imagePath);
                captions.add(searchImage.caption != null ? searchImage.caption.toString() : null);
            } else {
                webPhotos.add(searchImage);
            }
            searchImage.date = (int) (System.currentTimeMillis() / 1000);

            if (searchImage.type == 0) {
                webChange = true;
                MediaController.SearchImage recentImage = recentImagesWebKeys.get(searchImage.id);
                if (recentImage != null) {
                    recentWebImages.remove(recentImage);
                    recentWebImages.add(0, recentImage);
                } else {
                    recentWebImages.add(0, searchImage);
                }
            } else if (searchImage.type == 1) {
                gifChanged = true;
                MediaController.SearchImage recentImage = recentImagesGifKeys.get(searchImage.id);
                if (recentImage != null) {
                    recentGifImages.remove(recentImage);
                    recentGifImages.add(0, recentImage);
                } else {
                    recentGifImages.add(0, searchImage);
                }
            }
        }
        if (webChange) {
            MessagesStorage.getInstance().putWebRecent(recentWebImages);
        }
        if (gifChanged) {
            MessagesStorage.getInstance().putWebRecent(recentGifImages);
        }

        delegate.didSelectPhotos(photos, captions, webPhotos);
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

        WindowManager manager = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Activity.WINDOW_SERVICE);
        int rotation = manager.getDefaultDisplay().getRotation();
        columnsCount = 2;
        if (!AndroidUtilities.isTablet() && (rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90)) {
            columnsCount = 4;
        }
        listAdapter.notifyDataSetChanged();

        if (dropDownContainer != null) {
            if (!AndroidUtilities.isTablet()) {
                FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) dropDownContainer.getLayoutParams();
                layoutParams.topMargin = (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
                dropDownContainer.setLayoutParams(layoutParams);
            }

            if (!AndroidUtilities.isTablet() && ApplicationLoader.applicationContext.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                dropDown.setTextSize(18);
            } else {
                dropDown.setTextSize(20);
            }
        }
    }

    private void openPhotoPicker(MediaController.AlbumEntry albumEntry, int type) {
        ArrayList<MediaController.SearchImage> recentImages = null;
        if (albumEntry == null) {
            if (type == 0) {
                recentImages = recentWebImages;
            } else if (type == 1) {
                recentImages = recentGifImages;
            }
        }
        PhotoPickerActivity fragment = new PhotoPickerActivity(type, albumEntry, selectedPhotos, selectedWebPhotos, recentImages, singlePhoto, chatActivity);
        fragment.setDelegate(new PhotoPickerActivity.PhotoPickerActivityDelegate() {
            @Override
            public void selectedPhotosChanged() {
                if (pickerBottomLayout != null) {
                    pickerBottomLayout.updateSelectedCount(selectedPhotos.size() + selectedWebPhotos.size(), true);
                }
            }

            @Override
            public void actionButtonPressed(boolean canceled) {
                removeSelfFromStack();
                if (!canceled) {
                    sendSelectedPhotos();
                }
            }

            @Override
            public boolean didSelectVideo(String path) {
                removeSelfFromStack();
                return delegate.didSelectVideo(path);
            }
        });
        presentFragment(fragment);
    }

    private class ListAdapter extends BaseFragmentAdapter {
        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return true;
        }

        @Override
        public boolean isEnabled(int i) {
            return true;
        }

        @Override
        public int getCount() {
            if (singlePhoto || selectedMode == 0) {
                if (singlePhoto) {
                    return albumsSorted != null ? (int) Math.ceil(albumsSorted.size() / (float) columnsCount) : 0;
                }
                return 1 + (albumsSorted != null ? (int) Math.ceil(albumsSorted.size() / (float) columnsCount) : 0);
            } else {
                return (videoAlbumsSorted != null ? (int) Math.ceil(videoAlbumsSorted.size() / (float) columnsCount) : 0);
            }
        }

        @Override
        public Object getItem(int i) {
            return null;
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            int type = getItemViewType(i);
            if (type == 0) {
                PhotoPickerAlbumsCell photoPickerAlbumsCell;
                if (view == null) {
                    view = new PhotoPickerAlbumsCell(mContext);
                    photoPickerAlbumsCell = (PhotoPickerAlbumsCell) view;
                    photoPickerAlbumsCell.setDelegate(new PhotoPickerAlbumsCell.PhotoPickerAlbumsCellDelegate() {
                        @Override
                        public void didSelectAlbum(MediaController.AlbumEntry albumEntry) {
                            openPhotoPicker(albumEntry, 0);
                        }
                    });
                } else {
                    photoPickerAlbumsCell = (PhotoPickerAlbumsCell) view;
                }
                photoPickerAlbumsCell.setAlbumsCount(columnsCount);
                for (int a = 0; a < columnsCount; a++) {
                    int index;
                    if (singlePhoto || selectedMode == 1) {
                        index = i * columnsCount + a;
                    } else {
                        index = (i - 1) * columnsCount + a;
                    }
                    if (singlePhoto || selectedMode == 0) {
                        if (index < albumsSorted.size()) {
                            MediaController.AlbumEntry albumEntry = albumsSorted.get(index);
                            photoPickerAlbumsCell.setAlbum(a, albumEntry);
                        } else {
                            photoPickerAlbumsCell.setAlbum(a, null);
                        }
                    } else {
                        if (index < videoAlbumsSorted.size()) {
                            MediaController.AlbumEntry albumEntry = videoAlbumsSorted.get(index);
                            photoPickerAlbumsCell.setAlbum(a, albumEntry);
                        } else {
                            photoPickerAlbumsCell.setAlbum(a, null);
                        }
                    }
                }
                photoPickerAlbumsCell.requestLayout();
            } else if (type == 1) {
                if (view == null) {
                    view = new PhotoPickerSearchCell(mContext, allowGifs);
                    ((PhotoPickerSearchCell) view).setDelegate(new PhotoPickerSearchCell.PhotoPickerSearchCellDelegate() {
                        @Override
                        public void didPressedSearchButton(int index) {
                            openPhotoPicker(null, index);
                        }
                    });
                }
            }
            return view;
        }

        @Override
        public int getItemViewType(int i) {
            if (singlePhoto || selectedMode == 1) {
                return 0;
            }
            if (i == 0) {
                return 1;
            }
            return 0;
        }

        @Override
        public int getViewTypeCount() {
            if (singlePhoto || selectedMode == 1) {
                return 1;
            }
            return 2;
        }

        @Override
        public boolean isEmpty() {
            return getCount() == 0;
        }
    }
}
