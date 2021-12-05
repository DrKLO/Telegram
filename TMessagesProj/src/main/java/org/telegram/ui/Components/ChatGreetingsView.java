package org.telegram.ui.Components;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

import java.util.Locale;

public class ChatGreetingsView extends LinearLayout {

    private TLRPC.Document preloadedGreetingsSticker;
    private TextView titleView;
    private TextView descriptionView;
    private Listener listener;

    private final int currentAccount;

    public BackupImageView stickerToSendView;
    private final Theme.ResourcesProvider resourcesProvider;

    public ChatGreetingsView(Context context, TLRPC.User user, int distance, int currentAccount, TLRPC.Document sticker, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        setOrientation(VERTICAL);
        this.currentAccount = currentAccount;
        this.resourcesProvider = resourcesProvider;

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleView.setGravity(Gravity.CENTER_HORIZONTAL);

        descriptionView = new TextView(context);
        descriptionView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        descriptionView.setGravity(Gravity.CENTER_HORIZONTAL);


        stickerToSendView = new BackupImageView(context);

        addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 20, 14, 20, 14));
        addView(descriptionView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 20, 12, 20, 0));
        addView(stickerToSendView, LayoutHelper.createLinear(112, 112, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 16));

        updateColors();

        if (distance <= 0) {
            titleView.setText(LocaleController.getString("NoMessages", R.string.NoMessages));
            descriptionView.setText(LocaleController.getString("NoMessagesGreetingsDescription", R.string.NoMessagesGreetingsDescription));
        } else {
            titleView.setText(LocaleController.formatString("NearbyPeopleGreetingsMessage", R.string.NearbyPeopleGreetingsMessage, user.first_name, LocaleController.formatDistance(distance, 1)));
            descriptionView.setText(LocaleController.getString("NearbyPeopleGreetingsDescription", R.string.NearbyPeopleGreetingsDescription));
        }

        preloadedGreetingsSticker = sticker;
        if (preloadedGreetingsSticker == null) {
            preloadedGreetingsSticker = MediaDataController.getInstance(currentAccount).getGreetingsSticker();
        }
        setSticker(preloadedGreetingsSticker);
    }

    private void setSticker(TLRPC.Document sticker) {
        if (sticker == null) {
            return;
        }
        SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(sticker, Theme.key_chat_serviceBackground, 1.0f);
        if (svgThumb != null) {
            stickerToSendView.setImage(ImageLocation.getForDocument(sticker), createFilter(sticker), svgThumb, 0, sticker);
        } else {
            TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(sticker.thumbs, 90);
            stickerToSendView.setImage(ImageLocation.getForDocument(sticker), createFilter(sticker), ImageLocation.getForDocument(thumb, sticker), null, 0, sticker);
        }
        stickerToSendView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onGreetings(sticker);
            }
        });
    }

    public static String createFilter(TLRPC.Document document) {
        float maxHeight;
        float maxWidth;
        int photoWidth = 0;
        int photoHeight = 0;
        if (AndroidUtilities.isTablet()) {
            maxHeight = maxWidth = AndroidUtilities.getMinTabletSide() * 0.4f;
        } else {
            maxHeight = maxWidth = Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.5f;
        }
        for (int a = 0; a < document.attributes.size(); a++) {
            TLRPC.DocumentAttribute attribute = document.attributes.get(a);
            if (attribute instanceof TLRPC.TL_documentAttributeImageSize) {
                photoWidth = attribute.w;
                photoHeight = attribute.h;
                break;
            }
        }
        if (MessageObject.isAnimatedStickerDocument(document, true) && photoWidth == 0 && photoHeight == 0) {
            photoWidth = photoHeight = 512;
        }

        if (photoWidth == 0) {
            photoHeight = (int) maxHeight;
            photoWidth = photoHeight + AndroidUtilities.dp(100);
        }
        photoHeight *= maxWidth / photoWidth;
        photoWidth = (int) maxWidth;
        if (photoHeight > maxHeight) {
            photoWidth *= maxHeight / photoHeight;
            photoHeight = (int) maxHeight;
        }

        int w = (int) (photoWidth / AndroidUtilities.density);
        int h = (int) (photoHeight / AndroidUtilities.density);
        return String.format(Locale.US, "%d_%d", w, h);
    }

    private void updateColors() {
        titleView.setTextColor(getThemedColor(Theme.key_chat_serviceText));
        descriptionView.setTextColor(getThemedColor(Theme.key_chat_serviceText));
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public interface Listener {
        void onGreetings(TLRPC.Document sticker);
    }

    boolean ignoreLayot;

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        ignoreLayot = true;
        descriptionView.setVisibility(View.VISIBLE);
        stickerToSendView.setVisibility(View.VISIBLE);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (getMeasuredHeight() > MeasureSpec.getSize(heightMeasureSpec)) {
            descriptionView.setVisibility(View.GONE);
            stickerToSendView.setVisibility(View.GONE);
        } else {
            descriptionView.setVisibility(View.VISIBLE);
            stickerToSendView.setVisibility(View.VISIBLE);
        }
        ignoreLayot = false;
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public void requestLayout() {
        if (ignoreLayot) {
            return;
        }
        super.requestLayout();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        fetchSticker();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
    }

    private void fetchSticker() {
        if (preloadedGreetingsSticker == null) {
            preloadedGreetingsSticker = MediaDataController.getInstance(currentAccount).getGreetingsSticker();
            setSticker(preloadedGreetingsSticker);
        }
    }

    private int getThemedColor(String key) {
        Integer color = resourcesProvider != null ? resourcesProvider.getColor(key) : null;
        return color != null ? color : Theme.getColor(key);
    }
}
