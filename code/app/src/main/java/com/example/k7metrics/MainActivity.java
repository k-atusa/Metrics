// test816a : project USAG metrics
package com.example.k7metrics;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private Notebook nb;
    private EditText inputNum;
    private TextView textUnit;
    private TextView textResult;
    private boolean isReverse = false;
    private double[] constants = {
            2.54, 0.3048, 0.9144,
            1.609344, 28.349523125, 0.45359237,
            0.5555555555555556, 3.785411784, 1400
    };
    private String[] units = {
            "in", "cm", "ft", "m", "yd", "m",
            "mi", "km", "oz", "g", "lb", "kg",
            "'F", "'C", "gal", "L", "$", "₩"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.view_main);
        try {
            this.nb = Notebook.getInstance(MainActivity.this);
            this.constants[8] = this.nb.Rate;
        } catch (Exception e) {
            Toast.makeText(MainActivity.this, e.toString(), Toast.LENGTH_LONG).show();
        }

        inputNum = findViewById(R.id.input_num);
        textUnit = findViewById(R.id.text_unit);
        textResult = findViewById(R.id.text_result);

        Button button11 = findViewById(R.id.button11);
        button11.setOnClickListener(v -> buttonAction(0));
        Button button12 = findViewById(R.id.button12);
        button12.setOnClickListener(v -> buttonAction(1));
        Button button13 = findViewById(R.id.button13);
        button13.setOnClickListener(v -> buttonAction(2));

        Button button21 = findViewById(R.id.button21);
        button21.setOnClickListener(v -> buttonAction(3));
        Button button22 = findViewById(R.id.button22);
        button22.setOnClickListener(v -> buttonAction(4));
        Button button23 = findViewById(R.id.button23);
        button23.setOnClickListener(v -> buttonAction(5));

        Button button31 = findViewById(R.id.button31);
        button31.setOnClickListener(v -> buttonAction(6));
        Button button32 = findViewById(R.id.button32);
        button32.setOnClickListener(v -> buttonAction(7));
        Button button33 = findViewById(R.id.button33);
        button33.setOnClickListener(v -> buttonAction(8));

        findViewById(R.id.button_money).setOnClickListener(v -> {
            EditText rateInput = new EditText(this);
            rateInput.setInputType(InputType.TYPE_CLASS_NUMBER);
            rateInput.setText(String.valueOf((int) this.constants[8]));

            new AlertDialog.Builder(this)
                    .setTitle("Set Exchange Rate")
                    .setView(rateInput)
                    .setPositiveButton("Save", (dialog, which) -> {
                        try {
                            int newRate = Integer.parseInt(rateInput.getText().toString());
                            this.nb.Rate = newRate;
                            this.constants[8] = newRate;
                            this.nb.SaveAccount();
                            Toast.makeText(this, "Rate updated: " + newRate, Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            Toast.makeText(this, "Update failed: " + e.toString(), Toast.LENGTH_LONG).show();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void buttonAction(int pos) {
        String input = this.inputNum.getText().toString();
        if ((int)this.constants[8]%2 == 0 && input.equals(this.nb.ScreenPW)) {
            EditText passwordInput = new EditText(this);
            passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

            new AlertDialog.Builder(this)
                    .setTitle(this.nb.Msg)
                    .setView(passwordInput)
                    .setPositiveButton("Login", (dialog, which) -> {
                        String realPw = passwordInput.getText().toString();
                        this.nb.Login(realPw, e -> {
                            if (e == null) {
                                Intent intent = new Intent(MainActivity.this, Listview.class);
                                startActivity(intent);
                            } else {
                                Toast.makeText(this, "Login Failed: " + e.toString(), Toast.LENGTH_LONG).show();
                            }
                        });
                    })
                    .setNegativeButton("Cancel", null)
                    .show();

        } else if (!input.isEmpty()) { // unit conversion
            String unitStart = this.units[2*pos];
            String unitEnd = this.units[2*pos+1];

            double value = pos == 6 ? Double.parseDouble(input)+40: Double.parseDouble(input);
            if (isReverse) {
                value = value / this.constants[pos];
                String temp = unitStart;
                unitStart = unitEnd;
                unitEnd = temp;
            } else {
                value = value * this.constants[pos];
            }
            value = pos == 6 ? value - 40: value;

            isReverse = !isReverse;
            textUnit.setText(" " + unitStart + " ");
            textResult.setText(String.format("%,.3f", value) + " " + unitEnd);
        }
    }
}
