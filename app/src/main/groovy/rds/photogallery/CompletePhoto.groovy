package rds.photogallery

import java.awt.image.BufferedImage

class CompletePhoto {
    final String relativePath
    BufferedImage image

    CompletePhoto(String relativePath, BufferedImage image) {
        this.relativePath = relativePath
        this.image = image
    }

    @Override
    String toString() {
        return "CompletePhoto{" +
                "relativePath='" + relativePath + '\'' +
                '}'
    }
}
