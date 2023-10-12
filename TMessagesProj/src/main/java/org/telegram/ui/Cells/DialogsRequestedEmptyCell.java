package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.StickerEmptyView;

public class DialogsRequestedEmptyCell extends LinearLayout implements NotificationCenter.NotificationCenterDelegate {

    int currentAccount = UserConfig.selectedAccount;
    BackupImageView stickerView;
    TextView titleView;
    TextView subtitleView;
    TextView buttonView;

    public DialogsRequestedEmptyCell(Context context) {
        super(context);

        setOrientation(VERTICAL);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        LinearLayout linearLayout = new LinearLayout(context) {
            Path path = new Path();
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            {
                paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                paint.setShadowLayer(dp(1.33f), 0, dp(.33f), 0x1e000000);
            }
            @Override
            protected void onDraw(Canvas canvas) {
                canvas.drawPath(path, paint);
                super.onDraw(canvas);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                path.rewind();
                AndroidUtilities.rectTmp.set(AndroidUtilities.dp(12), AndroidUtilities.dp(6), getMeasuredWidth() - AndroidUtilities.dp(12), getMeasuredHeight() - AndroidUtilities.dp(12));
                path.addRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(10), AndroidUtilities.dp(10), Path.Direction.CW);
            }
        };
        linearLayout.setWillNotDraw(false);
        linearLayout.setOrientation(VERTICAL);
        linearLayout.setPadding(AndroidUtilities.dp(12 + 20), AndroidUtilities.dp(6 + 10), AndroidUtilities.dp(12 + 20), AndroidUtilities.dp(12 +20));

        stickerView = new BackupImageView(context);
        stickerView.setOnClickListener(e -> {
            stickerView.getImageReceiver().startAnimation();
        });
        updateSticker();
        linearLayout.addView(stickerView, LayoutHelper.createLinear(130, 130, Gravity.CENTER_HORIZONTAL | Gravity.TOP));

        titleView = new TextView(context);
        titleView.setGravity(Gravity.CENTER);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        linearLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 6, 0, 0));

        subtitleView = new TextView(context);
        subtitleView.setGravity(Gravity.CENTER);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        linearLayout.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 7, 0, 0));

        buttonView = new TextView(context);
        buttonView.setGravity(Gravity.CENTER);
        buttonView.setBackground(Theme.AdaptiveRipple.filledRectByKey(Theme.key_featuredStickers_addButton, 8));
        buttonView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        buttonView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        buttonView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        buttonView.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(14), AndroidUtilities.dp(14), AndroidUtilities.dp(14));
        buttonView.setOnClickListener(e -> {
            onButtonClick();
        });
        linearLayout.addView(buttonView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 18, 0, 0));

        addView(linearLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        set(null);
    }

    protected void onButtonClick() {

    }

    public void set(TLRPC.RequestPeerType requestPeerType) {
        if (requestPeerType instanceof TLRPC.TL_requestPeerTypeBroadcast) {
            titleView.setText(LocaleController.getString("NoSuchChannels", R.string.NoSuchChannels));
            subtitleView.setText(LocaleController.getString("NoSuchChannelsInfo", R.string.NoSuchChannelsInfo));
            buttonView.setVisibility(View.VISIBLE);
            buttonView.setText(LocaleController.getString("CreateChannelForThis", R.string.CreateChannelForThis));
        } else if (requestPeerType instanceof TLRPC.TL_requestPeerTypeChat) {
            titleView.setText(LocaleController.getString("NoSuchGroups", R.string.NoSuchGroups));
            subtitleView.setText(LocaleController.getString("NoSuchGroupsInfo", R.string.NoSuchGroupsInfo));
            buttonView.setVisibility(View.VISIBLE);
            buttonView.setText(LocaleController.getString("CreateGroupForThis", R.string.CreateGroupForThis));
        } else {
            titleView.setText(LocaleController.getString("NoSuchUsers", R.string.NoSuchUsers));
            subtitleView.setText(LocaleController.getString("NoSuchUsersInfo", R.string.NoSuchUsersInfo));
            buttonView.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.diceStickersDidLoad);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.diceStickersDidLoad);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.diceStickersDidLoad) {
            String name = (String) args[0];
            if (AndroidUtilities.STICKERS_PLACEHOLDER_PACK_NAME.equals(name) && getVisibility() == VISIBLE) {
                updateSticker();
            }
        }
    }

    private void updateSticker() {
        final int stickerType = StickerEmptyView.STICKER_TYPE_SEARCH;

        TLRPC.Document document = null;
        TLRPC.TL_messages_stickerSet set = MediaDataController.getInstance(currentAccount).getStickerSetByName(AndroidUtilities.STICKERS_PLACEHOLDER_PACK_NAME);
        if (set == null) {
            set = MediaDataController.getInstance(currentAccount).getStickerSetByEmojiOrName(AndroidUtilities.STICKERS_PLACEHOLDER_PACK_NAME);
        }
        if (set != null && stickerType >= 0 && stickerType < set.documents.size() ) {
            document = set.documents.get(stickerType);
        }

        if (document != null) {
            SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(document.thumbs, Theme.key_windowBackgroundGray, 0.2f);
            if (svgThumb != null) {
                svgThumb.overrideWidthAndHeight(512, 512);
            }

            ImageLocation imageLocation = ImageLocation.getForDocument(document);
            stickerView.setImage(imageLocation, "130_130", "tgs", svgThumb, set);
            stickerView.getImageReceiver().setAutoRepeat(2);
        } else {
            MediaDataController.getInstance(currentAccount).loadStickersByEmojiOrName(AndroidUtilities.STICKERS_PLACEHOLDER_PACK_NAME, false, set == null);
            stickerView.getImageReceiver().clearImage();
        }
    }

}
