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
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.transition.ChangeBounds;
import android.transition.Fade;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.DotDividerSpan;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LineProgressView;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.FilteredSearchView;

import java.io.File;
import java.util.Date;
import java.util.Locale;

public class SharedDocumentCell extends FrameLayout implements DownloadController.FileDownloadProgressListener {

    private ImageView placeholderImageView;
    private BackupImageView thumbImageView;
    private TextView nameTextView;
    private TextView extTextView;
    private AnimatedEmojiSpan.TextViewEmojis dateTextView;
    private RLottieImageView statusImageView;
    private LineProgressView progressView;
    private CheckBox2 checkBox;
    public TextView rightDateTextView;
    private TextView captionTextView;

    private boolean drawDownloadIcon = true;

    private boolean needDivider;

    private int currentAccount = UserConfig.selectedAccount;
    private int TAG;

    private MessageObject message;
    private boolean loading;
    private boolean loaded;

    private int viewType;

    public final static int VIEW_TYPE_DEFAULT = 0;
    public final static int VIEW_TYPE_PICKER = 1;
    public final static int VIEW_TYPE_GLOBAL_SEARCH = 2;
    public final static int VIEW_TYPE_CACHE = 3;

    private SpannableStringBuilder dotSpan;
    private CharSequence caption;
    private RLottieDrawable statusDrawable;
    private final Theme.ResourcesProvider resourcesProvider;
    FlickerLoadingView globalGradientView;
    private long downloadedSize;
    boolean showReorderIcon;
    float showReorderIconProgress;

    public SharedDocumentCell(Context context) {
        this(context, VIEW_TYPE_DEFAULT);
    }

    public SharedDocumentCell(Context context, int viewType) {
        this(context, viewType, null);
    }

