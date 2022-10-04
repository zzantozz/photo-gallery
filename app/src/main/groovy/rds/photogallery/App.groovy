package rds.photogallery

import javax.swing.*
import java.awt.*
import java.util.concurrent.Executors

class App {
    static void main(String[] args) {
        def generalWorkPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() + 1)
        def scheduler = Executors.newSingleThreadScheduledExecutor()
        String photoRootDir = JOptionPane.showInputDialog("Enter path to photo dir")
        def photoLister = new FileSystemPhotoLister(photoRootDir)
        def photoContentLoader = new FileSystemPhotoContentLoader(photoRootDir)
        def controller = new PhotosController(photoLister, photoContentLoader, scheduler, generalWorkPool)
        def frames = [
                new PhotoFrame('frame1', new PersistentFrameState().with {
                    fullScreen = false
                    normalConfig = new FrameConfiguration()
                    fullScreenConfig = new FrameConfiguration().with {
                        distractionFree = true
                        x = 0
                        y = 0
                        width = (int) Toolkit.defaultToolkit.screenSize.width
                        height = (int) Toolkit.defaultToolkit.screenSize.height
                        it
                    }
                    it
                })
        ]
        frames.each {controller.adopt(it) }
        controller.start()
        frames.each { it.show()}
    }
}
