package rds.photogallery;

import org.imgscalr.Scalr;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.function.Function;

/**
 * Holds useful methods for working with photos. It's Java instead of Groovy for the same reason as {@link PhotoPanel}.
 */
public class PhotoTools {
    /**
     * Resizes a given image to fit in a given size using native Java libs to do so. So far, I haven't been impressed
     * with its performance. Something like imgproxy is far more efficient.
     */
    public static BufferedImage resizeImage(BufferedImage loadedImage, Dimension maxImageSize, Function<Object[], Void> logger) {
        // Scalr has an automatic mode, but it wasn't shrinking landscape images enough. It seemed to decide that it was
        // "better" to not fit inside the size I told it, and they came out too tall.
        final BufferedImage result;
        double widthRatio = (double) loadedImage.getWidth() / maxImageSize.getWidth();
        double heightRatio = (double) loadedImage.getHeight() / maxImageSize.getHeight();
        final Scalr.Mode resizeMode;
        if (widthRatio > heightRatio) {
            resizeMode = Scalr.Mode.FIT_TO_WIDTH;
        } else {
            resizeMode = Scalr.Mode.FIT_TO_HEIGHT;
        }
        result = Scalr.resize(loadedImage, Scalr.Method.ULTRA_QUALITY, resizeMode, maxImageSize.width, maxImageSize.height, Scalr.OP_ANTIALIAS);
        // I've had lots of trouble with scaling an image to fit inside a certain dimension.
        if (result.getWidth() > maxImageSize.getWidth() || result.getHeight() > maxImageSize.getHeight()) {
            if (result.getWidth() > maxImageSize.width || result.getHeight() > maxImageSize.height) {
                logger.apply(new Object[] {
                        "I tried to scale an image to fit in {} but got an image of size {}. Original image was {}.",
                        maxImageSize.width + "x" + maxImageSize.height,
                        result.getWidth() + "x" + result.getHeight(),
                        loadedImage.getWidth() + "x" + loadedImage.getHeight()});
            }
        }
        return result;
    }
}
