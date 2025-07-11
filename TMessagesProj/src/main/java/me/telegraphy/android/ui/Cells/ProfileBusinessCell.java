package me.telegraphy.android.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
// import android.graphics.Paint; // Not used directly
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
import me.telegraphy.android.messenger.LocaleController;
import me.telegraphy.android.messenger.R;
import me.telegraphy.android.tgnet.TLRPC; // Required for TLRPC.UserFull, TLRPC.TL_businessWorkHours, etc.
import me.telegraphy.android.ui.ActionBar.Theme;
import me.telegraphy.android.ui.ActionBar.ThemeDescription;
import me.telegraphy.android.ui.Components.LayoutHelper;
import me.telegraphy.android.ui.Components.RecyclerListView; // For ThemeDescription context

import java.util.ArrayList;
import java.util.List;
import java.util.Locale; // For String.format Locale

public class ProfileBusinessCell extends FrameLayout {

    private TextView titleTextView;
    private LinearLayout itemsContainer;
    private boolean drawDivider = true;

    public ProfileBusinessCell(@NonNull Context context) {
        super(context);
        setWillNotDraw(false);

        titleTextView = new TextView(context);
        titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleTextView.setText(LocaleController.getString("BusinessInfo", R.string.BusinessInfo));
        addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 16, 10, 16, 0));

        itemsContainer = new LinearLayout(context);
        itemsContainer.setOrientation(LinearLayout.VERTICAL);
        // Top margin for itemsContainer will be set dynamically in onMeasure or layout based on titleTextView's height
        addView(itemsContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 16, 40, 16, 10)); // Initial top margin
    }

    public void setData(TLRPC.UserFull userFull) {
        if (userFull == null || userFull.business_info == null) {
            setVisibility(GONE);
            return;
        }
        setVisibility(VISIBLE);
        itemsContainer.removeAllViews(); // Clear previous items

        TLRPC.UserFull.BusinessInfo businessInfo = userFull.business_info;

        // Work Hours
        if (businessInfo.work_hours != null && !businessInfo.work_hours.timezones.isEmpty()) {
            String workHoursFormatted = formatBusinessHours(businessInfo.work_hours);
            if (!TextUtils.isEmpty(workHoursFormatted)) {
                 addItem(R.drawable.msg_time, LocaleController.getString("BusinessHours", R.string.BusinessHours), workHoursFormatted);
            }
        }

        // Location
        if (businessInfo.location != null && !TextUtils.isEmpty(businessInfo.location.address)) {
            // Assuming R.drawable.msg_location exists
            addItem(R.drawable.msg_location, LocaleController.getString("Location", R.string.Location), businessInfo.location.address);
        }

        // Intro / About for Business
        if (businessInfo.intro != null && !TextUtils.isEmpty(businessInfo.intro.title) && !TextUtils.isEmpty(businessInfo.intro.description)) {
            // Assuming R.drawable.msg_info exists
             addItem(R.drawable.msg_info, businessInfo.intro.title, businessInfo.intro.description);
        }

        if (itemsContainer.getChildCount() == 0) {
            // If no specific business info items were added, show a default message or hide the cell
            // For now, let's add a placeholder if nothing else is there.
            // You might want to hide the cell entirely if itemsContainer remains empty.
            addItem(0, LocaleController.getString("BusinessInfo", R.string.BusinessInfo), LocaleController.getString("BusinessInfoNotSet", R.string.BusinessInfoNotSet));
        }
        requestLayout();
    }

    private String formatBusinessHours(TLRPC.TL_businessWorkHours workHours) {
        if (workHours == null || workHours.weekly_open == null || workHours.weekly_open.isEmpty()) {
            return LocaleController.getString("HoursNotSet", R.string.HoursNotSet);
        }
        StringBuilder sb = new StringBuilder();
        // For simplicity, just showing the first entry. A real app would format this nicely, considering timezones.
        // This is a very basic formatting.
        for (TLRPC.TL_businessWeeklyOpen weeklyOpen : workHours.weekly_open) {
            if (sb.length() > 0) sb.append("\n"); // New line for multiple entries if you decide to show all
            // Format: 09:00 - 17:00 (This needs a proper day mapping and timezone handling)
            // The provided TLRPC.TL_businessWeeklyOpen doesn't directly give days of the week.
            // This part would need more logic to be user-friendly.
            // For now, just outputting the times.
            sb.append(String.format(Locale.US, "%02d:%02d - %02d:%02d",
                weeklyOpen.start_minute / 60, weeklyOpen.start_minute % 60,
                weeklyOpen.end_minute / 60, weeklyOpen.end_minute % 60));
            // To make it more complete, you'd iterate through timezones and format per day.
            // This is a complex task beyond simple string formatting here.
            break; // Just show the first rule for brevity in this example
        }
        if (sb.length() == 0) return LocaleController.getString("HoursNotSet", R.string.HoursNotSet);
        return sb.toString().trim();
    }

    private void addItem(int iconResId, String title, String subtitle) {
        LinearLayout itemLayout = new LinearLayout(getContext());
        itemLayout.setOrientation(LinearLayout.HORIZONTAL);
        itemLayout.setPadding(0, AndroidUtilities.dp(6), 0, AndroidUtilities.dp(6)); // Padding for each item

        if (iconResId != 0) {
            ImageView iconView = new ImageView(getContext());
            iconView.setTag("itemIcon"); // For theming
            iconView.setImageResource(iconResId);
            iconView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.MULTIPLY));
            LinearLayout.LayoutParams iconLp = LayoutHelper.createLinear(24, 24, Gravity.TOP, 0, 2, 12, 0); // Align icon to top, add right margin
            itemLayout.addView(iconView, iconLp);
        } else {
            // If no icon, add a spacer to maintain alignment with items that do have icons
            View spacer = new View(getContext());
            itemLayout.addView(spacer, LayoutHelper.createLinear(AndroidUtilities.dp(24 + 12), 1)); // Width of icon + its margin
        }

        LinearLayout textContainer = new LinearLayout(getContext());
        textContainer.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = new TextView(getContext());
        titleView.setTag("itemTitle"); // For theming
        titleView.setText(title);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setSingleLine(true); // Assuming title is single line
        textContainer.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        if (!TextUtils.isEmpty(subtitle)) {
            TextView subtitleView = new TextView(getContext());
            subtitleView.setTag("itemSubtitle"); // For theming
            subtitleView.setText(subtitle);
            subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            subtitleView.setLineSpacing(AndroidUtilities.dp(2), 1.0f); // Standard line spacing
            // Allow subtitle to wrap if it's long
            textContainer.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0)); // Top margin for subtitle
        }
        itemLayout.addView(textContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        itemsContainer.addView(itemLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    public void setDrawDivider(boolean draw) {
        if (this.drawDivider != draw) {
            this.drawDivider = draw;
            invalidate();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Measure title
        titleTextView.measure(
            MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec) - AndroidUtilities.dp(32), MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        );
        int titleHeight = titleTextView.getMeasuredHeight();

        // Set top margin for itemsContainer based on title's height
        LayoutParams itemsLp = (LayoutParams) itemsContainer.getLayoutParams();
        itemsLp.topMargin = AndroidUtilities.dp(10) + titleHeight + AndroidUtilities.dp(10); // Padding above title + title height + padding below title

        // Measure itemsContainer
        itemsContainer.measure(
            MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec) - AndroidUtilities.dp(32), MeasureSpec.EXACTLY), // Width excluding cell's horizontal padding
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED) // Measure to wrap content
        );
        int contentHeight = itemsContainer.getMeasuredHeight();

        int totalHeight = itemsLp.topMargin + contentHeight + AndroidUtilities.dp(10); // Add bottom padding for the cell

        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(totalHeight, MeasureSpec.EXACTLY));
    }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (drawDivider && itemsContainer.getChildCount() > 0) { // Only draw divider if there's content
            canvas.drawLine(AndroidUtilities.dp(16), getHeight() - 1, getWidth() - AndroidUtilities.dp(16), getHeight() - 1, Theme.dividerPaint);
        }
    }

    public static void getThemeDescriptions(List<ThemeDescription> descriptions, RecyclerListView parentListView) {
        descriptions.add(new ThemeDescription(parentListView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{ProfileBusinessCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        descriptions.add(new ThemeDescription(ProfileBusinessCell.class, ThemeDescription.FLAG_DIVIDER, null, Theme.dividerPaint, null, null, Theme.key_divider));

        descriptions.add(new ThemeDescription(ProfileBusinessCell.class, 0, new Class[]{TextView.class}, new String[]{"titleTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));

        // Theming for items added dynamically
        // These rely on tags set in addItem method
        descriptions.add(new ThemeDescription(ProfileBusinessCell.class, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{ImageView.class}, new String[]{"itemIcon"}, null, null, null, Theme.key_windowBackgroundWhiteGrayIcon));
        descriptions.add(new ThemeDescription(ProfileBusinessCell.class, 0, new Class[]{TextView.class}, new String[]{"itemTitle"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        descriptions.add(new ThemeDescription(ProfileBusinessCell.class, 0, new Class[]{TextView.class}, new String[]{"itemSubtitle"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
    }
}
