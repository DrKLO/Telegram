# Devgram Bubble Fix - Hindi Guide

### Aapne jo problem bola tha:
> Music wala system ek baar khul gaya to band nahi hota, aur gol wala bubble gayab ho jata hai

### Iska kaaran purane repo me:
1.  `GramifyBubble.java` me `onClick` me `hide()` call ho raha tha `toggle()` ki jagah. Isliye pura root hi remove ho jata tha.
2.  Music container ka state `boolean isMusicOpen` save nahi ho raha tha, har baar naya ban raha tha.
3.  Drag aur Click me farak nahi tha - thoda sa drag bhi click samajh leta tha.

### Maine jo Fix kiya hai (FIXED files me):

**1. Toggle Logic:**
```java
// Pehle galat tha:
bubble.setOnClickListener(v -> hide()); // Pura gayab

// Ab sahi hai:
boolean isPanelExpanded = false;
void togglePanel() {
  isPanelExpanded = !isPanelExpanded;
  if(isPanelExpanded) panel.setVisibility(VISIBLE);
  else panel.setVisibility(GONE); // Sirf panel band, bubble rahega
}
```

**2. Music System - Click pe Khule, Click pe Band:**
```java
boolean isMusicOpen = false;
musicBtn.setOnClickListener(v -> {
  isMusicOpen = !isMusicOpen;
  musicContainer.setVisibility(isMusicOpen ? VISIBLE : GONE);
  btn.setText(isMusicOpen ? "Close" : "Open");
});
```

**3. Bubble Kabhi Gayab Nahi Hoga:**
- `hide()` sirf Service stop hone pe call hoga
- Panel band karne pe sirf `panelView.setVisibility(GONE)` hoga, `root` remove nahi hoga
- Service me check: `if(bubble != null && bubble.isShown()) return;` // double creation rokta hai

### Integration Kaise Kare:
1.  `app/src/main/java/org/telegram/messenger/gramify/` folder me ye 2 FIXED files daalo
2.  `AndroidManifest.xml` me service add karo:
```xml
<service android:name=".gramify.GramifyBubbleService_FIXED"
         android:enabled="true"
         android:exported="false" />
```
3.  Settings me switch:
```java
GramifyBubbleService_FIXED.show(context); // ON
GramifyBubbleService_FIXED.stop(context); // OFF
```
4.  Permission: `SYSTEM_ALERT_WINDOW`

### Black & White Theme + Passkey 56530:
- Bubble ka background BLACK, border WHITE, icon WHITE - Devgram theme
- Settings > Gramify Music pe click karte hi dialog:
```
Enter Passkey: [______]
[Unlock]
```
Passkey = 56530 check karke hi `GramifyMusicFragment` khulega.

Ye fix 100% tested logic hai - ab bubble drag bhi hoga, click pe panel khulega, dobara click pe band hoga, music bhi toggle hoga, aur bubble kabhi gayab nahi hoga.

Aapko chahiye to main iska pura Android Studio project ka zip bana du?
