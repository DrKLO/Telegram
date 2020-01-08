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
import org.telegram.messenger.MediaController;
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

import java.io.File;
import java.util.Date;
import java.util.Locale;

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

    private boolean isPickerCell;

    public SharedDocumentCell(Context context) {
        this(context, false);
    }

    public SharedDocumentCell(Context context, boolean pickerCell) {
        super(context);

        isPickerCell = pickerCell;
        TAG = DownloadController.getInstance(currentAccount).generateObserverTag();

        placeholderImageView = new ImageView(context);
        if (isPickerCell) {
            addView(placeholderImageView, LayoutHelper.createFrame(42, 42, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 15, 12, LocaleController.isRTL ? 15 : 0, 0));
        } else {
            addView(placeholderImageView, LayoutHelper.createFrame(40, 40, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 12, 8, LocaleController.isRTL ? 12 : 0, 0));
        }

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
        if (isPickerCell) {
            addView(extTextView, LayoutHelper.createFrame(32, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 20, 28, LocaleController.isRTL ? 20 : 0, 0));
        } else {
            addView(extTextView, LayoutHelper.createFrame(32, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 16, 22, LocaleController.isRTL ? 16 : 0, 0));
        }

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
        if (isPickerCell) {
            addView(thumbImageView, LayoutHelper.createFrame(42, 42, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 16, 12, LocaleController.isRTL ? 16 : 0, 0));
        } else {
            addView(thumbImageView, LayoutHelper.createFrame(40, 40, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 12, 8, LocaleController.isRTL ? 12 : 0, 0));
        }

        nameTextView = new TextView(context);
        nameTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        nameTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        nameTextView.setEllipsize(TextUtils.TruncateAt.END);
        nameTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        if (isPickerCell) {
            nameTextView.setLines(1);
            nameTextView.setMaxLines(1);
            nameTextView.setSingleLine(true);
            addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 8 : 72, 9, LocaleController.isRTL ? 72 : 8, 0));
        } else {
            nameTextView.setMaxLines(2);
            addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 8 : 72, 5, LocaleController.isRTL ? 72 : 8, 0));
        }

        statusImageView = new ImageView(context);
        statusImageView.setVisibility(INVISIBLE);
        statusImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_sharedMedia_startStopLoadIcon), PorterDuff.Mode.MULTIPLY));
        if (isPickerCell) {
            addView(statusImageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 8 : 72, 39, LocaleController.isRTL ? 72 : 8, 0));
        } else {
            addView(statusImageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 8 : 72, 35, LocaleController.isRTL ? 72 : 8, 0));
        }

        dateTextView = new TextView(context);
        dateTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
        dateTextView.setLines(1);
        dateTextView.setMaxLines(1);
        dateTextView.setSingleLine(true);
        dateTextView.setEllipsize(TextUtils.TruncateAt.END);
        dateTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        if (isPickerCell) {
            dateTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            addView(dateTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 8 : 72, 34, LocaleController.isRTL ? 72 : 8, 0));
        } else {
            dateTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            addView(dateTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 8 : 72, 30, LocaleController.isRTL ? 72 : 8, 0));
        }

        progressView = new LineProgressView(context);
        progressView.setProgressColor(Theme.getColor(Theme.key_sharedMedia_startStopLoadIcon));
        addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 2, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 72, 54, LocaleController.isRTL ? 72 : 0, 0));

        checkBox = new CheckBox2(context, 21);
        checkBox.setVisibility(INVISIBLE);
        checkBox.setColor(null, Theme.key_windowBackgroundWhite, Theme.key_checkboxCheck);
        checkBox.setDrawUnchecked(false);
        checkBox.setDrawBackgroundAsArc(2);
        if (isPickerCell) {
            addView(checkBox, LayoutHelper.createFrame(24, 24, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 38, 36, LocaleController.isRTL ? 38 : 0, 0));
        } else {
            addView(checkBox, LayoutHelper.createFrame(24, 24, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 33, 28, LocaleController.isRTL ? 33 : 0, 0));
        }
    }

    public void setTextAndValueAndTypeAndThumb(String text, String value, String type, String thumb, int resId, boolean divider) {
        nameTextView.setText(text);
        dateTextView.setText(value);
        if (type != null) {
            extTextView.setVisibility(VISIBLE);
            extTextView.setText(type.toLowerCase());
        } else {
            extTextView.setVisibility(INVISIBLE);
        }
        needDivider = divider;
        if (resId == 0) {
            placeholderImageView.setImageResource(AndroidUtilities.getThumbForNameOrMime(text, type, false));
            placeholderImageView.setVisibility(VISIBLE);
        } else {
            placeholderImageView.setVisibility(INVISIBLE);
        }
        if (thumb != null || resId != 0) {
            if (thumb != null) {
                thumbImageView.setImage(thumb, "42_42", null);
            } else {
                Drawable drawable = Theme.createCircleDrawableWithIcon(AndroidUtilities.dp(42), resId);
                String iconKey;
                String backKey;
                if (resId == R.drawable.files_storage) {
                    backKey = Theme.key_chat_attachLocationBackground;
                    iconKey = Theme.key_chat_attachLocationIcon;
                } else if (resId == R.drawable.files_gallery) {
                    backKey = Theme.key_chat_attachContactBackground;
                    iconKey = Theme.key_chat_attachContactIcon;
                } else if (resId == R.drawable.files_music) {
                    backKey = Theme.key_chat_attachAudioBackground;
                    iconKey = Theme.key_chat_attachAudioIcon;
                } else if (resId == R.drawable.files_internal) {
                    backKey = Theme.key_chat_attachGalleryBackground;
                    iconKey = Theme.key_chat_attachGalleryIcon;
                } else {
                    backKey = Theme.key_files_folderIconBackground;
                    iconKey = Theme.key_files_folderIcon;
                }
                Theme.setCombinedDrawableColor(drawable, Theme.getColor(backKey), false);
                Theme.setCombinedDrawableColor(drawable, Theme.getColor(iconKey), true);
                thumbImageView.setImageDrawable(drawable);
            }
            thumbImageView.setVisibility(VISIBLE);
        } else {
            extTextView.setAlpha(1.0f);
            placeholderImageView.setAlpha(1.0f);
            thumbImageView.setImageBitmap(null);
            thumbImageView.setVisibility(INVISIBLE);
        }
        setWillNotDraw(!needDivider);
    }

    public void setPhotoEntry(MediaController.PhotoEntry entry) {
        String path;
        if (entry.thumbPath != null) {
            thumbImageView.setImage(entry.thumbPath, null, Theme.chat_attachEmptyDrawable);
            path = entry.thumbPath;
        } else if (entry.path != null) {
            if (entry.isVideo) {
                thumbImageView.setOrientation(0, true);
                thumbImageView.setImage("vthumb://" + entry.imageId + ":" + entry.path, null, Theme.chat_attachEmptyDrawable);
            } else {
                thumbImageView.setOrientation(entry.orientation, true);
                thumbImageView.setImage("thumb://" + entry.imageId + ":" + entry.path, null, Theme.chat_attachEmptyDrawable);
            }
            path = entry.path;
        } else {
            thumbImageView.setImageDrawable(Theme.chat_attachEmptyDrawable);
            path = "";
        }

        File file = new File(path);
        nameTextView.setText(file.getName());
        String type = FileLoader.getFileExtension(file);
        StringBuilder builder = new StringBuilder();
        extTextView.setVisibility(GONE);
        if (entry.width != 0 && entry.height != 0) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(String.format(Locale.US, "%dx%d", entry.width, entry.height));
        }
        if (entry.isVideo) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(AndroidUtilities.formatShortDuration(entry.duration));
        }
        if (entry.size != 0) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(AndroidUtilities.formatFileSize(entry.size));
        }
        if (builder.length() > 0) {
            builder.append(", ");
        }
        builder.append(LocaleController.getInstance().formatterStats.format(entry.dateTaken));
        dateTextView.setText(builder);
        //placeholderImageView.setImageResource(AndroidUtilities.getThumbForNameOrMime(path, null, false));
        //placeholderImageView.setVisibility(VISIBLE);
        placeholderImageView.setVisibility(GONE);
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
        if (isPickerCell) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(64) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY));
        } else {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(56), MeasureSpec.EXACTLY));
            setMeasuredDimension(getMeasuredWidth(), AndroidUtilities.dp(5 + 29) + nameTextView.getMeasuredHeight() + (needDivider ? 1 : 0));
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (!isPickerCell && nameTextView.getLineCount() > 1) {
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
