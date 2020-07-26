package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ProfileActivity;

import java.util.ArrayList;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

public class ProfileGalleryView extends CircularViewPager implements NotificationCenter.NotificationCenterDelegate {

    private final Callback callback;
    private final PointF downPoint = new PointF();
    private final int touchSlop;
    private boolean isScrollingListView = true;
    private boolean isSwipingViewPager = true;
    private final RecyclerListView parentListView;
    private final ViewPagerAdapter adapter;
    private final int parentClassGuid;
    private final long dialogId;
    private TLRPC.ChatFull chatInfo;

    private boolean scrolledByUser;
    private boolean isDownReleased;

    private int currentAccount = UserConfig.selectedAccount;

    private ImageLocation prevImageLocation;
    private ArrayList<String> videoFileNames = new ArrayList<>();
    private ArrayList<String> thumbsFileNames = new ArrayList<>();
    private ArrayList<TLRPC.Photo> photos = new ArrayList<>();
    private ArrayList<ImageLocation> videoLocations = new ArrayList<>();
    private ArrayList<ImageLocation> imagesLocations = new ArrayList<>();
    private ArrayList<ImageLocation> thumbsLocations = new ArrayList<>();
    private ArrayList<Integer> imagesLocationsSizes = new ArrayList<>();

    private int settingMainPhoto;

    private final SparseArray<RadialProgress2> radialProgresses = new SparseArray<>();

    private OnPageChangeListener onPageChangeListener;

    private static class Item {
        private BackupImageView imageView;
    }

    public interface Callback {
        void onDown(boolean left);
        void onRelease();
        void onPhotosLoaded();
        void onVideoSet();
    }

