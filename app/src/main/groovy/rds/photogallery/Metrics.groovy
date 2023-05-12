package rds.photogallery

import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.config.NamingConvention
import io.micrometer.core.instrument.util.HierarchicalNameMapper
import io.micrometer.graphite.GraphiteConfig
import io.micrometer.graphite.GraphiteMeterRegistry
import io.micrometer.graphite.GraphiteProtocol
import io.micrometer.opentsdb.OpenTSDBConfig
import io.micrometer.opentsdb.OpenTSDBMeterRegistry

import java.nio.file.Paths
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

/**
 * Records metrics about the app. To start with, it's a simple, dumb implementation that just writes stuff to stdout.
 * Eventually, it can write to Graphite or something.
 */
class Metrics {
    private final MeterRegistry registry

    Metrics() {
        String registrySetting = App.settings().asString(Settings.Setting.METER_REGISTRY)
        if (registrySetting == "OpenTSDB") {
            OpenTSDBConfig openTsdbConfig = new OpenTSDBConfig() {
                @Override
                String get(String key) {
                    return null
                }

                @Override
                Duration step() {
                    return Duration.of(10, ChronoUnit.SECONDS)
                }
            }
            registry = new OpenTSDBMeterRegistry(openTsdbConfig, Clock.SYSTEM)
        } else if (registrySetting == "Graphite") {
            GraphiteConfig graphiteConfig = new GraphiteConfig() {
                @Override
                String get(String k) {
                    return null // accept the rest of the defaults
                }

                @Override
                boolean graphiteTagsEnabled() {
                    return false
                }

                @Override
                GraphiteProtocol protocol() {
                    return GraphiteProtocol.PLAINTEXT
                }

                @Override
                String host() {
                    return App.settings().asString(Settings.Setting.GRAPHITE_HOST)
                }

                @Override
                int port() {
                    return 2003
                }

                @Override
                Duration step() {
                    // Defaults to one minute, which puts big gaps in grafana charts, which show 10-second intervals at
                    // close resolution.
                    return Duration.of(10, ChronoUnit.SECONDS)
                }
            }
            registry = new GraphiteMeterRegistry(graphiteConfig, Clock.SYSTEM,
                    (id, convention) -> "photoGallery." + HierarchicalNameMapper.DEFAULT.toHierarchicalName(id, convention))
            registry.config()
                    .namingConvention(NamingConvention.identity)
        } else {
            throw new IllegalStateException("The " + Settings.Setting.METER_REGISTRY.name() + " setting must be either "
                    + " 'OpenTSDB' or 'Graphite'.")
        }
    }

    void time(String desc, Runnable timedThing) {
        registry.timer(desc.replaceAll(' ', '_')).record(timedThing)
    }

    /**
     * Surely there's a good way to combine these two. Groovy wants to make closures sent here into Runnable unless you
     * "as Callable" them.
     */
    def <T> T timeAndReturn(String desc, Callable<T> timedThing) {
        registry.timer(desc.replaceAll(' ', '_')).recordCallable(timedThing)
    }

    void photoDeliveryTime(long time) {
        registry.timer('total_photo_delivery_time').record(time, TimeUnit.MILLISECONDS)
    }

    void loadFailure() {
        registry.counter('load_photo_failures').increment()
    }

    void photoShown(PhotoData data) {
        registry.counter("photo_shown.rating.${data.rating}").increment()
        def path = Paths.get(data.relativePath)
        final String parent
        if (path.nameCount == 1) {
            parent = 'root'
        } else {
            parent = path.getName(path.nameCount - 2)
        }
        registry.counter("photo_shown.dir.$parent").increment()
    }
}
