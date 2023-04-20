package rds.photogallery;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Loads databases. I moved this code into its own class thinking that I can probably abstract loading local data
 * from remote data this way, and produce an appropriate database implementation that can work with no local data
 * or files at all. Time will tell.
 */
public class DataSourceLoader {
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
                    parser = DataSourceLoader::parseLineOriginal;
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
