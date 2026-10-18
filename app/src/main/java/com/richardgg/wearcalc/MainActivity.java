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
    // Rows are {weight, keys...}. Both pages use four equal rows so the output layer can line
    // up with the seams between them (see buildScreen).
    private static final String[][] PAGE_NUMBERS = {
            {"1", "7", "8", "9"}, {"1", "4", "5", "6"}, {"1", "1", "2", "3"}, {"1", ".", "0", "⌫"}};
    // Equals is a swipe up and clear is a long-press on backspace, so neither needs a key.
    private static final String[][] PAGE_OPS = {
            {"1", "÷", "×"}, {"1", "−", "+"}, {"1", "(", ")"}, {"1", "%", "±"}};

    private final StringBuilder expr = new StringBuilder();
    private boolean showingResult;
    private TextView display;
    private TextView total;
    private LinearLayout pager;
    private int page; // 0 = numbers, 1 = operators
    private float downX, downY, startTx;
    private static final int NONE = 0, HORIZONTAL = 1, VERTICAL = 2;
    private int drag = NONE;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        root.addView(buildScreen(), new FrameLayout.LayoutParams(-1, -1));

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

    private static final int INSET_X = 22, INSET_Y = 18;

    /**
     * Output layer: the expression is centred on the seam between key rows 1 and 2 and the
     * running total on the seam between rows 3 and 4, clear of the key labels.
     */
    private View buildScreen() {
        final float box = 0.6f; // text box height in key rows
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(INSET_X), dp(INSET_Y), dp(INSET_X), dp(INSET_Y));
        display = outputView(0xFF4FD8FF);
        total = outputView(0xFFFFC247);
        col.addView(new View(this), new LinearLayout.LayoutParams(-1, 0, 1f - box / 2));
        col.addView(display, new LinearLayout.LayoutParams(-1, 0, box));
        col.addView(new View(this), new LinearLayout.LayoutParams(-1, 0, 2f - box));
        col.addView(total, new LinearLayout.LayoutParams(-1, 0, box));
        col.addView(new View(this), new LinearLayout.LayoutParams(-1, 0, 1f - box / 2));
        return col;
    }

    private TextView outputView(int color) {
        TextView t = new TextView(this);
        t.setTextColor(color);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setMaxLines(1);
        t.setIncludeFontPadding(false);
        t.setAutoSizeTextTypeUniformWithConfiguration(10, 36, 1, TypedValue.COMPLEX_UNIT_SP);
        return t;
    }

    private View buildPage(String[][] rows) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        // Large inset so buttons stay inside a round screen.
        col.setPadding(dp(INSET_X), dp(INSET_Y), dp(INSET_X), dp(INSET_Y));
        for (String[] row : rows) {
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            for (int i = 1; i < row.length; i++) r.addView(button(row[i]), cell(1));
            col.addView(r, new LinearLayout.LayoutParams(-1, 0, Float.parseFloat(row[0])));
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
        t.setTextColor(Color.WHITE);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        t.setShadowLayer(dp(3), 0, 0, Color.BLACK); // halo keeps labels legible over the display
        t.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(10));
        // Dark scrim so the key stands out from the output behind it.
        bg.setColor(0x59000000);
        t.setBackground(bg);
        t.setClickable(true);
        t.setOnClickListener(v -> press(label));
        if (label.equals("⌫")) {
            t.setOnLongClickListener(v -> {
                press("C");
                return true;
            });
        }
        return t;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        int w = pager.getChildAt(0).getWidth();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getRawX();
                downY = e.getRawY();
                startTx = pager.getTranslationX();
                drag = NONE;
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = e.getRawX() - downX;
                float dy = e.getRawY() - downY;
                if (drag == NONE && Math.max(Math.abs(dx), Math.abs(dy)) > dp(12)) {
                    drag = Math.abs(dx) > Math.abs(dy) ? HORIZONTAL : VERTICAL;
                    MotionEvent cancel = MotionEvent.obtain(e);
                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                    super.dispatchTouchEvent(cancel);
                    cancel.recycle();
                }
                if (drag == HORIZONTAL) {
                    pager.setTranslationX(Math.max(-w, Math.min(0, startTx + dx)));
                }
                if (drag != NONE) return true;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (drag == HORIZONTAL) {
                    float dxu = e.getRawX() - downX;
                    if (Math.abs(dxu) > w / 5f) showPage(dxu < 0 ? 1 : 0);
                    else showPage(page);
                } else if (drag == VERTICAL && e.getActionMasked() == MotionEvent.ACTION_UP
                        && downY - e.getRawY() > dp(40)) {
                    press("=");
                }
                if (drag != NONE) {
                    drag = NONE;
                    return true;
                }
                break;
            default:
                break;
        }
        return super.dispatchTouchEvent(e);
    }

    private void showPage(int p) {
        page = p;
        pager.animate().translationX(-page * pager.getChildAt(0).getWidth()).setDuration(150).start();
    }

    private void press(String k) {
        boolean isDigit = k.length() == 1 && (Character.isDigit(k.charAt(0)) || k.equals("."));
        if (showingResult) {
            // Typing a digit starts fresh; an operator continues from the result.
            if (isDigit || k.equals("(")) expr.setLength(0);
            showingResult = false;
        }
        if (!isDigit && !k.equals("⌫") && !k.equals("C") && !k.equals("=")) showPage(0);
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
            total.setText("");
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
        total.setText(showingResult ? "" : runningTotal());
    }

    /** Value of the expression so far, ignoring a dangling operator; empty if it is just a number. */
    private String runningTotal() {
        String e = expr.toString();
        int end = e.length();
        while (end > 0 && "×÷+−(".indexOf(e.charAt(end - 1)) >= 0) end--;
        e = e.substring(0, end);
        if (!e.matches(".*[×÷+%(].*") && !e.substring(Math.min(1, e.length())).contains("−")) return "";
        try {
            double v = Evaluator.eval(e);
            return Double.isNaN(v) || Double.isInfinite(v) ? "" : "= " + format(v);
        } catch (RuntimeException ex) {
            return "";
        }
    }
}
