package rds.photogallery

/**
 * Represents the state of a {@link PhotoFrame} that persists across restarts of the app so that it starts up in the
 * same configuration as you last left it.
 */
class PersistentFrameState {
    boolean fullScreen
    FrameConfiguration normalConfig
    FrameConfiguration fullScreenConfig
}
