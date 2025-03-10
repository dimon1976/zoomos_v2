package by.zoomos_v2.util;

public class HeapSize {

    public static String getHeapSizeAsString() {

        // Get current size of heap in bytes
        long heapSize = Runtime.getRuntime().totalMemory();

        // Get maximum size of heap in bytes. The heap cannot grow beyond this
        // size.// Any attempt will result in an OutOfMemoryException.
        long heapMaxSize = Runtime.getRuntime().maxMemory();

        // Get amount of free memory within the heap in bytes. This size will
        // increase // after garbage collection and decrease as new objects are
        // created.
        // long heapFreeSize = Runtime.getRuntime().freeMemory();

        return "heapSize = " + (heapSize / 1024 / 1024) + " / " + (heapMaxSize / 1024 / 1024) + " ("
                + (heapSize * 100 / heapMaxSize) + "%)";

        // + ", heapFreeSize = " + (heapFreeSize / 1024 / 1024)
    }
}
