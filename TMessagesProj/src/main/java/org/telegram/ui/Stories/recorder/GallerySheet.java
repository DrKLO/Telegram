package org.telegram.ui.Stories.recorder;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Stories.DarkThemeResourceProvider;

public class GallerySheet extends BottomSheet {

    private final GalleryListView listView;

    public GallerySheet(Context context, Theme.ResourcesProvider resourcesProvider, String title, boolean onlyPhotos, float aspectRatio) {
        super(context, false, resourcesProvider);

        fixNavigationBar(0xff1f1f1f);
        listView = new GalleryListView(UserConfig.selectedAccount, context, new DarkThemeResourceProvider(), null, onlyPhotos, aspectRatio, false, false) {
            @Override
            public String getTitle() {
                return title;
            }
        };
        listView.allowSearch(false);
        listView.setMultipleOnClick(false);
        listView.setOnBackClickListener(() -> {
            dismiss();
        });
        listView.setOnSelectListener((entry, blurredBitmap) -> {
            if (entry == null || galleryListViewOpening != null) {
                return;
            }
            if (entry instanceof MediaController.PhotoEntry && onGalleryListener != null) {
                onGalleryListener.run((MediaController.PhotoEntry) entry);
            }
        });

        this.containerView = new SizeNotifierFrameLayout(context);
        this.containerView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);
        this.containerView.addView(listView);
    }

    private ValueAnimator galleryOpenCloseAnimator;
    private SpringAnimation galleryOpenCloseSpringAnimator;
    private Boolean galleryListViewOpening;
    private Runnable galleryLayouted;

    @Override
    public void show() {
        super.show();
        animate(true, null);
    }

    @Override
    public void dismiss() {
        animate(false, super::dismiss);
        super.dismiss();
    }

    @Override
    protected boolean canDismissWithSwipe() {
        return !listView.actionBarShown;
    }

    @Override
    public boolean dispatchTouchEvent(@NonNull MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN && ev.getY() < listView.top()) {
            dismiss();
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    private void animate(boolean open, Runnable done) {
        float from = listView.getTranslationY();
        float to = open ? 0 : containerView.getHeight() - listView.top() + AndroidUtilities.navigationBarHeight * 2.5f;

        galleryListViewOpening = open;
        if (open) {
            galleryOpenCloseSpringAnimator = new SpringAnimation(listView, DynamicAnimation.TRANSLATION_Y, to);
            galleryOpenCloseSpringAnimator.getSpring().setDampingRatio(0.75f);
            galleryOpenCloseSpringAnimator.getSpring().setStiffness(350.0f);
            galleryOpenCloseSpringAnimator.addEndListener((a, canceled, c, d) -> {
                if (canceled) {
                    return;
                }
                listView.setTranslationY(to);
                listView.ignoreScroll = false;
                galleryOpenCloseSpringAnimator = null;
                galleryListViewOpening = null;
                if (done != null) {
                    done.run();
                }
            });
            galleryOpenCloseSpringAnimator.start();
        } else {
            galleryOpenCloseAnimator = ValueAnimator.ofFloat(from, to);
            galleryOpenCloseAnimator.addUpdateListener(anm -> {
                listView.setTranslationY((float) anm.getAnimatedValue());
            });
            galleryOpenCloseAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    galleryOpenCloseAnimator = null;
                    galleryListViewOpening = null;
                    if (done != null) {
                        done.run();
                    }
                }
            });
            galleryOpenCloseAnimator.setDuration(450L);
            galleryOpenCloseAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            galleryOpenCloseAnimator.start();
        }
    }

    private Utilities.Callback<MediaController.PhotoEntry> onGalleryListener;
    public void setOnGalleryImage(Utilities.Callback<MediaController.PhotoEntry> listener) {
        onGalleryListener = listener;
    }

}
