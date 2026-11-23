// The same scenario by CAPABILITY PASSING (chapter 3c): no tree, no kernel. An effect is an interface, the right
// to ask is a value in scope (a context parameter), a handler is an implementation passed in — and an
// interceptor is an implementation that wraps another. The rest of the program is the host's own stack.
// `scala-cli run code/scala --main-class book.capabilities`
package book

import scala.util.boundary

object caps:
  trait Bank:
    def balance(account: String): Long
    def move(from: String, to: String, amount: Long): Unit
  trait Clock:
    def now(): Long
  trait Failure:
    def refuse(why: String): Nothing

  /** the program: plain code; its signature lists what it may ask */
  def pay(from: String, to: String, amount: Long)(using bank: Bank, clock: Clock, failure: Failure): String =
    val have = bank.balance(from)
    if have < amount then failure.refuse(s"$from has $have, needs $amount")
    bank.move(from, to, amount)
    s"moved $amount from $from to $to at ${clock.now()}"

  final class InMemory(var balances: Map[String, Long]) extends Bank:
    def balance(a: String) = balances.getOrElse(a, 0L)
    def move(f: String, t: String, n: Long) =
      balances = balances.updated(f, balance(f) - n).updated(t, balance(t) + n)

  /** leaving early is the host's own non-local exit, delimited by this function */
  def refusals[A](body: Failure ?=> A): Either[String, A] =
    boundary[Either[String, A]]:
      Right(body(using new Failure { def refuse(why: String) = boundary.break(Left(why)) }))

  /** an interceptor is a wrapper: it sees what passes through IT */
  final class Journal(inner: Bank, var lines: Vector[String] = Vector.empty) extends Bank:
    def balance(a: String) = { val n = inner.balance(a); lines :+= s"Balance($a) -> $n"; n }
    def move(f: String, t: String, n: Long) = { inner.move(f, t, n); lines :+= s"Move($f,$t,$n) -> ()" }

  final class Limit(inner: Bank, max: Long)(using failure: Failure) extends Bank:
    def balance(a: String) = inner.balance(a)
    def move(f: String, t: String, n: Long) =
      if n > max then failure.refuse(s"a move of $n is over the limit of $max") else inner.move(f, t, n)

@main def capabilities(): Unit =
  import caps.*
  given Clock = () => 42L
  def show(r: Either[String, String]) = r.fold("refused: " + _, "paid: " + _)
  def balances(b: InMemory) = s"ann=${b.balance("ann")} bob=${b.balance("bob")}"

  val rich = InMemory(Map("ann" -> 100L))
  println("1 " + show(refusals(pay("ann", "bob", 30)(using rich))))
  println("1 balances: " + balances(rich))

  val poor = InMemory(Map("ann" -> 10L))
  println("2 " + show(refusals(pay("ann", "bob", 30)(using poor))))
  println("2 balances: " + balances(poor))

  val journal = Journal(InMemory(Map("ann" -> 100L)))
  refusals(pay("ann", "bob", 30)(using journal))
  journal.lines.foreach(l => println("3 journal: " + l))

  val limited = InMemory(Map("ann" -> 100L))
  println("4 " + show(refusals(pay("ann", "bob", 80)(using Limit(limited, 50)))))
  println("4 balances: " + balances(limited))

  val seen = Journal(InMemory(Map("ann" -> 10L)))
  refusals(pay("ann", "bob", 30)(using seen))
  println(s"5 left early: the journal has ${seen.lines.size} line, ${seen.lines.mkString("; ")}")

  println("6 twice: cannot: a capability's method returns once; the rest of the caller is the host's stack, " +
    "not a value")
