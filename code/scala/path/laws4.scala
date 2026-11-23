// THE LAWS ON STAGE 4 (chapters 3i and 4): the end of the path, where a program is five kinds of node and
// `flatMap` no longer substitutes, so that the laws of sequencing are no longer true of the nodes and must be
// true of what an interpreter makes of them. Two mains:
//   `scala-cli run code/scala --main-class book.path.stage4.laws`        the lines of code/laws-expected.txt
//   `scala-cli run code/scala --main-class book.path.stage4.generated`   the laws on a thousand programs
package book.path.stage4

import Free.Return
import Refusal.Refuse
import book.laws.{Reference, Term, Terms}

enum State[A]:
  case Get extends State[Long]
  case Put(n: Long) extends State[Unit]
import State.{Get, Put}

/** the state, with the handler that has a state of its own: the content at the end comes back */
def cell[R[_], A](s: Long)(p: A ! (R + State)): (A, Long) ! R =
  handle[State, R, A, (A, Long), Long](p, s)((s, a) => Return((a, s)),
    [X] =>
      (s: Long, op: State[X], k: (X, Long) => (A, Long) ! R) =>
        op match
          case Get    => k(s, s)
          case Put(n) => k((), n))

/** the same state, answered IN PLACE from a variable: the cheap handler of stage 4, for one path only */
def variable[R[_], A](s: Long)(p: A ! (R + State)): (A, Long) ! R =
  var held = s
  answer[State, R, A](p)([X] =>
    (op: State[X]) =>
      op match
        case Get    => held
        case Put(n) => held = n).map(a => (a, held))

/** the observer: an interceptor in front of a program already written */
def logged[R[_], A](p: A ! R)(using In[State, R]): (Vector[String], A) ! R =
  handle[State, R, A, (Vector[String], A), Vector[String]](interposed(p), Vector.empty)(
    (lines, a) => Return((lines, a)),
    [X] =>
      (lines: Vector[String], op: State[X], k: (X, Vector[String]) => (Vector[String], A) ! R) =>
        ask[State, R, X](op).flatMap(x =>
          k(x, lines :+ (op match
            case Get    => s"Get=$x"
            case Put(n) => s"Put($n)"))))

/** a choice answered once, with its first option */
def firstChoice[R[_], A](p: A ! (R + Choice)): A ! R =
  answer[Choice, R, A](p)([X] =>
    (op: Choice[X]) =>
      op match
        case Choice.Choose(options) => options.head)

// ---- the programs, each for any row that has what it asks

def get[R[_]](using In[State, R]): Long ! R = ask(Get)
def put[R[_]](n: Long)(using In[State, R]): Long ! R = ask(Put(n)).map(_ => n)
def bump[R[_]](using In[State, R]): Long ! R = for x <- get; _ <- put(x + 1) yield x
def stopped[R[_]](using In[State, R], In[Refusal, R]): Long ! R =
  for _ <- put(1); x <- get; _ <- ask(Refuse(s"stop at $x")); y <- put(9) yield y
def lost[R[_]](using In[State, R], In[Refusal, R]): Long ! R = for _ <- put(1); _ <- ask(Refuse("no")) yield 0L
def timed[R[_]](using In[State, R], In[Clock, R]): Long ! R =
  for t <- ask(Clock.Now); _ <- put(t); x <- get yield x + t
def forked[R[_]](using In[State, R], In[Choice, R]): Long ! R =
  for _ <- put(1); x <- ask(Choice.Choose(List(10L, 20L))); y <- get; _ <- put(y + x); z <- get yield z

type Only[X] = (Empty + State)[X]
def held[A](r: (A, Long)): String = s"${r._1}, cell ${r._2}"
def outcome[A](r: Outcome[A]): String = r.fold(why => s"refused($why)", a => s"paid($a)")
def seen(p: Long ! Only): String =
  val ((lines, a), s) = run(cell(0)(logged(p)))
  s"${lines.mkString(" ")} => $a, cell $s"

