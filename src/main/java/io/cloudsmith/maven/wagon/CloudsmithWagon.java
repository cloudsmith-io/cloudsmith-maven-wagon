package io.cloudsmith.maven.wagon;

import com.google.gson.internal.LinkedTreeMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map.Entry;

import io.cloudsmith.api.ApiClient;
import io.cloudsmith.api.ApiException;
import io.cloudsmith.api.apis.FilesApi;
import io.cloudsmith.api.apis.PackagesApi;
import io.cloudsmith.api.apis.ReposApi;
import io.cloudsmith.api.Configuration;
import io.cloudsmith.api.models.FilesCreate;
import io.cloudsmith.api.models.MavenPackageUpload;
import io.cloudsmith.api.models.PackageFileUpload;
import io.cloudsmith.api.models.PackageStatus;
import io.cloudsmith.api.models.PackagesUploadMaven;
import io.cloudsmith.api.models.Repository;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.MultipartBody.Builder;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import okio.BufferedSink;
import okio.Okio;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.http.client.utils.URIBuilder;
import org.apache.maven.wagon.AbstractWagon;
import org.apache.maven.wagon.authentication.AuthenticationException;
import org.apache.maven.wagon.authorization.AuthorizationException;
import org.apache.maven.wagon.events.TransferEvent;
import org.apache.maven.wagon.ConnectionException;
import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.resource.Resource;
import org.apache.maven.wagon.TransferFailedException;
import org.apache.tika.Tika;


/**
 * Implements the interface for Cloudsmith Maven Wagons.
 */
public class CloudsmithWagon extends AbstractWagon {

    private enum FileType { UNK, POM, PKG, DOC, SRC };

    private static final String METADATA_XML = "maven-metadata.xml";

    private static final MediaType MEDIA_TYPE_JAR    = MediaType.parse("application/java-archive");
    private static final MediaType MEDIA_TYPE_X_JAR  = MediaType.parse("application/x-java-archive");
    private static final MediaType MEDIA_TYPE_X_EAR  = MediaType.parse("application/x-ear");
    private static final MediaType MEDIA_TYPE_X_WAR  = MediaType.parse("application/x-war");
    private static final MediaType MEDIA_TYPE_X_AAR  = MediaType.parse("application/x-aar");
    private static final MediaType MEDIA_TYPE_ZIP    = MediaType.parse("application/zip");
    private static final MediaType[] MEDIA_TYPE_JAR_LIKES = new MediaType[]{
        MEDIA_TYPE_JAR, MEDIA_TYPE_X_JAR, MEDIA_TYPE_X_EAR, MEDIA_TYPE_X_WAR,
        MEDIA_TYPE_X_AAR, MEDIA_TYPE_ZIP
    };
    private static final MediaType MEDIA_TYPE_XML  = MediaType.parse("application/xml");
    private static final MediaType[] MEDIA_TYPE_POM_LIKES = new MediaType[]{
        MEDIA_TYPE_XML
    };
    private static final Integer PACKAGE_SYNC_WAIT = 5000;
    private static final Tika TIKA = new Tika();

    private String               cdnUrl     = "";
    private CloudsmithRepository repository = null;

    private ApiClient           client         = null;
    private PackagesUploadMaven packageParams  = null;

    /* Using static here for now because of issues with attached stages running twice. */
    private static Boolean      uploadFailed   = false;
    private static Boolean      uploadComplete = false;

    public CloudsmithWagon() {
        super();
    }

    @Override
    protected void openConnectionInternal()
            throws ConnectionException, AuthenticationException {
        logDebug("openConnectionInternal", "opening");
        resetState();
        configure();
    }

