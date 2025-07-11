package me.telegraphy.android.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
// import android.graphics.Paint; // Not used directly in provided code
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import me.telegraphy.android.messenger.AndroidUtilities;
// import me.telegraphy.android.messenger.ChatObject; // Not directly used in this cell's logic, but contextually relevant
import me.telegraphy.android.messenger.LocaleController;
// import me.telegraphy.android.messenger.MessagesController; // Not directly used
import me.telegraphy.android.messenger.R;
import me.telegraphy.android.messenger.UserConfig;
import me.telegraphy.android.tgnet.TLRPC;
import me.telegraphy.android.ui.ActionBar.Theme;
import me.telegraphy.android.ui.ActionBar.ThemeDescription;
// import me.telegraphy.android.ui.Components.AvatarDrawable; // Not used
// import me.telegraphy.android.ui.Components.BackupImageView; // Not used
import me.telegraphy.android.ui.Components.LayoutHelper;
import me.telegraphy.android.ui.Components.RecyclerListView; // For ThemeDescription context

import java.util.ArrayList;
import java.util.List;

public class ProfileGroupCell extends FrameLayout {

    private TextView titleTextView;
    private TextView membersCountTextView;
    private TextView descriptionTextView;
    private TextView linkTextView;
    private ImageView verifiedIcon;
    // private LinearLayout adminsPreviewLayout; // Declared but not added in provided code, might be for future use

    private boolean drawDivider = true;
    private int currentAccount = UserConfig.selectedAccount;

