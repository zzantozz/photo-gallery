package rds.photogallery

import javax.imageio.ImageIO

class FileSystemPhotoContentLoader implements PhotoContentLoader {
    String photoRootDir

    FileSystemPhotoContentLoader(String photoRootDir) {
        this.photoRootDir = photoRootDir
    }

    @Override
    CompletePhoto load(String photoRelativePath) {
        try {
            def read = ImageIO.read(new File(photoRootDir, photoRelativePath))
            if (read == null) {
                throw new IllegalStateException("Failed to read image from file: " + photoRelativePath)
            }
            return new CompletePhoto(photoRelativePath, read)
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load image from path: " + photoRelativePath, e)
        }
    }
}
