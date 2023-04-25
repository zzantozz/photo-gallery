package rds.photogallery;

import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.tiff.TiffField;
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants;
import org.imgscalr.Scalr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Manages all the photos that are being shown and should be shown. It manages a set of photo frames, including all the
 * panels on those frames. It keeps track of the desired state of all its panels and knows how to get panels to that
 * state.
 */
public class PhotosController {

    public static final Logger log = LoggerFactory.getLogger(PhotosController.class);
    private PhotoRotation photoRotation;
    private List<PhotoFrame> photoFrames = new CopyOnWriteArrayList<>();
    private int changeDelayMillis = 1000;
    // States of tracked panels, used to manage photo changes, resizes, and so on
    private Map<PhotoPanel, PhotoPanelState> photoPanelStates = new ConcurrentHashMap<>();
    private Timer timer;
    private AutoChangeTimerTask autoChangeTimerTask;

    static class PhotoPanelState {
        enum State { INIT, NEW_ASSIGNMENT, IDLE, FAILED, DIRTY }

        State state;
        final PhotoPanel photoPanel;
        String assignedPhotoPath;
        int failureCount;
        long photoAssigned;
        long photoDelivered;
        AtomicInteger activeLoaders = new AtomicInteger();
        public PhotoPanelState(PhotoPanel photoPanel, String photoPath) {
            this.state = State.INIT;
            this.photoPanel = photoPanel;
            assignPhotoPath(photoPath);
        }

        public void assignPhotoPath(String photoPath) {
            assignedPhotoPath = photoPath;
            photoAssigned = System.currentTimeMillis();
            this.state = State.NEW_ASSIGNMENT;
        }

        public boolean isSettled() {
            return state == State.IDLE;
        }

        public void forceSettle(String reason) {
            log.info("Forcing panel " + photoPanel + " to settle because: " + reason);
            this.state = State.IDLE;
        }

        public void startLoad(PhotoPanel panel, String path) {
            log.info("startLoad");
        }

        public void finishLoad(PhotoPanel panel, String path) {
            log.info("finishLoad");
        }

        public void startRotate(PhotoPanel panel, String path) {
            log.info("startRotate");
        }

        public void finishRotate(PhotoPanel panel, String path) {
            log.info("finishRotate");
        }

        public void startResize(PhotoPanel panel, String path) {
            log.info("startResize");
        }

        public void finishResize(PhotoPanel panel, String path) {
            log.info("finishResize");
        }

        public void startDeliver(PhotoPanel panel, String path) {
            log.info("startDeliver");
        }

        public void finishDeliver(PhotoPanel panel, String path) {
            log.info("Photo delivered for " + panel);
            photoDelivered = System.currentTimeMillis();
            // We delivered a photo, but does it match the current state of the panel?
            if (panel.imageFitsPanel()) {
                App.metrics().photoDeliveryTime(System.currentTimeMillis() - photoAssigned);
                this.state = State.IDLE;
            } else {
                log.info("Delivered image isn't a size match, dirtying state!");
                this.state = State.DIRTY;
            }
        }

        public void failure(PhotoPanel panel, String path, Exception e) {
            log.info("failure");
            failureCount++;
            e.printStackTrace();
            this.state = State.FAILED;
        }

        public void setNeedsRefresh() {
            this.state = State.DIRTY;
        }
    }

    public PhotosController(PhotoRotation photoRotation) {
        this.photoRotation = photoRotation;
    }

    public void switchRotation(PhotoRotation photoRotation) {
        this.photoRotation = photoRotation;
    }

    public void adopt(PhotoFrame photoFrame) {
        photoFrames.add(photoFrame);
        managePanels(photoFrame.getPanels());
    }

    public void unadopt(PhotoFrame photoFrame) {
        photoFrames.remove(photoFrame);
        unmanagePanels(photoFrame.getPanels());
    }

    private void managePanel(PhotoPanel panel) {
        managePanels(Collections.singletonList(panel));
    }

    private void managePanels(Collection<PhotoPanel> panels) {
        for (PhotoPanel panel : panels) {
            PhotoPanelState state = new PhotoPanelState(panel, photoRotation.next());
            photoPanelStates.put(panel, state);
        }
    }

    private void unmanagePanel(PhotoPanel panel) {
        unmanagePanels(Collections.singletonList(panel));
    }

    private void unmanagePanels(Collection<PhotoPanel> panels) {
        photoPanelStates.entrySet().removeIf(entry -> panels.contains(entry.getKey()));
    }

