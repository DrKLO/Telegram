# Devgram Remastered by Dev - FINAL BUILD

## Branding - Pura Remastered by Dev

Har jagah ye text dikhega:

**App Name:** `Devgram Remastered by Dev`
**Package:** `com.devgram.remasteredbydev`
**Notification:** `Devgram Remastered by Dev - Bot Active`
**Settings Footer:** `© Remastered by Dev | Developed by Dev 🫍`
**Onboarding:** `Developed by Dev 🫍 Thank you for joining our application - Remastered by Dev`

### Kahan Kahan Change Karna Hai:

1. **strings.xml**
```xml
<string name="AppName">Devgram Remastered by Dev</string>
<string name="app_name">Devgram Remastered by Dev</string>
```

2. **BuildVars.java**
```java
public static final String APP_NAME = "Devgram Remastered by Dev";
```

3. **Bubble Service**
```java
.setContentTitle(Devgram_Final_RemasteredByDev_PinLock.getNotificationTitle())
.setContentText(Devgram_Final_RemasteredByDev_PinLock.getNotificationText())
```

4. **Har Fragment ke niche:**
```java
container.addView(Devgram_Final_RemasteredByDev_PinLock.createBrandingLabel(context));
container.addView(Devgram_Final_RemasteredByDev_PinLock.createFooterLabel(context));
```

## Pin Lock System - Bot Feature Pe

Aapne bola tha bot wale feature pe bhi pin lock lage.

**Pin:** `56530` (same as Gramify Music wala)

**Flow:**
```
[User clicks Bot Manager] 
   ↓
[Pin Dialog: Enter Pin - Remastered by Dev]
   ↓
[Wrong Pin -> Error]
[Correct Pin 56530 -> Unlock -> Bot Panel Khulega]
```

**Code:**
```java
Devgram_Final_RemasteredByDev_PinLock.checkPinAndOpen(context, () -> {
    // Yahan aapka 10 slots wala bot manager khulega
    presentFragment(new Devgram_Bot_10_Slots_Safe());
});
```

**Features:**
- Ek baar unlock karne ke baad dubara pin nahi mangega (SharedPreferences me save)
- Logout pe lock wapas lag jayega
- Black & White theme ka dialog
- Wrong pin pe error

## Final File List:

1. `Devgram_Bot_10_Slots_Safe.java` - 10 slots bot manager (safe, 1 bot active)
2. `DevgramBotPanelFinalSafe.java` - Bubble panel (Group Link + Name + Message)
3. `DevgramBubbleService.java` - Aapka wala bubble service (drag + toggle)
4. `Devgram_Final_RemasteredByDev_PinLock.java` - Pin lock + Branding (ye naya)
5. `GramifyBubble_FIXED.java` - Music + EQ with 56530 lock (pehle wala fixed)

Sab me `Remastered by Dev` branding add hai.

## Build Steps:

1. Telegram Android source clone karo
2. Ye 5 files `org.telegram.messenger.gramify` me daalo
3. `AndroidManifest.xml` me service add karo
4. `strings.xml` me AppName change karo to `Devgram Remastered by Dev`
5. Build karo

APK ka naam hoga: `Devgram-Remastered-by-Dev.apk`

Done! 🫍
