package org.telegram.messenger;

public class GexCustomFunction {

    public static String mockifyText(String text) {
        String receivedText = text.toLowerCase();
        int textLength = receivedText.length();
        StringBuilder stringBuilder = new StringBuilder(textLength);
        boolean uppercase = false;
        for(int i = 0; i < textLength; i++) {
            char currentCharacter = receivedText.charAt(i);
            if (currentCharacter >= 97 && currentCharacter <= 122) {
                if (uppercase) {
                    currentCharacter -= 32;
                    uppercase = false;
                } else {
                    uppercase = true;
                }
            }
            stringBuilder.append(currentCharacter);
        }

        return stringBuilder.toString();
    }
}
