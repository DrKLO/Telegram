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
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AnimatorListenerAdapterProxy;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.query.SearchQuery;
import org.telegram.messenger.support.widget.LinearLayoutManager;
import org.telegram.messenger.R;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.PhotoAttachCameraCell;
import org.telegram.ui.Cells.PhotoAttachPhotoCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.PhotoViewer;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

public class ChatAttachAlert extends BottomSheet implements NotificationCenter.NotificationCenterDelegate, PhotoViewer.PhotoViewerProvider, BottomSheet.BottomSheetDelegateInterface {

    public interface ChatAttachViewDelegate {
        void didPressedButton(int button);
        View getRevealView();
        void didSelectBot(TLRPC.User user);
    }

    private class InnerAnimator {
        private AnimatorSet animatorSet;
        private float startRadius;
    }

    private LinearLayoutManager attachPhotoLayoutManager;
    private PhotoAttachAdapter photoAttachAdapter;
    private ChatActivity baseFragment;
    private AttachButton sendPhotosButton;
    private View views[] = new View[20];
    private RecyclerListView attachPhotoRecyclerView;
    private View lineView;
    private EmptyTextProgressView progressView;
    private ArrayList<Holder> viewsCache = new ArrayList<>(8);
    private RecyclerListView listView;
    private LinearLayoutManager layoutManager;
    private Drawable shadowDrawable;
    private ViewGroup attachView;
    private ListAdapter adapter;
    private TextView hintTextView;
    private ArrayList<InnerAnimator> innerAnimators = new ArrayList<>();

    private AnimatorSet currentHintAnimation;
    private boolean hintShowed;
    private Runnable hideHintRunnable;

    private boolean deviceHasGoodCamera = false;//Build.VERSION.SDK_INT >= 16;

    private DecelerateInterpolator decelerateInterpolator = new DecelerateInterpolator();

    private boolean loading = true;

    private ChatAttachViewDelegate delegate;

    private int scrollOffsetY;
    private boolean ignoreLayout;

    private boolean useRevealAnimation;
    private float revealRadius;
    private int revealX;
    private int revealY;
    private boolean revealAnimationInProgress;

    private class AttachButton extends FrameLayout {

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
            textView.setTextColor(Theme.ATTACH_SHEET_TEXT_COLOR);
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

        @Override
        public boolean hasOverlappingRendering() {
            return false;
        }
    }

    private class AttachBotButton extends FrameLayout {

        private BackupImageView imageView;
        private TextView nameTextView;
        private AvatarDrawable avatarDrawable = new AvatarDrawable();
        private boolean pressed;

        private boolean checkingForLongPress = false;
        private CheckForLongPress pendingCheckForLongPress = null;
        private int pressCount = 0;
        private CheckForTap pendingCheckForTap = null;

        private TLRPC.User currentUser;

        private final class CheckForTap implements Runnable {
            public void run() {
                if (pendingCheckForLongPress == null) {
                    pendingCheckForLongPress = new CheckForLongPress();
                }
                pendingCheckForLongPress.currentPressCount = ++pressCount;
                postDelayed(pendingCheckForLongPress, ViewConfiguration.getLongPressTimeout() - ViewConfiguration.getTapTimeout());
            }
        }

        class CheckForLongPress implements Runnable {
            public int currentPressCount;

            public void run() {
                if (checkingForLongPress && getParent() != null && currentPressCount == pressCount) {
                    checkingForLongPress = false;
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    onLongPress();
                    MotionEvent event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0);
                    onTouchEvent(event);
                    event.recycle();
                }
            }
        }

