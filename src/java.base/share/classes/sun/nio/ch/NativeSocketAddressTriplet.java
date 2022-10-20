package sun.nio.ch;

import jdk.internal.misc.Unsafe;

import java.lang.foreign.MemorySegment;

import static jdk.internal.include.support.ch.NetUtils.SOCKET_ADDRESS;

/**
 * Represents a triplet of native socket addresses; source, cached and target.
 */
sealed public interface NativeSocketAddressTriplet {

    /**
     * {@return the source native socket address}.
     */
    NativeSocketAddress source();

    /**
     * {@return the cached native socket address}.
     */
    NativeSocketAddress cached();

    /**
     * {@return the target native socket address}.
     */
    NativeSocketAddress target();

    /**
     * Releases all native memory associated to the triplets of native socket addresses.
     * <p>
     * This must only be done via a Cleaner whereby is it guaranteed that native memory is not referenced.
     * Calling this method directly and then accessing any of the native socket addresses is <em>unsafe</em> and
     * results in unspecified behaviour including JVM crashes.
     */
    void freeAll();

    /**
     * {@return a new native socket address triplet}.
     */
    static NativeSocketAddressTriplet create() {
        return new Impl();
    }

    final class Impl implements NativeSocketAddressTriplet {

        private static final Unsafe UNSAFE = Unsafe.getUnsafe();

        private static final long SEGMENT_SIZE = SOCKET_ADDRESS.byteSize();

        private final long baseAddress;
        private final NativeSocketAddress source;
        private final NativeSocketAddress cached;
        private final NativeSocketAddress target;

        public Impl() {
            // Allocate the backing native memory in a single chunk
            // This increases locality and makes a triple allocation either succeed or fail atomically
            baseAddress = UNSAFE.allocateMemory(SEGMENT_SIZE * 3);
            source = new NativeSocketAddress.Impl(ofShard(0));
            cached = new NativeSocketAddress.Impl(ofShard(1));
            target = new NativeSocketAddress.Impl(ofShard(2));
        }

        @Override public NativeSocketAddress source() { return source; }

        @Override public NativeSocketAddress cached() { return cached; }

        @Override public NativeSocketAddress target() { return target; }
        @Override public void freeAll() { UNSAFE.freeMemory(baseAddress); }

        private MemorySegment ofShard(int shard) {
            return MemorySegment.ofAddress(baseAddress + SEGMENT_SIZE * (long) shard, SEGMENT_SIZE)
                    .fill((byte) 0);
        }
    }

}
