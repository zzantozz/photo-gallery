package rds.photogallery;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
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

    public PhotoPanel(String name) {
        this.name = name;
    }

    public void setPhoto(CompletePhoto photo) {
        if (!this.isVisible()) {
            log.info("You wasted your time getting here!");
        }
        this.photo = photo;
    }

    public CompletePhoto getPhotoOnDisplay() {
        return photo;
    }

    /**
     * Returns true if this panel's currently visible photo is sized correctly for the panel.
     */
    public boolean imageFitsPanel() {
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
        final PhotoData photoData = photo.getData();
        App.metrics().time("paint photo panel", () -> {
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
        });
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