    public SharedDocumentCell(Context context, int viewType, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        this.viewType = viewType;
        TAG = DownloadController.getInstance(currentAccount).generateObserverTag();

        placeholderImageView = new ImageView(context);
        if (viewType == VIEW_TYPE_PICKER) {
            addView(placeholderImageView, LayoutHelper.createFrame(42, 42, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 15, 12, LocaleController.isRTL ? 15 : 0, 0));
        } else {
            addView(placeholderImageView, LayoutHelper.createFrame(40, 40, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 12, 8, LocaleController.isRTL ? 12 : 0, 0));
        }

        extTextView = new TextView(context);
        extTextView.setTextColor(getThemedColor(Theme.key_files_iconText));
        extTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        extTextView.setTypeface(AndroidUtilities.bold());
        extTextView.setLines(1);
        extTextView.setMaxLines(1);
        extTextView.setSingleLine(true);
        extTextView.setGravity(Gravity.CENTER);
        extTextView.setEllipsize(TextUtils.TruncateAt.END);
        extTextView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        if (viewType == VIEW_TYPE_PICKER) {
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
        if (viewType == VIEW_TYPE_PICKER) {
            addView(thumbImageView, LayoutHelper.createFrame(42, 42, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 16, 12, LocaleController.isRTL ? 16 : 0, 0));
        } else {
            addView(thumbImageView, LayoutHelper.createFrame(40, 40, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 12, 8, LocaleController.isRTL ? 12 : 0, 0));
        }

        nameTextView = new TextView(context);
        nameTextView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        nameTextView.setTypeface(AndroidUtilities.bold());
        nameTextView.setEllipsize(TextUtils.TruncateAt.END);
        nameTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);

        LinearLayout linearLayout;
        if (viewType == VIEW_TYPE_PICKER) {
            nameTextView.setLines(1);
            nameTextView.setMaxLines(1);
            nameTextView.setSingleLine(true);
            addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 8 : 72, 9, LocaleController.isRTL ? 72 : 8, 0));
        } else if (viewType == VIEW_TYPE_GLOBAL_SEARCH) {
            linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(LinearLayout.HORIZONTAL);
            addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 16 : 72, 5, LocaleController.isRTL ? 72 : 16, 0));

            rightDateTextView = new TextView(context);
            rightDateTextView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText3));
            rightDateTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            if (!LocaleController.isRTL) {
                linearLayout.addView(nameTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 1f));
                linearLayout.addView(rightDateTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0f, 4, 0, 0, 0));
            } else {
                linearLayout.addView(rightDateTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0f));
                linearLayout.addView(nameTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 4, 0));
            }
            nameTextView.setMaxLines(2);

            captionTextView = new TextView(context);
            captionTextView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            captionTextView.setLines(1);
            captionTextView.setMaxLines(1);
            captionTextView.setSingleLine(true);
            captionTextView.setEllipsize(TextUtils.TruncateAt.END);
            captionTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
            captionTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            addView(captionTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 8 : 72, 30, LocaleController.isRTL ? 72 : 8, 0));
            captionTextView.setVisibility(View.GONE);
        } else {
            nameTextView.setMaxLines(1);
            addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 8 : 72, 5, LocaleController.isRTL ? 72 : 8, 0));
        }

        statusDrawable = new RLottieDrawable(R.raw.download_arrow, "download_arrow", AndroidUtilities.dp(14), AndroidUtilities.dp(14), true, null);
        statusImageView = new RLottieImageView(context);
        statusImageView.setAnimation(statusDrawable);
        statusImageView.setVisibility(INVISIBLE);
        statusImageView.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_sharedMedia_startStopLoadIcon), PorterDuff.Mode.MULTIPLY));
        if (viewType == VIEW_TYPE_PICKER) {
            addView(statusImageView, LayoutHelper.createFrame(14, 14, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 8 : 70, 37, LocaleController.isRTL ? 72 : 8, 0));
        } else {
            addView(statusImageView, LayoutHelper.createFrame(14, 14, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 8 : 70, 33, LocaleController.isRTL ? 72 : 8, 0));
        }

        dateTextView = new AnimatedEmojiSpan.TextViewEmojis(context);
        dateTextView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText3));
        dateTextView.setLines(1);
        dateTextView.setMaxLines(1);
        dateTextView.setSingleLine(true);
        dateTextView.setEllipsize(TextUtils.TruncateAt.END);
        dateTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        NotificationCenter.listenEmojiLoading(dateTextView);
        if (viewType == VIEW_TYPE_PICKER) {
            dateTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            addView(dateTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 8 : 72, 34, LocaleController.isRTL ? 72 : 8, 0));
        } else {
            dateTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            addView(dateTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 8 : 72, 30, LocaleController.isRTL ? 72 : 8, 0));
        }

        progressView = new LineProgressView(context);
        progressView.setProgressColor(getThemedColor(Theme.key_sharedMedia_startStopLoadIcon));
        addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 2, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 72, 54, LocaleController.isRTL ? 72 : 0, 0));

        checkBox = new CheckBox2(context, 21, resourcesProvider);
        checkBox.setVisibility(INVISIBLE);
        checkBox.setColor(-1, Theme.key_windowBackgroundWhite, Theme.key_checkboxCheck);
        checkBox.setDrawUnchecked(false);
        checkBox.setDrawBackgroundAsArc(2);
        if (viewType == VIEW_TYPE_PICKER) {
            addView(checkBox, LayoutHelper.createFrame(24, 24, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 38, 36, LocaleController.isRTL ? 38 : 0, 0));
        } else {
            addView(checkBox, LayoutHelper.createFrame(24, 24, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 33, 28, LocaleController.isRTL ? 33 : 0, 0));
        }

        if (viewType == VIEW_TYPE_GLOBAL_SEARCH) {
            dotSpan = new SpannableStringBuilder(".");
            dotSpan.setSpan(new DotDividerSpan(), 0, 1, 0);
        }
    }

    public void setDrawDownloadIcon(boolean value) {
        drawDownloadIcon = value;
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
                if (viewType != VIEW_TYPE_CACHE) {
                    thumbImageView.setImage(thumb, "42_42", null);
                }
            } else {
                Drawable drawable = Theme.createCircleDrawableWithIcon(AndroidUtilities.dp(42), resId);
                int iconKey;
                int backKey;
                if (resId == R.drawable.files_storage) {
                    backKey = Theme.key_chat_attachLocationBackground;
                    iconKey = Theme.key_chat_attachIcon;
                } else if (resId == R.drawable.files_gallery) {
                    backKey = Theme.key_chat_attachContactBackground;
                    iconKey = Theme.key_chat_attachIcon;
                } else if (resId == R.drawable.files_music) {
                    backKey = Theme.key_chat_attachAudioBackground;
                    iconKey = Theme.key_chat_attachIcon;
                } else if (resId == R.drawable.files_internal) {
                    backKey = Theme.key_chat_attachGalleryBackground;
                    iconKey = Theme.key_chat_attachIcon;
                } else {
                    backKey = Theme.key_files_folderIconBackground;
                    iconKey = Theme.key_files_folderIcon;
                }
                Theme.setCombinedDrawableColor(drawable, getThemedColor(backKey), false);
                Theme.setCombinedDrawableColor(drawable, getThemedColor(iconKey), true);
                thumbImageView.setImageDrawable(drawable);
            }
            thumbImageView.setVisibility(VISIBLE);
        } else {
            extTextView.setAlpha(1.0f);
            placeholderImageView.setAlpha(1.0f);
            if (viewType != VIEW_TYPE_CACHE) {
                thumbImageView.setImageBitmap(null);
                thumbImageView.setVisibility(INVISIBLE);
            }
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
                thumbImageView.setOrientation(entry.orientation, entry.invert, true);
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
        extTextView.setVisibility(GONE);
        StringBuilder builder = new StringBuilder();
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
        builder.append(LocaleController.getInstance().getFormatterStats().format(entry.dateTaken));
        dateTextView.setText(builder);
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
            updateFileExistIcon(false);
        }
    }

    public void setChecked(boolean checked, boolean animated) {
        if (checkBox.getVisibility() != VISIBLE) {
            checkBox.setVisibility(VISIBLE);
        }
        checkBox.setChecked(checked, animated);
    }

    public void setDocument(MessageObject messageObject, boolean divider) {
        boolean animated = message != null && messageObject != null && message.getId() != messageObject.getId();
        needDivider = divider;
        message = messageObject;
        loaded = false;
        loading = false;
        if (!animated) {
            downloadedSize = 0;
        }

        TLRPC.Document document = messageObject.getDocument();
        if (document != null) {
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
            String fileName = null;
            if (!messageObject.isVideo() && !(messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) && !MessageObject.isGifDocument(document)) {
                fileName = FileLoader.getDocumentFileName(document);
            }
            if (TextUtils.isEmpty(fileName) && document.mime_type != null) {
                if (document.mime_type.startsWith("video")) {
                    if (MessageObject.isGifDocument(document)) {
                        fileName = LocaleController.getString(R.string.AttachGif);
                    } else {
                        fileName = LocaleController.getString(R.string.AttachVideo);
                    }
                } else if (document.mime_type.startsWith("image")) {
                    if (MessageObject.isGifDocument(document)) {
                        fileName = LocaleController.getString(R.string.AttachGif);
                    } else {
                        fileName = LocaleController.getString(R.string.AttachPhoto);
                    }
                } else if (document.mime_type.startsWith("audio")) {
                    fileName = LocaleController.getString(R.string.AttachAudio);
                } else {
                    fileName = LocaleController.getString(R.string.AttachDocument);
                }
            }
            if (name == null) {
                name = fileName;
            }
            CharSequence nameH = AndroidUtilities.highlightText(name, messageObject.highlightedWords, resourcesProvider);
            if (nameH != null) {
                nameTextView.setText(nameH);
            } else {
                nameTextView.setText(name);
            }

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
                if (messageObject.strippedThumb != null) {
                    thumbImageView.setImage(ImageLocation.getForDocument(bigthumb, document), "40_40", null, null, messageObject.strippedThumb, null, null, 1, messageObject);
                } else {
                    thumbImageView.setImage(ImageLocation.getForDocument(bigthumb, document), "40_40", ImageLocation.getForDocument(thumb, document), "40_40_b", null, 0, 1, messageObject);
                }
            }
            updateDateView();

            if (messageObject.hasHighlightedWords() && !TextUtils.isEmpty(message.messageOwner.message)) {
                String str = message.messageOwner.message.replace("\n", " ").replaceAll(" +", " ").trim();
                caption = AndroidUtilities.highlightText(str, message.highlightedWords, resourcesProvider);
                if (captionTextView != null) {
                    captionTextView.setVisibility(caption == null ? View.GONE : View.VISIBLE);
                }
            } else {
                if (captionTextView != null) {
                    captionTextView.setVisibility(View.GONE);
                }
            }
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
            caption = null;
            if (captionTextView != null) {
                captionTextView.setVisibility(View.GONE);
            }
        }

        setWillNotDraw(!needDivider);
        progressView.setProgress(0, false);
        updateFileExistIcon(animated);
    }

    private void updateDateView() {
        if (message == null || message.getDocument() == null) {
            return;
        }
        long date = (long) message.messageOwner.date * 1000;
        String fileSize = null;
        if (downloadedSize == 0) {
            fileSize = AndroidUtilities.formatFileSize(message.getDocument().size);
        } else {
            fileSize = String.format(Locale.ENGLISH, "%s / %s", AndroidUtilities.formatFileSize(downloadedSize), AndroidUtilities.formatFileSize(message.getDocument().size));
        }
        if (viewType == VIEW_TYPE_GLOBAL_SEARCH) {
            CharSequence fromName = FilteredSearchView.createFromInfoString(message, true, 2, dateTextView.getPaint());

            dateTextView.setText(new SpannableStringBuilder().append(fileSize)
                    .append(' ').append(dotSpan).append(' ')
                    .append(fromName));
            rightDateTextView.setText(LocaleController.stringForMessageListDate(message.messageOwner.date));
        } else {
            dateTextView.setText(String.format("%s, %s", fileSize, LocaleController.formatString("formatDateAtTime", R.string.formatDateAtTime, LocaleController.getInstance().getFormatterYear().format(new Date(date)), LocaleController.getInstance().getFormatterDay().format(new Date(date)))));
        }
    }

    public void updateFileExistIcon(boolean animated) {
        if (animated && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            TransitionSet transition = new TransitionSet();
            ChangeBounds changeBounds = new ChangeBounds();
            changeBounds.setDuration(150);
            transition.addTransition(new Fade().setDuration(150)).addTransition(changeBounds);
            transition.setOrdering(TransitionSet.ORDERING_TOGETHER);
            transition.setInterpolator(CubicBezierInterpolator.DEFAULT);
            TransitionManager.beginDelayedTransition(this, transition);
        }
        if (message != null && message.messageOwner.media != null) {
            loaded = false;
            if (message.attachPathExists || message.mediaExists || !drawDownloadIcon) {
                statusImageView.setVisibility(INVISIBLE);
                progressView.setVisibility(INVISIBLE);

                LayoutParams layoutParams = (LayoutParams) dateTextView.getLayoutParams();
                if (layoutParams != null) {
                    layoutParams.leftMargin = AndroidUtilities.dp(LocaleController.isRTL ? 8 : 72);
                    layoutParams.rightMargin = AndroidUtilities.dp(LocaleController.isRTL ? 72 : 8);
                    dateTextView.requestLayout();
                }
                loading = false;
                loaded = true;
                DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
            } else {
                String fileName = FileLoader.getAttachFileName(message.getDocument());
                DownloadController.getInstance(currentAccount).addLoadingFileObserver(fileName, message, this);
                loading = FileLoader.getInstance(currentAccount).isLoadingFile(fileName);
                statusImageView.setVisibility(VISIBLE);
                statusDrawable.setCustomEndFrame(loading ? 15 : 0);
                statusDrawable.setPlayInDirectionOfCustomEndFrame(true);
                if (animated) {
                    statusImageView.playAnimation();
                } else {
                    statusDrawable.setCurrentFrame(loading ? 15 : 0);
                    statusImageView.invalidate();
                }
                LayoutParams layoutParams = (LayoutParams) dateTextView.getLayoutParams();
                if (layoutParams != null) {
                    layoutParams.leftMargin = AndroidUtilities.dp(LocaleController.isRTL ? 8 : (72 + 14));
                    layoutParams.rightMargin = AndroidUtilities.dp(LocaleController.isRTL ? (72 + 14) : 8);
                    dateTextView.requestLayout();
                }
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
            LayoutParams layoutParams = (LayoutParams) dateTextView.getLayoutParams();
            if (layoutParams != null) {
                layoutParams.leftMargin = AndroidUtilities.dp(LocaleController.isRTL ? 8 : 72);
                layoutParams.rightMargin = AndroidUtilities.dp(LocaleController.isRTL ? 72 : 8);
                dateTextView.requestLayout();
            }
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
        if (viewType == VIEW_TYPE_PICKER) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(64) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY));
        } else if (viewType == VIEW_TYPE_DEFAULT) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(56), MeasureSpec.EXACTLY));
        } else {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(56), MeasureSpec.EXACTLY));
            int h = AndroidUtilities.dp(5 + 29) + nameTextView.getMeasuredHeight() + (needDivider ? 1 : 0);
            if (caption != null && captionTextView != null && message.hasHighlightedWords()) {
                ignoreRequestLayout = true;
                captionTextView.setText(AndroidUtilities.ellipsizeCenterEnd(caption, message.highlightedWords.get(0), captionTextView.getMeasuredWidth(), captionTextView.getPaint(), 130));
                ignoreRequestLayout = false;

                h += captionTextView.getMeasuredHeight() + AndroidUtilities.dp(3);
            }
            setMeasuredDimension(getMeasuredWidth(), h);
        }
    }

    boolean ignoreRequestLayout;

    @Override
    public void requestLayout() {
        if (ignoreRequestLayout) {
            return;
        }
        super.requestLayout();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (viewType != VIEW_TYPE_PICKER && ((nameTextView.getLineCount() > 1 || (captionTextView != null && captionTextView.getVisibility() == View.VISIBLE)))) {
            int y = nameTextView.getMeasuredHeight() - AndroidUtilities.dp(22);
            if (captionTextView != null && captionTextView.getVisibility() == View.VISIBLE) {
                captionTextView.layout(captionTextView.getLeft(), y + captionTextView.getTop(), captionTextView.getRight(), y + captionTextView.getBottom());
                y += captionTextView.getMeasuredHeight() + AndroidUtilities.dp(3);
            }
            dateTextView.layout(dateTextView.getLeft(), y + dateTextView.getTop(), dateTextView.getRight(), y + dateTextView.getBottom());
            statusImageView.layout(statusImageView.getLeft(), y + statusImageView.getTop(), statusImageView.getRight(), y + statusImageView.getBottom());
            progressView.layout(progressView.getLeft(), getMeasuredHeight() - progressView.getMeasuredHeight() - (needDivider ? 1 : 0), progressView.getRight(), getMeasuredHeight() - (needDivider ? 1 : 0));
        }
    }


    @Override
    public void onFailedDownload(String name, boolean canceled) {
        updateFileExistIcon(true);
        downloadedSize = 0;
        updateDateView();
    }

    @Override
    public void onSuccessDownload(String name) {
        progressView.setProgress(1, true);
        updateFileExistIcon(true);
        downloadedSize = 0;
        updateDateView();
    }

    @Override
    public void onProgressDownload(String fileName, long downloadedSize, long totalSize) {
        if (progressView.getVisibility() != VISIBLE) {
            updateFileExistIcon(true);
        }
        this.downloadedSize = downloadedSize;
        updateDateView();
        progressView.setProgress(Math.min(1f, downloadedSize / (float) totalSize), true);
    }

    @Override
    public void onProgressUpload(String fileName, long uploadedSize, long totalSize, boolean isEncrypted) {

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
            info.setChecked(checkBox.isChecked());
        }
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    float enterAlpha = 1f;

    public void setGlobalGradientView(FlickerLoadingView globalGradientView) {
        this.globalGradientView = globalGradientView;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (enterAlpha != 1f && globalGradientView != null) {
            canvas.saveLayerAlpha(0, 0, getMeasuredWidth(), getMeasuredHeight(), (int) ((1f - enterAlpha) * 255), Canvas.ALL_SAVE_FLAG);
            globalGradientView.setViewType(FlickerLoadingView.FILES_TYPE);
            globalGradientView.updateColors();
            globalGradientView.updateGradient();
            globalGradientView.draw(canvas);
            canvas.restore();
            canvas.saveLayerAlpha(0, 0, getMeasuredWidth(), getMeasuredHeight(), (int) (enterAlpha * 255), Canvas.ALL_SAVE_FLAG);
            super.dispatchDraw(canvas);
            drawDivider(canvas);
            canvas.restore();
        } else {
            super.dispatchDraw(canvas);
            drawDivider(canvas);
        }

        if (showReorderIcon || showReorderIconProgress != 0) {
            if (showReorderIcon && showReorderIconProgress != 1f) {
                showReorderIconProgress += 16 / 150f;
                invalidate();
            } else if (!showReorderIcon && showReorderIconProgress != 0) {
                showReorderIconProgress -= 16 / 150f;
                invalidate();
            }
            showReorderIconProgress = Utilities.clamp(showReorderIconProgress, 1f, 0);

            int x = getMeasuredWidth() - AndroidUtilities.dp(12) - Theme.dialogs_reorderDrawable.getIntrinsicWidth();
            int y = (getMeasuredHeight() - Theme.dialogs_reorderDrawable.getIntrinsicHeight()) >> 1;

            canvas.save();
            canvas.scale(showReorderIconProgress, showReorderIconProgress, x + Theme.dialogs_reorderDrawable.getIntrinsicWidth() / 2f, y + Theme.dialogs_reorderDrawable.getIntrinsicHeight() / 2f);
            Theme.dialogs_reorderDrawable.setBounds(x, y, x + Theme.dialogs_reorderDrawable.getIntrinsicWidth(), y + Theme.dialogs_reorderDrawable.getIntrinsicHeight());
            Theme.dialogs_reorderDrawable.draw(canvas);
            canvas.restore();
        }
    }

    private void drawDivider(Canvas canvas) {
        if (needDivider) {
            canvas.drawLine(AndroidUtilities.dp(72), getHeight() - 1, getWidth() - getPaddingRight(), getHeight() - 1, Theme.getThemePaint(Theme.key_paint_divider, resourcesProvider));
        }
    }

    public void setEnterAnimationAlpha(float alpha) {
        if (enterAlpha != alpha) {
            this.enterAlpha = alpha;
            invalidate();
        }
    }

    public void showReorderIcon(boolean show, boolean animated) {
        if (showReorderIcon == show) {
            return;
        }
        showReorderIcon = show;
        if (!animated) {
            showReorderIconProgress = show ? 1f : 0;
        }
        invalidate();
    }

    public void setPhoto(String path) {
        if (path.endsWith("mp4")) {
            thumbImageView.setImage("vthumb://0:" + path, null, null);
            thumbImageView.setVisibility(View.VISIBLE);
        } else if (path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png") || path.endsWith(".gif")) {
            thumbImageView.setImage("thumb://0:" + path, null, null);
            thumbImageView.setVisibility(View.VISIBLE);
        } else {
            thumbImageView.setVisibility(View.GONE);
        }
    }
}
