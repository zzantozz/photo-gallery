package rds.photogallery

/**
 * Knows how to get the content for a photo. The app only deals with a photo's metadata until the content is needed
 * because loading content is slow and resource-hungry.
 */
interface PhotoContentLoader {
    CompletePhoto load(String photoRelativePath)
    CompletePhoto getToolkitImage(String photoRelativePath)
}