    public ProfileGroupCell(@NonNull Context context) {
        super(context);
        setWillNotDraw(false); // Important for onDraw if drawing divider
        currentAccount = UserConfig.selectedAccount;

        titleTextView = new TextView(context);
        titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleTextView.setLines(1);
        titleTextView.setEllipsize(TextUtils.TruncateAt.END);
        // Adjusted right margin to accommodate potential verifiedIcon
        addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 16, 10, 70, 0));

        verifiedIcon = new ImageView(context);
        verifiedIcon.setImageResource(R.drawable.verified_area); // Ensure this drawable exists
        verifiedIcon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_profile_verifiedBackground), PorterDuff.Mode.MULTIPLY));
        verifiedIcon.setVisibility(GONE); // Initially hidden
        // Will be positioned in setData after titleTextView is measured
        addView(verifiedIcon, LayoutHelper.createFrame(16, 16, Gravity.TOP | Gravity.LEFT, 0, 12, 0, 0));


        membersCountTextView = new TextView(context);
        membersCountTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        membersCountTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        membersCountTextView.setSingleLine(true);
        membersCountTextView.setEllipsize(TextUtils.TruncateAt.END);
        addView(membersCountTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 16, 35, 16, 0));

        descriptionTextView = new TextView(context);
        descriptionTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        descriptionTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        descriptionTextView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
        descriptionTextView.setMaxLines(3); // Or as per design
        descriptionTextView.setEllipsize(TextUtils.TruncateAt.END);
        descriptionTextView.setVisibility(GONE); // Initially hidden
        // Added dynamically based on content, top margin set in setData
        addView(descriptionTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 16, 0, 16, 0));

        linkTextView = new TextView(context);
        linkTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText));
        linkTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        linkTextView.setSingleLine(true);
        linkTextView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        linkTextView.setVisibility(GONE); // Initially hidden
        // Added dynamically, top margin set in setData
        addView(linkTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 16, 0, 16, 10)); // Bottom margin for spacing

        // adminsPreviewLayout = new LinearLayout(context); // Declared but not used in provided layout logic
        // adminsPreviewLayout.setOrientation(LinearLayout.HORIZONTAL);
        // adminsPreviewLayout.setVisibility(GONE);
        // addView(adminsPreviewLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 16, 0, 16, 0));
    }

    public void setData(TLRPC.Chat chat, TLRPC.ChatFull chatFull) {
        if (chat == null) {
            setVisibility(GONE);
            return;
        }
        setVisibility(VISIBLE);

        titleTextView.setText(LocaleController.getString("GroupInformation", R.string.GroupInformation)); // Generic title for the cell
        // Position verified icon after title text
        if (chat.verified) {
            verifiedIcon.setVisibility(VISIBLE);
            // Measure title text to position icon correctly
            titleTextView.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.x - AndroidUtilities.dp(16 + 70 + 20), MeasureSpec.AT_MOST), // available width
                                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(20), MeasureSpec.EXACTLY)); // approx height
            LayoutParams lp = (LayoutParams) verifiedIcon.getLayoutParams();
            lp.leftMargin = AndroidUtilities.dp(16) + (int)titleTextView.getPaint().measureText(titleTextView.getText().toString()) + AndroidUtilities.dp(4);
            verifiedIcon.setLayoutParams(lp);
        } else {
            verifiedIcon.setVisibility(GONE);
        }

        int onlineCount = 0;
        if (chatFull != null) {
            onlineCount = chatFull.online_count;
        }
        String membersStr = LocaleController.formatPluralString("Members", chat.participants_count);
        if (onlineCount > 0) {
            // Using string concatenation as an example, consider using formatted strings for better localization
            membersStr += ", " + LocaleController.formatPluralString("OnlineCount", onlineCount, onlineCount);
        }
        membersCountTextView.setText(membersStr);

        // Handle description and link visibility and positioning
        int currentTopMargin = AndroidUtilities.dp(35 + 20 + 4); // Initial top margin below membersCountTextView

        if (chatFull != null && !TextUtils.isEmpty(chatFull.about)) {
            descriptionTextView.setText(chatFull.about);
            descriptionTextView.setVisibility(VISIBLE);
            LayoutParams descLp = (LayoutParams) descriptionTextView.getLayoutParams();
            descLp.topMargin = currentTopMargin;
            descriptionTextView.setLayoutParams(descLp);
            // Measure to get actual height for subsequent element positioning
            descriptionTextView.measure(
                MeasureSpec.makeMeasureSpec(getMeasuredWidth() > 0 ? getMeasuredWidth() - AndroidUtilities.dp(32) : AndroidUtilities.displaySize.x - AndroidUtilities.dp(32) , MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            );
            currentTopMargin += descriptionTextView.getMeasuredHeight() + AndroidUtilities.dp(8); // Add its height and some margin
        } else {
            descriptionTextView.setVisibility(GONE);
        }

        String inviteLink = null;
        if (chatFull != null && chatFull.exported_invite instanceof TLRPC.TL_chatInviteExported) {
            inviteLink = ((TLRPC.TL_chatInviteExported) chatFull.exported_invite).link;
        }
        if (!TextUtils.isEmpty(inviteLink)) {
            linkTextView.setText(inviteLink.replaceFirst("https://", "")); // Show link without scheme
            linkTextView.setVisibility(VISIBLE);
            LayoutParams linkLp = (LayoutParams) linkTextView.getLayoutParams();
            linkLp.topMargin = currentTopMargin;
            linkTextView.setLayoutParams(linkLp);
        } else {
            linkTextView.setVisibility(GONE);
        }
        // Admins preview layout would be handled similarly if it were to be displayed
        requestLayout(); // Important to re-measure and re-layout after changing content/visibility
    }

    public void setDrawDivider(boolean draw) {
        if (this.drawDivider != draw) {
            this.drawDivider = draw;
            invalidate();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Calculate height based on visible elements
        int height = AndroidUtilities.dp(10); // Top padding
        height += AndroidUtilities.dp(25); // titleTextView approx height
        height += AndroidUtilities.dp(20); // membersCountTextView approx height
        height += AndroidUtilities.dp(4);  // Margin

        if (descriptionTextView.getVisibility() == VISIBLE) {
            // Ensure descriptionTextView is measured if its content might change height
            descriptionTextView.measure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec) - AndroidUtilities.dp(32), MeasureSpec.AT_MOST), // Width excluding padding
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED) // Measure freely for height
            );
            height += descriptionTextView.getMeasuredHeight() + AndroidUtilities.dp(8); // Add its height and margin
        }
        if (linkTextView.getVisibility() == VISIBLE) {
            linkTextView.measure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec) - AndroidUtilities.dp(32), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(20), MeasureSpec.EXACTLY) // Fixed height for link
            );
            height += linkTextView.getMeasuredHeight() + AndroidUtilities.dp(4); // Add its height and margin
        }
        // Add height for adminsPreviewLayout if it were visible and used
        height += AndroidUtilities.dp(10); // Bottom padding

        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (drawDivider) {
            // Draw divider at the bottom of the cell, respecting padding
            canvas.drawLine(AndroidUtilities.dp(16), getHeight() - 1, getWidth() - AndroidUtilities.dp(16), getHeight() - 1, Theme.dividerPaint);
        }
    }

    public static void getThemeDescriptions(List<ThemeDescription> descriptions, RecyclerListView parentListView) {
        descriptions.add(new ThemeDescription(parentListView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{ProfileGroupCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        descriptions.add(new ThemeDescription(ProfileGroupCell.class, ThemeDescription.FLAG_DIVIDER, null, Theme.dividerPaint, null, null, Theme.key_divider));

        descriptions.add(new ThemeDescription(ProfileGroupCell.class, 0, new Class[]{TextView.class}, new String[]{"titleTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        descriptions.add(new ThemeDescription(ProfileGroupCell.class, 0, new Class[]{TextView.class}, new String[]{"membersCountTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText));
        descriptions.add(new ThemeDescription(ProfileGroupCell.class, 0, new Class[]{TextView.class}, new String[]{"descriptionTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        descriptions.add(new ThemeDescription(ProfileGroupCell.class, 0, new Class[]{TextView.class}, new String[]{"linkTextView"}, null, null, null, Theme.key_windowBackgroundWhiteLinkText));
        descriptions.add(new ThemeDescription(ProfileGroupCell.class, 0, new Class[]{ImageView.class}, new String[]{"verifiedIcon"}, null, null, null, Theme.key_profile_verifiedBackground));
        // Add descriptions for adminsPreviewLayout if it gets implemented with themable elements
    }
}
