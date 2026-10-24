# Daily Signals

A public radio experiment built progressively from a small Java service into a multi-language system. The first checkpoint provides a Java 21 Maven build, a health endpoint, and deterministic fixture playback.

Build with `mvn -B -ntp verify`. Run locally with `java -cp radio-api/target/classes ro.bitboy.dailysignals.DailySignals`; the optional argument is a port (default `8151`). Try `GET /health` and `GET /editions/demo/audio`.

The repository history is intentionally authored as small, identifiable product changes so the companion optimizer experiment can replay exact revisions. The product does not require live news, voice, or cloud credentials for its reference checks.
