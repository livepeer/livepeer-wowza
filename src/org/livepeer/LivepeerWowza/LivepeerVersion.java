package org.livepeer.LivepeerWowza;

/**
 * Class containing the current version of LivepeerWowza. Get hyped.
 */
public final class LivepeerVersion {

    /**
     * Current version of LivepeerWowza.
     * 
     * This gets overriden during the build process in the Docker file to
     * the current version as provided by git describe --always --dirty --tags
     */
    public static final String VERSION = "UNKNOWN_VERSION";
}
