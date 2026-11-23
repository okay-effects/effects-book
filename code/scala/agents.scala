// The examples of chapter 12 (§12.9: a supervisor is a handler; a decision that needs the past), run and asserted.
package book

/** what an agent may do: one operation. The answer is what came back: an output, or "error: …" */
case class Tool(name: String, arg: String) extends Op[String]

/** a question to a person */
case class Confirm(call: Tool, because: String) extends Op[Boolean]

/** an agent that does not learn: the same command again, until it passes or it is told something else */
def stubborn(tries: Int): Prog[String] =
  if tries == 0 then done("gave up")
  else ask(Tool("sh", "make test")).flatMap(out => if out.startsWith("error") then stubborn(tries - 1) else done(out))

/** the calls so far, each with how it ended */
type Past = Vector[(Tool, String)]

enum Decision:
  case Go, AskFirst

def failedInRow(past: Past): Int = past.reverseIterator.takeWhile(_._2.startsWith("error")).size

/** the rule: a function of the call AND of what came before it. With no past to read, it does not allow */
def stuck(past: Option[Past], call: Tool): Decision = past match
  case None                                                  => Decision.AskFirst
  case Some(p) if failedInRow(p) >= 3 && call.name != "read" => Decision.AskFirst
  case _                                                     => Decision.Go

/** the supervisor: one more handler. It keeps each call as it went, decides the next one from that past, and
 * either passes the call on or asks a person first. A person's refusal is recorded, and is not a failure */
final class Supervisor[A](rule: (Option[Past], Tool) => Decision) extends Handler[A, A]:
  var past: Past = Vector.empty
  var decided = Vector.empty[Decision]
  def done(a: A) = Prog.Done(a)
  def on[X](op: Op[X], rest: X => Prog[A]) = op match
    case call @ Tool(_, _) =>
      val decision = rule(Some(past), call)
      decided :+= decision
      def forward: Prog[A] = Prog.Ask(call, (out: String) => { past :+= (call, out); rest(out) })
      decision match
        case Decision.Go => Some(forward)
        case Decision.AskFirst =>
          val why = s"${failedInRow(past)} calls failed in a row"
          Some(ask(Confirm(call, why)).flatMap(yes =>
            if yes then forward else { past :+= (call, s"refused: $why"); rest(s"refused: $why") }))
    case _ => None

/** a world in which the tests fail until the given run */
final class Tests[A](passFrom: Int) extends Handler[A, A]:
  var ran = 0
  def done(a: A) = Prog.Done(a)
  def on[X](op: Op[X], rest: X => Prog[A]) = op match
    case Tool(_, _) => ran += 1; Some(rest(if ran >= passFrom then "ok: all pass" else "error: 2 tests fail"))
    case _          => None

final class Person[A](says: Boolean) extends Handler[A, A]:
  var asked = Vector.empty[String]
  def done(a: A) = Prog.Done(a)
  def on[X](op: Op[X], rest: X => Prog[A]) = op match
    case Confirm(call, why) => asked :+= s"${call.name} ${call.arg}: $why"; Some(rest(says))
    case _                  => None

/** the audit: the same rule at every step of a kept journal, each step with the past up to it */
def audit(journal: Past, rule: (Option[Past], Tool) => Decision): Vector[Decision] =
  journal.indices.toVector.map(i => rule(Some(journal.take(i)), journal(i)._1))

@main def agents(): Unit =
  // the agent is stuck: three calls go through, the fourth waits for a person, who says no
  val supervisor = Supervisor[String](stuck)
  val world = Tests[String](passFrom = 100)
  val person = Person[String](says = false)
  val out = run(handle(person)(handle(world)(handle(supervisor)(stubborn(10)))))
  assert(out == "refused: 3 calls failed in a row", out)
  assert(world.ran == 3, "the fourth call never reached the world")
  assert(person.asked == Vector("sh make test: 3 calls failed in a row"))
  assert(supervisor.decided == Vector(Decision.Go, Decision.Go, Decision.Go, Decision.AskFirst))

  // the law: the audit of the kept journal gives exactly the decisions taken live
  assert(audit(supervisor.past, stuck) == supervisor.decided)

  // the same agent in another world: the tests pass on the third run, and nobody is asked
  val calm = Supervisor[String](stuck)
  val nobody = Person[String](says = false)
  val fine = run(handle(nobody)(handle(Tests[String](passFrom = 3))(handle(calm)(stubborn(10)))))
  assert(fine == "ok: all pass" && nobody.asked.isEmpty)
  assert(audit(calm.past, stuck) == calm.decided)

  // where there is no past to read, the rule does not allow
  assert(stuck(None, Tool("read", "README.md")) == Decision.AskFirst)
  println("agent examples hold")
