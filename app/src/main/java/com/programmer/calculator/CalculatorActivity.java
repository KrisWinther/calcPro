package com.programmer.calculator;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;

public class CalculatorActivity extends AppCompatActivity {

    // Элементы UI (база)
    private TextView tvDisplay;
    private TextView tvExpression;
    private Spinner spinnerBase;
    private Spinner spinnerBits;

    // Основания систем счисления
    private static final int[] RADIX = {2, 8, 10, 16};
    private static final String[] BASE_NAMES = {"BIN", "OCT", "DEC", "HEX"};

    // Разрядности и их маски
    private static final int[] BIT_WIDTHS = {8, 16, 32, 64};
    private static final String[] BIT_NAMES = {"8 бит", "16 бит", "32 бит", "64 бит"};
    private static final long[] BIT_MASKS = {
            0xFFL, // 8  бит
            0xFFFFL, // 16 бит
            0xFFFFFFFFL, // 32 бит
            0xFFFFFFFFFFFFFFFFL // 64 бита (все биты long)
    };

    // Базовое состояние
    private int currentRadix = 10;
    private int currentBitWidth = 64;   // текущая разрядность
    private long currentBitMask = BIT_MASKS[3]; // Текущая маска
    private String currentInput = "0";
    private long operandA = 0L;
    private String pendingOp = "";
    private boolean freshInput = true;

