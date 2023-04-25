package rds.photogallery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
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

    static class PhotoPanelState {
        enum State { INIT, NEW_ASSIGNMENT, IDLE, FAILED, DIRTY }

        State state;
        final PhotoPanel photoPanel;
        String assignedPhotoPath;
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

        public void startLoad(PhotoPanel panel, String path) {
            log.info("startLoad");
        }

        public void finishLoad(PhotoPanel panel, String path) {
            log.info("finishLoad");
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
        // TODO: I really hate scheduling photo changes like this. It should be more of a discrete timer that's reset by
        // any photo changing anywhere, so that things like manually advancing or delayed loading cause photo changes to
        // be properly spaced out.
        App.getInstance().scheduleWithFixedDelay(this::next, 0, changeDelayMillis, TimeUnit.MILLISECONDS);
        // TODO: I really don't like this imperative way of populating panels, either. It should be more reactive as well.
        App.getInstance().scheduleWithFixedDelay(submitNeeds(), 0, 100, TimeUnit.MILLISECONDS);
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
        state.activeLoaders.incrementAndGet();
        Runnable fullfillTheNeed = () -> {
            try {
                // loading stage
                state.startLoad(panel, pathToLoad);
                final CompletePhoto rawPhoto = App.metrics().timeAndReturn("load photo", () ->
                        App.getInstance().getPhotoContentLoader().load(pathToLoad));
                state.finishLoad(panel, pathToLoad);
                if (!pathToLoad.equals(state.assignedPhotoPath)) {
                    log.info("Discarding loaded photo because it's no longer assigned to the panel");
                    return;
                }
                final CompletePhoto photoOnDisplay = panel.getPhotoOnDisplay();
                if (photoOnDisplay != null && pathToLoad.equals(photoOnDisplay.getRelativePath()) &&
                        panel.imageFitsPanel()) {
                    log.info("Discarding loaded photo because the panel already has it");
                    return;
                }
                Function<Object[], Void> logger = objects -> {
                    log.info("log this: " + Arrays.toString(objects));
                    return null;
                };
                state.startResize(panel, pathToLoad);
                final BufferedImage resized = App.metrics().timeAndReturn("resize photo", () ->
                        PhotoTools.resizeImage(rotatedPhoto.getImage(), panel.getSize(), logger));
                state.finishResize(panel, pathToLoad);
                // Check all our return conditions again!
                if (!pathToLoad.equals(state.assignedPhotoPath)) {
                    log.info("Discarding resized photo because it's no longer assigned to the panel");
                    return;
                }
                final CompletePhoto photoOnDisplay2 = panel.getPhotoOnDisplay();
                if (photoOnDisplay2 != null && pathToLoad.equals(photoOnDisplay2.getRelativePath()) &&
                        panel.imageFitsPanel()) {
                    log.info("Discarding resized photo because the panel already has it");
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
            log.info("Auto changing photo on " + oldestState.photoPanel);
            oldestState.assignPhotoPath(photoRotation.next());
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
        photoPanelStates.get(photoPanel).setNeedsRefresh();
    }
}
