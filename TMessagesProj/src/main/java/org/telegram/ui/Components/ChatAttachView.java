/*
 * This is the source code of Telegram for Android v. 2.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2015.
 */

package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
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
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.support.widget.LinearLayoutManager;
import org.telegram.messenger.R;
import org.telegram.ui.Adapters.PhotoAttachAdapter;
import org.telegram.ui.Cells.PhotoAttachPhotoCell;
import org.telegram.ui.ChatActivity;

import java.util.ArrayList;
import java.util.HashMap;

public class ChatAttachView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

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

    private float[] distCache = new float[20];

    private DecelerateInterpolator decelerateInterpolator = new DecelerateInterpolator();

    private boolean loading;

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

        public void setTextAndIcon(CharSequence text, int icon) {
            textView.setText(text);
            imageView.setBackgroundResource(icon);
        }
    }

    public ChatAttachView(Context context) {
        super(context);

        NotificationCenter.getInstance().addObserver(this, NotificationCenter.albumsDidLoaded);
        if (MediaController.allPhotosAlbumEntry == null) {
            if (Build.VERSION.SDK_INT >= 21) {
                MediaController.loadGalleryPhotosAlbums(0);
            }
            loading = true;
        }

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
        photoAttachAdapter.setDelegate(new PhotoAttachAdapter.PhotoAttachAdapterDelegate() {
            @Override
            public void selectedPhotosChanged() {
                updatePhotosButton();
            }
        });
        attachPhotoRecyclerView.setOnItemClickListener(new RecyclerListView.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                photoAttachAdapter.onItemClick((PhotoAttachPhotoCell) view);
            }
        });

        views[9] = progressView = new EmptyTextProgressView(context);
        progressView.setText(LocaleController.getString("NoPhotos", R.string.NoPhotos));
        addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 80));
        attachPhotoRecyclerView.setEmptyView(progressView);

        views[10] = lineView = new View(getContext());
        lineView.setBackgroundColor(0xffd2d2d2);
        addView(lineView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1, Gravity.TOP | Gravity.LEFT));
        CharSequence[] items = new CharSequence[]{
                LocaleController.getString("ChatCamera", R.string.ChatCamera),
                LocaleController.getString("ChatGallery", R.string.ChatGallery),
                LocaleController.getString("ChatVideo", R.string.ChatVideo),
                LocaleController.getString("AttachAudio", R.string.AttachAudio),
                LocaleController.getString("ChatDocument", R.string.ChatDocument),
                LocaleController.getString("AttachContact", R.string.AttachContact),
                LocaleController.getString("ChatLocation", R.string.ChatLocation),
                ""
        };
        int itemIcons[] = new int[] {
                R.drawable.attach_camera_states,
                R.drawable.attach_gallery_states,
                R.drawable.attach_video_states,
                R.drawable.attach_audio_states,
                R.drawable.attach_file_states,
                R.drawable.attach_contact_states,
                R.drawable.attach_location_states,
                R.drawable.attach_hide_states,
        };
        for (int a = 0; a < 8; a++) {
            AttachButton attachButton = new AttachButton(context);
            attachButton.setTextAndIcon(items[a], itemIcons[a]);
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
    }

    public void setDelegate(ChatAttachViewDelegate chatAttachViewDelegate) {
        delegate = chatAttachViewDelegate;
    }

    public void onRevealAnimationEnd(boolean open) {
        if (open && Build.VERSION.SDK_INT <= 19 && MediaController.allPhotosAlbumEntry == null) {
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
}
