// The laws of code/laws-expected.txt in Kotlin, on the language's own `suspend`: the technique of bank.kt
// (chapters 3c and 3e), and twelve observations on it (chapter 4). An effect is an interface of suspend
// functions, a program names the effects it needs as context parameters, and a handler is a function that
// runs a block with its effect in scope: `p under h` is the block of `h`, so the order of handlers is the
// nesting of blocks. A handler that needs the rest takes the continuation, an object resumed once.
// `kotlinc code/kotlin/laws.kt -include-runtime -d /tmp/laws-kt.jar && java -jar /tmp/laws-kt.jar`

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.createCoroutineUnintercepted
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

interface State {
    suspend fun get(): Long
    suspend fun put(n: Long)
}

interface Failure {
    suspend fun refuse(why: String): Nothing
}

interface Clock {
    suspend fun now(): Long
}

interface Choice {
    suspend fun choose(options: List<Long>): Long
}

// ---- the handlers of the chapter

/** the state: answered in place from a variable, which comes back beside the block's value */
suspend fun <A> cell(s: Long, body: suspend State.() -> A): Pair<A, Long> {
    var content = s
    val a = object : State {
        override suspend fun get() = content
        override suspend fun put(n: Long) { content = n }
    }.body()
    return a to content
}

suspend fun <A> fixedClock(t: Long, body: suspend Clock.() -> A): A =
    object : Clock {
        override suspend fun now() = t
    }.body()

/** the observer, an interceptor: a wrapper of the state outside it, every question asked again and written
 * down with its answer */
context(outer: State)
suspend fun <A> log(body: suspend State.() -> A): Pair<List<String>, A> {
    val lines = mutableListOf<String>()
    val a = object : State {
        override suspend fun get() = outer.get().also { lines += "Get=$it" }
        override suspend fun put(n: Long) = outer.put(n).also { lines += "Put($n)" }
    }.body()
    return lines to a
}

sealed interface Outcome<out A>
data class Paid<A>(val value: A) : Outcome<A>
data class Refused(val why: String) : Outcome<Nothing>

/** thrown into a rest that is dropped, so that its `finally` blocks run */
class Abandoned : RuntimeException(null, null, false, false)

/** a handler that TAKES THE REST. The block runs as a coroutine of its own, so the rest that `refuse` takes
 * ends where this handler stands: the rest is dropped, and whoever is outside goes on with `Refused` */
suspend fun <A> refusals(body: suspend Failure.() -> A): Outcome<A> = suspendCoroutine { outside ->
    var refused: Refused? = null
    var dropped: Continuation<Nothing>? = null
    val failure = object : Failure {
        override suspend fun refuse(why: String): Nothing =
            suspendCoroutineUninterceptedOrReturn { rest ->
                refused = Refused(why)
                dropped = rest
                COROUTINE_SUSPENDED
            }
    }
    val end = object : Continuation<A> {
        override val context = outside.context
        override fun resumeWith(result: Result<A>) {
            result.fold({ outside.resume(Paid(it)) }, { if (it !is Abandoned) outside.resumeWithException(it) })
        }
    }
    body.createCoroutineUnintercepted(failure, end).resume(Unit)
    dropped?.resumeWithException(Abandoned())
    refused?.let { outside.resume(it) }
}

/** the attempt at many resumptions: the rest taken at `choose` and resumed once for each option */
suspend fun <A> allChoices(body: suspend Choice.() -> A): List<A> = suspendCoroutine { outside ->
    val all = mutableListOf<A>()
    var offered = listOf<Long>()
    var taken: Continuation<Long>? = null
    val choice = object : Choice {
        override suspend fun choose(options: List<Long>): Long =
            suspendCoroutineUninterceptedOrReturn { rest ->
                offered = options
                taken = rest
                COROUTINE_SUSPENDED
            }
    }
    val end = object : Continuation<A> {
        override val context = outside.context
        override fun resumeWith(result: Result<A>) { all += result.getOrThrow() }
    }
    body.createCoroutineUnintercepted(choice, end).resume(Unit)
    val rest = taken
    if (rest != null) for (option in offered) rest.resume(option)
    outside.resume(all)
}

/** running: a block with no question left in its context has a value */
fun <A> run(body: suspend () -> A): A {
    var value: Result<A>? = null
    val end = object : Continuation<A> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(result: Result<A>) { value = result }
    }
    body.createCoroutineUnintercepted(end).resume(Unit)
    return value!!.getOrThrow()
}

// ---- the programs of the twelve lines: plain Kotlin; `suspend` marks that they may stop

context(s: State)
suspend fun set(n: Long): Long {
    s.put(n)
    return n
}

context(s: State)
suspend fun bump(): Long {
    val x = s.get()
    s.put(x + 1)
    return x
}

