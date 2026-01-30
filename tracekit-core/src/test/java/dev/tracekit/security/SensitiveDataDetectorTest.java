package dev.tracekit.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for SensitiveDataDetector
 * Tests detection and redaction of sensitive data in code
 */
@DisplayName("SensitiveDataDetector Tests")
class SensitiveDataDetectorTest {

    private SensitiveDataDetector detector;

    @BeforeEach
    void setUp() {
        detector = new SensitiveDataDetector();
    }

    // ===== API Key Detection Tests =====

    @Test
    @DisplayName("Should detect AWS access key")
    void shouldDetectAwsAccessKey() {
        String code = "String awsKey = \"AKIAIOSFODNN7EXAMPLE\";";
        List<SensitiveDataDetector.Finding> findings = detector.scan(code);

        assertFalse(findings.isEmpty(), "Should detect AWS access key");
        assertEquals(1, findings.size());

        SensitiveDataDetector.Finding finding = findings.get(0);
        assertEquals("AWS_ACCESS_KEY", finding.getType());
        assertEquals("critical", finding.getSeverity());
        assertEquals(1, finding.getLine());
        assertTrue(finding.getMessage().contains("AWS"));
    }

    @Test
    @DisplayName("Should detect Stripe secret key")
    void shouldDetectStripeSecretKey() {
        String code = "stripe.setApiKey(\"sk_live_51H3qI2Abc123xyz456789\");";
        List<SensitiveDataDetector.Finding> findings = detector.scan(code);

        assertFalse(findings.isEmpty(), "Should detect Stripe secret key");
        assertEquals(1, findings.size());

        SensitiveDataDetector.Finding finding = findings.get(0);
        assertEquals("STRIPE_SECRET_KEY", finding.getType());
        assertEquals("critical", finding.getSeverity());
        assertTrue(finding.getMessage().contains("Stripe"));
    }

    @Test
    @DisplayName("Should detect Stripe publishable key")
    void shouldDetectStripePublishableKey() {
        String code = "const key = \"pk_live_51H3qI2Abc123xyz456789\";";
        List<SensitiveDataDetector.Finding> findings = detector.scan(code);

        assertFalse(findings.isEmpty(), "Should detect Stripe publishable key");
        assertEquals(1, findings.size());

        SensitiveDataDetector.Finding finding = findings.get(0);
        assertEquals("STRIPE_PUBLISHABLE_KEY", finding.getType());
        assertEquals("high", finding.getSeverity());
    }

    @Test
    @DisplayName("Should detect generic API key patterns")
    void shouldDetectGenericApiKey() {
        String code = "apiKey: \"abc123def456ghi789jkl012mno345pqr678stu901vwx234yz\";";
        List<SensitiveDataDetector.Finding> findings = detector.scan(code);

        assertFalse(findings.isEmpty(), "Should detect generic API key");
        SensitiveDataDetector.Finding finding = findings.get(0);
        assertEquals("API_KEY", finding.getType());
        assertEquals("high", finding.getSeverity());
    }

    // ===== Password Detection Tests =====

    @Test
    @DisplayName("Should detect password in assignment")
    void shouldDetectPasswordAssignment() {
        String code = "String password = \"mySecretP@ssw0rd\";";
        List<SensitiveDataDetector.Finding> findings = detector.scan(code);

        assertFalse(findings.isEmpty(), "Should detect password");
        SensitiveDataDetector.Finding finding = findings.get(0);
        assertEquals("PASSWORD", finding.getType());
        assertEquals("critical", finding.getSeverity());
        assertTrue(finding.getMessage().contains("password"));
    }

    @Test
    @DisplayName("Should detect password in method call")
    void shouldDetectPasswordInMethodCall() {
        String code = "user.setPassword(\"SecurePass123!\");";
        List<SensitiveDataDetector.Finding> findings = detector.scan(code);

        assertFalse(findings.isEmpty(), "Should detect password in method");
        assertEquals("PASSWORD", findings.get(0).getType());
    }

