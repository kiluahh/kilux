package com.termux.app.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;
import com.termux.app.filemanager.FileItem;
import com.termux.app.filemanager.FileListAdapter;
import com.termux.app.filemanager.FileOperationHelper;
import com.termux.shared.shell.command.ExecutionCommand.Runner;
import com.termux.shared.termux.TermuxConstants;

import android.content.res.Configuration;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Stack;

public class FileManagerActivity extends AppCompatActivity {

    private RecyclerView fileList;
    private LinearLayout breadcrumbContainer;
    private FileListAdapter adapter;
    private File currentDir;
    private final Stack<File> backStack = new Stack<>();
    private List<File> clipBoard;
    private boolean clipBoardIsCut;
    private SearchView searchView;
    private String searchQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_manager);

        Toolbar toolbar = findViewById(com.termux.shared.R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            toolbar.setNavigationOnClickListener(v -> onBackPressed());
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.setDisplayHomeAsUpEnabled(true);
            }
        }

        breadcrumbContainer = findViewById(R.id.breadcrumb_container);

        fileList = findViewById(R.id.file_list);
        fileList.setLayoutManager(new LinearLayoutManager(this));

        adapter = new FileListAdapter(this);
        adapter.setOnItemClickListener(this::onItemClick);
        adapter.setOnItemLongClickListener(this::onItemLongClick);
        fileList.setAdapter(adapter);

        File root = TermuxConstants.TERMUX_HOME_DIR;
        if (!root.exists() || !root.isDirectory()) {
            Toast.makeText(this, "Home directory not accessible", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        navigateTo(root);
        setTitle(getString(R.string.title_file_manager));
    }

    private void navigateTo(File dir) {
        if (currentDir != null && !currentDir.equals(dir)) {
            backStack.push(currentDir);
        }
        currentDir = dir;
        updateBreadcrumb();
        loadDirectory();
    }

    private void navigateUp() {
        if (!backStack.isEmpty()) {
            currentDir = backStack.pop();
            updateBreadcrumb();
            loadDirectory();
        } else {
            finish();
        }
    }

    private void loadDirectory() {
        File[] files = currentDir.listFiles();
        List<FileItem> items = new ArrayList<>();

        if (files != null) {
            for (File f : files) {
                FileItem item = new FileItem(f);
                if (searchQuery.isEmpty()
                        || item.name.toLowerCase().contains(searchQuery.toLowerCase())) {
                    items.add(item);
                }
            }
        }

        adapter.setItems(items);
    }

    private void updateBreadcrumb() {
        breadcrumbContainer.removeAllViews();

        List<File> path = new ArrayList<>();
        File f = currentDir;
        while (f != null) {
            path.add(f);
            f = f.getParentFile();
        }
        Collections.reverse(path);

        for (int i = 0; i < path.size(); i++) {
            final File dir = path.get(i);
            boolean isLast = (i == path.size() - 1);

            if (i > 0) {
                TextView sep = new TextView(this);
                sep.setText(" / ");
                sep.setTextColor(breadcrumbDimColor());
                sep.setTextSize(12);
                breadcrumbContainer.addView(sep);
            }

            TextView seg = new TextView(this);
            seg.setText(dir.getName().isEmpty() ? "/" : dir.getName());
            seg.setTextSize(12);
            if (isLast) {
                seg.setTextColor(breadcrumbCurrentColor());
            } else {
                seg.setTextColor(breadcrumbDimColor());
                seg.setOnClickListener(v -> {
                    while (!backStack.isEmpty()
                            && !isParentOf(dir, backStack.peek())) {
                        backStack.pop();
                    }
                    navigateTo(dir);
                });
            }
            breadcrumbContainer.addView(seg);
        }
    }

    private boolean isNightMode() {
        int night = getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES;
    }

    private int breadcrumbDimColor() {
        return isNightMode() ? 0xB3FFFFFF : 0xFF757575;
    }

    private int breadcrumbCurrentColor() {
        return isNightMode() ? 0xFFFFFFFF : 0xFF000000;
    }

    private boolean isParentOf(File potentialParent, File child) {
        File p = child.getParentFile();
        while (p != null) {
            if (p.equals(potentialParent)) return true;
            p = p.getParentFile();
        }
        return false;
    }

    private void onItemClick(FileItem item, int position) {
        if (adapter.isSelectionMode()) {
            return;
        }
        if (item.isDirectory) {
            navigateTo(item.file);
        } else {
            showContextMenu(item);
        }
    }

    private void onItemLongClick(FileItem item, int position) {
        invalidateOptionsMenu();
    }

    private void openFile(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, getMimeType(file.getName()));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            Intent chooser = Intent.createChooser(intent, "Open with");
            startActivity(chooser);
        } catch (IllegalArgumentException e) {
            android.util.Log.e("FileManager", "FileProvider error for: " + file.getAbsolutePath(), e);
            Toast.makeText(this, "Cannot open: file is outside accessible storage",
                    Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            android.util.Log.e("FileManager", "Error opening file: " + file.getAbsolutePath(), e);
            Toast.makeText(this, "Cannot open file", Toast.LENGTH_SHORT).show();
        }
    }

    private String getMimeType(String filename) {
        String ext = filename.contains(".") ?
                filename.substring(filename.lastIndexOf(".")).toLowerCase() : "";
        switch (ext) {
            case ".txt": case ".md": case ".sh": case ".py": case ".js": case ".java":
            case ".xml": case ".json": case ".yml": case ".yaml": case ".properties":
                return "text/plain";
            case ".html": case ".htm": return "text/html";
            case ".pdf": return "application/pdf";
            case ".png": return "image/png";
            case ".jpg": case ".jpeg": return "image/jpeg";
            case ".gif": return "image/gif";
            case ".mp4": return "video/mp4";
            case ".mp3": return "audio/mpeg";
            case ".zip": return "application/zip";
            case ".apk": return "application/vnd.android.package-archive";
            default: return "*/*";
        }
    }

    // Menu

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.file_manager_menu, menu);

        MenuItem searchItem = menu.findItem(R.id.menu_search);
        searchView = (SearchView) searchItem.getActionView();
        searchView.setQueryHint(getString(R.string.action_search));
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchQuery = query;
                loadDirectory();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                searchQuery = newText;
                loadDirectory();
                return true;
            }
        });

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean selecting = adapter.isSelectionMode();

        setMenuVisible(menu, R.id.menu_delete, selecting);
        setMenuVisible(menu, R.id.menu_copy, selecting);
        setMenuVisible(menu, R.id.menu_cut, selecting);

        MenuItem selectAll = menu.findItem(R.id.menu_select_all);
        if (selectAll != null) {
            selectAll.setVisible(selecting);
            selectAll.setTitle(adapter.getSelectedCount() == adapter.getItemCount()
                    ? "Deselect all" : getString(R.string.action_select_all));
        }

        setMenuVisible(menu, R.id.menu_open_in_terminal, !selecting);
        setMenuVisible(menu, R.id.menu_new_folder, !selecting);
        setMenuVisible(menu, R.id.menu_new_file, !selecting);
        setMenuVisible(menu, R.id.menu_paste,
                !selecting && clipBoard != null && !clipBoard.isEmpty());

        MenuItem searchItem = menu.findItem(R.id.menu_search);
        if (searchItem != null && searchView != null && searchView.isIconified()) {
            searchItem.setVisible(!selecting);
        }

        return super.onPrepareOptionsMenu(menu);
    }

    private void setMenuVisible(Menu menu, int id, boolean visible) {
        MenuItem item = menu.findItem(id);
        if (item != null) item.setVisible(visible);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menu_open_in_terminal) {
            openInTerminal();
            return true;
        } else if (id == R.id.menu_new_folder) {
            showNewFolderDialog();
            return true;
        } else if (id == R.id.menu_new_file) {
            showNewFileDialog();
            return true;
        } else if (id == R.id.menu_paste) {
            pasteFiles();
            return true;
        } else if (id == R.id.menu_delete) {
            showDeleteDialog(adapter.getSelectedItems());
            return true;
        } else if (id == R.id.menu_copy) {
            clipBoard = getSelectedFileList();
            clipBoardIsCut = false;
            Toast.makeText(this, "Copied " + adapter.getSelectedCount() + " items",
                    Toast.LENGTH_SHORT).show();
            adapter.clearSelection();
            invalidateOptionsMenu();
            return true;
        } else if (id == R.id.menu_cut) {
            clipBoard = getSelectedFileList();
            clipBoardIsCut = true;
            Toast.makeText(this, "Cut " + adapter.getSelectedCount() + " items",
                    Toast.LENGTH_SHORT).show();
            adapter.clearSelection();
            invalidateOptionsMenu();
            return true;
        } else if (id == R.id.menu_select_all) {
            if (adapter.getSelectedCount() == adapter.getItemCount()) {
                adapter.clearSelection();
            } else {
                adapter.selectAll();
            }
            invalidateOptionsMenu();
            return true;
        } else if (id == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void openInTerminal() {
        Intent intent = new Intent(TermuxConstants.TERMUX_APP.TERMUX_SERVICE.ACTION_SERVICE_EXECUTE);
        intent.setClassName(TermuxConstants.TERMUX_PACKAGE_NAME,
                TermuxConstants.TERMUX_APP.TERMUX_SERVICE_NAME);
        intent.putExtra(TermuxConstants.TERMUX_APP.TERMUX_SERVICE.EXTRA_WORKDIR,
                currentDir.getAbsolutePath());
        intent.putExtra(TermuxConstants.TERMUX_APP.TERMUX_SERVICE.EXTRA_RUNNER,
                Runner.TERMINAL_SESSION.getName());
        startService(intent);
    }

    private List<File> getSelectedFileList() {
        List<File> files = new ArrayList<>();
        for (FileItem item : adapter.getSelectedItems()) {
            files.add(item.file);
        }
        return files;
    }

    private void pasteFiles() {
        if (clipBoard == null || clipBoard.isEmpty()) return;
        if (clipBoardIsCut) {
            FileOperationHelper.moveFiles(this, clipBoard, currentDir);
            clipBoard = null;
            Toast.makeText(this, "Moved", Toast.LENGTH_SHORT).show();
        } else {
            FileOperationHelper.copyFiles(this, clipBoard, currentDir);
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
        }
        loadDirectory();
        invalidateOptionsMenu();
    }

    // Dialogs

    private void showNewFolderDialog() {
        EditText input = new EditText(this);
        input.setHint(R.string.folder_name_hint);
        new AlertDialog.Builder(this)
                .setTitle(R.string.action_new_folder)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        File folder = FileOperationHelper.createFolder(currentDir, name);
                        if (folder != null) {
                            Toast.makeText(this, R.string.toast_folder_created,
                                    Toast.LENGTH_SHORT).show();
                            loadDirectory();
                        } else {
                            Toast.makeText(this, "Failed to create folder",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showNewFileDialog() {
        EditText input = new EditText(this);
        input.setHint(R.string.folder_name_hint);
        new AlertDialog.Builder(this)
                .setTitle(R.string.action_new_file)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        File file = new File(currentDir, name);
                        try {
                            if (file.createNewFile()) {
                                Toast.makeText(this, "File created",
                                        Toast.LENGTH_SHORT).show();
                                loadDirectory();
                            } else {
                                Toast.makeText(this, "Failed to create file",
                                        Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception e) {
                            Toast.makeText(this, "Error: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showRenameDialog(FileItem item) {
        EditText input = new EditText(this);
        input.setText(item.name);
        input.setHint(R.string.rename_hint);
        input.setSelection(item.name.length());
        new AlertDialog.Builder(this)
                .setTitle(R.string.action_rename)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty() && !newName.equals(item.name)) {
                        File dest = new File(item.file.getParent(), newName);
                        if (dest.exists()) {
                            new AlertDialog.Builder(this)
                                    .setTitle("File exists")
                                    .setMessage("Overwrite?")
                                    .setPositiveButton(android.R.string.ok, (d2, w2) -> {
                                        FileOperationHelper.deleteRecursive(dest);
                                        if (FileOperationHelper.rename(item.file, newName)) {
                                            Toast.makeText(this, R.string.toast_renamed,
                                                    Toast.LENGTH_SHORT).show();
                                            loadDirectory();
                                        }
                                    })
                                    .setNegativeButton(android.R.string.cancel, null)
                                    .show();
                        } else {
                            if (FileOperationHelper.rename(item.file, newName)) {
                                Toast.makeText(this, R.string.toast_renamed,
                                        Toast.LENGTH_SHORT).show();
                                loadDirectory();
                            }
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showDeleteDialog(List<FileItem> items) {
        String message;
        if (items.size() == 1) {
            message = getString(R.string.confirm_delete_message, items.get(0).name);
        } else {
            message = getString(R.string.confirm_delete_multiple, items.size());
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_delete_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    for (FileItem item : items) {
                        FileOperationHelper.deleteRecursive(item.file);
                    }
                    Toast.makeText(this, R.string.toast_deleted,
                            Toast.LENGTH_SHORT).show();
                    adapter.clearSelection();
                    loadDirectory();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showContextMenu(FileItem item) {
        String[] options = {"Open", "Edit", "Rename", "Delete", "Copy", "Cut"};
        new AlertDialog.Builder(this)
                .setTitle(item.name)
                .setItems(options, (d, which) -> {
                    switch (which) {
                        case 0:
                            openFile(item.file);
                            break;
                        case 1:
                            Intent intent = new Intent(FileManagerActivity.this,
                                    EditorActivity.class);
                            intent.putExtra("file_path", item.file.getAbsolutePath());
                            startActivity(intent);
                            break;
                        case 2:
                            showRenameDialog(item);
                            break;
                        case 3:
                            showDeleteDialog(Arrays.asList(item));
                            break;
                        case 4:
                            clipBoard = new ArrayList<>();
                            clipBoard.add(item.file);
                            clipBoardIsCut = false;
                            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
                            break;
                        case 5:
                            clipBoard = new ArrayList<>();
                            clipBoard.add(item.file);
                            clipBoardIsCut = true;
                            Toast.makeText(this, "Cut", Toast.LENGTH_SHORT).show();
                            break;
                    }
                })
                .show();
    }

    // Back Navigation

    @Override
    public void onBackPressed() {
        if (adapter.isSelectionMode()) {
            adapter.clearSelection();
            invalidateOptionsMenu();
        } else if (!backStack.isEmpty()) {
            navigateUp();
        } else {
            super.onBackPressed();
        }
    }
}
