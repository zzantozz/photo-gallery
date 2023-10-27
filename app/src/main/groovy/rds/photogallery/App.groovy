package rds.photogallery

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.google.common.util.concurrent.ThreadFactoryBuilder
import org.apache.commons.io.FilenameUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
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

    private static final Logger log = LoggerFactory.getLogger(App.class)

    // Keeps track of frames, just so we can know when the last frame is closed and stop the app as a result
    private List<PhotoFrame> photoFrames = []
    private ExecutorService generalWorkPool
    private ScheduledExecutorService scheduler
    private PersistentFrameState lastFrameState
    private DataSource sqliteDataSource

    private Settings settings
    private Metrics metrics
    // Remember the root dir we're loading photos from, mainly so relative paths can be quickly resolved.
    private String rootDir

    private PhotoContentLoader photoContentLoader
    private PhotosController controller
    private AtomicInteger frameCount = new AtomicInteger(1)

    private static final App INSTANCE = new App()
    public static final String REWRITE_SUFFIX = '-rewrite'
    private PhotoDataSource localData

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

    PhotosController getController() {
        controller
    }

    PhotoContentLoader getPhotoContentLoader() {
        photoContentLoader
    }

    DataSource getSqliteDataSource() {
        return sqliteDataSource
    }

    ExecutorService getGeneralWorkPool() {
        throw new UnsupportedOperationException("Don't get the work pool directly. Use an appropriate submit* method!")
    }

    ScheduledExecutorService getScheduler() {
        throw new UnsupportedOperationException("Don't get the work pool directly. Use an appropriate schedule* method!")
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
        rootDir = settings.asString(Settings.Setting.PHOTO_ROOT_DIR)
        if (!rootDir) {
            rootDir = JOptionPane.showInputDialog("Enter path to photo dir")
        }
        if (!rootDir) {
            System.err.println("You must provide a root dir to find photos in.")
            System.exit(1)
        }
        def photoDataFilePath = settings.asString(Settings.Setting.PHOTO_DATA_FILE)
        if (!Paths.get(photoDataFilePath).isAbsolute()) {
            settings.setString(Settings.Setting.PHOTO_DATA_FILE, rootDir + '/' + photoDataFilePath)
        }
        settings.setString(Settings.Setting.PHOTO_ROOT_DIR, rootDir)
        def photoRotation = new RandomDirWalkPhotoRotation(Paths.get(rootDir))
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
        localData = LocalDataIO.loadLocalData(
                new File(settings.asString(Settings.Setting.PHOTO_DATA_FILE)),
                { true },
                new File(rootDir))
        sqliteDataSource = new SQLiteDataSource()
        sqliteDataSource.setUrl('jdbc:sqlite:photos.sqlite')
        // A couple of settings to drastically increase speed at the expense of possible data loss, but this is an
        // ephemeral database that's built on demand.
        sqliteDataSource.setSynchronous(SQLiteConfig.SynchronousMode.OFF.toString())
        sqliteDataSource.setJournalMode(SQLiteConfig.JournalMode.WAL.toString())
        // Allow for updates to the database - not normal, but it can be useful to modify the db on the fly to test
        // things out or just for fun.
        sqliteDataSource.setBusyTimeout(10000);
        buildRatingsDb()
        controller.switchRotation(new SqliteRatingsBasedPhotoRotation())
    }

    /**
     * Builds a sqlite db consisting of all photos found in the selected root dir, enriched with metadata from the local
     * photo data source, if any. This db serves as the mechanism for selecting a random photo of a given rating that
     * hasn't yet been displayed.
     */
    private void buildRatingsDb() {
        def excludedPaths = settings.asStringList(Settings.Setting.EXCLUDED_PATHS)
        def tagsFilter = new TagsFilter(settings.asString(Settings.Setting.TAG_FILTER))
        def photoPredicate = { PhotoData it ->
            def filteredByTags = tagsFilter.apply(it)
            def filteredByPath = !excludedPaths.isEmpty() && excludedPaths.any { path ->
                it.relativePath.startsWith(path)
            }
            !filteredByTags && !filteredByPath
        }
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
                def photoData = this.localData.getPhotoData(photoPath)
                if (!photoPredicate(photoData)) {
                    log.trace("Filtering out of db: " + photoData)
                    continue
                }
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
            photoFrames.remove(it)
            if (photoFrames.isEmpty()) {
                lastFrameState = it.frameState
                shutDown()
            }
        }
        registerGlobalHotKeys(newFrame)
        photoFrames.add(newFrame)
        newFrame.show()
    }

    private void registerGlobalHotKeys(PhotoFrame photoFrame) {
        photoFrame.addHotKey("ctrl S", "Save database", (e) -> {
            int answer = JOptionPane.showConfirmDialog(
                    null, "Saving photo db. Are you sure?", "Save DB", JOptionPane.YES_NO_OPTION);
            if (answer == JOptionPane.YES_OPTION) {
                def photoDataFile = settings.asString(Settings.Setting.PHOTO_DATA_FILE)
                LocalDataIO.saveLocalData(new File(photoDataFile), localData)
            }
        });
        photoFrame.addHotKey("ESCAPE", "Quit", (e) -> {
            controller.stopAutoChanging()
            photoFrames.each{ it.hide() }
            int answer = HomelessDialog.showConfirmDialog(null, 'Are you sure?', 'Exit now?', JOptionPane.YES_NO_OPTION)
            if (answer == JOptionPane.YES_OPTION) {
                shutDown()
            } else {
                photoFrames.each{ it.show() }
                controller.startAutoChanging()
            }
        });
    }

    static boolean isRewrite(Path path) {
        FilenameUtils.removeExtension(path.toString()).endsWith(REWRITE_SUFFIX)
    }

    String resolveRewrite(String relativePath) {
        String dir = FilenameUtils.getFullPath(relativePath)
        String baseName = FilenameUtils.getBaseName(relativePath)
        String extension = FilenameUtils.getExtension(relativePath)
        String comprehensiveWayPath = comprehensiveRewriteCheck(relativePath, dir, baseName, extension)
        String cheapWayPath = cheapRewriteCheck(relativePath, dir, baseName, extension)
        if (cheapWayPath != comprehensiveWayPath) {
            log.warn("Found a rewrite inconsistency for {}. Cheap path determined {} but comprehensive path determined {}",
                    relativePath, cheapWayPath, comprehensiveWayPath)
        }
        cheapWayPath
    }

    /**
     * Looks for rewrites comprehensively, by scanning the whole directory for files with matching base names. This is
     * the original way but means more file system access, which can be slow when it's a network mount.
     */
    String comprehensiveRewriteCheck(String relativePath, String dir, String baseName, String extension) {
        String[] list = new File(rootDir, dir).list((dir1, name) -> {
            String n = name.toLowerCase()
            String rewriteBase = baseName.toLowerCase() + REWRITE_SUFFIX
            return n.startsWith(rewriteBase) && n.endsWith(extension.toLowerCase())
        })
        final String pathToLoad
        if (list == null) {
            log.warn("Failed to list contents of {}", dir)
            pathToLoad = relativePath
        } else if (list.length > 1) {
            log.warn("Found multiple rewrite files for {}: {}", relativePath, list)
            pathToLoad = FilenameUtils.concat(dir, list[0])
        } else if (list.length == 0) {
            pathToLoad = relativePath
        } else {
            pathToLoad = FilenameUtils.concat(dir, list[0])
        }
        // Make sure separators are correct, mostly for testing
        FilenameUtils.separatorsToUnix(pathToLoad)
    }

    /**
     * Looks for rewrites a cheap way with respect to file system access. Just check for the existence of specific
     * files. Could miss rewrites with odd naming casing, particularly in the file extension.
     */
    String cheapRewriteCheck(String relativePath, String dir, String baseName, String extension) {
        File searchDir = new File(rootDir, dir)
        String rewriteName1 = baseName + REWRITE_SUFFIX + "." + extension.toLowerCase()
        String rewriteName2 = baseName + REWRITE_SUFFIX + "." + extension.toUpperCase()
        final String pathToLoad
        if (new File(searchDir, rewriteName1).exists()) {
            log.info("Load {} in place of {}", rewriteName1, relativePath)
            pathToLoad = FilenameUtils.concat(dir, rewriteName1)
        } else if (new File(searchDir, rewriteName2).exists()) {
            log.info("Load {} in place of {}", rewriteName2, relativePath)
            pathToLoad = FilenameUtils.concat(dir, rewriteName2)
        } else {
            log.info("Load {}", relativePath)
            pathToLoad = relativePath
        }
        // Make sure separators are correct, mostly for testing
        FilenameUtils.separatorsToUnix(pathToLoad)
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
        controller.dispose()
        photoFrames.each { it.dispose() }
    }

    File resolvePhotoPath(String photoPath) {
        // While this whole app is built to treat paths as relative to some base dir, allowing for absolute paths here
        // allows the possibility of sneaking other photos into the rotation by just inserting them into the db at
        // runtime.
        def path = Paths.get(photoPath)
        if (path.isAbsolute()) {
            return path.toFile()
        } else {
            return new File(rootDir, photoPath)
        }
    }

    File resolvePhotoPath(PhotoData photoData) {
        resolvePhotoPath(photoData.relativePath)
    }

    PhotoData getPhotoData(String relativePath) {
        def result = null
        // TODO: PhotoData is hacked into the constructor of CompletePhoto, which will get called before data is loaded
        if (localData) {
            result = localData.getPhotoData(relativePath)
        }
        return result ? result : new PhotoData(relativePath)
    }

    PopupListener makeMeAPopupListener(PhotoPanel photoPanel) {
        new PopupListener(photoPanel)
    }

    /**
     * Changes or sets the rating of a photo. This is the one and only safe way to do so. It ensures the data is updated
     * in the photo data and also in the current photo rotation so that the change is both saved long term and takes
     * effect immediately.
     */
    void changeRating(PhotoData photoData, int newRating) {
        localData.changeRating(photoData, newRating)
        def conn = sqliteDataSource.getConnection()
        def sql = 'update photos set rating = ? where relative_path = ?'
        def stmt = conn.prepareStatement(sql)
        stmt.setInt(1, newRating)
        stmt.setString(2, photoData.relativePath)
        int count = stmt.executeUpdate()
        stmt.close()
        conn.close()
        if (count != 1) {
            throw new IllegalStateException("Should have updated exactly one entry in sqlite, updated " + count)
        }
    }
}