    @Test
    @DisplayName("Should detect various password patterns")
    void shouldDetectVariousPasswordPatterns() {
        String code = "db.password = \"dbpass123\"\n" +
                "PASSWORD=\"EnvPassword\"\n" +
                "pwd: \"shortpwd\"\n";
        List<SensitiveDataDetector.Finding> findings = detector.scan(code);

        assertTrue(findings.size() >= 3, "Should detect multiple password patterns");
        assertTrue(findings.stream().allMatch(f -> f.getType().equals("PASSWORD")));
    }

    // ===== JWT Token Detection Tests =====

    @Test
    @DisplayName("Should detect JWT token")
    void shouldDetectJwtToken() {
        String code = "String token = \"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c\";";
        List<SensitiveDataDetector.Finding> findings = detector.scan(code);

        assertFalse(findings.isEmpty(), "Should detect JWT token");
        SensitiveDataDetector.Finding finding = findings.get(0);
        assertEquals("JWT_TOKEN", finding.getType());
        assertEquals("high", finding.getSeverity());
        assertTrue(finding.getMessage().contains("JWT"));
    }

    @Test
    @DisplayName("Should detect JWT in authorization header")
    void shouldDetectJwtInHeader() {
        String code = "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJ1c2VyIjoidGVzdCJ9.abcdef123456";
        List<SensitiveDataDetector.Finding> findings = detector.scan(code);

        assertFalse(findings.isEmpty(), "Should detect JWT in header");
        assertEquals("JWT_TOKEN", findings.get(0).getType());
    }

    // ===== Credit Card Detection Tests =====

    @Test
    @DisplayName("Should detect valid credit card with Luhn validation")
    void shouldDetectValidCreditCard() {
        String code = "String cardNumber = \"4532015112830366\";"; // Valid Visa test number
        List<SensitiveDataDetector.Finding> findings = detector.scan(code);

        assertFalse(findings.isEmpty(), "Should detect valid credit card");
        SensitiveDataDetector.Finding finding = findings.get(0);
        assertEquals("CREDIT_CARD", finding.getType());
        assertEquals("critical", finding.getSeverity());
        assertTrue(finding.getMessage().contains("credit card"));
    }

    @Test
    @DisplayName("Should detect Mastercard number")
    void shouldDetectMastercard() {
        String code = "card: \"5425233430109903\""; // Valid Mastercard test number
        List<SensitiveDataDetector.Finding> findings = detector.scan(code);

        assertFalse(findings.isEmpty(), "Should detect Mastercard");
        assertEquals("CREDIT_CARD", findings.get(0).getType());
    }

    @Test
    @DisplayName("Should NOT detect invalid credit card (fails Luhn)")
    void shouldNotDetectInvalidCreditCard() {
        String code = "String notCard = \"1234567890123456\";"; // Invalid Luhn checksum
        List<SensitiveDataDetector.Finding> findings = detector.scan(code);

        // Should either have no findings or no credit card findings
        assertTrue(findings.stream().noneMatch(f -> f.getType().equals("CREDIT_CARD")),
                "Should not detect invalid credit card number");
    }

    @Test
    @DisplayName("Should NOT detect numbers with spaces or dashes as credit cards")
    void shouldNotDetectFormattedNumbers() {
        String code = "phone: \"1234-5678-9012-3456\"\n" +
                "amount: \"1234 5678 9012\"\n";
        List<SensitiveDataDetector.Finding> findings = detector.scan(code);

        // These shouldn't be detected as credit cards due to formatting
        assertTrue(findings.stream().noneMatch(f -> f.getType().equals("CREDIT_CARD")),
                "Should not detect formatted numbers as credit cards");
    }

    // ===== False Positive Tests =====

    @Test
    @DisplayName("Should NOT detect normal strings")
    void shouldNotDetectNormalStrings() {
        String code = "String message = \"Hello World\";\n" +
                "String name = \"John Doe\";\n" +
                "int count = 42;\n";
        List<SensitiveDataDetector.Finding> findings = detector.scan(code);

        assertTrue(findings.isEmpty(), "Should not detect normal strings");
    }

