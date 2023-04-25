package rds.photogallery

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class FileSystemPhotoLister implements PhotoLister {
    String rootDir
    Iterator<Path> fileIterator
    List<String> photoFileExtensions = ['jpg', 'jpeg', 'png', 'gif']

    FileSystemPhotoLister(String rootDir) {
        this.rootDir = rootDir
        this.fileIterator = getIterator()
    }

    @Override
    boolean hasNext() {
        fileIterator.hasNext();
    }

    @Override
    String next() {
        fileIterator.next()
    }

    private Iterator<Path> getIterator() {
        def rootPath = Paths.get(rootDir)
        this.fileIterator = App.metrics().timeAndReturn("getting photo iterator from file system", {
            Files.walk(Paths.get(rootDir))
                    .filter(Files::isRegularFile)
                    .filter(p -> !App.isRewrite(p))
                    .filter((p) -> photoFileExtensions.any {
                        p.fileName.toString().toLowerCase().endsWith(it)
                    })
                    .map(rootPath::relativize)
                    .iterator()
        })
        fileIterator
    }
}
