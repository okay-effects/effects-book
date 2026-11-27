# 13 · What it costs

Earlier chapters made promises about this one: that it would give the measured cost of the machine, of an answer
in place, of taking the rest of a program. It does. It also lists the costs that have no number, which in my
experience decide more adoptions than the numbers do.

Every figure below names the file it comes from, what was measured, how, and what that file says was not
measured. I have not re-run anything for this chapter. Where two sources disagree I say so.

## 13.1 Three things were measured, and the forty lines are not one of them

Keep these apart; most wrong conclusions come from mixing them.

- **The forty lines of chapter 3**, and the fifteen other renderings under `code/`, have never been
  benchmarked and should not be. Each is the shortest honest form of its technique. They exist to be read.
- **My JVM library** (Scala) is what `okay/docs/benchmarks.md` measures, with JMH, against other libraries and
  against plain code. The continuations book's chapter 20 (`20-the-costs-measured.md`) measures the same
  library's delimited control.
- **The reference kernels** in Scala 3, Java 25 and Rust are measured in the `okay-effects` README, each
  against itself only.

The benchmarks page states its own rule first: one measurement is a hypothesis, not a result; lanes quoted
together were measured in one session. Its tables are the per-lane minimum of three rounds. And it ends with a
limit that applies to everything here: these are microbenchmarks, "naked plumbing, no real workloads; they price
mechanisms, not applications".

## 13.2 A handler against no handler

This is the comparison a sceptical engineer should ask for first, and the page has it (section 2c, dated
2026-09-26): 1,000 state and log operations, written as a plain `while` loop and as handled effects. Minima of
three rounds, two forks, with allocation profiling; a set-up step checks that every version computes the same
answer before any is timed.

| the same 1,000 operations | ns per operation | bytes per run |
| --- | ---: | ---: |
| plain loop, mutable buffer for the log | 0.71 | 4,192 |
| plain loop, the same immutable log the handlers keep | 2.8 | 34,136 |
| handlers inlined at compile time | 7.8 | 85,368 |
| handlers on the general road (program as data) | 14.2 | 164,928 |

Against a loop that keeps the same immutable log, the handlers cost 2.8 times when inlined and 5.1 times on the
general road. Against what an imperative programmer would write — a mutable buffer — 11 and 20 times.

The same section compares with an interface, the handler as dependency injection. A virtual call that the JIT
cannot inline read 1.4 ns per operation with a mutable log. The page marks this table as one round, stopped by
the operator, with one row discarded because the machine got busy. Its own conclusion is the one I would quote:
effect handlers here "are not 'goto speed'"; the handler is a program the runner walks, where the interface is a
call the CPU predicts.

So: several nanoseconds per operation over an interface call. For a question that then goes to a database, that
is nothing. For arithmetic in an inner loop, it is everything; chapter 14 says what to do about that.

## 13.3 Effects in the type against interfaces passed in

Section 2d of the same page: a mutual recursion of a million levels that counts every level and logs every
thousandth, with both effects in the row and the shipping handlers. Minima of three alternating rounds.

- As first written: 33.3 ms for the million levels (so 33 ns a level), 376 bytes a level.
- After one fix in the library (a `modify` that was two operations became one): 18.2 ms, 160 bytes a level.
- With the frequent effect handled innermost: 9.7 ms, 88 bytes a level.
- The same recursion with the count and the log passed as interfaces: about 4 ns a level, against 9.7.

Two things to take from it. The effect row costs about 2.4 times an interface call on this task, by the page's
own summary. And **the order of handlers is a price**: an operation that passes another handler on its way out
costs 72 bytes each time, so the same program was nearly twice as fast with its handlers the other way round.
Nothing in the types tells you that. It is a thing to know.

## 13.4 The machine itself

Chapter 3b said a library of programs as data is a loop over frames
on the heap. Section 2e prices that loop on the one task
plain JVM code cannot do at all: `isEven` calling `isOdd` a million deep, where direct recursion overflows.

| road, per level | time | bytes | rounds |
| --- | ---: | ---: | ---: |
| a hand-written state machine (the floor) | 0.50 ns | 0 | 2 |
| a hand-written trampoline | 1.90 ns | 32 | 2 |
| cats `Eval.defer` | 1.91 ns | 32 | 2 |
| my library, continuation-passing road | 2.16 ns | 32 | 1 |
| my library, program-as-data road | 2.26 ns | 40 | 1 |
| ZIO `suspendSucceed` | 4.17 ns | 56 | 2 |
| kyo `IO` | 8.35 ns | 88 | 2 |
| cats `IO.defer` | 8.89 ns | 64 | 2 |