    @Test
    @DisplayName("Should NOT detect short API-like strings")
    void shouldNotDetectShortStrings() {
        String code = "String id = \"abc123\";";
        List<SensitiveDataDetector.Finding> findings = detector.scan(code);

        assertTrue(findings.isEmpty(), "Should not detect short strings as API keys");
    }

    @Test
    @DisplayName("Should NOT detect random long numbers")
    void shouldNotDetectRandomNumbers() {
        String code = "long timestamp = 1234567890123L;";
        List<SensitiveDataDetector.Finding> findings = detector.scan(code);

        assertTrue(findings.isEmpty(), "Should not detect timestamps as credit cards");
    }

    @Test
    @DisplayName("Should NOT detect password-like variables without values")
    void shouldNotDetectPasswordVariables() {
        String code = "String passwordHash;\n" +
                "boolean passwordValid;\n" +
                "getPassword();\n";
        List<SensitiveDataDetector.Finding> findings = detector.scan(code);

        assertTrue(findings.isEmpty(), "Should not detect password variables without literal values");
    }

    // ===== Redaction Tests =====

    @Test
    @DisplayName("Should redact AWS key while keeping prefix")
    void shouldRedactAwsKey() {
        String code = "String awsKey = \"AKIAIOSFODNN7EXAMPLE\";";
        String redacted = detector.redact(code);

        assertNotEquals(code, redacted, "Code should be modified");
        assertTrue(redacted.contains("AKIA"), "Should keep prefix for context");
        assertTrue(redacted.contains("***"), "Should contain redaction marker");
        assertFalse(redacted.contains("AKIAIOSFODNN7EXAMPLE"), "Should not contain full key");
    }

    @Test
    @DisplayName("Should redact Stripe key")
    void shouldRedactStripeKey() {
        String code = "stripe.setApiKey(\"sk_live_51H3qI2Abc123xyz456789\");";
        String redacted = detector.redact(code);

        assertTrue(redacted.contains("sk_live_"), "Should keep prefix");
        assertTrue(redacted.contains("***"), "Should contain redaction marker");
        assertFalse(redacted.contains("51H3qI2Abc123xyz456789"), "Should not contain key suffix");
    }

    @Test
    @DisplayName("Should redact password")
    void shouldRedactPassword() {
        String code = "String password = \"mySecretP@ssw0rd\";";
        String redacted = detector.redact(code);

        assertTrue(redacted.contains("***"), "Should contain redaction marker");
        assertFalse(redacted.contains("mySecretP@ssw0rd"), "Should not contain actual password");
    }

    @Test
    @DisplayName("Should redact JWT token")
    void shouldRedactJwtToken() {
        String code = "String token = \"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c\";";
        String redacted = detector.redact(code);

        assertTrue(redacted.contains("eyJ"), "Should keep JWT prefix");
        assertTrue(redacted.contains("***"), "Should contain redaction marker");
        assertFalse(redacted.contains("SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"), "Should not contain signature");
    }

    @Test
    @DisplayName("Should redact credit card number")
    void shouldRedactCreditCard() {
        String code = "String cardNumber = \"4532015112830366\";";
        String redacted = detector.redact(code);

        assertTrue(redacted.contains("***"), "Should contain redaction marker");
        assertFalse(redacted.contains("4532015112830366"), "Should not contain full card number");
        // Optionally check if last 4 digits are preserved
        assertTrue(redacted.contains("0366") || redacted.contains("***"), "Should preserve last 4 or fully redact");
    }

