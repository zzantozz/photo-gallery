package rds.photogallery

/**
 * Records metrics about the app. To start with, it's a simple, dumb implementation that just writes stuff to stdout.
 * Eventually, it can write to Graphite or something.
 */
class Metrics {
    static <T> T time(String desc, Closure<T> timedThing) {
        def start = System.currentTimeMillis()
        def result = timedThing()
        def elapsed = System.currentTimeMillis() - start
        println "TIME: $desc - $elapsed ms"
        return result
    }
}
