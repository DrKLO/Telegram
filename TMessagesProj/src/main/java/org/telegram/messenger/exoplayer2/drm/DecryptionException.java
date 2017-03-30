package org.telegram.messenger.exoplayer2.drm;

/**
 * An exception when doing drm decryption using the In-App Drm
 */
public class DecryptionException extends Exception {
  private final int errorCode;

  public DecryptionException(int errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  /**
   * Get error code
   */
  public int getErrorCode() {
    return errorCode;
  }
}