    public void start() {
        timer = new Timer("Photo Changer", true);
        startAutoChanging();
        // TODO: I really don't like this imperative way of populating panels, either. It should be more reactive as well.
        App.getInstance().scheduleWithFixedDelay(submitNeeds(), 0, 100, TimeUnit.MILLISECONDS);
    }

    public void startAutoChanging() {
        autoChangeTimerTask = new AutoChangeTimerTask();
        timer.schedule(autoChangeTimerTask, changeDelayMillis);
    }

    public void stopAutoChanging() {
        if (autoChangeTimerTask == null) {
            log.warn("Attempt to stop auto-changing when no change is scheduled");
            return;
        }
        autoChangeTimerTask.cancel();
    }

    private class AutoChangeTimerTask extends TimerTask {
        @Override
        public void run() {
            next();
            startAutoChanging();
        }
    }

    public void pauseAndHideAll() {
        stopAutoChanging();
        photoFrames.forEach(PhotoFrame::hide);
    }

    public void unpauseAndUnhideAll() {
        photoFrames.forEach(PhotoFrame::show);
        startAutoChanging();
    }

    public void dispose() {
        timer.cancel();
        photoFrames.forEach(PhotoFrame::dispose);
    }

    private ThrowableReporting.Runnable submitNeeds() {
        return new ThrowableReporting.Runnable() {
            @Override
            public void doRun() {
                for (PhotoPanelState state : photoPanelStates.values()) {
                    if (!state.isSettled()) {
                        if (compareStateToReality(state)) {
                            log.info("Scheduled work for " + state.photoPanel);
                        }
                    }
                }
            }
        };
    }

