package io.trellis.common.json;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Canonical JSON writer and a small strict parser, with no dependencies.
 *
 * <p>Canonical form, identical to Python {@code json.dumps(obj, sort_keys=True, separators=(",",
 * ":"), ensure_ascii=False)} on NFC-normalised strings: object keys sorted, no whitespace, strings
 * NFC normalised, keys ordered by Unicode code point (as Python does, not by UTF-16 unit), integers
 * in plain notation (an integral {@link BigDecimal} is accepted). Floating point values are
 * rejected because their text form differs between languages.
 *
 * <p>Used by {@code StableId} (hash input) and {@code CursorCodec} (token body). Not a general JSON
 * library; use Jackson for API payloads.
 */
public final class CanonicalJson {
  private CanonicalJson() {}

  public static String write(Object value) {
    StringBuilder out = new StringBuilder();
    writeValue(value, out);
    return out.toString();
  }

  /**
   * Parses objects to {@link LinkedHashMap}, arrays to {@link ArrayList}, numbers to {@link
   * BigDecimal}.
   */
  public static Object parse(String json) {
    Parser parser = new Parser(json);
    Object value = parser.readValue();
    parser.skipWhitespace();
    if (!parser.atEnd()) {
      throw new IllegalArgumentException("Trailing characters at " + parser.pos);
    }
    return value;
  }

  private static void writeValue(Object value, StringBuilder out) {
    switch (value) {
      case null -> out.append("null");
      case Boolean b -> out.append(b);
      case Integer i -> out.append(i);
      case Long l -> out.append(l);
      case Short s -> out.append(s);
      case Byte b -> out.append(b);
      case BigInteger bi -> out.append(bi);
      case BigDecimal bd -> out.append(integral(bd));
      case Double d -> throw new IllegalArgumentException("Floating point is not canonical: " + d);
      case Float f -> throw new IllegalArgumentException("Floating point is not canonical: " + f);
      case CharSequence s -> writeString(s.toString(), out);
      case Map<?, ?> map -> writeObject(map, out);
      case Collection<?> items -> writeArray(items, out);
      case Object[] items -> writeArray(List.of(items), out);
      default ->
          throw new IllegalArgumentException(
              "Unsupported JSON type: " + value.getClass().getName());
    }
  }

  private static String integral(BigDecimal value) {
    try {
      return value.toBigIntegerExact().toString();
    } catch (ArithmeticException e) {
      throw new IllegalArgumentException("Fractional numbers are not canonical: " + value, e);
    }
  }

  private static void writeObject(Map<?, ?> map, StringBuilder out) {
    TreeMap<String, Object> sorted = new TreeMap<>(CanonicalJson::compareCodePoints);
    map.forEach(
        (key, val) -> {
          if (!(key instanceof String name)) {
            throw new IllegalArgumentException("JSON object keys must be strings");
          }
          sorted.put(Normalizer.normalize(name, Normalizer.Form.NFC), val);
        });
    out.append('{');
    boolean first = true;
    for (Map.Entry<String, Object> entry : sorted.entrySet()) {
      if (!first) {
        out.append(',');
      }
      first = false;
      writeString(entry.getKey(), out);
      out.append(':');
      writeValue(entry.getValue(), out);
    }
    out.append('}');
  }

  private static int compareCodePoints(String a, String b) {
    int i = 0;
    int j = 0;
    while (i < a.length() && j < b.length()) {
      int ca = a.codePointAt(i);
      int cb = b.codePointAt(j);
      if (ca != cb) {
        return Integer.compare(ca, cb);
      }
      i += Character.charCount(ca);
      j += Character.charCount(cb);
    }
    return Integer.compare(a.length() - i, b.length() - j);
  }

  private static void writeArray(Collection<?> items, StringBuilder out) {
    out.append('[');
    boolean first = true;
    for (Object item : items) {
      if (!first) {
        out.append(',');
      }
      first = false;
      writeValue(item, out);
    }
    out.append(']');
  }

