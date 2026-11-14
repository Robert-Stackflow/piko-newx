package app.morphe.extension.newx.settings;

import android.content.Intent;
import android.app.Fragment;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import app.morphe.extension.crimera.settings.PikoSettingsActivity;
import app.morphe.extension.crimera.settings.PikoSettingsFragment;
import app.morphe.extension.crimera.theme.PikoTheme;
import app.morphe.extension.newx.featureswitches.FeatureSwitchImportExport;
import app.morphe.extension.newx.mediatools.HeaderToolsRuntime;
import app.morphe.extension.newx.misc.UpdateFont;

/**
 * The activity the patch declares in the manifest. Layout, navigation and theming live in the shared
 * {@link PikoSettingsActivity}; NewX supplies its configuration through {@link NewXSettingsHost} and
 * only handles the results of the NewX features launched from its screens.
 */
public final class NewXSettingsActivity extends PikoSettingsActivity {
    private View pageAction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) return;
        Fragment screen = HeaderToolsRuntime.initialScreen(getIntent());
        if (screen instanceof PikoSettingsFragment) return;
        getFragmentManager().beginTransaction().replace(0x00f00001, screen).commit();
    }

    public void setPageAction(View action) {
        ViewGroup content = findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0) return;
        View root = content.getChildAt(0);
        if (!(root instanceof LinearLayout layout) || layout.getChildCount() == 0) return;
        View header = layout.getChildAt(0);
        if (!(header instanceof LinearLayout toolbar)) return;
        if (pageAction != null) toolbar.removeView(pageAction);
        pageAction = action;
        if (action != null) toolbar.addView(action, new LinearLayout.LayoutParams(
                PikoTheme.dpToPx(this, 48f), PikoTheme.dpToPx(this, 48f)));
    }

    @Override
    protected void onUnhandledActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (FeatureSwitchImportExport.handleActivityResult(this, requestCode, resultCode, data)) {
            return;
        }
        UpdateFont.handleActivityResult(this, requestCode, resultCode, data);
    }
}
