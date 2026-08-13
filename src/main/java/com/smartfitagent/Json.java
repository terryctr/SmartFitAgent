package com.smartfitagent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Json {
    private Json() {}

    public static String q(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : String.valueOf(text).toCharArray()) {
            if (c == '\\') out.append("\\\\");
            else if (c == '"') out.append("\\\"");
            else if (c == '\n') out.append("\\n");
            else if (c == '\r') out.append("\\r");
            else if (c == '\t') out.append("\\t");
            else out.append(c);
        }
        return out.append('"').toString();
    }

    public static String obj(Map<String, String> map) {
        StringBuilder sb = new StringBuilder("{");
        int i = 0;
        for (var entry : map.entrySet()) {
            if (i++ > 0) sb.append(',');
            sb.append(q(entry.getKey())).append(':');
            String value = entry.getValue();
            if (value != null && (value.startsWith("{") || value.startsWith("[") || value.equals("true") || value.equals("false") || value.matches("-?\\d+(\\.\\d+)?"))) {
                sb.append(value);
            } else {
                sb.append(q(value == null ? "" : value));
            }
        }
        return sb.append('}').toString();
    }

    public static String arr(Collection<String> rows) {
        return "[" + String.join(",", rows) + "]";
    }

    public static Map<String, String> parse(String body) {
        Map<String, String> map = new LinkedHashMap<>();
        if (body == null || body.isBlank()) return map;
        String s = body.trim();
        if (s.startsWith("{") && s.endsWith("}")) s = s.substring(1, s.length() - 1);
        boolean inString = false;
        boolean escaped = false;
        StringBuilder current = new StringBuilder();
        List<String> pairs = new ArrayList<>();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escaped) {
                current.append(c);
                escaped = false;
            } else if (c == '\\') {
                current.append(c);
                escaped = true;
            } else if (c == '"') {
                inString = !inString;
                current.append(c);
            } else if (c == ',' && !inString) {
                pairs.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) pairs.add(current.toString());
        for (String pair : pairs) {
            int split = pair.indexOf(':');
            if (split < 0) continue;
            map.put(unq(pair.substring(0, split)), unq(pair.substring(split + 1)));
        }
        return map;
    }

    private static String unq(String value) {
        String s = value.trim();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) s = s.substring(1, s.length() - 1);
        return s.replace("\\n", "\n").replace("\\t", "\t").replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
