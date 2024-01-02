package org.telegram.ui.Components.Premium.boosts.cells;

import static org.telegram.messenger.AndroidUtilities.REPLACING_TAG_TYPE_LINKBOLD;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.Premium.GLIcon.GLIconRenderer;
import org.telegram.ui.Components.Premium.GLIcon.GLIconTextureView;
import org.telegram.ui.Components.Premium.StarParticlesView;

@SuppressLint("ViewConstructor")
public class HeaderCell extends FrameLayout {

    private final GLIconTextureView iconTextureView;
    private final StarParticlesView starParticlesView;
    private final TextView titleView;
    private final LinkSpanDrawable.LinksTextView subtitleView;
    private final Theme.ResourcesProvider resourcesProvider;
    private final LinearLayout linearLayout;
    private LinkSpanDrawable.LinkCollector links;

    public HeaderCell(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        iconTextureView = new GLIconTextureView(context, GLIconRenderer.DIALOG_STYLE) {
            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                setPaused(false);
            }

            @Override
            protected void onDetachedFromWindow() {
                super.onDetachedFromWindow();
                setPaused(true);
            }
        };
        Bitmap bitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(ColorUtils.blendARGB(Theme.getColor(Theme.key_premiumGradient2, resourcesProvider), Theme.getColor(Theme.key_dialogBackground, resourcesProvider), 0.5f));
        iconTextureView.setBackgroundBitmap(bitmap);
        iconTextureView.mRenderer.colorKey1 = Theme.key_premiumGradient2;
        iconTextureView.mRenderer.colorKey2 = Theme.key_premiumGradient1;
        iconTextureView.mRenderer.updateColors();
        linearLayout.addView(iconTextureView, LayoutHelper.createLinear(160, 160, Gravity.CENTER_HORIZONTAL));

        starParticlesView = new StarParticlesView(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                drawable.rect2.set(0, 0, getMeasuredWidth(), getMeasuredHeight() - AndroidUtilities.dp(52));
            }

            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                starParticlesView.setPaused(false);
            }

            @Override
            protected void onDetachedFromWindow() {
                super.onDetachedFromWindow();
                starParticlesView.setPaused(true);
            }
        };

        starParticlesView.drawable.useGradient = true;
        starParticlesView.drawable.useBlur = false;
        starParticlesView.drawable.forceMaxAlpha = true;
        starParticlesView.drawable.checkBounds = true;
        starParticlesView.drawable.init();
        iconTextureView.setStarParticlesView(starParticlesView);

        titleView = new TextView(context);
        titleView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        titleView.setGravity(Gravity.CENTER_HORIZONTAL);

        linearLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 24, -8, 24, 0));

        subtitleView = new LinkSpanDrawable.LinksTextView(context, links = new LinkSpanDrawable.LinkCollector(this), resourcesProvider);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        subtitleView.setGravity(Gravity.CENTER);
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        subtitleView.setMovementMethod(LinkMovementMethod.getInstance());
        subtitleView.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText, resourcesProvider));
        subtitleView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        linearLayout.addView(subtitleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 24, 8, 24, 18));

        setClipChildren(false);
        addView(starParticlesView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 234, Gravity.TOP));
        addView(linearLayout);
        setWillNotDraw(false);
    }

    public void setBoostViaGifsText() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    float cornerRadius = AndroidUtilities.dp(12);
                    outline.setRoundRect(0, 0, view.getWidth(), (int) (view.getHeight() + cornerRadius), cornerRadius);
                }
            });
            setClipToOutline(true);
        }
        MarginLayoutParams lp = (MarginLayoutParams) getLayoutParams();
        lp.topMargin = -AndroidUtilities.dp(6);
        setLayoutParams(lp);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
        titleView.setText(LocaleController.formatString("BoostingBoostsViaGifts", R.string.BoostingBoostsViaGifts));
        subtitleView.setText(LocaleController.formatString("BoostingGetMoreBoost", R.string.BoostingGetMoreBoost));
        subtitleView.setTextColor(Theme.getColor(Theme.key_dialogTextGray3, resourcesProvider));
    }

    public void setUsedGiftLinkText() {
        titleView.setText(LocaleController.formatString("BoostingUsedGiftLink", R.string.BoostingUsedGiftLink));
        subtitleView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("BoostingLinkUsed", R.string.BoostingLinkUsed)));
    }

    public void setGiftLinkText() {
        titleView.setText(LocaleController.formatString("BoostingGiftLink", R.string.BoostingGiftLink));
        subtitleView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("BoostingLinkAllows", R.string.BoostingLinkAllows)));
    }

    public void setUnclaimedText() {
        titleView.setText(LocaleController.formatString("BoostingGiftLink", R.string.BoostingGiftLink));
        subtitleView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("BoostingLinkAllowsAnyone", R.string.BoostingLinkAllowsAnyone)));
    }

    public void setGiftLinkToUserText(long toUserId, Utilities.Callback<TLObject> onObjectClicked) {
        titleView.setText(LocaleController.formatString("BoostingGiftLink", R.string.BoostingGiftLink));

        CharSequence description = AndroidUtilities.replaceTags(LocaleController.getString("BoostingLinkAllowsToUser", R.string.BoostingLinkAllowsToUser));
        TLRPC.User toUser = MessagesController.getInstance(UserConfig.selectedAccount).getUser(toUserId);

        SpannableStringBuilder link = AndroidUtilities.replaceSingleTag(
                "**" + UserObject.getUserName(toUser) + "**",
                Theme.key_chat_messageLinkIn, REPLACING_TAG_TYPE_LINKBOLD,
                () -> onObjectClicked.run(toUser),
                resourcesProvider
        );
        subtitleView.setText(AndroidUtilities.replaceCharSequence("%1$s", description, link));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        float y = iconTextureView.getTop() + iconTextureView.getMeasuredHeight() / 2f;
        starParticlesView.setTranslationY(y - starParticlesView.getMeasuredHeight() / 2f);
    }

    public void setPaused(boolean value) {
        iconTextureView.setPaused(value);
        starParticlesView.setPaused(value);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (links != null) {
            canvas.save();
            canvas.translate(subtitleView.getLeft(), subtitleView.getTop());
            if (links.draw(canvas)) {
                invalidate();
            }
            canvas.restore();
        }
    }
}
