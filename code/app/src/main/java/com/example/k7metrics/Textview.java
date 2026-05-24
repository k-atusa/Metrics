package com.example.k7metrics;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class Textview extends AppCompatActivity {
    private Notebook nb;
    private String title;
    private EditText editTitle;
    private EditText editContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.view_edit);
        this.title = getIntent().getStringExtra("title");
        try {
            this.nb = Notebook.getInstance(this);
        } catch (Exception e) {
            Toast.makeText(this, e.toString(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        this.editTitle = findViewById(R.id.text_title);
        this.editContent = findViewById(R.id.note_content);
        this.editTitle.setText(this.title);
        this.editContent.setText(this.nb.GetTextsContent(this.title));

        // title and content update button
        findViewById(R.id.button_save).setOnClickListener(v -> {
            String newTitle = this.editTitle.getText().toString();
            String newContent = this.editContent.getText().toString();
            try {
                this.nb.SetTextsContent(this.title, newTitle, newContent);
                this.nb.SaveTexts();
                Toast.makeText(this, "Note Saved", Toast.LENGTH_SHORT).show();
                finish();
            } catch (Exception e) {
                Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }
}
