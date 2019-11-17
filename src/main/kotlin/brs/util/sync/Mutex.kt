package brs.util.sync

inline class Mutex(val lock: Any = Any()) {
    inline fun <T> withLock(action: () -> T): T {
        return synchronized(lock, action)
    }
}