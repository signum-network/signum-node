package brs.util;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/*
 * EventBus is a simple event bus implementation that allows for subscribing to and publishing events.
 * It uses a thread-safe map to store listeners for different event types.
 * Listeners can be added or removed, and events can be published to all subscribed listeners.
 * This implementation is suitable for use in a multi-threaded environment.
 * * @param <T> The type of event to be handled by the listeners.
 * * @param <E> The type of event bus that can handle events of type T.
 */
public class EventBus {

    private Map<Class<?>, List<Consumer<Object>>> listeners = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <T> void subscribe(Class<T> eventType, Consumer<T> listener) {
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                .add((Consumer<Object>) listener);
    }

    public <T> void unsubscribe(Class<T> eventType, Consumer<T> listener) {
        List<Consumer<Object>> eventListeners = listeners.get(eventType);
        if (eventListeners != null) {
            eventListeners.remove(listener);
        }
    }

    public <T> void publish(T event) {
        List<Consumer<Object>> eventListeners = listeners.get(event.getClass());
        if (eventListeners != null) {
            // Fontos: A GUI listenereket az Event Dispatch Thread-en (EDT) kell futtatni!
            // Egy fejlettebb implementáció ezt kezelhetné.
            for (Consumer<Object> listener : eventListeners) {
                listener.accept(event);
            }
        }
    }
}
