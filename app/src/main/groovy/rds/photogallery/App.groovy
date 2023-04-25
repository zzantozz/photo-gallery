package rds.photogallery

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.google.common.util.concurrent.ThreadFactoryBuilder
import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteDataSource

import javax.sql.DataSource
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

    // Keeps track of frames, just so we can know when the last frame is closed and stop the app as a result
    List<PhotoFrame> photoFrames = []
    ExecutorService generalWorkPool
    ScheduledExecutorService scheduler
    PersistentFrameState lastFrameState
    DataSource sqliteDataSource

    Settings settings
    Metrics metrics
    // Remember the root dir we're loading photos from, mainly so relative paths can be quickly resolved.
    String rootDir

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

    static Settings settings() {
        instance.settings
    }

    static Metrics metrics() {
        instance.metrics
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
        settings = new Settings()
        metrics = new Metrics()
        // First, do a quick start to get something on the screen. This should be as fast as possible. Leave out any
        // unnecessary steps.
        generalWorkPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() + 1,
                new ThreadFactoryBuilder().setNameFormat('general-worker-%d').build())
        // TODO: Scheduler is bad! It's taking the place of what should be reactive, event driven things!
        scheduler = Executors.newSingleThreadScheduledExecutor(
                new ThreadFactoryBuilder().setNameFormat('scheduler-%d').build())
        rootDir = JOptionPane.showInputDialog("Enter path to photo dir")
        settings.setString(Settings.Setting.PHOTO_DATA_FILE, rootDir + '/photo-db.txt')
        settings.setString(Settings.Setting.PHOTO_ROOT_DIR, rootDir)
        def photoRotation = new FileSystemPhotoRotation(rootDir)
        photoContentLoader = new FileSystemPhotoContentLoader(rootDir)
        controller = new PhotosController(photoRotation)

        def frameStateConfigFilePath = Paths.get(settings.asString(Settings.Setting.FRAME_STATE_FILE))
        getInitialFrameStates(frameStateConfigFilePath).each {
            newPhotoFrame(it as PersistentFrameState)
        }
        this.controller.start()

        // TODO: sleeping a while lets initial photos load, but this next bit is still really resource intensive.
        // need to find a way to deconflict from the app that's already visible.
        sleep(3000)

        // Now that the quick version is up and running, we do the heavy lifting to get all the features loaded.
        sqliteDataSource = new SQLiteDataSource()
        sqliteDataSource.setUrl('jdbc:sqlite:photos.sqlite')
        // A couple of settings to drastically increase speed at the expense of possible data loss, but this is an
        // ephemeral database that's built on demand.
        sqliteDataSource.setSynchronous(SQLiteConfig.SynchronousMode.OFF.toString())
        sqliteDataSource.setJournalMode(SQLiteConfig.JournalMode.WAL.toString())
        buildRatingsDb()
        controller.switchRotation(new SqliteRatingsBasedPhotoRotation())
    }

    /**
     * Builds a sqlite db consisting of all photos found in the selected root dir, enriched with metadata from the local
     * photo data source, if any. This db serves as the mechanism for selecting a random photo of a given rating that
     * hasn't yet been displayed.
     */
    private void buildRatingsDb() {
        def photoDataSource = DataSourceLoader.loadLocalData(
                new File(settings.asString(Settings.Setting.PHOTO_DATA_FILE)),
                { true },
                new File(rootDir))
        def listerForDb = new FileSystemPhotoLister(rootDir)
        def connection = sqliteDataSource.getConnection()
        def initDbStmt = connection.createStatement()
        // dump the data before re-populating. just a temporary measure, i think.
        // maybe i can find a way to reuse the data in the future?
        initDbStmt.execute('drop table if exists photos')
        initDbStmt.execute('create table main.photos(' +
                'pk serial primary key,' +
                'relative_path text not null,' +
                'rating integer not null,' +
                'cycle text not null' +
                ')'
        )
        def insertSql = 'insert into photos (relative_path, rating, cycle) values (?, ?, ?)'
        def insertStmt = connection.prepareStatement(insertSql)
        int batchCount = 0
        metrics.time('insert all rows to db', {
            while (listerForDb.hasNext()) {
                def photoPath = listerForDb.next()
                def photoData = photoDataSource.getPhotoData(photoPath)
                insertStmt.setString(1, photoPath)
                insertStmt.setInt(2, photoData.rating)
                insertStmt.setString(3, 'none yet')
                insertStmt.addBatch()
                if (++batchCount >= 100) {
                    batchCount = 0
                    insertStmt.executeBatch()
                    // TODO: get rid of this if there's a better way to free up resources
                    sleep(50)
                }
            }
            // Make sure to insert any final things that didn't make up a full batch
            insertStmt.executeBatch()
        })
        insertStmt.close()
        connection.close()
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

    /**
     * Adds a new frame to the app set to the given frame state.
     */
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

    /**
     * Handles app shutdown. This is typically triggered by someone pressing the "exit" hotkey or by closing the last
     * frame of the app. It generally takes care of saving current state so it can be restored at the next startup.
     */
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
        Files.write(Paths.get(settings.asString(Settings.Setting.FRAME_STATE_FILE)), frameStatesString.bytes)
    }

    File resolvePhotoPath(String photoPath) {
        new File(rootDir, photoPath)
    }
}
