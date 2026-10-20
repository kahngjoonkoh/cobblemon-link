package io.github.kahngjoonkoh.cobblemonlink;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Just enough MoLang for Cobblemon's dialogues: statements split by ';', v.x = assignments, ?: and ternaries
 * without an else, || && == != ! +, numbers, 'strings', and names (q.x and c.x are the same) that are either
 * values or calls into {@code funcs}. An unknown name or call is 0, as in MoLang.
 * ponytail: no arithmetic beyond +, no loops or arrays; Cobblemon's own dialogues use none of them.
 */
final class Molang {
    final Map<String, Object> vars = new HashMap<>();
    final Map<String, Function<List<Object>, Object>> funcs = new HashMap<>();
    private String s;
    private int i;
    private boolean dry; // parsing a branch that isn't taken: no calls

    /** Parses (and only runs when take) one sub-expression. */
    private Object branch(boolean take, java.util.function.Supplier<Object> p) {
        boolean was = dry;
        dry = was || !take;
        try { return p.get(); } finally { dry = was; }
    }

    /** Runs statements; the last one's value. */
    Object run(String src) {
        Object last = 0.0;
        for (String st : split(src)) {
            s = st.trim();
            i = 0;
            if (s.isEmpty()) continue;
            var m = java.util.regex.Pattern.compile("^[vct]\\.([A-Za-z_][\\w.]*)\\s*=(?!=)").matcher(s);
            if (m.find()) {
                i = m.end();
                vars.put(m.group(1), last = expr());
            } else last = expr();
        }
        return last;
    }

    static boolean truthy(Object o) {
        return o instanceof Boolean b ? b : o instanceof Double d ? d != 0 : o instanceof String str && !str.isEmpty();
    }

    /** ';' outside quotes. */
    private static List<String> split(String src) {
        List<String> out = new ArrayList<>();
        StringBuilder b = new StringBuilder();
        boolean q = false;
        for (int k = 0; k < src.length(); k++) {
            char c = src.charAt(k);
            if (c == '\\' && k + 1 < src.length()) { b.append(c).append(src.charAt(++k)); continue; }
            if (c == '\'') q = !q;
            if (c == ';' && !q) { out.add(b.toString()); b.setLength(0); } else b.append(c);
        }
        out.add(b.toString());
        return out;
    }

    private Object expr() {
        Object c = or();
        if (eat("?")) {
            boolean t = truthy(c);
            Object a = branch(t, this::expr), b = eat(":") ? branch(!t, this::expr) : 0.0;
            return t ? a : b;
        }
        return c;
    }

    private Object or() {
        Object a = and();
        while (eat("||")) { boolean t = truthy(a); Object b = branch(!t, this::and); a = t || truthy(b); }
        return a;
    }

    private Object and() {
        Object a = eq();
        while (eat("&&")) { boolean t = truthy(a); Object b = branch(t, this::eq); a = t && truthy(b); }
        return a;
    }

    private Object eq() {
        Object a = add();
        while (true) {
            if (eat("==")) a = same(a, add());
            else if (eat("!=")) a = !same(a, add());
            else return a;
        }
    }

    private static boolean same(Object a, Object b) {
        if (a instanceof Boolean || b instanceof Boolean) return truthy(a) == truthy(b);
        return String.valueOf(a).equals(String.valueOf(b));
    }

    private Object add() {
        Object a = unary();
        while (eat("+")) {
            Object b = unary();
            a = a instanceof Double x && b instanceof Double y ? (Object) (x + y) : text(a) + text(b);
        }
        return a;
    }

    static String text(Object o) {
        return o instanceof Double d && d == Math.rint(d) ? String.valueOf(d.longValue()) : String.valueOf(o);
    }

    private Object unary() {
        if (eat("!")) return !truthy(unary());
        return primary();
    }

    private Object primary() {
        ws();
        if (eat("(")) { Object v = expr(); eat(")"); return v; }
        if (i < s.length() && s.charAt(i) == '\'') {
            StringBuilder b = new StringBuilder();
            for (i++; i < s.length() && s.charAt(i) != '\''; i++) b.append(s.charAt(i) == '\\' ? s.charAt(++i) : s.charAt(i));
            i++;
            return b.toString();
        }
        int st = i;
        while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.') && (i > st || Character.isDigit(s.charAt(i)))) i++;
        if (i > st) return Double.parseDouble(s.substring(st, i));
        while (i < s.length() && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_' || s.charAt(i) == '.')) i++;
        String name = s.substring(st, i);
        if (name.equals("true")) return true;
        if (name.equals("false")) return false;
        name = name.replaceFirst("^(query|context|c)\\.", "q.").replaceFirst("^(variable|temp|t)\\.", "v.");
        if (eat("(")) {
            List<Object> args = new ArrayList<>();
            if (!eat(")")) {
                do args.add(expr()); while (eat(","));
                eat(")");
            }
            var f = dry ? null : funcs.get(name);
            return f == null ? 0.0 : f.apply(args);
        }
        if (name.startsWith("v.")) return vars.getOrDefault(name.substring(2), 0.0);
        var f = funcs.get(name);
        return f == null ? 0.0 : f.apply(List.of());
    }

    private void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }

    private boolean eat(String tok) {
        ws();
        if (s.startsWith(tok, i) && !(tok.equals("!") && s.startsWith("!=", i))) {
            i += tok.length();
            return true;
        }
        return false;
    }
}