This is the number chapter 5 used. Its limits, from the page: the run is marked **partial** — it was stopped
after two rounds of the plain lanes and one round of the effect lanes, and my library's rows are that one
round. Not measured: the JDK's internal continuations, and compiler tail calls.

Two sources describe this result slightly differently. The page's prose says ZIO is "1.9x"; my published note
on the word "impossible" says "about 1.8x". Both are right: 4.17 over 2.16 is 1.9, over 2.26 is 1.8.
The note compares the program-as-data road, which is the one that matters for this book.

The same note reports a second, cruder measurement: ten million steps in a single test run, 87.5 ms for my
library against 179.3 ms for ZIO, with three repeats at 48–78 ms against 153–169 ms. Divided out, that is
roughly 5 to 9 ns a step — several times the JMH figure, as a run without warm-up or forks would be. Where the
two disagree I take the JMH figure, because it has a protocol, and I take neither as "faster than ZIO": one
task, one machine, one partial run. The claim it supports is the narrow one of chapter 5 — that a program held
as data is not slow *in principle*.

## 13.5 Taking the rest of the program

An answer in place is the cheap case. Handing the rest of the program to a handler is the expensive one, and
chapter 6 was built entirely on it. The continuations book's chapter 20 prices it for my library, at 1,000
operations per lane, paired runs with three forks, each number dated and tied to a commit:

- a cons in a loop, the floor: about 4 ns;
- installing a boundary and capturing nothing: about 24 ns;
- one capture: about 90 ns on 2026-09-17, and about 69 ns after allocation work ten days later.

Its headline is stated against itself: a generator built from captures costs about 22 times a hand-written
`while` loop. And it records a claim of mine that was wrong. A comment said a guard around a stream cost "the
prompt push, not the capture price". The lane that measures that shape had not been written; once it was, the
guard cost 2.01 and then 2.27 times the work inside it, across two rounds. The absolute times moved 20–35%
between those rounds and the ratio held.

Two figures in that chapter look like a contradiction and are not, and tracing them to the benchmark's history
file showed that both are labelled loosely. The first table gives "a capture's allocation" as 104 bytes. That is
a *difference*: what a lane with 100 captures allocates over the same lane with none, divided by 100 — the delay
and the closure of one capture — and it was measured on 2026-09-15, in a different benchmark from the table it
sits in. The later sentence, a capture "now costs about 69 ns and 726 B", is a *total*: everything the generator
lane allocates, divided by its 1,000 elements — the capture, the resumption, the frames and the emitted value.
So 104 bytes is the capture alone, on an older engine, and 726 bytes and 69 ns are a whole step of a generator.
Calling the second "a capture" overstates what a capture costs, and the 69 ns in the list above should be read
the same way.

The rule that chapter draws is the one to keep: a cost claim is measured only if some lane has the shape the
claim is about. Honest numbers nearby are camouflage.

## 13.6 The reference kernels

The `okay-effects` README has one benchmark in three languages. Nanoseconds per operation, the best of seven
rounds, one machine; in the README's words "a record of runs, not a claim". Java's and Rust's rows are one run
each; Scala's is a range over two sessions under different load.

| | `state` | `reader3` | `choose` | `binds` |
| --- | ---: | ---: | ---: | ---: |
| Scala 3 | 7.2 – 9.8 | 9.5 – 11.6 | 109 – 125.6 | 6.1 – 6.5 |
| Java 25 | 11.0 | 13.5 | 88 | 6.4 |
| Rust | 5.7 | 7.8 | 187 | 5.8 |

`state` is a get and a put in a loop, answered in place: this is the "few nanoseconds" of chapter 3. `reader3`
is a question answered three handlers away. `choose` takes the continuation twice at every step of a tree of
depth 18: eight to thirty times the price of an answer in place. A further lane passes every operation of a
sensitive effect through its audit, which takes and resumes the rest each time: 33 to 67 ns per operation
across the three languages, three runs.

No other library is in that table. It says what these kernels cost, not how they compare. okay-tiny, the
smallest of my JVM kernels, has its own figures in section 3j.9, from its own record and another day; they are
not comparable with this table either.