    @Override
    public void get(String source, File destination)
            throws TransferFailedException, ResourceDoesNotExistException,
                   AuthorizationException {
        if (uploadFailed) {
            return;
        }

        if (isPathMetadataXml(destination.toString())) {
            /* Don't bother - Downloading is eventually consistent. */
            return;
        }

        logDebug(
            "get",
            "\n  Source      =", source,
            "\n  Destination =", destination.getName()
        );

        Resource resource = new Resource(source);
        fireGetInitiated(resource, destination);

        String baseUrl;
        String url;

        try {
            baseUrl = getCdnUrl();
            url = getQualifiedCdnUrl(source);
        } catch (ApiException | URISyntaxException ex) {
            logError(ex.getMessage());
            fireTransferError(resource, ex, TransferEvent.REQUEST_GET);
            throw new TransferFailedException("Error getting repository CDN: ", ex);
        }

        logDebug(
            "get",
            "\n  BaseUrl      =", baseUrl,
            "\n  QualifiedUrl =", url);

        Request request = new Request.Builder()
            .url(url)
            .get()
            .build();

        OkHttpClient httpclient = new OkHttpClient();

        fireGetStarted(resource, destination);
        Response response = null;
        BufferedSink sink = null;

        try {
            response = httpclient.newCall(request).execute();
            sink = Okio.buffer(Okio.sink(destination));
            sink.writeAll(response.body().source());
        } catch (IOException ex) {
            logError(ex.getMessage());
            fireTransferError(resource, ex, TransferEvent.REQUEST_GET);
            throw new TransferFailedException("Error downloading file: ", ex);
        } finally {
            if (response != null) {
                response.body().close();
            }

            try {
                if (sink != null) {
                    sink.close();
                }
            } catch (IOException ex) {
                /* Absorb */
            }
        }

        fireGetCompleted(resource, destination);
    }

    @Override
    public boolean getIfNewer(String resourceName, File destination, long timestamp)
            throws TransferFailedException, ResourceDoesNotExistException, AuthorizationException {
        logDebug(
            "getIfNewer",
            "\n  ResourceName =", resourceName,
            "\n  Destination  =", destination.getName(),
            "\n  Timestamp    =", Long.toString(timestamp)
        );

        return false;
    }

