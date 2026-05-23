# Upgrade Plan: simulate (20260523190838)

- **Generated**: 2026-05-24
- **HEAD Branch**: main
- **HEAD Commit ID**: N/A

## Available Tools

**JDKs**
- Java 25.0.1: /opt/homebrew/Cellar/openjdk/25.0.1/libexec/openjdk.jdk/Contents/Home/bin (required for final validation)
- Java 17.0.17: /opt/homebrew/Cellar/openjdk@17/17.0.17/libexec/openjdk.jdk/Contents/Home/bin (baseline)

**Build Tools**
- Maven wrapper: 3.9.15 via `QCA/fes/.mvn/wrapper/maven-wrapper.properties` (verify compatibility with Java 25; may require upgrade to Maven 4.x if execution fails)
- Local Maven: 3.9.11 at /opt/homebrew/Cellar/maven/3.9.11/bin

## Guidelines
- Upgrade runtime from Java 17 to the latest LTS Java 25.
- Keep changes minimal and limited to the Java runtime configuration unless compatibility requires a focused build adjustment.
- Prefer the existing Maven wrapper `QCA/fes/mvnw` for build verification.
- Run the full test suite before and after the upgrade.

## Options
- Working branch: appmod/java-upgrade-20260523190838
- Run tests before and after the upgrade: true

## Upgrade Goals
- Upgrade Java runtime from 17 to 25

## Technology Stack
| Technology/Dependency | Current | Min Compatible | Why Incompatible |
| --------------------- | ------- | -------------- | ---------------- |
| Java runtime | 17 | 25 | User requested upgrade to latest LTS |
| Spring Boot parent | 3.5.14 | 3.5.14 | Current version should remain if Java 25 compatibility holds |
| Maven wrapper | 3.9.15 | 4.0.0+ | Maven 4.x is the safer compatibility baseline for Java 25; validate wrapper execution and upgrade if needed |
| spring-boot-maven-plugin | managed by Spring Boot 3.5.14 | managed by Spring Boot 3.5.14 | - |

## Derived Upgrades
- Primary derived upgrade is the runtime target: Java 25.
- No Spring Boot version upgrade is currently planned unless Java 25 compatibility issues are discovered during execution.
- Maven wrapper may require verification; if issues appear, evaluate a minimal wrapper upgrade.

## Key Challenges
- Confirming the Spring Boot 3.5.14 build path is compatible with JDK 25.
- Ensuring the Maven wrapper and compiler plugin can compile the existing code under Java 25.
- Addressing any Java 25-specific source or bytecode compatibility issues in application or test code.

## Upgrade Steps

- Step 1: Setup Environment
  - Rationale: Ensure all required JDKs and build tools are available before making configuration changes.
  - Changes to Make:
    - Confirm JDK 25 is available and usable.
    - Confirm `QCA/fes/mvnw` is executable and configured.
    - No installation is expected because required tools are already present.
  - Verification:
    - Command: `./QCA/fes/mvnw -q -version`
    - Expected: wrapper executes successfully and JDK 25 is available by environment configuration.

- Step 2: Setup Baseline
  - Rationale: Capture the current build and test status before the runtime upgrade.
  - Changes to Make:
    - Run baseline compile with current Java 17 configuration.
    - Run baseline tests with current Java 17 configuration.
  - Verification:
    - Command: `./QCA/fes/mvnw -q -DskipTests clean compile`
    - Expected: Baseline compile succeeds.
    - Command: `./QCA/fes/mvnw -q clean test`
    - Expected: Baseline test suite executes and results are recorded.

- Step 3: Upgrade Java Runtime
  - Rationale: Update the module runtime target to Java 25 and validate compile/test behavior.
  - Changes to Make:
    - Update `QCA/fes/pom.xml` property `java.version` from `17` to `25`.
    - Run Maven compile using JDK 25.
    - Fix any compilation or plugin issues caused by Java 25.
  - Verification:
    - Command: `./QCA/fes/mvnw -q clean test-compile`
    - Expected: Compilation succeeds with JDK 25.

- Step 4: Final Validation
  - Rationale: Confirm the completed upgrade meets the success criteria with a full test run.
  - Changes to Make:
    - Run the full test suite with JDK 25.
    - Fix any failing tests or compatibility issues.
  - Verification:
    - Command: `./QCA/fes/mvnw -q clean test`
    - Expected: All tests pass.
