package com.pedrodalben.bigbangessentials.jobs.license;

/**
 * Result of a license operation (start, claim, cancel, grant).
 */
public record LicenseActionResult(boolean success, String message) {
    public static LicenseActionResult ok(String msg) { return new LicenseActionResult(true, msg); }
    public static LicenseActionResult fail(String msg) { return new LicenseActionResult(false, msg); }
}
