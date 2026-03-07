package dev.tracekit.security;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects and redacts sensitive data in snapshot variables and code.
 * Uses typed [REDACTED:type] markers for 13+ PII/credential patterns.
 * PII scrubbing is enabled by default and can be toggled via setScrubbing().
 */
public class SensitiveDataDetector {

    /**
     * Represents a PII pattern with its typed redaction marker.
     */
    public static class PIIPattern {
        private final Pattern pattern;
        private final String marker;

        public PIIPattern(Pattern pattern, String marker) {
            this.pattern = pattern;
            this.marker = marker;
        }

        public Pattern getPattern() { return pattern; }
        public String getMarker() { return marker; }
    }

    // Pre-compiled built-in patterns (13 standard patterns)
    private static final List<PIIPattern> BUILT_IN_PATTERNS = new ArrayList<>();

    static {
        BUILT_IN_PATTERNS.add(new PIIPattern(
                Pattern.compile("\\b[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}\\b"),
                "[REDACTED:email]"));
        BUILT_IN_PATTERNS.add(new PIIPattern(
                Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b"),
                "[REDACTED:ssn]"));
        BUILT_IN_PATTERNS.add(new PIIPattern(
                Pattern.compile("\\b\\d{4}[- ]?\\d{4}[- ]?\\d{4}[- ]?\\d{4}\\b"),
                "[REDACTED:credit_card]"));
        BUILT_IN_PATTERNS.add(new PIIPattern(
                Pattern.compile("\\b\\d{3}[-.  ]?\\d{3}[-.]?\\d{4}\\b"),
                "[REDACTED:phone]"));
        BUILT_IN_PATTERNS.add(new PIIPattern(
                Pattern.compile("AKIA[0-9A-Z]{16}"),
                "[REDACTED:aws_key]"));
        BUILT_IN_PATTERNS.add(new PIIPattern(
                Pattern.compile("(?i)aws.{0,20}secret.{0,20}[A-Za-z0-9/+=]{40}"),
                "[REDACTED:aws_secret]"));
        BUILT_IN_PATTERNS.add(new PIIPattern(
                Pattern.compile("(?i)(?:bearer\\s+)[A-Za-z0-9._~+/=\\-]{20,}"),
                "[REDACTED:oauth_token]"));
        BUILT_IN_PATTERNS.add(new PIIPattern(
                Pattern.compile("sk_live_[0-9a-zA-Z]{10,}"),
                "[REDACTED:stripe_key]"));
        BUILT_IN_PATTERNS.add(new PIIPattern(
                Pattern.compile("(?i)(?:password|passwd|pwd)\\s*[=:]\\s*[\"']?[^\\s\"']{6,}"),
                "[REDACTED:password]"));
        BUILT_IN_PATTERNS.add(new PIIPattern(
                Pattern.compile("eyJ[A-Za-z0-9_\\-]+\\.eyJ[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+"),
                "[REDACTED:jwt]"));
        BUILT_IN_PATTERNS.add(new PIIPattern(
                Pattern.compile("-----BEGIN (?:RSA |EC )?PRIVATE KEY-----"),
                "[REDACTED:private_key]"));
        BUILT_IN_PATTERNS.add(new PIIPattern(
                Pattern.compile("(?i)(?:api[_\\-]?key|apikey)\\s*[:=]\\s*[\"']?[A-Za-z0-9_\\-]{20,}"),
                "[REDACTED:api_key]"));
    }

    // Letter-boundary pattern -- \b treats _ as word char, so api_key/user_token won't match
    private static final Pattern SENSITIVE_NAME_PATTERN =
            Pattern.compile("(?i)(?:^|[^a-zA-Z])(password|passwd|pwd|secret|token|key|credential|api_key|apikey)(?:[^a-zA-Z]|$)");

    private boolean scrubbingEnabled = true;
    private final List<PIIPattern> allPatterns;

    /**
     * Creates a detector with the standard 13 built-in patterns.
     */
    public SensitiveDataDetector() {
        this.allPatterns = new ArrayList<>(BUILT_IN_PATTERNS);
    }

    /**
     * Creates a detector with custom patterns appended to the built-in set.
     */
    public SensitiveDataDetector(List<PIIPattern> customPatterns) {
        this.allPatterns = new ArrayList<>(BUILT_IN_PATTERNS);
        if (customPatterns != null) {
            this.allPatterns.addAll(customPatterns);
        }
    }

