# 10 · What is not answered does not happen

Chapter 7 treated effects as a way to organise a system. This one treats them as a way to **control** it: to
see, limit, record, replay and prove what a piece of software did. The listings are in the notation of section
2.0. Each section says what checks it: the limit and the journal are lines 3 and 4 of the scenario that all
sixteen renderings under `code/` print, and the rest is in `code/scala/control.scala`, whose assertions are the
results below (`scala-cli run code/scala --main-class book.control` prints `control examples hold`).

Like chapter 7 this is the **first half** of chapter 2b at work: control is possible because a question is a
value with a meaning, at a point it cannot avoid. The second half appears where a policy must do more than
look: to refuse is to drop the rest, and to ask a person is to keep it.

The whole chapter rests on one property, so I state it first.

> A program can only ask. So every action it takes on the world passes through a handler — and a handler can
> answer, refuse, change, record, delay or forward the question. There is no other road.

Conventional control is added *around* code: a proxy in front of a service, a policy at the network, a wrapper
on a client. Each sees the calls that pass through that particular point. Control by handlers is not around the
code; it is the only way the code touches anything.

## 10.1 Seeing: the journal

The `Journal` of chapter 2 saw every `Balance` and `Move` and re-asked them. Three properties make it more than
a logging decorator.

**Completeness by construction.** It sees every operation of the effect, including ones written next year in a
module that has never heard of it. Coverage is not a discipline each team keeps.

**It sees the meaning.** The journal line is `Move(ann, bob, 30)`, not `POST /v2/transfers` and not a TCP
segment. Control sits at the level where a person can read what happened.

**It sees the answer too.** The handler holds the rest of the program, so it is there when the answer comes
back, and can record the pair. A record of every question with its answer is something very specific: it is
enough to run the program again without the world.

In the forty lines the journal keeps its lines in a field. In my libraries writing a line of the trail is itself
an operation, so the trail has handlers like anything else: a test reads it as a value, production sends it
wherever it must go.

## 10.2 Limiting: a policy the program cannot go around

```
handler Limit(max) : A ! (Accounts + Failure + R)  =>  A ! (Accounts + Failure + R)
  done(a)                              => done a
  Move(from, to, n), rest  if n > max  => Refuse("a move of {n} is over the limit of {max}")
                                          -- asked of whoever handles refusals outside; rest is not called
  -- a smaller move has no line here: it passes on, still asked, to the bank
```

Every `Move` is seen *before* it reaches the bank. One over the limit is not forwarded; the handler asks
`Refuse` instead. Smaller moves pass: not this handler's to decide.

```
guarded(amount) = run( pay("ann", "bob", amount) under Limit(1000) under bank under FixedClock(42) under Refusals )

guarded(300)   =  paid("moved 300 from ann to bob at 42")
guarded(3000)  =  refused("a move of 3000 is over the limit of 1000")
  -- bank, which started from {ann: 5000}, ends with ann 4700, bob 300: only the small move happened
```

The last line is the point. The large move did not happen and was not undone; it was never asked of the bank.

*Checked by* the first block of `control.scala`, and — with a limit of 50 and a move of 80 — by line 4 of
`code/expected.txt` in all sixteen renderings. How a rendering writes "ask `Refuse` instead" is a small survey
of Part III: the trees return a node; the generators yield the refusal; the Rust and Zig handlers return the
reply "ask this instead"; in Java on threads, Go and C# the handler asks the refusal of the handlers outside
it, which it was passed as `outer`; in Koka the clause of an `override` handler simply calls `refuse`; in
OCaml the clause performs `Refuse` and then discontinues the rest it holds. In the two renderings with no
value for the question to be — Scala's capabilities and Haskell's tagless final — the limit is a wrapper
around the bank (a class in one, a monad over the monad in the other), and prints the same line with the
qualification of chapter 5: it limits what was passed through it.

Note what `pay` had to do to be subject to this policy: nothing. It was not written with a limit in mind, does
not call a policy service, and cannot bypass the check, because its only way to move money is to ask.

Everything usually called *policy* has this shape:

- **authorisation** — may this actor ask this operation, with these arguments?
- **limits** — amount, rate, budget per day;
- **approval** — keep the rest of the program, show the question to a person, continue or refuse on their word
  (chapter 11 is about the "keep");
- **redaction** — forward the question with sensitive fields removed;
- **dry run** — answer plausibly without forwarding, to see what a program *would* do.

