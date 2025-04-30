# BoringSSL pki - Web PKI Certificate path building and verification library

This directory and library should be considered experimental and should not be
depended upon not to change without notice.  You should not use this.

It contains chrome's certificate verifier core logic as used by chrome.

## Current status:
 * Currently chrome uses this code via private API from within this directory.
 * At the moment there is no public API for these functions, as mentioned above
   if you make use of this you do so at your own risk and your code may be broken
   by API change at any time.
 * Public API will be forthcoming.
