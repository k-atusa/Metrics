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

        // Send Button
        findViewById(R.id.button_send).setOnClickListener(v -> {
            String address = inputAddress.getText().toString().trim();
            String secret = inputSecret.getText().toString().trim();
            boolean trim = checkTrim.isChecked();
            if (address.isEmpty()) {
                Toast.makeText(this, "Address Required", Toast.LENGTH_SHORT).show();
                return;
            }
            v.setEnabled(false);
            send(address, secret, trim);
            v.setEnabled(true);
        });
    }

    // TP1 send
    private void send(String address, String secret, boolean trim) {
        new Thread(() -> {
            TP1.TCPsocket sock = new TP1.TCPsocket();
            byte[] data = null;
            byte[] shs = null;
            try {
                // 1. Parse address
                String ip = address;
                int port = 8001;
                if (address.contains(":")) {
                    String[] parts = address.split(":");
                    ip = parts[0];
                    port = Integer.parseInt(parts[1]);
                }

                // 2. Read and prepare data
                data = this.nb.ReadFile(this.pos, this.filename, 8 * 1024 * 1024);
                if (trim && data.length > 4096) {
                    data = java.util.Arrays.copyOf(data, 4096);
                }
                String dataCrc = Opsec.Crc32(data);
                int dataSize = data.length;

                // 3. Connect and Send using TP1
                sock.MakeConnection(ip, port);
                shs = Bencode.NormPW(secret);
                TP1 tp = new TP1(TP1.HASH_ARG2_ST | TP1.SYM_GCM1 | TP1.ASYM_PQC1, true, true, shs, sock.Conn);

                java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(data);
                TP1.TP1Result res = tp.Send(bais, data.length, "", null);

                // 4. Update UI
                runOnUiThread(() -> {
                    StringBuilder sb = new StringBuilder(textInfo.getText());
                    sb.append("\n\n-- Send Result --\n");
                    if (res.Err != null) {
                        sb.append("Status: Error - ").append(res.Err.getMessage());
                    } else {
                        sb.append("Status: Success\n");
                        sb.append("Payload Size: ").append(dataSize).append(" bytes\n");
                        sb.append("Payload CRC: ").append(dataCrc).append("\n");
                        sb.append("Sender CRC: ").append(Opsec.Crc32(res.FromPub)).append("\n");
                        sb.append("Receiver CRC: ").append(Opsec.Crc32(res.ToPub));
                    }
                    textInfo.setText(sb.toString());
                });

            } catch (Exception e) {
                runOnUiThread(() -> { textInfo.append("\n\nSend Failed: " + e.toString()); });
            } finally {
                if (data != null) java.util.Arrays.fill(data, (byte) 0);
                if (shs != null) java.util.Arrays.fill(shs, (byte) 0);
                sock.Close();
            }
        }).start();
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
