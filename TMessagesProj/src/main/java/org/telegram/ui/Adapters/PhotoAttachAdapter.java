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

import org.telegram.messenger.MediaController;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.ui.Cells.PhotoAttachPhotoCell;

import java.util.HashMap;

public class PhotoAttachAdapter extends RecyclerView.Adapter {

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
            /*cell.setOnCheckClickLisnener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onItemClick((PhotoAttachPhotoCell) v.getParent());
                }
            });
            view = cell;*/
        //}
        return new Holder(cell);
    }

    public void onItemClick(PhotoAttachPhotoCell cell) {
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