    private boolean compareStateToReality(final PhotoPanelState state) {
        // State could change, and we need to remember the original value
        final String pathToLoad = state.assignedPhotoPath;
        final PhotoPanel panel = state.photoPanel;
        if (state.activeLoaders.get() > 1) {
            // Just sanity checking here. If I forget code somewhere else, this will show up.
            log.info("OOPS! More than one loader running for " + state);
        }
        if (state.activeLoaders.get() > 0) {
            // Already loading for this panel/state. Don't queue up double work.
            return false;
        }
        if (state.failureCount > 10) {
            // Bail on this one. Give it a default, broken image so that loading and the photo rotation can go on
            state.failureCount = 0;
            int width = panel.getWidth();
            int height = panel.getHeight();
            BufferedImage brokenImage = panel.getGraphicsConfiguration().createCompatibleImage(width, height);
            Graphics g = brokenImage.getGraphics();
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, width, height);
            g.setColor(Color.RED);
            g.drawString("Failed to load: " + pathToLoad, 10, 10);
            g.dispose();
            state.startDeliver(panel, pathToLoad);
            panel.setPhoto(new CompletePhoto("BROKEN_IMAGE", brokenImage));
            panel.refresh();
            state.finishDeliver(panel, pathToLoad);
            return false;
        }
        state.activeLoaders.incrementAndGet();
        Runnable fullfillTheNeed = () -> {
            try {
                // loading stage
                state.startLoad(panel, pathToLoad);
                final CompletePhoto rawPhoto = App.metrics().timeAndReturn("load photo", () ->
                        App.getInstance().getPhotoContentLoader().load(pathToLoad));
                state.finishLoad(panel, pathToLoad);
                if (shouldStopFulfillment(pathToLoad, state, panel)) {
                    return;
                }
                // rotate stage
                state.startRotate(panel, pathToLoad);
                BufferedImage rotatedImage = App.metrics().timeAndReturn("rotate photo", () ->
                        rotateToOrientation(rawPhoto.getImage(), pathToLoad));
                state.finishRotate(panel, pathToLoad);
                if (shouldStopFulfillment(pathToLoad, state, panel)) {
                    return;
                }
                // resize stage
                CompletePhoto rotatedPhoto = new CompletePhoto(pathToLoad, rotatedImage);
                Function<Object[], Void> logger = objects -> {
                    log.info("log this: " + Arrays.toString(objects));
                    return null;
                };
                state.startResize(panel, pathToLoad);
                final BufferedImage resized = App.metrics().timeAndReturn("resize photo", () ->
                        PhotoTools.resizeImage(rotatedPhoto.getImage(), panel.getSize(), logger));
                state.finishResize(panel, pathToLoad);
                if (shouldStopFulfillment(pathToLoad, state, panel)) {
                    return;
                }
                // deliver stage
                state.startDeliver(panel, pathToLoad);
                CompletePhoto resizedPhoto = new CompletePhoto(pathToLoad, resized);
                panel.setPhoto(resizedPhoto);
                panel.refresh();
                state.finishDeliver(panel, pathToLoad);
            } catch (Exception e) {
                state.failure(panel, pathToLoad, e);
            } finally {
                state.activeLoaders.decrementAndGet();
            }
        };
        App.getInstance().submitGeneralWork(fullfillTheNeed);
        return true;
    }

    private boolean shouldStopFulfillment(String pathToLoad, PhotoPanelState state, PhotoPanel panel) {
        boolean result = false;
        if (!pathToLoad.equals(state.assignedPhotoPath)) {
            log.info("Discarding in-process photo because it's no longer assigned to the panel");
            result = true;
        }
        final CompletePhoto photoOnDisplay = panel.getPhotoOnDisplay();
        if (photoOnDisplay != null && pathToLoad.equals(photoOnDisplay.getRelativePath()) &&
                panel.imageFitsPanel()) {
            log.info("Discarding in-process photo because the panel already has it");
            result = true;
        }
        if (result) {
            state.forceSettle("stopped fulfilling a need");
        }
        return result;
    }

    private BufferedImage rotateToOrientation(BufferedImage image, String relativePath) {
        try {
            // TODO: requires local files
            File imageFile = App.resolvePath(relativePath);
            ImageMetadata metadata1 = Imaging.getMetadata(imageFile);
            // A GIF doesn't return any metadata
            if (metadata1 == null) return image;
            // A PNG seems to give a GenericImageMetadata, which I don't think I can work with
            if (!(metadata1 instanceof JpegImageMetadata)) return image;
            JpegImageMetadata metadata = (JpegImageMetadata) metadata1;
            TiffField rotationField = metadata.findEXIFValue(TiffTagConstants.TIFF_TAG_ORIENTATION);
            if (rotationField == null) return image;
            int rotation = rotationField.getIntValue();
            switch (rotation) {
                case 1:
                    return image;
                case 8:
                    return Scalr.rotate(image, Scalr.Rotation.CW_270, Scalr.OP_ANTIALIAS);
                case 3:
                    return Scalr.rotate(image, Scalr.Rotation.CW_180, Scalr.OP_ANTIALIAS);
                case 6:
                    return Scalr.rotate(image, Scalr.Rotation.CW_90, Scalr.OP_ANTIALIAS);
                default:
                    throw new RuntimeException("Unexpected image rotation: " + rotation);
            }
        } catch (ImageReadException | IOException e) {
            throw new RuntimeException("Error reading image file to get metadata - maybe this shouldn't be fatal", e);
        }
    }


    public void next() {
        if (photoPanelStates.isEmpty()) {
            return;
        }
        // Find the panel that had a photo delivered to it the longest ago
        PhotoPanelState oldestState = photoPanelStates.values().iterator().next();
        for (PhotoPanelState state : photoPanelStates.values()) {
            if (state.photoDelivered < oldestState.photoDelivered) {
                oldestState = state;
            }
        }
        // Now, if that state has no loading activity, and if the state is settled, and if it's been sitting that way
        // for "a while", then assign it a new photo.
        long aWhileAgo = System.currentTimeMillis() - 2500;
        if (oldestState.activeLoaders.get() == 0 && oldestState.isSettled() && oldestState.photoDelivered < aWhileAgo) {
            String next = photoRotation.next();
            log.info("Auto changing photo on " + oldestState.photoPanel + " to " + next);
            oldestState.assignPhotoPath(next);
        }
    }

    public void removeRowFromFrame(PhotoFrame photoFrame) {
        List<PhotoPanel> removedPanels = photoFrame.removeRow();
        unmanagePanels(removedPanels);
    }

    public void addRowToFrame(PhotoFrame photoFrame) {
        List<PhotoPanel> newPanels = photoFrame.addRow();
        managePanels(newPanels);
    }

    public void removeColumnFromFrame(PhotoFrame photoFrame) {
        List<PhotoPanel> removedPanels = photoFrame.removeColumn();
        unmanagePanels(removedPanels);
    }

    public void addColumnToFrame(PhotoFrame photoFrame) {
        List<PhotoPanel> newPanels = photoFrame.addColumn();
        managePanels(newPanels);
    }

    public void panelImageSizeIsWrong(PhotoPanel photoPanel, CompletePhoto photo) {
        PhotoPanelState photoPanelState = photoPanelStates.get(photoPanel);
        if (photoPanelState == null) {
            log.warn("Not tracking state for panel that reported in! {}", photoPanel);
            return;
        }
        photoPanelState.setNeedsRefresh();
    }
}
