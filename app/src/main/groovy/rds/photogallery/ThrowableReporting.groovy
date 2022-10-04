package rds.photogallery

/**
 * Wrappers for use in submitting work to an ExecutorService, which doesn't report Throwables, only Exceptions.
 */
class ThrowableReporting {
    abstract static class Runnable implements java.lang.Runnable {
        @Override
        final void run() {
            try {
                doRun()
            } catch (Throwable t) {
                t.printStackTrace()
                throw t
            }
        }

        abstract void doRun() throws Throwable;
    }

    abstract static class Callable<T> implements java.util.concurrent.Callable<T> {
        @Override
        final T call() {
            try {
                return doCall()
            } catch (Throwable t) {
                t.printStackTrace()
                throw t
            }
        }

        abstract T doCall() throws Throwable;
    }
}
