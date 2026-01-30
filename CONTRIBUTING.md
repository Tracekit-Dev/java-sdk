# Contributing to TraceKit Java SDK

Thank you for considering contributing to the TraceKit Java SDK! This document provides guidelines and instructions for contributing.

## Table of Contents

- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [Building the Project](#building-the-project)
- [Running Tests](#running-tests)
- [Code Style](#code-style)
- [Making Changes](#making-changes)
- [Pull Request Process](#pull-request-process)
- [Project Structure](#project-structure)

## Getting Started

1. Fork the repository on GitHub
2. Clone your fork locally:
   ```bash
   git clone https://github.com/YOUR-USERNAME/tracekit-java-sdk.git
   cd tracekit-java-sdk
   ```
3. Add the upstream repository:
   ```bash
   git remote add upstream https://github.com/context-io/tracekit-java-sdk.git
   ```

## Development Setup

### Prerequisites

- **Java Development Kit (JDK)**: 11 or higher
- **Maven**: 3.6+ (or use the included Maven wrapper)
- **Git**: For version control
- **IDE** (optional but recommended):
  - IntelliJ IDEA (recommended)
  - Eclipse
  - VS Code with Java extensions

### IDE Setup

#### IntelliJ IDEA

1. Open the project: `File > Open` and select the `pom.xml` file
2. IntelliJ will automatically import the Maven project
3. Enable annotation processing: `Settings > Build, Execution, Deployment > Compiler > Annotation Processors`
4. Install recommended plugins:
   - Lombok (if using IntelliJ < 2020.3)
   - CheckStyle-IDEA (for code style validation)

#### Eclipse

1. Import as Maven project: `File > Import > Maven > Existing Maven Projects`
2. Select the root directory containing `pom.xml`
3. Eclipse will automatically configure the project

### Environment Variables

For running examples and tests, you may need to set:

```bash
export TRACEKIT_API_KEY=your-test-api-key
export TRACEKIT_ENDPOINT=https://api.tracekit.dev/v1/traces
```

## Building the Project

### Build All Modules

```bash
# Using Maven wrapper (recommended)
./mvnw clean install

# Or using system Maven
mvn clean install
```

### Build Specific Module

```bash
# Build only tracekit-core
mvn clean install -pl tracekit-core

# Build only tracekit-spring-boot
mvn clean install -pl tracekit-spring-boot
```

### Build Without Tests

```bash
mvn clean install -DskipTests
```

### Generate JavaDoc

```bash
mvn javadoc:javadoc
```

JavaDoc will be generated in `target/site/apidocs/` for each module.

## Running Tests

### Run All Tests

```bash
mvn test
```

### Run Tests for Specific Module

```bash
mvn test -pl tracekit-core
```

### Run Specific Test Class

```bash
mvn test -Dtest=TracekitSDKTest
```

### Run Specific Test Method

```bash
mvn test -Dtest=TracekitSDKTest#testInitialization
```

### Run Integration Tests

```bash
# Integration tests are marked with @Tag("integration")
mvn verify
```

### Test Coverage

Generate test coverage report using JaCoCo:

```bash
mvn clean verify
```

Coverage reports will be in `target/site/jacoco/index.html`.

## Code Style

### Java Code Style

This project follows the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html) with minor modifications:

- **Indentation**: 4 spaces (not 2)
- **Line Length**: 120 characters maximum
- **Import Order**:
  1. Static imports
  2. `java.*` and `javax.*`
  3. Third-party libraries
  4. Project imports

### Code Formatting

Format your code before committing:

```bash
# Using Maven formatter plugin (if configured)
mvn formatter:format

# Or configure your IDE to format on save
```

### Naming Conventions

- **Classes**: PascalCase (e.g., `TracekitSDK`, `TracekitConfig`)
- **Methods**: camelCase (e.g., `getServiceName()`, `buildConfiguration()`)
- **Constants**: UPPER_SNAKE_CASE (e.g., `DEFAULT_ENDPOINT`, `SDK_VERSION`)
- **Variables**: camelCase (e.g., `apiKey`, `serviceName`)

### JavaDoc Standards

All public classes and methods must have JavaDoc:

```java
/**
 * Brief description of what this class/method does.
 *
 * <p>Additional details if needed, including usage examples.</p>
 *
 * @param paramName description of parameter
 * @return description of return value
 * @throws ExceptionType when this exception is thrown
 */
public ReturnType methodName(ParamType paramName) throws ExceptionType {
    // implementation
}
```

### Code Quality Checks

Before submitting a PR, ensure:

1. **No compiler warnings**: Code compiles without warnings
2. **All tests pass**: `mvn test` returns success
3. **No checkstyle violations**: Follow code style guidelines
4. **Test coverage**: New code should have >80% test coverage
5. **JavaDoc complete**: All public APIs are documented

## Making Changes

### Branching Strategy

1. Create a feature branch from `main`:
   ```bash
   git checkout -b feature/your-feature-name
   ```

2. Use descriptive branch names:
   - `feature/add-kafka-support`
   - `bugfix/fix-null-pointer-in-config`
   - `docs/update-readme`
   - `refactor/improve-error-handling`

### Commit Messages

Follow the [Conventional Commits](https://www.conventionalcommits.org/) specification:

```
<type>(<scope>): <subject>

<body>

<footer>
```

**Types:**
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `test`: Adding or updating tests
- `refactor`: Code refactoring
- `perf`: Performance improvements
- `chore`: Build process or auxiliary tool changes

**Examples:**

```
feat(core): add support for custom span processors

Implement custom span processor configuration to allow users
to add their own processing logic before spans are exported.

Closes #123
```

```
fix(spring-boot): resolve null pointer in auto-configuration

Fix NPE when api-key is not configured by adding proper
validation in TracekitAutoConfiguration.

Fixes #456
```

### Writing Tests

1. **Unit Tests**: Test individual components in isolation
   ```java
   @Test
   void testConfigBuilderValidation() {
       assertThrows(IllegalArgumentException.class, () -> {
           TracekitConfig.builder().build();
       });
   }
   ```

2. **Integration Tests**: Test component interactions
   ```java
   @Test
   @Tag("integration")
   void testEndToEndTracing() {
       // Test full tracing pipeline
   }
   ```

3. **Use descriptive test names**: Test names should describe what is being tested
   ```java
   @Test
   void shouldThrowExceptionWhenApiKeyIsMissing() { }

   @Test
   void shouldDetectLocalUIWhenPortIsOpen() { }
   ```

4. **Follow AAA pattern**: Arrange, Act, Assert
   ```java
   @Test
   void shouldConfigureSDKWithValidParameters() {
       // Arrange
       TracekitConfig config = TracekitConfig.builder()
           .apiKey("test-key")
           .serviceName("test-service")
           .build();

       // Act
       TracekitSDK sdk = TracekitSDK.create(config);

       // Assert
       assertEquals("test-service", sdk.getServiceName());
   }
   ```

## Pull Request Process

### Before Submitting

1. **Update your branch** with the latest changes from upstream:
   ```bash
   git fetch upstream
   git rebase upstream/main
   ```

2. **Run all tests**:
   ```bash
   mvn clean verify
   ```

3. **Build successfully**:
   ```bash
   mvn clean install
   ```

4. **Update documentation** if needed:
   - Update README.md for new features
   - Add/update JavaDoc for public APIs
   - Update CHANGELOG.md (if it exists)

### Submitting the PR

1. Push your changes to your fork:
   ```bash
   git push origin feature/your-feature-name
   ```

2. Create a Pull Request on GitHub

3. Fill out the PR template with:
   - Clear description of changes
   - Motivation and context
   - Related issue numbers
   - Screenshots (if UI changes)
   - Checklist completion

### PR Review Process

1. **Automated checks**: CI/CD pipeline must pass
2. **Code review**: At least one maintainer approval required
3. **Discussion**: Address reviewer feedback promptly
4. **Squash commits**: May be required before merge

### After PR is Merged

1. Delete your feature branch:
   ```bash
   git branch -d feature/your-feature-name
   git push origin --delete feature/your-feature-name
   ```

2. Update your local main branch:
   ```bash
   git checkout main
   git pull upstream main
   ```

## Project Structure

```
tracekit-java-sdk/
├── tracekit-core/              # Core SDK module
│   ├── src/main/java/          # Main source code
│   ├── src/test/java/          # Unit tests
│   └── pom.xml                 # Module POM
├── tracekit-spring-boot/       # Spring Boot integration
│   ├── src/main/java/          # Auto-configuration
│   ├── src/test/java/          # Integration tests
│   └── pom.xml                 # Module POM
├── examples/                   # Example applications
│   └── spring-boot-example/    # Spring Boot demo
├── pom.xml                     # Parent POM
├── README.md                   # Project README
├── CONTRIBUTING.md             # This file
└── LICENSE                     # MIT License
```

### Key Packages

- `dev.tracekit`: Core SDK classes
- `dev.tracekit.local`: Local UI detection
- `dev.tracekit.security`: Security scanning
- `dev.tracekit.spring`: Spring Boot integration

## Questions?

If you have questions or need help:

1. Check existing [GitHub Issues](https://github.com/context-io/tracekit-java-sdk/issues)
2. Create a new issue with the `question` label
3. Email support@tracekit.dev

## License

By contributing, you agree that your contributions will be licensed under the MIT License.