@main def laws(): Unit =
  println(s"1 done then f: ${seen(Return(5L).flatMap(put[Only]))} = ${seen(put(5))}")
  println(s"2 p then done: ${seen(bump[Only].flatMap(Return(_)))} = ${seen(bump)}")
  val (f, g) = ((x: Long) => put[Only](x + 1), (y: Long) => put[Only](y * 2).map(_ => y))
  println(s"3 grouping: ${seen(get[Only].flatMap(f).flatMap(g))} = ${seen(get[Only].flatMap(x => f(x).flatMap(g)))}")

  println(s"4 handling done: ${held(run(cell(3)(Return(7): Int ! Only)))} | " +
    outcome(run(refusals(Return(7): Int ! (Empty + Refusal)))))
  val whole = cell(0)(bump[Only].flatMap(a => bump[Only].map(a + _)))
  val pieces = cell(0)(bump[Only]).flatMap((a, s) => cell(s)(bump[Only]).map((b, s2) => (a + b, s2)))
  println(s"5 handling a sequence: ${held(run(whole))} = ${held(run(pieces))}")

  val ((lines, r), s) = run(cell(0)(logged(refusals(stopped[Empty + State + Refusal]))))
  println(s"6 its own and no other's: ${lines.mkString(" ")} => ${outcome(r)}, cell $s")

  // 7. `run` takes the empty row; the compiler is asked, while it compiles this file, about the other program
  val errors = scala.compiletime.testing.typeCheckErrors("run(cell(0)(timed[Empty + Clock + State]))")
  assert(errors.nonEmpty, "a program with the clock left in its row was run")
  val handled = "run(fixedClock(7)(cell(0)(timed[Empty + Clock + State])))"
  assert(scala.compiletime.testing.typeCheckErrors(handled).isEmpty, "the same with its clock should compile")
  println("7 nobody answers: does not compile: run takes the empty row, and Clock is left in this one")

  val inside = outcome(run(refusals(cell(0)(lost[Empty + Refusal + State]))).map(held))
  val (refused, after) = run(cell(0)(refusals(lost[Empty + State + Refusal])))
  println(s"8 state then failure: $inside | failure then state: ${outcome(refused)}, cell $after")

  println(s"9 two that only answer: ${held(run(cell(0)(fixedClock(7)(timed[Empty + State + Clock]))))} = " +
    held(run(fixedClock(7)(cell(0)(timed[Empty + Clock + State])))))

  val each = run(everyChoice(cell(0)(forked[Empty + Choice + State])))
  println(s"10 twice, state inside: ${each.map(held).mkString(" | ")}")
  val (paths, last) = run(cell(0)(everyChoice(forked[Empty + State + Choice])))
  println(s"11 twice, state outside: ${paths.mkString(" | ")}, cell $last")

  val afterPut = run(cell(0)(put[Only](3).flatMap(_ => get)))._1
  val lastPut = run(cell(0)(put[Only](3).flatMap(_ => put(4)).flatMap(_ => get)))._1
  val (x, y) = run(cell(0)(for x <- get[Only]; y <- get[Only] yield (x, y)))._1
  val putBack = run(cell(5)(get[Only].flatMap(x => put(x))))._2
  println(s"12 the cell's own: $afterPut $lastPut $x=$y cell $putBack")

// ---- the laws on generated programs

/** a term as a program, for any row that has the four effects: the row is chosen where it is handled */
def build[R[_]](t: Term, x: Long = 0)(using In[State, R], In[Clock, R], In[Refusal, R], In[Choice, R]): Long ! R =
  t match
    case Term.Lit(n)       => Return(n)
    case Term.Var          => Return(x)
    case Term.Get          => get
    case Term.Now          => ask(Clock.Now)
    case Term.Put(t)       => build(t, x).flatMap(put(_))
    case Term.Refuse(t)    => build(t, x).flatMap(n => ask(Refuse(s"at $n")))
    case Term.Choose(a, b) => ask(Choice.Choose(List(a, b)))
    case Term.Add(a, b)    => build(a, x).flatMap(n => build(b, x).map(n + _))
    case Term.Then(a, b)   => build(a, x).flatMap(n => build(b, n))
    case Term.IfOdd(c, a, b) => build(c, x).flatMap(n => build(if n % 2 != 0 then a else b, x))

/** the row most checks use: the clock handled first, then the state, the refusal, the choice */
type All[X] = (Empty + Choice + Refusal + State + Clock)[X]
type Rest[X] = (Empty + Choice + Refusal)[X]

/** every path of a program whose clock and state have been handled, and how each ended */
def ends[A](p: A ! Rest): List[Outcome[A]] = run(everyChoice(refusals(p)))

