package org.telegram.ui.web;

/** Standalone JVM regression checks, runnable with the existing JDK and no test dependency. */
public final class WebViewPermissionSessionTest {
    private static final String ORIGIN = "https://mini.example";
    private static final String OTHER = "https://other.example";
    private static final String[] MIC = {WebViewPermissionSession.AUDIO};
    private static final String[] CAM = {WebViewPermissionSession.VIDEO};
    private static final String[] GEO = {WebViewPermissionSession.LOCATION};
    private static final String[] BOTH = {WebViewPermissionSession.AUDIO, WebViewPermissionSession.VIDEO};
    private static int checks;

    private static void check(boolean condition, String description) {
        checks++;
        if (!condition) {
            throw new AssertionError(description);
        }
    }

    public static void main(String[] args) {
        check("https://mini.example".equals(WebViewPermissionSession.originKey("HTTPS://MINI.EXAMPLE:443/path?q=1")), "Normalize scheme, host and default HTTPS port");
        check("http://mini.example".equals(WebViewPermissionSession.originKey("http://mini.example:80/")), "Normalize default HTTP port");
        check("https://mini.example:8443".equals(WebViewPermissionSession.originKey("https://mini.example:8443/")), "Keep nondefault port");
        check(!WebViewPermissionSession.originKey("http://mini.example").equals(WebViewPermissionSession.originKey("https://mini.example")), "HTTP and HTTPS origins stay separate");
        check(!WebViewPermissionSession.originKey("https://mini.example").equals(WebViewPermissionSession.originKey("https://child.mini.example")), "Subdomains stay separate");
        check("https://[::1]:444".equals(WebViewPermissionSession.originKey("https://[::1]:444/")), "Keep brackets around IPv6 host");
        check(!WebViewPermissionSession.originKey("https://[::1]:444").equals(WebViewPermissionSession.originKey("https://[::1:444]")), "IPv6 host suffix cannot collide with a port");
        check(WebViewPermissionSession.originKey("null") == null, "Opaque origin is not a web origin");
        check(WebViewPermissionSession.originKey("file:///tmp/page") == null, "No file-origin consent reuse");
        check(WebViewPermissionSession.originKey("data:text/plain,hello") == null, "No data-origin consent reuse");
        check(WebViewPermissionSession.originKey("https://mini.example:70000") == null, "Reject invalid port");
        check(WebViewPermissionSession.originKey("https://[broken") == null, "Reject malformed origin");
        check(WebViewPermissionSession.originKey(null) == null, "No null-origin normalization");
        WebViewPermissionSession session = new WebViewPermissionSession();
        check(!session.hasAll(ORIGIN, MIC), "First microphone request must ask");
        check(session.allow(session.generation(), ORIGIN, MIC), "Explicit microphone approval is remembered");
        check(session.hasAll(ORIGIN, MIC), "Same origin and resource may reuse approval");
        check(!session.hasAll(ORIGIN, CAM), "Microphone approval is not camera consent");
        check(!session.hasAll(OTHER, MIC), "Another origin does not inherit consent");
        check(!session.hasAll(ORIGIN, BOTH), "Combined request still needs the missing camera approval");
        check(session.missing(ORIGIN, BOTH).length == 1
                && WebViewPermissionSession.VIDEO.equals(session.missing(ORIGIN, BOTH)[0]), "Ask only for the unapproved resource");
        session.allow(session.generation(), ORIGIN, CAM);
        check(session.hasAll(ORIGIN, BOTH), "Two explicit approvals cover a combined request");
        session.deny(ORIGIN, CAM);
        session.allow(session.generation(), ORIGIN, MIC);
        check(!session.hasAll(ORIGIN, CAM), "Allowing microphone cannot override a camera denial");
        check(session.hasAll(ORIGIN, MIC), "A camera denial preserves independent microphone consent");
        session.clear();
        session.allow(session.generation(), ORIGIN, GEO);
        check(!session.hasAll(ORIGIN, MIC) && !session.hasAll(ORIGIN, CAM), "Location must not authorize media");
        check(session.hasAll(ORIGIN, GEO), "Same-origin location may reuse its own approval");
        session.allow(session.generation(), OTHER, MIC);
        session.deny(ORIGIN, GEO);
        check(session.hasAll(OTHER, MIC), "Denial does not revoke another origin's resource");
        long oldGeneration = session.generation();
        session.clear();
        check(!session.hasAll(OTHER, MIC), "Reload/navigation clears all document approvals");
        check(!session.allow(oldGeneration, ORIGIN, MIC), "Late approval from the old document must be rejected");
        check(!session.hasAll(ORIGIN, MIC), "Rejected stale approval must not change current consent");
        check(!session.allow(session.generation(), ORIGIN, new String[]{WebViewPermissionSession.AUDIO, "unknown"}), "Mixed unknown resources must not be approved");
        check(!session.hasAll(ORIGIN, MIC), "Rejecting an unknown resource must not partially authorize microphone");
        check(!session.allow(session.generation(), ORIGIN, new String[0]), "Empty resource arrays are not approvals");
        check(!session.allow(session.generation(), null, MIC), "No anonymous-origin approval");
        check(!session.allow(session.generation(), ORIGIN, new String[]{WebViewPermissionSession.AUDIO, WebViewPermissionSession.AUDIO}), "Duplicate resources are not a combined grant");
        session.allow(session.generation(), ORIGIN, MIC);
        session.retainSystemResources(MIC);
        check(session.hasAll(ORIGIN, MIC), "Adding a camera request without OS camera permission preserves microphone consent");
        check(session.missing(ORIGIN, BOTH).length == 1
                && WebViewPermissionSession.VIDEO.equals(session.missing(ORIGIN, BOTH)[0]), "Only camera needs web consent when OS microphone remains granted");
        session.allow(session.generation(), ORIGIN, CAM);
        session.allow(session.generation(), ORIGIN, GEO);
        session.allow(session.generation(), OTHER, BOTH);
        session.retainSystemResources(new String[]{WebViewPermissionSession.AUDIO, WebViewPermissionSession.LOCATION});
        check(!session.hasAll(ORIGIN, CAM) && !session.hasAll(OTHER, CAM), "OS camera revocation removes camera consent for every origin");
        check(session.hasAll(ORIGIN, MIC) && session.hasAll(OTHER, MIC), "OS camera revocation preserves microphone consent for every origin");
        check(session.hasAll(ORIGIN, GEO), "OS camera revocation preserves independent location consent");
        session.retainSystemResources(new String[]{WebViewPermissionSession.AUDIO, WebViewPermissionSession.VIDEO, WebViewPermissionSession.LOCATION});
        check(!session.hasAll(ORIGIN, CAM), "Restoring an OS grant does not restore revoked web consent");
        session.retainSystemResources(GEO);
        check(!session.hasAll(ORIGIN, MIC) && !session.hasAll(OTHER, MIC), "OS microphone revocation does not leave another origin approved");
        check(session.hasAll(ORIGIN, GEO), "Remaining OS location grant preserves its web consent");
        session.retainSystemResources(new String[0]);
        check(!session.hasAll(ORIGIN, GEO), "No OS grants leaves no reusable web consent");
        session.clear();
        session.allow(session.generation(), ORIGIN, MIC);
        String[] newlyPrompted = session.missing(ORIGIN, BOTH);
        session.allow(session.generation(), ORIGIN, BOTH);
        session.deny(ORIGIN, newlyPrompted);
        check(session.hasAll(ORIGIN, MIC) && !session.hasAll(ORIGIN, CAM), "Canceled combined delivery rolls back only the newly prompted camera");
        session.allow(session.generation(), ORIGIN, CAM);
        String[] alreadyApproved = session.missing(ORIGIN, BOTH);
        session.deny(ORIGIN, alreadyApproved);
        check(session.hasAll(ORIGIN, BOTH), "Canceled reuse does not revoke previously approved resources");
        System.out.println("PASS: " + checks + " permission-session regression checks");
    }
}
