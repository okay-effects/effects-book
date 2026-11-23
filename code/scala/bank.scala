// The one scenario every rendering under code/ prints (code/expected.txt), here on the forty lines of
// kernel.scala: a program is a tree of questions, the rest of it a closure.
// `scala-cli run code/scala --main-class book.bank`
package book

@main def bank(): Unit =
  type R = Either[String, String]
  def show(r: R) = r.fold("refused: " + _, "paid: " + _)
  def balances(b: Accounts[?]) = s"ann=${b.balances.getOrElse("ann", 0L)} bob=${b.balances.getOrElse("bob", 0L)}"
  def edge(bank: Accounts[R])(p: Prog[R]) = run(handle(bank)(handle(FixedClock[R](42))(p)))

  // 1. the program, run in one line under a bank in memory and a fixed clock
  val rich = Accounts[R](Map("ann" -> 100L))
  println("1 " + show(edge(rich)(handle(Refusals[String]())(pay("ann", "bob", 30)))))
  println("1 balances: " + balances(rich))

  // 2. the same program, refused: nothing after the refusal ran
  val poor = Accounts[R](Map("ann" -> 10L))
  println("2 " + show(edge(poor)(handle(Refusals[String]())(pay("ann", "bob", 30)))))
  println("2 balances: " + balances(poor))

  // 3. under a journal it knows nothing of
  val journal = Journal[String]()
  edge(Accounts[R](Map("ann" -> 100L)))(handle(Refusals[String]())(handle(journal)(pay("ann", "bob", 30))))
  journal.lines.foreach(l => println("3 journal: " + l))

  // 4. under a limit: the move is seen before it is answered, and refused
  val limited = Accounts[R](Map("ann" -> 100L))
  println("4 " + show(edge(limited)(handle(Refusals[String]())(handle(Limit[String](50))(pay("ann", "bob", 80))))))
  println("4 balances: " + balances(limited))

  // 5. leaving early: after the refusal nothing more was asked
  val seen = Journal[String]()
  edge(Accounts[R](Map("ann" -> 10L)))(handle(Refusals[String]())(handle(seen)(pay("ann", "bob", 30))))
  println(s"5 left early: the journal has ${seen.lines.size} line, ${seen.lines.mkString("; ")}")

  // 6. the rest resumed twice: what would the program do with a balance of 100, and of 10; nothing is moved
  val outcomes = run(handle(WhatIf[R](List(100, 10)))(handle(DryRun[R]())(
    handle(FixedClock[R](42))(handle(Refusals[String]())(pay("ann", "bob", 30))))))
  println("6 twice: " + outcomes.map((path, r) => s"${path.head} -> ${show(r).stripPrefix("paid: ")}").mkString(" | "))