/** WHAT IS SEEN OF A PROGRAM. Stage 4's nodes cannot be compared as chapter 3's tree can: `m.flatMap(f)` is
 * a `Bind` node holding a function. So two programs are compared by what handlers make of them, from two
 * contents of the cell: every path with the questions it asked, the cell after it, and how it ended. That
 * is weaker than comparing trees, and it is what a user of the kernel can see */
def observed(p: Long ! All): String =
  List(0L, 3L).map(s => ends(cell(s)(logged(fixedClock(42)(p)))).toString).mkString(" / ")

@main def generated(): Unit =
  val terms = Terms(20261118)
  val cases = List.fill(1000)((terms.term(3), terms.term(2), terms.term(2), terms.below(4).toLong))
  var checked = 0
  def law(name: String)(holds: ((Term, Term, Term, Long)) => Boolean): Unit =
    cases.find(c => !holds(c)).foreach(c => sys.error(s"$name FAILS for $c"))
    checked += 1
    println(s"holds for ${cases.size}: $name")
  def same(p: Long ! All, q: Long ! All): Boolean = observed(p) == observed(q)
  def timed(t: Term, x: Long = 0): Long ! (Rest + State) = fixedClock(42)(build[All](t, x))

  law("done then f is f")((_, tf, _, a) => same(Return(a).flatMap(build[All](tf, _)), build(tf, a)))
  law("p then done is p")((tm, _, _, _) => same(build[All](tm).flatMap(Return(_)), build(tm)))
  law("grouping does not matter")((tm, tf, tg, _) =>
    val (m, f, g) = (build[All](tm), build[All](tf, _), build[All](tg, _))
    same(m.flatMap(f).flatMap(g), m.flatMap(x => f(x).flatMap(g))))

  law("handling a sequence under the cell: in two pieces, the content passed on")((tm, tf, _, s) =>
    ends(cell(s)(timed(tm).flatMap(timed(tf, _)))) ==
      ends(cell(s)(timed(tm)).flatMap((a, s1) => cell(s1)(timed(tf, a)))))

  law("a run is what the reference says: the questions seen, the cell, the result")((tm, _, _, s) =>
    val ((lines, result), after) = run(firstChoice(cell(s)(logged(refusals(
      fixedClock(42)(build[Empty + Choice + State + Refusal + Clock](tm)))))))
    Reference.eval(tm, 0, s, Some(42)) == Reference.Ran(lines, after, result))

  law("state inside failure is state outside with the cell forgotten at a refusal")((tm, _, _, s) =>
    val outside = everyChoice(cell(s)(refusals(fixedClock(42)(build[Empty + Choice + State + Refusal + Clock](tm)))))
    ends(cell(s)(timed(tm))) == run(outside).map((r, after) => r.map((_, after))))

  law("state and a fixed clock: either order")((tm, _, _, s) =>
    ends(cell(s)(timed(tm))) == ends(fixedClock(42)(cell(s)(build[Rest + Clock + State](tm)))))

  // ---- stage 4's two handlers of a state: one resumes `k`, one answers in place from a variable
  law("answering in place is resuming at once, on one path")((tm, _, _, s) =>
    def one(state: Long ! (Empty + Refusal + State) => (Long, Long) ! (Empty + Refusal)) =
      run(refusals(state(firstChoice(fixedClock(42)(build[Empty + Refusal + State + Choice + Clock](tm))))))
    one(cell(s)) == one(variable(s)))
  val caught = cases.count((tm, _, _, s) => ends(cell(s)(timed(tm))) != ends(variable(s)(timed(tm))))
  assert(caught > 0)
  println(s"FAILS for $caught of ${cases.size}: the same on every path of a choice")

  law("the observer changes nothing but its own lines")((tm, _, _, s) =>
    ends(cell(s)(logged(timed(tm))).map((r, after) => (r._2, after))) == ends(cell(s)(timed(tm))))

  law("an interceptor that asks every question again is none")((tm, _, _, s) =>
    val again = handle[State, Rest + State, Long, Long](interposed(timed(tm)))(a => Return(a),
      [X] => (op: State[X], k: X => Long ! (Rest + State)) => ask[State, Rest + State, X](op).flatMap(k))
    ends(cell(s)(again)) == ends(cell(s)(timed(tm))))
  println(s"$checked laws hold on ${cases.size} generated programs each")
