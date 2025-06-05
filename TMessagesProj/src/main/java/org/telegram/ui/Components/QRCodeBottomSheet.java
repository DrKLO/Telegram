package org.telegram.ui.Components;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;

import java.util.HashMap;

public class QRCodeBottomSheet extends BottomSheet {

    Bitmap qrCode;
    private final TextView help;
    private final TextView buttonTextView;
    private TextView button2TextView;
    int imageSize;
    RLottieImageView iconImage;

    public QRCodeBottomSheet(Context context, String title, String link, String helpMessage, boolean includeShareLink) {
        this(context, title, link, helpMessage, includeShareLink, null);
    }
    public QRCodeBottomSheet(Context context, String title, String link, String helpMessage, boolean includeShareLink, Theme.ResourcesProvider resourcesProvider) {
        super(context, false, resourcesProvider);
        fixNavigationBar();

        setTitle(title, true);
        ImageView imageView = new ImageView(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int size = MeasureSpec.getSize(widthMeasureSpec);
                super.onMeasure(MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY));
            }
        };
        imageView.setScaleType(ImageView.ScaleType.FIT_XY);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            imageView.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight(), AndroidUtilities.dp(12));
                }
            });
            imageView.setClipToOutline(true);
        }

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setPadding(0, AndroidUtilities.dp(16), 0, 0);
        imageView.setImageBitmap(qrCode = createQR(context, link, qrCode));

        iconImage = new RLottieImageView(context);
        iconImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iconImage.setBackgroundColor(Color.WHITE);
        //iconImage.setPadding(-AndroidUtilities.dp(4), -AndroidUtilities.dp(4), -AndroidUtilities.dp(4), -AndroidUtilities.dp(4));

        FrameLayout frameLayout = new FrameLayout(context) {

            float lastX;
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                float x = imageSize / 768f * imageView.getMeasuredHeight();
                if (lastX != x) {
                    lastX = x;
                    iconImage.getLayoutParams().height = iconImage.getLayoutParams().width = (int) x;
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                }
            }
        };
        frameLayout.addView(imageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        frameLayout.addView(iconImage, LayoutHelper.createFrame(60, 60, Gravity.CENTER));
        linearLayout.addView(frameLayout, LayoutHelper.createLinear(220, 220, Gravity.CENTER_HORIZONTAL, 30, 0,30 ,0));

        help = new TextView(context);
        help.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        help.setText(helpMessage);
        help.setGravity(Gravity.CENTER_HORIZONTAL);
        linearLayout.addView(help, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 40, 8, 40, 8));

        buttonTextView = new TextView(context);
        buttonTextView.setPadding(AndroidUtilities.dp(34), 0, AndroidUtilities.dp(34), 0);
        buttonTextView.setGravity(Gravity.CENTER);
        buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        buttonTextView.setTypeface(AndroidUtilities.bold());
        buttonTextView.setText(LocaleController.getString(R.string.ShareQrCode));
        buttonTextView.setOnClickListener(view -> {
            Uri uri = AndroidUtilities.getBitmapShareUri(qrCode, "qr_tmp.png", Bitmap.CompressFormat.PNG);
            if (uri != null) {
                Intent i = new Intent(Intent.ACTION_SEND);

                i.setType("image/*");
                i.putExtra(Intent.EXTRA_STREAM, uri);
                try {
                    AndroidUtilities.findActivity(context).startActivityForResult(Intent.createChooser(i, getTitleView().getText()), 500);
                } catch (ActivityNotFoundException ex) {
                    ex.printStackTrace();
                }
            }
        });
        linearLayout.addView(buttonTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM, 16, 15, 16, 3));

        if (includeShareLink) {
            button2TextView = new TextView(context);
            button2TextView.setPadding(AndroidUtilities.dp(34), 0, AndroidUtilities.dp(34), 0);
            button2TextView.setGravity(Gravity.CENTER);
            button2TextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            //        button2TextView.setTypeface(AndroidUtilities.medium());
            button2TextView.setText(LocaleController.getString(R.string.ShareLink));
            button2TextView.setOnClickListener(view -> {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, link);
                Intent chooserIntent = Intent.createChooser(shareIntent, LocaleController.getString(R.string.ShareLink));
                chooserIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(chooserIntent);
            });
            linearLayout.addView(button2TextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM, 16, 3, 16, 16));
        }

        updateColors();
        ScrollView scrollView = new ScrollView(context);
        scrollView.addView(linearLayout);
        setCustomView(scrollView);
    }

    public Bitmap createQR(Context context, String key, Bitmap oldBitmap) {
        try {
            HashMap<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 0);
            QRCodeWriter writer = new QRCodeWriter();
            Bitmap bitmap = writer.encode(key, 768, 768, hints, oldBitmap);
            imageSize = writer.getImageSize();
            return bitmap;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    public void setCenterAnimation(int resId) {
        iconImage.setAutoRepeat(true);
        iconImage.setAnimation(resId, 60, 60);
        iconImage.playAnimation();
    }

    public void setCenterImage(int resId) {
        iconImage.setImageResource(resId);
    }

    public void setCenterImage(Drawable drawable) {
        iconImage.setImageDrawable(drawable);
    }

    public void setCenterImage(Bitmap bitmap) {
        iconImage.setImageBitmap(bitmap);
    }

    void updateColors() {
        buttonTextView.setTextColor(getThemedColor(Theme.key_featuredStickers_buttonText));
        buttonTextView.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(6), getThemedColor(Theme.key_featuredStickers_addButton), getThemedColor(Theme.key_featuredStickers_addButtonPressed)));
        if (button2TextView != null) {
            button2TextView.setTextColor(getThemedColor(Theme.key_featuredStickers_addButton));
            button2TextView.setBackground(Theme.createSelectorDrawable(ColorUtils.setAlphaComponent(getThemedColor(Theme.key_featuredStickers_addButton), Math.min(255, Color.alpha(getThemedColor(Theme.key_listSelector)) * 2)), Theme.RIPPLE_MASK_ROUNDRECT_6DP));
        }
        help.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        help.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        if (getTitleView() != null) {
            getTitleView().setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        }
        setBackgroundColor(getThemedColor(Theme.key_dialogBackground));
    }
}
