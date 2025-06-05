package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

public class PremiumFeatureCell extends FrameLayout {

    private final SimpleTextView title;
    private final TextView description;
    public ImageView imageView;
    public final ImageView nextIcon;
    boolean drawDivider;
    public PremiumPreviewFragment.PremiumFeatureData data;

    public AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable imageDrawable;

    public PremiumFeatureCell(Context context) {
        this(context, null);
    }

    public PremiumFeatureCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        setClipChildren(false);
        linearLayout.setClipChildren(false);
        title = new SimpleTextView(context);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextSize(15);
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        linearLayout.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        description = new TextView(context);
        description.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        description.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
        description.setLineSpacing(AndroidUtilities.dp(2), 1f);
        linearLayout.addView(description, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 1, 0, 0));

        addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 62, 8, 48, 9));

        imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        addView(imageView, LayoutHelper.createFrame(28, 28, 0, 18, 12, 0, 0));

        nextIcon = new ImageView(context);
        nextIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        nextIcon.setImageResource(R.drawable.msg_arrowright);
        nextIcon.setColorFilter(Theme.getColor(Theme.key_switchTrack, resourcesProvider));
        addView(nextIcon, LayoutHelper.createFrame(24, 24, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 18, 0));
    }

    public void setData(PremiumPreviewFragment.PremiumFeatureData data, boolean drawDivider) {
        if (UserConfig.getInstance(UserConfig.selectedAccount).isPremium() && data.type == PremiumPreviewFragment.PREMIUM_FEATURE_EMOJI_STATUS && data.icon == R.drawable.filled_premium_status2) {
            nextIcon.setVisibility(View.GONE);
            if (imageDrawable == null) {
                imageDrawable = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(this, false, dp(24), AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW_STATIC);
                if (isAttachedToWindow()) {
                    imageDrawable.attach();
                }
            }
            TLRPC.User user = UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser();
            Long emojiStatusDocumentId = UserObject.getEmojiStatusDocumentId(user);
            setEmoji(emojiStatusDocumentId == null ? 0 : emojiStatusDocumentId, false);
        } else {
            nextIcon.setVisibility(View.VISIBLE);
            if (imageDrawable != null){
                imageDrawable.detach();
                imageDrawable = null;
            }
        }
        this.data = data;
        title.setText(data.title);
        description.setText(data.description);
        imageView.setImageResource(data.icon);
        this.drawDivider = drawDivider;
    }

    private Drawable premiumStar;
    public void setEmoji(long documentId, boolean animated) {
        if (imageDrawable == null) {
            imageDrawable = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(this, false, dp(24), AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW_STATIC);
            if (isAttachedToWindow()) {
                imageDrawable.attach();
            }
        }
        if (documentId == 0) {
            if (premiumStar == null) {
                premiumStar = getContext().getResources().getDrawable(R.drawable.msg_premium_prolfilestar).mutate();
                premiumStar.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlueIcon), PorterDuff.Mode.SRC_IN));
            }
            imageDrawable.set(premiumStar, animated);
        } else {
            imageDrawable.set(documentId, animated);
        }
    }

    public void updateImageBounds() {
        imageDrawable.setBounds(
            getWidth() - imageDrawable.getIntrinsicWidth() - dp(21),
            (getHeight() - imageDrawable.getIntrinsicHeight()) / 2,
            getWidth() - dp(21),
            (getHeight() + imageDrawable.getIntrinsicHeight()) / 2
        );
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (imageDrawable != null) {
            updateImageBounds();
            imageDrawable.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueIcon));
            imageDrawable.draw(canvas);
        }
        if (drawDivider) {
            canvas.drawRect(AndroidUtilities.dp(62), getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight(), Theme.dividerPaint);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        if (imageDrawable != null) {
            imageDrawable.attach();
        }
        super.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (imageDrawable != null) {
            imageDrawable.detach();
        }
        super.onDetachedFromWindow();
    }

    public static class Factory extends UItem.UItemFactory<PremiumFeatureCell> {
        static { setup(new Factory()); }

        @Override
        public PremiumFeatureCell createView(Context context, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new PremiumFeatureCell(context, resourcesProvider);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            ((PremiumFeatureCell) view).setData((PremiumPreviewFragment.PremiumFeatureData) item.object, divider);
        }

        public static UItem of(PremiumPreviewFragment.PremiumFeatureData data) {
            UItem item = UItem.ofFactory(Factory.class);
            item.object = data;
            return item;
        }
    }
}
