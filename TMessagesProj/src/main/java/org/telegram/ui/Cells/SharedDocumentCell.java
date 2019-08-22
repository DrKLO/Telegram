/*
 * This is the source code of Telegram for Android v. 2.0.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LineProgressView;

import java.util.Date;

public class SharedDocumentCell extends FrameLayout implements DownloadController.FileDownloadProgressListener {

    private ImageView placeholderImageView;
    private BackupImageView thumbImageView;
    private TextView nameTextView;
    private TextView extTextView;
    private TextView dateTextView;
    private ImageView statusImageView;
    private LineProgressView progressView;
    private CheckBox2 checkBox;

    private boolean needDivider;

    private int currentAccount = UserConfig.selectedAccount;
    private int TAG;

    private MessageObject message;
    private boolean loading;
    private boolean loaded;

    public SharedDocumentCell(Context context) {
        super(context);

        TAG = DownloadController.getInstance(currentAccount).generateObserverTag();

        placeholderImageView = new ImageView(context);
        addView(placeholderImageView, LayoutHelper.createFrame(40, 40, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 12, 8, LocaleController.isRTL ? 12 : 0, 0));

        extTextView = new TextView(context);
        extTextView.setTextColor(Theme.getColor(Theme.key_files_iconText));
        extTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        extTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        extTextView.setLines(1);
        extTextView.setMaxLines(1);
        extTextView.setSingleLine(true);
        extTextView.setGravity(Gravity.CENTER);
        extTextView.setEllipsize(TextUtils.TruncateAt.END);
        extTextView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(extTextView, LayoutHelper.createFrame(32, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 16, 22, LocaleController.isRTL ? 16 : 0, 0));

        thumbImageView = new BackupImageView(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                float alpha;
                if (thumbImageView.getImageReceiver().hasBitmapImage()) {
                    alpha = 1.0f - thumbImageView.getImageReceiver().getCurrentAlpha();
                } else {
                    alpha = 1.0f;
                }
                extTextView.setAlpha(alpha);
                placeholderImageView.setAlpha(alpha);
                super.onDraw(canvas);
            }
        };
        thumbImageView.setRoundRadius(AndroidUtilities.dp(4));
        addView(thumbImageView, LayoutHelper.createFrame(40, 40, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 12, 8, LocaleController.isRTL ? 12 : 0, 0));

        nameTextView = new TextView(context);
        nameTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        nameTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        nameTextView.setMaxLines(2);
        nameTextView.setEllipsize(TextUtils.TruncateAt.END);
        nameTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 8 : 72, 5, LocaleController.isRTL ? 72 : 8, 0));

        statusImageView = new ImageView(context);
        statusImageView.setVisibility(INVISIBLE);
        statusImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_sharedMedia_startStopLoadIcon), PorterDuff.Mode.MULTIPLY));
        addView(statusImageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 8 : 72, 35, LocaleController.isRTL ? 72 : 8, 0));

        dateTextView = new TextView(context);
        dateTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
        dateTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        dateTextView.setLines(1);
        dateTextView.setMaxLines(1);
        dateTextView.setSingleLine(true);
        dateTextView.setEllipsize(TextUtils.TruncateAt.END);
        dateTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        addView(dateTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 8 : 72, 30, LocaleController.isRTL ? 72 : 8, 0));

        progressView = new LineProgressView(context);
        progressView.setProgressColor(Theme.getColor(Theme.key_sharedMedia_startStopLoadIcon));
        addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 2, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 72, 54, LocaleController.isRTL ? 72 : 0, 0));

        checkBox = new CheckBox2(context, 21);
        checkBox.setVisibility(INVISIBLE);
        checkBox.setColor(null, Theme.key_windowBackgroundWhite, Theme.key_checkboxCheck);
        checkBox.setDrawUnchecked(false);
        checkBox.setDrawBackgroundAsArc(2);
        addView(checkBox, LayoutHelper.createFrame(24, 24, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 33, 28, LocaleController.isRTL ? 33 : 0, 0));
    }

    public void setTextAndValueAndTypeAndThumb(String text, String value, String type, String thumb, int resId) {
        nameTextView.setText(text);
        dateTextView.setText(value);
        if (type != null) {
            extTextView.setVisibility(VISIBLE);
            extTextView.setText(type);
        } else {
            extTextView.setVisibility(INVISIBLE);
        }
        if (resId == 0) {
            placeholderImageView.setImageResource(AndroidUtilities.getThumbForNameOrMime(text, type, false));
            placeholderImageView.setVisibility(VISIBLE);
        } else {
            placeholderImageView.setVisibility(INVISIBLE);
        }
        if (thumb != null || resId != 0) {
            if (thumb != null) {
                thumbImageView.setImage(thumb, "40_40", null);
            } else {
                Drawable drawable = Theme.createCircleDrawableWithIcon(AndroidUtilities.dp(40), resId);
                Theme.setCombinedDrawableColor(drawable, Theme.getColor(Theme.key_files_folderIconBackground), false);
                Theme.setCombinedDrawableColor(drawable, Theme.getColor(Theme.key_files_folderIcon), true);
                thumbImageView.setImageDrawable(drawable);
            }
            thumbImageView.setVisibility(VISIBLE);
        } else {
            extTextView.setAlpha(1.0f);
            placeholderImageView.setAlpha(1.0f);
            thumbImageView.setImageBitmap(null);
            thumbImageView.setVisibility(INVISIBLE);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (progressView.getVisibility() == VISIBLE) {
            updateFileExistIcon();
        }
    }

    public void setChecked(boolean checked, boolean animated) {
        if (checkBox.getVisibility() != VISIBLE) {
            checkBox.setVisibility(VISIBLE);
        }
        checkBox.setChecked(checked, animated);
    }

    public void setDocument(MessageObject messageObject, boolean divider) {
        needDivider = divider;
        message = messageObject;
        loaded = false;
        loading = false;

        TLRPC.Document document = messageObject.getDocument();
        if (messageObject != null && document != null) {
            int idx;
            String name = null;
            if (messageObject.isMusic()) {
                for (int a = 0; a < document.attributes.size(); a++) {
                    TLRPC.DocumentAttribute attribute = document.attributes.get(a);
                    if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                        if (attribute.performer != null && attribute.performer.length() != 0 || attribute.title != null && attribute.title.length() != 0) {
                            name = messageObject.getMusicAuthor() + " - " + messageObject.getMusicTitle();
                        }
                    }
                }
            }
            String fileName = FileLoader.getDocumentFileName(document);
            if (name == null) {
                name = fileName;
            }
            nameTextView.setText(name);
            placeholderImageView.setVisibility(VISIBLE);
            extTextView.setVisibility(VISIBLE);
            placeholderImageView.setImageResource(AndroidUtilities.getThumbForNameOrMime(fileName, document.mime_type, false));
            extTextView.setText((idx = fileName.lastIndexOf('.')) == -1 ? "" : fileName.substring(idx + 1).toLowerCase());

            TLRPC.PhotoSize bigthumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 320);
            TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 40);
            if (thumb == bigthumb) {
                bigthumb = null;
            }
            if (thumb instanceof TLRPC.TL_photoSizeEmpty || thumb == null) {
                thumbImageView.setVisibility(INVISIBLE);
                thumbImageView.setImageBitmap(null);
                extTextView.setAlpha(1.0f);
                placeholderImageView.setAlpha(1.0f);
            } else {
                thumbImageView.getImageReceiver().setNeedsQualityThumb(bigthumb == null);
                thumbImageView.getImageReceiver().setShouldGenerateQualityThumb(bigthumb == null);

                thumbImageView.setVisibility(VISIBLE);
                thumbImageView.setImage(ImageLocation.getForDocument(bigthumb, document), "40_40", ImageLocation.getForDocument(thumb, document), "40_40_b", null, 0, 1, messageObject);
            }
            long date = (long) messageObject.messageOwner.date * 1000;
            dateTextView.setText(String.format("%s, %s", AndroidUtilities.formatFileSize(document.size), LocaleController.formatString("formatDateAtTime", R.string.formatDateAtTime, LocaleController.getInstance().formatterYear.format(new Date(date)), LocaleController.getInstance().formatterDay.format(new Date(date)))));
        } else {
            nameTextView.setText("");
            extTextView.setText("");
            dateTextView.setText("");
            placeholderImageView.setVisibility(VISIBLE);
            extTextView.setVisibility(VISIBLE);
            extTextView.setAlpha(1.0f);
            placeholderImageView.setAlpha(1.0f);
            thumbImageView.setVisibility(INVISIBLE);
            thumbImageView.setImageBitmap(null);
        }

        setWillNotDraw(!needDivider);
        progressView.setProgress(0, false);
        updateFileExistIcon();
    }

    public void updateFileExistIcon() {
        if (message != null && message.messageOwner.media != null) {
            loaded = false;
            if (message.attachPathExists || message.mediaExists) {
                statusImageView.setVisibility(INVISIBLE);
                progressView.setVisibility(INVISIBLE);
                dateTextView.setPadding(0, 0, 0, 0);
                loading = false;
                loaded = true;
                DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
            } else {
                String fileName = FileLoader.getAttachFileName(message.getDocument());
                DownloadController.getInstance(currentAccount).addLoadingFileObserver(fileName, message, this);
                loading = FileLoader.getInstance(currentAccount).isLoadingFile(fileName);
                statusImageView.setVisibility(VISIBLE);
                statusImageView.setImageResource(loading ? R.drawable.media_doc_pause : R.drawable.media_doc_load);
                dateTextView.setPadding(LocaleController.isRTL ? 0 : AndroidUtilities.dp(14), 0, LocaleController.isRTL ? AndroidUtilities.dp(14) : 0, 0);
                if (loading) {
                    progressView.setVisibility(VISIBLE);
                    Float progress = ImageLoader.getInstance().getFileProgress(fileName);
                    if (progress == null) {
                        progress = 0.0f;
                    }
                    progressView.setProgress(progress, false);
                } else {
                    progressView.setVisibility(INVISIBLE);
                }
            }
        } else {
            loading = false;
            loaded = true;
            progressView.setVisibility(INVISIBLE);
            progressView.setProgress(0, false);
            statusImageView.setVisibility(INVISIBLE);
            dateTextView.setPadding(0, 0, 0, 0);
            DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
        }
    }

    public MessageObject getMessage() {
        return message;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public boolean isLoading() {
        return loading;
    }

    public BackupImageView getImageView() {
        return thumbImageView;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(56), MeasureSpec.EXACTLY));
        setMeasuredDimension(getMeasuredWidth(), AndroidUtilities.dp(5 + 29) + nameTextView.getMeasuredHeight() + (needDivider ? 1 : 0));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (nameTextView.getLineCount() > 1) {
            int y = nameTextView.getMeasuredHeight() - AndroidUtilities.dp(22);
            dateTextView.layout(dateTextView.getLeft(), y + dateTextView.getTop(), dateTextView.getRight(), y + dateTextView.getBottom());
            statusImageView.layout(statusImageView.getLeft(), y + statusImageView.getTop(), statusImageView.getRight(), y + statusImageView.getBottom());
            progressView.layout(progressView.getLeft(), getMeasuredHeight() - progressView.getMeasuredHeight() - (needDivider ? 1 : 0), progressView.getRight(), getMeasuredHeight() - (needDivider ? 1 : 0));
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (needDivider) {
            canvas.drawLine(AndroidUtilities.dp(72), getHeight() - 1, getWidth() - getPaddingRight(), getHeight() - 1, Theme.dividerPaint);
        }
    }

    @Override
    public void onFailedDownload(String name, boolean canceled) {
        updateFileExistIcon();
    }

    @Override
    public void onSuccessDownload(String name) {
        progressView.setProgress(1, true);
        updateFileExistIcon();
    }

    @Override
    public void onProgressDownload(String fileName, float progress) {
        if (progressView.getVisibility() != VISIBLE) {
            updateFileExistIcon();
        }
        progressView.setProgress(progress, true);
    }

    @Override
    public void onProgressUpload(String fileName, float progress, boolean isEncrypted) {

    }

    @Override
    public int getObserverTag() {
        return TAG;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        if (checkBox.isChecked()) {
            info.setCheckable(true);
            info.setChecked(true);
        }
    }
}
