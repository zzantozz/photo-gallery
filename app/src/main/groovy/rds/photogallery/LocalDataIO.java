package rds.photogallery;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

import static rds.photogallery.App.REWRITE_SUFFIX;

/**
 * Loads databases. I moved this code into its own class thinking that I can probably abstract loading local data
 * from remote data this way, and produce an appropriate database implementation that can work with no local data
 * or files at all. Time will tell.
 */
public class LocalDataIO {

    private static final Logger log = LoggerFactory.getLogger(LocalDataIO.class);

    public static void saveLocalData(File destinationFile, PhotoDataSource photoDataSource) {
        // Be safe in how we write the db. This is a pretty valuable resource. We'll start by writing to a temp file
        // so that if the writing is interrupted, we haven't corrupted the actual db file. Then, if the temp file
        // actually has different content than the existing db file, move the db file to a backup file, and move the
        // temp file to be the new db file. If there's no difference between the existing db file and the temp file,
        // just delete the temp file and leave everything else alone. This should provide a decent safety net that
        // didn't exist for a long time.
        try {
            final File backupFile = backupFile(destinationFile);
            final File tmpFile = new File(destinationFile.getAbsolutePath() + "~");
            final PrintWriter out = tryFileOpen(tmpFile);
            String dbVersion = "42";
            out.print("VERSION=" + dbVersion + "\r\n");
            photoDataSource.getAllPhotoData()
                    // Don't include "rewrite" versions. Only associate data with the base file name.
                    .filter(data -> !FilenameUtils.getBaseName(data.getPath()).endsWith(REWRITE_SUFFIX))
                    // Don't save data that has no user data in it yet.
                    .filter(data -> !data.isDefault())
                    .sorted(new PhotoDataPathComparator())
                    .forEach(photoData -> {
                        String line;
                        switch (dbVersion) {
                            case "0":
                                line = unparseDataOriginal(photoData);
                                break;
                            case "42":
                                line = JsonDbFormat.unparse(photoData);
                                break;
                            default:
                                throw new RuntimeException("I don't know how to produce data of version " + dbVersion);

                        }
                        // Non-changing line ending to avoid needless photo db backups when switching between platforms.
                        out.print(line + "\r\n");
                    });
            out.close();
            if (tmpFile.length() == 0) {
                log.info("Building the temp file failed silently somehow. Aborting db save.");
                throw new RuntimeException("Failed to save db to temp file.");
            }
            if (Hashing.sha1sum(tmpFile).equals(Hashing.sha1sum(destinationFile))) {
                log.info("No change to db; removing temp file and leaving db files alone.");
                Files.delete(tmpFile.toPath());
                return;
            }
            if (backupFile.exists()) {
                throw new RuntimeException("Failed to pick a unique backup file. It already exists: " + backupFile.getAbsolutePath());
            }
            if (destinationFile.exists()) {
                Files.move(destinationFile.toPath(), backupFile.toPath());
                log.info("Existing db file backed up to {}", backupFile.getAbsolutePath());
            }
            Files.move(tmpFile.toPath(), destinationFile.toPath());
            log.info("Db file replaced with new version. Save successful: {}", destinationFile.getAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Something bad happened while writing the photo db", e);
        }
    }

    private static File backupFile(File destinationFile) {
        String dateString = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        File parent = destinationFile.getParentFile();
        File backupDir = new File(parent, "db-backups");
        if(!backupDir.exists() && !backupDir.mkdirs()) {
            throw new RuntimeException("Failed to create backup dir " + backupDir);
        }
        String baseName = FilenameUtils.getBaseName(destinationFile.getName()) + "." + dateString + ".autobackup.";
        int n = 0;
        File candidate;
        do {
            candidate = new File(backupDir, baseName + n++ + ".txt");
        } while (candidate.exists());
        return candidate;
    }

    public static PrintWriter tryFileOpen(File file) throws IOException {
        FileNotFoundException last = null;
        for (int i = 0; i < 3; i++) {
            try {
                return new PrintWriter(new FileWriter(file));
            } catch (FileNotFoundException e) {
                last = e;
                String message =
                        "Failed to open file for output at:\n" +
                                "  " + file.getAbsolutePath() + "\n" +
                                "If this is on an unreliable mount, take this opportunity to remount it,\n" +
                                "and we'll try again.";
                HomelessDialog.showMessageDialog(null, message);
            }
        }
        throw last;
    }

    private static class PhotoDataPathComparator implements Comparator<PhotoData> {
        @Override
        public int compare(PhotoData o1, PhotoData o2) {
            return o1.getPath().compareTo(o2.getPath());
        }
    }

    private static String unparseDataOriginal(PhotoData photoData) {
        return photoData.getPhotoHash() +
                "," +
                photoData.getPath() +
                "," +
                photoData.getRating() +
                "," +
                Joiner.on("::").join(photoData.getUserTags());
    }

    public static PhotoDataSource loadLocalData(File file, Predicate<PhotoData> globalFilter, File baseDir) {
        Map<String, PhotoData> photoDatasByPath = new HashMap<>();
        try (BufferedReader in = new BufferedReader(new FileReader(file))) {
            String line = in.readLine();
            String version = "0";
            if (line.startsWith("VERSION=")) {
                version = line.substring("VERSION=".length());
                line = in.readLine();
            }
            final Function<String, PhotoData> parser;
            switch (version) {
                case "0":
                    parser = LocalDataIO::parseLineOriginal;
                    break;
                case "42":
                    parser = JsonDbFormat::parse;
                    break;
                default:
                    throw new RuntimeException("I don't know how to load a database of version " + version);
            }
            while (line != null) {
                PhotoData data = parser.apply(line);
                photoDatasByPath.put(data.getPath(), data);
                line = in.readLine();
            }
            return new MemoryPhotoDataSource(photoDatasByPath, globalFilter, baseDir);
        } catch (IOException e) {
            throw new RuntimeException("Something bad happened while reading the photo db", e);
        }
    }

    public static PhotoData parseLineOriginal(String line) {
        String[] parts = line.split(",");
        if (parts.length != 3 && parts.length != 4) {
            throw new IllegalStateException("A db line should have three or four fields, was: " + line);
        }
        String filePath = parts[1];
        String parentDir = new File(filePath).getParent();
        PhotoData data = new PhotoData(parts[0], filePath, Integer.parseInt(parts[2]));
        List<String> tags;
        if (parts.length == 4) {
            tags = Lists.newArrayList(Splitter.on("::").split(parts[3]));
        } else {
            tags = new ArrayList<>();
        }
        data.setUserTags(tags);
        // Mostly, they're organized in subdirs of the root dir, but occasionally I encounter one in the root dir.
        // Then, because these paths are relative, there's no parent.
        if (parentDir != null) data.setImplicitTags(Lists.newArrayList(parentDir));
        return data;
    }

}