    /**
     * Enable or disable PII scrubbing. Enabled by default.
     */
    public void setScrubbing(boolean enabled) {
        this.scrubbingEnabled = enabled;
    }

    /**
     * Returns whether PII scrubbing is currently enabled.
     */
    public boolean isScrubbingEnabled() {
        return scrubbingEnabled;
    }

    /**
     * Represents a security finding in snapshot variables.
     */
    public static class SecurityFlag {
        private final String type;
        private final String severity;
        private final String variable;
        private final boolean redacted;

        public SecurityFlag(String type, String severity, String variable, boolean redacted) {
            this.type = type;
            this.severity = severity;
            this.variable = variable;
            this.redacted = redacted;
        }

        public String getType() { return type; }
        public String getSeverity() { return severity; }
        public String getVariable() { return variable; }
        public boolean isRedacted() { return redacted; }
    }

    /**
     * Result of scanning snapshot variables.
     */
    public static class ScanResult {
        private final Map<String, Object> sanitizedVariables;
        private final List<SecurityFlag> securityFlags;

        public ScanResult(Map<String, Object> sanitizedVariables, List<SecurityFlag> securityFlags) {
            this.sanitizedVariables = sanitizedVariables;
            this.securityFlags = securityFlags;
        }

        public Map<String, Object> getSanitizedVariables() { return sanitizedVariables; }
        public List<SecurityFlag> getSecurityFlags() { return securityFlags; }
    }

    /**
     * Scans snapshot variables for sensitive data and returns sanitized variables
     * with typed [REDACTED:type] markers. Scans serialized (stringified) values
     * to catch nested PII.
     *
     * @param variables The snapshot variables to scan
     * @return ScanResult with sanitized variables and security flags
     */
    public ScanResult scanVariables(Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return new ScanResult(new HashMap<>(), new ArrayList<>());
        }

        // If scrubbing is disabled, return as-is
        if (!scrubbingEnabled) {
            return new ScanResult(new HashMap<>(variables), new ArrayList<>());
        }

        Map<String, Object> sanitized = new HashMap<>();
        List<SecurityFlag> flags = new ArrayList<>();

        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue();

            // Check variable name for sensitive keywords (word-boundary matching)
            if (SENSITIVE_NAME_PATTERN.matcher(name).find()) {
                flags.add(new SecurityFlag("sensitive_variable_name", "medium", name, true));
                sanitized.put(name, "[REDACTED:sensitive_name]");
                continue;
            }

            // Serialize value to string for deep scanning
            String serialized = String.valueOf(value);

            boolean flagged = false;
            for (PIIPattern pp : allPatterns) {
                if (pp.getPattern().matcher(serialized).find()) {
                    flags.add(new SecurityFlag("sensitive_data", "high", name, true));
                    sanitized.put(name, pp.getMarker());
                    flagged = true;
                    break;
                }
            }

