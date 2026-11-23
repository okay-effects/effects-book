// The examples of chapter 8 (a catalogue of standard effects), run and asserted:
// `scala-cli run code/scala --main-class book.catalogue`. Nothing here but the forty lines of kernel.scala,
// six names of examples.scala (`Refuse`, `Refusals`, `Now`, `FixedClock`, `Choose`, `AllChoices`) and two of
// continuation.scala (`Emit`, `Collected`). Everything else is in the object `std`, because the package
// already has a `take`, a `Journal` and a `Recording` that belong to other chapters.
package book

import scala.util.Try

object std:

  // ---- 0. a handler that CARRIES something along. The forty lines have no handler with a state of its own
  // (stage 4 of chapter 3i grows one). Chapter 4's `Cell` got one by making the handled program a function of
  // the state; that trick is written here once. What is carried is a value in the closure of each rest, never
  // a variable: a rest resumed twice starts twice from what was carried at the question (section 4.6)

  trait Carrying[S, A, B]:
    /** the `done` line, given what is carried at the end */
    def end(s: S, a: A): Prog[B]
    /** a question: given the rest, which takes the answer AND what to carry on; the result is a function of
     * what is carried now. None: not my question */
    def on[X](op: Op[X], rest: (X, S) => Prog[B]): Option[S => Prog[B]]

  def carry[S, A, B](h: Carrying[S, A, B], from: S)(p: Prog[A]): Prog[B] =
    val fold = new Handler[A, S => Prog[B]]:
      def done(a: A) = Prog.Done(s => h.end(s, a))
      def on[X](op: Op[X], rest: X => Prog[S => Prog[B]]) =
        h.on(op, (x, s) => rest(x).flatMap(_(s))).map(Prog.Done(_))
    handle(fold)(p).flatMap(_(from))

  // ---- 1. state

  case object Get extends Op[Long]
  case class Put(n: Long) extends Op[Unit]

  /** in memory: chapter 4's `Cell` */
  final class Cell[A] extends Carrying[Long, A, (A, Long)]:
    def end(s: Long, a: A) = done((a, s))
    def on[X](op: Op[X], rest: (X, Long) => Prog[(A, Long)]) = op match
      case Get    => Some(s => rest(s, s))
      case Put(n) => Some(_ => rest((), n))
      case _      => None
  def cell[A](s: Long)(p: Prog[A]): Prog[(A, Long)] = carry(Cell[A](), s)(p)

  /** a store: the lower vocabulary a state can be answered in */
  case class Load(key: String) extends Op[Option[Long]]
  case class Save(key: String, value: Long) extends Op[Unit]

  /** in a store: each question of the state becomes a question of ANOTHER effect, and nothing is kept here */
  final class Stored[A](key: String, otherwise: Long) extends Handler[A, A]:
    def done(a: A) = Prog.Done(a)
    def on[X](op: Op[X], rest: X => Prog[A]) = op match
      case Get    => Some(ask(Load(key)).flatMap(found => rest(found.getOrElse(otherwise))))
      case Put(n) => Some(ask(Save(key, n)).flatMap(_ => rest(())))
      case _      => None

  /** the store itself, in memory: a table carried along */
  final class Table[A] extends Carrying[Map[String, Long], A, (A, Map[String, Long])]:
    def end(rows: Map[String, Long], a: A) = done((a, rows))
    def on[X](op: Op[X], rest: (X, Map[String, Long]) => Prog[(A, Map[String, Long])]) = op match
      case Load(key)        => Some(rows => rest(rows.get(key), rows))
      case Save(key, value) => Some(rows => rest((), rows.updated(key, value)))
      case _                => None
  def table[A](rows: Map[String, Long])(p: Prog[A]): Prog[(A, Map[String, Long])] = carry(Table[A](), rows)(p)

  /** journalled: nothing is overwritten; a `Put` adds a line, a `Get` reads the last one */
  final class Journalled[A](first: Long) extends Carrying[Vector[Long], A, (A, Vector[Long])]:
    def end(puts: Vector[Long], a: A) = done((a, puts))
    def on[X](op: Op[X], rest: (X, Vector[Long]) => Prog[(A, Vector[Long])]) = op match
      case Get    => Some(puts => rest(puts.lastOption.getOrElse(first), puts))
      case Put(n) => Some(puts => rest((), puts :+ n))
      case _      => None
  def journalled[A](first: Long)(p: Prog[A]): Prog[(A, Vector[Long])] = carry(Journalled[A](first), Vector.empty)(p)

  /** a test double as they are often written: `Get` is always what the test said, `Put` is accepted */
  final class Stub[A](always: Long) extends Handler[A, A]:
    def done(a: A) = Prog.Done(a)
    def on[X](op: Op[X], rest: X => Prog[A]) = op match
      case Get    => Some(rest(always))
      case Put(_) => Some(rest(()))
      case _      => None

  /** anything that claims to answer the state: a program in, its value and the content at the end out */
  trait AState:
    def apply[A](from: Long)(p: Prog[A]): (A, Long)

  val inMemory: AState = new AState:
    def apply[A](from: Long)(p: Prog[A]) = run(cell(from)(p))
  val inStore: AState = new AState:
    def apply[A](from: Long)(p: Prog[A]) =
      val (a, rows) = run(table(Map("n" -> from))(handle(Stored[A]("n", 0))(p)))
      (a, rows("n"))
  val inJournal: AState = new AState:
    def apply[A](from: Long)(p: Prog[A]) =
      val (a, puts) = run(journalled(from)(p))
      (a, puts.lastOption.getOrElse(from))
  val stubbed: AState = new AState:
    def apply[A](from: Long)(p: Prog[A]) = (run(handle(Stub[A](from))(p)), from)

  /** the four equations of section 4.7, each side run under the handler, from two contents, with a rest that
   * looks at the state again. The list of which hold */
  def stateOwes(h: AState): List[Boolean] =
    def k(x: Long, y: Long): Prog[(Long, Long, Long)] = ask(Get).map(z => (x, y, z))
    def same[A](left: Prog[A], right: Prog[A]) = List(0L, 5L).forall(s => h(s)(left) == h(s)(right))
    List(
      same(ask(Put(3)).flatMap(_ => ask(Get)).flatMap(x => k(x, 0)), ask(Put(3)).flatMap(_ => k(3, 0))),
      same(ask(Put(3)).flatMap(_ => ask(Put(4))).flatMap(_ => k(0, 0)), ask(Put(4)).flatMap(_ => k(0, 0))),
      same(ask(Get).flatMap(x => ask(Get).flatMap(y => k(x, y))), ask(Get).flatMap(x => k(x, x))),
      same(ask(Get).flatMap(x => ask(Put(x))).flatMap(_ => k(0, 0)), k(0, 0)))

  // ---- 2. reader

  case object Env extends Op[String]

  /** the environment, given: answered in place */
  final class Given[A](e: String) extends Handler[A, A]:
    def done(a: A) = Prog.Done(a)
    def on[X](op: Op[X], rest: X => Prog[A]) = op match
      case Env => Some(rest(e))
      case _   => None
  def given_[A](e: String)(p: Prog[A]): Prog[A] = handle(Given[A](e))(p)

  /** a changed environment for a PART of a program: a handler around that part, which asks whoever is
   * outside and answers with what `f` makes of it */
  final class Local[A](f: String => String) extends Handler[A, A]:
    def done(a: A) = Prog.Done(a)
    def on[X](op: Op[X], rest: X => Prog[A]) = op match
      case Env => Some(ask(Env).flatMap(e => rest(f(e))))
      case _   => None
  def local[A](f: String => String)(p: Prog[A]): Prog[A] = handle(Local[A](f))(p)

  // ---- 3. writer

  case class Tell(line: String) extends Op[Unit]

  /** collected: everything told, in order, beside the result */
  final class Told[A] extends Carrying[Vector[String], A, (Vector[String], A)]:
    def end(lines: Vector[String], a: A) = done((lines, a))
    def on[X](op: Op[X], rest: (X, Vector[String]) => Prog[(Vector[String], A)]) = op match
      case Tell(line) => Some(lines => rest((), lines :+ line))
      case _          => None
  def told[A](p: Prog[A]): Prog[(Vector[String], A)] = carry(Told[A](), Vector.empty)(p)
  def lines[A](p: Prog[A]): Vector[String] = run(told(p))._1

  /** folded as it is told: only a count is kept */
  final class Counted[A] extends Carrying[Int, A, (Int, A)]:
    def end(n: Int, a: A) = done((n, a))
    def on[X](op: Op[X], rest: (X, Int) => Prog[(Int, A)]) = op match
      case Tell(_) => Some(n => rest((), n + 1))
      case _       => None

  /** sent on to a sink that is a real shared world, a console: so a variable, on purpose. Whatever resumes a
   * rest twice writes twice, and nothing takes a written line back */
  final class Printed[A](console: java.lang.StringBuilder) extends Handler[A, A]:
    def done(a: A) = Prog.Done(a)
    def on[X](op: Op[X], rest: X => Prog[A]) = op match
      case Tell(line) => console.append(line).append('\n'); Some(rest(()))
      case _          => None

  /** what a part of a program tells, rewritten on its way out: a handler around that part */
  final class Censored[A](f: String => String) extends Handler[A, A]:
    def done(a: A) = Prog.Done(a)
    def on[X](op: Op[X], rest: X => Prog[A]) = op match
      case Tell(line) => Some(ask(Tell(f(line))).flatMap(_ => rest(())))
      case _          => None

  // ---- 4. failure: `Refuse` and `Refusals` are chapter 2's

  def refusals[A](p: Prog[A]): Prog[Either[String, A]] = handle(Refusals[A]())(p)

  /** a default: the whole computation ends in it */
  final class OrElse[A](default: A) extends Handler[A, A]:
    def done(a: A) = Prog.Done(a)
    def on[X](op: Op[X], rest: X => Prog[A]) = op match
      case Refuse(_) => Some(Prog.Done(default))
      case _         => None

  /** catching: the handler applied to a part, and a program for the refusal */
  def recover[A](p: Prog[A])(h: String => Prog[A]): Prog[A] = refusals(p).flatMap(_.fold(h, done))

  /** retrying: the PART is a value, so it is handled again. Not the rest: a refusal has no rest to call */
  def retry[A](times: Int)(p: Prog[A]): Prog[Either[String, A]] =
    refusals(p).flatMap(r => if r.isLeft && times > 1 then retry(times - 1)(p) else done(r))

  // ---- 5. choice: `Choose` and `AllChoices` are chapter 2's

  def allChoices[A](p: Prog[A]): Prog[List[A]] = handle(AllChoices[A]())(p)
  val fail: Prog[Nothing] = ask(Choose[Nothing](Nil))
  def among[A](options: A*): Prog[A] = ask(Choose(options.toList))

  /** the first option, in place: the rest is called once, or (no options) not at all */
  final class First[A] extends Handler[A, Option[A]]:
    def done(a: A) = Prog.Done(Some(a))
    def on[X](op: Op[X], rest: X => Prog[Option[A]]) = op match
      case Choose(options) => Some(options.headOption.fold(Prog.Done(None))(rest))
      case _               => None

  /** backtracking: the rest with each option in turn, until one path ends without failing */
  final class FirstSuccess[A] extends Handler[A, Option[A]]:
    def done(a: A) = Prog.Done(Some(a))
    def on[X](op: Op[X], rest: X => Prog[Option[A]]) = op match
      case Choose(options) =>
        def from(left: List[X]): Prog[Option[A]] = left match
          case Nil       => Prog.Done(None)
          case o :: more => rest(o).flatMap(found => if found.isEmpty then from(more) else Prog.Done(found))
        Some(from(options))
      case _ => None

  /** at random: a choice among n is a draw below n, asked of whoever answers randomness */
  final class ByDraw[A] extends Handler[A, A]:
    def done(a: A) = Prog.Done(a)
    def on[X](op: Op[X], rest: X => Prog[A]) = op match
      case Choose(options) if options.nonEmpty => Some(ask(Draw(options.size)).flatMap(i => rest(options(i))))
      case _                                   => None

  // ---- 6. resources

  /** run this when the scope ends. A finalizer is a program: it may tell, or ask anything outside the scope */
  case class Defer(finalizer: Prog[Unit]) extends Op[Unit]

  def bracket[R, A](acquire: Prog[R])(release: R => Prog[Unit])(use: R => Prog[A]): Prog[A] =
    acquire.flatMap(r => ask(Defer(release(r))).flatMap(_ => use(r)))

  def released(finalizers: List[Prog[Unit]]): Prog[Unit] =
    finalizers.foldLeft(done(()))((before, f) => before.flatMap(_ => f))

  /** a scope: the finalizers deferred in it are carried, last first, and run ON ITS `done` LINE. `guarded`
   * adds one clause: a `Refuse` going by is known to have no rest, so the finalizers run before it goes on */
  final class Scoped[A](guarded: Boolean) extends Carrying[List[Prog[Unit]], A, A]:
    def end(finalizers: List[Prog[Unit]], a: A) = released(finalizers).map(_ => a)
    def on[X](op: Op[X], rest: (X, List[Prog[Unit]]) => Prog[A]) = op match
      case Defer(f)               => Some(finalizers => rest((), f :: finalizers))
      case Refuse(why) if guarded => Some(finalizers => released(finalizers).flatMap(_ => ask(Refuse(why))))
      case _                      => None
  def scoped[A](p: Prog[A]): Prog[A] = carry(Scoped[A](guarded = false), Nil)(p)
  def guarded[A](p: Prog[A]): Prog[A] = carry(Scoped[A](guarded = true), Nil)(p)

  /** a file, as far as this chapter needs one: opening and closing are told */
  def file[A](name: String)(use: String => Prog[A]): Prog[A] =
    bracket(ask(Tell(s"open $name")).map(_ => name))(n => ask(Tell(s"close $n")))(use)

  // ---- 7. time: `Now` is chapter 2's

  case class Sleep(millis: Long) extends Op[Unit]

  /** virtual: the time is carried, and a sleep is an addition */
  final class Virtual[A] extends Carrying[Long, A, (A, Long)]:
    def end(t: Long, a: A) = done((a, t))
    def on[X](op: Op[X], rest: (X, Long) => Prog[(A, Long)]) = op match
      case Now      => Some(t => rest(t, t))
      case Sleep(d) => Some(t => rest((), t + d))
      case _        => None
  def virtual[A](t: Long)(p: Prog[A]): Prog[(A, Long)] = carry(Virtual[A](), t)(p)

  /** real: the one handler of this file that reaches the machine it runs on */
  final class Wall[A] extends Handler[A, A]:
    def done(a: A) = Prog.Done(a)
    def on[X](op: Op[X], rest: X => Prog[A]) = op match
      case Now      => Some(rest(System.nanoTime / 1_000_000))
      case Sleep(d) => Thread.sleep(d); Some(rest(()))
      case _        => None

  /** a deadline, for a program that sleeps: sleeps go by while there is time left; the one that would pass
   * the limit is cut to what is left and the program refused */
  final class Budget[A] extends Carrying[Long, A, A]:
    def end(left: Long, a: A) = done(a)
    def on[X](op: Op[X], rest: (X, Long) => Prog[A]) = op match
      case Sleep(d) =>
        Some(left =>
          if d <= left then ask(Sleep(d)).flatMap(_ => rest((), left - d))
          else ask(Sleep(left)).flatMap(_ => ask(Refuse("out of time"))))
      case _ => None
  def budget[A](millis: Long)(p: Prog[A]): Prog[A] = carry(Budget[A](), millis)(p)

  // ---- 8. randomness

  case class Draw(bound: Int) extends Op[Int]

  /** seeded: the generator's state is carried; one multiplication and one addition a draw */
  final class Seeded[A] extends Carrying[Long, A, A]:
    def end(s: Long, a: A) = done(a)
    def on[X](op: Op[X], rest: (X, Long) => Prog[A]) = op match
      case Draw(bound) =>
        Some { s =>
          val next = s * 6364136223846793005L + 1442695040888963407L
          rest(((next >>> 33) % bound).toInt, next)
        }
      case _ => None
  def seeded[A](seed: Long)(p: Prog[A]): Prog[A] = carry(Seeded[A](), seed)(p)

  /** recorded: every draw asked again of whoever answers it, and kept */
  final class Taped[A] extends Carrying[Vector[Int], A, (Vector[Int], A)]:
    def end(tape: Vector[Int], a: A) = done((tape, a))
    def on[X](op: Op[X], rest: (X, Vector[Int]) => Prog[(Vector[Int], A)]) = op match
      case Draw(_) => Some(tape => ask(op).flatMap(x => rest(x, tape :+ x)))
      case _       => None
  def taped[A](p: Prog[A]): Prog[(Vector[Int], A)] = carry(Taped[A](), Vector.empty)(p)

  /** replayed: answered from a tape while it lasts; after that the question goes on outward, still asked */
  final class FromTape[A] extends Carrying[List[Int], A, A]:
    def end(tape: List[Int], a: A) = done(a)
    def on[X](op: Op[X], rest: (X, List[Int]) => Prog[A]) = op match
      case Draw(_) =>
        Some {
          case x :: more => rest(x, more)
          case Nil       => ask(op).flatMap(x => rest(x, Nil))
        }
      case _ => None
  def fromTape[A](tape: Vector[Int])(p: Prog[A]): Prog[A] = carry(FromTape[A](), tape.toList)(p)

  // ---- 9. async

  case object Yield extends Op[Unit]
  /** start `body` beside me. An operation that HOLDS A PROGRAM: scoped, not algebraic (chapter 2b) */
  case class Fork(body: Prog[Unit]) extends Op[Unit]

  /** a fibre that waits its turn: given the others that wait, the program that runs it and then them */
  final case class Fibre(go: Vector[Fibre] => Prog[Unit])

  /** A SCHEDULER IS A HANDLER THAT KEEPS RESTS IN A QUEUE. At `Yield` and at `Fork` the rest is not called:
   * it is put with the fibres that wait, and `pick` says which of them goes next. `pick` is a program, so
   * the choice may itself be asked of someone. The queue is carried, as a state is in section 0 */
  final class Scheduler(pick: Int => Prog[Int]) extends Handler[Unit, Fibre]:
    private def next(waiting: Vector[Fibre]): Prog[Unit] =
      if waiting.isEmpty then Prog.Done(())
      else pick(waiting.size).flatMap(i => waiting(i).go(waiting.patch(i, Nil, 1)))
    private def fibre(p: => Prog[Fibre]): Fibre = Fibre(waiting => p.flatMap(_.go(waiting)))
    def done(u: Unit) = Prog.Done(Fibre(next))
    def on[X](op: Op[X], rest: X => Prog[Fibre]) = op match
      case Yield => Some(Prog.Done(Fibre(waiting => next(waiting :+ fibre(rest(()))))))
      case Fork(body) => // the body is a value: THIS handler walks it, and nobody between it and the asker
        Some(Prog.Done(Fibre(waiting => next(waiting :+ fibre(rest(())) :+ fibre(handle(this)(body))))))
      case _ => None
  def scheduled(pick: Int => Prog[Int])(p: Prog[Unit]): Prog[Unit] =
    handle(Scheduler(pick))(p).flatMap(_.go(Vector.empty))

  val roundRobin: Int => Prog[Int] = _ => done(0)       // the one that has waited longest
  val newestFirst: Int => Prog[Int] = n => done(n - 1)  // the one that came last
  val byDraw: Int => Prog[Int] = n => ask(Draw(n))      // whoever answers randomness decides

  /** a question answered LATER, by whoever is handed the callback */
  case class Await[X](register: (X => Unit) => Unit) extends Op[X]

  /** A FIBRE IS A PROGRAM ON A THREAD OF CONTROL. `start` goes as far as the first `Await`, hands the rest
   * of the program to `register` as the callback, and RETURNS: no thread is held. Whoever calls the callback
   * lends its thread for the next stretch. Nothing is captured, because the rest was never hidden. This is
   * not a `Handler`: it stands where `run` stands, and returns before the program has ended */
  def start[A](p: Prog[A])(whenDone: A => Unit): Unit = p match
    case Prog.Done(a) => whenDone(a)
    case asked: Prog.Ask[x, a] => asked.op match
        case Await(register) => register(answer => start(asked.rest(answer))(whenDone))
        case other           => sys.error(s"nobody answers $other")

  /** an event loop: one thread and a list of what to do later. A real shared world, so a variable */
  final class Loop:
    private var later = Vector.empty[() => Unit]
    def soon[X](x: => X): Prog[X] = ask(Await[X](callback => later :+= (() => callback(x))))
    def turn(): Boolean = later.headOption.exists { job => later = later.tail; job(); true }

  /** another thread answers */
  def elsewhere[X](x: => X): Prog[X] = ask(Await[X](callback => new Thread(() => callback(x)).start()))

  /** BLOCKING IS A CAPABILITY. To hand back a program's value, something must wait for it, and waiting parks
   * a thread. A platform that has threads to park gives the evidence; a single event loop gives none */
  sealed trait CanBlock
  object threads:
    given canBlock: CanBlock = new CanBlock {}

  def await[A](p: Prog[A])(using CanBlock): A =
    val result = java.util.concurrent.CompletableFuture[A]()
    start(p)(a => result.complete(a))
    result.get()

  // ---- 10. streams: `Emit` is chapter 6's, where push was turned into pull

  /** every item of a producer, by chapter 6's `Collected`; what the producer tells on the way is dropped */
  def items[A](p: Prog[A]): List[String] = run(told(handle(Collected())(p.map(_ => ()))))._2

  /** a stage of a pipeline is an interceptor of `Emit`: it asks `Emit` again, of whoever is further out */
  final class Mapped[A](f: String => String) extends Handler[A, A]:
    def done(a: A) = Prog.Done(a)
    def on[X](op: Op[X], rest: X => Prog[A]) = op match
      case Emit(item) => Some(ask(Emit(f(item))).flatMap(_ => rest(())))
      case _          => None
  def mapped[A](f: String => String)(p: Prog[A]): Prog[A] = handle(Mapped[A](f))(p)

  /** the first `n` items, and then the producer is NOT continued: what is left to let through is carried */
  final class Taking extends Carrying[Int, Unit, Unit]:
    def end(left: Int, u: Unit) = done(())
    def on[X](op: Op[X], rest: (X, Int) => Prog[Unit]) = op match
      case Emit(_) =>
        Some(left =>
          if left <= 0 then done(())
          else if left == 1 then ask(op).map(_ => ())
          else ask(op).flatMap(x => rest(x, left - 1)))
      case _ => None
  def taking[A](n: Int)(p: Prog[A]): Prog[Unit] = carry(Taking(), n)(p.map(_ => ()))

  /** a consumer run on each item BEFORE the producer is continued: the producer goes at the consumer's pace */
  final class Each[A](consume: String => Prog[Unit]) extends Handler[A, A]:
    def done(a: A) = Prog.Done(a)
    def on[X](op: Op[X], rest: X => Prog[A]) = op match
      case Emit(item) => Some(consume(item).flatMap(_ => rest(())))
      case _          => None
  def each[A](consume: String => Prog[Unit])(p: Prog[A]): Prog[A] = handle(Each[A](consume))(p)

  /** a buffer of `size`: the producer is continued at once while there is room, and waits for the consumer
   * when there is none. What is in the buffer is carried */
  final class Batched[A](size: Int, consume: Vector[String] => Prog[Unit]) extends Carrying[Vector[String], A, A]:
    def end(buffer: Vector[String], a: A) = (if buffer.isEmpty then done(()) else consume(buffer)).map(_ => a)
    def on[X](op: Op[X], rest: (X, Vector[String]) => Prog[A]) = op match
      case Emit(item) =>
        Some(buffer =>
          if buffer.size + 1 < size then rest((), buffer :+ item)
          else consume(buffer :+ item).flatMap(_ => rest((), Vector.empty)))
      case _ => None
  def batched[A](size: Int)(consume: Vector[String] => Prog[Unit])(p: Prog[A]): Prog[A] =
    carry(Batched[A](size, consume), Vector.empty)(p)

  // ---- the programs of the chapter

  def tell(line: String): Prog[Unit] = ask(Tell(line))
  val tick: Prog[Unit] = ask(Get).flatMap(n => ask(Put(n + 1)))

  /** it fails until the state has counted two attempts */
  val flaky: Prog[String] =
    for
      n <- ask(Get)
      _ <- ask(Put(n + 1))
      r <- if n < 2 then ask(Refuse(s"attempt $n failed")) else done(s"done at attempt $n")
    yield r

  /** two numbers from 1 to 4, the first smaller, that make 6; a tick of the state at each number tried */
  val pair: Prog[(Int, Int)] =
    for
      a <- among(1, 2, 3, 4)
      _ <- tick
      b <- among(1, 2, 3, 4)
      _ <- tick
      _ <- if a < b && a + b == 6 then done(()) else fail
    yield (a, b)

  val greeting: Prog[String] = ask(Env).map(e => s"hello, $e")

  /** an hour between two readings of the clock */
  val patient: Prog[Long] = for t <- ask(Now); _ <- ask(Sleep(3_600_000)); u <- ask(Now) yield u - t
  val slow: Prog[String] = for _ <- ask(Sleep(40)); _ <- tell("half way"); _ <- ask(Sleep(40)) yield "finished"

  val dice: Prog[List[Int]] = for a <- ask(Draw(6)); b <- ask(Draw(6)); c <- ask(Draw(6)) yield List(a, b, c)
  val branchThenDraw: Prog[(String, Int)] = for side <- among("left", "right"); n <- ask(Draw(1000)) yield (side, n)

  /** a file opened, then a choice, then the file used */
  val openThenChoose: Prog[Int] =
    file("a")(a => among(1, 2).flatMap(x => tell(s"use $a for $x").map(_ => x)))
  val openThenRefuse: Prog[Int] = file("a")(a => tell(s"use $a").flatMap(_ => ask(Refuse("no"))))
  /** a producer that opens its own file, as one writes it: the scope is inside the producer */
  def linesOf(scope: Prog[Unit] => Prog[Unit]): Prog[Unit] =
    scope(file("a")(_ => made(3)))

  def worker(name: String): Prog[Unit] = for _ <- tell(s"$name 1"); _ <- ask(Yield); _ <- tell(s"$name 2") yield ()
  val twoWorkers: Prog[Unit] = for _ <- ask(Fork(worker("a"))); _ <- ask(Fork(worker("b"))) yield ()
  /** read, let the others run, write back one more */
  val increment: Prog[Unit] = for x <- ask(Get); _ <- ask(Yield); _ <- ask(Put(x + 1)) yield ()
  val twoIncrements: Prog[Unit] = for _ <- ask(Fork(increment)); _ <- ask(Fork(increment)) yield ()
  /** a fibre that refuses beside one that works; `around` is what the first is wrapped in */
  def crashing(around: Prog[Unit] => Prog[Unit]): Prog[Unit] =
    val broken: Prog[Unit] = tell("a 1").flatMap(_ => ask(Refuse("a broke")))
    for _ <- ask(Fork(around(broken))); _ <- ask(Fork(worker("b"))) yield ()

  /** a producer that says what it made before it emits it */
  def made(n: Int): Prog[Unit] =
    (1 to n).foldLeft(done(()))((before, i) => before.flatMap(_ => tell(s"made $i")).flatMap(_ => ask(Emit(s"$i"))))
  def endless(from: Int): Prog[Nothing] =
    tell(s"made $from").flatMap(_ => ask(Emit(s"$from"))).flatMap(_ => endless(from + 1))

  def count(n: Int): Prog[Unit] = if n == 0 then done(()) else tick.flatMap(_ => count(n - 1))

