package io.cloudsmith.maven.wagon;


import org.apache.maven.wagon.WagonException;


/**
 * Cloudsmith-specific wagon errors.
 */
@SuppressWarnings("serial")
public class CloudsmithWagonException extends WagonException {

    public CloudsmithWagonException(final String message, Throwable throwable) {
        super(message, throwable);
    }

    public CloudsmithWagonException(final String message) {
        super(message);
    }
}
