package com.example.k7metrics;

import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;

public class Fileview extends AppCompatActivity {
    private Notebook nb;
    private int pos;
    private String filename;

    private EditText textTitle;
    private TextView textInfo;
    private TextInputEditText inputAddress;
    private TextInputEditText inputSecret;
    private CheckBox checkTrim;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.view_file);

        // Get parameters from Intent
        this.pos = getIntent().getIntExtra("pos", -1);
        this.filename = getIntent().getStringExtra("file");
        if (this.pos == -1 || this.filename == null) {
            Toast.makeText(this, "Invalid file parameters", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        try {
            this.nb = Notebook.getInstance(this);
        } catch (Exception e) {
            Toast.makeText(this, "Load Failed: " + e.toString(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Initialize UI
        textTitle = findViewById(R.id.text_title);
        textInfo = findViewById(R.id.text_info);
        inputAddress = findViewById(R.id.input_address);
        inputSecret = findViewById(R.id.input_secret);
        checkTrim = findViewById(R.id.check_trim);
        textTitle.setText(filename);
        updateInfo();

        // Rename button
        findViewById(R.id.button_save).setOnClickListener(v -> {
            String newName = textTitle.getText().toString().trim();
            if (newName.isEmpty() || newName.equals(filename)) return;
            try {
                this.nb.RenameFile(this.pos, this.filename, newName);
                this.filename = newName;
                updateInfo();
                Toast.makeText(this, "File Renamed", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Rename failed: " + e.toString(), Toast.LENGTH_LONG).show();
            }
        });

        // Export button
        findViewById(R.id.button_export).setOnClickListener(v -> {
            Toast.makeText(this, "Exporting...", Toast.LENGTH_SHORT).show();
            this.nb.ExportFile(this.pos, this.filename, e -> {
                if (e == null) Toast.makeText(this, "File Exported", Toast.LENGTH_SHORT).show();
                else Toast.makeText(this, "Export failed: " + e.toString(), Toast.LENGTH_LONG).show();
            });
        });

        // Send Button /////
        findViewById(R.id.button_send).setOnClickListener(v -> {
            String address = inputAddress.getText().toString().trim();
            String secret = inputSecret.getText().toString().trim();
            boolean trim = checkTrim.isChecked();
        });
    }

    // Information display
    private void updateInfo() {
        try {
            String folderName = this.nb.GetDirNames()[this.pos];
            String hexName = this.nb.Names[this.pos].get(filename);
            IO1.VFile f = IO1.GetLocal(this, pos + "/" + hexName);
            long size = f.GetSize(this);

            StringBuilder info = new StringBuilder();
            info.append("Logical Path: ").append(folderName).append("/").append(filename).append("\n");
            info.append("Physical Path: ").append(pos).append("/").append(hexName).append("\n");
            info.append("Physical Size: ").append(size).append(" bytes");
            
            textInfo.setText(info.toString());
        } catch (Exception e) {
            textInfo.setText("No Information: " + e.toString());
        }
    }
}
