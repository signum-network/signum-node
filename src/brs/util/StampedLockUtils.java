package brs.util;

import java.util.concurrent.locks.StampedLock;
import java.util.function.Supplier;

public class StampedLockUtils {
    public static <T> T stampedLockRead(StampedLock lock, Supplier<T> supplier) {
        long stamp = lock.tryOptimisticRead();
        T retVal = supplier.get();
        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                retVal = supplier.get();
            } finally {
                lock.unlockRead(stamp);
            }
        }
        return retVal;
    }
}
