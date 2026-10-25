package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;


import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Wallet.GramWallet;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class ProfileBalanceView extends ProfileMusicView {
    private final Drawable gramIcon;
    private final Drawable gramColorIcon;
    private final Text label;
    private Text amount;
    private final Paint chevronPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path chevron = new Path();
    private float ejectProgress;
    private final DecimalFormat balanceFormat = new DecimalFormat("#,##0.#########", new DecimalFormatSymbols(Locale.US));

    public ProfileBalanceView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);
        gramIcon = context.getResources().getDrawable(R.drawable.profile_balance_gram).mutate();
        gramColorIcon = context.getResources().getDrawable(R.drawable.profile_balance_gram_color).mutate();
        label = new Text(getString(R.string.StarsBalance) + ":", 11);
        chevronPaint.setStyle(Paint.Style.STROKE);
        chevronPaint.setStrokeCap(Paint.Cap.ROUND);
        chevronPaint.setStrokeJoin(Paint.Join.ROUND);
        chevronPaint.setStrokeWidth(dpf2(1.16f));
        chevron.moveTo(0, -dpf2(3.33f));
        chevron.lineTo(dpf2(3.16f), 0);
        chevron.lineTo(0, dpf2(3.33f));
        setBalance(0);
    }

    public void setBalance(long nanograms) {
        final String formatted = balanceFormat.format(new BigDecimal(GramWallet.formatGramsBalance(nanograms, false)));
        amount = new Text(formatted, 11, AndroidUtilities.bold());
        setContentDescription(getString(R.string.StarsBalance) + ": " + formatted);
        invalidate();
    }

    /** Pushes the content up out of the pill, as if the wallet card is being ejected from it. */
    public void setEjectProgress(float progress) {
        if (ejectProgress == progress) return;
        ejectProgress = progress;
        invalidate();
    }

    @Override
    protected float getContentWidth(float maxWidth) {
        label.ellipsize(-1);
        amount.ellipsize(-1);
        final float available = Math.max(0, maxWidth - dp(16 + 4 + 13 + 2 + 8));
        // Prefer the numeric balance when a translation or a large amount needs more room.
        label.ellipsize(Math.max(0, available - amount.getWidth()));
        amount.ellipsize(Math.max(0, available - label.getWidth()));
        return label.getWidth() + dp(4 + 13 + 2 + 8) + amount.getWidth();
    }

    @Override
    protected void drawContent(Canvas canvas, float alpha) {
        final float cy = getHeight() / 2f;
        canvas.translate(0, -dp(20) * ejectProgress);
        label.draw(canvas, 0, cy, textColor, 0.85f * alpha);
        canvas.translate(label.getWidth() + dp(4), 0);
        final Drawable icon = hasProfileColor || isOnProfilePhoto() ? gramColorIcon : gramIcon;
        final int size = dp(13);
        icon.setBounds(0, (int) cy - size / 2, size, (int) cy - size / 2 + size);
        icon.setAlpha((int) (255 * alpha));
        icon.draw(canvas);
        canvas.translate(size + dp(2), 0);
        amount.draw(canvas, 0, cy, textColor, alpha);
        canvas.translate(amount.getWidth() + dpf2(4.8f), cy);
        chevronPaint.setColor(Theme.multAlpha(textColor, 0.85f * alpha));
        canvas.drawPath(chevron, chevronPaint);
    }
}
