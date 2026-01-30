package dev.tracekit.security;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects and redacts sensitive data in code.
 * Scans for API keys, passwords, JWT tokens, credit cards, and other secrets.
 */
public class SensitiveDataDetector {

    // Pre-compiled patterns for efficiency
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
     * Represents a security finding in code
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

        public String getType() {
            return type;
        }

        public int getLine() {
            return line;
        }

        public int getColumn() {
            return column;
        }

        public String getSeverity() {
            return severity;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            return String.format("[%s] %s at line %d, column %d: %s",
                    severity.toUpperCase(), type, line, column, message);
        }
    }

    /**
     * Scans code for sensitive data
     *
     * @param code The code to scan
     * @return List of findings
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

            // Check AWS access keys
            findPattern(line, lineNumber, AWS_ACCESS_KEY, findings,
                    "AWS_ACCESS_KEY", "critical",
                    "AWS Access Key detected - should be stored in environment variables or secrets manager");

            // Check Stripe secret keys
            findPattern(line, lineNumber, STRIPE_SECRET_KEY, findings,
                    "STRIPE_SECRET_KEY", "critical",
                    "Stripe Secret Key detected - must never be committed to code");

            // Check Stripe publishable keys
            findPattern(line, lineNumber, STRIPE_PUBLISHABLE_KEY, findings,
                    "STRIPE_PUBLISHABLE_KEY", "high",
                    "Stripe Publishable Key detected - should be stored securely");

            // Check generic API keys
            Matcher apiKeyMatcher = GENERIC_API_KEY.matcher(line);
            if (apiKeyMatcher.find()) {
                findings.add(new Finding(
                        "API_KEY",
                        lineNumber,
                        apiKeyMatcher.start(),
                        "high",
                        "API Key detected - should be stored in environment variables"
                ));
            }

            // Check passwords
            Matcher passwordMatcher = PASSWORD.matcher(line);
            if (passwordMatcher.find()) {
                findings.add(new Finding(
                        "PASSWORD",
                        lineNumber,
                        passwordMatcher.start(),
                        "critical",
                        "Hardcoded password detected - must be removed and stored securely"
                ));
            }

            // Check password methods
            Matcher passwordMethodMatcher = PASSWORD_METHOD.matcher(line);
            if (passwordMethodMatcher.find()) {
                findings.add(new Finding(
                        "PASSWORD",
                        lineNumber,
                        passwordMethodMatcher.start(),
                        "critical",
                        "Hardcoded password in method call - must be removed"
                ));
            }

            // Check JWT tokens
            findPattern(line, lineNumber, JWT_TOKEN, findings,
                    "JWT_TOKEN", "high",
                    "JWT token detected - should not be hardcoded in source code");

            // Check credit cards (with Luhn validation)
            Matcher cardMatcher = CREDIT_CARD.matcher(line);
            while (cardMatcher.find()) {
                String cardNumber = cardMatcher.group(1);
                if (isValidCreditCard(cardNumber)) {
                    findings.add(new Finding(
                            "CREDIT_CARD",
                            lineNumber,
                            cardMatcher.start(),
                            "critical",
                            "Valid credit card number detected - must be removed immediately"
                    ));
                }
            }
        }

        return findings;
    }

    /**
     * Redacts sensitive data in code
     *
     * @param code The code to redact
     * @return Code with sensitive data redacted
     */
    public String redact(String code) {
        if (code == null || code.isEmpty()) {
            return "";
        }

        String redacted = code;

        // Redact AWS keys (keep first 8 chars)
        redacted = redactPattern(redacted, AWS_ACCESS_KEY, 8);

        // Redact Stripe secret keys (keep prefix)
        redacted = redactPattern(redacted, STRIPE_SECRET_KEY, 8);

        // Redact Stripe publishable keys (keep prefix)
        redacted = redactPattern(redacted, STRIPE_PUBLISHABLE_KEY, 8);

        // Redact generic API keys (keep nothing from value)
        Matcher apiKeyMatcher = GENERIC_API_KEY.matcher(redacted);
        StringBuffer sb = new StringBuffer();
        while (apiKeyMatcher.find()) {
            String replacement = apiKeyMatcher.group(1) + " = \"***\"";
            apiKeyMatcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        apiKeyMatcher.appendTail(sb);
        redacted = sb.toString();

        // Redact passwords
        redacted = redactPasswordPattern(redacted, PASSWORD);
        redacted = redactPasswordPattern(redacted, PASSWORD_METHOD);

        // Redact JWT tokens (keep first 3 chars - "eyJ")
        redacted = redactPattern(redacted, JWT_TOKEN, 3);

        // Redact credit cards (keep last 4 digits)
        Matcher cardMatcher = CREDIT_CARD.matcher(redacted);
        sb = new StringBuffer();
        while (cardMatcher.find()) {
            String cardNumber = cardMatcher.group(1);
            if (isValidCreditCard(cardNumber)) {
                String last4 = cardNumber.substring(cardNumber.length() - 4);
                String replacement = "****" + last4;
                cardMatcher.appendReplacement(sb, replacement);
            } else {
                cardMatcher.appendReplacement(sb, cardMatcher.group(0));
            }
        }
        cardMatcher.appendTail(sb);
        redacted = sb.toString();

        return redacted;
    }

    /**
     * Helper method to find pattern matches and add findings
     */
    private void findPattern(String line, int lineNumber, Pattern pattern,
                             List<Finding> findings, String type, String severity, String message) {
        Matcher matcher = pattern.matcher(line);
        while (matcher.find()) {
            findings.add(new Finding(type, lineNumber, matcher.start(), severity, message));
        }
    }

    /**
     * Redacts a pattern while keeping a prefix
     */
    private String redactPattern(String text, Pattern pattern, int keepChars) {
        Matcher matcher = pattern.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String match = matcher.group();
            String prefix = match.length() > keepChars ? match.substring(0, keepChars) : "";
            String replacement = prefix + "***";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Redacts password patterns
     */
    private String redactPasswordPattern(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String prefix = matcher.group(1); // Keep the "password" keyword
            String fullMatch = matcher.group(0);
            String replacement;
            if (fullMatch.contains("(")) {
                // Method call: setPassword("...")
                replacement = "." + prefix + "(\"***\")";
            } else if (fullMatch.contains(",")) {
                // Map/list: "password", "..."
                replacement = prefix + ", \"***\"";
            } else {
                // Assignment: password = "..."
                replacement = prefix + " = \"***\"";
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Validates credit card number using Luhn algorithm
     * Helps prevent false positives from random numbers
     *
     * @param cardNumber The card number to validate
     * @return true if valid according to Luhn algorithm
     */
    private boolean isValidCreditCard(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 13 || cardNumber.length() > 19) {
            return false;
        }

        // Luhn algorithm
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
