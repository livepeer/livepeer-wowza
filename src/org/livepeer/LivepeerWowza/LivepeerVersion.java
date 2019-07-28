package org.livepeer.LivepeerWowza;

public final class LivepeerVersion {

    /**
     * This gets overriden during the build process in the Docker file to
     * the current version as provided by git describe --always --dirty --tags
     */
    public static final String VERSION = "UNKNOWN_VERSION";
}