    public ProfileGalleryView(Context context, long dialogId, ActionBar parentActionBar, RecyclerListView parentListView, ProfileActivity.AvatarImageView parentAvatarImageView, int parentClassGuid, Callback callback) {
        super(context);
        setVisibility(View.GONE);
        setOverScrollMode(View.OVER_SCROLL_NEVER);
        setOffscreenPageLimit(2);

        this.dialogId = dialogId;
        this.parentListView = parentListView;
        this.parentClassGuid = parentClassGuid;
        setAdapter(adapter = new ViewPagerAdapter(context, parentAvatarImageView, parentActionBar));
        this.touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        this.callback = callback;

        addOnPageChangeListener(new OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                if (positionOffsetPixels == 0) {
                    position = adapter.getRealPosition(position);
                    BackupImageView currentView = getCurrentItemView();
                    int count = getChildCount();
                    for (int a = 0; a < count; a++) {
                        View child = getChildAt(a);
                        if (!(child instanceof BackupImageView)) {
                            continue;
                        }
                        int p = adapter.getRealPosition(adapter.imageViews.indexOf(child));
                        BackupImageView imageView = (BackupImageView) child;
                        ImageReceiver imageReceiver = imageView.getImageReceiver();
                        boolean currentAllow = imageReceiver.getAllowStartAnimation();
                        if (p == position) {
                            if (!currentAllow) {
                                imageReceiver.setAllowStartAnimation(true);
                                imageReceiver.startAnimation();
                            }
                            ImageLocation location = videoLocations.get(p);
                            if (location != null) {
                                FileLoader.getInstance(currentAccount).setForceStreamLoadingFile(location.location, "mp4");
                            }
                        } else {
                            if (currentAllow) {
                                AnimatedFileDrawable fileDrawable = imageReceiver.getAnimation();
                                if (fileDrawable != null) {
                                    ImageLocation location = videoLocations.get(p);
                                    if (location != null) {
                                        fileDrawable.seekTo(location.videoSeekTo, false, true);
                                    }
                                }
                                imageReceiver.setAllowStartAnimation(false);
                                imageReceiver.stopAnimation();
                            }
                        }
                    }
                }
            }

            @Override
            public void onPageSelected(int position) {

            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });

        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.dialogPhotosLoaded);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.FileLoadProgressChanged);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.reloadDialogPhotos);
        MessagesController.getInstance(currentAccount).loadDialogPhotos((int) dialogId, 80, 0, true, parentClassGuid);
    }

    public void onDestroy() {
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.dialogPhotosLoaded);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.FileLoadProgressChanged);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.reloadDialogPhotos);
        int count = getChildCount();
        for (int a = 0; a < count; a++) {
            View child = getChildAt(a);
            if (!(child instanceof BackupImageView)) {
                continue;
            }
            BackupImageView imageView = (BackupImageView) child;
            if (imageView.getImageReceiver().hasStaticThumb()) {
                Drawable drawable = imageView.getImageReceiver().getDrawable();
                if (drawable instanceof AnimatedFileDrawable) {
                    ((AnimatedFileDrawable) drawable).removeSecondParentView(imageView);
                }
            }
        }
    }

    public void setAnimatedFileMaybe(AnimatedFileDrawable drawable) {
        if (drawable == null) {
            return;
        }
        int count = getChildCount();
        for (int a = 0; a < count; a++) {
            View child = getChildAt(a);
            if (!(child instanceof BackupImageView)) {
                continue;
            }
            int p = adapter.getRealPosition(adapter.imageViews.indexOf(child));
            if (p != 0) {
                continue;
            }
            BackupImageView imageView = (BackupImageView) child;
            ImageReceiver imageReceiver = imageView.getImageReceiver();
            AnimatedFileDrawable currentDrawable = imageReceiver.getAnimation();
            if (currentDrawable == drawable) {
                continue;
            }
            if (currentDrawable != null) {
                currentDrawable.removeSecondParentView(imageView);
            }
            imageView.setImageDrawable(drawable);
            drawable.addSecondParentView(this);
            drawable.setInvalidateParentViewWithSecond(true);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (parentListView.getScrollState() != RecyclerView.SCROLL_STATE_IDLE && !isScrollingListView && isSwipingViewPager) {
            isSwipingViewPager = false;
            final MotionEvent cancelEvent = MotionEvent.obtain(ev);
            cancelEvent.setAction(MotionEvent.ACTION_CANCEL);
            super.onTouchEvent(cancelEvent);
            cancelEvent.recycle();
            return false;
        }

        final int action = ev.getAction();
        if (action == MotionEvent.ACTION_DOWN) {
            isScrollingListView = true;
            isSwipingViewPager = true;
            scrolledByUser = true;
            downPoint.set(ev.getX(), ev.getY());
            if (adapter.getCount() > 1) {
                callback.onDown(ev.getX() < getWidth() / 3f);
            }
            isDownReleased = false;
        } else if (action == MotionEvent.ACTION_UP) {
            if (!isDownReleased) {
                final int itemsCount = adapter.getCount();
                int currentItem = getCurrentItem();
                if (itemsCount > 1) {
                    if (ev.getX() > getWidth() / 3f) {
                        final int extraCount = adapter.getExtraCount();
                        if (++currentItem >= itemsCount - extraCount) {
                            currentItem = extraCount;
                        }
                    } else {
                        final int extraCount = adapter.getExtraCount();
                        if (--currentItem < extraCount) {
                            currentItem = itemsCount - extraCount - 1;
                        }
                    }
                    callback.onRelease();
                    setCurrentItem(currentItem, false);
                }
            }
        } else if (action == MotionEvent.ACTION_MOVE) {
            final float dx = ev.getX() - downPoint.x;
            final float dy = ev.getY() - downPoint.y;
            boolean move = Math.abs(dy) >= touchSlop || Math.abs(dx) >= touchSlop;
            if (move) {
                isDownReleased = true;
                callback.onRelease();
            }
            if (isSwipingViewPager && isScrollingListView) {
                if (move) {
                    if (Math.abs(dy) > Math.abs(dx)) {
                        isSwipingViewPager = false;
                        final MotionEvent cancelEvent = MotionEvent.obtain(ev);
                        cancelEvent.setAction(MotionEvent.ACTION_CANCEL);
                        super.onTouchEvent(cancelEvent);
                        cancelEvent.recycle();
                    } else {
                        isScrollingListView = false;
                        final MotionEvent cancelEvent = MotionEvent.obtain(ev);
                        cancelEvent.setAction(MotionEvent.ACTION_CANCEL);
                        parentListView.onTouchEvent(cancelEvent);
                        cancelEvent.recycle();
                    }
                }
            } else if (isSwipingViewPager && !canScrollHorizontally(-1) && dx > touchSlop) {
                return false;
            }
        }

        boolean result = false;

        if (isScrollingListView) {
            result |= parentListView.onTouchEvent(ev);
        }

        if (isSwipingViewPager) {
            result |= super.onTouchEvent(ev);
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            isScrollingListView = false;
            isSwipingViewPager = false;
        }

        return result;
    }

    public void setChatInfo(TLRPC.ChatFull chatFull) {
        chatInfo = chatFull;
        if (!photos.isEmpty() && photos.get(0) == null && chatInfo != null && FileLoader.isSamePhoto(imagesLocations.get(0).location, chatInfo.chat_photo)) {
            photos.set(0, chatInfo.chat_photo);
            if (!chatInfo.chat_photo.video_sizes.isEmpty()) {
                final TLRPC.VideoSize videoSize = chatInfo.chat_photo.video_sizes.get(0);
                videoLocations.set(0, ImageLocation.getForPhoto(videoSize, chatInfo.chat_photo));
                videoFileNames.set(0, FileLoader.getAttachFileName(videoSize));
                callback.onPhotosLoaded();
            } else {
                videoLocations.set(0, null);
                videoFileNames.add(0, null);
            }
            adapter.notifyDataSetChanged();
        }
    }

    public boolean initIfEmpty(ImageLocation imageLocation, ImageLocation thumbLocation) {
        if (imageLocation == null || thumbLocation == null || settingMainPhoto != 0) {
            return false;
        }
        if (prevImageLocation == null && imageLocation != null || prevImageLocation != null && prevImageLocation.location.local_id != imageLocation.location.local_id) {
            imagesLocations.clear();
            MessagesController.getInstance(currentAccount).loadDialogPhotos((int) dialogId, 80, 0, true, parentClassGuid);
        }
        if (!imagesLocations.isEmpty()) {
            return false;
        }
        prevImageLocation = imageLocation;
        thumbsFileNames.add(null);
        videoFileNames.add(null);
        imagesLocations.add(imageLocation);
        thumbsLocations.add(thumbLocation);
        videoLocations.add(null);
        photos.add(null);
        imagesLocationsSizes.add(-1);
        getAdapter().notifyDataSetChanged();
        return true;
    }

    public ImageLocation getImageLocation(int index) {
        if (index < 0 || index >= imagesLocations.size()) {
            return null;
        }
        ImageLocation location = videoLocations.get(index);
        if (location != null) {
            return location;
        }
        return imagesLocations.get(index);
    }

    public ImageLocation getRealImageLocation(int index) {
        if (index < 0 || index >= imagesLocations.size()) {
            return null;
        }
        return imagesLocations.get(index);
    }

    public ImageLocation getThumbLocation(int index) {
        if (index < 0 || index >= thumbsLocations.size()) {
            return null;
        }
        return thumbsLocations.get(index);
    }

    public boolean hasImages() {
        return !imagesLocations.isEmpty();
    }

    public BackupImageView getCurrentItemView() {
        if (adapter != null && !adapter.objects.isEmpty()) {
            return adapter.objects.get(getCurrentItem()).imageView;
        } else {
            return null;
        }
    }

    public boolean isLoadingCurrentVideo() {
        if (videoLocations.get(getRealPosition()) == null) {
            return false;
        }
        BackupImageView imageView = getCurrentItemView();
        if (imageView == null) {
            return false;
        }
        AnimatedFileDrawable drawable = imageView.getImageReceiver().getAnimation();
        return drawable == null || !drawable.hasBitmap();
    }

    public float getCurrentItemProgress() {
        BackupImageView imageView = getCurrentItemView();
        if (imageView == null) {
            return 0.0f;
        }
        AnimatedFileDrawable drawable = imageView.getImageReceiver().getAnimation();
        if (drawable == null) {
            return 0.0f;
        }
        return drawable.getCurrentProgress();
    }

    public boolean isCurrentItemVideo() {
        return videoLocations.get(getRealPosition()) != null;
    }

    public ImageLocation getCurrentVideoLocation(ImageLocation thumbLocation, ImageLocation imageLocation) {
        if (thumbLocation == null) {
            return null;
        }
        for (int b = 0; b < 2; b++) {
            ArrayList<ImageLocation> arrayList = b == 0 ? thumbsLocations : imagesLocations;
            for (int a = 0, N = arrayList.size(); a < N; a++) {
                ImageLocation loc = arrayList.get(a);
                if (loc == null || loc.location == null) {
                    continue;
                }
                if (loc.dc_id == thumbLocation.dc_id && loc.location.local_id == thumbLocation.location.local_id && loc.location.volume_id == thumbLocation.location.volume_id ||
                        loc.dc_id == imageLocation.dc_id && loc.location.local_id == imageLocation.location.local_id && loc.location.volume_id == imageLocation.location.volume_id) {
                    return videoLocations.get(a);
                }
            }
        }

        return null;
    }

    public void resetCurrentItem() {
        setCurrentItem(adapter.getExtraCount(), false);
    }

    public int getRealCount() {
        return photos.size();
    }

    public int getRealPosition(int position) {
        return adapter.getRealPosition(position);
    }

    public int getRealPosition() {
        return adapter.getRealPosition(getCurrentItem());
    }

    public TLRPC.Photo getPhoto(int index) {
        if (index < 0 || index >= photos.size()) {
            return null;
        }
        return photos.get(index);
    }

    public void replaceFirstPhoto(TLRPC.Photo oldPhoto, TLRPC.Photo photo) {
        if (photos.isEmpty()) {
            return;
        }
        int idx = photos.indexOf(oldPhoto);
        if (idx < 0) {
            return;
        }
        photos.set(idx, photo);
    }

    public void finishSettingMainPhoto() {
        settingMainPhoto--;
    }

    public void startMovePhotoToBegin(int index) {
        if (index <= 0 || index >= photos.size()) {
            return;
        }
        settingMainPhoto++;
        TLRPC.Photo photo = photos.get(index);
        photos.remove(index);
        photos.add(0, photo);

        String name = thumbsFileNames.get(index);
        thumbsFileNames.remove(index);
        thumbsFileNames.add(0, name);

        videoFileNames.add(0, videoFileNames.remove(index));

        ImageLocation location = videoLocations.get(index);
        videoLocations.remove(index);
        videoLocations.add(0, location);

        location = imagesLocations.get(index);
        imagesLocations.remove(index);
        imagesLocations.add(0, location);

        location = thumbsLocations.get(index);
        thumbsLocations.remove(index);
        thumbsLocations.add(0, location);

        Integer size = imagesLocationsSizes.get(index);
        imagesLocationsSizes.remove(index);
        imagesLocationsSizes.add(0, size);

        prevImageLocation = imagesLocations.get(0);
    }

    public void commitMoveToBegin() {
        adapter.notifyDataSetChanged();
        resetCurrentItem();
    }

    public boolean removePhotoAtIndex(int index) {
        if (index < 0 || index >= photos.size()) {
            return false;
        }
        photos.remove(index);
        thumbsFileNames.remove(index);
        videoFileNames.remove(index);
        videoLocations.remove(index);
        imagesLocations.remove(index);
        thumbsLocations.remove(index);
        imagesLocationsSizes.remove(index);
        radialProgresses.delete(index);
        if (index == 0 && !imagesLocations.isEmpty()) {
            prevImageLocation = imagesLocations.get(0);
        }
        adapter.notifyDataSetChanged();
        return photos.isEmpty();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        if (parentListView.getScrollState() != RecyclerView.SCROLL_STATE_IDLE) {
            return false;
        }
        if (getParent() != null && getParent().getParent() != null) {
            getParent().getParent().requestDisallowInterceptTouchEvent(canScrollHorizontally(-1));
        }
        return super.onInterceptTouchEvent(e);
    }

    private void loadNeighboringThumbs() {
        final int locationsCount = thumbsLocations.size();
        if (locationsCount > 1) {
            for (int i = 0; i < (locationsCount > 2 ? 2 : 1); i++) {
                final int pos = i == 0 ? 1 : locationsCount - 1;
                FileLoader.getInstance(currentAccount).loadFile(thumbsLocations.get(pos), null, null, 0, 1);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.dialogPhotosLoaded) {
            int guid = (Integer) args[3];
            int did = (Integer) args[0];
            if (did == dialogId && parentClassGuid == guid) {
                boolean fromCache = (Boolean) args[2];
                ArrayList<TLRPC.Photo> arrayList = (ArrayList<TLRPC.Photo>) args[4];
                thumbsFileNames.clear();
                videoFileNames.clear();
                imagesLocations.clear();
                videoLocations.clear();
                thumbsLocations.clear();
                photos.clear();
                imagesLocationsSizes.clear();
                ImageLocation currentImageLocation = null;
                if (did < 0) {
                    TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-did);
                    currentImageLocation = ImageLocation.getForChat(chat, true);
                    if (currentImageLocation != null) {
                        imagesLocations.add(currentImageLocation);
                        thumbsLocations.add(ImageLocation.getForChat(chat, false));
                        thumbsFileNames.add(null);
                        if (chatInfo != null && FileLoader.isSamePhoto(currentImageLocation.location, chatInfo.chat_photo)) {
                            photos.add(chatInfo.chat_photo);
                            if (!chatInfo.chat_photo.video_sizes.isEmpty()) {
                                final TLRPC.VideoSize videoSize = chatInfo.chat_photo.video_sizes.get(0);
                                videoLocations.add(ImageLocation.getForPhoto(videoSize, chatInfo.chat_photo));
                                videoFileNames.add(FileLoader.getAttachFileName(videoSize));
                            } else {
                                videoLocations.add(null);
                                videoFileNames.add(null);
                            }
                        } else {
                            photos.add(null);
                            videoFileNames.add(null);
                            videoLocations.add(null);
                        }
                        imagesLocationsSizes.add(-1);
                    }
                }
                for (int a = 0; a < arrayList.size(); a++) {
                    TLRPC.Photo photo = arrayList.get(a);
                    if (photo == null || photo instanceof TLRPC.TL_photoEmpty || photo.sizes == null) {
                        continue;
                    }
                    TLRPC.PhotoSize sizeThumb = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, 50);
                    for (int b = 0, N = photo.sizes.size(); b < N; b++) {
                        TLRPC.PhotoSize photoSize = photo.sizes.get(b);
                        if (photoSize instanceof TLRPC.TL_photoStrippedSize) {
                            sizeThumb = photoSize;
                            break;
                        }
                    }
                    if (currentImageLocation != null) {
                        boolean cont = false;
                        for (int b = 0, N = photo.sizes.size(); b < N; b++) {
                            TLRPC.PhotoSize size = photo.sizes.get(b);
                            if (size.location != null && size.location.local_id == currentImageLocation.location.local_id && size.location.volume_id == currentImageLocation.location.volume_id) {
                                photos.set(0, photo);
                                if (!photo.video_sizes.isEmpty()) {
                                    videoLocations.set(0, ImageLocation.getForPhoto(photo.video_sizes.get(0), photo));
                                }
                                cont = true;
                                break;
                            }
                        }
                        if (cont) {
                            continue;
                        }
                    }
                    TLRPC.PhotoSize sizeFull = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, 640);
                    if (sizeFull != null) {
                        if (photo.dc_id != 0) {
                            sizeFull.location.dc_id = photo.dc_id;
                            sizeFull.location.file_reference = photo.file_reference;
                        }
                        ImageLocation location = ImageLocation.getForPhoto(sizeFull, photo);
                        if (location != null) {
                            imagesLocations.add(location);
                            thumbsFileNames.add(FileLoader.getAttachFileName(sizeThumb instanceof TLRPC.TL_photoStrippedSize ? sizeFull : sizeThumb));
                            thumbsLocations.add(ImageLocation.getForPhoto(sizeThumb, photo));
                            if (!photo.video_sizes.isEmpty()) {
                                final TLRPC.VideoSize videoSize = photo.video_sizes.get(0);
                                videoLocations.add(ImageLocation.getForPhoto(videoSize, photo));
                                videoFileNames.add(FileLoader.getAttachFileName(videoSize));
                            } else {
                                videoLocations.add(null);
                                videoFileNames.add(null);
                            }
                            photos.add(photo);
                            imagesLocationsSizes.add(sizeFull.size);
                        }
                    }
                }
                loadNeighboringThumbs();
                getAdapter().notifyDataSetChanged();
                if (!scrolledByUser) {
                    resetCurrentItem();
                }
                if (fromCache) {
                    MessagesController.getInstance(currentAccount).loadDialogPhotos(did, 80, 0, false, parentClassGuid);
                }
                if (callback != null) {
                    callback.onPhotosLoaded();
                }
            }
        } else if (id == NotificationCenter.fileDidLoad) {
            final String fileName = (String) args[0];
            for (int i = 0; i < thumbsFileNames.size(); i++) {
                String fileName2 = videoFileNames.get(i);
                if (fileName2 == null) {
                    fileName2 = thumbsFileNames.get(i);
                }
                if (fileName2 != null && TextUtils.equals(fileName, fileName2)) {
                    final RadialProgress2 radialProgress = radialProgresses.get(i);
                    if (radialProgress != null) {
                        radialProgress.setProgress(1f, true);
                    }
                }
            }
        } else if (id == NotificationCenter.FileLoadProgressChanged) {
            String fileName = (String) args[0];
            for (int i = 0; i < thumbsFileNames.size(); i++) {
                String fileName2 = videoFileNames.get(i);
                if (fileName2 == null) {
                    fileName2 = thumbsFileNames.get(i);
                }
                if (fileName2 != null && TextUtils.equals(fileName, fileName2)) {
                    final RadialProgress2 radialProgress = radialProgresses.get(i);
                    if (radialProgress != null) {
                        Long loadedSize = (Long) args[1];
                        Long totalSize = (Long) args[2];
                        float progress = Math.min(1f, loadedSize / (float) totalSize);
                        radialProgress.setProgress(progress, true);
                    }
                }
            }
        } else if (id == NotificationCenter.reloadDialogPhotos) {
            if (settingMainPhoto != 0) {
                return;
            }
            MessagesController.getInstance(currentAccount).loadDialogPhotos((int) dialogId, 80, 0, true, parentClassGuid);
        }
    }

    public class ViewPagerAdapter extends Adapter {

        private final ArrayList<Item> objects = new ArrayList<>();
        private final ArrayList<BackupImageView> imageViews = new ArrayList<>();

        private final Context context;
        private final Paint placeholderPaint;
        private final ProfileActivity.AvatarImageView parentAvatarImageView;
        private final ActionBar parentActionBar;

        public ViewPagerAdapter(Context context, ProfileActivity.AvatarImageView parentAvatarImageView, ActionBar parentActionBar) {
            this.context = context;
            this.parentAvatarImageView = parentAvatarImageView;
            this.parentActionBar = parentActionBar;
            placeholderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            placeholderPaint.setColor(Color.BLACK);
        }

        @Override
        public int getCount() {
            return objects.size();
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == ((Item) object).imageView;
        }

        @Override
        public int getItemPosition(Object object) {
            final int idx = objects.indexOf((Item) object);
            return idx == -1 ? POSITION_NONE : idx;
        }

        @Override
        public Item instantiateItem(ViewGroup container, int position) {
            final Item item = objects.get(position);

            if (item.imageView == null) {
                item.imageView = new BackupImageView(context) {

                    private final int radialProgressSize = AndroidUtilities.dp(64f);

                    private RadialProgress2 radialProgress;
                    private ValueAnimator radialProgressHideAnimator;
                    private float radialProgressHideAnimatorStartValue;
                    private long firstDrawTime = -1;
                    private boolean isVideo;

                    {
                        getImageReceiver().setAllowDecodeSingleFrame(true);
                        final int realPosition = getRealPosition(position);
                        boolean needProgress = false;
                        if (realPosition == 0) {
                            Drawable drawable = parentAvatarImageView.getImageReceiver().getDrawable();
                            if (drawable instanceof AnimatedFileDrawable && ((AnimatedFileDrawable) drawable).hasBitmap()) {
                                AnimatedFileDrawable animatedFileDrawable = (AnimatedFileDrawable) drawable;
                                setImageDrawable(drawable);
                                animatedFileDrawable.addSecondParentView(this);
                                animatedFileDrawable.setInvalidateParentViewWithSecond(true);
                            } else {
                                ImageLocation videoLocation = videoLocations.get(realPosition);
                                isVideo = videoLocation != null;
                                needProgress = true;
                                String filter;
                                if (videoLocation != null && videoLocation.imageType == FileLoader.IMAGE_TYPE_ANIMATION) {
                                    filter = ImageLoader.AUTOPLAY_FILTER;
                                } else {
                                    filter = null;
                                }
                                setImageMedia(videoLocations.get(realPosition), filter, imagesLocations.get(realPosition), null, parentAvatarImageView.getImageReceiver().getBitmap(), imagesLocationsSizes.get(realPosition), 1, null);
                            }
                        } else {
                            final ImageLocation videoLocation = videoLocations.get(realPosition);
                            isVideo = videoLocation != null;
                            needProgress = true;
                            ImageLocation location = thumbsLocations.get(realPosition);
                            String filter = location.photoSize instanceof TLRPC.TL_photoStrippedSize ? "b" : null;
                            setImageMedia(videoLocation, null, imagesLocations.get(realPosition), null, thumbsLocations.get(realPosition), filter, null, 0, 1, imagesLocationsSizes.get(realPosition));
                        }
                        if (needProgress) {
                            radialProgress = radialProgresses.get(realPosition);
                            if (radialProgress == null) {
                                radialProgress = new RadialProgress2(this);
                                radialProgress.setOverrideAlpha(0.0f);
                                radialProgress.setIcon(MediaActionDrawable.ICON_EMPTY, false, false);
                                radialProgress.setColors(0x42000000, 0x42000000, Color.WHITE, Color.WHITE);
                                radialProgresses.append(realPosition, radialProgress);
                            }
                            postInvalidateOnAnimation();
                        }
                        getImageReceiver().setDelegate(new ImageReceiver.ImageReceiverDelegate() {
                            @Override
                            public void didSetImage(ImageReceiver imageReceiver, boolean set, boolean thumb, boolean memCache) {

                            }

                            @Override
                            public void onAnimationReady(ImageReceiver imageReceiver) {
                                callback.onVideoSet();
                            }
                        });
                        getImageReceiver().setCrossfadeAlpha((byte) 2);
                    }

                    @Override
                    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
                        super.onSizeChanged(w, h, oldw, oldh);
                        if (radialProgress != null) {
                            int paddingTop = (parentActionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0) + ActionBar.getCurrentActionBarHeight();
                            int paddingBottom = AndroidUtilities.dp2(80f);
                            radialProgress.setProgressRect((w - radialProgressSize) / 2, paddingTop + (h - paddingTop - paddingBottom - radialProgressSize) / 2, (w + radialProgressSize) / 2, paddingTop + (h - paddingTop - paddingBottom + radialProgressSize) / 2);
                        }
                    }

                    @Override
                    protected void onDraw(Canvas canvas) {
                        if (radialProgress != null) {
                            final Drawable drawable = getImageReceiver().getDrawable();
                            if (drawable != null && (!isVideo || (drawable instanceof AnimatedFileDrawable && ((AnimatedFileDrawable) drawable).getDurationMs() > 0))) {
                                if (radialProgressHideAnimator == null) {
                                    long startDelay = 0;
                                    if (radialProgress.getProgress() < 1f) {
                                        radialProgress.setProgress(1f, true);
                                        startDelay = 100;
                                    }
                                    radialProgressHideAnimatorStartValue = radialProgress.getOverrideAlpha();
                                    radialProgressHideAnimator = ValueAnimator.ofFloat(0f, 1f);
                                    radialProgressHideAnimator.setStartDelay(startDelay);
                                    radialProgressHideAnimator.setDuration((long) (radialProgressHideAnimatorStartValue * 250f));
                                    radialProgressHideAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                                    radialProgressHideAnimator.addUpdateListener(anim -> radialProgress.setOverrideAlpha(AndroidUtilities.lerp(radialProgressHideAnimatorStartValue, 0f, anim.getAnimatedFraction())));
                                    radialProgressHideAnimator.addListener(new AnimatorListenerAdapter() {
                                        @Override
                                        public void onAnimationEnd(Animator animation) {
                                            radialProgress = null;
                                            radialProgresses.delete(getRealPosition(position));
                                        }
                                    });
                                    radialProgressHideAnimator.start();
                                }
                            } else {
                                if (firstDrawTime < 0) {
                                    firstDrawTime = System.currentTimeMillis();
                                } else {
                                    final long elapsedTime = System.currentTimeMillis() - firstDrawTime;
                                    final long startDelay = isVideo ? 250 : 750;
                                    final long duration = 250;
                                    if (elapsedTime <= startDelay + duration) {
                                        if (elapsedTime > startDelay) {
                                            radialProgress.setOverrideAlpha(CubicBezierInterpolator.DEFAULT.getInterpolation((elapsedTime - startDelay) / (float) duration));
                                        }
                                    }
                                }
                                postInvalidateOnAnimation();
                            }
                            canvas.drawRect(0, 0, getWidth(), getHeight(), placeholderPaint);
                        }

                        super.onDraw(canvas);

                        if (radialProgress != null && radialProgress.getOverrideAlpha() > 0f) {
                            radialProgress.draw(canvas);
                        }
                    }
                };
                imageViews.set(position, item.imageView);
            }

            if (item.imageView.getParent() == null) {
                container.addView(item.imageView);
            }

            return item;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            BackupImageView imageView = ((Item) object).imageView;
            if (imageView.getImageReceiver().hasStaticThumb()) {
                Drawable drawable = imageView.getImageReceiver().getDrawable();
                if (drawable instanceof AnimatedFileDrawable) {
                    ((AnimatedFileDrawable) drawable).removeSecondParentView(imageView);
                }
            }
            container.removeView(imageView);
        }

        @Nullable
        @Override
        public CharSequence getPageTitle(int position) {
            return (getRealPosition(position) + 1) + "/" + (getCount() - getExtraCount() * 2);
        }

        @Override
        public void notifyDataSetChanged() {
            objects.clear();
            for (int a = 0, N = imagesLocations.size() + getExtraCount() * 2; a < N; a++) {
                objects.add(new Item());
                imageViews.add(null);
            }
            super.notifyDataSetChanged();
        }

        @Override
        public int getExtraCount() {
            final int count = imagesLocations.size();
            if (count >= 2) {
                return getOffscreenPageLimit();
            } else {
                return 0;
            }
        }
    }
}
