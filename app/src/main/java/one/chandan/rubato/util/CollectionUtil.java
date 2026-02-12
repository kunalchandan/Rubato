package one.chandan.rubato.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CollectionUtil {
    private CollectionUtil() {
    }

    public static <T> List<T> emptyIfNull(List<T> items) {
        return items == null ? Collections.emptyList() : items;
    }

    public static <T> ArrayList<T> arrayListOrEmpty(List<? extends T> items) {
        return items == null ? new ArrayList<>() : new ArrayList<>(items);
    }
}
