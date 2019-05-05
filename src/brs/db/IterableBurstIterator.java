package brs.db;

import java.util.Iterator;

/**
 * TODO this class is a hack
 * @param <T>
 */
public class IterableBurstIterator<T> implements BurstIterator<T> {

    private final Iterator<T> iterator;

    public IterableBurstIterator(Iterable<T> iterable) {
        this.iterator = iterable.iterator();
    }

    @Override
    public boolean hasNext() {
        return iterator.hasNext();
    }

    @Override
    public T next() {
        return iterator.next();
    }

    @Override
    public void remove() {
        iterator.remove();
    }

    @Override
    public void close() {
    }
}
