// THE LAWS OVER GENERATED PROGRAMS (chapter 4), on the forty lines of kernel.scala. The twelve lines of
// laws.scala are twelve programs; here each law is asked of a thousand, drawn by terms.scala.
// `scala-cli run code/scala --main-class book.laws.generated` prints what it checked and how often.
package book.laws

import book.{Handler, Op, Prog, ask, done, handle, run}

/** a term as a program of the forty lines; `x` is what `Var` stands for */
def build(t: Term, x: Long = 0): Prog[Long] = t match
  case Term.Lit(n)       => done(n)
  case Term.Var          => done(x)
  case Term.Get          => ask(Get)
  case Term.Now          => ask(Now)
  case Term.Put(t)       => build(t, x).flatMap(put)
  case Term.Refuse(t)    => build(t, x).flatMap(n => ask(Refuse(s"at $n")))
  case Term.Choose(a, b) => ask(Choose(List(a, b)))
  case Term.Add(a, b)    => build(a, x).flatMap(n => build(b, x).map(n + _))
  case Term.Then(a, b)   => build(a, x).flatMap(n => build(b, n))
  case Term.IfOdd(c, a, b) => build(c, x).flatMap(n => build(if n % 2 != 0 then a else b, x))

/** WHEN ARE TWO PROGRAMS THE SAME? When they are the same tree: the same question at the head, and the same
 * program after every answer. A tree is as wide as its answers' types, so a few answers are tried for each
 * question, and a tree is followed for twelve questions and no further; `shape` writes down what is found,
 * and two programs are compared by what was written. Equal shapes are evidence, not proof */
def answers[X](op: Op[X]): List[X] = op match
  case Get             => List(0L, 7L)
  case Now             => List(42L)
  case Put(_)          => List(())
  case Choose(options) => options
  case _               => Nil

def shape[A](p: Prog[A], questions: Int = 12): String = p match
  case Prog.Done(a) => s"done $a"
  case _ if questions == 0 => "..."
  case ask: Prog.Ask[x, a] =>
    answers(ask.op).map(v => s"$v => ${shape(ask.rest(v), questions - 1)}").mkString(s"${ask.op} {", "; ", "}")

/** the first option: a choice answered once */
final class FirstChoice[A] extends Handler[A, A]:
  def done(a: A) = Prog.Done(a)
  def on[X](op: Op[X], rest: X => Prog[A]) = op match
    case Choose(options) => Some(rest(options.head))
    case _               => None

/** the same answer given twice, the rest called twice: what each call ends in */
final class Again[A] extends Handler[A, List[A]]:
  def done(a: A) = Prog.Done(List(a))
  def on[X](op: Op[X], rest: X => Prog[List[A]]) = op match
    case Choose(options) => Some(rest(options.head).flatMap(as => rest(options.head).map(as ++ _)))
    case _               => None

/** A STATE KEPT IN A VARIABLE OF THE HANDLER, as chapter 3's `InMemory` keeps its balances. It answers a
 * single run correctly. It is here to be caught: see `twice` below */
final class Variable[A](var held: Long) extends Handler[A, (A, Long)]:
  def done(a: A) = Prog.Done((a, held))
  def on[X](op: Op[X], rest: X => Prog[(A, Long)]) = op match
    case Get    => Some(rest(held))
    case Put(n) => held = n; Some(rest(()))
    case _      => None

/** `h`, with its `done` replaced by "go on with `f`, handled": the right-hand side of the fold law */
def andThen[A, B, C](h: Handler[B, C], f: A => Prog[B]): Handler[A, C] = new Handler[A, C]:
  def done(a: A) = handle(h)(f(a))
  def on[X](op: Op[X], rest: X => Prog[C]) = h.on(op, rest)

