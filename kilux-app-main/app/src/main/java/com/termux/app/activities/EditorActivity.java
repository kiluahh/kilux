package com.termux.app.activities;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.termux.R;
import com.termux.app.editor.LineNumberEditText;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

public class EditorActivity extends AppCompatActivity {

    private LineNumberEditText editor;
    private File file;
    private String originalText;
    private boolean isCodeMode = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);

        Toolbar toolbar = findViewById(com.termux.shared.R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            toolbar.setNavigationOnClickListener(v -> onBackPressed());
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.setDisplayHomeAsUpEnabled(true);
            }
        }

        editor = findViewById(R.id.code_editor);
        editor.requestFocus();

        String path = getIntent().getStringExtra("file_path");
        if (path == null) {
            Toast.makeText(this, "No file specified", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        file = new File(path);
        if (!file.exists() || !file.isFile()) {
            Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setTitle(file.getName());
        originalText = readFile(file);
        editor.setText(originalText);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.editor_menu, menu);
        updateModeMenuItem(menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        updateModeMenuItem(menu);
        return super.onPrepareOptionsMenu(menu);
    }

    private void updateModeMenuItem(Menu menu) {
        MenuItem item = menu.findItem(R.id.menu_toggle_mode);
        if (item != null) {
            item.setTitle(isCodeMode ? "Note mode" : "Code mode");
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_save) {
            saveFile();
            return true;
        } else if (id == R.id.menu_toggle_mode) {
            toggleMode();
            return true;
        } else if (id == R.id.menu_font_size) {
            showFontSizeDialog();
            return true;
        } else if (id == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void toggleMode() {
        isCodeMode = !isCodeMode;
        editor.setLineNumbersEnabled(isCodeMode);
        editor.setHorizontallyScrolling(isCodeMode);
        invalidateOptionsMenu();
    }

    private void showFontSizeDialog() {
        final int[] sizes = {10, 12, 14, 16, 18, 20, 24};
        String[] labels = new String[sizes.length];
        int current = (int) (editor.getTextSize() / getResources().getDisplayMetrics().scaledDensity);
        for (int i = 0; i < sizes.length; i++) {
            labels[i] = sizes[i] + (sizes[i] == current ? "  ✔" : "") + " sp";
        }
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Font size")
                .setItems(labels, (d, which) -> {
                    float sizePx = sizes[which] * getResources().getDisplayMetrics().scaledDensity;
                    editor.setTextSize(sizes[which]);
                    editor.setLineNumberTextSize(sizePx);
                })
                .show();
    }

    private void saveFile() {
        try {
            String text = editor.getText().toString();
            try (OutputStreamWriter out = new OutputStreamWriter(
                    new FileOutputStream(file), "UTF-8")) {
                out.write(text);
            }
            originalText = text;
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Save failed: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        String current = editor.getText().toString();
        if (!current.equals(originalText)) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Unsaved changes")
                    .setMessage("Save before exit?")
                    .setPositiveButton("Save", (d, w) -> {
                        saveFile();
                        finish();
                    })
                    .setNegativeButton("Discard", (d, w) -> finish())
                    .setNeutralButton(android.R.string.cancel, null)
                    .show();
        } else {
            super.onBackPressed();
        }
    }

    private static String readFile(File file) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (Exception ignored) {
        }
        return sb.toString();
    }
}