    @Override
    public void put(File source, String destination)
            throws TransferFailedException, ResourceDoesNotExistException,
                   AuthorizationException {
        if (uploadFailed || uploadComplete) {
            return;
        }

        if (isPathMetadataXml(destination)) {
            /* This should be the last file in a normal upload. */
            finalisePackage();
            return;
        }

        logDebug(
            "put",
            "\n  Source      =", source.getName(),
            "\n  Destination =", destination
        );

        FileType fileType = FileType.UNK;
        Resource resource = getResourceFromFile(source);
        String filename = getPathFilename(destination);
        MediaType mediaType = null;

        if (filename != null) {
            try {
                mediaType = getFileMediaType(source);
                fileType = determineFileType(mediaType, source, filename);
            } catch (IOException ex) {
                setUploadFailed(true);
                logError("Could not determine file type:", ex.getMessage());
                fireTransferError(resource, ex, TransferEvent.REQUEST_PUT);
                throw new TransferFailedException("Could not determine file type: ", ex);
            }
        }

        if (fileType == FileType.UNK) {
            logDebug("put", "Skipping unsupported file:", source.getName());
            return;
        }

        firePutInitiated(resource, source);
        FilesCreate filesParams = new FilesCreate();
        filesParams.setFilename(source.getName());

        try {
            filesParams.setMd5Checksum(getChecksum(source));
        } catch (IOException ex) {
            setUploadFailed(true);
            logError("Could not calculate file checksum:", ex.getMessage());
            fireTransferError(resource, ex, TransferEvent.REQUEST_PUT);
            throw new TransferFailedException("Could not calculate file checksum: ", ex);
        }

        FilesApi filesApi = new FilesApi(this.client);
        CloudsmithRepository csmRepository = getCloudsmithRepository();
        PackageFileUpload uploadParams;

        logInfo("Requesting file upload for", filename, "...");

        try {
            uploadParams = filesApi.filesCreate(
                csmRepository.getOwnerName(),
                csmRepository.getRepositoryName(),
                filesParams
            );
        } catch (ApiException ex) {
            setUploadFailed(true);
            logError("Could not request file upload:", ex.getResponseBody());
            fireTransferError(resource, ex, TransferEvent.REQUEST_PUT);
            throw new TransferFailedException("Could not request file upload:", ex);
        }

        String identifier = uploadParams.getIdentifier();
        switch (fileType) {
            case POM:
                this.packageParams.setPomFile(identifier);
                break;

            case PKG:
                this.packageParams.setPackageFile(identifier);
                break;

            case DOC:
                this.packageParams.setJavadocFile(identifier);
                break;

            case SRC:
                this.packageParams.setSourcesFile(identifier);
                break;

            default:
                /* Not handled */
                return;
        }

        Builder body = new MultipartBody.Builder();
        body.setType(MultipartBody.FORM);

        @SuppressWarnings("unchecked")
        LinkedTreeMap<String, String> o =
            (LinkedTreeMap<String, String>) uploadParams.getUploadFields();
        for (Entry<String, String> attr : o.entrySet()) {
            body.addFormDataPart(attr.getKey(), attr.getValue());
        }

        body.addFormDataPart(
            "file", source.getName(), RequestBody.create(mediaType, source)
        );
        body.addFormDataPart("md5_checksum", filesParams.getMd5Checksum());

        RequestBody requestBody = body.build();
        Request request = new Request.Builder()
                .url(uploadParams.getUploadUrl())
                .post(requestBody)
                .build();

        OkHttpClient httpclient = new OkHttpClient();

        firePutStarted(resource, source);
        Response response = null;

        logInfo("Uploading", filename, "...");

        try {
            response = httpclient.newCall(request).execute();
            checkUploadSuccess(response, source);
        } catch (IOException | CloudsmithWagonException ex) {
            setUploadFailed(true);
            logError("Could not upload file:", ex.getMessage());
            fireTransferError(resource, ex, TransferEvent.REQUEST_PUT);
            throw new TransferFailedException("Could not upload file:", ex);
        } finally {
            if (response != null) {
                response.body().close();
            }
        }

        firePutCompleted(resource, source);
        logInfo("Uploaded", filename);
    }

    @Override
    public void closeConnection() throws ConnectionException {
    }

    // helper methods

    /**
     * Finalise the uploaded files into a Cloudsmith package.
     */
    private void finalisePackage() throws TransferFailedException {
        if (uploadFailed || uploadComplete) {
            return;
        }

        if (this.packageParams.getPackageFile() == null) {
            logError(
                "Could not find a package to upload - This is probably our "
                + "fault, please log an issue at:", Properties.getUrl());
            return;
        }

        logInfo("Creating a new Maven package ...");

        CloudsmithRepository csmRepository = getCloudsmithRepository();
        PackagesApi packagesApi = new PackagesApi(this.client);
        MavenPackageUpload packageData;

        try {
            packageData =
                packagesApi.packagesUploadMaven(
                    csmRepository.getOwnerName(),
                    csmRepository.getRepositoryName(),
                    this.packageParams);
            logDebug("finalisePackage", packageData.toString());
        } catch (ApiException ex) {
            setUploadFailed(true);
            logError("Could not create package:", ex.getResponseBody());
            throw new TransferFailedException("Could not create package: ", ex);
        }

        try {
            PackageStatus status = null;
            do {
                // TODO(ls): Make this configurable
                if (status != null) {
                    try {
                        Thread.sleep(PACKAGE_SYNC_WAIT);
                    } catch (InterruptedException ex) {
                    }
                }

                status =
                    packagesApi.packagesStatus(
                        csmRepository.getOwnerName(),
                        csmRepository.getRepositoryName(),
                        packageData.getSlug()
                    );
                logDebug(
                    "finalisePackage",
                    "\n  Status   =", status.getStatusStr(),
                    "\n  Stage    =", status.getStageStr(),
                    "\n  Progress =",
                        Integer.toString(status.getSyncProgress())
                );

            } while (!status.getIsSyncCompleted()
                     && !status.getIsSyncFailed());
        } catch (ApiException ex) {
            setUploadFailed(true);
            logError("Could not wait for package:", ex.getResponseBody());
            throw new TransferFailedException("Could not wait for package:", ex);
        }

        logInfo(
            "Created:",
            csmRepository.getOwnerName() + "/"
            + csmRepository.getRepositoryName()
            + "/" + packageData.getSlug());

        setUploadComplete(true);
    }

