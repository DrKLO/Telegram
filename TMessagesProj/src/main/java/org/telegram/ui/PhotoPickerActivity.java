/*
 * This is the source code of Telegram for Android v. 1.4.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.LocaleController;
import org.telegram.android.MediaController;
import org.telegram.android.NotificationCenter;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.messenger.TLRPC;
import org.telegram.android.MessageObject;
import org.telegram.ui.Adapters.BaseFragmentAdapter;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BackupImageView;

import java.util.ArrayList;
import java.util.HashMap;

public class PhotoPickerActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, PhotoViewer.PhotoViewerProvider {

    public static interface PhotoPickerActivityDelegate {
        public abstract void didSelectPhotos(ArrayList<String> photos);
        public abstract void startPhotoSelectActivity();
    }

    private ArrayList<MediaController.AlbumEntry> albumsSorted = null;
    private HashMap<Integer, MediaController.PhotoEntry> selectedPhotos = new HashMap<Integer, MediaController.PhotoEntry>();
    private Integer cameraAlbumId = null;
    private boolean loading = false;
    private MediaController.AlbumEntry selectedAlbum = null;

    private GridView listView;
    private ListAdapter listAdapter;
    private View progressView;
    private TextView emptyView;
    private View doneButton;
    private TextView doneButtonTextView;
    private TextView doneButtonBadgeTextView;
    private int itemWidth = 100;
    private boolean sendPressed = false;

    private PhotoPickerActivityDelegate delegate;

    @Override
    public boolean onFragmentCreate() {
        loading = true;
        MediaController.loadGalleryPhotosAlbums(classGuid);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.albumsDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.closeChats);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.albumsDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.closeChats);
        super.onFragmentDestroy();
    }

    @Override
    public View createView(LayoutInflater inflater, ViewGroup container) {
        if (fragmentView == null) {
            actionBar.setBackgroundColor(0xff333333);
            actionBar.setItemsBackground(R.drawable.bar_selector_picker);
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
            actionBar.setTitle(LocaleController.getString("Gallery", R.string.Gallery));
            actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == -1) {
                        if (selectedAlbum != null) {
                            selectedAlbum = null;
                            actionBar.setTitle(LocaleController.getString("Gallery", R.string.Gallery));
                            fixLayoutInternal();
                        } else {
                            if (Build.VERSION.SDK_INT < 11) {
                                listView.setAdapter(null);
                                listView = null;
                                listAdapter = null;
                            }
                            finishFragment();
                        }
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

            fragmentView = inflater.inflate(R.layout.photo_picker_layout, container, false);

            emptyView = (TextView)fragmentView.findViewById(R.id.searchEmptyView);
            emptyView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return true;
                }
            });
            emptyView.setText(LocaleController.getString("NoPhotos", R.string.NoPhotos));
            listView = (GridView)fragmentView.findViewById(R.id.media_grid);
            progressView = fragmentView.findViewById(R.id.progressLayout);

            Button cancelButton = (Button)fragmentView.findViewById(R.id.cancel_button);
            cancelButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    finishFragment();
                }
            });
            doneButton = fragmentView.findViewById(R.id.done_button);
            doneButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    sendSelectedPhotos();
                }
            });

            cancelButton.setText(LocaleController.getString("Cancel", R.string.Cancel).toUpperCase());
            cancelButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            doneButtonTextView = (TextView)doneButton.findViewById(R.id.done_button_text);
            doneButtonTextView.setText(LocaleController.getString("Send", R.string.Send).toUpperCase());
            doneButtonTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            doneButtonBadgeTextView = (TextView)doneButton.findViewById(R.id.done_button_badge);
            doneButtonBadgeTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));

            listView.setAdapter(listAdapter = new ListAdapter(getParentActivity()));
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    if (selectedAlbum == null) {
                        if (i < 0 || i >= albumsSorted.size()) {
                            return;
                        }
                        selectedAlbum = albumsSorted.get(i);
                        actionBar.setTitle(selectedAlbum.bucketName);
                        fixLayoutInternal();
                    } else {
                        if (i < 0 || i >= selectedAlbum.photos.size()) {
                            return;
                        }
                        PhotoViewer.getInstance().setParentActivity(getParentActivity());
                        PhotoViewer.getInstance().openPhotoForSelect(selectedAlbum.photos, i, PhotoPickerActivity.this);
                    }
                }
            });
            if (loading && (albumsSorted == null || albumsSorted != null && albumsSorted.isEmpty())) {
                progressView.setVisibility(View.VISIBLE);
                listView.setEmptyView(null);
            } else {
                progressView.setVisibility(View.GONE);
                listView.setEmptyView(emptyView);
            }
            updateSelectedCount();
        } else {
            ViewGroup parent = (ViewGroup)fragmentView.getParent();
            if (parent != null) {
                parent.removeView(fragmentView);
            }
        }
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
            int guid = (Integer)args[0];
            if (classGuid == guid) {
                albumsSorted = (ArrayList<MediaController.AlbumEntry>)args[1];
                if (args[2] != null) {
                    cameraAlbumId = (Integer) args[2];
                }
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
        }
    }

    @Override
    public boolean onBackPressed() {
        if (selectedAlbum != null) {
            selectedAlbum = null;
            actionBar.setTitle(LocaleController.getString("Gallery", R.string.Gallery));
            fixLayoutInternal();
            return false;
        }
        return super.onBackPressed();
    }

    @Override
    public PhotoViewer.PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) {
        if (selectedAlbum == null) {
            return null;
        }
        int count = listView.getChildCount();

        for (int a = 0; a < count; a++) {
            View view = listView.getChildAt(a);
            BackupImageView imageView = (BackupImageView)view.findViewById(R.id.media_photo_image);
            if (imageView != null) {
                int num = (Integer)imageView.getTag();
                if (num < 0 || num >= selectedAlbum.photos.size()) {
                    continue;
                }
                if (num == index) {
                    int coords[] = new int[2];
                    imageView.getLocationInWindow(coords);
                    PhotoViewer.PlaceProviderObject object = new PhotoViewer.PlaceProviderObject();
                    object.viewX = coords[0];
                    object.viewY = coords[1] - AndroidUtilities.statusBarHeight;
                    object.parentView = listView;
                    object.imageReceiver = imageView.imageReceiver;
                    object.thumb = object.imageReceiver.getBitmap();
                    View frameView = view.findViewById(R.id.photo_frame);
                    frameView.setVisibility(View.GONE);
                    ImageView checkImageView = (ImageView)view.findViewById(R.id.photo_check);
                    checkImageView.setVisibility(View.GONE);
                    return object;
                }
            }
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
            int num = (Integer)view.getTag();
            if (num < 0 || num >= selectedAlbum.photos.size()) {
                continue;
            }
            if (num == index) {
                View frameView = view.findViewById(R.id.photo_frame);
                frameView.setVisibility(View.VISIBLE);
                ImageView checkImageView = (ImageView)view.findViewById(R.id.photo_check);
                checkImageView.setVisibility(View.VISIBLE);
                break;
            }
        }
    }

    @Override
    public void willHidePhotoViewer() {
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public boolean isPhotoChecked(int index) {
        if (selectedAlbum == null || index < 0 || index >= selectedAlbum.photos.size()) {
            return false;
        }
        MediaController.PhotoEntry photoEntry = selectedAlbum.photos.get(index);
        return selectedPhotos.containsKey(photoEntry.imageId);
    }

    @Override
    public void setPhotoChecked(int index) {
        if (selectedAlbum == null || index < 0 || index >= selectedAlbum.photos.size()) {
            return;
        }
        MediaController.PhotoEntry photoEntry = selectedAlbum.photos.get(index);
        if (selectedPhotos.containsKey(photoEntry.imageId)) {
            selectedPhotos.remove(photoEntry.imageId);
        } else {
            selectedPhotos.put(photoEntry.imageId, photoEntry);
        }
        int count = listView.getChildCount();

        for (int a = 0; a < count; a++) {
            View view = listView.getChildAt(a);
            int num = (Integer)view.getTag();
            if (num == index) {
                updateSelectedPhoto(view, photoEntry);
                break;
            }
        }
        updateSelectedCount();
    }

    @Override
    public void cancelButtonPressed() {
        finishFragment();
    }

    @Override
    public void sendButtonPressed(int index) {
        if (selectedPhotos.isEmpty()) {
            if (selectedAlbum == null || index < 0 || index >= selectedAlbum.photos.size()) {
                return;
            }
            MediaController.PhotoEntry photoEntry = selectedAlbum.photos.get(index);
            selectedPhotos.put(photoEntry.imageId, photoEntry);
        }
        sendSelectedPhotos();
    }

    @Override
    public int getSelectedCount() {
        return selectedPhotos.size();
    }

    public void setDelegate(PhotoPickerActivityDelegate delegate) {
        this.delegate = delegate;
    }

    private void sendSelectedPhotos() {
        if (selectedPhotos.isEmpty() || delegate == null || sendPressed) {
            return;
        }
        sendPressed = true;
        ArrayList<String> photos = new ArrayList<String>();
        for (HashMap.Entry<Integer, MediaController.PhotoEntry> entry : selectedPhotos.entrySet()) {
            MediaController.PhotoEntry photoEntry = entry.getValue();
            if (photoEntry.path != null) {
                photos.add(photoEntry.path);
            }
        }
        delegate.didSelectPhotos(photos);
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
                    return false;
                }
            });
        }
    }

    private void fixLayoutInternal() {
        if (getParentActivity() == null) {
            return;
        }
        int position = listView.getFirstVisiblePosition();
        WindowManager manager = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Activity.WINDOW_SERVICE);
        int rotation = manager.getDefaultDisplay().getRotation();

        int columnsCount = 2;
        if (selectedAlbum != null) {
            if (AndroidUtilities.isTablet()) {
                columnsCount = 3;
            } else {
                if (rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90) {
                    columnsCount = 5;
                } else {
                    columnsCount = 3;
                }
            }
        } else {
            if (rotation == Surface.ROTATION_270 || rotation == Surface.ROTATION_90) {
                columnsCount = 4;
            }
        }
        listView.setNumColumns(columnsCount);
        if (AndroidUtilities.isTablet()) {
            itemWidth = (AndroidUtilities.dp(490) - ((columnsCount + 1) * AndroidUtilities.dp(4))) / columnsCount;
        } else {
            itemWidth = (AndroidUtilities.displaySize.x - ((columnsCount + 1) * AndroidUtilities.dp(4))) / columnsCount;
        }
        listView.setColumnWidth(itemWidth);

        listAdapter.notifyDataSetChanged();
        listView.setSelection(position);
    }

    private void updateSelectedCount() {
        if (selectedPhotos.isEmpty()) {
            doneButtonTextView.setTextColor(0xff999999);
            doneButtonTextView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.selectphoto_small_grey, 0, 0, 0);
            doneButtonBadgeTextView.setVisibility(View.GONE);
            doneButton.setEnabled(false);
        } else {
            doneButtonTextView.setTextColor(0xffffffff);
            doneButtonTextView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            doneButtonBadgeTextView.setVisibility(View.VISIBLE);
            doneButtonBadgeTextView.setText("" + selectedPhotos.size());
            doneButton.setEnabled(true);
        }
    }

    private void updateSelectedPhoto(View view, MediaController.PhotoEntry photoEntry) {
        View frameView = view.findViewById(R.id.photo_frame);
        ImageView checkImageView = (ImageView)view.findViewById(R.id.photo_check);
        if (selectedPhotos.containsKey(photoEntry.imageId)) {
            frameView.setBackgroundResource(R.drawable.photoborder);
            checkImageView.setImageResource(R.drawable.selectphoto_small_active);
            checkImageView.setBackgroundColor(0xff42d1f6);
        } else {
            frameView.setBackgroundDrawable(null);
            checkImageView.setImageResource(R.drawable.selectphoto_small);
            checkImageView.setBackgroundColor(0x501c1c1c);
        }
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
            if (selectedAlbum != null) {
                return selectedAlbum.photos.size();
            }
            return albumsSorted != null ? albumsSorted.size() : 0;
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
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.photo_picker_album_layout, viewGroup, false);
                }
                ViewGroup.LayoutParams params = view.getLayoutParams();
                params.width = itemWidth;
                params.height = itemWidth;
                view.setLayoutParams(params);

                MediaController.AlbumEntry albumEntry = albumsSorted.get(i);
                BackupImageView imageView = (BackupImageView)view.findViewById(R.id.media_photo_image);
                if (albumEntry.coverPhoto != null && albumEntry.coverPhoto.path != null) {
                    imageView.setImage("thumb://" + albumEntry.coverPhoto.imageId + ":" + albumEntry.coverPhoto.path, null, mContext.getResources().getDrawable(R.drawable.nophotos));
                } else {
                    imageView.setImageResource(R.drawable.nophotos);
                }
                TextView textView = (TextView)view.findViewById(R.id.album_name);
                textView.setText(albumEntry.bucketName);
                if (cameraAlbumId != null && albumEntry.bucketId == cameraAlbumId) {

                } else {

                }
                textView = (TextView)view.findViewById(R.id.album_count);
                textView.setText("" + albumEntry.photos.size());
            } else if (type == 1) {
                if (view == null) {
                    LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = li.inflate(R.layout.photo_picker_photo_layout, viewGroup, false);
                    View checkImageView = view.findViewById(R.id.photo_check_frame);
                    checkImageView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            MediaController.PhotoEntry photoEntry = selectedAlbum.photos.get((Integer)((View)v.getParent()).getTag());
                            if (selectedPhotos.containsKey(photoEntry.imageId)) {
                                selectedPhotos.remove(photoEntry.imageId);
                            } else {
                                selectedPhotos.put(photoEntry.imageId, photoEntry);
                            }
                            updateSelectedPhoto((View)v.getParent(), photoEntry);
                            updateSelectedCount();
                        }
                    });
                }
                ViewGroup.LayoutParams params = view.getLayoutParams();
                params.width = itemWidth;
                params.height = itemWidth;
                view.setLayoutParams(params);

                MediaController.PhotoEntry photoEntry = selectedAlbum.photos.get(i);
                BackupImageView imageView = (BackupImageView)view.findViewById(R.id.media_photo_image);
                imageView.setTag(i);
                view.setTag(i);
                if (photoEntry.path != null) {
                    imageView.setImage("thumb://" + photoEntry.imageId + ":" + photoEntry.path, null, mContext.getResources().getDrawable(R.drawable.nophotos));
                } else {
                    imageView.setImageResource(R.drawable.nophotos);
                }
                updateSelectedPhoto(view, photoEntry);
                boolean showing = PhotoViewer.getInstance().isShowingImage(photoEntry.path);
                imageView.imageReceiver.setVisible(!showing, false);
                View frameView = view.findViewById(R.id.photo_frame);
                frameView.setVisibility(showing ? View.GONE : View.VISIBLE);
                ImageView checkImageView = (ImageView)view.findViewById(R.id.photo_check);
                checkImageView.setVisibility(showing ? View.GONE : View.VISIBLE);
            }
            return view;
        }

        @Override
        public int getItemViewType(int i) {
            if (selectedAlbum != null) {
                return 1;
            }
            return 0;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public boolean isEmpty() {
            if (selectedAlbum != null) {
                return selectedAlbum.photos.isEmpty();
            }
            return albumsSorted == null || albumsSorted.isEmpty();
        }
    }
}