  private static void writeString(String raw, StringBuilder out) {
    String s = Normalizer.normalize(raw, Normalizer.Form.NFC);
    out.append('"');
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        case '\b' -> out.append("\\b");
        case '\f' -> out.append("\\f");
        default -> {
          if (c < 0x20) {
            out.append(String.format("\\u%04x", (int) c));
          } else {
            out.append(c);
          }
        }
      }
    }
    out.append('"');
  }

  private static final class Parser {
    private final String text;
    private int pos;

    Parser(String text) {
      this.text = text;
    }

    boolean atEnd() {
      return pos >= text.length();
    }

    void skipWhitespace() {
      while (!atEnd() && Character.isWhitespace(text.charAt(pos))) {
        pos++;
      }
    }

    Object readValue() {
      skipWhitespace();
      if (atEnd()) {
        throw error("Unexpected end of input");
      }
      char c = text.charAt(pos);
      return switch (c) {
        case '{' -> readObject();
        case '[' -> readArray();
        case '"' -> readString();
        case 't' -> literal("true", Boolean.TRUE);
        case 'f' -> literal("false", Boolean.FALSE);
        case 'n' -> literal("null", null);
        default -> readNumber();
      };
    }

    private Map<String, Object> readObject() {
      Map<String, Object> map = new LinkedHashMap<>();
      pos++;
      skipWhitespace();
      if (peek('}')) {
        pos++;
        return map;
      }
      while (true) {
        skipWhitespace();
        if (!peek('"')) {
          throw error("Expected object key");
        }
        String key = readString();
        skipWhitespace();
        expect(':');
        map.put(key, readValue());
        skipWhitespace();
        if (peek(',')) {
          pos++;
          continue;
        }
        expect('}');
        return map;
      }
    }

    private List<Object> readArray() {
      List<Object> list = new ArrayList<>();
      pos++;
      skipWhitespace();
      if (peek(']')) {
        pos++;
        return list;
      }
      while (true) {
        list.add(readValue());
        skipWhitespace();
        if (peek(',')) {
          pos++;
          continue;
        }
        expect(']');
        return list;
      }
    }

    private String readString() {
      expect('"');
      StringBuilder sb = new StringBuilder();
      while (true) {
        if (atEnd()) {
          throw error("Unterminated string");
        }
        char c = text.charAt(pos++);
        if (c == '"') {
          return sb.toString();
        }
        if (c != '\\') {
          sb.append(c);
          continue;
        }
        if (atEnd()) {
          throw error("Unterminated escape");
        }
        char e = text.charAt(pos++);
        switch (e) {
          case '"', '\\', '/' -> sb.append(e);
          case 'n' -> sb.append('\n');
          case 'r' -> sb.append('\r');
          case 't' -> sb.append('\t');
          case 'b' -> sb.append('\b');
          case 'f' -> sb.append('\f');
          case 'u' -> {
            if (pos + 4 > text.length()) {
              throw error("Bad unicode escape");
            }
            sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
            pos += 4;
          }
          default -> throw error("Bad escape");
        }
      }
    }

    private BigDecimal readNumber() {
      int start = pos;
      while (!atEnd() && "+-0123456789.eE".indexOf(text.charAt(pos)) >= 0) {
        pos++;
      }
      if (start == pos) {
        throw error("Unexpected character");
      }
      try {
        return new BigDecimal(text.substring(start, pos));
      } catch (NumberFormatException e) {
        throw error("Bad number");
      }
    }

    private Object literal(String word, Object value) {
      if (!text.startsWith(word, pos)) {
        throw error("Unexpected literal");
      }
      pos += word.length();
      return value;
    }

    private boolean peek(char c) {
      return !atEnd() && text.charAt(pos) == c;
    }

    private void expect(char c) {
      if (!peek(c)) {
        throw error("Expected '" + c + "'");
      }
      pos++;
    }

    private IllegalArgumentException error(String message) {
      return new IllegalArgumentException(message + " at " + pos);
    }
  }
}
