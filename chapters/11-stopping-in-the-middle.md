# 11 · Stopping in the middle

Chapter 2 listed what a handler may do with the rest of a program — not continue it, continue it once, continue
it many times — and then a fourth thing hidden in "once": *once, but later*. This chapter is about "later": an
approval that waits for a person, a workflow that outlives the process that started it.

It is also the chapter with a disagreement in it, including one with my own earlier work, so it separates what
runs here from what I argue. The listings are in the notation of section 2.0; each is checked by
`code/scala/stopping.scala`, whose assertions are the results shown (`scala-cli run code/scala --main-class
book.stopping` prints `stopping examples hold`). Two sections turn on what the rest is *made of*, which no
neutral notation can show, and there the chapter quotes the rendering that makes the point.

On the two halves of chapter 2b, this chapter is where they must become one. Section 2b.2 said the rest of the
program is a value. To outlive a process a value must be *data*, the property section 2b.1 gave the operations
and did not give the rest. The whole chapter is about the ways of getting it for the rest as well, and what
each costs.

## 11.1 Stopping, inside one process

Chapter 1b's third program, in a straight line:

```
effect Shop
  Place(item, price)  -> Text          -- the order's reference
  Ship(order)         -> Unit
  Refund(order)       -> Unit
effect Approving
  Approval(order)     -> yes or no     -- a person's answer, some time this week

purchase(item, price) : Text ! (Shop + Approving + Clock) =
  order <- Place(item, price)
  ok    := yes
  if price > 10000 then ok <- Approval(order)
  if ok then Ship(order) else Refund(order)
  at    <- Now
  done "{order} {shipped or refunded} at {at}"
```

The handler for `Approval` does not answer. It keeps the rest and lets the run end:

```
handler Approvals : A ! (Approving + R)  =>  (finished(A) | waiting(order, rest)) ! R
  done(a)                 => done finished(a)
  Approval(order), rest   => done waiting(order, rest)          -- rest is not called: it is the result
```

On Monday the purchase runs as far as the approval and returns a value. No thread is parked; nothing is running.

```
monday = run( purchase("a crane", 50000) under Approvals under FixedClock(1) under shop )

monday  =  waiting("order-1", rest)             -- shop.did = ["Place(a crane,50000)"]
```

On Thursday the director says yes, and the rest is continued:

```
thursday = run( rest(yes) under FixedClock(4) under shop )

thursday  =  finished("order-1 shipped at 4")   -- shop.did = ["Place(a crane,50000)", "Ship(order-1)"]
```

(`shop` answers `Place` with "order-" and a count, and keeps what it was asked to do in `did`. The file also
asserts that a purchase of a pen for 3 never stops: it is `finished("order-1 shipped at 1")` at once. Its
result type is named `Stopped`, with the cases `Finished` and `Waiting`.)

Two things to notice. The order was placed once; nothing before the stop ran again. And the rest is a *program*
— it still asks `Ship` and `Now` — so it was continued under Thursday's handlers, with Thursday's clock. What was
kept is the program's own future, not a snapshot of the world around it.

There is no state enum and no transition table. The sequence of the four lines is the state machine.

Within one process this is a complete tool, and most of what I use it for is this size: the policy of chapter 10
that answers "ask a person"; the agent of chapter 12 whose session waits while I decide on a phone; a question
sent across a wire to another process, which keeps the rest under a number until the answer comes back.
Section 9.3 does it for the pages of a web dialogue, and then by each of the two roads below.

*Checked by* the first three blocks of `stopping.scala`'s `main`. *What it needs:* a rest that can be handed out
of the handler and resumed once, later. Every technique of Part III has that, and they differ in what is
sitting in memory in the meantime. Here it is a closure. In a generator rendering it is the generator object,
stopped at its `yield`. On threads "nothing is running" is not quite true: a thread is parked, cheaply if it is
a virtual one. In a state machine it is one small value. That difference is nothing while the process lives,
and it is the whole of the next section.

## 11.2 The process dies

The business requirement was harder: the answer comes in days, and the process will be restarted before then.
So the rest must be written somewhere another process can read it. Whether it can be is a property of what the
rest is made of, so this section is in two real languages.

In the forty lines of chapter 3 the rest is a closure, and it cannot be written. The file tries, with the
platform's own serialiser:

```scala
val bytes = java.io.ObjectOutputStream(java.io.ByteArrayOutputStream())
assert(scala.util.Try(bytes.writeObject(rest)).failed.get.isInstanceOf[java.io.NotSerializableException])
```

`rest` is a closure: compiled code, holding references. A platform can be persuaded to serialise some closures;
what comes back after the next deploy is a reference to a class that has changed, which is worse than an error.

