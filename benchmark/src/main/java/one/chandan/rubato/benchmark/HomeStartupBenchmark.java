package one.chandan.rubato.benchmark;

import androidx.benchmark.macro.FrameTimingMetric;
import androidx.benchmark.macro.CompilationMode;
import androidx.benchmark.macro.MacrobenchmarkScope;
import androidx.benchmark.macro.StartupMode;
import androidx.benchmark.macro.StartupTimingMetric;
import androidx.benchmark.macro.junit4.MacrobenchmarkRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.Until;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class HomeStartupBenchmark {
    private static final String PACKAGE_NAME = "one.chandan.rubato";

    @Rule
    public MacrobenchmarkRule benchmarkRule = new MacrobenchmarkRule();

    @Test
    public void startupAndRenderHome() {
        benchmarkRule.measureRepeated(
                PACKAGE_NAME,
                Arrays.asList(new StartupTimingMetric(), new FrameTimingMetric()),
                new CompilationMode.None(),
                StartupMode.COLD,
                5,
                scope -> {
                    scope.pressHome();
                    return null;
                },
                scope -> {
                    launchActivity(scope);
                    scope.getDevice().waitForIdle();
                    return null;
                }
        );
    }

    @Test
    public void scrollHome() {
        benchmarkRule.measureRepeated(
                PACKAGE_NAME,
                Arrays.asList(new FrameTimingMetric()),
                new CompilationMode.None(),
                StartupMode.WARM,
                5,
                scope -> {
                    scope.pressHome();
                    return null;
                },
                scope -> {
                    launchActivity(scope);
                    if (!scope.getDevice().hasObject(By.res(PACKAGE_NAME, "bottom_navigation"))) {
                        return null;
                    }

                    var selector = By.res(PACKAGE_NAME, "fragment_home_nested_scroll_view");
                    scope.getDevice().wait(Until.hasObject(selector), 5_000);
                    var scrollView = scope.getDevice().findObject(selector);
                    if (scrollView == null) return null;
                    scrollView.fling(Direction.DOWN);
                    scrollView.fling(Direction.UP);
                    return null;
                }
        );
    }

    @Test
    public void scrollLibrary() {
        benchmarkRule.measureRepeated(
                PACKAGE_NAME,
                Arrays.asList(new FrameTimingMetric()),
                new CompilationMode.None(),
                StartupMode.WARM,
                5,
                scope -> {
                    scope.pressHome();
                    return null;
                },
                scope -> {
                    launchActivity(scope);
                    if (!scope.getDevice().hasObject(By.res(PACKAGE_NAME, "bottom_navigation"))) {
                        return null;
                    }

                    var libraryTab = scope.getDevice().findObject(By.res(PACKAGE_NAME, "libraryFragment"));
                    if (libraryTab != null) {
                        libraryTab.click();
                        scope.getDevice().waitForIdle();
                    }

                    var selector = By.res(PACKAGE_NAME, "fragment_library_nested_scroll_view");
                    scope.getDevice().wait(Until.hasObject(selector), 5_000);
                    var scrollView = scope.getDevice().findObject(selector);
                    if (scrollView == null) return null;
                    scrollView.fling(Direction.DOWN);
                    scrollView.fling(Direction.UP);
                    return null;
                }
        );
    }

    @Test
    public void scrollDownloaded() {
        benchmarkRule.measureRepeated(
                PACKAGE_NAME,
                Arrays.asList(new FrameTimingMetric()),
                new CompilationMode.None(),
                StartupMode.WARM,
                5,
                scope -> {
                    scope.pressHome();
                    return null;
                },
                scope -> {
                    launchActivity(scope);
                    if (!scope.getDevice().hasObject(By.res(PACKAGE_NAME, "bottom_navigation"))) {
                        return null;
                    }

                    var downloadsTab = scope.getDevice().findObject(By.res(PACKAGE_NAME, "downloadFragment"));
                    if (downloadsTab != null) {
                        downloadsTab.click();
                        scope.getDevice().waitForIdle();
                    }

                    var selector = By.res(PACKAGE_NAME, "downloaded_recycler_view");
                    scope.getDevice().wait(Until.hasObject(selector), 5_000);
                    var list = scope.getDevice().findObject(selector);
                    if (list == null) return null;
                    list.fling(Direction.DOWN);
                    list.fling(Direction.UP);
                    return null;
                }
        );
    }

    private void launchActivity(MacrobenchmarkScope scope) {
        try {
            scope.startActivityAndWait();
        } catch (IllegalStateException e) {
            try {
                scope.getDevice().executeShellCommand(
                        "am start -W -n " + PACKAGE_NAME + "/.ui.activity.MainActivity"
                );
                scope.getDevice().wait(Until.hasObject(By.pkg(PACKAGE_NAME)), 10_000);
            } catch (Exception ignored) {
            }
        }
    }
}
