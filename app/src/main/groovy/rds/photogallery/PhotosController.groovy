package rds.photogallery

import java.util.concurrent.*

/**
 * Manages the photos being shown. This means it knows about all the frames and panels that exist and handles loading
 * and assigning photos to them.
 */
class PhotosController {
    PhotoLister photoLister
    PhotoContentLoader photoContentLoader
    ScheduledExecutorService scheduledExecutorService
    ExecutorService executorService
    List<PhotoFrame> photoFrames = new CopyOnWriteArrayList<>()
    Queue<PhotoPanel> panelsToChange = new ConcurrentLinkedQueue<>()

    PhotosController(PhotoLister photoLister, PhotoContentLoader photoContentLoader,
                     ScheduledExecutorService scheduledExecutorService, ExecutorService executorService) {
        this.photoLister = photoLister
        this.scheduledExecutorService = scheduledExecutorService
        this.executorService = executorService
        this.photoContentLoader = photoContentLoader
    }

    void adopt(PhotoFrame photoFrame) {
        photoFrames.add(photoFrame)
        photoFrame.onDispose {
            unadopt(photoFrame)
        }
    }

    void unadopt(PhotoFrame photoFrame) {
        photoFrames.remove(photoFrame)
        photoFrames.panels.each {
            panelsToChange.remove(it)
        }
    }

    void start() {
        scheduledExecutorService.scheduleWithFixedDelay({ next() }, 2, 2, TimeUnit.SECONDS)
    }

    void next() {
        println "Going next"
        if (panelsToChange.isEmpty()) {
            Collection<PhotoPanel> panels = photoFrames.collectMany { it.getPanels() }
            panelsToChange = new ConcurrentLinkedQueue<>(panels)
        }
        def panelToChange = panelsToChange.remove()
        def nextPhoto = photoLister.next()
        BlockingQueue<CompletePhoto> q = new SynchronousQueue<>()
        def futurePhoto = executorService.submit({
            photoContentLoader.load(nextPhoto)
        } as ThrowableReporting.Callable<CompletePhoto>)
        def futurePanelChange = executorService.submit({
            def photo = futurePhoto.get()
            def resized = PhotoTools.resizeImage(photo.image, panelToChange.size, { println it })
            photo.image = resized
            panelToChange.setPhoto(photo)
            panelToChange.refresh()
        } as ThrowableReporting.Runnable)
        futurePanelChange.get()
    }
}