The same is true, for the same reason, of every rendering whose rest belongs to a runtime: a generator object,
a parked thread, a fibre, a compiler's continuation object. None of them is offered a way to be written down
(chapter 3a, axis g; the one exception I know of is noted there).

It is false of a state machine. In `code/rust/bank.rs` the rest of `pay`, stopped at its move, is a value such
as this one (the type derives `Debug`, so it can be printed):

```rust
Pay::AtMove { from: "ann", to: "bob", amount: 30 }
```

There is no code in it and no pointer into a stack: a tag saying which stop, and the variables alive there.
Writing such a value to a disk is a matter of a serialiser, and I have not run one here (chapter 3e says the
same). What matters for this chapter is that the question "can the rest be stored" has two honest answers in
`code/`, and they are decided by the technique, not by the idea.

From here there are two roads. Both work. They put the cost in different places.

## 11.3 The first road: keep the answers, and run the program again

Do not save the program at all. Save every answer it was given. To resume, start it again from its first line
and feed the recorded answers back without asking the world; where the journal ends is where it stood.

```
handler Journaling(journal) : A ! R  =>  A ! R                  -- every question passed on, its answer kept
  done(a)          => done a
  op, rest         => answer <- op
                      journal := journal ++ [(op, answer)]
                      rest(answer)

handler Replaying(left) : A ! R  =>  A ! R                      -- while the journal lasts, nobody is asked
  done(a)          => done a
  op, rest         => if left is empty then pass op on, unanswered
                      else if left starts with (q, answer) and q = op then
                        left := left without its first entry
                        replayed := replayed + 1
                        rest(answer)
                      else fail: "diverged: the program asks {op}, the journal has {q}"

-- Monday: the same run as before, journaled; then the process dies and only the journal is left
run( purchase("a crane", 50000) under Journaling(journal) under Approvals under FixedClock(1) under shop1 )
  -- journal = [ (Place(a crane,50000), "order-1") ]

-- Thursday, another process: the director's answer is appended, and the program is run AGAIN from its start
told = journal ++ [ (Approval(order-1), yes) ]

run( purchase("a crane", 50000) under Replaying(told) under Approvals under FixedClock(4) under shop2 )
  =  finished("order-1 shipped at 4")
  -- shop2.did = ["Ship(order-1)"]            the order was not placed a second time
```

**What it does well.** It needs nothing from the language or the kernel: a closure never has to become data.
The journal it keeps is the audit trail of chapter 10, so one artefact serves two purposes. It is event sourcing
in which the fold from events to state is not written by hand, because the fold is the program. Temporal and
Azure Durable Functions work this way, and they are serious software with operations that a library does not
have.

An earlier durable engine of mine is of this kind, and I do not disown it. Five words — pause, now, sleep, await
a signal, patch — gave a straight-line program that survived restarts, on the journal as its only state.

**What it costs.** Three things, and each showed up in that engine as a mechanism I had to build.

*The code must be deterministic.* Replay is just running the program again, so anything it reads other than
through a journalled question — a clock, a random number, a service — is read again and may differ. In most
engines this is a documented rule. In mine it became a type: a program whose row held an effect that a replay
would perform again did not compile as a workflow. That is the right way to keep the rule, and it is still a
restriction on what a workflow may be. Section 9.3 breaks the rule without meaning to: a replayed session
whose store was not journalled saves its order twice.

*The code must keep its shape while a run lives.* The journal holds answers by position. A new version that
asks one more question, earlier, maps old answers onto new questions:

```
purchase2(item, price) : Text ! (Shop + Approving + Clock) =       -- the next version: one question more
  seen <- Now
  out  <- purchase(item, price)
  done "{out} (seen at {seen})"

run( purchase2("a crane", 50000) under Replaying(told) under Approvals under FixedClock(4) under a new shop )
  fails:  "diverged: the program asks Now, the journal has Place(a crane,50000)"
```

The handler here names the divergence, as chapter 10's did. Naming it is the good outcome; the run is still
stranded. My engine stamped the program's name on every record and stopped the fold on a mismatch, and had a
`patch` question so that old runs took the old branch and new runs the new one. Each deploy while runs are alive
becomes a small exercise in keeping two shapes in one program.

*The time to resume grows with the history.* Every recorded answer passes through the program again:

```
replayed  =  the length of told        -- every recorded answer was passed through the program again
```

A snapshot does not remove this. That engine's suite counted it: a cold start over a journal of 40 answers read
80 records and ran 40 steps of the program; with a snapshot it read 8 records and ran the same 40 steps. The
snapshot cut the reading, not the running. Only declaring the old history superseded — one seed that replaces
it — shortened the run, and that is a decision the program's author takes, per workflow.

