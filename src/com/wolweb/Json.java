package com.wolweb;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 极简 JSON 解析/序列化 (仅用于本项目 API, 无第三方依赖) */
public final class Json {
    private Json() {}

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(Object o) {
        return (Map<String, Object>) o;
    }

    public static String string(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    public static String stringify(Object value) {
        StringBuilder sb = new StringBuilder();
        write(value, sb);
        return sb.toString();
    }

    private static void write(Object v, StringBuilder sb) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String s) {
            sb.append('"');
            escape(s, sb);
            sb.append('"');
        } else if (v instanceof Number || v instanceof Boolean) {
            sb.append(v);
        } else if (v instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                write(String.valueOf(e.getKey()), sb);
                sb.append(':');
                write(e.getValue(), sb);
            }
            sb.append('}');
        } else if (v instanceof List<?> l) {
            sb.append('[');
            boolean first = true;
            for (Object item : l) {
                if (!first) sb.append(',');
                first = false;
                write(item, sb);
            }
            sb.append(']');
        } else {
            write(String.valueOf(v), sb);
        }
    }

    private static void escape(String s, StringBuilder sb) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
    }

    public static Object parse(String s) {
        Parser p = new Parser(s);
        Object v = p.parseValue();
        p.skipWs();
        if (!p.atEnd()) throw new IllegalArgumentException("JSON 末尾有多余内容");
        return v;
    }

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s == null ? "" : s;
        }

        boolean atEnd() {
            return i >= s.length();
        }

        void skipWs() {
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') i++;
                else break;
            }
        }

        char peek() {
            if (atEnd()) throw new IllegalArgumentException("JSON 意外结束");
            return s.charAt(i);
        }

        Object parseValue() {
            skipWs();
            return switch (peek()) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> parseLiteral("true", Boolean.TRUE);
                case 'f' -> parseLiteral("false", Boolean.FALSE);
                case 'n' -> parseLiteral("null", null);
                default -> parseNumber();
            };
        }

        Map<String, Object> parseObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            i++;
            skipWs();
            if (peek() == '}') {
                i++;
                return map;
            }
            while (true) {
                skipWs();
                String key = parseString();
                skipWs();
                if (peek() != ':') throw new IllegalArgumentException("JSON 缺少冒号");
                i++;
                map.put(key, parseValue());
                skipWs();
                char c = peek();
                if (c == ',') {
                    i++;
                } else if (c == '}') {
                    i++;
                    return map;
                } else {
                    throw new IllegalArgumentException("JSON 对象格式错误");
                }
            }
        }

        List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            i++;
            skipWs();
            if (peek() == ']') {
                i++;
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWs();
                char c = peek();
                if (c == ',') {
                    i++;
                } else if (c == ']') {
                    i++;
                    return list;
                } else {
                    throw new IllegalArgumentException("JSON 数组格式错误");
                }
            }
        }

        String parseString() {
            if (peek() != '"') throw new IllegalArgumentException("JSON 字符串格式错误");
            i++;
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (atEnd()) throw new IllegalArgumentException("JSON 字符串未结束");
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (atEnd()) throw new IllegalArgumentException("JSON 转义未结束");
                    char e = s.charAt(i++);
                    switch (e) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (i + 4 > s.length()) throw new IllegalArgumentException("JSON unicode 转义错误");
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            i += 4;
                        }
                        default -> throw new IllegalArgumentException("JSON 非法转义: \\" + e);
                    }
                } else {
                    sb.append(c);
                }
            }
        }

        Object parseNumber() {
            int start = i;
            while (i < s.length()) {
                char c = s.charAt(i);
                if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') i++;
                else break;
            }
            String num = s.substring(start, i);
            try {
                if (num.contains(".") || num.contains("e") || num.contains("E")) return Double.parseDouble(num);
                return Long.parseLong(num);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("JSON 数字格式错误: " + num);
            }
        }

        Object parseLiteral(String lit, Object value) {
            if (!s.startsWith(lit, i)) throw new IllegalArgumentException("JSON 字面量错误");
            i += lit.length();
            return value;
        }
    }
}
