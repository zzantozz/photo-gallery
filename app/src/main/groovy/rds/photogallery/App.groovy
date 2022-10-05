package rds.photogallery

import com.fasterxml.jackson.databind.ObjectMapper

import javax.swing.*
import java.awt.*
import java.nio.file.Files
import java.nio.file.Paths
import java.util.List
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.atomic.AtomicInteger

/*
 * A singleton that represents the running application. This acts sort of as an IoC container for everything else. It
 * wires everything together at startup and provides methods that expose important functionality that the rest of the
 * app needs.
 */
class App {

    List<PhotoFrame> photoFrames = []
    ExecutorService generalWorkPool
    ScheduledExecutorService scheduler
    PersistentFrameState lastFrameState
    String frameStatePath = 'frame-state.json'

    PhotosController controller
    AtomicInteger frameCount = new AtomicInteger(1)

    private static final App INSTANCE = new App()

    static App getInstance() {
        INSTANCE
    }

    static void main(String[] args) {
        getInstance().start()
    }

    def start() {
        generalWorkPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() + 1)
        scheduler = Executors.newSingleThreadScheduledExecutor()
        String photoRootDir = JOptionPane.showInputDialog("Enter path to photo dir")
        def photoLister = new FileSystemPhotoLister(photoRootDir)
        def photoContentLoader = new FileSystemPhotoContentLoader(photoRootDir)
        controller = new PhotosController(photoLister, photoContentLoader, this.scheduler, this.generalWorkPool)

        def frameStateConfigFilePath = Paths.get(frameStatePath)
        final List<PersistentFrameState> frameStates
        if (Files.exists(frameStateConfigFilePath)) {
            def mapper = new ObjectMapper()
            frameStates = mapper.readValue(frameStateConfigFilePath.toFile(), List)
        } else {
            frameStates = [new PersistentFrameState().with {
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
            }]
        }
        frameStates.each { newPhotoFrame(it as PersistentFrameState) }
        this.controller.start()
    }

    def newPhotoFrame(PersistentFrameState frameState) {
        def newFrame = new PhotoFrame("frame" + frameCount.getAndIncrement(), frameState)
        newFrame.onDispose {
            controller.unadopt(it)
            photoFrames.remove(it)
            if (photoFrames.isEmpty()) {
                lastFrameState = it.frameState
                shutDown()
            }
        }
        photoFrames.add(newFrame)
        controller.adopt(newFrame)
        newFrame.show()
    }

    def shutDown() {
        generalWorkPool.shutdown()
        scheduler.shutdown()
        List<PersistentFrameState> frameStates
        if (photoFrames.isEmpty()) {
            if (lastFrameState == null) {
                throw new IllegalStateException('Reached shutdown with no frames and no "last frame state"!')
            }
            frameStates = [lastFrameState]
        } else {
            frameStates = photoFrames.collect { it.frameState }
        }
        def mapper = new ObjectMapper()
        def frameStatesString = mapper.writeValueAsString(frameStates)
        Files.write(Paths.get(frameStatePath), frameStatesString.bytes)
    }
}
