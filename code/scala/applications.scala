// The examples of chapter 9 (screens, servers and stores as effects), run and asserted:
// `scala-cli run code/scala --main-class book.applications`. On the forty lines of kernel.scala, with `Refuse`
// of examples.scala and `carry`, `refusals` and `allChoices` of catalogue.scala. Everything else is in the
// object `app`, because the package already has other chapters' `Place`, `Insert`, `Journal` and `Shop`.
// A handler that remembers something carries it (chapter 4, section 4.6). Two keep a world on purpose: the
// terminal (`Console`) and a directory of files (`Directory`).
package book

import std.{carry, Carrying, refusals, allChoices, among}

object app:

  def each[A](parts: List[Prog[A]]): Prog[List[A]] =
    parts.foldRight(done(List.empty[A]))((p, later) => p.flatMap(a => later.map(a :: _)))

  // ---- 1. a screen. What a person entered, by the label it was entered under

  type Filled = Map[String, String]

  /** a form is a VALUE: its fields, and for each what it accepts, as data and not as a function */
  enum Kind:
    case Words, Positive
  case class Field(label: String, kind: Kind = Kind.Words):
    def problem(text: String): Option[String] = kind match
      case _ if text.isBlank                                 => Some(s"$label is needed")
      case Kind.Positive if !text.toLongOption.exists(_ > 0) => Some(s"$label must be a number above zero")
      case _                                                 => None
  case class Form(title: String, fields: List[Field]):
    def problems(filled: Filled): Vector[String] =
      fields.flatMap(f => f.problem(filled.getOrElse(f.label, ""))).toVector

  case class Show(text: String) extends Op[Unit]
  case class Enter(label: String) extends Op[String]
  case class Press(buttons: List[String]) extends Op[String]
  /** a whole form in one question: the screen as data */
  case class Fill(form: Form) extends Op[Filled]

  /** a screen as a SEQUENCE of questions: the program checks, and asks again */
  def entered(field: Field): Prog[String] = ask(Enter(field.label)).flatMap(text =>
    field.problem(text) match
      case None          => done(text)
      case Some(problem) => ask(Show(problem)).flatMap(_ => entered(field)))

  val orderForm = Form("New order", List(Field("customer"), Field("item"), Field("quantity", Kind.Positive)))

  val byQuestions: Prog[Filled] =
    ask(Show("New order")).flatMap(_ => each(orderForm.fields.map(f => entered(f).map(f.label -> _)))).map(_.toMap)

  /** a form lowered into questions, for a handler that can only ask one thing at a time: a handler of `Fill`
   * that asks `Show` and `Enter` of whoever is outside */
  final class FieldByField[A] extends Handler[A, A]:
    def done(a: A) = Prog.Done(a)
    def on[X](op: Op[X], rest: X => Prog[A]) = op match
      case Fill(form) =>
        Some(ask(Show(form.title))
          .flatMap(_ => each(form.fields.map(f => entered(f).map(f.label -> _))))
          .flatMap(pairs => rest(pairs.toMap)))
      case _ => None
  def fieldByField[A](p: Prog[A]): Prog[A] = handle(FieldByField[A]())(p)

  /** a terminal: IN PLACE. A real shared world, so two functions and no state of its own: `say` prints, `hear`
   * reads a line. `Console(println, () => scala.io.StdIn.readLine())` is the real one; it is not run here */
  final class Console[A](say: String => Unit, hear: () => String) extends Handler[A, A]:
    def done(a: A) = Prog.Done(a)
    def on[X](op: Op[X], rest: X => Prog[A]) = op match
      case Show(text)   => say(text); Some(rest(()))
      case Enter(label) => say(s"$label:"); Some(rest(hear()))
      case Press(buttons) =>
        def pressed(): String =
          say(buttons.map(b => s"[$b]").mkString(" "))
          val line = hear()
          if buttons.contains(line) then line else pressed()
        Some(rest(pressed()))
      case _ => None

  /** a person by script: IN PLACE, carrying the acts left and what was shown. An act is a label and what is
   * entered under it, or `press` and a button. An act that does not fit the question is a refusal */
  final class Scripted[A] extends Carrying[(List[(String, String)], Vector[String]), A, (Vector[String], A)]:
    type S = (List[(String, String)], Vector[String])
    def end(s: S, a: A) = done((s._2, a))
    def on[X](op: Op[X], rest: (X, S) => Prog[(Vector[String], A)]) =
      def act(asks: String, fits: String => Boolean)(go: (String, S) => Prog[(Vector[String], A)]) =
        Some((s: S) => s._1 match
          case (label, value) :: more if label == asks && fits(value) => go(value, (more, s._2))
          case (label, value) :: _ => ask(Refuse(s"the script has $label = $value, the screen asks $asks"))
          case Nil                 => ask(Refuse(s"the script has ended, the screen asks $asks")))
      op match
        case Show(text)     => Some(s => rest((), (s._1, s._2 :+ text)))
        case Enter(label)   => act(label, _ => true)(rest)
        case Press(buttons) => act("press", buttons.contains)(rest)
        case _              => None
  def scripted[A](acts: (String, String)*)(p: Prog[A]): Prog[(Vector[String], A)] =
    carry(Scripted[A](), (acts.toList, Vector.empty))(p)

  /** what a page asks */
  enum Question:
    case Entering(label: String)
    case Pressing(buttons: List[String])
    case Filling(form: Form)

  /** where a program stands for a caller that must return: ended, or at a question WITH THE REST IN HAND */
  enum Page[+A]:
    case Ended(shown: Vector[String], a: A)
    case Waiting(shown: Vector[String], asks: Question, resume: Filled => Prog[Page[A]])

  /** a page at a time: LATER. At a question the rest is not called; it is the result. What was shown since the
   * last question is carried. An answer that does not fit gives the same page again, with the problem */
  final class Paged[A] extends Carrying[Vector[String], A, Page[A]]:
    def end(shown: Vector[String], a: A) = done(Page.Ended(shown, a))
    def on[X](op: Op[X], rest: (X, Vector[String]) => Prog[Page[A]]) =
      def page(asks: Question)(read: Filled => Either[Vector[String], X]) =
        def at(shown: Vector[String]): Prog[Page[A]] =
          done(Page.Waiting(shown, asks, answers => read(answers).fold(at, x => rest(x, Vector.empty))))
        Some(at)
      op match
        case Show(text)   => Some(shown => rest((), shown :+ text))
        case Enter(label) => page(Question.Entering(label))(answers => Right(answers.getOrElse(label, "")))
        case Press(buttons) =>
          page(Question.Pressing(buttons))(_.get("press").filter(buttons.contains)
            .toRight(Vector(s"press one of ${buttons.mkString(", ")}")))
        case Fill(form) =>
          page(Question.Filling(form))(answers => Some(form.problems(answers)).filter(_.nonEmpty).toLeft(answers))
        case _ => None
  def paged[A](p: Prog[A]): Prog[Page[A]] = carry(Paged[A](), Vector.empty)(p)

  /** answer a waiting page */
  def answer[A](page: Page[A], answers: Filled): Prog[Page[A]] = page match
    case Page.Waiting(_, _, resume) => resume(answers)
    case ended                      => done(ended)

  // ---- 2. a store

  case class Row(key: String, fields: Map[String, String])

  case class Find(table: String, key: String) extends Op[Option[Row]]
  case class Save(table: String, row: Row) extends Op[Unit]
  /** the rows of a table with this value in this column, by key */
  case class Query(table: String, column: String, value: String) extends Op[List[Row]]
  /** several keys in one question: what a batching handler asks */
  case class FindMany(table: String, keys: List[String]) extends Op[List[Row]]

  type Tables = Map[String, Map[String, Row]]
  def matching(rows: Iterable[Row], column: String, value: String): List[Row] =
    rows.filter(_.fields.get(column).contains(value)).toList.sortBy(_.key)

  /** in memory: the tables carried */
  final class InTables[A] extends Carrying[Tables, A, (A, Tables)]:
    def end(tables: Tables, a: A) = done((a, tables))
    def on[X](op: Op[X], rest: (X, Tables) => Prog[(A, Tables)]) = op match
      case Find(table, key) => Some(tables => rest(tables.get(table).flatMap(_.get(key)), tables))
      case Save(table, row) =>
        Some(tables => rest((), tables.updated(table, tables.getOrElse(table, Map.empty).updated(row.key, row))))
      case Query(table, column, value) =>
        Some(tables => rest(matching(tables.getOrElse(table, Map.empty).values, column, value), tables))
      case FindMany(table, keys) =>
        Some(tables => rest(keys.flatMap(tables.getOrElse(table, Map.empty).get).sortBy(_.key), tables))
      case _ => None
  def inTables[A](tables: Tables)(p: Prog[A]): Prog[(A, Tables)] = carry(InTables[A](), tables)(p)

  /** journalled: nothing is overwritten. A save is a line; a row is the last line of its key */
  final class InJournal[A] extends Carrying[Vector[(String, Row)], A, (A, Vector[(String, Row)])]:
    type J = Vector[(String, Row)]
    private def now(lines: J, table: String): Map[String, Row] =
      lines.collect { case (t, row) if t == table => row.key -> row }.toMap
    def end(lines: J, a: A) = done((a, lines))
    def on[X](op: Op[X], rest: (X, J) => Prog[(A, J)]) = op match
      case Find(table, key)            => Some(lines => rest(now(lines, table).get(key), lines))
      case Save(table, row)            => Some(lines => rest((), lines :+ (table, row)))
      case Query(table, column, value) => Some(lines => rest(matching(now(lines, table).values, column, value), lines))
      case FindMany(table, keys) =>
        Some(lines => rest(keys.flatMap(now(lines, table).get).sortBy(_.key), lines))
      case _ => None
  def inJournal[A](p: Prog[A]): Prog[(A, Vector[(String, Row)])] = carry(InJournal[A](), Vector.empty)(p)

  /** the level below: a statement as DATA, its values beside it and never in its text */
  case class Sql(text: String, args: List[String]) extends Op[List[Row]]

  /** SQL as text: each question of the store becomes a statement asked of whoever runs SQL. Names of tables
   * and columns do go into the text, so this is the one place that checks them */
  final class AsSql[A] extends Handler[A, A]:
    private def named(names: String*)(text: => Prog[A]): Prog[A] =
      names.find(!_.matches("[a-z_]+")).fold(text)(n => ask(Refuse(s"not a name: $n")))
    private def marks(n: Int) = List.fill(n)("?").mkString(", ")
    def done(a: A) = Prog.Done(a)
    def on[X](op: Op[X], rest: X => Prog[A]) = op match
      case Find(table, key) =>
        Some(named(table)(
          ask(Sql(s"SELECT * FROM $table WHERE key = ?", List(key))).flatMap(rows => rest(rows.headOption))))
      case Save(table, Row(key, fields)) =>
        val columns = fields.keys.toList.sorted
        Some(named(table :: columns*)(ask(Sql(
          s"INSERT INTO $table (key, ${columns.mkString(", ")}) VALUES (${marks(columns.size + 1)}) " +
            s"ON CONFLICT (key) DO UPDATE SET ${columns.map(c => s"$c = excluded.$c").mkString(", ")}",
          key :: columns.map(fields))).flatMap(_ => rest(()))))
      case Query(table, column, value) =>
        Some(named(table, column)(
          ask(Sql(s"SELECT * FROM $table WHERE $column = ? ORDER BY key", List(value))).flatMap(rest)))
      case FindMany(table, keys) =>
        Some(named(table)(
          ask(Sql(s"SELECT * FROM $table WHERE key IN (${marks(keys.size)}) ORDER BY key", keys)).flatMap(rest)))
      case _ => None
  def asSql[A](p: Prog[A]): Prog[A] = handle(AsSql[A]())(p)

  /** no database: every statement is kept as it was said, and answered with no rows */
  type Said = Vector[(String, List[String])]
  final class NoDatabase[A] extends Carrying[Said, A, (Said, A)]:
    def end(said: Said, a: A) = done((said, a))
    def on[X](op: Op[X], rest: (X, Said) => Prog[(Said, A)]) = op match
      case Sql(text, args) => Some(said => rest(Nil, said :+ (text, args)))
      case _               => None
  def noDatabase[A](p: Prog[A]): Prog[(Said, A)] = carry(NoDatabase[A](), Vector.empty)(p)

  /** anything that claims to answer the store */
  trait AStore:
    def apply[A](p: Prog[A]): A
  val tables: AStore = new AStore:
    def apply[A](p: Prog[A]) = run(inTables(Map.empty)(p))._1
  val journal: AStore = new AStore:
    def apply[A](p: Prog[A]) = run(inJournal(p))._1
  val sqlOverNothing: AStore = new AStore:
    def apply[A](p: Prog[A]) = run(noDatabase(asSql(p)))._2

  /** what a store owes: you find what was saved; the last save wins; a query finds it; many is each */
  def storeOwes(h: AStore): List[Boolean] =
    val tea = Row("tea", Map("name" -> "green tea", "shelf" -> "a"))
    val tea2 = Row("tea", Map("name" -> "black tea", "shelf" -> "a"))
    val cup = Row("cup", Map("name" -> "a cup", "shelf" -> "a"))
    val both = ask(Save("products", tea)).flatMap(_ => ask(Save("products", cup)))
    List(
      h(ask(Save("products", tea)).flatMap(_ => ask(Find("products", "tea")))) == Some(tea),
      h(ask(Save("products", tea)).flatMap(_ => ask(Save("products", tea2))).flatMap(_ => ask(Find("products", "tea"))))
        == h(ask(Save("products", tea2)).flatMap(_ => ask(Find("products", "tea")))),
      h(both.flatMap(_ => ask(Query("products", "shelf", "a")))) == List(cup, tea),
      h(both.flatMap(_ => ask(FindMany("products", List("tea", "cup"))))) ==
        h(both.flatMap(_ => each(List("cup", "tea").map(k => ask(Find("products", k)))))).flatten)

  /** a transaction as a handler around a PART: the part's saves are carried in a log and not asked; its reads
   * go through the log; ON ITS `done` LINE the log is asked of whoever is outside. A part that does not reach
   * `done` has written nothing */
  final class Atomically[A] extends Carrying[Vector[(String, Row)], A, A]:
    type L = Vector[(String, Row)]
    private def mine(log: L, table: String): Map[String, Row] =
      log.collect { case (t, row) if t == table => row.key -> row }.toMap
    private def over(log: L, table: String, outside: List[Row], keep: Row => Boolean): List[Row] =
      (outside.filterNot(r => mine(log, table).contains(r.key)) ++ mine(log, table).values.filter(keep)).sortBy(_.key)
    def end(log: L, a: A) = each(log.toList.map((table, row) => ask(Save(table, row)))).map(_ => a)
    def on[X](op: Op[X], rest: (X, L) => Prog[A]) = op match
      case Save(table, row) => Some(log => rest((), log :+ (table, row)))
      case Find(table, key) =>
        Some(log => mine(log, table).get(key).fold(ask(op).flatMap(found => rest(found, log)))(r => rest(Some(r), log)))
      case Query(table, column, value) =>
        Some(log => ask(op).flatMap(out => rest(over(log, table, out, _.fields.get(column).contains(value)), log)))
      case FindMany(table, keys) =>
        Some(log => ask(op).flatMap(out => rest(over(log, table, out, r => keys.contains(r.key)), log)))
      case _ => None
  def atomically[A](part: Prog[A]): Prog[A] = carry(Atomically[A](), Vector.empty)(part)

  /** an observer: every question of the store asked again, its name kept */
  final class Seen[A] extends Carrying[Vector[String], A, (Vector[String], A)]:
    def end(seen: Vector[String], a: A) = done((seen, a))
    def on[X](op: Op[X], rest: (X, Vector[String]) => Prog[(Vector[String], A)]) = op match
      case _: Find | _: Save | _: Query | _: FindMany =>
        Some(seen => ask(op).flatMap(x => rest(x, seen :+ op.getClass.getSimpleName)))
      case _ => None
  def seen[A](p: Prog[A]): Prog[(Vector[String], A)] = carry(Seen[A](), Vector.empty)(p)

  /** a part stopped at a `Find`, with its rest */
  enum Parked[+A]:
    case Ended(a: A)
    case AtFind(table: String, key: String, resume: Option[Row] => Prog[Parked[A]])
  final class Parking[A] extends Handler[A, Parked[A]]:
    def done(a: A) = Prog.Done(Parked.Ended(a))
    def on[X](op: Op[X], rest: X => Prog[Parked[A]]) = op match
      case Find(table, key) => Some(Prog.Done(Parked.AtFind(table, key, rest)))
      case _                => None

  /** BATCHING: parts that do not depend on each other, each run to its first `Find` and stopped there. The
   * rests are kept, ONE `FindMany` is asked for all the keys of a table, and each rest is resumed with its
   * own row. Then again, until every part has ended */
  def together[A](parts: List[Prog[A]]): Prog[List[A]] =
    def round(parked: List[Parked[A]]): Prog[List[A]] =
      parked.collectFirst { case Parked.AtFind(table, _, _) => table } match
        case None => done(parked.collect { case Parked.Ended(a) => a })
        case Some(table) =>
          val keys = parked.collect { case Parked.AtFind(t, key, _) if t == table => key }.distinct
          ask(FindMany(table, keys)).flatMap { rows =>
            val byKey = rows.map(r => r.key -> r).toMap
            each(parked.map {
              case Parked.AtFind(t, key, resume) if t == table => resume(byKey.get(key))
              case other                                       => done(other)
            }).flatMap(round)
          }
    each(parts.map(handle(Parking[A]()))).flatMap(round)

  def line(order: Row): Prog[String] =
    ask(Find("products", order.fields("item")))
      .map(product => s"${order.fields("quantity")} x ${product.fold("?")(_.fields("name"))}")
  def lineWithMaker(order: Row): Prog[String] =
    for
      product <- ask(Find("products", order.fields("item")))
      maker   <- ask(Find("makers", product.fold("?")(_.fields("maker"))))
    yield s"${order.fields("item")} by ${maker.fold("?")(_.fields("name"))}"

  def reportOneByOne(customer: String): Prog[List[String]] =
    ask(Query("orders", "customer", customer)).flatMap(orders => each(orders.map(line)))
  def report(customer: String, line: Row => Prog[String] = line): Prog[List[String]] =
    ask(Query("orders", "customer", customer)).flatMap(orders => together(orders.map(line)))

  // ---- 3. a server

  /** a platform: what a program may ask of where it runs */
  case class KvGet(key: String) extends Op[Option[String]]
  case class KvPut(key: String, value: String) extends Op[Unit]
  case class Fetch(url: String) extends Op[String]

  /** in memory, for tests: the keys carried, the pages of the outside world given */
  final class Memory[A](pages: Map[String, String]) extends Carrying[Map[String, String], A, (A, Map[String, String])]:
    def end(kv: Map[String, String], a: A) = done((a, kv))
    def on[X](op: Op[X], rest: (X, Map[String, String]) => Prog[(A, Map[String, String])]) = op match
      case KvGet(key)        => Some(kv => rest(kv.get(key), kv))
      case KvPut(key, value) => Some(kv => rest((), kv.updated(key, value)))
      case Fetch(url)        => Some(kv => pages.get(url).fold(ask(Refuse(s"nothing at $url")))(rest(_, kv)))
      case _                 => None
  def memory[A](kv: Map[String, String], pages: Map[String, String] = Map.empty)(p: Prog[A]) =
    carry(Memory[A](pages), kv)(p)

  /** a directory of files: a real world, so nothing is carried and a new handler on the same directory finds
   * what an earlier one wrote. It has no network, and says so */
  final class Directory[A](dir: java.nio.file.Path) extends Handler[A, A]:
    private def file(key: String) = dir.resolve(java.net.URLEncoder.encode(key, "UTF-8"))
    def done(a: A) = Prog.Done(a)
    def on[X](op: Op[X], rest: X => Prog[A]) = op match
      case KvGet(key) =>
        Some(rest(Option.when(java.nio.file.Files.exists(file(key)))(java.nio.file.Files.readString(file(key)))))
      case KvPut(key, value) => java.nio.file.Files.writeString(file(key), value); Some(rest(()))
      case Fetch(url)        => Some(ask(Refuse(s"no network on this platform: $op")))
      case _                 => None
  def directory[A](dir: java.nio.file.Path)(p: Prog[A]): Prog[A] = handle(Directory[A](dir))(p)

  /** anything that claims to be a platform, started empty */
  trait APlatform:
    def apply[A](p: Prog[A]): A
  def kvOwes(h: APlatform): List[Boolean] = List(
    h(ask(KvPut("k", "v")).flatMap(_ => ask(KvGet("k")))) == Some("v"),
    h(ask(KvPut("k", "v")).flatMap(_ => ask(KvPut("k", "w"))).flatMap(_ => ask(KvGet("k")))) == Some("w"),
    h(ask(KvGet("never put"))) == None)

  /** what the world asks of an application, and what it is answered */
  case class Request(session: Option[String], answers: Filled)
  case class Response(session: String, shown: Vector[String], asks: Option[Question])
  case class Visit(request: Request) extends Op[Response]

  def respond[A](session: String, page: Page[A]): Response = page match
    case Page.Waiting(shown, asks, _) => Response(session, shown, Some(asks))
    case Page.Ended(shown, a)         => Response(session, shown :+ s"done: $a", None)
  def gone(session: String) = Response(session, Vector("no such session"), None)

  /** a client: it opens a session, then sends each act to it */
  def visits(acts: Filled*): Prog[List[Response]] =
    ask(Visit(Request(None, Map.empty))).flatMap(first => later(first.session, acts*).map(first :: _))
  def later(session: String, acts: Filled*): Prog[List[Response]] =
    each(acts.toList.map(act => ask(Visit(Request(Some(session), act)))))

  /** A HOST THAT KEEPS RESTS: the rest of each session's program, in a table carried by the handler, as the
   * scheduler of chapter 8 carried a queue. The rests are closures: they live as long as this run */
  final class Hosted[C](program: => Prog[String]) extends Carrying[Map[String, Filled => Prog[Page[String]]], C, C]:
    type S = Map[String, Filled => Prog[Page[String]]]
    private def kept(sessions: S, id: String, page: Page[String]): S = page match
      case Page.Waiting(_, _, resume) => sessions.updated(id, resume)
      case Page.Ended(_, _)           => sessions - id
    def end(sessions: S, c: C) = done(c)
    def on[X](op: Op[X], rest: (X, S) => Prog[C]) = op match
      case Visit(Request(None, _)) =>
        Some(sessions => paged(program).flatMap { page =>
          val id = s"s${sessions.size + 1}"
          rest(respond(id, page), kept(sessions, id, page))
        })
      case Visit(Request(Some(id), answers)) =>
        Some(sessions => sessions.get(id) match
          case None         => rest(gone(id), sessions)
          case Some(resume) => resume(answers).flatMap(page => rest(respond(id, page), kept(sessions, id, page))))
      case _ => None
  def hosted[C](program: => Prog[String])(client: Prog[C]): Prog[C] = carry(Hosted[C](program), Map.empty)(client)

  /** a new session's name, from a counter on the platform. Read and written in two steps: two hosts at once
   * could take the same one; a platform's commit that checks what was read, or a random name, is the remedy */
  val fresh: Prog[String] = ask(KvGet("sessions")).flatMap { n =>
    val next = n.fold(0)(_.toInt) + 1
    ask(KvPut("sessions", next.toString)).map(_ => s"s$next")
  }

  def writeAct(act: Filled): String = act.toList.sorted.map((k, v) => s"$k=$v").mkString("&")
  def readAct(text: String): Filled =
    text.split('&').toList.filter(_.nonEmpty).map(_.split("=", 2)).collect { case Array(k, v) => k -> v }.toMap

  def writeRows(rows: List[Row]): String = rows.map(r => s"${r.key}?${writeAct(r.fields)}").mkString(";")
  def readRows(text: String): List[Row] =
    text.split(';').toList.filter(_.nonEmpty).map(_.split("\\?", 2)).collect { case Array(k, f) => Row(k, readAct(f)) }

  /** the store's answers of one session, journalled on the platform by their position: the n-th question of
   * the store is answered from the journal if it has an n-th answer, and asked and written down if not.
   * The position is carried; on a replay it counts again from nothing. Answers are text, read back as the
   * type their question is answered with */
  final class Recalled[A](id: String) extends Carrying[Int, A, A]:
    def end(n: Int, a: A) = done(a)
    def on[X](op: Op[X], rest: (X, Int) => Prog[A]) =
      def recalled(write: X => String, read: String => X) = Some((n: Int) =>
        ask(KvGet(s"answers/$id/$n")).flatMap {
          case Some(text) => rest(read(text), n + 1)
          case None => ask(op).flatMap(x => ask(KvPut(s"answers/$id/$n", write(x))).flatMap(_ => rest(x, n + 1)))
        })
      op match
        case _: Find     => recalled(found => writeRows(found.toList), readRows(_).headOption)
        case _: Save     => recalled(_ => "", _ => ())
        case _: Query    => recalled(writeRows, readRows)
        case _: FindMany => recalled(writeRows, readRows)
        case _           => None

  /** A HOST THAT KEEPS ANSWERS, and nothing in memory: a session is the acts it was sent, on the platform.
   * Each visit runs the program AGAIN from its first line and feeds them back: resuming by replay. With
   * `world`, what the store answered is journalled too; without, only what the person answered */
  final class Replayed[C](program: => Prog[String], world: Boolean) extends Handler[C, C]:
    private def journal(id: String) = s"journal/$id"
    private def served(id: String, acts: Vector[Filled], rest: Response => Prog[C]): Prog[C] =
      val again = if world then carry(Recalled[String](id), 0)(program) else program
      for
        _    <- ask(KvPut(journal(id), acts.map(writeAct).mkString("\n")))
        page <- acts.foldLeft(paged(again))((at, act) => at.flatMap(answer(_, act)))
        out  <- rest(respond(id, page))
      yield out
    def done(c: C) = Prog.Done(c)
    def on[X](op: Op[X], rest: X => Prog[C]) = op match
      case Visit(Request(None, _)) => Some(fresh.flatMap(id => served(id, Vector.empty, rest)))
      case Visit(Request(Some(id), answers)) =>
        Some(ask(KvGet(journal(id))).flatMap {
          case None       => rest(gone(id))
          case Some(text) => served(id, text.linesIterator.map(readAct).toVector :+ answers, rest)
        })
      case _ => None
  def replayed[C](program: => Prog[String])(client: Prog[C]): Prog[C] =
    handle(Replayed[C](program, world = true))(client)
  def replayedPersonOnly[C](program: => Prog[String])(client: Prog[C]): Prog[C] =
    handle(Replayed[C](program, world = false))(client)

  // ---- 3 and 4. the application, and its rest as data

  /** the order desk: a form (data), a confirmation that shows what was entered, a save, and again or not */
  def summary(order: Filled): String = s"${order("quantity")} x ${order("item")} for ${order("customer")}"

  def desk: Prog[String] =
    for
      order <- ask(Fill(orderForm))
      _     <- ask(Show(summary(order)))
      how   <- ask(Press(List("Save", "Cancel")))
      out   <- if how == "Cancel" then done("cancelled") else place(order)
    yield out

  def keep(order: Filled): Prog[String] =
    for
      mine <- ask(Query("orders", "customer", order("customer")))
      key = s"${order("customer")}-${mine.size + 1}"
      _ <- ask(Save("orders", Row(key, order)))
    yield key

  def place(order: Filled): Prog[String] =
    for
      key  <- keep(order)
      _    <- ask(Show(s"saved as $key"))
      more <- ask(Press(List("Another", "Finish")))
      out  <- if more == "Another" then desk else done(key)
    yield out

  /** THE REST OF `desk` AS DATA: which stop, and the variables alive there. Written by hand */
  enum At:
    case AtForm
    case AtConfirm(order: Filled)
    case AtMore(key: String)

  def asks(at: At): Question = at match
    case At.AtForm       => Question.Filling(orderForm)
    case At.AtConfirm(_) => Question.Pressing(List("Save", "Cancel"))
    case At.AtMore(_)    => Question.Pressing(List("Another", "Finish"))

  /** one turn: from a stop and an answer to the next stop and what to show there, or to the end */
  def turn(at: At, answers: Filled): Prog[Either[String, (At, Vector[String])]] =
    def again(buttons: String) = done(Right((at, Vector(s"press one of $buttons"))))
    (at, answers.get("press")) match
      case (At.AtForm, _) =>
        val problems = orderForm.problems(answers)
        val order = answers.view.filterKeys(orderForm.fields.map(_.label).contains).toMap
        if problems.nonEmpty then done(Right((at, problems)))
        else done(Right((At.AtConfirm(order), Vector(summary(order)))))
      case (At.AtConfirm(_), Some("Cancel")) => done(Left("cancelled"))
      case (At.AtConfirm(order), Some("Save")) =>
        keep(order).map(key => Right((At.AtMore(key), Vector(s"saved as $key"))))
      case (At.AtConfirm(_), _)                => again("Save, Cancel")
      case (At.AtMore(_), Some("Another"))     => done(Right((At.AtForm, Vector.empty)))
      case (At.AtMore(key), Some("Finish"))    => done(Left(key))
      case (At.AtMore(_), _)                   => again("Another, Finish")

  def writeAt(at: At): String = at match
    case At.AtForm           => "AtForm"
    case At.AtConfirm(order) => s"AtConfirm|${writeAct(order)}"
    case At.AtMore(key)      => s"AtMore|$key"
  def readAt(text: String): Option[At] = text.split("\\|", 2) match
    case Array("AtForm")           => Some(At.AtForm)
    case Array("AtConfirm", order) => Some(At.AtConfirm(readAct(order)))
    case Array("AtMore", key)      => Some(At.AtMore(key))
    case _                         => None

  /** A HOST THAT KEEPS THE STATE: a session is one line of text on the platform, read, turned and written.
   * Nothing is run again, and nothing is kept in memory */
  final class Stated[C] extends Handler[C, C]:
    private def state(id: String) = s"state/$id"
    def done(c: C) = Prog.Done(c)
    def on[X](op: Op[X], rest: X => Prog[C]) = op match
      case Visit(Request(None, _)) =>
        Some(fresh.flatMap(id => ask(KvPut(state(id), writeAt(At.AtForm)))
          .flatMap(_ => rest(Response(id, Vector.empty, Some(asks(At.AtForm)))))))
      case Visit(Request(Some(id), answers)) =>
        Some(ask(KvGet(state(id))).map(_.flatMap(readAt)).flatMap {
          case None => rest(gone(id))
          case Some(at) =>
            turn(at, answers).flatMap {
              case Left(ended) =>
                ask(KvPut(state(id), "ended")).flatMap(_ => rest(Response(id, Vector(s"done: $ended"), None)))
              case Right((next, shown)) =>
                ask(KvPut(state(id), writeAt(next))).flatMap(_ => rest(Response(id, shown, Some(asks(next)))))
            }
        })
      case _ => None
  def stated[C](client: Prog[C]): Prog[C] = handle(Stated[C]())(client)

