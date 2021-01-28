package org.telegram.ui.Components;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;

public class QRCodeBottomSheet extends BottomSheet {

    Bitmap qrCode;
    private final TextView help;
    private final TextView buttonTextView;

    public QRCodeBottomSheet(Context context, String link) {
        super(context, false);

        setTitle(LocaleController.getString("InviteByQRCode", R.string.InviteByQRCode), true);
        ImageView imageView = new ImageView(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int size = MeasureSpec.getSize(widthMeasureSpec);
                super.onMeasure(MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY));
            }
        };
        int p = AndroidUtilities.dp(54);
        imageView.setPadding(p, p, p, p);
        imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        imageView.setImageBitmap(qrCode = createQR(context, link, qrCode));
        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.addView(imageView);
        linearLayout.addView(frameLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 30, 0,30 ,0));

        help = new TextView(context);
        help.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        help.setText(LocaleController.getString("QRCodeLinkHelp", R.string.QRCodeLinkHelp));
        help.setGravity(Gravity.CENTER_HORIZONTAL);
        frameLayout.addView(help, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM,40, 0,40 ,8));

        buttonTextView = new TextView(context);

        buttonTextView.setPadding(AndroidUtilities.dp(34), 0, AndroidUtilities.dp(34), 0);
        buttonTextView.setGravity(Gravity.CENTER);
        buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        buttonTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        buttonTextView.setText(LocaleController.getString("ShareQrCode", R.string.ShareQrCode));
        buttonTextView.setOnClickListener(view -> {
            Intent i = new Intent(Intent.ACTION_SEND);

            i.setType("image/*");
            i.putExtra(Intent.EXTRA_STREAM, getImageUri(context, qrCode));
            try {
                AndroidUtilities.findActivity(context).startActivityForResult(Intent.createChooser(i, LocaleController.getString("InviteByQRCode", R.string.InviteByQRCode)), 500);
            } catch (ActivityNotFoundException ex) {
                ex.printStackTrace();
            }
        });

        linearLayout.addView(buttonTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM, 16, 15, 16, 16));

        updateColors();
        setCustomView(linearLayout);
    }

    public Uri getImageUri(Context inContext, Bitmap inImage) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        inImage.compress(Bitmap.CompressFormat.JPEG, 100, bytes);

        String path = MediaStore.Images.Media.insertImage(inContext.getContentResolver(), inImage, "group_invite_qr", null);
        return Uri.parse(path);
    }

    public Bitmap createQR(Context context, String key, Bitmap oldBitmap) {
        try {
            HashMap<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 0);
            return new QRCodeWriter().encode(key, BarcodeFormat.QR_CODE, 768, 768, hints, oldBitmap, context);
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