            if (!flagged) {
                sanitized.put(name, value);
            }
        }

        return new ScanResult(sanitized, flags);
    }

    // ---- Legacy code-scanning API (kept for backward compatibility) ----

    /**
     * Represents a security finding in code (legacy API)
     */
    public static class Finding {
        private final String type;
        private final int line;
        private final int column;
        private final String severity;
        private final String message;

        public Finding(String type, int line, int column, String severity, String message) {
            this.type = type;
            this.line = line;
            this.column = column;
            this.severity = severity;
            this.message = message;
        }

        public String getType() { return type; }
        public int getLine() { return line; }
        public int getColumn() { return column; }
        public String getSeverity() { return severity; }
        public String getMessage() { return message; }

        @Override
        public String toString() {
            return String.format("[%s] %s at line %d, column %d: %s",
                    severity.toUpperCase(), type, line, column, message);
        }
    }

    // Legacy patterns used by scan()/redact()
    private static final Pattern AWS_ACCESS_KEY = Pattern.compile("\\bAKIA[0-9A-Z]{16}\\b");
    private static final Pattern STRIPE_SECRET_KEY = Pattern.compile("\\bsk_live_[0-9a-zA-Z]{10,}");
    private static final Pattern STRIPE_PUBLISHABLE_KEY = Pattern.compile("\\bpk_live_[0-9a-zA-Z]{10,}");
    private static final Pattern GENERIC_API_KEY = Pattern.compile(
            "(?i)(api[_-]?key|apikey|access[_-]?token)\\s*[:=]\\s*['\"]([a-zA-Z0-9]{32,})['\"]"
    );
    private static final Pattern PASSWORD = Pattern.compile(
            "(?i)(password|passwd|pwd)\\s*[:=,]\\s*['\"]([^'\"]{6,})['\"]"
    );
    private static final Pattern PASSWORD_METHOD = Pattern.compile(
            "(?i)\\.(setPassword|setPasswd|password)\\s*\\(\\s*['\"]([^'\"]{6,})['\"]\\s*\\)"
    );
    private static final Pattern JWT_TOKEN = Pattern.compile(
            "\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b"
    );
    private static final Pattern CREDIT_CARD = Pattern.compile("\\b([0-9]{13,19})\\b");

    /**
     * Scans code for sensitive data (legacy API)
     */
    public List<Finding> scan(String code) {
        if (code == null || code.isEmpty()) {
            return new ArrayList<>();
        }

        List<Finding> findings = new ArrayList<>();
        String[] lines = code.split("\n", -1);

        for (int i = 0; i < lines.length; i++) {
            int lineNumber = i + 1;
            String line = lines[i];

            findPattern(line, lineNumber, AWS_ACCESS_KEY, findings,
                    "AWS_ACCESS_KEY", "critical",
                    "AWS Access Key detected");
            findPattern(line, lineNumber, STRIPE_SECRET_KEY, findings,
                    "STRIPE_SECRET_KEY", "critical",
                    "Stripe Secret Key detected");
            findPattern(line, lineNumber, STRIPE_PUBLISHABLE_KEY, findings,
                    "STRIPE_PUBLISHABLE_KEY", "high",
                    "Stripe Publishable Key detected");

            Matcher apiKeyMatcher = GENERIC_API_KEY.matcher(line);
            if (apiKeyMatcher.find()) {
                findings.add(new Finding("API_KEY", lineNumber, apiKeyMatcher.start(), "high",
                        "API Key detected"));
            }

            Matcher passwordMatcher = PASSWORD.matcher(line);
            if (passwordMatcher.find()) {
                findings.add(new Finding("PASSWORD", lineNumber, passwordMatcher.start(), "critical",
                        "Hardcoded password detected"));
            }

            Matcher passwordMethodMatcher = PASSWORD_METHOD.matcher(line);
            if (passwordMethodMatcher.find()) {
                findings.add(new Finding("PASSWORD", lineNumber, passwordMethodMatcher.start(), "critical",
                        "Hardcoded password in method call"));
            }

            findPattern(line, lineNumber, JWT_TOKEN, findings,
                    "JWT_TOKEN", "high", "JWT token detected");

            Matcher cardMatcher = CREDIT_CARD.matcher(line);
            while (cardMatcher.find()) {
                String cardNumber = cardMatcher.group(1);
                if (isValidCreditCard(cardNumber)) {
                    findings.add(new Finding("CREDIT_CARD", lineNumber, cardMatcher.start(), "critical",
                            "Valid credit card number detected"));
                }
            }
        }

        return findings;
    }

    /**
     * Redacts sensitive data in code using typed [REDACTED:type] markers.
     */
    public String redact(String code) {
        if (code == null || code.isEmpty()) {
            return "";
        }

        String redacted = code;

        // Apply all built-in patterns with typed markers
        for (PIIPattern pp : BUILT_IN_PATTERNS) {
            Matcher matcher = pp.getPattern().matcher(redacted);
            redacted = matcher.replaceAll(Matcher.quoteReplacement(pp.getMarker()));
        }

        return redacted;
    }

    private void findPattern(String line, int lineNumber, Pattern pattern,
                             List<Finding> findings, String type, String severity, String message) {
        Matcher matcher = pattern.matcher(line);
        while (matcher.find()) {
            findings.add(new Finding(type, lineNumber, matcher.start(), severity, message));
        }
    }

    private boolean isValidCreditCard(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 13 || cardNumber.length() > 19) {
            return false;
        }

        int sum = 0;
        boolean alternate = false;

        for (int i = cardNumber.length() - 1; i >= 0; i--) {
            char c = cardNumber.charAt(i);
            if (!Character.isDigit(c)) {
                return false;
            }

            int digit = Character.getNumericValue(c);

            if (alternate) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }

            sum += digit;
            alternate = !alternate;
        }

        return (sum % 10 == 0);
    }
}