    /**
     * Calculate the MD5 checksum for a file.
     */
    private String getChecksum(File file) throws IOException {
        FileInputStream stream = new FileInputStream(file);
        return DigestUtils.md5Hex(stream);
    }

    /**
     * Determine filetype of artifact.
     */
    private FileType determineFileType(
            MediaType mediaType, File source, String filename) throws IOException {
        FileType fileType = FileType.UNK;
        String sourceName = getPathFilename(source.getName());

        if (sourceName != null) {
            if (isFilePomLike(mediaType, filename)) {
                // The package POM
                fileType = FileType.POM;
            } else if (isFileJarLike(mediaType, filename)) {
                // The package, javadoc, sources or tests Java archive
                if (sourceName.contains("-javadoc.")) {
                    fileType = FileType.DOC;
                } else if (sourceName.contains("-sources.")) {
                    fileType = FileType.SRC;
                } else {
                    fileType = FileType.PKG;
                }
            }
        }

        return fileType;
    }

    /**
     * Check if a file is a JAR-like type.
     */
    private boolean isFileJarLike(MediaType mediaType, String filename) {
        for (MediaType jarMediaType : MEDIA_TYPE_JAR_LIKES) {
            if (mediaType.equals(jarMediaType)) {
                return true;
            }
        }

        String extension = getFileExtension(filename);
        return (extension != null && extension.toLowerCase().endsWith("ar"));
    }