Several notes of mine (numbers 02, 09, 14, 20, 21, 22) carry further figures: other
languages, other roads, earlier states of the libraries. Two are quoted where their subject is, in chapter 10
(section 10.8) and chapter 11 (section 11.5), each with as much protocol as its note states. The rest are not in
this chapter. A note written for a feed says what was faster and seldom how often it was run, and the pages
quoted above measure most of the same things under a stated protocol. One line from those notes is a design
rule and needs no figure: *data where the rest must be data, compiled code elsewhere.* It is section 14.5's
advice seen from the kernel's side.

## 13.7 What the sources say they did not show

Collected in one place, because a reader should not have to assemble it.

- Nothing here measures a service under load. The one workload-sized comparison on the benchmarks page (its
  section 20, a streaming job over a city's timetable) prices that library's streams against stream engines,
  not the cost of handlers in business logic, and I do not use it as evidence for this book's subject.
- The benchmarks page says where my library loses: forking and joining 100 fibres against kyo (24.0 against
  18.5 µs), and one many-to-many channel lane against ZIO.
- The same page found five of its own comparisons unfair on one day in September 2026 — all on my side, four in
  my favour — and re-measured. It now requires the same shape, granularity, source and resources on both sides
  before a competitor's number is quoted. I take that as evidence the page is audited, and also as evidence of
  how easily such tables flatter their author.
- The page calls its host "a quiet 14-core box" in its summary and "a busy laptop" in its limits. Individual
  sections record the load; I would not rest anything on a difference under ten per cent.
- This chapter quotes three sections of a long page. The rest of it prices streams, channels, parsers and other
  parts of that library. I left it out as beside the point of this book, not because it is unfavourable: the
  two losing rows above come from it.

## 13.8 The costs that have no number

**Type errors in rows.** When a program asks an effect nobody handles, the compiler says so, and that is the
feature. How it says so is another matter: a mismatch between two rows can be reported as a mismatch between
two long types, far from the line that caused it. The agents that write code under my rules are asked afterwards
what was friction, and this is the complaint I read most often. Libraries can improve their messages, and
a language with effects built in can report the error in its own terms. In a library, today, it is a real cost.

**Stack traces show the machine.** A failure inside a program has a call stack made of the kernel's loop, not
of your functions. Debuggers step through the interpreter. Profilers attribute time to the runner. The journal
of chapter 10 is in some ways a better instrument than a stack trace, since it shows what was asked and
answered, but it is a different habit, and every tool your team already owns was built for the other shape.

**It is unfamiliar.** A function that returns a description, with nothing having happened yet, contradicts what
most engineers have practised for years. Chapter 6's hazards — what is below a question runs once per
continuation, the order of handlers changes the meaning — are not learned in an afternoon. I would expect hiring
to be harder too: many people know dependency injection, some know an effect runtime, few know this.

**The alternatives are mature.** The effect runtimes have years of production use, integrations with the
clients and servers a system needs, documentation, courses, and people who have debugged them at three in the
morning. Dependency injection has all of that in every language. Being right about a mechanism buys none of it.

**One author.** The libraries behind this book have a single maintainer, and it is me. That is a risk to
weigh like any other dependency risk. The idea does not depend on my code — chapter 3 is forty lines, and
languages with handlers built in exist — but a team that adopts my libraries is adopting that risk, and should
either be able to maintain what it uses or keep the usage small enough to replace.

[AUTHOR: is there anything factual to add here — users in production other than your own applications, a
second contributor, a stated support policy? I found none in the sources and have claimed none.]

## 13.9 Reading the bill

The measured part comes to this, and it divides along the two halves of chapter 2b. The first half — a
question that is a value, answered where it is asked — is cheap: an operation answered in place costs single
nanoseconds, a few times an interface call. The second half is what is paid for when it is used: taking the
rest of the program costs from tens of nanoseconds to somewhat over a hundred. The
machine that makes long programs safe is, on the one task measured, in the same range as a hand-written
trampoline. Programs whose every step is a question pay several times a plain loop.

Whether that matters depends on what a question is. If it is a row read from a database, the handler is noise.
If it is one addition in a loop of a billion, the handler is the whole cost, and the loop should not be written
that way.

The unmeasured part is larger and I would weigh it first: the error messages, the tooling, the learning, and
who maintains the thing. The next chapter is about when those tip the answer to no.
