# Instructions for coding agents

Hyperfoil is a load generator. Its own overhead distorts what it measures, so performance and
allocation discipline matter more here than in most projects. Keep this file short: every rule
should be one that a reviewer had to point out at least once.

## Build

- The project targets JDK 21 (`maven.compiler.release=21`) and also builds on newer JDKs.
- Annotation processors are declared in the root `pom.xml` (`annotationProcessorPaths`), because JDK 23+
  no longer picks them up from the classpath. A new processor must be added there.
- Build without tests: `mvn install -pl api,core,http -DskipTests`
- Single test: `mvn test -pl <module> -am -Dtest=<TestClass> -Dsurefire.failIfNoSpecifiedTests=false`
- Before pushing: `mvn formatter:validate impsort:check`
- Keep commit messages to a short single-line subject.

## Hot path: do not allocate

The hot path is everything that runs per request, per response chunk or per session event: steps,
handlers, parsers, `Statistics`, connections, request timeouts.

- No allocation there: no capturing lambdas, boxing, `String.split`, regex, streams, varargs,
  iterators over collections, `String` building, or a scheduled task per request.
- Pre-allocate when the benchmark is built or when the session is created, and reuse
  (pools, `maxSessions`, mutable objects reset between uses).
- Allocating while parsing configuration or building the benchmark is fine.
- A change on the hot path comes with allocated bytes per request before and after:
  JFR `jdk.ThreadAllocationStatistics` or async-profiler `-e alloc` on a fixed workload, at least two
  runs each, measured on the event-loop threads (not the whole JVM). Put the numbers in the PR.

## Threading

- A session is pinned to one Netty event loop. Session state, requests, connections and connection
  pools are not thread-safe.
- Touch them only from that event loop. From any other thread, go through `session.executor().execute(...)`.
- Make the invariant explicit with `assert executor.inEventLoop()`. Surefire runs with assertions enabled,
  so tests catch a violation; production launch scripts don't pass `-ea`, so the assert is a no-op there.
  This is intentional, not a gap to flag or fix: checking thread affinity on the hot path in production
  would itself be the kind of per-call overhead this project avoids. Don't describe a bare `assert` as
  "missing production enforcement" — that's the expected, accepted tradeoff here.
- An `AssertionError` thrown inside a Netty handler is usually logged, not propagated, so the test can
  still pass. After a test run, check `<module>/target/surefire-reports/*-output.txt` of every tested
  module for `AssertionError`.

## Tests

- A bug fix comes with a test that fails without the fix. Revert the fix, run the test, see it fail,
  and say so in the PR.
- Put the test in the module that owns the code. Use `test-suite` only when the whole distribution
  is needed.
- HTTP behaviour: extend `BaseHttpScenarioTest` (embedded Vert.x server); override `threads()` to return
  `1` for deterministic tests.
- No sleeps or timing assertions that depend on machine speed.
- `test-suite` runs can write multi-GB logs (`target/log.txt`); mind disk space, especially on tmpfs.

## Configuration surface

- An option a user can set must be settable from YAML, not only from the Java builder.
- Reference docs are generated from builder Javadoc: write the Javadoc.
- The ergonomics section of the schema is maintained by hand. A new HTTP ergonomics property touches:
  1. `http/src/main/java/io/hyperfoil/http/config/HttpErgonomics.java` (field, getter, setter with Javadoc)
  2. `http/src/main/java/io/hyperfoil/http/config/HttpPlugin.java` (register in `enhanceErgonomics()`)
  3. `distribution/src/main/resources/schema.json`
  4. `docs/site/static/schema.json`
  5. `docs/site/content/en/docs/user-guide/benchmark/ergonomics.md`

## Modules

- Do not add a dependency between modules without a reason in the PR. Check that it doesn't
  re-introduce a dependency that was removed on purpose (e.g. `clustering` must not depend on `cli`).

## CLI

- Exit with a non-zero status on failure.
- Never silently rename or overwrite a path the user supplied.

## Pull requests

- One concern per PR. An unrelated fix found on the way goes in its own PR.
- The description states the problem, how it was reproduced, and the evidence (test output,
  before/after numbers). Label anything inferred rather than measured.
