package com.programmer.calculator;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class MainActivity extends AppCompatActivity {

    //Константы - элементы интерфейса
    private Spinner spinnerFrom, spinnerTo;
    private TextInputEditText etInput;
    private TextInputLayout tilInput;
    private TextView tvResult, tvError, tvCopyFeedback, btnGoSettings;
    private MaterialButton btnConvert, btnGoCalculator;
    private View resultContainer;
    private static final int[] RADIX = {2, 8, 10, 16};
    private static final String[] BASE_NAMES = {"BIN (2)", "OCT (8)", "DEC (10)", "HEX (16)"};
    private int fromRadix = 2;
    private int toRadix = 10;
    private String lastResult = "";


    // Главный метод onCreate

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeManager.applyTheme(this);
        EdgeToEdge.enable(this); // Для Android 12 и ранее

        setContentView(R.layout.activity_main);

        initViews();
        setupSpinners();
        setupButtons();

        TextView tvInfo = findViewById(R.id.tvInfo);

        tvInfo.setOnLongClickListener(v ->{
            copyInfoToClipboard();
            return true;
        });
    }

    // Отображение ошибки по шаблону
    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }

    //Виджеты
    private void initViews() {
        spinnerFrom = findViewById(R.id.spinnerFrom);
        spinnerTo = findViewById(R.id.spinnerTo);
        etInput = findViewById(R.id.etInput);
        tilInput = findViewById(R.id.tilInput);
        tvResult = findViewById(R.id.tvResult);
        tvError = findViewById(R.id.tvError);
        tvCopyFeedback = findViewById(R.id.tvCopyFeedback);
        btnConvert = findViewById(R.id.btnConvert);
        btnGoCalculator = findViewById(R.id.btnGoCalculator);
        btnGoSettings = findViewById(R.id.btnGoSettings);
        resultContainer = findViewById(R.id.resultContainer);
    }

    // Логика Spinner-ов
    private void setupSpinners() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                R.layout.spinner_item,
                BASE_NAMES
        );
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);

        spinnerFrom.setAdapter(adapter);
        spinnerTo.setAdapter(adapter);

        // По умолчанию: BIN -> DEC
        spinnerFrom.setSelection(0); // BIN
        spinnerTo.setSelection(2);   // DEC

        spinnerFrom.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                fromRadix = RADIX[position];
                clearError();
                updateInputHint();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        spinnerTo.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                toRadix = RADIX[position];
                clearError();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    // При каждом ином выборе -> новая подсказка по допустимому вводу
    private void updateInputHint() {
        String hint;
        switch (fromRadix) {
            case 2:
                hint = "Например: 1010 (двоичное)";
                break;
            case 8:
                hint = "Например: 17 (восьмеричное)";
                break;
            case 16:
                hint = "Например: 1F (шестнадцатеричное)";
                break;
            default:
                hint = "Например: 42 или 3.14 (десятичное)";
                break;
        }
        tilInput.setHint(hint);
    }

    // Кнопки
    private void setupButtons() {
        btnConvert.setOnClickListener(v -> performConversion());

        resultContainer.setOnClickListener(v -> {
            if (!lastResult.isEmpty() && !lastResult.equals("—")) {
                copyToClipboard(lastResult);
            }
        });

        btnGoCalculator.setOnClickListener(v -> {
            Intent intent = new Intent(this, CalculatorActivity.class);
            startActivity(intent);
        });

        btnGoSettings.setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        });
    }

    // Механизм перевода
    private void performConversion() {
        String input = "";
        if (etInput.getText() != null) {
            input = etInput.getText().toString().trim().toUpperCase();
        }

        if (input.isEmpty()) {
            showError("Введите число");
            return;
        }

        // Отбор допустимых символов для СС
        if (!isValidForBase(input, fromRadix)) {
            showError(getInvalidCharsMessage(fromRadix));
            return;
        }

        try {
            long decimalValue;

            if (fromRadix == 10 && input.contains(".")) {
                // Десятичное число с дробной частью — округляем до целого
                double doubleVal = Double.parseDouble(input);
                decimalValue = Math.round(doubleVal);
            } else {
                // Parse числа базовой СС в Long
                decimalValue = Long.parseLong(input, fromRadix);
            }

            // Перевод в другую СС
            String result = Long.toString(decimalValue, toRadix).toUpperCase();

            clearError();
            lastResult = result;
            tvResult.setText(result);
        } catch (NumberFormatException e) {
            showError("Недопустимое число для данной системы"); // catch для недопустимого значения
        }
    }

    // Листы для допустимых символов для каждой СС
    // Для DEC допускается также одна десятичная точка (".")
    private boolean isValidForBase(String input, int radix) {
        String validChars;
        switch (radix) {
            case 2:
                validChars = "01"; // BIN
                break;
            case 8:
                validChars = "01234567"; // OCT
                break;
            case 10:
                validChars = "0123456789."; // DEC (точка допустима)
                break;
            case 16:
                validChars = "0123456789ABCDEF"; // HEX
                break;
            default:
                return false;
        }
        // Для DEC: допускается не более одной точки, и не в начале строки
        if (radix == 10) {
            int dotCount = 0;
            for (char c : input.toCharArray()) {
                if (c == '.') dotCount++;
                if (validChars.indexOf(c) < 0) return false;
            }
            if (dotCount > 1) return false;
            return !input.startsWith(".");
        }
        for (char c : input.toCharArray()) {
            if (validChars.indexOf(c) < 0)
                return false;
        }
        return true;
    }

    // Подсказка для верного ввода при ошибке
    private String getInvalidCharsMessage(int radix) {
        switch (radix) {
            case 2:
                return "Для BIN допустимы только 0 и 1";
            case 8:
                return "Для OCT допустимы цифры 0–7";
            case 10:
                return "Для DEC допустимы только цифры 0–9";
            case 16:
                return "Для HEX допустимы 0–9 и A–F";
            default:
                return "Недопустимый символ";
        }
    }

    // Убираем сообщение
    private void clearError() {
        tvError.setVisibility(View.GONE);
    }

    // Возможность скопировать ответ в буфер обмена
    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("result", text);
        clipboard.setPrimaryClip(clip);
        Snackbar.make(btnGoCalculator, "Результат скопирован в буфер обмена!",
                Snackbar.LENGTH_SHORT).show();

        tvCopyFeedback.setVisibility(View.VISIBLE);
        new Handler().postDelayed(() -> tvCopyFeedback.setVisibility(View.GONE), 1500);
    }

    private void copyInfoToClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("link", "https://github.com/KrisWinther/calcPro");
        clipboard.setPrimaryClip(clip);
        Snackbar.make(btnGoCalculator, "Ссылка на проект скопирована в буфер обмена!",
                Snackbar.LENGTH_SHORT).show();

        tvCopyFeedback.setVisibility(View.VISIBLE);
        new Handler().postDelayed(() -> tvCopyFeedback.setVisibility(View.GONE), 1500);
    }
}
