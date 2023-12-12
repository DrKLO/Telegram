package org.telegram.ui.Components.Premium.boosts.cells;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EffectsTextView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.TextStyleSpan;
import org.telegram.ui.Components.spoilers.SpoilerEffect2;
import org.telegram.ui.Components.spoilers.SpoilersTextView;

@SuppressLint("ViewConstructor")
public class LinkCell extends FrameLayout {

    private SpoilersTextView linkView;
    private FrameLayout linkContainer;
    private String slug;
    private String link;
    private ImageView imageView;

    public LinkCell(@NonNull Context context, BaseFragment fragment, Theme.ResourcesProvider resourcesProvider) {
        super(context);

        linkContainer = new FrameLayout(context);
        linkView = new SpoilersTextView(context);
        linkView.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(13), AndroidUtilities.dp(50), AndroidUtilities.dp(13));
        linkView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        linkView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        linkView.setSingleLine(true);
        linkView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        linkView.allowClickSpoilers = false;

        linkContainer.addView(linkView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        linkContainer.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(8), Theme.getColor(Theme.key_graySection, resourcesProvider), ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_listSelector, resourcesProvider), (int) (255 * 0.3f))));
        addView(linkContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 14, 0, 14, 0));

        linkContainer.setOnClickListener(v -> AndroidUtilities.addToClipboard(link));

        imageView = new ImageView(getContext());
        imageView.setImageResource(R.drawable.menu_copy_s);
        imageView.setColorFilter(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        imageView.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
        imageView.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(20), 0, ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_listSelector, resourcesProvider), (int) (255 * 0.3f))));
        addView(imageView, LayoutHelper.createFrame(40, 40, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 15, 0, 17, 0));
        imageView.setOnClickListener(v -> AndroidUtilities.addToClipboard(link));
    }

    public void setSlug(String slug) {
        this.slug = slug;
        this.link = "https://t.me/giftcode/" + slug;
        linkView.setText("t.me/giftcode/" + slug);
    }

    public void hideSlug(Runnable onHiddenLinkClicked) {
        imageView.setVisibility(INVISIBLE);
        linkView.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(14), AndroidUtilities.dp(14), AndroidUtilities.dp(18));
        TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
        run.flags |= TextStyleSpan.FLAG_STYLE_SPOILER;
        SpannableStringBuilder builder = new SpannableStringBuilder("t.me/giftcode/" + slug);
        if (slug == null) {
            String stub = "1234567891011123654897566536223";
            builder.append(stub);
        }
        builder.setSpan(new TextStyleSpan(run), 0, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        linkView.setText(builder);
        linkContainer.setOnClickListener(v -> onHiddenLinkClicked.run());
    }
}
