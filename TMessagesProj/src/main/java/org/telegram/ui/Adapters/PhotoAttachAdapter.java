/*
 * This is the source code of Telegram for Android v. 2.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2015.
 */

package org.telegram.ui.Adapters;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.android.MediaController;
import org.telegram.android.NotificationCenter;
import org.telegram.android.support.widget.RecyclerView;
import org.telegram.ui.Cells.PhotoAttachPhotoCell;

import java.util.HashMap;

public class PhotoAttachAdapter extends RecyclerView.Adapter implements NotificationCenter.NotificationCenterDelegate {

    private Context mContext;
    private PhotoAttachAdapterDelegate delegate;
    private HashMap<Integer, MediaController.PhotoEntry> selectedPhotos = new HashMap<>();

    public interface PhotoAttachAdapterDelegate {
        void selectedPhotosChanged();
    }

    private class Holder extends RecyclerView.ViewHolder {

        public Holder(View itemView) {
            super(itemView);
        }
    }

    public PhotoAttachAdapter(Context context) {
        mContext = context;
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.albumsDidLoaded);
        if (MediaController.allPhotosAlbumEntry == null) {
            MediaController.loadGalleryPhotosAlbums(0);
        }
    }

    public void onDestroy() {
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.albumsDidLoaded);
    }

    public void clearSelectedPhotos() {
        if (!selectedPhotos.isEmpty()) {
            selectedPhotos.clear();
            delegate.selectedPhotosChanged();
            notifyDataSetChanged();
        }
    }

    public HashMap<Integer, MediaController.PhotoEntry> getSelectedPhotos() {
        return selectedPhotos;
    }

    public void setDelegate(PhotoAttachAdapterDelegate photoAttachAdapterDelegate) {
        delegate = photoAttachAdapterDelegate;
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.albumsDidLoaded) {
            notifyDataSetChanged();
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        //if (position != 0) {
            PhotoAttachPhotoCell cell = (PhotoAttachPhotoCell) holder.itemView;
            MediaController.PhotoEntry photoEntry = MediaController.allPhotosAlbumEntry.photos.get(position/* - 1*/);
            cell.setPhotoEntry(photoEntry, position == MediaController.allPhotosAlbumEntry.photos.size());
            cell.setChecked(selectedPhotos.containsKey(photoEntry.imageId), false);
        //}
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view;
        /*if (viewType == 0) {
            view = new PhotoAttachCameraCell(mContext);
        } else {*/
            PhotoAttachPhotoCell cell = new PhotoAttachPhotoCell(mContext);
            cell.setOnCheckClickLisnener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    PhotoAttachPhotoCell cell = (PhotoAttachPhotoCell) v.getParent();
                    MediaController.PhotoEntry photoEntry = cell.getPhotoEntry();
                    if (selectedPhotos.containsKey(photoEntry.imageId)) {
                        selectedPhotos.remove(photoEntry.imageId);
                        cell.setChecked(false, true);
                    } else {
                        selectedPhotos.put(photoEntry.imageId, photoEntry);
                        cell.setChecked(true, true);
                    }
                    delegate.selectedPhotosChanged();
                }
            });
            view = cell;
        //}
        return new Holder(view);
    }

    @Override
    public int getItemCount() {
        return /*1 + */(MediaController.allPhotosAlbumEntry != null ? MediaController.allPhotosAlbumEntry.photos.size() : 0);
    }

    @Override
    public int getItemViewType(int position) {
        //if (position == 0) {
            return 0;
        //}
        //return 1;
    }
}
