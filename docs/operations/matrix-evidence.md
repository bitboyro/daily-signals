# Compatibility matrix evidence

Compatibility jobs answer whether the same behavior survives a supported environment change. A matrix cell is independent evidence; never treat one green cell as evidence for a skipped or failed variant.

## Current variants

| Check | Variants | Evidence |
| --- | --- | --- |
| `web-e2e` | mobile 390px, desktop 1440px | per-viewport JUnit and LCOV artifacts |
| `java-unit-matrix` | Temurin JDK 21, JDK 25 | per-JDK Surefire reports |
| `install-smoke-matrix` | Ubuntu, macOS | portable manifest, runtime-variable and mount checks |

The ordinary full jobs remain additive. The viewport matrix does not replace web unit or build checks; the JDK matrix does not replace integration jobs; and the OS matrix does not replace Linux container startup and post-deploy health.

## Reading failures

- Failure in every variant usually indicates shared code or configuration.
- Failure in one viewport points toward responsive markup, styling or client behavior.
- Failure in one JDK points toward runtime or toolchain compatibility even though bytecode targets Java 21.
- Failure on one operating system points toward paths, executable assumptions, line endings or platform tooling.

Download the uniquely named artifact for the failing cell and record its matrix values with the source revision. Reproduce the exact cell locally when that runtime is available; otherwise use the hosted log as provider evidence and do not relabel it as a code failure without a repeatable symptom.
