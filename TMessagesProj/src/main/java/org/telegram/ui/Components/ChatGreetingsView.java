package org.telegram.ui.Components;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Random;

public class ChatGreetingsView extends LinearLayout {

    private TextView titleView;
    private TextView descriptionView;
    private Listener listener;

    public BackupImageView stickerToSendView;

    public ChatGreetingsView(Context context, TLRPC.User user, int distance, TLRPC.Document preloadedGreetingsSticker) {
        super(context);
        setOrientation(VERTICAL);

        titleView = new TextView(context);
        titleView.setTextSize(14);
        titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleView.setGravity(Gravity.CENTER_HORIZONTAL);

        descriptionView = new TextView(context);
        descriptionView.setTextSize(14);
        descriptionView.setGravity(Gravity.CENTER_HORIZONTAL);


        stickerToSendView = new BackupImageView(context);

        addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 20, 14, 20, 14));
        addView(descriptionView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 20, 12, 20, 0));
        addView(stickerToSendView, LayoutHelper.createLinear(112, 112, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 16));

        updateColors();

        titleView.setText(LocaleController.formatString("NearbyPeopleGreetingsMessage", R.string.NearbyPeopleGreetingsMessage, user.first_name, LocaleController.formatDistance(distance, 1)));
        descriptionView.setText(LocaleController.getString("NearbyPeopleGreetingsDescription", R.string.NearbyPeopleGreetingsDescription));

        if (preloadedGreetingsSticker == null) {
            TLRPC.TL_messages_getStickers req = new TLRPC.TL_messages_getStickers();
            req.emoticon = "\uD83D\uDC4B" + Emoji.fixEmoji("⭐");
            ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(req, (response, error) -> {
                if (response instanceof TLRPC.TL_messages_stickers) {
                    ArrayList<TLRPC.Document> list = ((TLRPC.TL_messages_stickers) response).stickers;
                    if (!list.isEmpty()) {
                        TLRPC.Document sticker = list.get(Math.abs(new Random().nextInt() % list.size()));
                        AndroidUtilities.runOnUIThread(() -> {
                            setSticker(sticker);
                        });
                    }
                }
            });

        } else {
            setSticker(preloadedGreetingsSticker);
        }
    }

    private void setSticker(TLRPC.Document sticker) {
        TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(sticker.thumbs, 90);


        stickerToSendView.setImage(
                ImageLocation.getForDocument(sticker), createFilter(sticker),
                ImageLocation.getForDocument(thumb, sticker), null,
                0, sticker
        );
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
        titleView.setTextColor(Theme.getColor(Theme.key_chat_serviceText));
        descriptionView.setTextColor(Theme.getColor(Theme.key_chat_serviceText));
        setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(10), Theme.getColor(Theme.key_chat_serviceBackground)));
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
}
