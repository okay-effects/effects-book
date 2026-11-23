// The twelve lines of code/laws-expected.txt by CAPABILITY PASSING (chapters 3c and 4): no tree. A handler is
// a function that runs a block with an implementation in scope; `p under h` is the block inside the call.
// Sequencing is the host's own, so lines 1 to 3 are laws of `val` and `;`, seen through the handlers.
// `scala-cli run code/scala --main-class book.laws.capabilities`
package book.laws

import scala.util.boundary

object caps:
  trait State:
    def get(): Long
    def put(n: Long): Unit
  trait Failure:
    def refuse(why: String): Nothing
  trait Clock:
    def now(): Long

  /** `body under Cell(s)`: the cell is a variable of this call, and what it holds at the end comes back */
  def cell[A](s: Long)(body: State ?=> A): (A, Long) =
    var held = s
    val a = body(using new State { def get() = held; def put(n: Long) = held = n })
    (a, held)

  def refusals[A](body: Failure ?=> A): Either[String, A] =
    boundary[Either[String, A]]:
      Right(body(using new Failure { def refuse(why: String) = boundary.break(Left(why)) }))

  def fixedClock[A](now: Long)(body: Clock ?=> A): A = body(using () => now)

  /** the observer is a wrapper around whichever state is outside it */
  def log[A](body: State ?=> A)(using outer: State): (Vector[String], A) =
    var lines = Vector.empty[String]
    val a = body(using new State {
      def get() = { val x = outer.get(); lines :+= s"Get=$x"; x }
      def put(n: Long) = { outer.put(n); lines :+= s"Put($n)" }
    })
    (lines, a)

  def put(n: Long)(using s: State): Long = { s.put(n); n }
  def bump(using s: State): Long = { val x = s.get(); s.put(x + 1); x }
  def stopped(using s: State, f: Failure): Long =
    s.put(1)
    val x = s.get()
    f.refuse(s"stop at $x")
    put(9)
  def lost(using s: State, f: Failure): Long = { s.put(1); f.refuse("no") }
  def timed(using s: State, c: Clock): Long = { val t = c.now(); s.put(t); s.get() + t }

  def seen(body: State ?=> Long): String =
    val ((lines, a), s) = cell(0)(log(body))
    s"${lines.mkString(" ")} => $a, cell $s"

@main def capabilities(): Unit =
  import caps.*
  println(s"1 done then f: ${seen { val x = 5L; put(x) }} = ${seen(put(5))}")
  println(s"2 p then done: ${seen { val x = bump; x }} = ${seen(bump)}")
  def f(x: Long)(using State) = put(x + 1)
  def g(y: Long)(using State) = { put(y * 2); y }
  def get(using s: State) = s.get()
  println(s"3 grouping: ${seen { val y = { val x = get; f(x) }; g(y) }} = ${seen { val x = get; g(f(x)) }}")

  println(s"4 handling done: ${held(cell(3)(7))} | ${outcome(refusals(7))}")
  val whole = cell(0) { val a = bump; val b = bump; a + b }
  val pieces = { val (a, s) = cell(0)(bump); val (b, s2) = cell(s)(bump); (a + b, s2) }
  println(s"5 handling a sequence: ${held(whole)} = ${held(pieces)}")

  val ((lines, r), s) = cell(0)(log(refusals(stopped)))
  println(s"6 its own and no other's: ${lines.mkString(" ")} => ${outcome(r)}, cell $s")

  // 7. the program with no clock in scope is not a program: the compiler is asked, here, while compiling this
  val errors = scala.compiletime.testing.typeCheckErrors("cell(0)(timed)")
  assert(errors.nonEmpty && errors.head.message.contains("Clock"), errors)
  assert(scala.compiletime.testing.typeCheckErrors("cell(0)(fixedClock(7)(timed))").isEmpty)
  println("7 nobody answers: does not compile: no given Clock is in scope for timed")

  // 8. the order is the nesting of the calls: a refusal leaves through `cell`, or stops inside it
  val (refused, after) = cell(0)(refusals(lost))
  println(s"8 state then failure: ${outcome(refusals(cell(0)(lost)).map(held))} | " +
    s"failure then state: ${outcome(refused)}, cell $after")

  println(s"9 two that only answer: ${held(cell(0)(fixedClock(7)(timed)))} = " +
    held(fixedClock(7)(cell(0)(timed))))

  val why = "cannot: a capability's method returns once; the rest of the caller is the host's stack"
  println(s"10 twice, state inside: $why")
  println(s"11 twice, state outside: $why")

  val (x, y) = cell(0) { (get, get) }._1
  println(s"12 the cell's own: ${cell(0) { put(3); get }._1} ${cell(0) { put(3); put(4); get }._1} $x=$y " +
    s"cell ${cell(5) { val x = get; put(x) }._2}")
