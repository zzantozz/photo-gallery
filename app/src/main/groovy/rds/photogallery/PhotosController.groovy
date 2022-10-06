package rds.photogallery

import java.awt.Dimension
import java.util.concurrent.*

/**
 * Manages the photos being shown. This means it knows about all the frames and panels that exist and handles loading
 * and assigning photos to them.
 */
class PhotosController {
    PhotoLister photoLister
    PhotoContentLoader photoContentLoader
    List<PhotoFrame> photoFrames = new CopyOnWriteArrayList<>()
    Queue<PhotoPanel> panelsToChange = new ConcurrentLinkedQueue<>()
    PriorityBlockingQueue<PhotoNeed> photoNeeds = new PriorityBlockingQueue<>()

    static class PhotoNeed implements Comparable<PhotoNeed> {
        static final int WRONG_IMAGE_SIZE_PRIORITY = 9
        static final int EMPTY_PANEL_PRIORITY = 10
        int priority
        String relativePath
        Dimension size
        PhotoPanel panel

        PhotoNeed(int priority, String relativePath, Dimension size, PhotoPanel panel) {
            this.relativePath = relativePath
            this.size = size
            this.panel = panel
            this.priority = priority
        }

        @Override
        int compareTo(PhotoNeed o) {
            this.priority <=> o.priority
        }
    }

    PhotosController(PhotoLister photoLister, PhotoContentLoader photoContentLoader) {
        this.photoLister = photoLister
        this.photoContentLoader = photoContentLoader
    }

    void adopt(PhotoFrame photoFrame) {
        photoFrames.add(photoFrame)
    }

    void unadopt(PhotoFrame photoFrame) {
        photoFrames.remove(photoFrame)
        photoFrame.panels.each {
            panelsToChange.remove(it)
        }
    }

    void start() {
        App.instance.scheduler.scheduleWithFixedDelay({ next() }, 0, 3, TimeUnit.SECONDS)
        App.instance.scheduler.scheduleWithFixedDelay( { allocatePhotoNeeds() }, 0, 250, TimeUnit.MILLISECONDS)
    }

    def allocatePhotoNeeds() {
        while (!photoNeeds.isEmpty()) {
            def need = photoNeeds.take()
            App.instance.generalWorkPool.submit({
                def photo = Metrics.timeAndReturn('load needed photo', {
                    photoContentLoader.load(need.relativePath)
                })
                def resized = Metrics.timeAndReturn('resize needed photo', {
                    PhotoTools.resizeImage(photo.image, need.panel.size, { println it })
                })
                photo.image = resized
                need.panel.setPhoto(photo)
                need.panel.refresh()
            })
        }
    }

    void next() {
        println "Going next"
        if (panelsToChange.isEmpty()) {
            Collection<PhotoPanel> panels = photoFrames.collectMany { it.getPanels() }
            panelsToChange = new ConcurrentLinkedQueue<>(panels)
        }
        def panelToChange = panelsToChange.remove()
        def nextPhoto = photoLister.next()
        def futurePhoto = App.instance.generalWorkPool.submit({
            Metrics.timeAndReturn('load photo', { photoContentLoader.load(nextPhoto) })
        } as ThrowableReporting.Callable<CompletePhoto>)
        def futurePanelChange = App.instance.generalWorkPool.submit({
            def photo = futurePhoto.get()
            def resized = Metrics.timeAndReturn('resize photo', {
                PhotoTools.resizeImage(photo.image, panelToChange.size, { println it }) })
            photo.image = resized
            panelToChange.setPhoto(photo)
            panelToChange.refresh()
        } as ThrowableReporting.Runnable)
        futurePanelChange.get()
    }

    void removeRowFromFrame(PhotoFrame photoFrame) {
        def removedPanels = photoFrame.removeRow()
        removedPanels.each { panelsToChange.remove(it) }
    }

    void addRowToFrame(PhotoFrame photoFrame) {
        def newPanels = photoFrame.addRow()
        newPanels.each { panelsToChange.add(it) }
    }

    void removeColumnFromFrame(PhotoFrame photoFrame) {
        def removedPanels = photoFrame.removeColumn()
        removedPanels.each { panelsToChange.remove(it) }
    }

    void addColumnToFrame(PhotoFrame photoFrame) {
        def newPanels = photoFrame.addColumn()
        newPanels.each { panelsToChange.add(it) }
    }

    void panelImageSizeIsWrong(PhotoPanel photoPanel, CompletePhoto photo) {
        photoNeeds.add(new PhotoNeed(PhotoNeed.WRONG_IMAGE_SIZE_PRIORITY, photo.getRelativePath(), photoPanel.getSize(), photoPanel))
    }

    void panelHasNoImage(PhotoPanel photoPanel) {
        photoNeeds.add(new PhotoNeed(PhotoNeed.EMPTY_PANEL_PRIORITY, photoLister.next(), photoPanel.getSize(), photoPanel))
    }
}
