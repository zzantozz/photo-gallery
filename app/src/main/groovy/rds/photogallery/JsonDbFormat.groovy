package rds.photogallery

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper

class JsonDbFormat {
    static PhotoData parse(String line) {
        def parsed = new JsonSlurper().parseText(line)
        new PhotoData(parsed as Map)
    }

    static String unparse(PhotoData data) {
        def json = new JsonBuilder()
        json path: data.path, rating: data.rating, tags: data.userTags, hash: data.photoHash
        json.toString()
    }
}
