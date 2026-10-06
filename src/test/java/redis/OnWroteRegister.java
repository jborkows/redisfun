package redis;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

final class OnWroteRegister {
    private static final ThreadLocal<OnWroteRegister> INSTANCE = new ThreadLocal<>();
    private final List<OnSocketWrote> onWrote = new ArrayList<>();

    static <T> T with(Supplier<T> runnable, OnSocketWrote onWrote) {
        var entity = INSTANCE.get();
        try {
            if (entity == null) {
                entity = new OnWroteRegister();
            }
            entity.onWrote.add(onWrote);
            INSTANCE.set(entity);
            return runnable.get();
        } finally {
            if (entity != null) {
                entity.onWrote.remove(onWrote);
            }
            INSTANCE.set(entity);
        }
    }

    static void socketWroteData() {
        var entity = INSTANCE.get();
        if (entity != null) {
            entity.onWrote.forEach(OnSocketWrote::onWrote);
        }
    }
}
