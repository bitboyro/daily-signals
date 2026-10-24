# Daily Signals

A public radio experiment built progressively from a small Java service into a multi-language system. This first revision establishes a Java 21 Maven build and CLI entry point.

Build with `mvn -B -ntp verify`. Run the bootstrap shell with `java -cp radio-api/target/classes ro.bitboy.dailysignals.DailySignals`.

The repository history is intentionally authored as small, identifiable product changes so the companion optimizer experiment can replay exact revisions. The product does not require live news, voice, or cloud credentials for its reference checks.
