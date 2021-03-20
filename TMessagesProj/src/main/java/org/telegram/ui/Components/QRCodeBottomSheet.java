package org.telegram.ui.Components;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

public class QRCodeBottomSheet extends BottomSheet {

    Bitmap qrCode;
    private final TextView help;
    private final TextView buttonTextView;
    int imageSize;
    RLottieImageView iconImage;
    
    public QRCodeBottomSheet(Context context, String link, String helpMessage) {
        super(context, false);

        setTitle(LocaleController.getString("InviteByQRCode", R.string.InviteByQRCode), true);
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
        iconImage.setBackgroundColor(Color.WHITE);
        iconImage.setAutoRepeat(true);
        iconImage.setAnimation(R.raw.qr_code_logo, 60, 60);
        iconImage.playAnimation();

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
        buttonTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        buttonTextView.setText(LocaleController.getString("ShareQrCode", R.string.ShareQrCode));
        buttonTextView.setOnClickListener(view -> {

            Uri uri = getImageUri(qrCode);
            if (uri != null) {
                Intent i = new Intent(Intent.ACTION_SEND);

                i.setType("image/*");
                i.putExtra(Intent.EXTRA_STREAM, uri);
                try {
                    AndroidUtilities.findActivity(context).startActivityForResult(Intent.createChooser(i, LocaleController.getString("InviteByQRCode", R.string.InviteByQRCode)), 500);
                } catch (ActivityNotFoundException ex) {
                    ex.printStackTrace();
                }
            }
        });

        linearLayout.addView(buttonTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM, 16, 15, 16, 16));

        updateColors();
        ScrollView scrollView = new ScrollView(context);
        scrollView.addView(linearLayout);
        setCustomView(scrollView);
    }

    public Uri getImageUri(Bitmap inImage) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        inImage.compress(Bitmap.CompressFormat.JPEG, 100, bytes);

        File cachePath = AndroidUtilities.getCacheDir();
        if (!cachePath.isDirectory()) {
            try {
                cachePath.mkdirs();
            } catch (Exception e) {
                FileLog.e(e);
                return null;
            }
        }
        File file = new File(cachePath, "qr_tmp.png");
        try (FileOutputStream out = new FileOutputStream(file)) {
            inImage.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.close();
            return FileProvider.getUriForFile(ApplicationLoader.applicationContext, BuildConfig.APPLICATION_ID + ".provider", file);
        } catch (IOException e) {
            FileLog.e(e);
        }
        return null;
    }

    public Bitmap createQR(Context context, String key, Bitmap oldBitmap) {
        try {
            HashMap<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 0);
            QRCodeWriter writer = new QRCodeWriter();
            Bitmap bitmap = writer.encode(key, BarcodeFormat.QR_CODE, 768, 768, hints, oldBitmap, context);
            imageSize = writer.getImageSize();
            return bitmap;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    void updateColors() {
        buttonTextView.setBackgroundDrawable(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(6), Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
        help.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        buttonTextView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        if (getTitleView() != null) {
            getTitleView().setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        }
        setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));
    }
}
