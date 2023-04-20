package rds.photogallery

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.google.common.util.concurrent.ThreadFactoryBuilder

import javax.swing.JOptionPane
import java.awt.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.List
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
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

    PhotoContentLoader photoContentLoader
    PhotosController controller
    AtomicInteger frameCount = new AtomicInteger(1)

    private static final App INSTANCE = new App()

    static App getInstance() {
        INSTANCE
    }

    static void main(String[] args) {
        getInstance().start()
    }

    void submitGeneralWork(Runnable task) {
        generalWorkPool.submit(new ThrowableReporting.Runnable() {
            @Override
            void doRun() throws Throwable {
                task.run()
            }
        })
    }

    void scheduleWithFixedDelay(Runnable task, long initialDelay, long delay, TimeUnit timeUnit) {
        scheduler.scheduleWithFixedDelay(new ThrowableReporting.Runnable() {
            @Override
            void doRun() throws Throwable {
                task.run()
            }
        }, initialDelay, delay, timeUnit)
    }

    def start() {
        generalWorkPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() + 1,
                new ThreadFactoryBuilder().setNameFormat('general-worker-%d').build())
        scheduler = Executors.newSingleThreadScheduledExecutor(
                new ThreadFactoryBuilder().setNameFormat('scheduler-%d').build())
        String photoRootDir = JOptionPane.showInputDialog("Enter path to photo dir")
        def photoLister = new FileSystemPhotoLister(photoRootDir)
        photoContentLoader = new FileSystemPhotoContentLoader(photoRootDir)
        controller = new PhotosController(photoLister)

        def frameStateConfigFilePath = Paths.get(frameStatePath)
        getInitialFrameStates(frameStateConfigFilePath).each {
            newPhotoFrame(it as PersistentFrameState)
        }
        this.controller.start()
    }

    /**
     * Gets initial frame states to show. If a file containing previous states is present, it'll oad that. Otherwise, it
     * creates a single frame in a default state.
     */
    private static List<PersistentFrameState> getInitialFrameStates(Path frameStateConfigFilePath) {
        if (Files.exists(frameStateConfigFilePath)) {
            def mapper = new ObjectMapper()
            return mapper.readValue(frameStateConfigFilePath.toFile(), List)
        } else {
            return [new PersistentFrameState().with {
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
        final List<PersistentFrameState> frameStates
        if (photoFrames.isEmpty()) {
            if (lastFrameState == null) {
                throw new IllegalStateException('Reached shutdown with no frames and no "last frame state"!')
            }
            frameStates = [lastFrameState]
        } else {
            frameStates = photoFrames.collect { it.frameState }
        }
        def mapper = new ObjectMapper()
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        def frameStatesString = mapper.writeValueAsString(frameStates) + '\n'
        Files.write(Paths.get(frameStatePath), frameStatesString.bytes)
    }
}
