package rds.photogallery

/**
 * Provides a list of all the photos present, regardless if photo data exists for them. This makes it so that new photos
 * will show up in the app without needing data to first be added. This is generally for initially loading and starting
 * the app. Once running, a {@link PhotoRotation} will provide an endless stream of photos to display.
 */
interface PhotoLister {
    boolean hasNext();
    String next()
}
