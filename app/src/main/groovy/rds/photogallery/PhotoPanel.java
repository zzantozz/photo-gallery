package rds.photogallery;

import org.codehaus.groovy.runtime.DefaultGroovyMethods;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Shows a single photo. It's told which photo to display at any given time and doesn't try to do anything but display
 * it.
 *
 * This guy is in Java instead of Groovy because of classes like java.awt.{Point, Rectangle} that have int properties
 * like "x" and getters like getX() shadowing them that return doubles instead of ints. Groovy always resolves object.x
 * to object.getX() if it exists, and I don't want to risk losing precision.
 */
public class PhotoPanel extends JPanel {
    public void setPhoto(CompletePhoto photo) {
        this.photo = photo;
    }

    @Override
    public void repaint() {
        if (photo == null) {
            DefaultGroovyMethods.println(this, "Nothing to draw!");
            return;
        }
        Metrics.time("repaint photo panel", () -> {
            BufferedImage backBuffer = getGraphicsConfiguration().createCompatibleImage(getWidth(), getHeight());
            Graphics backBufferGraphics = backBuffer.getGraphics();
            fillBlack(backBufferGraphics, getBounds());
            BufferedImage image = photo.getImage();
            Point centerPosition = findCenterPosition(new Dimension(image.getWidth(null), image.getHeight(null)), this.getSize());
            backBufferGraphics.drawImage(image, centerPosition.x, centerPosition.y, null);
            getGraphics().drawImage(backBuffer, 0, 0, null);
            backBufferGraphics.dispose();
        });
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

    public CompletePhoto getPhoto() {
        return photo;
    }

    private CompletePhoto photo;
}