@main def applications(): Unit =
  import app.*
  val order = Map("customer" -> "ann", "item" -> "tea", "quantity" -> "2")

  // ---- 1. a screen as a sequence of questions, answered three ways
  // (a) at a terminal: in place. The lines "typed" are a list here; a wrong one is asked again by the program
  val typed = Iterator("ann", "tea", "two", "2")
  val printed = Vector.newBuilder[String]
  assert(run(handle(Console[Filled](printed += _, () => typed.next()))(byQuestions)) == order)
  assert(printed.result() == Vector("New order", "customer:", "item:", "quantity:",
    "quantity must be a number above zero", "quantity:"), printed.result())

  // (b) by a script, by labels: the same program, the same result, and what was shown
  val played = run(scripted("customer" -> "ann", "item" -> "tea", "quantity" -> "two", "quantity" -> "2")(byQuestions))
  assert(played == (Vector("New order", "quantity must be a number above zero"), order), played)
  // a script that does not fit the screen is a refusal that says where
  assert(run(refusals(scripted("customer" -> "ann", "quantity" -> "2")(byQuestions))) ==
    Left("the script has quantity = 2, the screen asks item"))
  assert(run(refusals(scripted("customer" -> "ann")(byQuestions))) ==
    Left("the script has ended, the screen asks item"))

  // (c) a page at a time: the run ENDS at each question, with the rest in hand
  val page1 = run(paged(byQuestions))
  val resume1 = page1 match
    case Page.Waiting(shown, Question.Entering("customer"), resume) => assert(shown == Vector("New order")); resume
    case other                                                      => sys.error(s"not at the first question: $other")
  val page2 = run(resume1(Map("customer" -> "ann")))
  val page3 = run(answer(page2, Map("item" -> "tea")))
  def at[A](page: Page[A]) = page match
    case Page.Waiting(shown, asks, _) => (shown, Some(asks))
    case Page.Ended(shown, _)         => (shown, None)
  assert(at(page2) == (Vector.empty, Some(Question.Entering("item"))))
  assert(at(page3) == (Vector.empty, Some(Question.Entering("quantity"))))
  val page4 = run(answer(page3, Map("quantity" -> "two")))
  assert(page4 match
    case Page.Waiting(shown, Question.Entering("quantity"), _) =>
      shown == Vector("quantity must be a number above zero")
    case _ => false)
  assert(run(answer(page4, Map("quantity" -> "2"))) == Page.Ended(Vector.empty, order))
  // BACK is the rest of an earlier page, called again with another answer: a second future, the first untouched
  val bobs = run(answer(run(resume1(Map("customer" -> "bob"))), Map("item" -> "tea")))
  assert(run(answer(bobs, Map("quantity" -> "2"))) == Page.Ended(Vector.empty, order.updated("customer", "bob")))
  assert(run(answer(page4, Map("quantity" -> "3"))) == Page.Ended(Vector.empty, order.updated("quantity", "3")))

  // a screen as DATA: one question holds the whole form. Lowered field by field it is the sequence above …
  assert(run(scripted("customer" -> "ann", "item" -> "tea", "quantity" -> "two", "quantity" -> "2")(
    fieldByField(ask(Fill(orderForm))))) == played)
  // … and a handler that can show a whole page answers it in ONE round, and checks it from the data
  val form1 = run(paged(ask(Fill(orderForm))))
  assert(form1 match
    case Page.Waiting(shown, Question.Filling(form), _) => shown.isEmpty && form == orderForm
    case _                                              => false)
  val form2 = run(answer(form1, Map("customer" -> "ann", "quantity" -> "two")))
  assert(form2 match
    case Page.Waiting(shown, Question.Filling(_), _) =>
      shown == Vector("item is needed", "quantity must be a number above zero")
    case _ => false)
  assert(run(answer(form2, order)) == Page.Ended(Vector.empty, order))

  // ---- 2. a store
  // what each handler owes. SQL over no database is a stub in chapter 8's sense: it finds nothing it saved
  assert(storeOwes(tables) == List(true, true, true, true))
  assert(storeOwes(journal) == List(true, true, true, true))
  assert(storeOwes(sqlOverNothing) == List(false, true, false, true))

  // the SQL handler's product is data: the statements, with their values beside them
  val tea = Row("tea", Map("name" -> "green tea", "maker" -> "m1"))
  val (said, _) = run(noDatabase(asSql(
    for
      _ <- ask(Save("products", tea))
      _ <- ask(Find("products", "tea"))
      _ <- ask(Query("orders", "customer", "ann"))
      _ <- ask(FindMany("products", List("tea", "cup")))
    yield ())))
  assert(said == Vector(
    ("INSERT INTO products (key, maker, name) VALUES (?, ?, ?) " +
      "ON CONFLICT (key) DO UPDATE SET maker = excluded.maker, name = excluded.name", List("tea", "m1", "green tea")),
    ("SELECT * FROM products WHERE key = ?", List("tea")),
    ("SELECT * FROM orders WHERE customer = ? ORDER BY key", List("ann")),
    ("SELECT * FROM products WHERE key IN (?, ?) ORDER BY key", List("tea", "cup"))), said)
  // a value never reaches the text; a name does, and is checked in one place
  val hostile = "x'; DROP TABLE orders; --"
  assert(run(noDatabase(asSql(ask(Find("orders", hostile)))))._1 ==
    Vector(("SELECT * FROM orders WHERE key = ?", List(hostile))))
  assert(run(refusals(noDatabase(asSql(ask(Query("orders", hostile, "ann")))))) == Left(s"not a name: $hostile"))

  // the journal keeps every version; the tables keep the last
  val black = tea.copy(fields = Map("name" -> "black tea"))
  val twice = ask(Save("products", tea)).flatMap(_ => ask(Save("products", black)))
  assert(run(inJournal(twice))._2.size == 2 && run(inTables(Map.empty)(twice))._2("products").size == 1)

  // a transaction around a part: both saves, or neither
  val cup = Row("cup", Map("name" -> "a cup", "maker" -> "m2"))
  def both(fails: Boolean): Prog[Option[Row]] = atomically(
    for
      _     <- ask(Save("products", tea))
      mine  <- ask(Find("products", "tea")) // its own write, read through the log
      _     <- if fails then ask(Refuse("no cups")) else done(())
      _     <- ask(Save("products", cup))
    yield mine)
  val (found, kept) = run(inTables(Map.empty)(both(fails = false)))
  assert(found == Some(tea) && kept("products").keySet == Set("tea", "cup"))
  // the part is refused: its rest is dropped, its `done` line with it, and NOTHING was written
  assert(run(inTables(Map.empty)(refusals(both(fails = true)))) == (Left("no cups"), Map.empty))
  // under SQL the database is told two inserts, at the end; the read between them never reached it
  assert(run(noDatabase(asSql(both(fails = false))))._1.map(_._1.take(6)) == Vector("INSERT", "INSERT"))
  assert(run(refusals(noDatabase(asSql(both(fails = true))))) == Left("no cups"))
  // the price of a `done` line that acts (section 8.6): a rest resumed twice from outside commits twice
  val forked = atomically(ask(Save("products", tea)).flatMap(_ => among("cup", "mug"))
    .flatMap(k => ask(Save("products", Row(k, Map("name" -> k))))))
  val (_, lines) = run(inJournal(allChoices(forked)))
  assert(lines.map(_._2.key) == Vector("tea", "cup", "tea", "mug"), lines)

  // N+1. Three orders of ann's, two products, two makers
  val stock: Tables = Map(
    "orders" -> List(Row("ann-1", order), Row("ann-2", order + ("item" -> "cup")), Row("ann-3", order))
      .map(r => r.key -> r).toMap,
    "products" -> Map("tea" -> tea, "cup" -> cup),
    "makers" -> Map("m1" -> Row("m1", Map("name" -> "Tea & Co")), "m2" -> Row("m2", Map("name" -> "Cups Ltd"))))
  def asked[A](p: Prog[A]): (Vector[String], A) = run(inTables(stock)(seen(p)))._1
  // one by one: a handler is offered one `Find` at a time, and cannot know that more are coming
  val oneByOne = asked(reportOneByOne("ann"))
  assert(oneByOne == (Vector("Query", "Find", "Find", "Find"), List("2 x green tea", "2 x a cup", "2 x green tea")))
  // together: the same lines, two questions; the two orders of tea ask for it once
  assert(asked(report("ann")) == (Vector("Query", "FindMany"), oneByOne._2))
  assert(run(noDatabase(asSql(report("ann"))))._1.size == 1, "no rows came back, so there was nothing to batch")
  // a part that asks twice takes two rounds: three questions, where one by one would be seven
  assert(asked(report("ann", lineWithMaker)) ==
    (Vector("Query", "FindMany", "FindMany"), List("tea by Tea & Co", "cup by Cups Ltd", "tea by Tea & Co")))
  assert(asked(ask(Query("orders", "customer", "ann")).flatMap(os => each(os.map(lineWithMaker))))._1.size == 7)

  // ---- 3. a server
  // a platform is an effect with several handlers; each owes the same
  val inMemory: APlatform = new APlatform:
    def apply[A](p: Prog[A]) = run(memory(Map.empty)(p))._1
  def onDisk(use: java.nio.file.Path => Unit): Unit =
    val dir = java.nio.file.Files.createTempDirectory("book-chapter-9")
    try use(dir)
    finally
      java.nio.file.Files.list(dir).forEach(f => java.nio.file.Files.delete(f))
      java.nio.file.Files.delete(dir)
  assert(kvOwes(inMemory) == List(true, true, true))
  onDisk { dir =>
    val files: APlatform = new APlatform:
      def apply[A](p: Prog[A]) = run(directory(dir)(p))
    assert(kvOwes(files) == List(true, true, true))
    // what a platform lacks, it says
    assert(run(refusals(directory(dir)(ask(Fetch("prices/tea"))))) ==
      Left("no network on this platform: Fetch(prices/tea)"))
  }
  assert(run(memory(Map.empty, Map("prices/tea" -> "4"))(ask(Fetch("prices/tea"))))._1 == "4")

  // a request is a question of the world, and a host is its handler. The shopper is a program
  val save = Map("press" -> "Save")
  val shopper = visits(order, save, Map("press" -> "Finish"))
  val expected = List(
    Response("s1", Vector.empty, Some(Question.Filling(orderForm))),
    Response("s1", Vector("2 x tea for ann"), Some(Question.Pressing(List("Save", "Cancel")))),
    Response("s1", Vector("saved as ann-1"), Some(Question.Pressing(List("Another", "Finish")))),
    Response("s1", Vector("done: ann-1"), None))
  val orders1: Tables = Map("orders" -> Map("ann-1" -> Row("ann-1", order)))

  // three hosts, one shopper: the same four responses and the same order saved
  val (inProcess, saved1) = run(inTables(Map.empty)(hosted(desk)(shopper)))
  assert(inProcess == expected && saved1 == orders1, inProcess)
  val ((byReplay, saved2), kv2) = run(memory(Map.empty)(inTables(Map.empty)(replayed(desk)(shopper))))
  assert(byReplay == expected && saved2 == orders1, byReplay)
  // what replay keeps: every answer of the person, and every answer of the store
  assert(kv2 == Map("sessions" -> "1", "answers/s1/0" -> "", "answers/s1/1" -> "",
    "journal/s1" -> "customer=ann&item=tea&quantity=2\npress=Save\npress=Finish"), kv2)
  val ((byState, saved3), kv3) = run(memory(Map.empty)(inTables(Map.empty)(stated(shopper))))
  assert(byState == expected && saved3 == orders1, byState)
  assert(kv3 == Map("sessions" -> "1", "state/s1" -> "ended"))
  // what is stored while the session waits at its second question: every answer so far, or one line of state
  def waiting[A](host: Prog[List[Response]] => Prog[A]) =
    run(memory(Map.empty)(inTables(Map.empty)(host(visits(order)))))._2
  assert(waiting(replayed(desk))("journal/s1") == "customer=ann&item=tea&quantity=2")
  assert(waiting(stated)("state/s1") == "AtConfirm|customer=ann&item=tea&quantity=2")

  // THE FINDING, as the first version of this file met it. A replaying host that journals only what the
  // person answered: the fourth visit runs the program again from its first line, and the save with it. The
  // store is a world. It was not journalled, so the second run's query sees the first run's order: the
  // shopper is told `ann-1`, then `ann-2`, and there are two orders
  val ((doubled, twoOrders), _) = run(memory(Map.empty)(inTables(Map.empty)(replayedPersonOnly(desk)(shopper))))
  assert(doubled.map(_.shown) == List(Vector.empty, Vector("2 x tea for ann"), Vector("saved as ann-1"),
    Vector("done: ann-2")), doubled)
  assert(twoOrders("orders").keySet == Set("ann-1", "ann-2"), twoOrders)

  // the three hosts agree on a longer visit too: two orders, the second after "Another"
  val bob = order.updated("customer", "bob")
  val twoByOne = visits(order, save, Map("press" -> "Another"), Map("customer" -> "bob"), bob, save,
    Map("press" -> "Finish"))
  val long1 = run(inTables(Map.empty)(hosted(desk)(twoByOne)))
  assert(long1._2("orders").keySet == Set("ann-1", "bob-1"), long1)
  assert(long1._1(4).shown == Vector("item is needed", "quantity is needed"), long1)
  assert(run(memory(Map.empty)(inTables(Map.empty)(replayed(desk)(twoByOne))))._1 == long1)
  assert(run(memory(Map.empty)(inTables(Map.empty)(stated(twoByOne))))._1 == long1)

  // another process. Two visits, the process ends, two more visits by whoever is started next
  val first = visits(order)
  val second = later("s1", save, Map("press" -> "Finish"))
  // closures do not outlive the run that made them
  val (r1, t1) = run(inTables(Map.empty)(hosted(desk)(first)))
  assert(run(inTables(t1)(hosted(desk)(second)))._1 == List(gone("s1"), gone("s1")))
  // answers and states do, on a platform that keeps them: the same on memory handed over, and on a directory
  for host <- List[Prog[List[Response]] => Prog[List[Response]]](replayed(desk), stated) do
    val ((a1, tablesA), kvA) = run(memory(Map.empty)(inTables(Map.empty)(host(first))))
    val ((a2, tablesB), _) = run(memory(kvA)(inTables(tablesA)(host(second))))
    assert(a1 ++ a2 == expected && tablesB == orders1, a1 ++ a2)
    onDisk { dir =>
      val (d1, tablesC) = run(directory(dir)(inTables(Map.empty)(host(first))))
      val (d2, tablesD) = run(directory(dir)(inTables(tablesC)(host(second))))
      assert(d1 ++ d2 == expected && tablesD == orders1, d1 ++ d2)
    }

  // ---- 4. one application, three stacks, one line each
  // a terminal and a store in memory
  val keys = Iterator("ann", "tea", "2", "Save", "Finish")
  val screen = Vector.newBuilder[String]
  val atTerminal = run(inTables(Map.empty)(handle(Console[String](screen += _, () => keys.next()))(fieldByField(desk))))
  assert(atTerminal == ("ann-1", orders1))
  assert(screen.result() == Vector("New order", "customer:", "item:", "quantity:", "2 x tea for ann",
    "[Save] [Cancel]", "saved as ann-1", "[Another] [Finish]"), screen.result())
  // a test by script
  val inTest = run(inTables(Map.empty)(scripted("customer" -> "ann", "item" -> "tea", "quantity" -> "2",
    "press" -> "Save", "press" -> "Finish")(fieldByField(desk))))
  assert(inTest == ((Vector("New order", "2 x tea for ann", "saved as ann-1"), "ann-1"), orders1))
  // on the forty lines a stack is checked when it runs: with no store, `desk` fails at its first `Query`
  assert(scala.util.Try(run(scripted("customer" -> "ann", "item" -> "tea", "quantity" -> "2", "press" -> "Save")(
    fieldByField(desk)))).failed.get.getMessage == "nobody answers Query(orders,customer,ann)")
  // pages over requests, the store as SQL text
  val (statements, onTheWeb) = run(noDatabase(asSql(hosted(desk)(shopper))))
  assert(onTheWeb == expected)
  assert(statements == Vector(
    ("SELECT * FROM orders WHERE customer = ? ORDER BY key", List("ann")),
    ("INSERT INTO orders (key, customer, item, quantity) VALUES (?, ?, ?, ?) ON CONFLICT (key) DO UPDATE SET " +
      "customer = excluded.customer, item = excluded.item, quantity = excluded.quantity",
      List("ann-1", "ann", "tea", "2"))), statements)
  println("application examples hold")
