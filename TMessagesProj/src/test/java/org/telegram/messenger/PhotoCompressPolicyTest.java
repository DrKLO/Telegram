package org.telegram.messenger;

/**
 * Pure-JVM checks for chat photo JPEG quality. No Android SDK.
 * Run: javac -d /tmp/pcp ... && java -cp /tmp/pcp org.telegram.messenger.PhotoCompressPolicyTest
 */
public final class PhotoCompressPolicyTest {
    private static int failures;

    public static void main(String[] args) {
        expectEq("chat jpeg default matches Photos-class JPEG (~q85+), not Bitmap q80/87",
                92, PhotoCompressPolicy.chatJpegQuality(false));
        expectEq("high-quality send stays near-lossless",
                99, PhotoCompressPolicy.chatJpegQuality(true));
        expectEq("cached big preview is not q83 mush",
                90, PhotoCompressPolicy.cacheJpegQuality(true));
        expectEq("tiny thumbs stay cheap",
                60, PhotoCompressPolicy.cacheJpegQuality(false));
        expectEq("default photo box is still 1280 (no bigger mush without better encode)",
                1280, PhotoCompressPolicy.photoMaxSide(false));
        expectEq("HQ photo box is 2560",
                2560, PhotoCompressPolicy.photoMaxSide(true));
        if (failures != 0) {
            System.err.println(failures + " failed");
            System.exit(1);
        }
        System.out.println("ok");
    }

    private static void expectEq(String name, int want, int got) {
        if (want != got) {
            failures++;
            System.err.println("FAIL " + name + ": want " + want + " got " + got);
        }
    }
}