@main def generated(): Unit =
  val terms = Terms(20261118)
  val cases = List.fill(1000)((terms.term(3), terms.term(2), terms.term(2), terms.below(4).toLong))
  var checked = 0
  def law(name: String)(holds: ((Term, Term, Term, Long)) => Boolean): Unit =
    cases.find(c => !holds(c)).foreach(c => sys.error(s"$name FAILS for $c"))
    checked += 1
    println(s"holds for ${cases.size}: $name")
  def same[A](p: Prog[A], q: Prog[A]): Boolean = shape(p) == shape(q)

  // ---- sequencing
  law("done then f is f")((_, tf, _, a) => same(done(a).flatMap(build(tf, _)), build(tf, a)))
  law("p then done is p")((tm, _, _, _) => same(build(tm).flatMap(done), build(tm)))
  law("grouping does not matter")((tm, tf, tg, _) =>
    val (m, f, g) = (build(tm), build(tf, _), build(tg, _))
    same(m.flatMap(f).flatMap(g), m.flatMap(x => f(x).flatMap(g))))

  // ---- a handler is a fold: handling `m then f` is handling `m` with "then f, handled" as its end
  law("handling a sequence, five handlers")((tm, tf, _, s) =>
    val (m, f) = (build(tm), build(tf, _))
    def fold[C](h: Handler[Long, C]) = same(handle(h)(m.flatMap(f)), handle(andThen(h, f))(m))
    fold(Refusals[Long]()) && fold(FixedClock[Long](42)) && fold(AllChoices[Long]()) && fold(Log[Long]()) &&
      same(cell(s)(m.flatMap(f)), handle(andThen(Cell[Long](), f))(m).flatMap(_(s))))

  // ---- its own questions, every one and in order, and no other's: against the reference
  law("a run is what the reference says: the questions seen, the cell, the result")((tm, _, _, s) =>
    val ((lines, result), after) =
      run(cell(s)(log(refusals(handle(FirstChoice[Long]())(fixedClock(42)(build(tm)))))))
    Reference.eval(tm, 0, s, Some(42)) == Reference.Ran(lines, after, result))
  law("another's question passes a handler untouched")((tm, _, _, s) =>
    // with the clock unhandled, what is left of the program is still stopped at `Now`, wherever it asks it
    val left = cell(s)(refusals(handle(FirstChoice[Long]())(build(tm))))
    val (wanted, got) = (Reference.eval(tm, 0, s, None).result, scala.util.Try(run(left)))
    if wanted == Left("nobody answers Now") then got.failed.toOption.exists(_.getMessage == "nobody answers Now")
    else got.toOption.exists(_._1 == wanted))

  // ---- two effects, two orders
  law("state inside failure is state outside with the cell forgotten at a refusal")((tm, _, _, s) =>
    same(refusals(cell(s)(build(tm))), cell(s)(refusals(build(tm))).map((r, after) => r.map((_, after)))))
  law("state and a fixed clock: either order")((tm, _, _, s) =>
    same(cell(s)(fixedClock(42)(build(tm))), fixedClock(42)(cell(s)(build(tm)))))
  val orders = cases.count((tm, _, _, s) =>
    val p = fixedClock(42)(refusals(build(tm)))
    run(allChoices(cell(s)(p))).map(_._1) != run(cell(s)(allChoices(p)))._1)
  assert(orders > 0)
  println(s"differs for $orders of ${cases.size}: state inside every choice, and state outside it")
  law("state inside and outside every choice agree when nothing is put")((tm, _, _, s) =>
    val p = fixedClock(42)(refusals(build(tm)))
    val puts = tm.has { case Term.Put(_) => true; case _ => false }
    puts || run(allChoices(cell(s)(p))).map(_._1) == run(cell(s)(allChoices(p)))._1)

  // ---- the rest is a value: called twice with one answer it ends twice the same
  def twice(inside: Prog[Long] => Prog[(Long, Long)])(tm: Term): Boolean =
    val ends = run(handle(Again[(Long, Long)]())(inside(fixedClock(42)(handle(Refusals[Long]())(build(tm)).map(
      _.fold(_ => -1L, identity))))))
    ends.distinct.size == 1
  law("the rest called twice with one answer ends twice the same")((tm, _, _, s) => twice(cell(s))(tm))
  val caught = cases.count((tm, _, _, s) => !twice(handle(Variable[Long](s)))(tm))
  assert(caught > 0)
  println(s"FAILS for $caught of ${cases.size}: the same, with the cell in a variable of the handler")
  val line10 = run(allChoices(handle(Variable[Long](0))(forked))).map(held).mkString(" | ")
  assert(line10 != run(allChoices(cell(0)(forked))).map(held).mkString(" | "))
  println(s"and line 10 of the twelve, with the cell in a variable: $line10")

  // ---- what the cell owes its operations, for every rest; and the observer, who is owed nothing
  law("the four equations of a state, under the cell")((tk, _, _, s) =>
    val k = build(tk, _)
    val (n, m) = (s + 1, s + 2)
    same(cell(s)(ask(Put(n)).flatMap(_ => ask(Get)).flatMap(k)), cell(s)(ask(Put(n)).flatMap(_ => k(n)))) &&
      same(cell(s)(ask(Put(n)).flatMap(_ => ask(Put(m))).flatMap(_ => k(0))),
        cell(s)(ask(Put(m)).flatMap(_ => k(0)))) &&
      same(cell(s)(ask(Get).flatMap(x => ask(Get).flatMap(y => k(x + y)))), cell(s)(ask(Get).flatMap(x => k(x + x)))) &&
      same(cell(s)(ask(Get).flatMap(x => ask(Put(x))).flatMap(_ => k(0))), cell(s)(k(0))))
  law("the observer changes nothing but its own lines")((tm, _, _, s) =>
    same(cell(s)(log(build(tm))).map((r, after) => (r._2, after)), cell(s)(build(tm))))
  val putPut = (p: Prog[Unit]) => shape(cell(0)(log(p)))
  assert(putPut(ask(Put(1)).flatMap(_ => ask(Put(2)))) != putPut(ask(Put(2))))
  println("differs: Put(1) ; Put(2) and Put(2), under the observer")
  println(s"$checked laws hold on ${cases.size} generated programs each")