A policy can be data itself: a text of rules — *deny a move over 1000; ask a person for any move to a new
recipient; allow the rest* — read by one handler. Then a policy has versions, tests ("these twelve recorded
calls must be decided so") and a diff. Rule order matters and should be stated; the one I use is *deny, then
ask, then allow, then the default*.

## 10.3 Replaying: the world from a journal

```
handler Recording(entries) : A ! (Accounts + Clock + R)  =>  A ! (Accounts + Clock + R)
  done(a)                         => done a
  op of Accounts or Clock, rest   => answer <- op                           -- asked again, outside
                                     entries := entries ++ [(op, answer)]   -- the answer itself is kept
                                     rest(answer)

handler Replay(left) : A ! (Accounts + Clock + Failure + R)  =>  A ! (Failure + R)
  done(a)                         => done a
  op of Accounts or Clock, rest   => if left starts with (q, answer) and q = op then
                                       left := left without its first entry
                                       rest(answer)                         -- from the journal; nobody is asked
                                     else Refuse("diverged at {op}: the journal has {the next question in left}")
```

`Replay` answers the bank's and the clock's questions **from a recording**. No bank, no clock: its type says
so, with `Accounts` and `Clock` gone from the right of the arrow.

```
-- recorded once, against the real handlers
first = run( pay("ann", "bob", 30) under Recording(entries) under live under FixedClock(1700000000) under Refusals )
  -- entries hold the questions Balance(ann), Move(ann,bob,30), Now, each with its answer

-- replayed with NO bank and NO clock
again = run( pay("ann", "bob", 30) under Replay(entries) under Refusals )

again  =  first
```

The program produced the same receipt, with the same timestamp, from the journal alone.

What this buys in practice:

- **An incident can be re-run.** Not "reproduced by guessing the state of the database" — re-run, with exactly
  the answers production received, on a laptop, under a debugger.
- **A report can be rebuilt.** In a product built this way, every answer from a data source is journaled under a
  sealed hash chain, and a report is rebuilt byte for byte, offline, from the journal alone. "Show me where this
  number came from" has a mechanical answer.
- **A change can be tested against history.** Run the new version of the program over last month's journals and
  see where it would have asked something different.

That last one needs the next property.

**Drift is detected, not hidden.** The handler above does not silently answer whatever comes. If the program now
asks something the journal does not have next, it stops and names the divergence:

```
run( pay("ann", "bob", 31) under Replay(entries) under Refusals )
  =  refused("diverged at Move(ann,bob,31): the journal has Some(Move(ann,bob,30))")
```

A replay that cannot tell you it has diverged is worse than none.

*Checked by* the second, third and fourth blocks of `control.scala` (record; replay; a changed program). The
message is that file's, `Some(…)` included, which is how Scala prints "there is a next entry, and it is this".
One thing in the file is cruder than the notation and should be said: it compares a question with a recorded
one by their printed text. The journal's entries are text, question and answer both, as they would be in a
journal that outlives the process. An entry has therefore forgotten the type of its answer, and the replaying
handler finds it again without a cast: it matches the question, and the match tells the compiler what the
recorded text must be read as — a number for `Balance` and `Now`, nothing for `Move` (the mechanism of §3.4).
The price is that the handler names each operation it can replay.

*What it needs:* only the first half. A handler that replays calls the rest once, in place, and a handler that
records does the same; neither keeps it. So replay is available on every technique in which the question is a
value that can be compared — the trees, the generators, the state machines, the languages with handlers — and
it is what chapter 5's command objects already do. No rendering but the Scala one has a line for it; the
backlog lists that.

One distinction is worth making here, because the word "replay" is used for two different things. Replaying a
*journal of answers*, as above, re-runs a program without the world. That is sound and useful. Using replay as
the way to *resume* a suspended program — re-running it from the start and feeding back old answers until it
reaches the point where it stopped — is a different technique, with different costs and a real restriction on
the code (it must be deterministic and must never change shape between runs). Chapter 11 argues for keeping the
rest of the program as an actual value instead.

## 10.4 Operations that may not be handled without an audit

A journal is only as good as the certainty that it is installed. If any piece of code can write
`handle(myOwnBank)(program)` and answer `Move` itself, the audit is a convention again.

The remedy is in the types, and it is one of the more satisfying uses of rows. An effect is declared
**sensitive**. Then:

- the ordinary `handle` and `intercept` *refuse to compile* for it;
- the only way to proceed is `audit`, which places an interceptor on the effect and leaves in the row not the
  effect but a marker — "this effect, audited";
- only a special `handleAudited` accepts that marker.

So a program that moves money cannot be run at all unless an audit stands between it and whatever answers. This
is not a rule in a document. A build that violates it does not produce a binary.

## 10.5 The second witness: checking the boundary

Chapter 1 admitted the gap: the row lists what a function *declares*, and on most platforms nothing stops the
body from reaching past it. Control that can be walked around is not control, so the gap must be closed by
something other than the type.

I close it at build time, from the compiled code. On the JVM the check reads the bytecode of every business
module and of its third-party jars — every class, field and method a class refers to, and native methods — and
fails the build if business code refers to any of: the network, the file system, SQL, processes, threads, the
system clock, randomness, reflection, class loading, native code. Handler and runtime modules are not failed:
they are **listed, by provider**.

Two things come out of that.

**The guarantee becomes checkable.** "All access to the outside world sits in a small, listed set of handlers,
and every build checks that nothing else reaches past them" is a sentence a tool verifies on every commit.

**The list is itself valuable.** A generated inventory, per module, of what reaches what, with the SHA-256 of
every input, current at every build, is the document that operational-resilience regulation asks for and that
most organisations maintain by hand. The EU's DORA (Regulation (EU) 2022/2554) asks financial entities for an
inventory of ICT assets and their dependencies (Art. 8) and for controlled, documented change (Art. 9, and the
RTS in Delegated Regulation (EU) 2024/1774, whose Art. 12 is about logging). A report generated from the bytecode
is *evidence* for these. It is not a compliance claim; only an auditor can make that, and I have not had this
reviewed by a compliance professional.

I said in chapter 1 what the first run found in my own repository: 232 real reaches, among them a clock and a
random source in a module I believed was pure data. The tool also had to learn what *not* to count — three
idioms the compiler generates — and it prints that list in the header of every report. A check that hides its
exclusions is asking to be trusted, which is the thing a check exists to avoid.

There are further witnesses to add where a platform offers them: a module system that refuses to link what a
module did not declare; launch flags that deny native access; a stricter compiler mode that tracks capabilities.
None replaces the scan — on the JVM everything in the base module is readable by every module, a socket and the
clock included, and that is exactly where the findings were — but each narrows what the scan must catch.
Section 10.7 looks at the last of them.

## 10.6 A program that picks its own handler

Somebody asked me a question that I answered too quickly: what if the continuation calls the handler, instead of
the other way round? I said it cannot. The rest of a program holds no reference to a handler. It only returns
questions, and the handler answers them from outside. In the model that is right.

In a codebase a handler is a class in a module. Import the module and you can use it anywhere, including the
middle of a program:

```
sneaky : Text ! nothing =                    -- its row is empty: it asks no one
  inner := run( pay("ann", "bob", 3000) under backdoor under FixedClock(42) under Refusals )
  done "{inner}"                             -- a run inside a program, under a bank reached by name

run( sneaky under Journal(around) under Limit(1000) )  =  "Right(moved 3000 from ann to bob at 42)"
  -- around = []                              the journal saw nothing
  -- backdoor, which started from {ann: 5000}, ends with ann 2000, bob 3000
```

The notation can write this, and that is itself the point: nothing in section 2.0 forbids `run` inside a
program, and nothing in the forty lines does. *Checked by* the last block of `control.scala`, where `sneaky` is
a finished program whose final step calls `run`, and the text it ends in is the printed form of that inner
result. It is not special to the tree. In every rendering under `code/` a handler is a value or a function
that a program's code could name, and what stops it is the rule below and a check, not the technique. The one
exception in kind is a language whose types track what a function may hold (section 10.7).

A limit of a thousand and a journal stand around this program. Three thousand moved, and the journal is empty.
The program asked nothing. It ran a program of its own, under a bank it reached by name. This is chapter 7's
rule — a program never calls `run` inside itself — seen from the side of control, and it is a different hole
from the one in 10.5: no socket was opened and no class of the platform was named.

How bad is it? There are three cases, and they should not be confused.

- **Sometimes it is fine.** One handler delegates to another. A program gives a part of itself its own answer by
  *applying* a handler to that part, which leaves a value with a smaller row and runs nothing.
- **Usually it is a loss.** The point of asking is that the code need not know who answers. Code that picks its
  handler can no longer be tested, moved or audited by swapping one.
- **For some operations it must never happen.** A payment, an approval, an action of an AI agent must be answered
  outside, under audit, with no way round. There it is a requirement to be enforced and checked.

Section 10.4 closes half of this where the library has rows: a sensitive effect cannot be handled without its
audit, by anyone. The other half is a rule about what business code may hold: **a program holds only data,
effects and identifiers; real clients live in handlers.** Like the rule of 10.5, it holds only if a tool checks
it, over the whole program, denying by default.

## 10.7 What types can take over, and what only the platform can ensure

The check of 10.5 reads compiled code after the fact. A newer line of work moves part of it into the type
system. Chapter 5 met it as capabilities: the right to perform an effect is a value that must be in scope, and a
*capture checker* tracks which capabilities each value holds. A row and a capture set say the same thing from
two sides, what a computation asks and what it holds; the authors of the Effekt language made the
correspondence exact (Brachthäuser, Schuster and Ostermann, "Effects as capabilities", OOPSLA 2020; chapter
15 has the full reference).

In a language with such a checker the boundary would read like this. The snippet is illustrative and is not in
`code/`. It is in Scala's syntax and not the book's notation because what it shows is a feature of one
compiler, the experimental capture checker of Scala 3:

```scala
def total(orders: List[Order]): Money             // no capability in its type
def charge(o: Order)(using Payments): Receipt     // holds one capability, and only that one
```

What that would give, where it can be switched on for a whole codebase:

- business logic that holds no capability is pure by its type, with no scan needed to say so;
- only the handlers at the edge hold the capabilities of the world — network, files, clock — and their types
  show it;
- a sensitive effect cannot leave its audit, and neither can the handler's right to pass it on;
- a program value cannot carry a transaction out of its bracket.

"This code cannot touch the payment interface" then stops being a review comment and becomes a type error. The
last item is the one my note reports as tried: under the compiler's experimental checker, a transaction that was
returned, captured in a closure or stored outside its bracket did not compile. I have not re-run that for this
chapter. The rest is argued: the checker is experimental, and programs as values need capture annotations on a
kernel's own types before the whole list holds.

There is a difference of default behind this. Rust is restricted by default and one opts into `unsafe`. Scala and
Java are the other way round: unrestricted by default, and one opts into checking. A capture checker that is on
by default would change which side a codebase starts from. That is a direction, not something a team can adopt
today.

So I tried how far it goes now, with every restriction I could switch on: the experimental checker, the
compiler's safe mode, the platform's module system. The experiment is unfinished, and what follows is from my
note of 2026-11-13, not re-run here.

- The checker makes continuations and handler clauses pure. Running a program inside a continuation, which is
  the shape of `sneaky` in 10.6, leaking a continuation, or leaking a handler's state does not compile.
- It is not enough alone. It tracks what is passed around, and on the JVM most effects are ambient: the clock,
  randomness and files are static calls anyone can name. The safe mode closes part of that and trusts whole
  packages, so a random source, the current time, a mutable map and a `throw` still compile.
- A whole-program check closes those: deny by default, plus the rule of 10.6.
- A sealed deployment: the program starts only if its classes, its flags and its checks match a seal.
- A gate for code written by agents: compiled, checked, then run, with the policy as a handler (chapter 12).

Not covered, by the same note: non-termination, exceptions at run time, the trusted base; and the seal is not
signed.

The short form of the result is the one I would keep. **Types say who holds a capability. Only the platform can
make sure there is no other way to get it.** Full guarantees look possible, and not from the type system alone;
the scan of 10.5 stays.

## 10.8 Speed is the least interesting part

The objection I hear most is about cost: surely a program that is data, interpreted, with every operation
passing a handler, is slow?

I have measured it (chapter 13 gives the protocol), and on the task I measured the machine was faster than a
well-regarded effect runtime, not slower. But I have come to think the question is aimed at the wrong thing.

Compare two systems. In one, journaling, replay, a dependency inventory and the ability to swap a provider are
each a discipline that every team must keep, for every service, for ever. In the other they follow from where
the handler stands. Even if the second were somewhat slower — on my measurements it is not — the difference
between them would not be one of performance. It is a difference in what can be *known* about the running
system.

Fairness, again: an effect runtime's supervisors, loggers and aspects are good tools and cover what that library
exposes. A handler covers every operation of the effect. And the guarantee has an edge, which this chapter has
tried not to hide: code that calls the platform directly walks around the handler. That is what the build-time
check is for.

One note of mine about my Rust library is headed as if control and audit cost nothing, and the figures behind
that heading should be read exactly. An operation answered in place by a handler known at compile time: 0.24 µs
per 1,000 operations. The same through an interceptor: 0.26 (note 20; one laptop, and the note
gives neither rounds nor spread). That is the road where the compiler knows the handler, and inlines it and the
interceptor with it. It is not the road where the program is data: there chapter 13 prices an audited operation
at tens of nanoseconds. Both statements are true, of different roads, and the heading is true of one.

## 10.9 Three layers that are usually one

This section is for the reader who does not write the code and answers for the system. The discipline that
studies how information systems serve an organisation is in large part about control: who may do what, how each
action is audited, how a supplier is changed without breaking the company.

In most enterprise systems three things are written in the same lines of code:

- what the process does — the business logic;
- who is allowed, what must be checked, what must be recorded — the policy;
- which database, which supplier, which cloud — the implementation.

Because they are tangled, an audit is a project, a migration is a risk, and a change of policy is a change to
code that has nothing to do with policy.

The mechanism of this book separates exactly these three. The program is the first. The interceptors of this
chapter are the second. The handlers at the edge are the third. Each consequence has had its section already;
here they are in the words of the person who signs for them.

1. **The audit trail belongs to the architecture.** Every action passes a handler, so the trail is not an
   addition that somebody may have forgotten (10.1), with the edge that 10.5 to 10.7 were about.
2. **A control is an interceptor.** Segregation of duties, limits, approvals: each stands around the process
   and is not written into it. So **change the policy, not the process**. The limit of 10.2 moves from a
   thousand to five hundred by changing one argument where the handlers are applied; `pay` is not edited,
   reviewed or released again. Where the policy is a text of rules, the change is a diff of that text, with its
   own tests.
3. **A supplier is a handler.** Moving from one payment provider or one cloud to another does not touch the
   business logic (chapter 7).
4. **One process, several readings.** The same expense approval can be executed; simulated, to ask what would
   happen if the limit were lower (chapter 6, section 6.7); tested; and shown to an auditor as the journal of
   what it did. Today these are often separate artefacts — the code, a model for simulation, a test suite, a
   process document — and they drift apart. A fifth reading, a handler that prices each operation instead of
   performing it, I have not built; I name it as something the shape allows.
5. **Waiting for a person is an operation.** The process stops at "ask a manager" and goes on when the answer
   comes, days later if need be (chapter 11).

Fairness to what exists. Process engines, the ports and adapters of hexagonal architecture, and middleware aim
at the same separation, and they have what this has not: products, operators, and people trained in them. Ports
and adapters is, in outline, chapter 7's two kinds of code. What effects add is in the detail. The requests are
typed values. Handlers compose and have a visible scope. The policy stands on the only road, not on a road the
code is asked to take. A paused process is a value.

And the limit. None of this touches adoption, culture or resistance to change, which is most of what managing
information systems is. The claim is about the control layer only, and it is argued from the mechanism and from
systems I built myself. I have not seen it at work in an organisation I do not run.

## 10.10 What an auditor is shown

Put together, a system built this way can put the following on a table.

1. **The vocabulary**: the complete list of what business logic can ask of the world.
2. **The handlers**: the complete list of what touches the world, by provider, generated at this build, with
   hashes.
3. **The proof of the boundary**: the build-time report, including what it did not count.
4. **The policy**: the rules, as a versioned text, with its tests.
5. **The journal**: every question and answer, under a hash chain, with the decision and the reason for each.
6. **The replay**: any past run re-executed from the journal, with divergence reported.

None of these is produced by a separate system bolted on afterwards. They are six views of the same fact: the
program can only ask.

On the two halves of chapter 2b: items 1 to 3 are the signature, and what it means for a signature to be the
*whole* of what a program can do; items 4 to 6 are what follows when each question is a value at a point that
holds the rest — a decision with a reason, a record with its answer, a run that can be made again. A mechanism
with only the second half can stop a program anywhere and cannot say what it stopped at. A mechanism with only
the first can list, record and queue every request, and cannot keep the program that made one waiting, in one
piece, for the answer.
