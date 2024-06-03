/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.WebFile;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

import java.util.Locale;

public class PaymentInfoCell extends FrameLayout {

    private TextView nameTextView;
    private TextView detailTextView;
    private TextView detailExTextView;
    private BackupImageView imageView;

    public PaymentInfoCell(Context context) {
        super(context);

        imageView = new BackupImageView(context);
        imageView.getImageReceiver().setRoundRadius(AndroidUtilities.dp(8));
        addView(imageView, LayoutHelper.createFrame(100, 100, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 10, 10, 10, 0));

        nameTextView = new TextView(context);
        nameTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        nameTextView.setLines(1);
        nameTextView.setTypeface(AndroidUtilities.bold());
        nameTextView.setMaxLines(1);
        nameTextView.setSingleLine(true);
        nameTextView.setEllipsize(TextUtils.TruncateAt.END);
        nameTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 10 : 123, 9, LocaleController.isRTL ? 123 : 10, 0));

        detailTextView = new TextView(context);
        detailTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        detailTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        detailTextView.setMaxLines(3);
        detailTextView.setEllipsize(TextUtils.TruncateAt.END);
        detailTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        addView(detailTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 10 : 123, 33, LocaleController.isRTL ? 123 : 10, 0));

        detailExTextView = new TextView(context);
        detailExTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        detailExTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        detailExTextView.setLines(1);
        detailExTextView.setMaxLines(1);
        detailExTextView.setSingleLine(true);
        detailExTextView.setEllipsize(TextUtils.TruncateAt.END);
        detailExTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        addView(detailExTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 10 : 123, 90, LocaleController.isRTL ? 123 : 10, 9));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int h;
        if (imageView.getVisibility() != GONE) {
            h = MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(120), MeasureSpec.EXACTLY);
        } else {
            h = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            measureChildWithMargins(detailTextView, widthMeasureSpec, 0, heightMeasureSpec, 0);
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) detailExTextView.getLayoutParams();
            layoutParams.topMargin = AndroidUtilities.dp(33) + detailTextView.getMeasuredHeight() + AndroidUtilities.dp(3);
        }
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), h);
    }

    public void setInfo(String title, String description, TLRPC.WebDocument photo, String botname, Object parentObject) {
        nameTextView.setText(title);
        detailTextView.setText(description);
        detailExTextView.setText(botname);

        int maxPhotoWidth;
        if (AndroidUtilities.isTablet()) {
            maxPhotoWidth = (int) (AndroidUtilities.getMinTabletSide() * 0.7f);
        } else {
            maxPhotoWidth = (int) (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.7f);
        }
        int width = 640;
        int height = 360;
        float scale = width / (float) (maxPhotoWidth - AndroidUtilities.dp(2));
        width /= scale;
        height /= scale;
        if (photo != null && photo.mime_type.startsWith("image/")) {
            nameTextView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 10 : 123, 9, LocaleController.isRTL ? 123 : 10, 0));
            detailTextView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 10 : 123, 33, LocaleController.isRTL ? 123 : 10, 0));
            detailExTextView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 10 : 123, 90, LocaleController.isRTL ? 123 : 10, 0));
            imageView.setVisibility(VISIBLE);
            String filter = String.format(Locale.US, "%d_%d", width, height);
            imageView.getImageReceiver().setImage(ImageLocation.getForWebFile(WebFile.createWithWebDocument(photo)), filter, null, null, -1, null, parentObject, 1);
        } else {
            nameTextView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 17, 9, 17, 0));
            detailTextView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 17, 33, 17, 0));
            detailExTextView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 17, 90, 17, 9));
            imageView.setVisibility(GONE);
        }
    }

    public void setInvoice(TLRPC.TL_messageMediaInvoice invoice, String botname) {
        setInfo(invoice.title, invoice.description, invoice.webPhoto, botname, invoice);
    }

    public void setReceipt(TLRPC.PaymentReceipt receipt, String botname) {
        setInfo(receipt.title, receipt.description, receipt.photo, botname, receipt);
    }
}