    // Главный метод onCreate
    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this); // Для Android 12 или ранее

        setContentView(R.layout.activity_calculator);
        tvDisplay = findViewById(R.id.tvDisplay);
        tvExpression = findViewById(R.id.tvExpression);
        spinnerBase = findViewById(R.id.spinnerBase);
        spinnerBits = findViewById(R.id.spinnerBits);
        setupSpinner();
        setupBitsSpinner();
        setupButtons();
        refreshDisplay();

        CheckBox cbSigned = findViewById(R.id.cbSigned);
        cbSigned.setOnCheckedChangeListener((btn, isChecked) -> {
                    signedMode = isChecked;
                    refreshDisplay();
                }
        );
    }

    // Spinner системы счисления и логика изменения
    private void setupSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, R.layout.spinner_item, BASE_NAMES);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinnerBase.setAdapter(adapter);
        spinnerBase.setSelection(2); // Десятичная СС по умолчанию
        spinnerBase.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                int newRadix = RADIX[pos];
                if (newRadix == currentRadix) return;
                try {
                    long currentVal = Long.parseLong(currentInput, currentRadix);
                    currentRadix = newRadix;
                    currentInput = encode(currentVal);
                    if (!pendingOp.isEmpty()) updateExpressionRow();
                    refreshDisplay();
                } catch (NumberFormatException e) {
                    currentRadix = newRadix;
                    resetAll();
                }
                updateDigitButtonAvailability();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    // Spinner разрядности и логика изменения
    private void setupBitsSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, R.layout.spinner_item, BIT_NAMES);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinnerBits.setAdapter(adapter);
        spinnerBits.setSelection(3); // 64 бит по умолчанию
        spinnerBits.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view,
                                       int pos, long id) {
                int newBits = BIT_WIDTHS[pos];
                long newMask = BIT_MASKS[pos];
                if (newBits == currentBitWidth) return;
                currentBitWidth = newBits;
                currentBitMask = newMask;

                // Новая маска числа -> новое значение
                try {
                    long val = Long.parseLong(currentInput, currentRadix);
                    currentInput = encode(val & currentBitMask);
                } catch (NumberFormatException e) {
                    resetAll();
                    return;
                }
                refreshDisplay();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    // Привязка сета кнопок к действиям
    private void setupButtons() {
        MaterialButton btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        // BIN, OCT, DEC СС
        int[] digitIds = {R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
                R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9};
        String[] digitChars = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9"};
        for (int i = 0; i < digitIds.length; i++) {
            final String ch = digitChars[i];
            MaterialButton b = findViewById(digitIds[i]);
            if (b != null) b.setOnClickListener(v -> appendDigit(ch));
        }

        // HEX СС
        int[] hexIds = {R.id.btnA, R.id.btnB, R.id.btnC_hex,
                R.id.btnD, R.id.btnE, R.id.btnF};
        String[] hexChars = {"A", "B", "C", "D", "E", "F"};
        for (int i = 0; i < hexIds.length; i++) {
            final String ch = hexChars[i];
            MaterialButton b = findViewById(hexIds[i]);
            if (b != null) b.setOnClickListener(v -> appendDigit(ch));
        }

        // Привязка кнопок к методам
        bindOp(R.id.btnAdd, "+");
        bindOp(R.id.btnSub, "-");
        bindOp(R.id.btnMul, "*");
        bindOp(R.id.btnDiv, "/");
        bindOp(R.id.btnMod, "%");
        bindOp(R.id.btnAnd, "AND");
        bindOp(R.id.btnOr, "OR");

        View btnNot = findViewById(R.id.btnNot);
        if (btnNot != null) btnNot.setOnClickListener(v -> applyNot());

        bindAction(R.id.btnEqual, this::calculate);
        bindAction(R.id.btnClear, this::resetAll);
        bindAction(R.id.btnDel, this::deleteLast);
        bindAction(R.id.btnNeg, this::negate);
        updateDigitButtonAvailability();
    }

    // Применение оператора к выражению
    private void bindOp(int id, String op) {
        View v = findViewById(id);
        if (v != null) v.setOnClickListener(btn -> setOperator(op));
    }

    // Выполнение выражения
    private void bindAction(int id, Runnable action) {
        View v = findViewById(id);
        if (v != null) v.setOnClickListener(btn -> action.run());
    }

    // Ввод цифры
    private void appendDigit(String digit) {
        if (!isValidForBase(digit, currentRadix)) return;
        if (freshInput) {
            currentInput = "0".equals(digit) ? "0" : digit;
            freshInput = false;
        } else {
            currentInput = "0".equals(currentInput) ? digit : currentInput + digit;
        }
        // Ввод обрезан по текущей маске разрядности
        try {
            long val = Long.parseUnsignedLong(currentInput, currentRadix);
            currentInput = encode(val & currentBitMask);
        } catch (NumberFormatException ignored) {
        }
        refreshDisplay();
    }


    // Новое поле
    private boolean signedMode = false; // Unsigned CheckBox переключатель

    // Метод знакового расширения — проверяет старший бит маски
    private long toSigned(long val) {
        // Старший бит текущей разрядности
        long signBit = (currentBitMask >> 1) + 1; // Логика: при 16 бит -> маска 0x8000
        if ((val & signBit) != 0) {
            // Бит знака установлен — число отрицательное
            // Старшие биты -> в единицы
            return val | ~currentBitMask;
        }
        return val; // Бит знака = 0 -> число положительное
    }

    private boolean isValidForBase(String digit, int radix) {
        if (digit == null || digit.isEmpty()) return false;
        char c = digit.toUpperCase().charAt(0);
        switch (radix) {
            case 2:
                return c == '0' || c == '1';
            case 8:
                return c >= '0' && c <= '7';
            case 10:
                return c >= '0' && c <= '9';
            case 16:
                return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F');
            default:
                return false;
        }
    }

    // Выбор оператора
    private void setOperator(String op) {
        if (!pendingOp.isEmpty() && !freshInput) calculate();
        try {
            operandA = Long.parseLong(currentInput, currentRadix) & currentBitMask;
        } catch (NumberFormatException e) {
            operandA = 0L;
        }
        pendingOp = op;
        freshInput = true;
        updateExpressionRow();
    }

    // Вычисление результата (калькулятор)
    @SuppressLint("SetTextI18n")
    private void calculate() {
        if (pendingOp.isEmpty()) return;
        long operandB;
        try {
            operandB = Long.parseLong(currentInput, currentRadix) & currentBitMask;
        } catch (NumberFormatException e) {
            showError("Error");
            return;
        }
        long result;
        try {
            switch (pendingOp) {
                case "+":
                    result = operandA + operandB;
                    break;
                case "-":
                    result = operandA - operandB;
                    break;
                case "*":
                    result = operandA * operandB;
                    break;
                case "/":
                    if (operandB == 0L) {
                        showError("Infinity");
                        return;
                    }
                    result = operandA / operandB;
                    break;
                case "%":
                    if (operandB == 0L) {
                        showError("Infinity");
                        return;
                    }
                    result = operandA % operandB;
                    break;
                case "AND":
                    result = operandA & operandB;
                    break;
                case "OR":
                    result = operandA | operandB;
                    break;
                default:
                    return;
            }
        } catch (ArithmeticException e) {
            showError("Error");
            return;
        }
        // Применение маски числа к результату
        result = result & currentBitMask;
        tvExpression.setText(
                encode(operandA) + " " + opSymbol(pendingOp)
                        + " " + encode(operandB) + " =");
        currentInput = encode(result);
        pendingOp = "";
        freshInput = true;
        refreshDisplay();
    }

    // Битовый NOT (инверсия)
    @SuppressLint("SetTextI18n")
    private void applyNot() {
        long val;
        try {
            val = Long.parseLong(currentInput, currentRadix);
        } catch (NumberFormatException e) {
            return;
        }
        //NOT инвертирует только биты в пределах currentBitWidth
        long result = (~val) & currentBitMask;
        String valStr = encode(val);
        tvExpression.setText("NOT(" + valStr + ") =");
        currentInput = encode(result);
        pendingOp = "";
        freshInput = true;
        refreshDisplay();
    }

    // Вспомогательные операции
    private void deleteLast() {
        if (freshInput) return;
        currentInput = currentInput.length() > 1
                ? currentInput.substring(0, currentInput.length() - 1)
                : "0";
        refreshDisplay();
    }

    private void negate() {
        try {
            long val = Long.parseLong(currentInput, currentRadix);
            // Маска после смены знака
            currentInput = encode((-val) & currentBitMask);
            refreshDisplay();
        } catch (NumberFormatException ignored) {
        }
    }

    // Сбросить всё
    private void resetAll() {
        currentInput = "0";
        operandA = 0L;
        pendingOp = "";
        freshInput = true;
        tvExpression.setText("");
        refreshDisplay();
    }

    // Отображение
    @SuppressLint("SetTextI18n")
    private void refreshDisplay() {
        long val;
        try {
            val = Long.parseLong(currentInput, currentRadix);
        } catch (NumberFormatException e) {
            tvDisplay.setText(currentInput.toUpperCase());
            return;
        }

        if (signedMode) {
            long signed = toSigned(val);

            // Число стало отрицательным -> знак и модуль в текущей СС
            if (signed < 0) {
                tvDisplay.setText("-" + encode(-signed));
            } else {
                tvDisplay.setText(encode(signed));
            }
        } else {
            tvDisplay.setText(currentInput.toUpperCase());
        }
    }

    // Поле выражения (обновить)
    @SuppressLint("SetTextI18n")
    private void updateExpressionRow() {
        if (!pendingOp.isEmpty())
            tvExpression.setText(encode(operandA) + " " + opSymbol(pendingOp));
    }

    // Отображние ошибок
    private void showError(String msg) {
        tvDisplay.setText(msg);
        tvExpression.setText("");
        currentInput = "0";
        operandA = 0L;
        pendingOp = "";
        freshInput = true;
    }

    // Инструменты
    // Кодирует long в строку в текущей СС в верхний регистр
    // Отрицательные значения кодируются как беззнаковые
    private String encode(long val) {
        if (!signedMode && val < 0) {
            return Long.toUnsignedString(val, currentRadix).toUpperCase();
        }
        return Long.toString(val, currentRadix).toUpperCase();
    }

    // Отображение операторов
    private String opSymbol(String op) {
        switch (op) {
            case "+":
                return "+";
            case "-":
                return "−";
            case "*":
                return "×";
            case "/":
                return "÷";
            case "%":
                return "%";
            case "AND":
                return "AND";
            case "OR":
                return "OR";
            default:
                return op;
        }
    }

    // Состояние кнопок клавиатуры при измененях
    private void updateDigitButtonAvailability() {
        int[][] groups = {
                {R.id.btn2, R.id.btn3, R.id.btn4, R.id.btn5, R.id.btn6, R.id.btn7},
                {R.id.btn8, R.id.btn9},
                {R.id.btnA, R.id.btnB, R.id.btnC_hex, R.id.btnD, R.id.btnE, R.id.btnF}
        };
        int[] minRadixes = {8, 10, 16};
        for (int g = 0; g < groups.length; g++) {
            boolean enabled = currentRadix >= minRadixes[g];
            for (int id : groups[g]) {
                View btn = findViewById(id);
                if (btn != null) {
                    btn.setEnabled(enabled);
                    btn.setAlpha(enabled ? 1.0f : 0.30f);
                }
            }
        }
    }
}