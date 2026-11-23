// THE LAWS, AS LINES (chapter 4), on the forty lines of kernel.scala: twelve observations that every rendering
// under code/ prints, compared with code/laws-expected.txt. Each line runs both sides of a law and prints what
// was seen; a rendering in which a law failed would print two different halves.
// `scala-cli run code/scala --main-class book.laws.laws`
package book.laws

import book.{Handler, Op, Prog, ask, done, handle, run}

// ---- the vocabulary of the chapter: a state, a failure, a clock, a choice

case object Get extends Op[Long]
case class Put(n: Long) extends Op[Unit]
case class Refuse(why: String) extends Op[Nothing]
case object Now extends Op[Long]
case class Choose(options: List[Long]) extends Op[Long]

// ---- its handlers. None has a variable: what a handler remembers is in what it returns

/** the state: a handled program is a function from the cell's content to the program that goes on from it */
final class Cell[A] extends Handler[A, Long => Prog[(A, Long)]]:
  def done(a: A) = Prog.Done(s => Prog.Done((a, s)))
  def on[X](op: Op[X], rest: X => Prog[Long => Prog[(A, Long)]]) = op match
    case Get    => Some(Prog.Done(s => rest(s).flatMap(_(s))))
    case Put(n) => Some(Prog.Done(_ => rest(()).flatMap(_(n))))
    case _      => None

/** `p under Cell(s)` */
def cell[A](s: Long)(p: Prog[A]): Prog[(A, Long)] = handle(Cell[A]())(p).flatMap(_(s))

final class Refusals[A] extends Handler[A, Either[String, A]]:
  def done(a: A) = Prog.Done(Right(a))
  def on[X](op: Op[X], rest: X => Prog[Either[String, A]]) = op match
    case Refuse(why) => Some(Prog.Done(Left(why)))
    case _           => None
def refusals[A](p: Prog[A]): Prog[Either[String, A]] = handle(Refusals[A]())(p)

final class FixedClock[A](now: Long) extends Handler[A, A]:
  def done(a: A) = Prog.Done(a)
  def on[X](op: Op[X], rest: X => Prog[A]) = op match
    case Now => Some(rest(now))
    case _   => None
def fixedClock[A](now: Long)(p: Prog[A]): Prog[A] = handle(FixedClock[A](now))(p)

/** every option taken: the rest once for each */
final class AllChoices[A] extends Handler[A, List[A]]:
  def done(a: A) = Prog.Done(List(a))
  def on[X](op: Op[X], rest: X => Prog[List[A]]) = op match
    case Choose(options) =>
      Some(options.foldLeft(Prog.Done(Nil): Prog[List[A]])((all, o) => all.flatMap(as => rest(o).map(as ++ _))))
    case _ => None
def allChoices[A](p: Prog[A]): Prog[List[A]] = handle(AllChoices[A]())(p)

/** the observer: every question of the state asked again, and written down with its answer */
final class Log[A] extends Handler[A, (Vector[String], A)]:
  def done(a: A) = Prog.Done((Vector.empty, a))
  def on[X](op: Op[X], rest: X => Prog[(Vector[String], A)]) =
    def noted(line: X => String) =
      Some(Prog.Ask(op, (x: X) => rest(x).map((later, a) => (line(x) +: later, a))))
    op match
      case Get    => noted(x => s"Get=$x")
      case Put(n) => noted(_ => s"Put($n)")
      case _      => None
def log[A](p: Prog[A]): Prog[(Vector[String], A)] = handle(Log[A]())(p)

// ---- the programs of the twelve lines

def put(n: Long): Prog[Long] = ask(Put(n)).map(_ => n)
val bump: Prog[Long] = for x <- ask(Get); _ <- ask(Put(x + 1)) yield x
val stopped: Prog[Long] = for _ <- ask(Put(1)); x <- ask(Get); _ <- ask(Refuse(s"stop at $x")); y <- put(9) yield y
val lost: Prog[Long] = for _ <- ask(Put(1)); _ <- ask(Refuse("no")) yield 0L
val timed: Prog[Long] = for t <- ask(Now); _ <- ask(Put(t)); x <- ask(Get) yield x + t
val forked: Prog[Long] =
  for _ <- ask(Put(1)); x <- ask(Choose(List(10, 20))); y <- ask(Get); _ <- ask(Put(y + x)); z <- ask(Get) yield z

