/*
 * This is the source code of Telegram for Android v. 2.0.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CheckBox;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LineProgressView;

import java.io.File;
import java.util.Date;

public class SharedDocumentCell extends FrameLayout implements MediaController.FileDownloadProgressListener {

    private ImageView placeholderImabeView;
    private BackupImageView thumbImageView;
    private TextView nameTextView;
    private TextView extTextView;
    private TextView dateTextView;
    private ImageView statusImageView;
    private LineProgressView progressView;
    private CheckBox checkBox;

    private boolean needDivider;

    private static Paint paint;

    private int TAG;

    private MessageObject message;
    private boolean loading;
    private boolean loaded;

    private int icons[] = {
            R.drawable.media_doc_blue,
            R.drawable.media_doc_green,
            R.drawable.media_doc_red,
            R.drawable.media_doc_yellow
    };

    public SharedDocumentCell(Context context) {
        super(context);

        if (paint == null) {
            paint = new Paint();
            paint.setColor(0xffd9d9d9);
            paint.setStrokeWidth(1);
        }

        TAG = MediaController.getInstance().generateObserverTag();

        placeholderImabeView = new ImageView(context);
        addView(placeholderImabeView, LayoutHelper.createFrame(40, 40, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 12, 8, LocaleController.isRTL ? 12 : 0, 0));

        extTextView = new TextView(context);
        extTextView.setTextColor(0xffffffff);
        extTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        extTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        extTextView.setLines(1);
        extTextView.setMaxLines(1);
        extTextView.setSingleLine(true);
        extTextView.setGravity(Gravity.CENTER);
        extTextView.setEllipsize(TextUtils.TruncateAt.END);
        addView(extTextView, LayoutHelper.createFrame(32, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 16, 22, LocaleController.isRTL ? 16 : 0, 0));

        thumbImageView = new BackupImageView(context);
        addView(thumbImageView, LayoutHelper.createFrame(40, 40, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 12, 8, LocaleController.isRTL ? 12 : 0, 0));
        thumbImageView.getImageReceiver().setDelegate(new ImageReceiver.ImageReceiverDelegate() {
            @Override
            public void didSetImage(ImageReceiver imageReceiver, boolean set, boolean thumb) {
                extTextView.setVisibility(set ? INVISIBLE : VISIBLE);
                placeholderImabeView.setVisibility(set ? INVISIBLE : VISIBLE);
            }
        });

        nameTextView = new TextView(context);
        nameTextView.setTextColor(0xff212121);
        nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        nameTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        nameTextView.setLines(1);
        nameTextView.setMaxLines(1);
        nameTextView.setSingleLine(true);
        nameTextView.setEllipsize(TextUtils.TruncateAt.END);
        nameTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 8 : 72, 5, LocaleController.isRTL ? 72 : 8, 0));

        statusImageView = new ImageView(context);
        statusImageView.setVisibility(INVISIBLE);
        addView(statusImageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 8 : 72, 35, LocaleController.isRTL ? 72 : 8, 0));

        dateTextView = new TextView(context);
        dateTextView.setTextColor(0xff999999);
        dateTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        dateTextView.setLines(1);
        dateTextView.setMaxLines(1);
        dateTextView.setSingleLine(true);
        dateTextView.setEllipsize(TextUtils.TruncateAt.END);
        dateTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        addView(dateTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 8 : 72, 30, LocaleController.isRTL ? 72 : 8, 0));

        progressView = new LineProgressView(context);
        addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 2, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 72, 54, LocaleController.isRTL ? 72 : 0, 0));

        checkBox = new CheckBox(context, R.drawable.round_check2);
        checkBox.setVisibility(INVISIBLE);
        addView(checkBox, LayoutHelper.createFrame(22, 22, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 34, 30, LocaleController.isRTL ? 34 : 0, 0));
    }

    private int getThumbForNameOrMime(String name, String mime) {
        if (name != null && name.length() != 0) {
            int color = -1;
            if (name.contains(".doc") || name.contains(".txt") || name.contains(".psd")) {
                color = 0;
            } else if (name.contains(".xls") || name.contains(".csv")) {
                color = 1;
            } else if (name.contains(".pdf") || name.contains(".ppt") || name.contains(".key")) {
                color = 2;
            } else if (name.contains(".zip") || name.contains(".rar") || name.contains(".ai") || name.contains(".mp3")  || name.contains(".mov") || name.contains(".avi")) {
                color = 3;
            }
            if (color == -1) {
                int idx;
                String ext = (idx = name.lastIndexOf('.')) == -1 ? "" : name.substring(idx + 1);
                if (ext.length() != 0) {
                    color = ext.charAt(0) % icons.length;
                } else {
                    color = name.charAt(0) % icons.length;
                }
            }
            return icons[color];
        }
        return icons[0];
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
            placeholderImabeView.setImageResource(getThumbForNameOrMime(text, type));
            placeholderImabeView.setVisibility(VISIBLE);
        } else {
            placeholderImabeView.setVisibility(INVISIBLE);
        }
        if (thumb != null || resId != 0) {
            if (thumb != null) {
                thumbImageView.setImage(thumb, "40_40", null);
            } else  {
                thumbImageView.setImageResource(resId);
            }
            thumbImageView.setVisibility(VISIBLE);
        } else {
            thumbImageView.setVisibility(INVISIBLE);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        MediaController.getInstance().removeLoadingFileObserver(this);
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

        if (messageObject != null && messageObject.getDocument() != null) {
            int idx;
            String name = null;
            if (messageObject.isMusic()) {
                TLRPC.Document document;
                if (messageObject.type == 0) {
                    document = messageObject.messageOwner.media.webpage.document;
                } else {
                    document = messageObject.messageOwner.media.document;
                }
                for (int a = 0; a < document.attributes.size(); a++) {
                    TLRPC.DocumentAttribute attribute = document.attributes.get(a);
                    if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                        if (attribute.performer != null && attribute.performer.length() != 0 || attribute.title != null && attribute.title.length() != 0) {
                            name = messageObject.getMusicAuthor() + " - " + messageObject.getMusicTitle();
                        }
                    }
                }
            }
            String fileName = FileLoader.getDocumentFileName(messageObject.getDocument());
            if (name == null) {
                name = fileName;
            }
            nameTextView.setText(name);
            placeholderImabeView.setVisibility(VISIBLE);
            extTextView.setVisibility(VISIBLE);
            placeholderImabeView.setImageResource(getThumbForNameOrMime(fileName, messageObject.getDocument().mime_type));
            extTextView.setText((idx = fileName.lastIndexOf('.')) == -1 ? "" : fileName.substring(idx + 1).toLowerCase());
            if (messageObject.getDocument().thumb instanceof TLRPC.TL_photoSizeEmpty || messageObject.getDocument().thumb == null) {
                thumbImageView.setVisibility(INVISIBLE);
                thumbImageView.setImageBitmap(null);
            } else {
                thumbImageView.setVisibility(VISIBLE);
                thumbImageView.setImage(messageObject.getDocument().thumb.location, "40_40", (Drawable) null);
            }
            long date = (long) messageObject.messageOwner.date * 1000;
            dateTextView.setText(String.format("%s, %s", AndroidUtilities.formatFileSize(messageObject.getDocument().size), LocaleController.formatString("formatDateAtTime", R.string.formatDateAtTime, LocaleController.getInstance().formatterYear.format(new Date(date)), LocaleController.getInstance().formatterDay.format(new Date(date)))));
        } else {
            nameTextView.setText("");
            extTextView.setText("");
            dateTextView.setText("");
            placeholderImabeView.setVisibility(VISIBLE);
            extTextView.setVisibility(VISIBLE);
            thumbImageView.setVisibility(INVISIBLE);
            thumbImageView.setImageBitmap(null);
        }

        setWillNotDraw(!needDivider);
        progressView.setProgress(0, false);
        updateFileExistIcon();
    }

    public void updateFileExistIcon() {
        if (message != null && message.messageOwner.media != null) {
            String fileName = null;
            File cacheFile;
            if (message.messageOwner.attachPath == null || message.messageOwner.attachPath.length() == 0 || !(new File(message.messageOwner.attachPath).exists())) {
                cacheFile = FileLoader.getPathToMessage(message.messageOwner);
                if (!cacheFile.exists()) {
                    fileName = FileLoader.getAttachFileName(message.getDocument());
                }
            }
            loaded = false;
            if (fileName == null) {
                statusImageView.setVisibility(INVISIBLE);
                dateTextView.setPadding(0, 0, 0, 0);
                loading = false;
                loaded = true;
                MediaController.getInstance().removeLoadingFileObserver(this);
            } else {
                MediaController.getInstance().addLoadingFileObserver(fileName, this);
                loading = FileLoader.getInstance().isLoadingFile(fileName);
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
            MediaController.getInstance().removeLoadingFileObserver(this);
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

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(56) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (needDivider) {
            canvas.drawLine(AndroidUtilities.dp(72), getHeight() - 1, getWidth() - getPaddingRight(), getHeight() - 1, paint);
        }
    }

    @Override
    public void onFailedDownload(String name) {
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
}
