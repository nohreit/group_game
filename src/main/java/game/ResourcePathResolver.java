package main.java.game;

import java.net.URI;

public class ResourcePathResolver {
    public static String resolve(String baseResourcePath, String relativePath) {
        // baseResourcePath: "/maps/demo.json"
        // relativePath: "../../tiled/Terrain.json"
        if (baseResourcePath == null || relativePath == null) {
            throw new IllegalArgumentException("Paths must not be null");
        }


        // Get directory part: "/maps/"
        int lastSlash = baseResourcePath.lastIndexOf('/');
        String baseDir = (lastSlash >= 0)
                ? baseResourcePath.substring(0, lastSlash + 1)
                : "/";

        // Use a fake "file:" URI just for normalization
        URI baseUri = URI.create("file:" + baseDir);
        URI resolved = baseUri.resolve(relativePath);  // handles ../ and ./

        // Extract the normalized path (still usable as a classpath resource)
        // e.g. "/tiled/Terrain.json"
        // Returns a normalized absolute classpath resource path (starts with '/')
        return resolved.getPath();
    }
}