    /**
     * Check if a file is a POM-like type.
     */
    private boolean isFilePomLike(MediaType mediaType, String filename) {
        for (MediaType pomMediaType : MEDIA_TYPE_POM_LIKES) {
            if (mediaType.equals(pomMediaType)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get the file extension for a filename.
     */
    private String getFileExtension(String filename) {
        int k = filename.lastIndexOf('.');

        if (k > 0) {
            return filename.substring(k + 1);
        } else {
            return null;
        }
    }

    /**
     * Create a Resource object from a file.
     */
    private Resource getResourceFromFile(File file) {
        Resource resource = new Resource(file.getName());
        resource.setContentLength(file.length());
        resource.setLastModified(file.lastModified());
        return resource;
    }

    /**
     * Log error.
     */
    private void logError(String... messageParts) {
        String message = String.format(
            "[ERROR] %s", String.join(" ", messageParts)
        );

        System.err.println(message);
    }

    /**
     * Log information.
     */
    private void logInfo(String... messageParts) {
        String message = String.format(
            "[INFO] %s", String.join(" ", messageParts)
        );

        System.out.println(message);
    }

    /**
     * Log debug information.
     */
    private void logDebug(String context, String... messageParts) {
        String message = String.format(
            "[DEBUG] %s(): %s", context, String.join(" ", messageParts)
        );

        if (Properties.isDebug()) {
            System.out.println(message);
        }

        fireSessionDebug(message);
    }

    /**
     * Reset state for the wagon.
     */
    private void resetState() {
        this.packageParams = new PackagesUploadMaven();
    }

    /**
     * Setup configuration for the wagon.
     */
    private void configure() {
        CloudsmithRepository csmRepository = getCloudsmithRepository();

        this.client = Configuration.getDefaultApiClient();
        this.client.setDebugging(Properties.isDebug());
        this.client.setBasePath(csmRepository.getApiUrl());
        this.client.setApiKey(getApiKey());
        this.client.setUserAgent(
            String.format(
                "cloudsmith-maven-wagon/%s wagon:%s api:%s",
                System.getProperty("os.name"),
                Properties.getVersion(),
                Properties.getApiVersion()
            )
        );
    }

    /**
     * Check if a file upload was successful.
     */
    private boolean checkUploadSuccess(Response response, File source)
            throws CloudsmithWagonException {
        try {
            if (response.code() == 400) {
                throw new CloudsmithWagonException(
                    "Bad Request: " + response.body().string()
                );
            }

            if (!response.isSuccessful()) {
                throw new CloudsmithWagonException(
                    "Failed to upload file: " + source.getName()
                );
            }
        } catch (IOException ex) {
            return false;
        }

        return true;
    }

    /**
     * Construct a CloudsmithRepository from the Maven Repository.
     */
    private CloudsmithRepository getCloudsmithRepository() {
        if (this.repository == null) {
            this.repository = new CloudsmithRepository(getRepository());
        }

        return this.repository;
    }

    /**
     * Get the base CDN URL (for downloads) from the Cloudsmith repository.
     */
    private String getCdnUrl() throws ApiException, URISyntaxException {
        if (this.cdnUrl == null || this.cdnUrl.isEmpty()) {
            CloudsmithRepository csmRepository = getCloudsmithRepository();

            ReposApi reposApi = new ReposApi(this.client);
            Repository upstream = reposApi.reposRead(
                csmRepository.getOwnerName(),
                csmRepository.getRepositoryName());

            // TODO(ls): Error handling
            URIBuilder builder = new URIBuilder(upstream.getCdnUrl());
            URI uri = builder.setPath(builder.getPath() + "/maven")
                .build().normalize();
            this.cdnUrl = uri.toString();
        }

        return this.cdnUrl;
    }

    /**
     * Get the filename from a path.
     */
    private String getPathFilename(String path) {
        Path filePath = Paths.get(path);

        if (filePath != null) {
            Path filePathName = filePath.getFileName();

            if (filePathName != null) {
                return filePathName.toString();
            }
        }

        return null;
    }

    /**
     * Check if a file is a metadata XML file.
     */
    private boolean isPathMetadataXml(String path) {
        String filename = getPathFilename(path);
        return (filename != null && filename.equals(METADATA_XML));
    }

    /**
     * Get a qualified (full path) for a file on the Cloudsmith CDN.
     */
    private String getQualifiedCdnUrl(String path)
            throws ApiException, URISyntaxException {
        URIBuilder builder = new URIBuilder(getCdnUrl());
        URI uri = builder.setPath(builder.getPath() + "/" + path)
            .build().normalize();
        return uri.toString();
    }

    /**
     * Determine the media type for a local file.
     */
    private MediaType getFileMediaType(File file) throws IOException {
        return MediaType.parse(TIKA.detect(file));
    }

    /**
     * Get the configured Cloudsmith API Key.
     */
    private String getApiKey() {
        String apiKey;

        apiKey = System.getProperty("cloudsmith.api_key");
        if (apiKey != null && !apiKey.isEmpty()) {
            return apiKey;
        }

        apiKey = System.getenv("CLOUDSMITH_API_KEY");
        if (apiKey != null && !apiKey.isEmpty()) {
            return apiKey;
        }

        return getAuthenticationInfo().getPassword();
    }

    /**
     * Flag the Wagon operation as failed.
     */
    private static void setUploadFailed(boolean state) {
        uploadFailed = state;
    }

    /**
     * Flag the Wagon operation as completed.
     */
    private static void setUploadComplete(boolean state) {
        uploadComplete = state;
    }
}
