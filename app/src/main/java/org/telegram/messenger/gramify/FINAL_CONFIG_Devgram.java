package org.telegram.messenger.gramify;

/**
 * FINAL CONFIG - Devgram
 * APK Name: Devgram
 * Channel: https://t.me/motivation_knight
 * Group: https://t.me/high_table_dev
 * Branding Inside: Remastered by Dev
 * Pin: 56530
 */

public class FINAL_CONFIG_Devgram {
    
    // APK Ka Naam - Launcher pe ye dikhega
    public static final String APK_NAME = "Devgram";
    
    // Package Name
    public static final String PACKAGE_NAME = "com.devgram.messenger";
    
    // Force Join Links - Updated
    public static final String CHANNEL_LINK = "https://t.me/tech_zone_dev"; // New aapne diya
    public static final String GROUP_LINK = "https://t.me/high_table_dev";
    public static final String OLD_CHANNEL = "https://t.me/motivation_knight"; // Purana backup
    
    // Channel/Group Username (API check ke liye)
    public static final String CHANNEL_USERNAME = "tech_zone_dev";
    public static final String GROUP_USERNAME = "high_table_dev";
    
    // Inside Branding - Har jagah ye likhega
    public static final String INSIDE_BRAND = "Remastered by Dev";
    public static final String FULL_BRAND = "Devgram - Remastered by Dev";
    public static final String DEV_TEXT = "Developed by Dev 🫍 Thank you for joining our application";
    
    // Pin Lock
    public static final String PIN_CODE = "56530";
    
    // Theme
    public static final int BLACK = 0xFF000000;
    public static final int WHITE = 0xFFFFFFFF;
    
    // Force Join Logic
    public static boolean isForceJoinEnabled = true;
    
    /*
    strings.xml me:
    <string name="AppName">Devgram</string>
    
    Lekin andar har screen pe:
    - Title: Devgram
    - Footer: Remastered by Dev | Developed by Dev 🫍
    - Notification: Devgram - Remastered by Dev - Bot Active
    
    Onboarding Flow:
    App Open -> Check if joined channel + group
    -> If not joined -> Show Join Screen with 2 buttons:
       [Join Channel: https://t.me/motivation_knight]
       [Join Group: https://t.me/high_table_dev]
    -> Dono join karne ke baad hi Continue button enable
    -> Continue -> Main App
    */
}
