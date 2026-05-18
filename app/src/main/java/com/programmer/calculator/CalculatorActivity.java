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
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

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
            0xFFL,                   // 8  бит
            0xFFFFL,                 // 16 бит
            0xFFFFFFFFL,             // 32 бит
            0xFFFFFFFFFFFFFFFFL      // 64 бита (все биты long)
    };

    // Базовое состояние
    private int currentRadix = 10;
    private int currentBitWidth = 64;
    private long currentBitMask = BIT_MASKS[3];
    private String currentInput = "0";
    private long operandA = 0L;
    private String pendingOp = "";
    private boolean freshInput = true;

    // Состояние десятичной точки
    // Периодические дроби обрезаются до 10 знаков после запятой
    private static final int FRACTION_DIGITS = 10;

    // Флаг: текущий ввод содержит десятичную точку (только DEC)
    private boolean hasDecimalPoint = false;

    // Точное значение операнда A (BigDecimal для корректного перевода между СС)
    private BigDecimal decimalOperandAExact = BigDecimal.ZERO;

    // Вещественный операнд A (используется вместо operandA при вещественном режиме)
    private double decimalOperandA = 0.0;

    // Текущий вещественный ввод (полная строка с точкой, напр. "3.14")
    private String decimalInput = "0";

    // Главный метод onCreate
    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ThemeManager.applyTheme(this);

        EdgeToEdge.enable(this);

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

                // При смене СС -> перевод через BigDecimal без потери точности
                if (hasDecimalPoint) {

                    // Paerse текущего дробного ввода в BigDecimal
                    BigDecimal exact = parseBigDecimal(decimalInput, currentRadix);
                    currentRadix = newRadix;
                    if (newRadix == 10) {

                        // В DEC показ дроби
                        String encoded = encodeBigDecimal(exact);
                        decimalInput = encoded;
                        hasDecimalPoint = encoded.contains(".");
                        currentInput = encoded.contains(".")
                                ? encoded.substring(0, encoded.indexOf('.'))
                                : encoded;
                    } else {

                        // В BIN/OCT/HEX дробная часть отбрасывается
                        long intPart = exact.longValue() & currentBitMask;
                        currentInput = encode(intPart);
                        hasDecimalPoint = false;
                        decimalInput = "0";
                    }
                } else {
                    try {
                        long currentVal = Long.parseLong(currentInput, currentRadix);
                        currentInput = encode(currentVal, newRadix);
                    } catch (NumberFormatException e) {
                        currentRadix = newRadix;
                        resetAll();
                        return;
                    }
                    currentRadix = newRadix;
                }
                if (!pendingOp.isEmpty()) updateExpressionRow();
                refreshDisplay();
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

                // При вещественном вводе маска не применяется (дробная часть),
                // округляем и применяем маску
                if (hasDecimalPoint) {
                    long rounded = Math.round(parseDecimalInput()) & newMask;
                    decimalInput = Long.toString(rounded);
                    currentInput = decimalInput;
                    hasDecimalPoint = false;
                } else {
                    try {
                        long val = Long.parseLong(currentInput, currentRadix);
                        currentInput = encode(val & currentBitMask);
                    } catch (NumberFormatException e) {
                        resetAll();
                        return;
                    }
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

        // Кнопка десятичной точки (id: btnPoint)
        View btnDot = findViewById(R.id.btnPoint);
        if (btnDot != null) btnDot.setOnClickListener(v -> appendDecimalPoint());

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

    // Десятичная точка
    // Добавляет десятичную точку к текущему вводу.
    // Работает только в десятичной СС (currentRadix == 10).
    // Повторное нажатие игнорируется.
    private void appendDecimalPoint() {
        // Точка доступна только в DEC
        if (currentRadix != 10)
            return;
        // Уже есть точка — ничего не делаем
        if (hasDecimalPoint)
            return;

        hasDecimalPoint = true;
        if (freshInput) {
            // Начинаем новый ввод с "0."
            decimalInput = "0.";
            currentInput = "0";
            freshInput = false;
        } else {
            // Добавляем точку к уже введённому целому числу
            decimalInput = currentInput + ".";
        }
        refreshDisplay();
    }

    // Parse в Double
    private double parseDecimalInput() {
        try {
            return Double.parseDouble(decimalInput);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    // Форматирование Double-резульатат
    private String formatDecimal(double val) {
        if (val == Math.floor(val) && !Double.isInfinite(val)) {
            return Long.toString((long) val);
        }

        // До 10 знаков после точки, хвостовые нули убраны
        @SuppressLint("DefaultLocale")
        String s = String.format("%.10f", val);
        s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s;
    }

    // Ввод цифры
    private void appendDigit(String digit) {
        if (!isValidForBase(digit, currentRadix)) return;

        if (hasDecimalPoint) {
            // Вещественный режим: наращиваем дробную часть
            if (freshInput) {
                decimalInput = "0." + digit;
                freshInput = false;
            } else {
                decimalInput = decimalInput + digit;
            }
            refreshDisplay();
            return;
        }

        // Целочисленный режим (исходная логика)
        if (freshInput) {
            currentInput = "0".equals(digit) ? "0" : digit;
            freshInput = false;
        } else {
            currentInput = "0".equals(currentInput) ? digit : currentInput + digit;
        }
        try {
            long val = Long.parseUnsignedLong(currentInput, currentRadix);
            currentInput = encode(val & currentBitMask);
        } catch (NumberFormatException ignored) {
        }
        refreshDisplay();
    }

    // Выбор оператора
    private void setOperator(String op) {
        if (!pendingOp.isEmpty() && !freshInput) calculate();

        if (hasDecimalPoint) {
            decimalOperandA = parseDecimalInput();
            decimalOperandAExact = parseBigDecimal(decimalInput, currentRadix);

            // operandA — целая часть, используется только для нецелых операций
            operandA = Math.round(decimalOperandA);
        } else {
            try {
                operandA = Long.parseLong(currentInput, currentRadix) & currentBitMask;
                decimalOperandA = operandA;
                decimalOperandAExact = BigDecimal.valueOf(operandA);
            } catch (NumberFormatException e) {
                operandA = 0L;
                decimalOperandA = 0.0;
                decimalOperandAExact = BigDecimal.ZERO;
            }
        }

        // Сбрасываем флаг точки — следующий ввод будет свежим
        hasDecimalPoint = false;
        decimalInput = "0";

        pendingOp = op;
        freshInput = true;
        updateExpressionRow();
    }

    // Вычисление результата
    @SuppressLint("SetTextI18n")
    private void calculate() {
        if (pendingOp.isEmpty()) return;

        // Второй операнд
        double operandBDouble;
        long operandB;
        if (hasDecimalPoint) {
            operandBDouble = parseDecimalInput();
            operandB = Math.round(operandBDouble);
        } else {
            try {
                operandB = Long.parseLong(currentInput, currentRadix) & currentBitMask;
                operandBDouble = operandB;
            } catch (NumberFormatException e) {
                showError("Error");
                return;
            }
        }

        // Использовать double-арифметику если:
        // - хотя бы один из операндов вещественный, И текущая СС = DEC
        // - операция деления в DEC (результат не обязан быть целым)
        boolean isDecMode = (currentRadix == 10);
        boolean operandsAreDecimal = hasDecimalPoint || decimalInput.contains(".");
        boolean useDouble = isDecMode && (operandsAreDecimal || pendingOp.equals("/"));

        double resultDouble = 0;
        long resultLong = 0;
        boolean isDoubleResult = false;

        try {
            switch (pendingOp) {
                case "+":
                    if (useDouble) {
                        resultDouble = decimalOperandA + operandBDouble;
                        isDoubleResult = true;
                    } else {
                        resultLong = operandA + operandB;
                    }
                    break;
                case "-":
                    if (useDouble) {
                        resultDouble = decimalOperandA - operandBDouble;
                        isDoubleResult = true;
                    } else {
                        resultLong = operandA - operandB;
                    }
                    break;
                case "*":
                    if (useDouble) {
                        resultDouble = decimalOperandA * operandBDouble;
                        isDoubleResult = true;
                    } else {
                        resultLong = operandA * operandB;
                    }
                    break;
                case "/":
                    if (operandBDouble == 0.0 || operandB == 0L) {
                        showError("Infinity");
                        return;
                    }
                    if (isDecMode) {
                        // В DEC — вещественное деление
                        resultDouble = decimalOperandA / operandBDouble;
                        isDoubleResult = true;
                    } else {
                        // В BIN / OCT / HEX — целочисленное деление
                        resultLong = operandA / operandB;
                    }
                    break;
                case "%":
                    if (operandB == 0L) {
                        showError("Infinity");
                        return;
                    }
                    resultLong = operandA % operandB;
                    break;
                case "AND":
                    resultLong = operandA & operandB;
                    break;
                case "OR":
                    resultLong = operandA | operandB;
                    break;
                default:
                    return;
            }
        } catch (ArithmeticException e) {
            showError("Error");
            return;
        }

        // Сбрасываем флаг вещественного ввода
        hasDecimalPoint = false;
        decimalInput = "0";

        if (isDoubleResult) {
            String displayVal = formatDecimal(resultDouble);
            tvExpression.setText(
                    formatDecimalForExpr(decimalOperandA)
                            + " " + opSymbol(pendingOp)
                            + " " + formatDecimalForExpr(operandBDouble) + " =");

            // Если результат целый — работаем как обычно (целочисленный режим)
            if (resultDouble == Math.floor(resultDouble) && !Double.isInfinite(resultDouble)) {
                currentInput = Long.toString((long) resultDouble);
            } else {
                // Дробный результат: активируем режим точки
                decimalInput = displayVal;
                currentInput = Long.toString((long) resultDouble);
                hasDecimalPoint = true;
            }
            tvDisplay.setText(displayVal);
        } else {
            resultLong = resultLong & currentBitMask;
            tvExpression.setText(
                    encode(operandA) + " " + opSymbol(pendingOp)
                            + " " + encode(operandB) + " =");
            currentInput = encode(resultLong);
            refreshDisplay();
        }

        pendingOp = "";
        freshInput = true;
    }

    // Форматирование double для строки выражения (без лишних нулей)
    private String formatDecimalForExpr(double val) {
        if (val == Math.floor(val) && !Double.isInfinite(val)) {
            return Long.toString((long) val);
        }
        @SuppressLint("DefaultLocale")
        String s = String.format("%.10f", val);
        return s.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    // Прочие операции
    // Битовый NOT
    @SuppressLint("SetTextI18n")
    private void applyNot() {
        long val;
        try {
            val = Long.parseLong(currentInput, currentRadix);
        } catch (NumberFormatException e) {
            return;
        }
        long result = (~val) & currentBitMask;
        tvExpression.setText("NOT(" + encode(val) + ") =");
        currentInput = encode(result);
        hasDecimalPoint = false;
        decimalInput = "0";
        pendingOp = "";
        freshInput = true;
        refreshDisplay();
    }

    // Удаление последнего символа
    private void deleteLast() {
        if (freshInput) return;

        if (hasDecimalPoint) {
            if (decimalInput.length() > 1) {
                decimalInput = decimalInput.substring(0, decimalInput.length() - 1);

                // Если стёрли точку — выходим из вещественного режима
                if (!decimalInput.contains(".")) {
                    hasDecimalPoint = false;
                    currentInput = decimalInput;
                    decimalInput = "0";
                } else {
                    int dotPos = decimalInput.indexOf('.');
                    currentInput = dotPos > 0 ? decimalInput.substring(0, dotPos) : "0";
                }
            } else {
                decimalInput = "0";
                hasDecimalPoint = false;
                currentInput = "0";
            }
        } else {
            currentInput = currentInput.length() > 1
                    ? currentInput.substring(0, currentInput.length() - 1)
                    : "0";
        }
        refreshDisplay();
    }

    // Смена знака
    private void negate() {
        if (hasDecimalPoint) {
            double val = parseDecimalInput();
            double negVal = -val;
            String formatted = formatDecimal(negVal);
            if (formatted.contains(".")) {
                decimalInput = formatted;
                int dotPos = decimalInput.indexOf('.');
                currentInput = dotPos > 0 ? decimalInput.substring(0, dotPos) : "0";
            } else {
                hasDecimalPoint = false;
                currentInput = formatted;
                decimalInput = "0";
            }
            refreshDisplay();
            return;
        }
        try {
            long val = Long.parseLong(currentInput, currentRadix);
            currentInput = encode((-val) & currentBitMask);
            refreshDisplay();
        } catch (NumberFormatException ignored) {
        }
    }

    // Сбросить всё
    private void resetAll() {
        currentInput = "0";
        operandA = 0L;
        decimalOperandA = 0.0;
        decimalOperandAExact = BigDecimal.ZERO;
        pendingOp = "";
        freshInput = true;
        hasDecimalPoint = false;
        decimalInput = "0";
        tvExpression.setText("");
        refreshDisplay();
    }

    // Отображение
    @SuppressLint("SetTextI18n")
    private void refreshDisplay() {
        if (hasDecimalPoint) {
            tvDisplay.setText(decimalInput);
            return;
        }

        long val;
        try {
            val = Long.parseLong(currentInput, currentRadix);
        } catch (NumberFormatException e) {
            tvDisplay.setText(currentInput.toUpperCase());
            return;
        }

        if (signedMode) {
            long signed = toSigned(val);
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
        if (!pendingOp.isEmpty()) {
            String aStr = formatDecimalForExpr(decimalOperandA);

            // Если operandA целый и не из вещественного режима -> показываем в текущей СС
            if (decimalOperandA == Math.floor(decimalOperandA)) {
                aStr = encode(operandA);
            }
            tvExpression.setText(aStr + " " + opSymbol(pendingOp));
        }
    }

    // Отображение ошибок
    private void showError(String msg) {
        tvDisplay.setText(msg);
        tvExpression.setText("");
        currentInput = "0";
        operandA = 0L;
        decimalOperandA = 0.0;
        decimalOperandAExact = BigDecimal.ZERO;
        pendingOp = "";
        freshInput = true;
        hasDecimalPoint = false;
        decimalInput = "0";
    }

    // Вспомогательные методы

    private boolean signedMode = false;

    private long toSigned(long val) {
        long signBit = (currentBitMask >> 1) + 1;
        if ((val & signBit) != 0) {
            return val | ~currentBitMask;
        }
        return val;
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

    // Кодирует Long -> String в текущей СС.
    private String encode(long val) {
        return encode(val, currentRadix);
    }

    // Кодирует Long -> String в целевой СС.
    private String encode(long val, int radix) {
        if (!signedMode && val < 0) {
            return Long.toUnsignedString(val, radix).toUpperCase();
        }
        return Long.toString(val, radix).toUpperCase();
    }

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

        // Кнопка точки активна только в DEC
        View btnDot = findViewById(R.id.btnPoint);
        if (btnDot != null) {
            btnDot.setEnabled(currentRadix == 10);
            btnDot.setAlpha(currentRadix == 10 ? 1.0f : 0.30f);
        }
    }

    // Parse строки в любой СС (включая дробную часть) в BigDecimal
    // Используется для точного перевода дробей между системами счисления
    private BigDecimal parseBigDecimal(String input, int radix) {
        if (input == null || input.isEmpty()) return BigDecimal.ZERO;
        boolean negative = input.startsWith("-");
        if (negative) input = input.substring(1);

        String[] parts = input.split("\\.");
        BigDecimal result = new BigDecimal(
                new BigInteger(parts[0].isEmpty() ? "0" : parts[0], radix));

        if (parts.length >= 2 && !parts[1].isEmpty()) {
            BigDecimal base = BigDecimal.valueOf(radix);
            BigDecimal divisor = base;
            for (char c : parts[1].toCharArray()) {
                int digit = Character.digit(c, radix);
                if (digit < 0) break;
                result = result.add(
                        BigDecimal.valueOf(digit)
                                .divide(divisor, 20, RoundingMode.HALF_UP));
                divisor = divisor.multiply(base);
            }
        }
        return negative ? result.negate() : result;
    }

    // Кодирует BigDecimal в строку в десятичной СС
    // Периодические дроби обрезаются до FRACTION_DIGITS
    private String encodeBigDecimal(BigDecimal value) {
        boolean negative = value.compareTo(BigDecimal.ZERO) < 0;
        value = value.abs();

        BigDecimal[] split = value.divideAndRemainder(BigDecimal.ONE);
        String intStr = split[0].toBigInteger().toString(10);
        BigDecimal fracPart = split[1];

        if (fracPart.compareTo(BigDecimal.ZERO) == 0) {
            return (negative ? "-" : "") + intStr;
        }

        StringBuilder frac = new StringBuilder(".");
        BigDecimal base = BigDecimal.valueOf(10);

        // Обрезка периодической дроби до 10 знаков после запятой
        int limit = FRACTION_DIGITS;

        while (fracPart.compareTo(BigDecimal.ZERO) != 0 && limit-- > 0) {
            fracPart = fracPart.multiply(base);
            int digit = fracPart.intValue();
            frac.append(digit);
            fracPart = fracPart.subtract(BigDecimal.valueOf(digit));
        }

        return (negative ? "-" : "") + intStr + frac;
    }
}
