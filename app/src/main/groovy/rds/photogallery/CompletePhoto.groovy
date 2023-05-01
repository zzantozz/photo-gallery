package rds.photogallery

import java.awt.image.BufferedImage

class CompletePhoto {
    final String relativePath
    final PhotoData data
    BufferedImage image

    CompletePhoto(String relativePath, BufferedImage image) {
        this.relativePath = relativePath
        this.image = image
        // TODO: This really doesn't belong here! PhotoData should be one of the first things retrieved, before the content is loaded!
        this.data = App.instance.getPhotoData(relativePath)
    }

    @Override
    String toString() {
        return "CompletePhoto{" +
                "relativePath='" + relativePath + '\'' +
                '}'
    }
}
