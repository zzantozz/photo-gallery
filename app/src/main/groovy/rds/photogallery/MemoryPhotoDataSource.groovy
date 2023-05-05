package rds.photogallery

import java.util.function.Predicate

class MemoryPhotoDataSource implements PhotoDataSource {
    private final Map<String, PhotoData> photoDatasByPath
    private final Predicate<PhotoData> globalFilter
    private final File baseDir

    MemoryPhotoDataSource(Map<String, PhotoData> photoDatasByPath, Predicate<PhotoData> globalFilter, File baseDir) {
        this.baseDir = baseDir
        this.globalFilter = globalFilter
        this.photoDatasByPath = photoDatasByPath
    }

    @Override
    PhotoData getPhotoData(String photoPath) {
        def result = photoDatasByPath[photoPath]
        if (result == null) {
            result = new PhotoData(photoPath)
            photoDatasByPath.put(photoPath, result)
        }
        result
    }

    @Override
    void changeRating(PhotoData photoData, int newRating) {
        throw new UnsupportedOperationException("Not yet implemented")
    }
}
