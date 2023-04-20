package rds.photogallery;

/**
 * Provides an infinite stream of photos to display. It's like an Iterator but never ends.
 */
public interface PhotoRotation {
    String next();
}