        public AttachBotButton(Context context) {
            super(context);

            imageView = new BackupImageView(context);
            imageView.setRoundRadius(AndroidUtilities.dp(27));
            addView(imageView, LayoutHelper.createFrame(54, 54, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 7, 0, 0));

            nameTextView = new TextView(context);
            nameTextView.setTextColor(Theme.ATTACH_SHEET_TEXT_COLOR);
            nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            nameTextView.setMaxLines(2);
            nameTextView.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            nameTextView.setLines(2);
            nameTextView.setEllipsize(TextUtils.TruncateAt.END);
            addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 6, 65, 6, 0));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(85), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(100), MeasureSpec.EXACTLY));
        }

        private void onLongPress() {
            if (baseFragment == null || currentUser == null) {
                return;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
            builder.setMessage(LocaleController.formatString("ChatHintsDelete", R.string.ChatHintsDelete, ContactsController.formatName(currentUser.first_name, currentUser.last_name)));
            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    SearchQuery.removeInline(currentUser.id);
                }
            });
            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
            builder.show();
        }

        public void setUser(TLRPC.User user) {
            if (user == null) {
                return;
            }
            currentUser = user;
            TLRPC.FileLocation photo = null;
            nameTextView.setText(ContactsController.formatName(user.first_name, user.last_name));
            avatarDrawable.setInfo(user);
            if (user != null && user.photo != null) {
                photo = user.photo.photo_small;
            }
            imageView.setImage(photo, "50_50", avatarDrawable);
            requestLayout();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            boolean result = false;

            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                pressed = true;
                invalidate();
                result = true;
            } else if (pressed) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                    pressed = false;
                    playSoundEffect(SoundEffectConstants.CLICK);
                    delegate.didSelectBot(MessagesController.getInstance().getUser(SearchQuery.inlineBots.get((Integer) getTag()).peer.user_id));
                    setUseRevealAnimation(false);
                    dismiss();
                    setUseRevealAnimation(true);
                    invalidate();
                } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                    pressed = false;
                    invalidate();
                }
            }
            if (!result) {
                result = super.onTouchEvent(event);
            } else {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    startCheckLongPress();
                }
            }
            if (event.getAction() != MotionEvent.ACTION_DOWN && event.getAction() != MotionEvent.ACTION_MOVE) {
                cancelCheckLongPress();
            }

            return result;
        }

        protected void startCheckLongPress() {
            if (checkingForLongPress) {
                return;
            }
            checkingForLongPress = true;
            if (pendingCheckForTap == null) {
                pendingCheckForTap = new CheckForTap();
            }
            postDelayed(pendingCheckForTap, ViewConfiguration.getTapTimeout());
        }

        protected void cancelCheckLongPress() {
            checkingForLongPress = false;
            if (pendingCheckForLongPress != null) {
                removeCallbacks(pendingCheckForLongPress);
            }
            if (pendingCheckForTap != null) {
                removeCallbacks(pendingCheckForTap);
            }
        }
    }

    public ChatAttachAlert(Context context) {
        super(context, false);
        setDelegate(this);
        setUseRevealAnimation(true);
        if (deviceHasGoodCamera) {
            //CameraController.getInstance().initCamera();
        }
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.albumsDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.reloadInlineHints);
        shadowDrawable = context.getResources().getDrawable(R.drawable.sheet_shadow);

        containerView = listView = new RecyclerListView(context) {
            @Override
            public void requestLayout() {
                if (ignoreLayout) {
                    return;
                }
                super.requestLayout();
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (ev.getAction() == MotionEvent.ACTION_DOWN && scrollOffsetY != 0 && ev.getY() < scrollOffsetY) {
                    dismiss();
                    return true;
                }
                return super.onInterceptTouchEvent(ev);
            }

            @Override
            public boolean onTouchEvent(MotionEvent e) {
                return !isDismissed() && super.onTouchEvent(e);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int height = MeasureSpec.getSize(heightMeasureSpec);
                if (Build.VERSION.SDK_INT >= 21) {
                    height -= AndroidUtilities.statusBarHeight;
                }
                int contentSize = backgroundPaddingTop + AndroidUtilities.dp(294) + (SearchQuery.inlineBots.isEmpty() ? 0 : ((int) Math.ceil(SearchQuery.inlineBots.size() / 4.0f) * AndroidUtilities.dp(100) + AndroidUtilities.dp(12)));
                int padding = contentSize == AndroidUtilities.dp(294) ? 0 : (height - AndroidUtilities.dp(294));
                if (padding != 0 && contentSize < height) {
                    padding -= (height - contentSize);
                }
                if (padding == 0) {
                    padding = backgroundPaddingTop;
                }
                if (getPaddingTop() != padding) {
                    ignoreLayout = true;
                    setPadding(backgroundPaddingLeft, padding, backgroundPaddingLeft, 0);
                    ignoreLayout = false;
                }
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(Math.min(contentSize, height), MeasureSpec.EXACTLY));
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                updateLayout();
            }

            @Override
            public void onDraw(Canvas canvas) {
                if (useRevealAnimation && Build.VERSION.SDK_INT <= 19) {
                    canvas.save();
                    canvas.clipRect(backgroundPaddingLeft, scrollOffsetY, getMeasuredWidth() - backgroundPaddingLeft, getMeasuredHeight());
                    if (revealAnimationInProgress) {
                        canvas.drawCircle(revealX, revealY, revealRadius, ciclePaint);
                    } else {
                        canvas.drawRect(backgroundPaddingLeft, scrollOffsetY, getMeasuredWidth() - backgroundPaddingLeft, getMeasuredHeight(), ciclePaint);
                    }
                    canvas.restore();
                } else {
                    shadowDrawable.setBounds(0, scrollOffsetY - backgroundPaddingTop, getMeasuredWidth(), getMeasuredHeight());
                    shadowDrawable.draw(canvas);
                }
            }
        };

        listView.setTag(10);
        containerView.setWillNotDraw(false);
        listView.setClipToPadding(false);
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(getContext()));
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        listView.setAdapter(adapter = new ListAdapter(context));
        listView.setVerticalScrollBarEnabled(false);
        listView.setEnabled(true);
        listView.setGlowColor(0xfff5f6f7);
        listView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                outRect.left = 0;
                outRect.right = 0;
                outRect.top = 0;
                outRect.bottom = 0;
            }
        });

        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (listView.getChildCount() <= 0) {
                    return;
                }
                if (hintShowed) {
                    if (layoutManager.findLastVisibleItemPosition() > 1) {
                        hideHint();
                        hintShowed = false;
                        ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE).edit().putBoolean("bothint", true).commit();
                    }
                }
                updateLayout();
            }
        });
        containerView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);

        attachView = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(294), MeasureSpec.EXACTLY));
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                int width = right - left;
                int height = bottom - top;
                int t = AndroidUtilities.dp(8);
                attachPhotoRecyclerView.layout(0, t, width, t + attachPhotoRecyclerView.getMeasuredHeight());
                progressView.layout(0, t, width, t + progressView.getMeasuredHeight());
                lineView.layout(0, AndroidUtilities.dp(96), width, AndroidUtilities.dp(96) + lineView.getMeasuredHeight());
                hintTextView.layout(width - hintTextView.getMeasuredWidth() - AndroidUtilities.dp(5), height - hintTextView.getMeasuredHeight() - AndroidUtilities.dp(5), width - AndroidUtilities.dp(5), height - AndroidUtilities.dp(5));

                int diff = (width - AndroidUtilities.dp(85 * 4 + 20)) / 3;
                for (int a = 0; a < 8; a++) {
                    int y = AndroidUtilities.dp(105 + 95 * (a / 4));
                    int x = AndroidUtilities.dp(10) + (a % 4) * (AndroidUtilities.dp(85) + diff);
                    views[a].layout(x, y, x + views[a].getMeasuredWidth(), y + views[a].getMeasuredHeight());
                }
            }
        };

        views[8] = attachPhotoRecyclerView = new RecyclerListView(context);
        attachPhotoRecyclerView.setVerticalScrollBarEnabled(true);
        attachPhotoRecyclerView.setAdapter(photoAttachAdapter = new PhotoAttachAdapter(context));
        attachPhotoRecyclerView.setClipToPadding(false);
        attachPhotoRecyclerView.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), 0);
        attachPhotoRecyclerView.setItemAnimator(null);
        attachPhotoRecyclerView.setLayoutAnimation(null);
        attachPhotoRecyclerView.setOverScrollMode(RecyclerListView.OVER_SCROLL_NEVER);
        attachView.addView(attachPhotoRecyclerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 80));
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
                if (!deviceHasGoodCamera || position != 0) {
                    if (deviceHasGoodCamera) {
                        position--;
                    }
                    ArrayList<Object> arrayList = (ArrayList) MediaController.allPhotosAlbumEntry.photos;
                    if (position < 0 || position >= arrayList.size()) {
                        return;
                    }
                    PhotoViewer.getInstance().setParentActivity(baseFragment.getParentActivity());
                    PhotoViewer.getInstance().openPhotoForSelect(arrayList, position, 0, ChatAttachAlert.this, baseFragment);
                    AndroidUtilities.hideKeyboard(baseFragment.getFragmentView().findFocus());
                } else {
                    final File path = AndroidUtilities.generatePicturePath();
                    /*CameraController.getInstance().takePicture(path, ((PhotoAttachCameraCell) view).cameraSession, new Runnable() {
                        @Override
                        public void run() {
                            PhotoViewer.getInstance().setParentActivity(baseFragment.getParentActivity());
                            final ArrayList<Object> arrayList = new ArrayList<>();
                            int orientation = 0;
                            try {
                                ExifInterface ei = new ExifInterface(path.getAbsolutePath());
                                int exif = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                                switch (exif) {
                                    case ExifInterface.ORIENTATION_ROTATE_90:
                                        orientation = 90;
                                        break;
                                    case ExifInterface.ORIENTATION_ROTATE_180:
                                        orientation = 180;
                                        break;
                                    case ExifInterface.ORIENTATION_ROTATE_270:
                                        orientation = 270;
                                        break;
                                }
                            } catch (Exception e) {
                                FileLog.e("tmessages", e);
                            }
                            arrayList.add(new MediaController.PhotoEntry(0, 0, 0, path.getAbsolutePath(), orientation, false));

                            PhotoViewer.getInstance().openPhotoForSelect(arrayList, 0, 2, new PhotoViewer.EmptyPhotoViewerProvider() {
                                @Override
                                public void sendButtonPressed(int index) {
                                    AndroidUtilities.addMediaToGallery(path.getAbsolutePath());
                                    baseFragment.sendPhoto((MediaController.PhotoEntry) arrayList.get(0));
                                }

                                @Override
                                public boolean cancelButtonPressed() {
                                    path.delete();
                                    return true;
                                }
                            }, baseFragment);
                        }
                    });*/
                }
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
        attachView.addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 80));
        attachPhotoRecyclerView.setEmptyView(progressView);

        views[10] = lineView = new View(getContext()) {
            @Override
            public boolean hasOverlappingRendering() {
                return false;
            }
        };
        lineView.setBackgroundColor(0xffd2d2d2);
        attachView.addView(lineView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1, Gravity.TOP | Gravity.LEFT));
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
            attachView.addView(attachButton, LayoutHelper.createFrame(85, 90, Gravity.LEFT | Gravity.TOP));
            attachButton.setTag(a);
            views[a] = attachButton;
            if (a == 7) {
                sendPhotosButton = attachButton;
                sendPhotosButton.imageView.setPadding(0, AndroidUtilities.dp(4), 0, 0);
            }
            attachButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    delegate.didPressedButton((Integer) v.getTag());
                }
            });
        }

        hintTextView = new TextView(context);
        hintTextView.setBackgroundResource(R.drawable.tooltip);
        hintTextView.setTextColor(Theme.CHAT_GIF_HINT_TEXT_COLOR);
        hintTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        hintTextView.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), 0);
        hintTextView.setText(LocaleController.getString("AttachBotsHelp", R.string.AttachBotsHelp));
        hintTextView.setGravity(Gravity.CENTER_VERTICAL);
        hintTextView.setVisibility(View.INVISIBLE);
        hintTextView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.scroll_tip, 0, 0, 0);
        hintTextView.setCompoundDrawablePadding(AndroidUtilities.dp(8));
        attachView.addView(hintTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 32, Gravity.RIGHT | Gravity.BOTTOM, 5, 0, 5, 5));

        for (int a = 0; a < 8; a++) {
            viewsCache.add(photoAttachAdapter.createHolder());
        }

        if (loading) {
            progressView.showProgress();
        } else {
            progressView.showTextView();
        }
    }

    private void hideHint() {
        if (hideHintRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(hideHintRunnable);
            hideHintRunnable = null;
        }
        if (hintTextView == null) {
            return;
        }
        currentHintAnimation = new AnimatorSet();
        currentHintAnimation.playTogether(
                ObjectAnimator.ofFloat(hintTextView, "alpha", 0.0f)
        );
        currentHintAnimation.setInterpolator(decelerateInterpolator);
        currentHintAnimation.addListener(new AnimatorListenerAdapterProxy() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (currentHintAnimation == null || !currentHintAnimation.equals(animation)) {
                    return;
                }
                currentHintAnimation = null;
                if (hintTextView != null) {
                    hintTextView.setVisibility(View.INVISIBLE);
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (currentHintAnimation != null && currentHintAnimation.equals(animation)) {
                    currentHintAnimation = null;
                }
            }
        });
        currentHintAnimation.setDuration(300);
        currentHintAnimation.start();
    }

    private void showHint() {
        if (SearchQuery.inlineBots.isEmpty()) {
            return;
        }
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        if (preferences.getBoolean("bothint", false)) {
            return;
        }
        hintShowed = true;

        hintTextView.setVisibility(View.VISIBLE);
        currentHintAnimation = new AnimatorSet();
        currentHintAnimation.playTogether(
                ObjectAnimator.ofFloat(hintTextView, "alpha", 0.0f, 1.0f)
        );
        currentHintAnimation.setInterpolator(decelerateInterpolator);
        currentHintAnimation.addListener(new AnimatorListenerAdapterProxy() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (currentHintAnimation == null || !currentHintAnimation.equals(animation)) {
                    return;
                }
                currentHintAnimation = null;
                AndroidUtilities.runOnUIThread(hideHintRunnable = new Runnable() {
                    @Override
                    public void run() {
                        if (hideHintRunnable != this) {
                            return;
                        }
                        hideHintRunnable = null;
                        hideHint();
                    }
                }, 2000);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (currentHintAnimation != null && currentHintAnimation.equals(animation)) {
                    currentHintAnimation = null;
                }
            }
        });
        currentHintAnimation.setDuration(300);
        currentHintAnimation.start();
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.albumsDidLoaded) {
            if (photoAttachAdapter != null) {
                loading = false;
                progressView.showTextView();
                photoAttachAdapter.notifyDataSetChanged();
            }
        } else if (id == NotificationCenter.reloadInlineHints) {
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        }
    }

    @SuppressLint("NewApi")
    private void updateLayout() {
        if (listView.getChildCount() <= 0) {
            listView.setTopGlowOffset(scrollOffsetY = listView.getPaddingTop());
            containerView.invalidate();
            return;
        }
        View child = listView.getChildAt(0);
        Holder holder = (Holder) listView.findContainingViewHolder(child);
        int top = child.getTop();
        int newOffset = 0;
        if (top >= 0 && holder != null && holder.getAdapterPosition() == 0) {
            newOffset = top;
        }
        if (scrollOffsetY != newOffset) {
            listView.setTopGlowOffset(scrollOffsetY = newOffset);
            containerView.invalidate();
        }
    }

    @Override
    protected boolean canDismissWithSwipe() {
        return false;
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

    public void loadGalleryPhotos() {
        if (MediaController.allPhotosAlbumEntry == null && Build.VERSION.SDK_INT >= 21) {
            MediaController.loadGalleryPhotosAlbums(0);
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
        if (currentHintAnimation != null) {
            currentHintAnimation.cancel();
            currentHintAnimation = null;
        }
        hintTextView.setAlpha(0.0f);
        hintTextView.setVisibility(View.INVISIBLE);
        attachPhotoLayoutManager.scrollToPositionWithOffset(0, 1000000);
        photoAttachAdapter.clearSelectedPhotos();
        baseFragment = parentFragment;
        layoutManager.scrollToPositionWithOffset(0, 1000000);
        updatePhotosButton();
    }

    public HashMap<Integer, MediaController.PhotoEntry> getSelectedPhotos() {
        return photoAttachAdapter.getSelectedPhotos();
    }

    public void onDestroy() {
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.albumsDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.reloadInlineHints);
        baseFragment = null;
    }

    private PhotoAttachPhotoCell getCellForIndex(int index) {
        if (MediaController.allPhotosAlbumEntry == null) {
            return null;
        }
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
            object.scale = cell.getImageView().getScaleX();
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
                if (cell.getCheckBox().getVisibility() != View.VISIBLE) {
                    cell.getCheckBox().setVisibility(View.VISIBLE);
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

    private void onRevealAnimationEnd(boolean open) {
        NotificationCenter.getInstance().setAnimationInProgress(false);
        revealAnimationInProgress = false;
        if (open && Build.VERSION.SDK_INT <= 19 && MediaController.allPhotosAlbumEntry == null) {
            MediaController.loadGalleryPhotosAlbums(0);
        }
        if (open) {
            showHint();
        }
    }

    @Override
    public void onOpenAnimationEnd() {
        onRevealAnimationEnd(true);
    }

    @Override
    public void onOpenAnimationStart() {

    }

    private class Holder extends RecyclerView.ViewHolder {

        public Holder(View itemView) {
            super(itemView);
        }
    }

    private class ListAdapter extends RecyclerView.Adapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = attachView;
                    break;
                case 1:
                    view = new ShadowSectionCell(mContext);
                    break;
                default:
                    FrameLayout frameLayout = new FrameLayout(mContext) {
                        @Override
                        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                            int diff = (right - left - AndroidUtilities.dp(85 * 4 + 20)) / 3;
                            for (int a = 0; a < 4; a++) {
                                int x = AndroidUtilities.dp(10) + (a % 4) * (AndroidUtilities.dp(85) + diff);
                                View child = getChildAt(a);
                                child.layout(x, 0, x + child.getMeasuredWidth(), child.getMeasuredHeight());
                            }
                        }
                    };
                    for (int a = 0; a < 4; a++) {
                        frameLayout.addView(new AttachBotButton(mContext));
                    }
                    view = frameLayout;
                    frameLayout.setLayoutParams(new RecyclerView.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(100)));
                    break;
            }
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (position > 1) {
                position -= 2;
                position *= 4;
                FrameLayout frameLayout = (FrameLayout) holder.itemView;
                for (int a = 0; a < 4; a++) {
                    AttachBotButton child = (AttachBotButton) frameLayout.getChildAt(a);
                    if (position + a >= SearchQuery.inlineBots.size()) {
                        child.setVisibility(View.INVISIBLE);
                    } else {
                        child.setVisibility(View.VISIBLE);
                        child.setTag(position + a);
                        child.setUser(MessagesController.getInstance().getUser(SearchQuery.inlineBots.get(position + a).peer.user_id));
                    }
                }
            }
        }

        @Override
        public int getItemCount() {
            return 1 + (!SearchQuery.inlineBots.isEmpty() ? 1 + (int) Math.ceil(SearchQuery.inlineBots.size() / 4.0f) : 0);
        }

        @Override
        public int getItemViewType(int position) {
            switch (position) {
                case 0:
                    return 0;
                case 1:
                    return 1;
                default:
                    return 2;
            }
        }
    }

    private class PhotoAttachAdapter extends RecyclerView.Adapter {

        private Context mContext;
        private HashMap<Integer, MediaController.PhotoEntry> selectedPhotos = new HashMap<>();

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
            if (!deviceHasGoodCamera || position != 0) {
                if (deviceHasGoodCamera) {
                    position--;
                }
                PhotoAttachPhotoCell cell = (PhotoAttachPhotoCell) holder.itemView;
                MediaController.PhotoEntry photoEntry = MediaController.allPhotosAlbumEntry.photos.get(position);
                cell.setPhotoEntry(photoEntry, position == MediaController.allPhotosAlbumEntry.photos.size() - 1);
                cell.setChecked(selectedPhotos.containsKey(photoEntry.imageId), false);
                cell.getImageView().setTag(position);
                cell.setTag(position);
            }
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            Holder holder;
            switch (viewType) {
                case 1:
                    holder = new Holder(new PhotoAttachCameraCell(mContext));
                    break;
                default:
                    if (!viewsCache.isEmpty()) {
                        holder = viewsCache.get(0);
                        viewsCache.remove(0);
                    } else {
                        holder = createHolder();
                    }
                    break;
            }

            return holder;
        }

        @Override
        public int getItemCount() {
            int count = 0;
            if (deviceHasGoodCamera) {
                count++;
            }
            if (MediaController.allPhotosAlbumEntry != null) {
                count += MediaController.allPhotosAlbumEntry.photos.size();
            }
            return count;
        }

        @Override
        public int getItemViewType(int position) {
            if (deviceHasGoodCamera && position == 0) {
                return 1;
            }
            return 0;
        }
    }

    private void setUseRevealAnimation(boolean value) {
        if (!value || value && Build.VERSION.SDK_INT >= 18 && !AndroidUtilities.isTablet()) {
            useRevealAnimation = value;
        }
    }

    @SuppressLint("NewApi")
    protected void setRevealRadius(float radius) {
        revealRadius = radius;
        if (Build.VERSION.SDK_INT <= 19) {
            containerView.invalidate();
        }
        if (!isDismissed()) {
            for (int a = 0; a < innerAnimators.size(); a++) {
                InnerAnimator innerAnimator = innerAnimators.get(a);
                if (innerAnimator.startRadius > radius) {
                    continue;
                }
                innerAnimator.animatorSet.start();
                innerAnimators.remove(a);
                a--;
            }
        }
    }

    protected float getRevealRadius() {
        return revealRadius;
    }

    @SuppressLint("NewApi")
    private void startRevealAnimation(final boolean open) {
        containerView.setTranslationY(0);

        final AnimatorSet animatorSet = new AnimatorSet();

        View view = delegate.getRevealView();
        if (view.getVisibility() == View.VISIBLE && ((ViewGroup) view.getParent()).getVisibility() == View.VISIBLE) {
            final int coords[] = new int[2];
            view.getLocationInWindow(coords);
            float top;
            if (Build.VERSION.SDK_INT <= 19) {
                top = AndroidUtilities.displaySize.y - containerView.getMeasuredHeight() - AndroidUtilities.statusBarHeight;
            } else {
                top = containerView.getY();
            }
            revealX = coords[0] + view.getMeasuredWidth() / 2;
            revealY = (int) (coords[1] + view.getMeasuredHeight() / 2 - top);
            if (Build.VERSION.SDK_INT <= 19) {
                revealY -= AndroidUtilities.statusBarHeight;
            }
        } else {
            revealX = AndroidUtilities.displaySize.x / 2 + backgroundPaddingLeft;
            revealY = (int) (AndroidUtilities.displaySize.y - containerView.getY());
        }

        int corners[][] = new int[][]{
                {0, 0},
                {0, AndroidUtilities.dp(304)},
                {containerView.getMeasuredWidth(), 0},
                {containerView.getMeasuredWidth(), AndroidUtilities.dp(304)}
        };
        int finalRevealRadius = 0;
        int y = revealY - scrollOffsetY + backgroundPaddingTop;
        for (int a = 0; a < 4; a++) {
            finalRevealRadius = Math.max(finalRevealRadius, (int) Math.ceil(Math.sqrt((revealX - corners[a][0]) * (revealX - corners[a][0]) + (y - corners[a][1]) * (y - corners[a][1]))));
        }
        int finalRevealX = revealX <= containerView.getMeasuredWidth() ? revealX : containerView.getMeasuredWidth();

        ArrayList<Animator> animators = new ArrayList<>(3);
        animators.add(ObjectAnimator.ofFloat(this, "revealRadius", open ? 0 : finalRevealRadius, open ? finalRevealRadius : 0));
        animators.add(ObjectAnimator.ofInt(backDrawable, "alpha", open ? 51 : 0));
        if (Build.VERSION.SDK_INT >= 21) {
            containerView.setElevation(AndroidUtilities.dp(10));
            try {
                animators.add(ViewAnimationUtils.createCircularReveal(containerView, finalRevealX, revealY, open ? 0 : finalRevealRadius, open ? finalRevealRadius : 0));
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
            animatorSet.setDuration(320);
        } else {
            if (!open) {
                animatorSet.setDuration(200);
                containerView.setPivotX(revealX <= containerView.getMeasuredWidth() ? revealX : containerView.getMeasuredWidth());
                containerView.setPivotY(revealY);
                animators.add(ObjectAnimator.ofFloat(containerView, "scaleX", 0.0f));
                animators.add(ObjectAnimator.ofFloat(containerView, "scaleY", 0.0f));
                animators.add(ObjectAnimator.ofFloat(containerView, "alpha", 0.0f));
            } else {
                animatorSet.setDuration(250);
                containerView.setScaleX(1);
                containerView.setScaleY(1);
                containerView.setAlpha(1);
                if (Build.VERSION.SDK_INT <= 19) {
                    animatorSet.setStartDelay(20);
                }
            }
        }
        animatorSet.playTogether(animators);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            public void onAnimationEnd(Animator animation) {
                if (currentSheetAnimation != null && currentSheetAnimation.equals(animation)) {
                    currentSheetAnimation = null;
                    onRevealAnimationEnd(open);
                    containerView.invalidate();
                    containerView.setLayerType(View.LAYER_TYPE_NONE, null);
                    if (!open) {
                        containerView.setVisibility(View.INVISIBLE);
                        try {
                            dismissInternal();
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }
                    }
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (currentSheetAnimation != null && animatorSet.equals(animation)) {
                    currentSheetAnimation = null;
                }
            }
        });

        if (open) {
            innerAnimators.clear();
            NotificationCenter.getInstance().setAllowedNotificationsDutingAnimation(new int[]{NotificationCenter.dialogsNeedReload});
            NotificationCenter.getInstance().setAnimationInProgress(true);
            revealAnimationInProgress = true;

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

                InnerAnimator innerAnimator = new InnerAnimator();

                int buttonX = views[a].getLeft() + views[a].getMeasuredWidth() / 2;
                int buttonY = views[a].getTop() + attachView.getTop() + views[a].getMeasuredHeight() / 2;
                float dist = (float) Math.sqrt((revealX - buttonX) * (revealX - buttonX) + (revealY - buttonY) * (revealY - buttonY));
                float vecX = (revealX - buttonX) / dist;
                float vecY = (revealY - buttonY) / dist;
                views[a].setPivotX(views[a].getMeasuredWidth() / 2 + vecX * AndroidUtilities.dp(20));
                views[a].setPivotY(views[a].getMeasuredHeight() / 2 + vecY * AndroidUtilities.dp(20));
                innerAnimator.startRadius = dist - AndroidUtilities.dp(27 * 3);

                views[a].setTag(R.string.AppName, 1);
                animators = new ArrayList<>();
                final AnimatorSet animatorSetInner;
                if (a < 8) {
                    animators.add(ObjectAnimator.ofFloat(views[a], "scaleX", 0.7f, 1.05f));
                    animators.add(ObjectAnimator.ofFloat(views[a], "scaleY", 0.7f, 1.05f));

                    animatorSetInner = new AnimatorSet();
                    animatorSetInner.playTogether(
                            ObjectAnimator.ofFloat(views[a], "scaleX", 1.0f),
                            ObjectAnimator.ofFloat(views[a], "scaleY", 1.0f));
                    animatorSetInner.setDuration(100);
                    animatorSetInner.setInterpolator(decelerateInterpolator);
                } else {
                    animatorSetInner = null;
                }
                if (Build.VERSION.SDK_INT <= 19) {
                    animators.add(ObjectAnimator.ofFloat(views[a], "alpha", 1.0f));
                }
                innerAnimator.animatorSet = new AnimatorSet();
                innerAnimator.animatorSet.playTogether(animators);
                innerAnimator.animatorSet.setDuration(150);
                innerAnimator.animatorSet.setInterpolator(decelerateInterpolator);
                innerAnimator.animatorSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (animatorSetInner != null) {
                            animatorSetInner.start();
                        }
                    }
                });
                innerAnimators.add(innerAnimator);
            }
        }
        currentSheetAnimation = animatorSet;
        animatorSet.start();
    }

    @Override
    protected boolean onCustomOpenAnimation() {
        if (useRevealAnimation) {
            startRevealAnimation(true);
            return true;
        }
        return false;
    }

    @Override
    protected boolean onCustomCloseAnimation() {
        if (useRevealAnimation) {
            backDrawable.setAlpha(51);
            startRevealAnimation(false);
            return true;
        }
        return false;
    }
}
