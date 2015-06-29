/*
 * This is the source code of Telegram for Android v. 2.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2015.
 */

package org.telegram.ui.Components;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.LocaleController;
import org.telegram.android.MediaController;
import org.telegram.android.support.widget.LinearLayoutManager;
import org.telegram.messenger.R;
import org.telegram.ui.Adapters.PhotoAttachAdapter;
import org.telegram.ui.ChatActivity;

import java.util.HashMap;

public class ChatAttachView extends FrameLayout {

    public interface ChatAttachViewDelegate {
        void didPressedButton(int button);
    }

    private LinearLayoutManager attachPhotoLayoutManager;
    private PhotoAttachAdapter photoAttachAdapter;
    private ChatActivity baseFragment;
    private AttachButton sendPhotosButton;
    private AttachButton buttons[] = new AttachButton[8];

    private ChatAttachViewDelegate delegate;

    private static class AttachButton extends FrameLayout {

        private TextView textView;
        private ImageView imageView;

        public AttachButton(Context context) {
            super(context);

            imageView = new ImageView(context);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            //imageView.setColorFilter(0x33000000);
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

        RecyclerListView attachPhotoRecyclerView = new RecyclerListView(context);
        if (photoAttachAdapter != null) {
            photoAttachAdapter.onDestroy();
        }
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

        View lineView = new View(getContext());
        lineView.setBackgroundColor(0xffd2d2d2);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1, Gravity.TOP | Gravity.LEFT);
        layoutParams.topMargin = AndroidUtilities.dp(88);
        addView(lineView, layoutParams);
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
                R.drawable.ic_attach_photo_big,
                R.drawable.ic_attach_gallery_big,
                R.drawable.ic_attach_video_big,
                R.drawable.ic_attach_music_big,
                R.drawable.ic_attach_file_big,
                R.drawable.ic_attach_contact_big,
                R.drawable.ic_attach_location_big,
                R.drawable.ic_attach_hide_big,
        };
        for (int a = 0; a < 8; a++) {
            AttachButton attachButton = new AttachButton(context);
            attachButton.setTextAndIcon(items[a], itemIcons[a]);
            int y = 97 + 95 * (a / 4);
            int x = 10 + (a % 4) * 85;
            addView(attachButton, LayoutHelper.createFrame(85, 90, Gravity.LEFT | Gravity.TOP, x, y, 0, 0));
            attachButton.setTag(a);
            buttons[a] = attachButton;
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
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(278), MeasureSpec.EXACTLY));
    }

    public void updatePhotosButton() {
        int count = photoAttachAdapter.getSelectedPhotos().size();
        if (count == 0) {
            sendPhotosButton.imageView.setPadding(0, AndroidUtilities.dp(4), 0, 0);
            sendPhotosButton.imageView.setBackgroundResource(R.drawable.ic_attach_hide_big);
            sendPhotosButton.imageView.setImageResource(R.drawable.ic_attach_hide_big_icon);
            sendPhotosButton.textView.setText("");
        } else {
            sendPhotosButton.imageView.setPadding(AndroidUtilities.dp(2), 0, 0, 0);
            sendPhotosButton.imageView.setBackgroundResource(R.drawable.ic_attach_send_big);
            sendPhotosButton.imageView.setImageResource(R.drawable.ic_attach_send_big_icon);
            sendPhotosButton.textView.setText(LocaleController.formatString("SendItems", R.string.SendItems, String.format("(%d)", count)));
        }
    }

    public void setDelegate(ChatAttachViewDelegate chatAttachViewDelegate) {
        delegate = chatAttachViewDelegate;
    }

    public void startAnimations(boolean up) {
        for (int a = 0; a < 4; a++) {
            //buttons[a].setTranslationY(AndroidUtilities.dp(up ? 20 : -20));
            //buttons[a + 4].setTranslationY(AndroidUtilities.dp(up ? 20 : -20));
            buttons[a].setScaleX(0.8f);
            buttons[a].setScaleY(0.8f);
            buttons[a + 4].setScaleX(0.8f);
            buttons[a + 4].setScaleY(0.8f);
            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(ObjectAnimator.ofFloat(buttons[a], "scaleX", 1),
                    ObjectAnimator.ofFloat(buttons[a + 4], "scaleX", 1),
                    ObjectAnimator.ofFloat(buttons[a], "scaleY", 1),
                    ObjectAnimator.ofFloat(buttons[a + 4], "scaleY", 1));
            animatorSet.setDuration(150);
            animatorSet.setStartDelay((3 - a) * 40);
            animatorSet.start();
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
        photoAttachAdapter.onDestroy();
        baseFragment = null;
    }
}
