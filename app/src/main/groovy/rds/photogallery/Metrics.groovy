package rds.photogallery

import java.util.concurrent.Callable

/**
 * Records metrics about the app. To start with, it's a simple, dumb implementation that just writes stuff to stdout.
 * Eventually, it can write to Graphite or something.
 */
class Metrics {
    static void time(String desc, Runnable timedThing) {
        def start = System.currentTimeMillis()
        timedThing.run()
        def elapsed = System.currentTimeMillis() - start
        println "TIME: $desc - $elapsed ms"
    }

    /**
     * Surely there's a good way to combine these two. Groovy wants to make closures sent here into Runnable unless you
     * "as Callable" them.
     */
    static <T> T timeAndReturn(String desc, Callable<T> timedThing) {
        def start = System.currentTimeMillis()
        def result = timedThing.call()
        def elapsed = System.currentTimeMillis() - start
        println "TIME: $desc - $elapsed ms"
        return result
    }
}
