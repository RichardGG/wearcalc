package com.richardgg.wearcalc;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.math.BigDecimal;
import java.math.MathContext;

public class MainActivity extends Activity {
    private static final String[][] PAGE_NUMBERS = {
            {"7", "8", "9"}, {"4", "5", "6"}, {"1", "2", "3"}, {".", "0", "⌫"}};
    private static final String[][] PAGE_OPS = {
            {"C", "(", ")"}, {"÷", "×", "%"}, {"−", "+", "±"}, {"⌫", "=", "="}};

    private final StringBuilder expr = new StringBuilder();
    private boolean showingResult;
    private TextView display;
    private LinearLayout pager;
    private int page; // 0 = numbers, 1 = operators
    private float downX, startTx;
    private boolean dragging;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        display = new TextView(this);
        display.setTextColor(Color.WHITE);
        display.setTypeface(Typeface.DEFAULT_BOLD);
        display.setGravity(Gravity.CENTER);
        display.setMaxLines(2);
        display.setAutoSizeTextTypeUniformWithConfiguration(
                14, 120, 2, TypedValue.COMPLEX_UNIT_SP);
        int pad = dp(14);
        display.setPadding(pad, pad, pad, pad);
        root.addView(display, new FrameLayout.LayoutParams(-1, -1));

        pager = new LinearLayout(this);
        pager.setOrientation(LinearLayout.HORIZONTAL);
        int w = getResources().getDisplayMetrics().widthPixels;
        pager.addView(buildPage(PAGE_NUMBERS), new LinearLayout.LayoutParams(w, -1));
        pager.addView(buildPage(PAGE_OPS), new LinearLayout.LayoutParams(w, -1));
        // The pager is two screens wide; clipping hides the off-screen page.
        root.addView(pager, new FrameLayout.LayoutParams(2 * w, -1));

        setContentView(root);
        refresh();
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    private View buildPage(String[][] rows) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        // Large inset so buttons stay inside a round screen.
        int inset = dp(22);
        col.setPadding(inset, dp(18), inset, dp(18));
        for (String[] row : rows) {
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            for (int i = 0; i < row.length; i++) {
                // On the operator page the bottom "=" spans two cells.
                if (row == rows[3] && rows == PAGE_OPS) {
                    if (i == 0) {
                        r.addView(button(row[0]), cell(1));
                    } else if (i == 1) {
                        r.addView(button("="), cell(2));
                    }
                    continue;
                }
                r.addView(button(row[i]), cell(1));
            }
            col.addView(r, new LinearLayout.LayoutParams(-1, 0, 1f));
        }
        return col;
    }

    private LinearLayout.LayoutParams cell(float weight) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, weight);
        lp.setMargins(dp(1), dp(1), dp(1), dp(1));
        return lp;
    }

    private TextView button(String label) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(0xE6FFFFFF);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        t.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(10));
        bg.setColor(label.equals("=") ? 0x4DFF9800 : 0x26FFFFFF); // mostly transparent
        t.setBackground(bg);
        t.setClickable(true);
        t.setOnClickListener(v -> press(label));
        return t;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        int w = pager.getChildAt(0).getWidth();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getRawX();
                startTx = pager.getTranslationX();
                dragging = false;
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = e.getRawX() - downX;
                if (!dragging && Math.abs(dx) > dp(12)) {
                    dragging = true;
                    MotionEvent cancel = MotionEvent.obtain(e);
                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                    super.dispatchTouchEvent(cancel);
                    cancel.recycle();
                }
                if (dragging) {
                    pager.setTranslationX(Math.max(-w, Math.min(0, startTx + dx)));
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    float dxu = e.getRawX() - downX;
                    if (Math.abs(dxu) > w / 5f) page = dxu < 0 ? 1 : 0;
                    pager.animate().translationX(-page * w).setDuration(150).start();
                    dragging = false;
                    return true;
                }
                break;
            default:
                break;
        }
        return super.dispatchTouchEvent(e);
    }

    private void press(String k) {
        boolean isDigit = k.length() == 1 && (Character.isDigit(k.charAt(0)) || k.equals("."));
        if (showingResult) {
            // Typing a digit starts fresh; an operator continues from the result.
            if (isDigit || k.equals("(")) expr.setLength(0);
            showingResult = false;
        }
        switch (k) {
            case "C":
                expr.setLength(0);
                break;
            case "⌫":
                if (expr.length() > 0) expr.setLength(expr.length() - 1);
                break;
            case "±":
                toggleSign();
                break;
            case "=":
                evaluate();
                return;
            default:
                if (expr.length() == 0 && "×÷+%)".contains(k)) break;
                expr.append(k);
        }
        refresh();
    }

    private void toggleSign() {
        int i = expr.length();
        while (i > 0 && (Character.isDigit(expr.charAt(i - 1)) || expr.charAt(i - 1) == '.')) i--;
        if (i > 0 && expr.charAt(i - 1) == '−'
                && (i == 1 || "×÷+−(".indexOf(expr.charAt(i - 2)) >= 0)) {
            expr.deleteCharAt(i - 1);
        } else {
            expr.insert(i, '−');
        }
    }

    private void evaluate() {
        if (expr.length() == 0) return;
        try {
            double v = Evaluator.eval(expr.toString());
            if (Double.isNaN(v) || Double.isInfinite(v)) throw new ArithmeticException();
            expr.setLength(0);
            expr.append(format(v));
            showingResult = true;
            refresh();
        } catch (RuntimeException ex) {
            expr.setLength(0);
            showingResult = false;
            display.setText("Error");
        }
    }

    private static String format(double v) {
        BigDecimal bd = new BigDecimal(v, new MathContext(10)).stripTrailingZeros();
        String s = bd.abs().compareTo(new BigDecimal("1e9")) >= 0
                || (bd.signum() != 0 && bd.abs().compareTo(new BigDecimal("1e-6")) < 0)
                ? bd.toString() : bd.toPlainString();
        return s.replace('-', '−');
    }

    private void refresh() {
        display.setText(expr.length() == 0 ? "0" : expr.toString());
    }
}
