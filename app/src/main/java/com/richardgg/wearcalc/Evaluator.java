package com.richardgg.wearcalc;

/** Recursive-descent evaluator for + - × ÷ ( ) % and unary minus. */
final class Evaluator {
    private final String s;
    private int pos;

    private Evaluator(String s) {
        this.s = s;
    }

    static double eval(String expr) {
        Evaluator e = new Evaluator(expr);
        double v = e.expr();
        if (e.pos != e.s.length()) throw new IllegalArgumentException();
        return v;
    }

    private char peek() {
        return pos < s.length() ? s.charAt(pos) : '\0';
    }

    private double expr() {
        double v = term();
        while (peek() == '+' || peek() == '−') {
            char op = s.charAt(pos++);
            double r = term();
            v = op == '+' ? v + r : v - r;
        }
        return v;
    }

    private double term() {
        double v = unary();
        while (peek() == '×' || peek() == '÷') {
            char op = s.charAt(pos++);
            double r = unary();
            v = op == '×' ? v * r : v / r;
        }
        return v;
    }

    private double unary() {
        if (peek() == '−') {
            pos++;
            return -unary();
        }
        return postfix();
    }

    private double postfix() {
        double v = primary();
        while (peek() == '%') {
            pos++;
            v /= 100;
        }
        return v;
    }

    private double primary() {
        if (peek() == '(') {
            pos++;
            double v = expr();
            if (peek() == ')') pos++; // tolerate a missing close paren
            return v;
        }
        int start = pos;
        while (Character.isDigit(peek()) || peek() == '.') pos++;
        if (start == pos) throw new IllegalArgumentException();
        return Double.parseDouble(s.substring(start, pos));
    }
}
