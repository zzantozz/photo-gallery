package rds.photogallery

/**
 * A {@link PhotoRotation} that selects photos based on ratings using the sqlite ratings db. The database must be fully
 * populated before using this class. The ratings frequency is hardcoded here. It should be made configurable in some
 * way.
 */
class SqliteRatingsBasedPhotoRotation implements PhotoRotation {

    def frequencies = [(-999): 3, (0): 1, (1): 2, (2): 4, (3): 8, (4): 16, (5): 32]
    def flatFreqList = frequencies.collectMany { Collections.nCopies(it.value, it.key) }
    def totalFreqs = flatFreqList.size()
    def rand = new Random()
    Map<Integer, String> currentCycleByRating = frequencies.collectEntries { [it.key, 'A'] }

    @Override
    String next() {
        def rating = flatFreqList[rand.nextInt(totalFreqs)]
        def cycleName = currentCycleByRating[rating]
        def conn = App.instance.sqliteDataSource.getConnection()
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
            // Either we've cycled all photos in this rating, or there are zero photos with this rating.
            def nextCycle = cycleName == 'A' ? 'B' : 'A'
            currentCycleByRating[rating] = nextCycle
            def updateCycleSql = 'update photos set cycle = ? where rating = ?'
            def updateCycleStmt = conn.prepareStatement(updateCycleSql)
            updateCycleStmt.setString(1, nextCycle)
            updateCycleStmt.setInt(2, rating)
            updateCycleStmt.execute()
            updateCycleStmt.close()
            result = 'FAKE_PHOTO_PATH_AT_END_OF_CYCLE'
        }
        resultSet.close()
        findPhotoStmt.close()
        conn.close()
        result
    }
}
