package one.chandan.rubato;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import android.os.Bundle;

import androidx.fragment.app.FragmentFactory;
import androidx.fragment.app.testing.FragmentScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import one.chandan.rubato.helper.ThemeHelper;
import one.chandan.rubato.ui.dialog.ThemeSelectorDialog;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class ThemeSelectorDialogTest {
    @Test
    public void showsThemeSelectorDialog() {
        TestDeviceUtil.assumeNotAutomotive();
        Bundle args = new Bundle();
        args.putString("theme_selector_selected", ThemeHelper.DEFAULT_MODE);

        FragmentScenario.launchInContainer(ThemeSelectorDialog.class, args, R.style.AppTheme, (FragmentFactory) null);

        onView(withId(R.id.theme_selector_container)).check(matches(isDisplayed()));
    }
}
