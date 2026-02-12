package one.chandan.rubato.ui.dialog;

import android.app.Dialog;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import one.chandan.rubato.R;
import one.chandan.rubato.databinding.DialogJellyfinServerBinding;
import one.chandan.rubato.jellyfin.JellyfinAuthRepository;
import one.chandan.rubato.jellyfin.model.JellyfinAuthResponse;
import one.chandan.rubato.jellyfin.model.JellyfinView;
import one.chandan.rubato.model.JellyfinServer;
import one.chandan.rubato.viewmodel.JellyfinViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public class JellyfinServerDialog extends DialogFragment {
    private DialogJellyfinServerBinding bind;
    private JellyfinViewModel viewModel;
    private final JellyfinAuthRepository authRepository = new JellyfinAuthRepository();

    private String serverName;
    private String username;
    private String password;
    private String server;
    private String selectedLibraryId;
    private String selectedLibraryName;
    private String pendingToken;
    private String pendingUserId;
    private final List<JellyfinView> availableLibraries = new ArrayList<>();
    private boolean librariesLoaded;
    private String loadedServer;
    private String loadedUsername;
    private boolean inLibraryStep = false;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        bind = DialogJellyfinServerBinding.inflate(getLayoutInflater());
        viewModel = new ViewModelProvider(requireActivity()).get(JellyfinViewModel.class);

        return new MaterialAlertDialogBuilder(getActivity())
                .setView(bind.getRoot())
                .setTitle(R.string.jellyfin_server_dialog_title)
                .setNeutralButton(R.string.server_signup_dialog_neutral_button, (dialog, id) -> { })
                .setPositiveButton(R.string.jellyfin_server_dialog_positive_button, (dialog, id) -> { })
                .setNegativeButton(R.string.server_signup_dialog_negative_button, (dialog, id) -> dialog.cancel())
                .create();
    }

    @Override
    public void onStart() {
        super.onStart();
        setServerInfo();
        initLibraryDropdown();
        setButtonAction();
        showCredentialsStep();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        bind = null;
    }

    private void setServerInfo() {
        if (getArguments() != null) {
            JellyfinServer existing = requireArguments().getParcelable("jellyfin_server");
            viewModel.setServerToEdit(existing);
            if (existing != null) {
                bind.serverNameTextView.setText(existing.getName());
                bind.usernameTextView.setText(existing.getUsername());
                bind.passwordTextView.setText("");
                bind.serverTextView.setText(existing.getAddress());
                selectedLibraryId = existing.getLibraryId();
                selectedLibraryName = existing.getLibraryName();
                if (selectedLibraryName != null) {
                    bind.libraryDropdown.setText(selectedLibraryName, false);
                }
                loadLibrariesIfPossible(existing.getAccessToken(), existing.getUserId());
            }
        } else {
            viewModel.setServerToEdit(null);
            prefillServerUrl();
        }
    }

    private void initLibraryDropdown() {
        bind.libraryInputLayout.setEnabled(false);
        bind.libraryDropdown.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < availableLibraries.size()) {
                setSelectedLibrary(availableLibraries.get(position), true);
            }
        });
    }

    private void loadLibrariesIfPossible(String token, String userId) {
        if (token == null || userId == null) {
            return;
        }
        server = normalizeUrl(bind.serverTextView.getText());
        username = Objects.requireNonNull(bind.usernameTextView.getText()).toString().trim();
        if (server == null) {
            setLoading(false);
            return;
        }
        setLoading(true);
        fetchLibraries(server, token, userId);
    }

    private void prefillServerUrl() {
        CharSequence text = bind.serverTextView.getText();
        if (text == null || text.length() == 0) {
            bind.serverTextView.setText("https://");
            bind.serverTextView.setSelection(bind.serverTextView.getText().length());
        }
    }

    private void setButtonAction() {
        androidx.appcompat.app.AlertDialog alertDialog = (androidx.appcompat.app.AlertDialog) Objects.requireNonNull(getDialog());

        alertDialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (inLibraryStep) {
                if (ensureLibrarySelected()) {
                    saveServer(pendingToken, pendingUserId, new JellyfinView(selectedLibraryId, selectedLibraryName, "music"));
                }
                return;
            }

            if (!validateInput()) {
                return;
            }
            if (librariesLoaded && !TextUtils.equals(server, loadedServer)) {
                librariesLoaded = false;
            }
            if (librariesLoaded && !TextUtils.equals(username, loadedUsername)) {
                librariesLoaded = false;
            }
            setLoading(true);
            authenticateAndLoadLibraries();
        });

        alertDialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL)
                .setOnClickListener(v -> Toast.makeText(requireContext(), R.string.server_signup_dialog_action_delete_toast, Toast.LENGTH_SHORT).show());

        alertDialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL)
                .setOnLongClickListener(v -> {
                    viewModel.deleteServer(null);
                    Objects.requireNonNull(getDialog()).dismiss();
                    return true;
                });
    }

    private boolean validateInput() {
        serverName = Objects.requireNonNull(bind.serverNameTextView.getText()).toString().trim();
        username = Objects.requireNonNull(bind.usernameTextView.getText()).toString().trim();
        password = Objects.requireNonNull(bind.passwordTextView.getText()).toString();
        server = normalizeUrl(bind.serverTextView.getText());

        if (TextUtils.isEmpty(serverName)) {
            bind.serverNameTextView.setError(getString(R.string.error_required));
            return false;
        }
        if (TextUtils.isEmpty(username)) {
            bind.usernameTextView.setError(getString(R.string.error_required));
            return false;
        }
        if (TextUtils.isEmpty(password) && viewModel.getServerToEdit() == null) {
            bind.passwordTextView.setError(getString(R.string.error_required));
            return false;
        }
        if (TextUtils.isEmpty(server)) {
            bind.serverTextView.setError(getString(R.string.error_required));
            return false;
        }
        if (!server.matches("^https?://(.*)")) {
            bind.serverTextView.setError(getString(R.string.error_server_prefix));
            return false;
        }

        return true;
    }

    private boolean ensureLibrarySelected() {
        if (TextUtils.isEmpty(selectedLibraryId) || TextUtils.isEmpty(selectedLibraryName)) {
            bind.libraryInputLayout.setError(getString(R.string.jellyfin_library_unselected));
            return false;
        }
        bind.libraryInputLayout.setError(null);
        return true;
    }

    private String normalizeUrl(@Nullable CharSequence text) {
        if (text == null) return null;
        String trimmed = text.toString().trim();
        if (trimmed.isEmpty()) return null;
        if ("https://".equalsIgnoreCase(trimmed) || "http://".equalsIgnoreCase(trimmed)) {
            return null;
        }
        return trimmed;
    }

    private void authenticateAndLoadLibraries() {
        JellyfinServer existing = viewModel.getServerToEdit();
        if ((password == null || password.trim().isEmpty()) && existing != null) {
            fetchLibraries(server, existing.getAccessToken(), existing.getUserId());
            return;
        }

        authRepository.authenticate(server, username, password, new JellyfinAuthRepository.AuthCallback() {
            @Override
            public void onSuccess(JellyfinAuthResponse response) {
                fetchLibraries(server, response.getAccessToken(), response.getUser().getId());
            }

            @Override
            public void onError(Exception exception) {
                showError(exception.getMessage());
            }
        });
    }

    private void fetchLibraries(String serverUrl, String token, String userId) {
        authRepository.fetchLibraries(serverUrl, userId, token, new JellyfinAuthRepository.ViewsCallback() {
            @Override
            public void onSuccess(List<JellyfinView> views) {
                List<JellyfinView> musicViews = new ArrayList<>();
                if (views != null) {
                    for (JellyfinView view : views) {
                        if (view.getCollectionType() != null && view.getCollectionType().toLowerCase(Locale.getDefault()).contains("music")) {
                            musicViews.add(view);
                        }
                    }
                }

                if (musicViews.isEmpty()) {
                    showError(getString(R.string.jellyfin_no_music_library));
                    return;
                }

                server = serverUrl;
                pendingToken = token;
                pendingUserId = userId;
                loadedServer = server;
                loadedUsername = username;
                librariesLoaded = true;
                populateLibraryDropdown(musicViews);
                if (TextUtils.isEmpty(selectedLibraryId) || TextUtils.isEmpty(selectedLibraryName)) {
                    Toast.makeText(requireContext(), R.string.jellyfin_library_unselected, Toast.LENGTH_SHORT).show();
                }
                setLoading(false);
                showLibraryStep();
            }

            @Override
            public void onError(Exception exception) {
                showError(exception.getMessage());
            }
        });
    }

    private void populateLibraryDropdown(List<JellyfinView> musicViews) {
        availableLibraries.clear();
        availableLibraries.addAll(musicViews);
        List<String> names = new ArrayList<>();
        for (JellyfinView view : musicViews) {
            names.add(view.getName());
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, names);
        bind.libraryDropdown.setAdapter(adapter);
        bind.libraryInputLayout.setEnabled(true);

        JellyfinView preferred = findPreferredLibrary(musicViews);
        JellyfinView existing = findExistingSelection(musicViews);
        JellyfinView fallback = musicViews.size() == 1 ? musicViews.get(0) : null;
        JellyfinView toSelect = existing != null ? existing : (preferred != null ? preferred : fallback);
        if (toSelect != null) {
            setSelectedLibrary(toSelect, true);
        }
    }

    private JellyfinView findExistingSelection(List<JellyfinView> views) {
        if (selectedLibraryId == null && selectedLibraryName == null) {
            return null;
        }
        for (JellyfinView view : views) {
            if (selectedLibraryId != null && selectedLibraryId.equals(view.getId())) {
                return view;
            }
            if (selectedLibraryName != null && selectedLibraryName.equalsIgnoreCase(view.getName())) {
                return view;
            }
        }
        return null;
    }

    private JellyfinView findPreferredLibrary(List<JellyfinView> views) {
        String preferredName = getString(R.string.jellyfin_preferred_library_name);
        for (JellyfinView view : views) {
            if (view.getName() != null && view.getName().equalsIgnoreCase(preferredName)) {
                return view;
            }
        }
        return null;
    }

    private void saveServer(String token, String userId, JellyfinView view) {
        if (view == null || TextUtils.isEmpty(view.getId()) || TextUtils.isEmpty(view.getName())) {
            showError(getString(R.string.jellyfin_library_unselected));
            return;
        }
        if (TextUtils.isEmpty(token) || TextUtils.isEmpty(userId)) {
            showError(getString(R.string.jellyfin_auth_failed));
            return;
        }

        String resolvedServerName = resolveServerName();
        String resolvedServer = resolveServerAddress();
        String resolvedUsername = resolveUsername();
        if (TextUtils.isEmpty(resolvedServer)) {
            showError(getString(R.string.error_required));
            return;
        }
        String serverId = viewModel.getServerToEdit() != null
                ? viewModel.getServerToEdit().getId()
                : UUID.randomUUID().toString();

        JellyfinServer jellyfinServer = new JellyfinServer(
                serverId,
                resolvedServerName,
                resolvedServer,
                resolvedUsername,
                token,
                userId,
                view.getId(),
                view.getName(),
                System.currentTimeMillis()
        );

        viewModel.saveServer(jellyfinServer);
        bind.libraryDropdown.setText(view.getName(), false);
        setLoading(false);
        Objects.requireNonNull(getDialog()).dismiss();
    }

    private void setSelectedLibrary(JellyfinView view, boolean updateText) {
        if (view == null) {
            return;
        }
        selectedLibraryId = view.getId();
        selectedLibraryName = view.getName() != null ? view.getName() : "Music";
        if (updateText) {
            bind.libraryDropdown.setText(view.getName(), false);
        }
        bind.libraryInputLayout.setError(null);
    }

    private void showError(String message) {
        setLoading(false);
        Toast.makeText(requireContext(), message == null ? getString(R.string.jellyfin_auth_failed) : message, Toast.LENGTH_SHORT).show();
    }

    private void setLoading(boolean loading) {
        bind.jellyfinProgressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (getDialog() instanceof androidx.appcompat.app.AlertDialog) {
            androidx.appcompat.app.AlertDialog dialog = (androidx.appcompat.app.AlertDialog) getDialog();
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setEnabled(!loading);
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE).setEnabled(!loading);
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL).setEnabled(!loading);
        }
    }

    private void showCredentialsStep() {
        inLibraryStep = false;
        if (bind != null) {
            bind.jellyfinCredentialsContainer.setVisibility(View.VISIBLE);
            bind.jellyfinLibraryContainer.setVisibility(View.GONE);
        }
        if (getDialog() instanceof androidx.appcompat.app.AlertDialog) {
            androidx.appcompat.app.AlertDialog dialog = (androidx.appcompat.app.AlertDialog) getDialog();
            dialog.setTitle(R.string.jellyfin_server_dialog_title);
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                    .setText(R.string.jellyfin_server_dialog_positive_button);
        }
    }

    private void showLibraryStep() {
        inLibraryStep = true;
        if (bind != null) {
            bind.jellyfinCredentialsContainer.setVisibility(View.GONE);
            bind.jellyfinLibraryContainer.setVisibility(View.VISIBLE);
        }
        if (getDialog() instanceof androidx.appcompat.app.AlertDialog) {
            androidx.appcompat.app.AlertDialog dialog = (androidx.appcompat.app.AlertDialog) getDialog();
            dialog.setTitle(R.string.jellyfin_select_library_title);
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                    .setText(R.string.jellyfin_server_dialog_save_button);
        }
    }

    private String resolveServerName() {
        String resolved = serverName;
        if (TextUtils.isEmpty(resolved) && bind != null) {
            CharSequence text = bind.serverNameTextView.getText();
            if (text != null) {
                resolved = text.toString().trim();
            }
        }
        if (TextUtils.isEmpty(resolved) && viewModel != null) {
            JellyfinServer existing = viewModel.getServerToEdit();
            if (existing != null && !TextUtils.isEmpty(existing.getName())) {
                resolved = existing.getName();
            }
        }
        if (TextUtils.isEmpty(resolved)) {
            resolved = deriveServerNameFromAddress();
        }
        return TextUtils.isEmpty(resolved) ? "Jellyfin" : resolved;
    }

    private String resolveServerAddress() {
        String resolved = server;
        if (TextUtils.isEmpty(resolved) && bind != null) {
            resolved = normalizeUrl(bind.serverTextView.getText());
        }
        if (TextUtils.isEmpty(resolved) && viewModel != null) {
            JellyfinServer existing = viewModel.getServerToEdit();
            if (existing != null && !TextUtils.isEmpty(existing.getAddress())) {
                resolved = existing.getAddress();
            }
        }
        return resolved;
    }

    private String resolveUsername() {
        String resolved = username;
        if (TextUtils.isEmpty(resolved) && bind != null) {
            CharSequence text = bind.usernameTextView.getText();
            if (text != null) {
                resolved = text.toString().trim();
            }
        }
        if (TextUtils.isEmpty(resolved) && viewModel != null) {
            JellyfinServer existing = viewModel.getServerToEdit();
            if (existing != null && !TextUtils.isEmpty(existing.getUsername())) {
                resolved = existing.getUsername();
            }
        }
        return TextUtils.isEmpty(resolved) ? "user" : resolved;
    }

    private String deriveServerNameFromAddress() {
        String address = resolveServerAddress();
        if (TextUtils.isEmpty(address)) return null;
        Uri uri = Uri.parse(address);
        String host = uri.getHost();
        if (!TextUtils.isEmpty(host)) {
            return host;
        }
        return address;
    }

}