*Checked by* the fifth, sixth and seventh blocks of `stopping.scala`. As in chapter 10, the file compares a
question with a recorded one by its printed text, keeps answers as text, and reads a recorded answer back as
the type its question is answered with, which the match on the question gives; there is no cast. *What it
needs:* nothing of the rest at all, and that is the attraction. Replay asks for the first half of the idea
only — questions that can be recorded and compared — and for a program that can be started again. Every
rendering under `code/` could do it, the one-shot ones included; only the Scala one has it written.

## 11.4 The second road: keep the rest, as a real value

The other road is to make the rest something that *can* be written: not a closure but data — which stop the
program is at, and the variables alive there.

```
data PurchaseRest  =  afterApproval(order)             -- one case for each stop; here there is one

resume(afterApproval(order), ok) : Text ! (Shop + Clock) =
  if ok then Ship(order) else Refund(order)
  at <- Now
  done "{order} {shipped or refunded} at {at}"

write(afterApproval(order))  =  "AfterApproval|{order}"                -- data can be written, and read back
read("AfterApproval|{order}") =  afterApproval(order)

handler ApprovalsAsData : A ! (Approving + R)  =>  (finished(A) | kept(Text)) ! R
  done(a)                 => done finished(a)
  Approval(order), rest   => done kept(write(afterApproval(order)))    -- rest is neither called nor kept
```

On Monday what is kept is a line of text. On Thursday another process reads it and goes on from the stop:

```
run( purchase("a crane", 50000) under ApprovalsAsData under FixedClock(1) under shop3 )
  =  kept("AfterApproval|order-1")

run( resume(read("AfterApproval|order-1"), ok: yes) under FixedClock(4) under shop4 )
  =  "order-1 shipped at 4"                                 -- shop4.did = ["Ship(order-1)"]
```

(*Checked by* the last two blocks of `stopping.scala`, where the two results of the handler are `Right(a)` and
`Left(text)`.)

Nothing before the stop ran again, so nothing before the stop has to be deterministic, and the time to resume
does not depend on how long the run has lived. The code before the stop may change freely between versions.

I wrote `PurchaseRest` by hand, and a reader of chapter 1b will recognise it: it is the state machine that
teams write, with one state. A reader of chapter 3e will recognise it too. `code/rust/bank.rs` and
`code/zig/bank.zig` are this section carried through a whole program: `Pay` is `PurchaseRest` with a case for
every stop of `pay`, and `step` is `resume`. Notice also what the handler above was *not* handed in any useful
sense. It ignores `rest`, the closure, and writes down a value of its own that means the same; the notation's
`rest` and this `afterApproval(order)` are one thing in two materials. That is the honest picture of this
road. Somebody has to turn the rest into data. What decides whether the road is worth taking is who:

- **A compiler.** A function is compiled into an enumeration with one case per stop, each holding the variables
  in scope there, and resuming is straight code to the next stop. This is what `async` does in many languages;
  my Rust library does it for effectful functions with an attribute, and the resulting value can be cloned.
- **A runtime with coroutines that can be copied.** The rest is the coroutine itself.
- **A machine whose state is data.** A machine is a loop over frames on the heap (chapter 3b), and the rest
  it hands a handler is an immutable value made of those frames. That is already not a closure *as a whole*.
- **The author, deliberately, at a coarse grain.** The state is a plain record with a codec; one step, from a
  state and an event to the next state, is an ordinary program over effects; the state is committed atomically
  with the step. My serverless library is built this way.

**The price** is serialisation, and it should not be made to sound small. A value written by one version of
the code is read by the next, so it needs a format and the care any stored schema needs: a stop that old runs
are waiting at cannot simply be deleted. And frames that hold arbitrary functions are not data until someone
makes them so. That is the reason the serverless library keeps a state the author names rather than the
machine's captured rest: on that machine the rest is a value in memory, but it holds closures — code, not data.

Where my own kernels stand today, so that nothing above is read as more than it is. One of them, the port to
Goose, writes a captured rest to bytes and resumes from the bytes: the step of a resumable function there is a
flat structure with no code in it, and an example saves one in 42 bytes and continues from it. That happens
inside one process. None of the kernels restores a captured rest in another process or after a restart. The
Rust library's rest can be cloned and has no serialised form. Everything of mine that survives a restart does
so by the last road in the list: a named state and a step.

## 11.5 The same two roads, when the rest is needed twice

Replay has a second use, and I went the same way with it.

Chapter 6 needed the rest of a program more than once — every provider tried, every supposition followed — and
said that some runtimes give one continuation, once. Where the rest is a piece of stack, or a future the
language will not copy, there is still a way to get many resumptions: for each path, run the body again from its
first line, feed back the answers already given until it reaches the choice, and give a different answer there.
That is resuming by replay, with the restrictions of 11.3 in small. What stands before the choice runs once per
path, and must do the same thing each time.

