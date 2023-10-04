package rds.photogallery;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.awt.image.ImageProducer;
import java.awt.image.renderable.RenderableImageProducer;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;

/**
 * Shows a single photo. It's told which photo to display at any given time and doesn't try to do anything but display
 * it.
 *
 * This guy is in Java instead of Groovy because of classes like java.awt.{Point, Rectangle} that have int properties
 * like "x" and getters like getX() shadowing them that return doubles instead of ints. Groovy always resolves object.x
 * to object.getX() if it exists, and I don't want to risk losing precision.
 */
public class PhotoPanel extends JPanel {
    public static final Logger log = LoggerFactory.getLogger(PhotoPanel.class);
    private final String name;
    private CompletePhoto photo;
    private boolean showName;
    private boolean showRating;
    private boolean showTags;
    // Gif animation relies on supplying an observer to any of the painting or drawing methods. There's an animation
    // thread that calls back to the imageUpdate() method to let it know to repaint with the new animation frame. That
    // means this panel needs to get that callback so it can do the painting. However, it also seems that the lifecycle
    // of the animation thread is tied to the given observer, so if you give this panel as the observer, the threads
    // never terminate, since this panel lasts forever (or at least until the frame is reconfigured). When that happens,
    // you end up with a ton of image animator threads, which seem to still be sending updates, and it fills up the
    // heap. To avoid this, we can create a specific observer object for each gif when it needs to be visible. This
    // object will relay imageUpdate() calls to the panel and be dereferenced when the gif is no longer shown so that it
    // can be garbage collected. In practice, this seems to make the image animator threads terminate pretty quickly.
    private ImageObserver myGifObserver;

    public PhotoPanel(String name) {
        this.name = name;
        this.addMouseListener(App.getInstance().makeMeAPopupListener(this));
    }

    public void setPhoto(CompletePhoto photo) {
        if (!this.isVisible()) {
            log.info("You wasted your time getting here!");
        }
        // Release the former gif observer so it and the photo can be GC'd.
        this.myGifObserver = null;
        this.photo = photo;
        if (photo.getGif() != null) {
            this.myGifObserver = (img, infoflags, x, y, width, height) ->
                    PhotoPanel.this.imageUpdate(img, infoflags, x, y, width, height);
        }
    }

    public CompletePhoto getPhotoOnDisplay() {
        return photo;
    }

    /**
     * Returns true if this panel's currently visible photo is sized correctly for the panel.
     */
    public boolean imageFitsPanel() {
        // gifs get sized locally, so there's nothing to check for
        if (photo.getGif() != null) {
            return true;
        }
        BufferedImage image = photo.getImage();
        int imageWidth = image.getWidth();
        int myWidth = this.getWidth();
        int imageHeight = image.getHeight();
        int myHeight = this.getHeight();
        return (imageWidth == myWidth && imageHeight <= myHeight) ||
                (imageHeight == myHeight && imageWidth <= myWidth);
    }
    @Override
    public void paint(Graphics g) {
        if (photo == null) {
            return;
        }
        // Use these values for everything, in case of concurrent updates of the current "photo" value. I.e.
        // "this.photo" should never be referenced after this point.
        final BufferedImage image = photo.getImage();
        final Image gifImage = photo.getGif();
        final PhotoData photoData = photo.getData();
        App.metrics().time("paint photo panel", () -> {
            if (photo.getGif() != null) {
                paintGif(g, gifImage, photoData);
            } else {
                paintNonGif(g, image, photoData);
            }
        });
    }

    private void paintGif(Graphics g, Image gifImage, PhotoData photoData) {
        super.paint(g);
        fillBlack(g, getBounds());
        final int myWidth = getWidth();
        final int myHeight = getHeight();
        final int imageWidth = gifImage.getWidth(myGifObserver);
        if (imageWidth == -1) {
            // Image not fully available yet, just draw what we have to get it loaded, I guess
            g.drawImage(gifImage, 0, 0, myGifObserver);
        } else {
            final int imageHeight = gifImage.getHeight(myGifObserver);
            final double widthRatio = (double) imageWidth / (double) myWidth;
            final double heightRatio = (double) imageHeight / (double) myHeight;
            final int newWidth;
            final int newHeight;
            if (widthRatio > heightRatio) {
                final double widthScale = (double) myWidth / (double) imageWidth;
                newWidth = myWidth;
                newHeight = (int) (imageHeight * widthScale);
            } else {
                final double heightScale = (double) myHeight / (double) imageHeight;
                newWidth = (int) (imageWidth * heightScale);
                newHeight = myHeight;
            }
            final Point centerPosition = findCenterPosition(
                    new Dimension(newWidth, newHeight),
                    new Dimension(myWidth, myHeight));
            g.drawImage(gifImage, centerPosition.x, centerPosition.y, newWidth, newHeight, myGifObserver);
            drawActiveOverlays(photoData, g);
        }
    }