context(s: State, f: Failure)
suspend fun stopped(): Long {
    s.put(1)
    val x = s.get()
    f.refuse("stop at $x")
    return set(9)
}

context(s: State, f: Failure)
suspend fun lost(): Long {
    s.put(1)
    f.refuse("no")
}

context(s: State, c: Clock)
suspend fun timed(): Long {
    val t = c.now()
    s.put(t)
    return s.get() + t
}

context(s: State, c: Choice)
suspend fun forked(): Long {
    s.put(1)
    val x = c.choose(listOf(10, 20))
    s.put(s.get() + x)
    return s.get()
}

fun <A> held(r: Pair<A, Long>) = "${r.first}, cell ${r.second}"
fun <A> outcome(r: Outcome<A>, shown: (A) -> String = { "$it" }) = when (r) {
    is Paid -> "paid(${shown(r.value)})"
    is Refused -> "refused(${r.why})"
}

/** what a program over the state is seen to do: its questions with their answers, its value, the cell after */
fun seen(p: suspend State.() -> Long): String {
    val (logged, s) = run { cell(0) { log(p) } }
    return "${logged.first.joinToString(" ")} => ${logged.second}, cell $s"
}

fun main() {
    // 1-3. sequencing: `done` is a value in hand, "then" is the next statement, grouping is where a call ends
    println("1 done then f: ${seen { val x = 5L; set(x) }} = ${seen { set(5) }}")
    println("2 p then done: ${seen { val x = bump(); x }} = ${seen { bump() }}")
    val f: suspend State.(Long) -> Long = { x -> set(x + 1) }
    val g: suspend State.(Long) -> Long = { y -> put(y * 2); y }
    val getThenF: suspend State.() -> Long = { f(get()) }
    val fThenG: suspend State.(Long) -> Long = { x -> g(f(x)) }
    println("3 grouping: ${seen { g(getThenF()) }} = ${seen { val x = get(); fThenG(x) }}")

    // 4-5. a handler is a fold: what it makes of a value, and of one program after another
    println("4 handling done: ${held(run { cell(3) { 7 } })} | ${outcome(run { refusals { 7 } })}")
    val whole = run { cell(0) { bump() + bump() } }
    val pieces = run {
        val (a, s) = cell(0) { bump() }
        val (b, s2) = cell(s) { bump() }
        a + b to s2
    }
    println("5 handling a sequence: ${held(whole)} = ${held(pieces)}")

    // 6. a handler sees every operation of its effect, in order, and nothing of another's
    val (logged, s) = run { cell(0) { log { refusals { stopped() } } } }
    println("6 its own and no other's: ${logged.first.joinToString(" ")} => ${outcome(logged.second)}, cell $s")

    // 7. a question nobody answers: `run { cell(0) { timed() } }` is not a program. The compiler stops at
    // `timed()`: "no context argument for 'c: Clock' found"
    println("7 nobody answers: does not compile: timed needs a Clock in its context and no block gives one")

    // 8. two effects whose order is the meaning: is the cell part of what a refusal throws away?
    val inside = outcome(run { refusals { cell(0) { lost() } } }, ::held)
    val (refused, after) = run { cell(0) { refusals { lost() } } }
    println("8 state then failure: $inside | failure then state: ${outcome(refused)}, cell $after")

    // 9. two handlers that only answer: either order
    println("9 two that only answer: ${held(run { cell(0) { fixedClock(7) { timed() } } })} = " +
        held(run { fixedClock(7) { cell(0) { timed() } } }))

    // 10-11. the rest resumed twice: the attempt, made. The first path runs to its end; resumed again, the
    // state machine is entered at its last state and not at the choice, and on this JVM it then fails inside
    // the coroutine machinery
    val once = "cannot: the rest is a state machine object that moves on; resumed a second time it fails"
    try {
        val paths = run { allChoices { cell(0) { forked() } } }
        println("10 twice, state inside: ${paths.joinToString(" | ", transform = ::held)}")
    } catch (e: RuntimeException) {
        println("10 twice, state inside: $once")
    }
    try {
        val (paths, last) = run { cell(0) { allChoices { forked() } } }
        println("11 twice, state outside: ${paths.joinToString(" | ")}, cell $last")
    } catch (e: RuntimeException) {
        println("11 twice, state outside: $once")
    }

    // 12. what this handler owes its own operations: the four equations of a state
    val afterPut = run { cell(0) { put(3); get() } }.first
    val lastPut = run { cell(0) { put(3); put(4); get() } }.first
    val (x, y) = run { cell(0) { get() to get() } }.first
    val putBack = run { cell(5) { put(get()) } }.second
    println("12 the cell's own: $afterPut $lastPut $x=$y cell $putBack")
}