My Rust library did this for a while, because its programs were async functions and a Rust future cannot be
copied. It does not now. Two things replaced it, and they are the first two entries of the list in 11.4. On the
stable compiler an attribute turns a function into an enumeration with one case per stop, each case holding the
variables alive there; the enumeration can be cloned, so a handler resumes the same rest as often as it likes.
On the nightly compiler the same value comes from a coroutine that can be cloned. Both give a handler the same
two types, so one handler drives either.

The note that reports this (note 22, 2026-10-28, the one note of mine I know to be published)
gives figures. Its protocol, as far as it states one: 1,000 operations a run, microseconds per run, three
alternating runs, medians, and handlers that pass each answer through `black_box` so the compiler cannot fold a
run into a formula. On the lane "all paths of a choice among 1,000": 0.24 with the generated enumeration, 2.75
with coroutines, which the note says are not yet optimised, and 5.9 by replay. The replay figure is carried
over from the previous note and an earlier version of the interface, so the three were not measured in one
session. By the rule of chapter 13 the ratio is a hypothesis, and I quote it as one.

The same note records what made the generated code fast, and it is worth more than the figure. The first
version of the attribute produced a loop that matched on the state at every hop: 1.25 on that lane. The second
writes each continuation as straight code from its stop to the next one.

The price is in the note too. The attribute understands `if`, `while`, `loop`, `match`, `break`, `continue` and
`return`. A stop inside an expression, or inside a `for` loop, is a compile error that says how to rewrite the
code. Somebody has to turn the rest into data; here a compiler does it, for the shapes it knows.

A further port, to a systems language with no heap, has the rest as a flat structure of the variables that live
across the stop. Copying it is resuming it again, and its note (note 21) says the structure can be
saved as bytes. If so it is the second road with nothing left to write by hand. I have not run it for this
chapter. Section 11.4 says how far it has been exercised, read from that port's code: one example, 42 bytes,
inside one process, and nothing restored in another.

## 11.6 My position

Resuming by replay works, and I have built it. I no longer build on it. It restricts the code — determinism,
and no change of shape while a run lives — and its cost grows with the history; both restrictions fall on every
workflow anyone writes, for ever, in exchange for the kernel author not having to make the rest into data.
Keeping the rest as a real value has neither restriction. Its price is that the value must be serialised and
versioned, and that price is paid once, by the kernel or the compiler, or deliberately by the author of a state.

This is argued, not measured. I have not run the same workflows on both roads and compared defects or cost.

And the word needs one more distinction, which chapter 10 made first. Replaying a journal of answers to
**re-run** a program — to reproduce an incident, rebuild a report, test a new version against last month — is
sound, and I rely on it. The program is run on purpose, from its start, by someone who wants to see what it
does. My objection is only to replay as the means of **resuming**: making every wake-up of a waiting program a
re-execution of its past.

## 11.7 What must hold on either road

Three things do not depend on the choice.

**What comes after a stop may run more than once.** A worker can die between doing a thing and recording that
it was done. So an external call needs a stable idempotency key — derived from the run and the position, the
same on every retry — and a step must be safe to attempt again. Neither road removes this.

**The stored thing is the only truth.** Timers, status indexes and leases are operational data about runs. If
losing one of them can make a run *wrong* rather than slow, there are two sources of truth and they will drift.

**The model is not the operations.** A handler that keeps the rest gives you the waiting program. It does not
give you a scheduler, dashboards, pre-emptive cancellation or a team on call. A workflow product does. If those
are what you need, buy them; the engine I described above had the model and said plainly that it lacked them.

And when not at all: a request that is answered while the caller waits, a job that finishes in a second.
Durability you do not need is storage you must still operate.

## 11.8 The two roads, on the two halves

Said in the terms of chapter 2b, the choice of this chapter is short.

The first road keeps the **first half** and gives up the second: what is stored is questions and answers, which
are data already, and the rest of the program is thrown away and grown again by running. It works on every
technique, because every technique has operations that can be written down. Its restrictions — determinism, a
fixed shape, a cost that grows with the history — are the price of regrowing a continuation from a record of
what it was asked.

The second road makes the **second half** data too: the rest itself is stored. It works only where the rest is
made of something that can be written — a state machine, a machine's frames with no closures in them, or a
state the author named by hand — and chapter 3a's axis (g) is the list of what qualifies. Its price is that a
continuation, once it is data, is a schema.

Chapter 5's map has the same split with other names. Event sourcing and the command log are the first road.
The hand-written state machine of chapter 1b is the second, without a compiler. What effects add to either is
that the program between two stops is still four straight lines.
