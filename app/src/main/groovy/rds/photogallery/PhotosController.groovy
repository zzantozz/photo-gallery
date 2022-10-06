package rds.photogallery

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
            // Shutdown problem here: if user closes final frame causing a shutdown, this can try to paint a final
            // image, resulting in an NPE when trying to getGraphics() from the panel to paint on. Shutdown needs to
            // somehow short-circuit all such activities without causing errors?
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
}
