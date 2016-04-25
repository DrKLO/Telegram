/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Components;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AnimationCompat.ViewProxy;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.support.widget.LinearLayoutManager;
import org.telegram.messenger.R;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.PhotoAttachPhotoCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.PhotoViewer;

import java.util.ArrayList;
import java.util.HashMap;

public class ChatAttachView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate, PhotoViewer.PhotoViewerProvider {

    public interface ChatAttachViewDelegate {
        void didPressedButton(int button);
    }

    private LinearLayoutManager attachPhotoLayoutManager;
    private PhotoAttachAdapter photoAttachAdapter;
    private ChatActivity baseFragment;
    private AttachButton sendPhotosButton;
    private View views[] = new View[20];
    private RecyclerListView attachPhotoRecyclerView;
    private View lineView;
    private EmptyTextProgressView progressView;
    private ArrayList<PhotoAttachAdapter.Holder> viewsCache = new ArrayList<>(8);

    private float[] distCache = new float[20];

    private DecelerateInterpolator decelerateInterpolator = new DecelerateInterpolator();

    private boolean loading = true;

    private ChatAttachViewDelegate delegate;

    private static class AttachButton extends FrameLayout {

        private TextView textView;
        private ImageView imageView;

        public AttachButton(Context context) {
            super(context);

            imageView = new ImageView(context);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            addView(imageView, LayoutHelper.createFrame(64, 64, Gravity.CENTER_HORIZONTAL | Gravity.TOP));

            textView = new TextView(context);
            textView.setLines(1);
            textView.setSingleLine(true);
            textView.setGravity(Gravity.CENTER_HORIZONTAL);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setTextColor(0xff757575);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 64, 0, 0));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(85), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(90), MeasureSpec.EXACTLY));
        }

        public void setTextAndIcon(CharSequence text, Drawable drawable) {
            textView.setText(text);
            imageView.setBackgroundDrawable(drawable);
        }
    }

    public ChatAttachView(Context context) {
        super(context);

        NotificationCenter.getInstance().addObserver(this, NotificationCenter.albumsDidLoaded);

        views[8] = attachPhotoRecyclerView = new RecyclerListView(context);
        attachPhotoRecyclerView.setVerticalScrollBarEnabled(true);
        attachPhotoRecyclerView.setAdapter(photoAttachAdapter = new PhotoAttachAdapter(context));
        attachPhotoRecyclerView.setClipToPadding(false);
        attachPhotoRecyclerView.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), 0);
        attachPhotoRecyclerView.setItemAnimator(null);
        attachPhotoRecyclerView.setLayoutAnimation(null);
        if (Build.VERSION.SDK_INT >= 9) {
            attachPhotoRecyclerView.setOverScrollMode(RecyclerListView.OVER_SCROLL_NEVER);
        }
        addView(attachPhotoRecyclerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 80));
        attachPhotoLayoutManager = new LinearLayoutManager(context) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }
        };
        attachPhotoLayoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
        attachPhotoRecyclerView.setLayoutManager(attachPhotoLayoutManager);
        attachPhotoRecyclerView.setOnItemClickListener(new RecyclerListView.OnItemClickListener() {
            @SuppressWarnings("unchecked")
            @Override
            public void onItemClick(View view, int position) {
                if (baseFragment == null || baseFragment.getParentActivity() == null) {
                    return;
                }
                ArrayList<Object> arrayList = (ArrayList) MediaController.allPhotosAlbumEntry.photos;
                if (position < 0 || position >= arrayList.size()) {
                    return;
                }
                PhotoViewer.getInstance().setParentActivity(baseFragment.getParentActivity());
                PhotoViewer.getInstance().openPhotoForSelect(arrayList, position, 0, ChatAttachView.this, baseFragment);
                AndroidUtilities.hideKeyboard(baseFragment.getFragmentView().findFocus());
            }
        });

        views[9] = progressView = new EmptyTextProgressView(context);
        if (Build.VERSION.SDK_INT >= 23 && getContext().checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            progressView.setText(LocaleController.getString("PermissionStorage", R.string.PermissionStorage));
            progressView.setTextSize(16);
        } else {
            progressView.setText(LocaleController.getString("NoPhotos", R.string.NoPhotos));
            progressView.setTextSize(20);
        }
        addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 80));
        attachPhotoRecyclerView.setEmptyView(progressView);

        views[10] = lineView = new View(getContext());
        lineView.setBackgroundColor(0xffd2d2d2);
        addView(lineView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1, Gravity.TOP | Gravity.LEFT));
        CharSequence[] items = new CharSequence[]{
                LocaleController.getString("ChatCamera", R.string.ChatCamera),
                LocaleController.getString("ChatGallery", R.string.ChatGallery),
                LocaleController.getString("ChatVideo", R.string.ChatVideo),
                LocaleController.getString("AttachMusic", R.string.AttachMusic),
                LocaleController.getString("ChatDocument", R.string.ChatDocument),
                LocaleController.getString("AttachContact", R.string.AttachContact),
                LocaleController.getString("ChatLocation", R.string.ChatLocation),
                ""
        };
        for (int a = 0; a < 8; a++) {
            AttachButton attachButton = new AttachButton(context);
            attachButton.setTextAndIcon(items[a], Theme.attachButtonDrawables[a]);
            addView(attachButton, LayoutHelper.createFrame(85, 90, Gravity.LEFT | Gravity.TOP));
            attachButton.setTag(a);
            views[a] = attachButton;
            if (a == 7) {
                sendPhotosButton = attachButton;
                sendPhotosButton.imageView.setPadding(0, AndroidUtilities.dp(4), 0, 0);
            }
            attachButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (delegate != null) {
                        delegate.didPressedButton((Integer) v.getTag());
                    }
                }
            });
        }
        setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        for (int a = 0; a < 8; a++) {
            viewsCache.add(photoAttachAdapter.createHolder());
        }

        if (loading) {
            progressView.showProgress();
        } else {
            progressView.showTextView();
        }
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.albumsDidLoaded) {
            if (photoAttachAdapter != null) {
                loading = false;
                progressView.showTextView();
                photoAttachAdapter.notifyDataSetChanged();
            }
        }
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(294), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left;

        int t = AndroidUtilities.dp(8);
        attachPhotoRecyclerView.layout(0, t, width, t + attachPhotoRecyclerView.getMeasuredHeight());
        progressView.layout(0, t, width, t + progressView.getMeasuredHeight());
        lineView.layout(0, AndroidUtilities.dp(96), width, AndroidUtilities.dp(96) + lineView.getMeasuredHeight());

        int diff = (width - AndroidUtilities.dp(85 * 4 + 20)) / 3;
        for (int a = 0; a < 8; a++) {
            int y = AndroidUtilities.dp(105 + 95 * (a / 4));
            int x = AndroidUtilities.dp(10) + (a % 4) * (AndroidUtilities.dp(85) + diff);
            views[a].layout(x, y, x + views[a].getMeasuredWidth(), y + views[a].getMeasuredHeight());
        }
    }

    public void updatePhotosButton() {
        int count = photoAttachAdapter.getSelectedPhotos().size();
        if (count == 0) {
            sendPhotosButton.imageView.setPadding(0, AndroidUtilities.dp(4), 0, 0);
            sendPhotosButton.imageView.setBackgroundResource(R.drawable.attach_hide_states);
            sendPhotosButton.imageView.setImageResource(R.drawable.attach_hide2);
            sendPhotosButton.textView.setText("");
        } else {
            sendPhotosButton.imageView.setPadding(AndroidUtilities.dp(2), 0, 0, 0);
            sendPhotosButton.imageView.setBackgroundResource(R.drawable.attach_send_states);
            sendPhotosButton.imageView.setImageResource(R.drawable.attach_send2);
            sendPhotosButton.textView.setText(LocaleController.formatString("SendItems", R.string.SendItems, String.format("(%d)", count)));
        }

        if (Build.VERSION.SDK_INT >= 23 && getContext().checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            progressView.setText(LocaleController.getString("PermissionStorage", R.string.PermissionStorage));
            progressView.setTextSize(16);
        } else {
            progressView.setText(LocaleController.getString("NoPhotos", R.string.NoPhotos));
            progressView.setTextSize(20);
        }
    }

    public void setDelegate(ChatAttachViewDelegate chatAttachViewDelegate) {
        delegate = chatAttachViewDelegate;
    }

    public void onRevealAnimationEnd(boolean open) {
        if (open && Build.VERSION.SDK_INT <= 19 && MediaController.allPhotosAlbumEntry == null) {
            MediaController.loadGalleryPhotosAlbums(0);
        }
    }

    public void loadGalleryPhotos() {
        if (MediaController.allPhotosAlbumEntry == null && Build.VERSION.SDK_INT >= 21) {
            MediaController.loadGalleryPhotosAlbums(0);
        }
    }

    @SuppressLint("NewApi")
    public void onRevealAnimationStart(boolean open) {
        if (!open) {
            return;
        }
        int count = Build.VERSION.SDK_INT <= 19 ? 11 : 8;
        for (int a = 0; a < count; a++) {
            if (Build.VERSION.SDK_INT <= 19) {
                if (a < 8) {
                    views[a].setScaleX(0.1f);
                    views[a].setScaleY(0.1f);
                }
                views[a].setAlpha(0.0f);
            } else {
                views[a].setScaleX(0.7f);
                views[a].setScaleY(0.7f);
            }
            views[a].setTag(R.string.AppName, null);
            distCache[a] = 0;
        }
    }

    @SuppressLint("NewApi")
    public void onRevealAnimationProgress(boolean open, float radius, int x, int y) {
        if (!open) {
            return;
        }
        int count = Build.VERSION.SDK_INT <= 19 ? 11 : 8;
        for (int a = 0; a < count; a++) {
            if (views[a].getTag(R.string.AppName) == null) {
                if (distCache[a] == 0) {
                    int buttonX = views[a].getLeft() + views[a].getMeasuredWidth() / 2;
                    int buttonY = views[a].getTop() + views[a].getMeasuredHeight() / 2;
                    distCache[a] = (float) Math.sqrt((x - buttonX) * (x - buttonX) + (y - buttonY) * (y - buttonY));
                    float vecX = (x - buttonX) / distCache[a];
                    float vecY = (y - buttonY) / distCache[a];
                    views[a].setPivotX(views[a].getMeasuredWidth() / 2 + vecX * AndroidUtilities.dp(20));
                    views[a].setPivotY(views[a].getMeasuredHeight() / 2 + vecY * AndroidUtilities.dp(20));
                }
                if (distCache[a] > radius + AndroidUtilities.dp(27)) {
                    continue;
                }

                views[a].setTag(R.string.AppName, 1);
                final ArrayList<Animator> animators = new ArrayList<>();
                final ArrayList<Animator> animators2 = new ArrayList<>();
                if (a < 8) {
                    animators.add(ObjectAnimator.ofFloat(views[a], "scaleX", 0.7f, 1.05f));
                    animators.add(ObjectAnimator.ofFloat(views[a], "scaleY", 0.7f, 1.05f));
                    animators2.add(ObjectAnimator.ofFloat(views[a], "scaleX", 1.0f));
                    animators2.add(ObjectAnimator.ofFloat(views[a], "scaleY", 1.0f));
                }
                if (Build.VERSION.SDK_INT <= 19) {
                    animators.add(ObjectAnimator.ofFloat(views[a], "alpha", 1.0f));
                }
                AnimatorSet animatorSet = new AnimatorSet();
                animatorSet.playTogether(animators);
                animatorSet.setDuration(150);
                animatorSet.setInterpolator(decelerateInterpolator);
                animatorSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        AnimatorSet animatorSet = new AnimatorSet();
                        animatorSet.playTogether(animators2);
                        animatorSet.setDuration(100);
                        animatorSet.setInterpolator(decelerateInterpolator);
                        animatorSet.start();
                    }
                });
                animatorSet.start();
            }
        }
    }

    public void init(ChatActivity parentFragment) {
        if (MediaController.allPhotosAlbumEntry != null) {
            for (int a = 0; a < Math.min(100, MediaController.allPhotosAlbumEntry.photos.size()); a++) {
                MediaController.PhotoEntry photoEntry = MediaController.allPhotosAlbumEntry.photos.get(a);
                photoEntry.caption = null;
                photoEntry.imagePath = null;
                photoEntry.thumbPath = null;
            }
        }
        attachPhotoLayoutManager.scrollToPositionWithOffset(0, 1000000);
        photoAttachAdapter.clearSelectedPhotos();
        baseFragment = parentFragment;
        updatePhotosButton();
    }

    public HashMap<Integer, MediaController.PhotoEntry> getSelectedPhotos() {
        return photoAttachAdapter.getSelectedPhotos();
    }

    public void onDestroy() {
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.albumsDidLoaded);
        baseFragment = null;
    }

    private PhotoAttachPhotoCell getCellForIndex(int index) {
        int count = attachPhotoRecyclerView.getChildCount();
        for (int a = 0; a < count; a++) {
            View view = attachPhotoRecyclerView.getChildAt(a);
            if (view instanceof PhotoAttachPhotoCell) {
                PhotoAttachPhotoCell cell = (PhotoAttachPhotoCell) view;
                int num = (Integer) cell.getImageView().getTag();
                if (num < 0 || num >= MediaController.allPhotosAlbumEntry.photos.size()) {
                    continue;
                }
                if (num == index) {
                    return cell;
                }
            }
        }
        return null;
    }

    @Override
    public PhotoViewer.PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) {
        PhotoAttachPhotoCell cell = getCellForIndex(index);
        if (cell != null) {
            int coords[] = new int[2];
            cell.getImageView().getLocationInWindow(coords);
            PhotoViewer.PlaceProviderObject object = new PhotoViewer.PlaceProviderObject();
            object.viewX = coords[0];
            object.viewY = coords[1] - (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
            object.parentView = attachPhotoRecyclerView;
            object.imageReceiver = cell.getImageView().getImageReceiver();
            object.thumb = object.imageReceiver.getBitmap();
            object.scale = ViewProxy.getScaleX(cell.getImageView());
            object.clipBottomAddition = (Build.VERSION.SDK_INT >= 21 ? 0 : -AndroidUtilities.statusBarHeight);
            cell.getCheckBox().setVisibility(View.GONE);
            return object;
        }
        return null;
    }

    @Override
    public void updatePhotoAtIndex(int index) {
        PhotoAttachPhotoCell cell = getCellForIndex(index);
        if (cell != null) {
            cell.getImageView().setOrientation(0, true);
            MediaController.PhotoEntry photoEntry = MediaController.allPhotosAlbumEntry.photos.get(index);
            if (photoEntry.thumbPath != null) {
                cell.getImageView().setImage(photoEntry.thumbPath, null, cell.getContext().getResources().getDrawable(R.drawable.nophotos));
            } else if (photoEntry.path != null) {
                cell.getImageView().setOrientation(photoEntry.orientation, true);
                cell.getImageView().setImage("thumb://" + photoEntry.imageId + ":" + photoEntry.path, null, cell.getContext().getResources().getDrawable(R.drawable.nophotos));
            } else {
                cell.getImageView().setImageResource(R.drawable.nophotos);
            }
        }
    }

    @Override
    public Bitmap getThumbForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) {
        PhotoAttachPhotoCell cell = getCellForIndex(index);
        if (cell != null) {
            return cell.getImageView().getImageReceiver().getBitmap();
        }
        return null;
    }

    @Override
    public void willSwitchFromPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index) {
        PhotoAttachPhotoCell cell = getCellForIndex(index);
        if (cell != null) {
            cell.getCheckBox().setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void willHidePhotoViewer() {
        int count = attachPhotoRecyclerView.getChildCount();
        for (int a = 0; a < count; a++) {
            View view = attachPhotoRecyclerView.getChildAt(a);
            if (view instanceof PhotoAttachPhotoCell) {
                PhotoAttachPhotoCell cell = (PhotoAttachPhotoCell) view;
                if (cell.getCheckBox().getVisibility() != VISIBLE) {
                    cell.getCheckBox().setVisibility(VISIBLE);
                }
            }
        }
    }

    @Override
    public boolean isPhotoChecked(int index) {
        return !(index < 0 || index >= MediaController.allPhotosAlbumEntry.photos.size()) && photoAttachAdapter.getSelectedPhotos().containsKey(MediaController.allPhotosAlbumEntry.photos.get(index).imageId);
    }

    @Override
    public void setPhotoChecked(int index) {
        boolean add = true;
        if (index < 0 || index >= MediaController.allPhotosAlbumEntry.photos.size()) {
            return;
        }
        MediaController.PhotoEntry photoEntry = MediaController.allPhotosAlbumEntry.photos.get(index);
        if (photoAttachAdapter.getSelectedPhotos().containsKey(photoEntry.imageId)) {
            photoAttachAdapter.getSelectedPhotos().remove(photoEntry.imageId);
            add = false;
        } else {
            photoAttachAdapter.getSelectedPhotos().put(photoEntry.imageId, photoEntry);
        }
        int count = attachPhotoRecyclerView.getChildCount();
        for (int a = 0; a < count; a++) {
            View view = attachPhotoRecyclerView.getChildAt(a);
            int num = (Integer) view.getTag();
            if (num == index) {
                ((PhotoAttachPhotoCell) view).setChecked(add, false);
                break;
            }
        }
        updatePhotosButton();
    }

    @Override
    public boolean cancelButtonPressed() {
        return false;
    }

    @Override
    public void sendButtonPressed(int index) {
        if (photoAttachAdapter.getSelectedPhotos().isEmpty()) {
            if (index < 0 || index >= MediaController.allPhotosAlbumEntry.photos.size()) {
                return;
            }
            MediaController.PhotoEntry photoEntry = MediaController.allPhotosAlbumEntry.photos.get(index);
            photoAttachAdapter.getSelectedPhotos().put(photoEntry.imageId, photoEntry);
        }
        delegate.didPressedButton(7);
    }

    @Override
    public int getSelectedCount() {
        return photoAttachAdapter.getSelectedPhotos().size();
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    public class PhotoAttachAdapter extends RecyclerView.Adapter {

        private Context mContext;
        private HashMap<Integer, MediaController.PhotoEntry> selectedPhotos = new HashMap<>();

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
                for (HashMap.Entry<Integer, MediaController.PhotoEntry> entry : selectedPhotos.entrySet()) {
                    MediaController.PhotoEntry photoEntry = entry.getValue();
                    photoEntry.imagePath = null;
                    photoEntry.thumbPath = null;
                    photoEntry.caption = null;
                }
                selectedPhotos.clear();
                updatePhotosButton();
                notifyDataSetChanged();
            }
        }

        public Holder createHolder() {
            PhotoAttachPhotoCell cell = new PhotoAttachPhotoCell(mContext);
            cell.setDelegate(new PhotoAttachPhotoCell.PhotoAttachPhotoCellDelegate() {
                @Override
                public void onCheckClick(PhotoAttachPhotoCell v) {
                    MediaController.PhotoEntry photoEntry = v.getPhotoEntry();
                    if (selectedPhotos.containsKey(photoEntry.imageId)) {
                        selectedPhotos.remove(photoEntry.imageId);
                        v.setChecked(false, true);
                        photoEntry.imagePath = null;
                        photoEntry.thumbPath = null;
                        v.setPhotoEntry(photoEntry, (Integer) v.getTag() == MediaController.allPhotosAlbumEntry.photos.size() - 1);
                    } else {
                        selectedPhotos.put(photoEntry.imageId, photoEntry);
                        v.setChecked(true, true);
                    }
                    updatePhotosButton();
                }
            });
            return new Holder(cell);
        }

        public HashMap<Integer, MediaController.PhotoEntry> getSelectedPhotos() {
            return selectedPhotos;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            PhotoAttachPhotoCell cell = (PhotoAttachPhotoCell) holder.itemView;
            MediaController.PhotoEntry photoEntry = MediaController.allPhotosAlbumEntry.photos.get(position);
            cell.setPhotoEntry(photoEntry, position == MediaController.allPhotosAlbumEntry.photos.size() - 1);
            cell.setChecked(selectedPhotos.containsKey(photoEntry.imageId), false);
            cell.getImageView().setTag(position);
            cell.setTag(position);
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            Holder holder;
            if (!viewsCache.isEmpty()) {
                holder = viewsCache.get(0);
                viewsCache.remove(0);
            } else {
                holder = createHolder();
            }
            return holder;
        }

        @Override
        public int getItemCount() {
            return (MediaController.allPhotosAlbumEntry != null ? MediaController.allPhotosAlbumEntry.photos.size() : 0);
        }

        @Override
        public int getItemViewType(int position) {
            return 0;
        }
    }
}
