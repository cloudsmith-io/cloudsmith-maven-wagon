
package io.cloudsmith.maven.wagon;

public final class Properties {
    //
    // Static Properties
    //

    private static final String  API_VERSION  = "${cloudsmith.api_version}";
    private static final String  ARTIFACT_ID  = "${project.artifactId}";
    private static final String  BUILD_NUMBER = "${buildNumber}";
    private static final String  GROUP_ID     = "${project.groupId}";
    private static final String  VERSION      = "${project.version}";
    private static final String  URL          = "${project.url}";

    //
    // Runtime Properties
    //

    private static final String  API_KEY_ENVVAR                = "CLOUDSMITH_API_KEY";
    private static final String  API_KEY_PROPERTY              = "cloudsmith.api_key";
    private static final boolean DEBUG_DEFAULT                 = false;
    private static final String  DEBUG_ENVVAR                  = "CLOUDSMITH_DEBUG";
    private static final String  DEBUG_PROPERTY                = "cloudsmith.debug";
    private static final int     HTTP_CONNECT_TIMEOUT_DEFAULT  = 15; // seconds
    private static final String  HTTP_CONNECT_TIMEOUT_ENVVAR   = "CLOUDSMITH_HTTP_CONNECT_TIMEOUT";
    private static final String  HTTP_CONNECT_TIMEOUT_PROPERTY = "cloudsmith.http.connect.timeout";
    private static final int     HTTP_READ_TIMEOUT_DEFAULT     = 30; // seconds
    private static final String  HTTP_READ_TIMEOUT_ENVVAR      = "CLOUDSMITH_HTTP_READ_TIMEOUT";
    private static final String  HTTP_READ_TIMEOUT_PROPERTY    = "cloudsmith.http.get.timeout";
    private static final int     HTTP_WRITE_TIMEOUT_DEFAULT    = 120; // seconds
    private static final String  HTTP_WRITE_TIMEOUT_ENVVAR     = "CLOUDSMITH_HTTP_WRITE_TIMEOUT";
    private static final String  HTTP_WRITE_TIMEOUT_PROPERTY   = "cloudsmith.http.put.timeout";
    private static final boolean SW_ENABLED_DEFAULT            = true;
    private static final String  SW_ENABLED_ENVVAR             = "CLOUDSMITH_SYNC_WAIT_ENABLED";
    private static final String  SW_ENABLED_PROPERTY           = "cloudsmith.sync_wait.enabled";
    private static final int     SW_INTERVAL_DEFAULT           = 5; // seconds
    private static final String  SW_INTERVAL_ENVVAR            = "CLOUDSMITH_SYNC_WAIT_INTERVAL";
    private static final String  SW_INTERVAL_PROPERTY          = "cloudsmith.sync_wait.interval";
    private static final boolean SW_VERBOSE_DEFAULT            = true;
    private static final String  SW_VERBOSE_ENVVAR             = "CLOUDSMITH_SYNC_WAIT_VERBOSE";
    private static final String  SW_VERBOSE_PROPERTY           = "cloudsmith.sync_wait.verbose";

    //
    // Static Properties
    //

    public static String getApiVersion() {
        return API_VERSION;
    }

    public static String getArtifactId() {
        return ARTIFACT_ID;
    }

    public static String getBuildNumber() {
        return BUILD_NUMBER;
    }

    public static String getGroupId() {
        return GROUP_ID;
    }

    public static String getVersion() {
        return VERSION;
    }

    public static String getUrl() {
        return URL;
    }

    //
    // Runtime Properties
    //

    public static String getApiKey(String defaultValue) {
        return getStringValue(
            API_KEY_ENVVAR,
            API_KEY_PROPERTY,
            defaultValue
        );
    }

    public static boolean isDebug() {
        return getBooleanValue(
            DEBUG_ENVVAR,
            DEBUG_PROPERTY,
            DEBUG_DEFAULT
        );
    }

    public static int getHttpConnectTimeout() {
        int value = getIntegerValue(
            HTTP_CONNECT_TIMEOUT_ENVVAR,
            HTTP_CONNECT_TIMEOUT_PROPERTY,
            HTTP_CONNECT_TIMEOUT_DEFAULT
        );

        if (value < 1) {
            System.out.println(
                "[WARN] HTTP connect timeout cannot be less than 1, setting value to 1."
            );
            value = 1;
        }

        return value;
    }

    public static int getHttpReadTimeout() {
        int value = getIntegerValue(
            HTTP_READ_TIMEOUT_ENVVAR,
            HTTP_READ_TIMEOUT_PROPERTY,
            HTTP_READ_TIMEOUT_DEFAULT
        );

        if (value < 1) {
            System.out.println(
                "[WARN] HTTP read timeout cannot be less than 1, setting value to 1."
            );
            value = 1;
        }

        return value;
    }

    public static int getHttpWriteTimeout() {
        int value = getIntegerValue(
            HTTP_WRITE_TIMEOUT_ENVVAR,
            HTTP_WRITE_TIMEOUT_PROPERTY,
            HTTP_WRITE_TIMEOUT_DEFAULT
        );

        if (value < 1) {
            System.out.println(
                "[WARN] HTTP write timeout cannot be less than 1, setting value to 1."
            );
            value = 1;
        }

        return value;
    }

    public static boolean isSyncWaitEnabled() {
        return getBooleanValue(
            SW_ENABLED_ENVVAR,
            SW_ENABLED_PROPERTY,
            SW_ENABLED_DEFAULT
        );
    }

    public static int getSyncWaitInterval() {
        int value = getIntegerValue(
            SW_INTERVAL_ENVVAR,
            SW_INTERVAL_PROPERTY,
            SW_INTERVAL_DEFAULT
        );

        if (value < 1) {
            System.out.println(
                "[WARN] Sync wait interval cannot be less than 1, setting value to 1."
            );
            value = 1;
        }

        return value;
    }

    public static boolean isSyncWaitVerbose() {
        return getBooleanValue(
            SW_VERBOSE_ENVVAR,
            SW_VERBOSE_PROPERTY,
            SW_VERBOSE_DEFAULT
        );
    }

    //
    // Private Helpers
    //

    private static String getStringValue(String envvar, String property, String defaultValue) {
        String value;

        if (envvar != null && !envvar.isEmpty()) {
            value = System.getenv(envvar);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }

        if (property != null && !property.isEmpty()) {
            value = System.getProperty(property);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }

        return defaultValue;
    }

    public static boolean getBooleanValue(String envvar, String property, boolean defaultValue) {
        String value = getStringValue(envvar, property, null);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }

        return Boolean.parseBoolean(value);
    }

    public static int getIntegerValue(String envvar, String property, int defaultValue) {
        String value = getStringValue(envvar, property, null);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    public static double getDoubleValue(String envvar, String property, double defaultValue) {
        String value = getStringValue(envvar, property, null);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }

        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private Properties() {
        throw new AssertionError("Instantiating utility class.");
    }
}
