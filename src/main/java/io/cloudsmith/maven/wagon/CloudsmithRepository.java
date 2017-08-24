package io.cloudsmith.maven.wagon;

import java.net.URL;
import java.net.MalformedURLException;
import java.util.Arrays;

import org.apache.maven.wagon.repository.Repository;


/**
 * Holds additional information about upstream Cloudsmith repos.
 */
@SuppressWarnings("serial")
public class CloudsmithRepository {
    private static final String PREFIX = "cloudsmith";
    private static final String DEFAULT_HOST = "api.cloudsmith.io";
    private static final String DEFAULT_PATH = "";

    private Repository repository = null;
    private String apiUrl;
    private String ownerName;
    private String repositoryName;

    public CloudsmithRepository(Repository repository) {
        this.repository = repository;
        determineCloudsmithInfo();
    }

    public String getOwnerName() {
        return this.ownerName;
    }

    public String getRepositoryName() {
        return this.repositoryName;
    }

    public String getApiUrl() {
        return this.apiUrl;
    }

    // Helpers

    private void determineCloudsmithInfo() {
        /* Form: cloudsmith://<api_url>/namespace/repo/ */
        String baseUrl = repository.getUrl();

        String[] baseParts = baseUrl.split("\\+");
        if (baseParts.length != 2 || !baseParts[0].equals(PREFIX)) {
            raiseCloudsmithInfoException(baseUrl);
        }

        URL url = null;

        try {
            url = new URL(baseParts[1]);
        } catch (MalformedURLException ex) {
            raiseCloudsmithInfoException(baseUrl);
        }

        String host = DEFAULT_HOST;

        if (url.getHost() != null && !url.getHost().equals(PREFIX)) {
            host = url.getHost();
        }

        String path = DEFAULT_PATH;
        String[] pathParts = url.getPath().split("/");
        if (pathParts.length < 2) {
            raiseCloudsmithInfoException(baseUrl);
        }

        if (pathParts.length > 2) {
            path = String.join(
                "/", Arrays.copyOfRange(pathParts, 0, pathParts.length - 2)
            );
        }

        try {
            url = new URL(url.getProtocol(), host, url.getPort(), path);
        } catch (MalformedURLException ex) {
            raiseCloudsmithInfoException(baseUrl);
        }

        this.apiUrl = url.toString();
        this.ownerName = pathParts[pathParts.length - 2];
        this.repositoryName = pathParts[pathParts.length - 1];
    }

    private void raiseCloudsmithInfoException(String baseUrl) {
        throw new IllegalArgumentException(
           "Repository <url> must be in form of "
            + "'cloudsmith+https://<api_url>/<namespace>/<repo>', got: "
            + baseUrl
        );
    }
}
