// The scenario of code/expected.txt in Kotlin, on the language's own `suspend` (chapters 3c and 3e): an effect
// is an interface of suspend functions, the program takes it as a receiver, and the compiler turns every
// suspend function into a state machine whose continuation is an object. A handler that needs the rest takes
// that object. Only the standard library is used; a continuation is resumed once.
// `kotlinc code/kotlin/bank.kt -include-runtime -d /tmp/bank-kt.jar && java -jar /tmp/bank-kt.jar`

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.createCoroutineUnintercepted
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface Bank {
    suspend fun balance(account: String): Long
    suspend fun move(from: String, to: String, amount: Long)
}

interface Clock {
    suspend fun now(): Long
}

interface Failure {
    suspend fun refuse(why: String): Nothing
}

/** what a program is run with: its row, as one interface */
interface World : Bank, Clock, Failure

/** the program: plain Kotlin; `suspend` marks that it may stop */
suspend fun World.pay(from: String, to: String, amount: Long): String {
    val have = balance(from)
    if (have < amount) refuse("$from has $have, needs $amount")
    move(from, to, amount)
    return "moved $amount from $from to $to at ${now()}"
}

class InMemory(ann: Long) : Bank {
    val balances = mutableMapOf("ann" to ann, "bob" to 0L)
    override suspend fun balance(account: String) = balances[account] ?: 0L
    override suspend fun move(from: String, to: String, amount: Long) {
        balances[from] = balance(from) - amount
        balances[to] = balance(to) + amount
    }
    override fun toString() = "ann=${balances["ann"]} bob=${balances["bob"]}"
}

/** an interceptor is a wrapper of the interface: answered in place, no rest taken */
class Journal(private val inner: Bank) : Bank {
    val lines = mutableListOf<String>()
    override suspend fun balance(account: String) =
        inner.balance(account).also { lines += "Balance($account) -> $it" }
    override suspend fun move(from: String, to: String, amount: Long) {
        inner.move(from, to, amount)
        lines += "Move($from,$to,$amount) -> ()"
    }
}

class Limit(private val inner: Bank, private val most: Long, private val failure: Failure) : Bank {
    override suspend fun balance(account: String) = inner.balance(account)
    override suspend fun move(from: String, to: String, amount: Long) =
        if (amount > most) failure.refuse("a move of $amount is over the limit of $most")
        else inner.move(from, to, amount)
}

/** thrown into a rest that is dropped, so that its `finally` blocks run */
class Abandoned : RuntimeException(null, null, false, false)

/** a handler that TAKES THE REST: at `refuse` the program's continuation is in hand, as an object. It is not
 * resumed with a value; it is resumed with `Abandoned`, the body unwinds, and the run ends refused */
fun run(bank: Bank, body: suspend World.() -> String): String {
    var outcome = ""
    var dropped: Continuation<Nothing>? = null
    lateinit var world: World
    val failure = object : Failure {
        override suspend fun refuse(why: String): Nothing =
            suspendCoroutineUninterceptedOrReturn { rest ->
                outcome = "refused: $why"
                dropped = rest
                COROUTINE_SUSPENDED
            }
    }
    world = object : World, Bank by bank, Failure by failure {
        override suspend fun now() = 42L
    }
    val end = object : Continuation<String> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(result: Result<String>) {
            result.onSuccess { outcome = "paid: $it" }
        }
    }
    body.createCoroutineUnintercepted(world, end).resume(Unit)
    dropped?.resumeWithException(Abandoned())
    return outcome
}

/** the same, for a bank that needs the failure it runs under */
fun runLimited(bank: Bank, most: Long, body: suspend World.() -> String): String {
    lateinit var limited: Limit
    return run(object : Bank {
        override suspend fun balance(account: String) = limited.balance(account)
        override suspend fun move(from: String, to: String, amount: Long) = limited.move(from, to, amount)
    }) {
        limited = Limit(bank, most, this)
        body()
    }
}

fun main() {
    val rich = InMemory(100)
    println("1 " + run(rich) { pay("ann", "bob", 30) })
    println("1 balances: $rich")

    val poor = InMemory(10)
    println("2 " + run(poor) { pay("ann", "bob", 30) })
    println("2 balances: $poor")

    val journal = Journal(InMemory(100))
    run(journal) { pay("ann", "bob", 30) }
    journal.lines.forEach { println("3 journal: $it") }

    val limited = InMemory(100)
    println("4 " + runLimited(limited, 50) { pay("ann", "bob", 80) })
    println("4 balances: $limited")

    val seen = Journal(InMemory(10))
    run(seen) { pay("ann", "bob", 30) }
    println("5 left early: the journal has ${seen.lines.size} line, ${seen.lines.joinToString("; ")}")

    // 6. the attempt, made: the rest taken at `balance` and resumed a second time
    var rest: Continuation<Long>? = null
    val twice = object : Bank {
        override suspend fun balance(account: String): Long =
            suspendCoroutineUninterceptedOrReturn { rest = it; COROUTINE_SUSPENDED }
        override suspend fun move(from: String, to: String, amount: Long) {}
    }
    run(twice) { pay("ann", "bob", 30) }
    val taken = rest!!
    taken.resume(100)
    try {
        taken.resume(10) // the state machine has moved on: on this JVM it fails inside the coroutine machinery
        println("6 twice: the second resumption was not refused")
    } catch (e: RuntimeException) {
        println("6 twice: cannot: the rest is a state machine object that moves on; resumed a second time it fails")
    }
}
