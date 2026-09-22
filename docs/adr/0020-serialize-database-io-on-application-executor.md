# ADR 0020 — Serialize database I/O on an application-scoped executor

- **Status:** Accepted
- **Date:** 2026-09-22

## Context

ADR 0019 introduced synchronous `SQLiteOpenHelper` and DAO operations. Calling them directly from
`QuizActivity` makes database opening, queries, transactions, and filesystem synchronisation part of
the main thread's event queue. Storage is usually fast but has unbounded latency under filesystem
and device contention. A delayed operation would therefore freeze input and drawing; a foreground
input-dispatch timeout becomes an Application Not Responding error after five seconds by default.

Answer writes have stronger ordering requirements than unrelated background tasks. A session row
must exist before its first answer, answer sequence numbers must reach SQLite in submission order,
and the final-answer transaction must not overtake an earlier answer. A configuration change may
destroy `QuizActivity` while any of those operations is running. Persistence should finish without
keeping that obsolete Activity or one of its Views reachable.

## Decision

Own one `ExecutorService` created by the application object and configured with a single worker.
`QuizHistoryStore` retains the application-context DAO and process-local session handles. A handle
accepts immutable answer drafts and lifecycle commands; it never accepts or retains an Activity,
View, or UI callback. One queue provides the required ordering without locks in Activity code.

Starting a session is asynchronous too. The Activity immediately receives a process-local token
and handle, so answers may be queued behind the pending insert. The token, original start time, and
generated database ID when available are saved as primitive instance state. After a configuration
change the new Activity reattaches to the existing handle. After process recreation, the stored ID
restores the row; if state was saved before that ID became available, the DAO atomically finds or
creates the session from its stable creation inputs.

Completing a session continues to insert the final answer and change the session state in the one
transaction established by ADR 0019. Explicit abandonment is another command on the same queue.
`Activity.onDestroy()` neither cancels the queue nor closes its DAO. Android terminates the worker
with the application process, so this design guarantees Activity-lifecycle independence but does
not claim durable execution after process death.

Install a `StrictMode.ThreadPolicy` from `Application.onCreate()` when the application is
debuggable. Detect main-thread disk reads and writes and log violations. Release builds do not pay
for this diagnostic policy.

## Alternatives considered

**`AsyncTask`** supplies background execution but was deprecated in API 30. Inner implementations
commonly retain an Activity, while its process-global serial executor gives unrelated work an
accidental shared queue.

**`HandlerThread`** can provide correct FIFO ordering when application-scoped, but requires manual
Looper, Handler, shutdown, and error handling. Current Android guidance prefers `Executor` unless a
Handler-based API specifically requires one.

**One `Thread` per write plus `runOnUiThread`** allows writes to overtake one another and encourages
callbacks that retain a destroyed Activity. Adding a blocking queue and lifecycle management would
reimplement an executor.

**WorkManager** persists scheduled work across process and device restarts, but adds a dependency,
its own durable scheduling database, retry policy, and potentially deferred execution for tiny
local transactions already initiated while the app is foreground. Use it only if a later
requirement demands guaranteed completion after process death.

**A service or `IntentService`** is unnecessary for short in-process writes. A normal service still
runs on the main thread unless it creates a worker, and `IntentService` is deprecated in API 30.

## Consequences

- Every quiz-history database access initiated by the quiz flow leaves the main thread.
- Session creation, answers, completion, and abandonment retain their user-action order.
- Destroying an Activity does not cancel a useful write or keep the Activity alive.
- The DAO has application-process lifetime rather than Activity lifetime.
- Debug logs expose future accidental main-thread disk access, including code outside quiz history.
- Asynchronous failures are recorded on the session handle and logged; a later persistence command
  fails rather than silently continuing from an invalid session identity.
- A process killed before queued work commits can still lose that work. SQLite transactions protect
  database integrity, but WorkManager would be needed for guaranteed eventual execution.
