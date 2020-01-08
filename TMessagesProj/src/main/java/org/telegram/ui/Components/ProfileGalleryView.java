package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.util.SparseArray;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.CircularViewPager;
import org.telegram.ui.ProfileActivity;

import java.util.ArrayList;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

public class ProfileGalleryView extends CircularViewPager implements NotificationCenter.NotificationCenterDelegate {

    private final PointF downPoint = new PointF();
    private final int touchSlop;
    private boolean isScrollingListView = true;
    private boolean isSwipingViewPager = true;
    private final GestureDetector gestureDetector;
    private final RecyclerListView parentListView;
    private final ViewPagerAdapter adapter;
    private final int parentClassGuid;
    private final long dialogId;

    private int currentAccount = UserConfig.selectedAccount;

    private ImageLocation prevImageLocation;
    private ArrayList<String> thumbsFileNames = new ArrayList<>();
    private ArrayList<ImageLocation> imagesLocations = new ArrayList<>();
    private ArrayList<ImageLocation> thumbsLocations = new ArrayList<>();
    private ArrayList<Integer> imagesLocationsSizes = new ArrayList<>();

    private final SparseArray<RadialProgress2> radialProgresses = new SparseArray<>();

    private class Item {
        private BackupImageView imageView;
    }

    public ProfileGalleryView(Context context, long dialogId, ActionBar parentActionBar, RecyclerListView parentListView, ProfileActivity.AvatarImageView parentAvatarImageView, int parentClassGuid) {
        super(context);
        setVisibility(View.GONE);
        setOverScrollMode(View.OVER_SCROLL_NEVER);
        setOffscreenPageLimit(2);

        this.dialogId = dialogId;
        this.parentListView = parentListView;
        this.parentClassGuid = parentClassGuid;
        setAdapter(adapter = new ViewPagerAdapter(context, parentAvatarImageView, parentActionBar));

        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        gestureDetector = new GestureDetector(context, new GestureDetector.OnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return false;
            }

            @Override
            public void onShowPress(MotionEvent e) {
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                final int itemsCount = adapter.getCount();
                int currentItem = getCurrentItem();
                if (itemsCount > 1) {
                    if (e.getX() > getWidth() / 3) {
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
                    setCurrentItem(currentItem, false);
                    return true;
                } else {
                    return false;
                }
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                return false;
            }

            @Override
            public void onLongPress(MotionEvent e) {
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                return false;
            }
        });

        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.dialogPhotosLoaded);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.FileLoadProgressChanged);
        MessagesController.getInstance(currentAccount).loadDialogPhotos((int) dialogId, 80, 0, true, parentClassGuid);
    }

    public void onDestroy() {
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.dialogPhotosLoaded);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.FileLoadProgressChanged);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        gestureDetector.onTouchEvent(ev);
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
            downPoint.set(ev.getX(), ev.getY());
        } else if (action == MotionEvent.ACTION_MOVE) {
            final float dx = ev.getX() - downPoint.x;
            final float dy = ev.getY() - downPoint.y;
            if (isSwipingViewPager && isScrollingListView) {
                if (Math.abs(dy) >= touchSlop || Math.abs(dx) >= touchSlop) {
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

    public void initIfEmpty(ImageLocation imageLocation, ImageLocation thumbLocation) {
        if (imageLocation == null || thumbLocation == null) {
            return;
        }
        if (prevImageLocation != null && prevImageLocation.location.local_id != imageLocation.location.local_id) {
            imagesLocations.clear();
            MessagesController.getInstance(currentAccount).loadDialogPhotos((int) dialogId, 80, 0, true, parentClassGuid);
        }
        if (!imagesLocations.isEmpty()) {
            return;
        }
        prevImageLocation = imageLocation;
        thumbsFileNames.add("");
        imagesLocations.add(imageLocation);
        thumbsLocations.add(thumbLocation);
        imagesLocationsSizes.add(-1);
        getAdapter().notifyDataSetChanged();
    }

    public ImageLocation getImageLocation(int index) {
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
        if (adapter != null) {
            return adapter.objects.get(getCurrentItem()).imageView;
        } else {
            return null;
        }
    }

    public void resetCurrentItem() {
        setCurrentItem(adapter.getExtraCount(), false);
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
                ArrayList<TLRPC.Photo> photos = (ArrayList<TLRPC.Photo>) args[4];
                thumbsFileNames.clear();
                imagesLocations.clear();
                thumbsLocations.clear();
                imagesLocationsSizes.clear();
                ImageLocation currentImageLocation = null;
                if (did < 0) {
                    TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-did);
                    currentImageLocation = ImageLocation.getForChat(chat, true);
                    if (currentImageLocation != null) {
                        thumbsFileNames.add("");
                        imagesLocations.add(currentImageLocation);
                        thumbsLocations.add(ImageLocation.getForChat(chat, false));
                        imagesLocationsSizes.add(-1);
                    }
                }
                for (int a = 0; a < photos.size(); a++) {
                    TLRPC.Photo photo = photos.get(a);
                    if (photo == null || photo instanceof TLRPC.TL_photoEmpty || photo.sizes == null) {
                        continue;
                    }
                    TLRPC.PhotoSize sizeFull = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, 640);
                    TLRPC.PhotoSize sizeThumb = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, 50);
                    if (currentImageLocation != null) {
                        boolean cont = false;
                        for (int b = 0; b < photo.sizes.size(); b++) {
                            TLRPC.PhotoSize size = photo.sizes.get(b);
                            if (size.location.local_id == currentImageLocation.location.local_id && size.location.volume_id == currentImageLocation.location.volume_id) {
                                cont = true;
                                break;
                            }
                        }
                        if (cont) {
                            continue;
                        }
                    }
                    if (sizeFull != null) {
                        if (photo.dc_id != 0) {
                            sizeFull.location.dc_id = photo.dc_id;
                            sizeFull.location.file_reference = photo.file_reference;
                        }
                        ImageLocation location = ImageLocation.getForPhoto(sizeFull, photo);
                        if (location != null) {
                            imagesLocations.add(location);
                            thumbsFileNames.add(FileLoader.getAttachFileName(sizeThumb));
                            thumbsLocations.add(ImageLocation.getForPhoto(sizeThumb, photo));
                            imagesLocationsSizes.add(sizeFull.size);
                        }
                    }
                }
                loadNeighboringThumbs();
                getAdapter().notifyDataSetChanged();
                if (fromCache) {
                    MessagesController.getInstance(currentAccount).loadDialogPhotos(did, 80, 0, false, parentClassGuid);
                }
            }
        } else if (id == NotificationCenter.fileDidLoad) {
            final String fileName = (String) args[0];
            for (int i = 0; i < thumbsFileNames.size(); i++) {
                if (thumbsFileNames.get(i).equals(fileName)) {
                    final RadialProgress2 radialProgress = radialProgresses.get(i);
                    if (radialProgress != null) {
                        radialProgress.setProgress(1f, true);
                    }
                }
            }
        } else if (id == NotificationCenter.FileLoadProgressChanged) {
            String fileName = (String) args[0];
            for (int i = 0; i < thumbsFileNames.size(); i++) {
                if (thumbsFileNames.get(i).equals(fileName)) {
                    final RadialProgress2 radialProgress = radialProgresses.get(i);
                    if (radialProgress != null) {
                        radialProgress.setProgress((Float) args[1], true);
                    }
                }
            }
        }
    }

    public class ViewPagerAdapter extends Adapter {

        private final ArrayList<Item> objects = new ArrayList<>();

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
            final int idx = objects.indexOf(object);
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

                    {
                        final int realPosition = getRealPosition(position);
                        if (realPosition == 0) {
                            setImage(imagesLocations.get(realPosition), null, parentAvatarImageView.getImageReceiver().getBitmap(), imagesLocationsSizes.get(realPosition), null);
                        } else {
                            setImage(imagesLocations.get(realPosition), null, thumbsLocations.get(realPosition), null, null, null, null, imagesLocationsSizes.get(realPosition), null);
                            radialProgress = new RadialProgress2(this);
                            radialProgress.setOverrideAlpha(0.0f);
                            radialProgress.setIcon(MediaActionDrawable.ICON_EMPTY, false, false);
                            radialProgress.setColors(0x42000000, 0x42000000, Color.WHITE, Color.WHITE);
                            radialProgresses.append(position, radialProgress);
                        }
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
                            if (getImageReceiver().getDrawable() != null) {
                                if (radialProgressHideAnimator == null) {
                                    radialProgressHideAnimatorStartValue = radialProgress.getOverrideAlpha();
                                    radialProgressHideAnimator = ValueAnimator.ofFloat(0f, 1f);
                                    radialProgressHideAnimator.setDuration((long) (radialProgressHideAnimatorStartValue * 175f));
                                    radialProgressHideAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                                    radialProgressHideAnimator.addUpdateListener(anim -> radialProgress.setOverrideAlpha(AndroidUtilities.lerp(radialProgressHideAnimatorStartValue, 0f, anim.getAnimatedFraction())));
                                    radialProgressHideAnimator.addListener(new AnimatorListenerAdapter() {
                                        @Override
                                        public void onAnimationEnd(Animator animation) {
                                            radialProgress = null;
                                            radialProgresses.delete(position);
                                        }
                                    });
                                    radialProgressHideAnimator.start();
                                }
                            } else {
                                if (firstDrawTime < 0) {
                                    firstDrawTime = System.currentTimeMillis();
                                } else {
                                    final long elapsedTime = System.currentTimeMillis() - firstDrawTime;
                                    if (elapsedTime <= 1000) {
                                        if (elapsedTime > 750) {
                                            radialProgress.setOverrideAlpha(CubicBezierInterpolator.DEFAULT.getInterpolation((elapsedTime - 750) / 250f));
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
            }

            if (item.imageView.getParent() == null) {
                container.addView(item.imageView);
            }

            return item;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView(((Item) object).imageView);
            radialProgresses.delete(getRealPosition(position));
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
