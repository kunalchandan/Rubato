package one.chandan.rubato.ui.state;

import java.util.List;

import one.chandan.rubato.model.DownloadStack;
import one.chandan.rubato.subsonic.models.Child;

public class DownloadUiState {
    public final boolean loading;
    public final boolean offline;
    public final List<Child> downloads;
    public final List<DownloadStack> stack;

    public DownloadUiState(
            boolean loading,
            boolean offline,
            List<Child> downloads,
            List<DownloadStack> stack
    ) {
        this.loading = loading;
        this.offline = offline;
        this.downloads = downloads;
        this.stack = stack;
    }
}
