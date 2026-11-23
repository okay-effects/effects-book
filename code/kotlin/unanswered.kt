// MEANT NOT TO COMPILE (chapter 4, line 7 of code/laws-expected.txt for laws.kt): the program `timed` under
// cell(0) and no handler for the clock. `timed` names the clock as a context parameter, and no block around
// the call gives one:
//   error: no context argument for 'c: Clock' found.
// `kotlinc code/kotlin/unanswered.kt -d <tmp>` must fail. With `fixedClock(7) { ... }` around `timed()`, as
// on line 9 of laws.kt, it compiles.

interface State {
    suspend fun get(): Long
    suspend fun put(n: Long)
}

interface Clock {
    suspend fun now(): Long
}

suspend fun <A> cell(s: Long, body: suspend State.() -> A): Pair<A, Long> {
    var content = s
    val a = object : State {
        override suspend fun get() = content
        override suspend fun put(n: Long) { content = n }
    }.body()
    return a to content
}

context(s: State, c: Clock)
suspend fun timed(): Long {
    val t = c.now()
    s.put(t)
    return s.get() + t
}

suspend fun unanswered(): Pair<Long, Long> = cell(0) { timed() }
