package io.cloudsmith.maven.wagon;

public final class Properties {

    private static final String  API_VERSION = "${cloudsmith.api_version}";
    private static final String  ARTIFACT_ID = "${project.artifactId}";
    private static final String  BUILD_NUMBER = "${buildNumber}";
    private static final Boolean DEBUG = Boolean.parseBoolean(System.getProperty("cloudsmith.debug"));
    private static final String  GROUP_ID = "${project.groupId}";
    private static final String  VERSION = "${project.version}";
    private static final String  URL = "${project.url}";

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

    public static Boolean isDebug() {
        return DEBUG;
    }

    public static String getUrl() {
        return URL;
    }

    private Properties() {
        throw new AssertionError("Instantiating utility class.");
    }
}
