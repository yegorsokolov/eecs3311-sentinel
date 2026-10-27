package sentinel.domain;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;

/** Local syntax normalization only; numeric IPv6 parsing never receives a hostname. */
public final class Indicator {
    private Indicator() { }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) throw invalid();
        String text = value.strip();
        if (text.length() > 254 || text.chars().anyMatch(c -> c > 127)) throw invalid();
        if (text.contains(":")) {
            if (!text.matches("[0-9A-Fa-f:.]+")) throw invalid();
            try {
                return InetAddress.getByName(text).getHostAddress().toLowerCase(Locale.ROOT);
            } catch (UnknownHostException ex) { throw invalid(); }
        }
        if (text.matches("[0-9.]+")) {
            String[] parts = text.split("\\.", -1);
            if (parts.length != 4) throw invalid();
            for (String part : parts) {
                if (!part.matches("0|[1-9][0-9]{0,2}") || Integer.parseInt(part) > 255) throw invalid();
            }
            return text;
        }
        text = text.toLowerCase(Locale.ROOT);
        if (text.endsWith(".")) text = text.substring(0, text.length() - 1);
        if (text.length() > 253) throw invalid();
        String[] labels = text.split("\\.", -1);
        if (labels.length < 2 || labels[labels.length - 1].matches("[0-9]+")) throw invalid();
        for (String label : labels) {
            if (label.length() > 63 || !label.matches("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?")) throw invalid();
        }
        return text;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Indicator must be an IPv4/IPv6 literal or an ASCII domain.");
    }
}
