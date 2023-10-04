package rds.photogallery

import javax.imageio.ImageIO
import java.awt.Toolkit

class FileSystemPhotoContentLoader implements PhotoContentLoader {
    String photoRootDir

    FileSystemPhotoContentLoader(String photoRootDir) {
        this.photoRootDir = photoRootDir
    }

    @Override
    CompletePhoto load(String photoRelativePath) {
        def pathToLoad = App.instance.resolvePhotoPath(photoRelativePath)
        try {
            def read = ImageIO.read(pathToLoad)
            if (read == null) {
                throw new IllegalStateException("Failed to read image from file: " + pathToLoad)
            }
            return new CompletePhoto(photoRelativePath, read)
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load image from path: " + pathToLoad, e)
        }
    }

    @Override
    CompletePhoto getToolkitImage(String photoRelativePath) {
        // todo: buffered image is no longer a required arg
        def result = new CompletePhoto(photoRelativePath, null);
        File file = App.getInstance().resolvePhotoPath(photoRelativePath);
        URL url = new URL("file://" + file.getAbsolutePath());
        result.setGif(Toolkit.getDefaultToolkit().createImage(url));
        result
    }
}
