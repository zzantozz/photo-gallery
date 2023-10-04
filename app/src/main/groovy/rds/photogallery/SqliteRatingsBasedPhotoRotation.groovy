package rds.photogallery

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.sql.Connection

/**
 * A {@link PhotoRotation} that selects photos based on ratings using the sqlite ratings db. The database must be fully
 * populated before using this class. The ratings frequency is hardcoded here. It should be made configurable in some
 * way.
 */
class SqliteRatingsBasedPhotoRotation implements PhotoRotation {

    private static final Logger log = LoggerFactory.getLogger(SqliteRatingsBasedPhotoRotation.class)
    public static final String CYCLE_EXHAUSTED = 'Cycle Exhausted'

    final def frequencies = [:]
    final def flatFreqList
    final def totalFreqs
    final def rand = new Random()
    final Map<Integer, String> currentCycleByRating

    SqliteRatingsBasedPhotoRotation() {
        def frequencyChart = [(-999): 16, (0): 1, (1): 2, (2): 4, (3): 8, (4): 16, (5): 32]
        def conn = App.instance.sqliteDataSource.getConnection()
        def statement = conn.createStatement()
        def resultSet = statement.executeQuery('select distinct(rating) from photos')
        while (resultSet.next()) {
            int rating = resultSet.getInt(1)
            frequencies.put(rating, frequencyChart[rating])
        }
        resultSet.close()
        statement.close()
        conn.close()

        flatFreqList = frequencies.collectMany { Collections.nCopies(it.value, it.key) }
        totalFreqs = flatFreqList.size()
        currentCycleByRating = frequencies.collectEntries { [it.key, 'A'] }
    }

    @Override
    String next() {
        App.metrics().timeAndReturn('find random in sqlite', this::doNext)
    }

    String doNext() {
        def rating = flatFreqList[rand.nextInt(totalFreqs)]
        def cycleName = currentCycleByRating[rating]
        def conn = App.instance.sqliteDataSource.getConnection()
        if (log.isInfoEnabled()) {
            dumpDbStats(conn)
        }
        String result = findOneByRating(conn, rating, cycleName)
        if (result == CYCLE_EXHAUSTED) {
            // Either we've cycled all photos in this rating, or there are zero photos with this rating.
            def nextCycle = cycleName == 'A' ? 'B' : 'A'
            log.info("Setting cycle for rating $rating to $nextCycle")
            currentCycleByRating[rating] = nextCycle
            result = findOneByRating(conn, rating, nextCycle)
        }
        if (result == CYCLE_EXHAUSTED) {
            log.warn('Just refreshed a cycle, but still got CYCLE_EXHAUSTED. Is there an empty photo cycle here?')
        }
        conn.close()
        result
    }

    void dumpDbStats(Connection conn) {
        Map<Integer, Integer> pastPhotos = new LinkedHashMap<>().withDefault {0}
        Map<Integer, Integer> comingPhotos = new LinkedHashMap<>().withDefault {0}
        def statement = conn.createStatement()
        def resultSet = statement.executeQuery(
                'select rating, cycle, count(*) from photos group by rating, cycle order by rating')
        while (resultSet.next()) {
            Integer rating = resultSet.getInt(1)
            String cycle = resultSet.getString(2)
            Integer count = resultSet.getInt(3)
            Map<Integer, Integer> toUpdate
            def currentCycle = currentCycleByRating[rating]
            if (cycle == currentCycle) {
                toUpdate = pastPhotos
            } else {
                toUpdate = comingPhotos
                def otherCycle = currentCycle == 'A' ? 'B' : 'A'
            }
            toUpdate.put(rating, count)
        }
        resultSet.close()
        statement.close()
        def allKeys = (pastPhotos.keySet() + comingPhotos.keySet()).unique().sort()
        def descs = allKeys.collect {"$it(${currentCycleByRating[it]}): ${comingPhotos[it]}->${pastPhotos[it]}" }
        log.info("Rating cycles: $descs")
    }

    static String findOneByRating(Connection conn, int rating, String cycleName) {
        def findPhotoSql = 'select relative_path from photos where rating = ? and cycle != ? order by random() limit 1'
        def findPhotoStmt = conn.prepareStatement(findPhotoSql)
        findPhotoStmt.setInt(1, rating)
        findPhotoStmt.setString(2, cycleName)
        def resultSet = findPhotoStmt.executeQuery()
        final String result
        if (resultSet.next()) {
            result = resultSet.getString('relative_path')
            def updateCycleSql = 'update photos set cycle = ? where relative_path = ?'
            def updateCycleStmt = conn.prepareStatement(updateCycleSql)
            updateCycleStmt.setString(1, cycleName)
            updateCycleStmt.setString(2, result)
            updateCycleStmt.execute()
            updateCycleStmt.close()
        } else {
            result = CYCLE_EXHAUSTED
        }
        resultSet.close()
        findPhotoStmt.close()
        result
    }
}