    @Test
    @DisplayName("Should redact multiple sensitive items in same code")
    void shouldRedactMultipleItems() {
        String code = "String awsKey = \"AKIAIOSFODNN7EXAMPLE\";\n" +
                "String password = \"mySecret123\";\n" +
                "String card = \"4532015112830366\";\n";
        String redacted = detector.redact(code);

        assertFalse(redacted.contains("AKIAIOSFODNN7EXAMPLE"), "Should redact AWS key");
        assertFalse(redacted.contains("mySecret123"), "Should redact password");
        assertFalse(redacted.contains("4532015112830366"), "Should redact card");
        assertTrue(redacted.split("\\*\\*\\*").length > 1, "Should have multiple redactions");
    }

    // ===== Line Number and Column Tracking Tests =====

    @Test
    @DisplayName("Should track line numbers correctly")
    void shouldTrackLineNumbers() {
        String code = "String normalString = \"hello\";\n" +
                "String awsKey = \"AKIAIOSFODNN7EXAMPLE\";\n" +
                "int count = 42;\n" +
                "String password = \"secret123\";\n";
        List<SensitiveDataDetector.Finding> findings = detector.scan(code);

        assertFalse(findings.isEmpty(), "Should find sensitive data");

        // AWS key should be on line 2
        SensitiveDataDetector.Finding awsFinding = findings.stream()
                .filter(f -> f.getType().equals("AWS_ACCESS_KEY"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("AWS key not found"));
        assertEquals(2, awsFinding.getLine(), "AWS key should be on line 2");

        // Password should be on line 4
        SensitiveDataDetector.Finding passwordFinding = findings.stream()
                .filter(f -> f.getType().equals("PASSWORD"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Password not found"));
        assertEquals(4, passwordFinding.getLine(), "Password should be on line 4");
    }

    @Test
    @DisplayName("Should track column positions")
    void shouldTrackColumnPositions() {
        String code = "String awsKey = \"AKIAIOSFODNN7EXAMPLE\";";
        List<SensitiveDataDetector.Finding> findings = detector.scan(code);

        assertFalse(findings.isEmpty(), "Should find AWS key");
        SensitiveDataDetector.Finding finding = findings.get(0);

        assertTrue(finding.getColumn() >= 0, "Column should be set");
        assertTrue(finding.getColumn() < code.length(), "Column should be within string bounds");
    }

    // ===== Multiple Findings Tests =====

    @Test
    @DisplayName("Should detect multiple findings in same line")
    void shouldDetectMultipleFindingsInSameLine() {
        String code = "config.set(awsKey=\"AKIAIOSFODNN7EXAMPLE\", password=\"secret123\");";
        List<SensitiveDataDetector.Finding> findings = detector.scan(code);

        assertTrue(findings.size() >= 2, "Should detect both AWS key and password");

        boolean hasAwsKey = findings.stream().anyMatch(f -> f.getType().equals("AWS_ACCESS_KEY"));
        boolean hasPassword = findings.stream().anyMatch(f -> f.getType().equals("PASSWORD"));

        assertTrue(hasAwsKey, "Should detect AWS key");
        assertTrue(hasPassword, "Should detect password");
    }

    @Test
    @DisplayName("Should detect all findings in code block")
    void shouldDetectAllFindingsInCodeBlock() {
        String code = "public class Config {\n" +
                "    private static final String AWS_KEY = \"AKIAIOSFODNN7EXAMPLE\";\n" +
                "    private static final String STRIPE_KEY = \"sk_live_51H3qI2Abc123\";\n" +
                "    private static final String PASSWORD = \"admin123\";\n" +
                "    private static final String JWT = \"eyJhbGciOiJIUzI1NiJ9.eyJ1c2VyIjoidGVzdCJ9.abc\";\n" +
                "    private static final String CARD = \"4532015112830366\";\n" +
                "}\n";
        List<SensitiveDataDetector.Finding> findings = detector.scan(code);

        assertTrue(findings.size() >= 5, "Should detect at least 5 different sensitive items");

        assertTrue(findings.stream().anyMatch(f -> f.getType().equals("AWS_ACCESS_KEY")));
        assertTrue(findings.stream().anyMatch(f -> f.getType().equals("STRIPE_SECRET_KEY")));
        assertTrue(findings.stream().anyMatch(f -> f.getType().equals("PASSWORD")));
        assertTrue(findings.stream().anyMatch(f -> f.getType().equals("JWT_TOKEN")));
        assertTrue(findings.stream().anyMatch(f -> f.getType().equals("CREDIT_CARD")));
    }

    // ===== Severity Level Tests =====

    @Test
    @DisplayName("Should assign correct severity levels")
    void shouldAssignCorrectSeverityLevels() {
        String code = "String awsKey = \"AKIAIOSFODNN7EXAMPLE\";\n" +
                "String stripeKey = \"pk_live_abc123\";\n" +
                "String jwt = \"eyJhbGciOiJIUzI1NiJ9.eyJ1c2VyIjoidGVzdCJ9.abc\";\n";
        List<SensitiveDataDetector.Finding> findings = detector.scan(code);

        // AWS key should be critical
        SensitiveDataDetector.Finding awsFinding = findings.stream()
                .filter(f -> f.getType().equals("AWS_ACCESS_KEY"))
                .findFirst()
                .orElseThrow();
        assertEquals("critical", awsFinding.getSeverity());

        // JWT should be high
        SensitiveDataDetector.Finding jwtFinding = findings.stream()
                .filter(f -> f.getType().equals("JWT_TOKEN"))
                .findFirst()
                .orElseThrow();
        assertEquals("high", jwtFinding.getSeverity());
    }

    // ===== Edge Cases =====

    @Test
    @DisplayName("Should handle empty string")
    void shouldHandleEmptyString() {
        String code = "";
        List<SensitiveDataDetector.Finding> findings = detector.scan(code);
        assertTrue(findings.isEmpty());

        String redacted = detector.redact(code);
        assertEquals("", redacted);
    }

    @Test
    @DisplayName("Should handle null string")
    void shouldHandleNullString() {
        assertDoesNotThrow(() -> {
            List<SensitiveDataDetector.Finding> findings = detector.scan(null);
            assertTrue(findings.isEmpty());
        });

        assertDoesNotThrow(() -> {
            String redacted = detector.redact(null);
            assertEquals("", redacted);
        });
    }

    @Test
    @DisplayName("Should handle very long code")
    void shouldHandleVeryLongCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append("String var").append(i).append(" = \"normalValue\";\n");
        }
        sb.append("String awsKey = \"AKIAIOSFODNN7EXAMPLE\";\n");

        String code = sb.toString();
        List<SensitiveDataDetector.Finding> findings = detector.scan(code);

        assertEquals(1, findings.size(), "Should find the one sensitive item");
        assertEquals(1001, findings.get(0).getLine(), "Should correctly identify line 1001");
    }

    // ===== Finding Model Tests =====

    @Test
    @DisplayName("Finding should have all required fields")
    void findingShouldHaveAllFields() {
        String code = "String awsKey = \"AKIAIOSFODNN7EXAMPLE\";";
        List<SensitiveDataDetector.Finding> findings = detector.scan(code);

        assertFalse(findings.isEmpty());
        SensitiveDataDetector.Finding finding = findings.get(0);

        assertNotNull(finding.getType(), "Type should not be null");
        assertNotNull(finding.getSeverity(), "Severity should not be null");
        assertNotNull(finding.getMessage(), "Message should not be null");
        assertTrue(finding.getLine() > 0, "Line should be positive");
        assertTrue(finding.getColumn() >= 0, "Column should be non-negative");
    }

    @Test
    @DisplayName("Finding should have meaningful messages")
    void findingShouldHaveMeaningfulMessages() {
        String code = "String awsKey = \"AKIAIOSFODNN7EXAMPLE\";\n" +
                "String password = \"secret123\";\n";
        List<SensitiveDataDetector.Finding> findings = detector.scan(code);

        for (SensitiveDataDetector.Finding finding : findings) {
            assertFalse(finding.getMessage().isEmpty(), "Message should not be empty");
            assertTrue(finding.getMessage().length() > 10, "Message should be descriptive");
        }
    }
}
