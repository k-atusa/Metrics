package com.example.k7metrics;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class Listview extends AppCompatActivity {
    private static final int MODE_TEXTS = 0;
    private static final int MODE_FOLDERS = 1;
    private static final int MODE_FILES = 2;
    private int currentMode = MODE_TEXTS;
    private int selectedFolder = -1;

    private Notebook nb;
    private RecyclerView recyclerView;
    private TextView textMode;
    private ListAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.view_list);
        try {
            this.nb = Notebook.getInstance(this);
        } catch (Exception e) {
            Toast.makeText(this, "Load failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        this.textMode = findViewById(R.id.text_mode);
        this.recyclerView = findViewById(R.id.list_folder);
        this.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        this.adapter = new ListAdapter();
        this.recyclerView.setAdapter(this.adapter);

        // Mode Change Button
        findViewById(R.id.button_change).setOnClickListener(v -> {
            this.currentMode = (this.currentMode == MODE_TEXTS) ? MODE_FOLDERS : MODE_TEXTS;
            updateUI();
        });

        // Add Button
        findViewById(R.id.button_new).setOnClickListener(v -> {
            if (this.currentMode == MODE_TEXTS) {
                try {
                    String title = "New Note " + (this.nb.GetTextsTitles().length + 1);
                    this.nb.SetTextsContent(title, title, "New Content");
                    this.nb.SaveTexts();
                    updateUI();
                    Toast.makeText(this, "Note Saved", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Add failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            } else if (this.currentMode == MODE_FILES) {
                IO1.SelectFile(filePickerLauncher, false);
            }
        });

        // PW Reset Button
        findViewById(R.id.button_reset).setOnClickListener(v -> {
            android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
            layout.setOrientation(android.widget.LinearLayout.VERTICAL);
            layout.setPadding(50, 40, 50, 10);

            final EditText spwInput = new EditText(this);
            spwInput.setHint("Screen Password (Int)");
            spwInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            spwInput.setText(this.nb.ScreenPW);
            layout.addView(spwInput);

            final EditText msgInput = new EditText(this);
            msgInput.setHint("Login Message");
            msgInput.setText(this.nb.Msg);
            layout.addView(msgInput);

            final EditText pwInput = new EditText(this);
            pwInput.setHint("New Real Password");
            pwInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
            layout.addView(pwInput);

            new AlertDialog.Builder(this)
                    .setTitle("Account Settings")
                    .setView(layout)
                    .setPositiveButton("Update", (dialog, which) -> {
                        this.nb.ScreenPW = spwInput.getText().toString();
                        this.nb.Msg = msgInput.getText().toString();
                        String newPw = pwInput.getText().toString();
                        if (!newPw.isEmpty()) {
                            this.nb.SetPW(newPw, e -> {
                                if (e == null) Toast.makeText(this, "Password Updated", Toast.LENGTH_SHORT).show();
                                else Toast.makeText(this, "Save Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                        } else {
                            try {
                                this.nb.SaveAccount();
                                Toast.makeText(this, "Settings Saved", Toast.LENGTH_SHORT).show();
                            } catch (Exception e) {
                                Toast.makeText(this, "Save Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        }
                    })
                    .setNegativeButton("Cancel", null).show();
        });

        updateUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI(); // Refresh list when returning from Textview/Fileview
    }

    private final ActivityResultLauncher<Intent> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    List<IO1.VFile> selectedFiles = IO1.HandleSelectedFile(result.getData());
                    if (!selectedFiles.isEmpty() && this.selectedFolder != -1) {
                        this.nb.ImportFile(selectedFolder, selectedFiles.get(0), e -> {
                            if (e == null) Toast.makeText(this, "File Imported", Toast.LENGTH_SHORT).show();
                            else Toast.makeText(this, "Import Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            updateUI();
                        });
                    }
                }
            }
    );

    private void updateUI() {
        if (currentMode == MODE_TEXTS) {
            this.textMode.setText("Texts");
            this.adapter.SetData(this.nb.GetTextsTitles());
        } else if (currentMode == MODE_FOLDERS) {
            this.textMode.setText("Folders");
            this.adapter.SetData(this.nb.GetDirNames());
        } else if (currentMode == MODE_FILES && selectedFolder != -1) {
            this.textMode.setText(this.nb.Names[selectedFolder].get(""));
            this.adapter.SetData(this.nb.GetFileNames(selectedFolder));
        }
    }

    class ListAdapter extends RecyclerView.Adapter<ListAdapter.ViewHolder> {
        private String[] items = new String[0];
        public void SetData(String[] data) {
            this.items = data != null ? data : new String[0];
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            int layoutId = (viewType == MODE_FOLDERS) ? R.layout.comp_diritem : R.layout.comp_fileitem;
            View view = LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public int getItemViewType(int position) {
            return currentMode;
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String title = items[position];
            holder.textView.setText(title);

            // rename button if folder view
            if (currentMode == MODE_FOLDERS) {
                View btnRename = holder.itemView.findViewById(R.id.btn_rename);
                if (btnRename != null) {
                    btnRename.setOnClickListener(v -> {
                        EditText input = new EditText(Listview.this);
                        input.setText(title);
                        new AlertDialog.Builder(Listview.this)
                                .setTitle("Rename Folder")
                                .setView(input)
                                .setPositiveButton("OK", (dialog, which) -> {
                                    String newName = input.getText().toString();
                                    nb.Names[holder.getBindingAdapterPosition()].put("", newName);
                                    try {
                                        nb.SaveNames(holder.getBindingAdapterPosition());
                                        Toast.makeText(Listview.this, "Renamed Folder", Toast.LENGTH_SHORT).show();
                                        updateUI();
                                    } catch (Exception e) {
                                        Toast.makeText(Listview.this, "Rename failed:" + e.toString(), Toast.LENGTH_LONG).show();
                                    }
                                })
                                .setNegativeButton("Cancel", null).show();
                    });
                }

            // delete button if text or file view
            } else {
                View btnDelete = holder.itemView.findViewById(R.id.btn_delete);
                if (btnDelete != null) {
                    btnDelete.setOnClickListener(v -> {
                        new AlertDialog.Builder(Listview.this)
                                .setTitle("Delete Confirmation")
                                .setMessage("Are you sure to delete '" + title + "'?")
                                .setPositiveButton("Delete", (dialog, which) -> {
                                    try {
                                        if (currentMode == MODE_TEXTS) { // text view
                                            nb.Texts.remove(title);
                                            nb.SaveTexts();
                                        } else { // file view
                                            String fileName = nb.Names[selectedFolder].remove(title);
                                            nb.SaveNames(selectedFolder);
                                            IO1.VFile targetFile = IO1.GetLocal(Listview.this, selectedFolder + "/" + fileName);
                                            if (targetFile.Exists(Listview.this)) {
                                                targetFile.Delete(Listview.this);
                                            }
                                        }
                                        updateUI();
                                        Toast.makeText(Listview.this, "Deleted " + title, Toast.LENGTH_SHORT).show();
                                    } catch (Exception e) {
                                        Toast.makeText(Listview.this, "Delete failed:" + e.toString(), Toast.LENGTH_LONG).show();
                                    }
                                })
                                .setNegativeButton("Cancel", null).show();
                    });
                }
            }

            holder.itemView.setOnClickListener(v -> {
                if (currentMode == MODE_TEXTS) { // start text edit
                    Intent intent = new Intent(Listview.this, Textview.class);
                    intent.putExtra("title", title);
                    startActivity(intent);
                } else if (currentMode == MODE_FOLDERS) { // switch to file view
                    selectedFolder = holder.getBindingAdapterPosition();
                    currentMode = MODE_FILES;
                    updateUI();
                } else { // start file manager
                    Intent intent = new Intent(Listview.this, Fileview.class);
                    intent.putExtra("pos", selectedFolder);
                    intent.putExtra("file", title);
                    startActivity(intent);
                }
            });
        }

        @Override
        public int getItemCount() { return items.length; }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView textView;
            ViewHolder(View itemView) {
                super(itemView);
                textView = itemView.findViewById(R.id.text_item);
            }
        }
    }
}