import std.*

@main def catalogue(): Unit =
  // ---- 1. state: three handlers, one list of equations; a stub that is not a state
  assert(stateOwes(inMemory) == List(true, true, true, true))
  assert(stateOwes(inStore) == List(true, true, true, true))
  assert(stateOwes(inJournal) == List(true, true, true, true))
  assert(stateOwes(stubbed) == List(false, true, true, true)) // "you get what was put" is what a stub drops
  val twice = tick.flatMap(_ => tick)
  assert(run(cell(0)(twice)) == ((), 2L))
  assert(run(table(Map.empty)(handle(Stored[Unit]("visits", 0))(twice))) == ((), Map("visits" -> 2L)))
  assert(run(journalled(0)(twice)) == ((), Vector(1L, 2L)))

  // ---- 2. reader: given; changed for a part; and why the changing is not an operation
  assert(run(given_("ann")(greeting)) == "hello, ann")
  val shout = local(_.toUpperCase)(greeting)
  assert(run(given_("ann")(shout.flatMap(a => greeting.map(b => (a, b))))) == ("hello, ANN", "hello, ann"))
  // sequencing does not move into the scope: the two programs differ, so `local` is not an algebraic operation
  val outside = local(_.toUpperCase)(greeting).flatMap(a => greeting.map(b => (a, b)))
  val inside = local(_.toUpperCase)(greeting.flatMap(a => greeting.map(b => (a, b))))
  assert(run(given_("ann")(outside)) != run(given_("ann")(inside)))
  assert(run(given_("ann")(inside)) == ("hello, ANN", "hello, ANN"))
  // what the reader owes: asking twice changes nothing, and asking for nothing is nothing
  val (twiceAsked, onceAsked) = (ask(Env).flatMap(x => ask(Env).map(y => (x, y))), ask(Env).map(x => (x, x)))
  assert(run(given_("e")(twiceAsked)) == run(given_("e")(onceAsked)))
  assert(run(given_("e")(ask(Env).flatMap(_ => done(1)))) == run(given_("e")(done(1))))

  // ---- 3. writer: collected, counted, printed, censored; and where the lines of a refused program go
  val steps = for _ <- tell("parse"); _ <- tell("check") yield 42
  assert(run(told(steps)) == (Vector("parse", "check"), 42))
  assert(run(carry(Counted[Int](), 0)(steps)) == (2, 42))
  val console = java.lang.StringBuilder()
  assert(run(handle(Printed[Int](console))(steps)) == 42 && console.toString == "parse\ncheck\n")
  assert(lines(handle(Censored[Int](_.reverse))(steps)) == Vector("esrap", "kcehc"))
  assert(lines(steps.flatMap(_ => steps)) == lines(steps) ++ lines(steps)) // what `Told` owes
  val failing = for _ <- tell("parse"); _ <- ask(Refuse("bad input")); _ <- tell("check") yield 42
  assert(run(told(refusals(failing))) == (Vector("parse"), Left("bad input"))) // the lines outlive the refusal
  assert(run(refusals(told(failing))) == Left("bad input"))                    // the lines went with the rest
  // a printed line is not taken back: the same program, handled inside a refusal, has still written
  val console2 = java.lang.StringBuilder()
  assert(run(refusals(handle(Printed[Int](console2))(failing))) == Left("bad input") && console2.toString == "parse\n")

  // ---- 4. failure: as a result, as a default, caught, retried; state and retry in two orders
  assert(run(told(refusals(failing.map(_ + 1)))) == (Vector("parse"), Left("bad input")))
  assert(run(told(handle(OrElse(0))(failing))) == (Vector("parse"), 0))
  assert(run(recover(ask(Refuse("x")))(why => done(s"caught $why"))) == "caught x") // what catching owes
  assert(run(recover(done("fine"))(why => done(s"caught $why"))) == "fine")
  // the state outside the retry: an attempt sees what the attempts before it wrote
  assert(run(cell(0)(retry(3)(flaky))) == (Right("done at attempt 2"), 3L))
  // the state inside: every attempt starts from the same content, and this program can never get on
  assert(run(retry(3)(cell(0)(flaky))) == Left("attempt 0 failed"))
  // catching is not an operation either: what follows it is not guarded
  val caughtThenFails = recover(done(1))(_ => done(0)).flatMap(_ => ask(Refuse("later")))
  assert(run(refusals(caughtThenFails)) == Left("later"))

  // ---- 5. choice: first, all, backtracking, at random; a state on each side of the search
  assert(run(cell(0)(handle(First[(Int, Int)]())(pair))) == (None, 2L))        // 1 and 1: fails, and stays failed
  assert(run(cell(0)(handle(FirstSuccess[(Int, Int)]())(pair))) == (Some((2, 4)), 10L))
  assert(run(cell(0)(allChoices(pair))) == (List((2, 4)), 20L))                // one state: the work of the search
  assert(run(allChoices(cell(0)(pair))) == List(((2, 4), 2L)))                 // each path's own: its length
  val split = for a <- among(10, 20); b <- among(1, 2) yield (a, b)
  val sampled = run(seeded(7)(handle(ByDraw[(Int, Int)]())(split)))
  assert(sampled == (10, 2) && sampled == run(seeded(7)(handle(ByDraw[(Int, Int)]())(split))))
  // what every handler of a choice owes: one option is no choice; what `AllChoices` owes: options concatenate
  assert(run(allChoices(among(5))) == List(5) && run(handle(First[Int]())(among(5))) == Some(5))
  assert(run(handle(FirstSuccess[Int]())(among(5))) == Some(5))
  assert(run(allChoices(among(1, 2, 3))) == run(allChoices(among(1))) ++ run(allChoices(among(2, 3))))

  // ---- 6. resources: a scope is a handler; its finalizers run on its `done` line, and so
  val nested = file("a")(a => file("b")(b => tell(s"use $a $b")))
  assert(lines(scoped(nested)) == Vector("open a", "open b", "use a b", "close b", "close a"))
  // a refusal answered INSIDE the scope ends the scope with a value: released
  assert(run(told(scoped(refusals(openThenRefuse)))) == (Vector("open a", "use a", "close a"), Left("no")))
  // a refusal answered OUTSIDE drops the rest of the scope, its `done` line with it: NOT released
  assert(run(told(refusals(scoped(openThenRefuse)))) == (Vector("open a", "use a"), Left("no")))
  // a scope that watches `Refuse` go by releases first: for the one operation it knows has no rest
  assert(run(told(refusals(guarded(openThenRefuse)))) == (Vector("open a", "use a", "close a"), Left("no")))
  // a choice OUTSIDE the scope resumes the rest of the scope twice: released twice, and used after the first
  assert(lines(allChoices(scoped(openThenChoose))) ==
    Vector("open a", "use a for 1", "close a", "use a for 2", "close a"))
  // the choice INSIDE the scope: one resource for every branch, released once
  assert(lines(scoped(allChoices(openThenChoose))) == Vector("open a", "use a for 1", "use a for 2", "close a"))
  // a consumer that stops: the scope outside it is released, the scope inside the producer is not
  assert(lines(handle(Collected())(scoped(taking(1)(file("a")(_ => made(3)))))) ==
    Vector("open a", "made 1", "close a"))
  assert(lines(handle(Collected())(taking(1)(linesOf(scoped)))) == Vector("open a", "made 1"))
  assert(lines(handle(Collected())(taking(1)(linesOf(guarded)))) == Vector("open a", "made 1")) // no `Refuse` went by

  // ---- 7. time: fixed, virtual, real; a deadline that only sees sleeps
  assert(run(handle(FixedClock[Long](42))(ask(Now).flatMap(t => ask(Now).map(_ - t)))) == 0L)
  assert(run(virtual(0)(patient)) == (3_600_000L, 3_600_000L))                 // an hour, in no time
  val twoSleeps = ask(Sleep(10)).flatMap(_ => ask(Sleep(5))).flatMap(_ => ask(Now))
  assert(run(virtual(100)(twoSleeps)) == run(virtual(100)(ask(Sleep(15)).flatMap(_ => ask(Now)))))
  val real = run(handle(Wall[Long]())(for t <- ask(Now); _ <- ask(Sleep(2)); u <- ask(Now) yield u - t))
  assert(real >= 1)                                                            // all it owes: time went forward
  assert(run(virtual(0)(told(refusals(budget(60)(slow))))) == ((Vector("half way"), Left("out of time")), 60L))
  assert(run(virtual(0)(told(refusals(budget(100)(slow))))) == ((Vector("half way"), Right("finished")), 80L))
  assert(run(refusals(budget(0)(done("asked nothing")))) == Right("asked nothing")) // nothing to see, nothing cut

  // ---- 8. randomness: seeded, recorded, replayed; and a generator on each side of a fork
  val (tape, rolled) = run(seeded(7)(taped(dice)))
  assert(rolled == List(2, 5, 3) && rolled == run(seeded(7)(dice)) && run(seeded(8)(dice)) == List(4, 0, 2))
  assert(tape.toList == rolled && run(fromTape(tape)(dice)) == rolled)         // the tape, with no generator at all
  assert(rolled.distinct.size > 1)                                             // asking twice is NOT asking once
  assert(run(seeded(1)(fromTape(Vector(5))(dice))).head == 5)                  // the tape ran out: asked outward
  val inside2 = run(allChoices(seeded(7)(branchThenDraw)))
  val outside2 = run(seeded(7)(allChoices(branchThenDraw)))
  assert(inside2 == List(("left", 278), ("right", 278)))                       // every branch drew the same number
  assert(outside2 == List(("left", 278), ("right", 231)))

  // ---- 9. async: one program, three schedulers; shared state; the body of a fork; callbacks; blocking
  assert(lines(scheduled(roundRobin)(twoWorkers)) == Vector("a 1", "b 1", "a 2", "b 2"))
  assert(lines(scheduled(newestFirst)(twoWorkers)) == Vector("a 1", "a 2", "b 1", "b 2"))
  val drawn = lines(seeded(2)(scheduled(byDraw)(twoWorkers)))
  assert(drawn == Vector("a 1", "b 1", "b 2", "a 2"))                          // a third interleaving, and
  assert(drawn == lines(seeded(2)(scheduled(byDraw)(twoWorkers))))             // the same seed, the same one
  // what every scheduler owes: each fibre runs to its end, in its own order. Not owed: the interleaving
  for trace <- List(lines(scheduled(roundRobin)(twoWorkers)), lines(scheduled(newestFirst)(twoWorkers)), drawn) do
    assert(trace.sorted == Vector("a 1", "a 2", "b 1", "b 2"))
    assert(trace.indexOf("a 1") < trace.indexOf("a 2") && trace.indexOf("b 1") < trace.indexOf("b 2"))
  // a state OUTSIDE the scheduler is shared by the fibres, and the scheduler decides whether an update is lost
  assert(run(cell(0)(scheduled(roundRobin)(twoIncrements)))._2 == 1L)
  assert(run(cell(0)(scheduled(newestFirst)(twoIncrements)))._2 == 2L)
  // under the drawing scheduler the seed decides, so a seed is a reproducible report of the lost update
  assert(run(cell(0)(seeded(1)(scheduled(byDraw)(twoIncrements))))._2 == 1L)
  assert(run(cell(0)(seeded(3)(scheduled(byDraw)(twoIncrements))))._2 == 2L)
  // a state BETWEEN the forking code and the scheduler does not reach the body of a fork
  val between = Try(run(scheduled(roundRobin)(cell(0)(twoIncrements).map(_ => ()))))
  assert(between.failed.map(_.getMessage).toOption == Some("nobody answers Get"))
  // a state INSIDE a body is that fibre's own
  val own = ask(Fork(cell(0)(increment).map(_ => ()))).flatMap(_ => ask(Fork(cell(0)(increment).map(_ => ()))))
  assert(run(scheduled(roundRobin)(own)) == ())
  // a refusal answered OUTSIDE the scheduler takes every fibre with it: the queue is part of the rest
  assert(run(told(refusals(scheduled(roundRobin)(crashing(a => a))))) == (Vector("a 1"), Left("a broke")))
  // answered INSIDE the body, it ends that fibre and no other
  val supervised = crashing(a => refusals(a).flatMap(r => tell(s"a ended: ${r.merge}")))
  assert(lines(scheduled(roundRobin)(supervised)) == Vector("a 1", "a ended: a broke", "b 1", "b 2"))

  // callbacks: the program returns at its first `Await`, holding no thread, and goes on when called back
  val loop = Loop()
  var result = Option.empty[Int]
  start(for a <- loop.soon(20); b <- loop.soon(22) yield a + b)(r => result = Some(r))
  assert(result.isEmpty)                    // `start` has returned; the rest of the program is a callback
  assert(loop.turn() && result.isEmpty)     // the first answer came; the program is at its second `Await`
  assert(loop.turn() && result == Some(42) && !loop.turn())
  // blocking: with the evidence it is a call; without it, it is not a program that compiles
  val sum = for a <- elsewhere(20); b <- elsewhere(22) yield a + b
  val refused = scala.compiletime.testing.typeCheckErrors("await(sum)")
  assert(refused.nonEmpty && refused.head.message.contains("CanBlock"), refused)
  locally {
    import std.threads.given
    assert(await(sum) == 42)
    assert(scala.compiletime.testing.typeCheckErrors("await(sum)").isEmpty)
  }

  // ---- 10. streams: stages are interceptors; back-pressure is when the handler resumes
  assert(items(mapped(_ + "!")(made(3))) == List("1!", "2!", "3!"))
  val firstTwo = run(told(handle(Collected())(taking(2)(mapped(_ + "!")(endless(1))))))
  assert(firstTwo == (Vector("made 1", "made 2"), List("1!", "2!")))           // an endless producer, two steps of it
  val used = (item: String) => tell(s"used $item")
  // collected, then used: the producer ran to its end before the consumer saw anything
  val pushed = handle(Collected())(made(3)).flatMap(_.foldLeft(done(()))((b, i) => b.flatMap(_ => used(i))))
  assert(lines(pushed) == Vector("made 1", "made 2", "made 3", "used 1", "used 2", "used 3"))
  // the consumer before the rest: one item is ever outstanding
  assert(lines(each(used)(made(3))) == Vector("made 1", "used 1", "made 2", "used 2", "made 3", "used 3"))
  // a buffer of two: the producer runs ahead by at most two
  assert(lines(batched(2)(items => used(items.mkString("+")))(made(3))) ==
    Vector("made 1", "made 2", "used 1+2", "made 3", "used 3"))
  // what a stage owes: mapping commutes with collecting
  assert(items(mapped(_ * 2)(made(3))) == items(made(3)).map(_ * 2))

  // ---- what the forty lines do not give: the stack. Stage 4 of chapter 3i runs this length (its main `forced`)
  assert(run(cell(0)(count(1_000)))._2 == 1_000L)
  val deep =
    try s"counted ${run(cell(0)(count(100_000)))._2}"
    catch case e: StackOverflowError => e.getClass.getName
  assert(deep == "java.lang.StackOverflowError", deep)

  println("catalogue examples hold")