def held[A](r: (A, Long)): String = s"${r._1}, cell ${r._2}"
def outcome[A](r: Either[String, A]): String = r.fold(why => s"refused($why)", a => s"paid($a)")

/** what a program over the state is seen to do: its questions with their answers, its value, the cell after */
def seen(p: Prog[Long]): String =
  val ((lines, a), s) = run(cell(0)(log(p)))
  s"${lines.mkString(" ")} => $a, cell $s"

@main def laws(): Unit =
  // 1-3. sequencing: `done` is a unit on each side, and grouping does not matter
  println(s"1 done then f: ${seen(done(5L).flatMap(put))} = ${seen(put(5))}")
  println(s"2 p then done: ${seen(bump.flatMap(done))} = ${seen(bump)}")
  val (f, g) = ((x: Long) => put(x + 1), (y: Long) => put(y * 2).map(_ => y))
  println(s"3 grouping: ${seen(ask(Get).flatMap(f).flatMap(g))} = ${seen(ask(Get).flatMap(x => f(x).flatMap(g)))}")

  // 4-5. a handler is a fold: what it makes of `done`, and of one program after another
  println(s"4 handling done: ${held(run(cell(3)(done(7))))} | ${outcome(run(refusals(done(7))))}")
  val whole = cell(0)(bump.flatMap(a => bump.map(a + _)))
  val pieces = cell(0)(bump).flatMap((a, s) => cell(s)(bump).map((b, s2) => (a + b, s2)))
  println(s"5 handling a sequence: ${held(run(whole))} = ${held(run(pieces))}")

  // 6. a handler sees every operation of its effect, in order, and nothing of another's
  val ((lines, r), s) = run(cell(0)(log(refusals(stopped))))
  println(s"6 its own and no other's: ${lines.mkString(" ")} => ${outcome(r)}, cell $s")

  // 7. a question nobody answers: the program has no value, and `run` says which question
  val unanswered = scala.util.Try(run(cell(0)(timed))).failed.map(_.getMessage.stripPrefix("nobody answers "))
  println(s"7 nobody answers: ${unanswered.getOrElse("it ran")}")

  // 8. two effects whose order is the meaning: is the cell part of what a refusal throws away?
  val inside = outcome(run(refusals(cell(0)(lost))).map(held))
  val (refused, after) = run(cell(0)(refusals(lost)))
  println(s"8 state then failure: $inside | failure then state: ${outcome(refused)}, cell $after")

  // 9. two handlers that only answer: either order
  println(s"9 two that only answer: ${held(run(cell(0)(fixedClock(7)(timed))))} = " +
    held(run(fixedClock(7)(cell(0)(timed)))))

  // 10-11. the rest resumed twice: a state handled inside the choice is each path's own, outside it is one
  println(s"10 twice, state inside: ${run(allChoices(cell(0)(forked))).map(held).mkString(" | ")}")
  val (paths, last) = run(cell(0)(allChoices(forked)))
  println(s"11 twice, state outside: ${paths.mkString(" | ")}, cell $last")

  // 12. what this handler owes its own operations: the four equations of a state
  val afterPut = run(cell(0)(ask(Put(3)).flatMap(_ => ask(Get))))._1
  val lastPut = run(cell(0)(ask(Put(3)).flatMap(_ => ask(Put(4))).flatMap(_ => ask(Get))))._1
  val (x, y) = run(cell(0)(for x <- ask(Get); y <- ask(Get) yield (x, y)))._1
  val putBack = run(cell(5)(ask(Get).flatMap(x => ask(Put(x)))))._2
  println(s"12 the cell's own: $afterPut $lastPut $x=$y cell $putBack")
