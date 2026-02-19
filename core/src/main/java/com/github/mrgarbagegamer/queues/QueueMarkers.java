package com.github.mrgarbagegamer.queues;

// TODO: Add Javadocs for the class and its interfaces
/**
 * Marker interfaces that tag queue wrappers with their access mode and boundedness. Used by
 * {@link QueueUtils} for validation instead of exhaustive {@code instanceof} chains.
 */
public final class QueueMarkers {

    private QueueMarkers() {
        throw new UnsupportedOperationException("This class cannot be instantiated.");
    }

    /**
     * Access mode markers. A queue implements exactly one of these.
     */
    public static final class AccessMode {

        private AccessMode() {
            throw new UnsupportedOperationException("This class cannot be instantiated.");
        }

        /** Multi-producer, multi-consumer. */
        public interface MPMC {}

        /** Multi-producer, single-consumer. */
        public interface MPSC {}

        /** Single-producer, multi-consumer. */
        public interface SPMC {}

        /** Single-producer, single-consumer. */
        public interface SPSC {}
    }

    /**
     * Boundedness markers. A queue implements exactly one of these.
     */
    public static final class Boundedness {

        private Boundedness() {
            throw new UnsupportedOperationException("This class cannot be instantiated.");
        }

        /** Queue has a fixed capacity. */
        public interface Bounded {
            int capacity();
        }

        /** Queue grows as needed. */
        public interface Unbounded {}
    }
}