    private void paintNonGif(Graphics g, BufferedImage image, PhotoData photoData) {
        BufferedImage backBuffer = getGraphicsConfiguration().createCompatibleImage(getWidth(), getHeight());
        Graphics backBufferGraphics = backBuffer.getGraphics();
        fillBlack(backBufferGraphics, getBounds());
        // Ideally, this image will have already been scaled for this panel, but if the frame is getting resized, or
        // the frame layout is being modified, panels can be a different size than their image until a new one is
        // given to them. If the image fits, draw it. If not, scale it right here to fit the panel. It'll be lower
        // quality, but at least it fits visually until a new image can be delivered.
        if (imageFitsPanel()) {
            // Image was scaled to this panel, so just draw it
            Point centerPosition = findCenterPosition(new Dimension(image.getWidth(null), image.getHeight(null)), this.getSize());
            backBufferGraphics.drawImage(image, centerPosition.x, centerPosition.y, null);
        } else {
            // Image is wrong size for this panel, so let the controller know, and scale it to match.
            App.getInstance().getController().panelImageSizeIsWrong(this);
            int imageWidth = image.getWidth();
            int myWidth = this.getWidth();
            int imageHeight = image.getHeight();
            int myHeight = this.getHeight();
            double imageRatio = (double) imageWidth / imageHeight;
            double panelRatio = (double) myWidth / myHeight;
            final int newWidth;
            final int newHeight;
            if (imageRatio > panelRatio) {
                newWidth = myWidth;
                newHeight = (int) (imageHeight * ((double) newWidth / imageWidth));
            } else {
                newHeight = myHeight;
                newWidth = (int) (imageWidth * ((double) newHeight / imageHeight));
            }
            Point centerPosition = findCenterPosition(new Dimension(newWidth, newHeight), this.getSize());
            backBufferGraphics.drawImage(image, centerPosition.x, centerPosition.y, newWidth, newHeight, null);
        }
        drawActiveOverlays(photoData, backBufferGraphics);
        g.drawImage(backBuffer, 0, 0, null);
        backBufferGraphics.dispose();
    }

    private void drawActiveOverlays(PhotoData photoData, Graphics graphics) {
        if (showTags) {
            graphics.setColor(Color.BLUE);
            if (photoData != null) {
                Iterable<String> allTags = Iterables.concat(photoData.getUserTags(), photoData.getImplicitTags());
                String tagString = Joiner.on(", ").join(allTags);
                graphics.drawString(tagString, 5, getHeight() - 5);
            }
        }
        if (showName) {
            graphics.setColor(Color.BLUE);
            int height = getHeight() - 5;
            if (showTags) {
                height -= 12;
            }
            if (photoData != null) {
                graphics.drawString(photoData.getRelativePath(), 5, height);
            }
        }
        if (showRating) {
            String text;
            if (photoData.getRating().equals(PhotoData.UNRATED)) {
                text = "U";
            } else if (photoData.getRating().equals(0)) {
                text = "X";
            } else {
                int stars = photoData.getRating();
                char[] chars = new char[stars];
                Arrays.fill(chars, '*');
                text = new String(chars);
            }
            int height = getHeight() - 5;
            if (showName) {
                height -= 12;
            }
            if (showTags) {
                height -= 12;
            }
            graphics.setColor(Color.BLUE);
            graphics.drawString(text, 10, height);
            graphics.setColor(Color.GREEN);
            graphics.drawString(text, getWidth() - 35, height);
        }
    }


    public void setShowingNames(boolean b) {
        this.showName = b;
        repaint();
    }

    public void setShowingRatings(boolean b) {
        this.showRating = b;
        repaint();
    }

    public void setShowingTags(boolean b) {
        this.showTags = b;
        repaint();
    }

    public static Point findCenterPosition(Dimension window, Dimension fullArea) {
        int x = (int) (fullArea.getWidth() - window.getWidth()) / 2;
        int y = (int) (fullArea.getHeight() - window.getHeight()) / 2;
        return new Point(x, y);
    }

    public void fillBlack(Graphics graphics, Rectangle rectangle) {
        graphics.setColor(Color.BLACK);
        int x = (int) rectangle.getX();
        int y = (int) rectangle.getY();
        int width = (int) rectangle.getWidth();
        int height = (int) rectangle.getHeight();
        graphics.fillRect(x, y, width, height);
    }

    public void refresh() {
        repaint();
    }

    @Override
    public String toString() {
        return "PhotoPanel{" +
                "name='" + name + '\'' +
                ", photo=" + photo +
                '}';
    }
}
