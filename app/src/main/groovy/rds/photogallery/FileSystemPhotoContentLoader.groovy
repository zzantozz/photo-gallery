package rds.photogallery

import javax.imageio.ImageIO

class FileSystemPhotoContentLoader implements PhotoContentLoader {
    String photoRootDir

    FileSystemPhotoContentLoader(String photoRootDir) {
        this.photoRootDir = photoRootDir
    }

    @Override
    CompletePhoto load(String photoRelativePath) {
        def pathToLoad = App.instance.resolvePathWithRewrites(photoRelativePath)
        try {
            def read = ImageIO.read(new File(photoRootDir, pathToLoad))
            if (read == null) {
                throw new IllegalStateException("Failed to read image from file: " + pathToLoad)
            }
            return new CompletePhoto(photoRelativePath, read)
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load image from path: " + pathToLoad, e)
        }
    }
}
