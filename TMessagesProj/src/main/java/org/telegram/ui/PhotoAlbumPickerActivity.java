/*
 * This is the source code of Telegram for Android v. 2.0.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.LayoutInflater;
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

import org.telegram.android.AndroidUtilities;
import org.telegram.android.LocaleController;
import org.telegram.android.MediaController;
import org.telegram.android.MessagesStorage;
import org.telegram.android.NotificationCenter;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Adapters.BaseFragmentAdapter;
import org.telegram.ui.Cells.PhotoPickerAlbumsCell;
import org.telegram.ui.Cells.PhotoPickerSearchCell;
import org.telegram.ui.Components.PhotoPickerBottomLayout;

import java.util.ArrayList;
import java.util.HashMap;

public class PhotoAlbumPickerActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    public interface PhotoAlbumPickerActivityDelegate {
        void didSelectPhotos(ArrayList<String> photos, ArrayList<MediaController.SearchImage> webPhotos);
        void startPhotoSelectActivity();
    }

    private ArrayList<MediaController.AlbumEntry> albumsSorted = null;
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
    private PhotoPickerBottomLayout photoPickerBottomLayout;
    private boolean sendPressed = false;
    private boolean singlePhoto = false;

    private PhotoAlbumPickerActivityDelegate delegate;

    public PhotoAlbumPickerActivity(boolean onlyOnePhoto) {
        super();
        singlePhoto = onlyOnePhoto;
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
    public View createView(Context context, LayoutInflater inflater) {
        actionBar.setBackgroundColor(0xff333333);
        actionBar.setItemsBackground(R.drawable.bar_selector_picker);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(LocaleController.getString("Gallery", R.string.Gallery));
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
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        menu.addItem(1, R.drawable.ic_ab_other);

        fragmentView = new FrameLayout(context);

        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(0xff000000);

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
        layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
        layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
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
        layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
        layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
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
        layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
        layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
        layoutParams.bottomMargin = AndroidUtilities.dp(48);
        progressView.setLayoutParams(layoutParams);

        ProgressBar progressBar = new ProgressBar(context);
        progressView.addView(progressBar);
        layoutParams = (FrameLayout.LayoutParams) progressView.getLayoutParams();
        layoutParams.width = FrameLayout.LayoutParams.WRAP_CONTENT;
        layoutParams.height = FrameLayout.LayoutParams.WRAP_CONTENT;
        layoutParams.gravity = Gravity.CENTER;
        progressView.setLayoutParams(layoutParams);

        photoPickerBottomLayout = new PhotoPickerBottomLayout(context);
        frameLayout.addView(photoPickerBottomLayout);
        layoutParams = (FrameLayout.LayoutParams) photoPickerBottomLayout.getLayoutParams();
        layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
        layoutParams.height = AndroidUtilities.dp(48);
        layoutParams.gravity = Gravity.BOTTOM;
        photoPickerBottomLayout.setLayoutParams(layoutParams);
        photoPickerBottomLayout.cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finishFragment();
            }
        });
        photoPickerBottomLayout.doneButton.setOnClickListener(new View.OnClickListener() {
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
        photoPickerBottomLayout.updateSelectedCount(selectedPhotos.size() + selectedWebPhotos.size(), true);

        return fragmentView;
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
        for (HashMap.Entry<Integer, MediaController.PhotoEntry> entry : selectedPhotos.entrySet()) {
            MediaController.PhotoEntry photoEntry = entry.getValue();
            if (photoEntry.imagePath != null) {
                photos.add(photoEntry.imagePath);
            } else if (photoEntry.path != null) {
                photos.add(photoEntry.path);
            }
        }
        ArrayList<MediaController.SearchImage> webPhotos = new ArrayList<>();
        boolean gifChanged = false;
        boolean webChange = false;
        for (HashMap.Entry<String, MediaController.SearchImage> entry : selectedWebPhotos.entrySet()) {
            MediaController.SearchImage searchImage = entry.getValue();
            if (searchImage.imagePath != null) {
                photos.add(searchImage.imagePath);
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

        delegate.didSelectPhotos(photos, webPhotos);
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
                    return false;
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
        PhotoPickerActivity fragment = new PhotoPickerActivity(type, albumEntry, selectedPhotos, selectedWebPhotos, recentImages, singlePhoto);
        fragment.setDelegate(new PhotoPickerActivity.PhotoPickerActivityDelegate() {
            @Override
            public void selectedPhotosChanged() {
                if (photoPickerBottomLayout != null) {
                    photoPickerBottomLayout.updateSelectedCount(selectedPhotos.size() + selectedWebPhotos.size(), true);
                }
            }

            @Override
            public void actionButtonPressed(boolean canceled) {
                removeSelfFromStack();
                if (!canceled) {
                    sendSelectedPhotos();
                }
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
            if (singlePhoto) {
                return albumsSorted != null ? (int) Math.ceil(albumsSorted.size() / (float) columnsCount) : 0;
            }
            return 1 + (albumsSorted != null ? (int) Math.ceil(albumsSorted.size() / (float) columnsCount) : 0);
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
                PhotoPickerAlbumsCell photoPickerAlbumsCell = null;
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
                    if (singlePhoto) {
                        index = i * columnsCount + a;
                    } else {
                        index = (i - 1) * columnsCount + a;
                    }
                    if (index < albumsSorted.size()) {
                        MediaController.AlbumEntry albumEntry = albumsSorted.get(index);
                        photoPickerAlbumsCell.setAlbum(a, albumEntry);
                    } else {
                        photoPickerAlbumsCell.setAlbum(a, null);
                    }
                }
                photoPickerAlbumsCell.requestLayout();
            } else if (type == 1) {
                if (view == null) {
                    view = new PhotoPickerSearchCell(mContext);
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
            if (singlePhoto) {
                return 0;
            }
            if (i == 0) {
                return 1;
            }
            return 0;
        }

        @Override
        public int getViewTypeCount() {
            if (singlePhoto) {
                return 1;
            }
            return 2;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }
    }
}
