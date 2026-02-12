package one.chandan.rubato.util;

public final class TestRunUtil {
    private TestRunUtil() {
    }

    public static boolean isInstrumentationTest() {
        try {
            Class<?> registry = Class.forName("androidx.test.platform.app.InstrumentationRegistry");
            Object instrumentation = registry.getMethod("getInstrumentation").invoke(null);
            return instrumentation != null;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
