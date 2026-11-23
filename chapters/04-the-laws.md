# 4 · The laws

Sixteen renderings print the same ten lines. That is one scenario, and "they agree on a scenario" is not what
anyone means by "they are the same thing". This chapter says what must hold of *any* implementation of the
idea, whatever it is made of, and then checks it: as twelve more lines that every rendering prints, as the
same laws asked of a thousand generated programs in two of the Scala kernels, and as theorems in Lean.

Three earlier chapters left this owing. Chapter 2b said that the theory has equations and practice has tests,
and that this book owed its tests. Chapter 3h said "the same" was not yet defined for the renderings. Chapter
16 listed it first among what was open.

A law, here, is an equation between two programs, or a statement of what a run may and may not do, that a
reader can rely on without knowing which rendering is underneath. Each one below comes with the smallest
example, and with what goes wrong in ordinary engineering when an implementation breaks it. They are in three
groups, and keeping the groups apart is half the chapter:

- **laws of the idea** (4.1 to 4.6): true of every program and every handler, or the implementation is not one;
- **laws a particular handler owes** (4.7): true because that handler was written to make them true;
- **things that are not laws**, though people expect them (4.8).

## 4.0 The vocabulary, and what "the same program" means

The bank is too large for laws. This chapter uses the smallest effects that show every one of them, in the
notation of section 2.0:

```
effect State     Get -> Number          effect Failure   Refuse(why) -> never
                 Put(n) -> Unit         effect Clock     Now -> Number
                                        effect Choice    Choose(options) -> one of the options

handler Cell(s)       : A ! (State + R)    =>  (A, Number) ! R
  done(a)             => done (a, s)
  Get, rest           => rest(s)                       -- and the cell still holds s
  Put(n), rest        => rest(unit)                    -- and the cell now holds n

handler Refusals      : A ! (Failure + R)  =>  (paid(A) | refused(Text)) ! R       -- chapter 2's
handler FixedClock(t) : A ! (Clock + R)    =>  A ! R                               -- Now, rest => rest(t)
handler AllChoices    : A ! (Choice + R)   =>  List(A) ! R                         -- chapter 2's
handler Log           : A ! (State + R)    =>  (lines, A) ! (State + R)            -- chapter 2's Journal
```

`Cell(s)` is a state: it answers `Get` with what it holds and `Put(n)` by holding `n`, and what it holds at
the end comes back beside the result. How a handler "holds" something is section 4.6's subject; until then
read the two comments as written. `Log` is the journal of section 2.5 for these two operations: it asks each
again of whoever is outside and writes `Get=3` or `Put(3)`.

Every law below says that two programs are **the same**, written `=`. What that means has to be said once.

Where a program is a tree, two programs are the same when they are the same tree: the same question at the
head, and the same program after each answer. Chapter 2b called the tree *free* for this reason: nothing in it
has been identified with anything else.

Where a program is not a tree — plain code on a stack, a generator, a state machine — there is nothing to
compare but behaviour: two programs are the same when no handler can tell them apart. The two definitions
agree, because a handler is given exactly the question and the rest, and nothing more.

A test cannot try every handler. The twelve lines look at a program through `Log` and `Cell`: the questions
it asked with their answers, the value it ended in, the cell afterwards. The generated checks on chapter 3's
tree compare trees, a few answers deep. Only the proofs are about all of them. Each section says which of the
three it is leaning on.

## 4.1 Sequencing

```
(x <- done v ; f(x))               =   f(v)                                 -- done, then f
(x <- p ; done x)                  =   p                                    -- p, then done
(y <- (x <- p ; f(x)) ; g(y))      =   (x <- p ; (y <- f(x) ; g(y)))        -- grouping
(x <- Op ; k(x)) then f            =   x <- Op ; (k(x) then f)              -- a question, then f
```

The first three are the laws of a monad, and the reader who knows the word may stop there. For the reader who
does not: `done` does nothing on either side of a `<-`, and a program made of three steps is the same
program whichever two you group first.

The fourth is chapter 2b's definition of *algebraic*: whatever follows a question can be moved inside it. In
a tree it is not a fourth law but the way sequencing is defined at a question, and the first three follow
from it by induction on the tree.

The smallest examples, with `bump = x <- Get ; Put(x + 1) ; done x` and `put(n) = Put(n) ; done n`:

```
1 done then f: Put(5) => 5, cell 5 = Put(5) => 5, cell 5
2 p then done: Get=0 Put(1) => 0, cell 1 = Get=0 Put(1) => 0, cell 1
3 grouping: Get=0 Put(1) Put(2) => 1, cell 2 = Get=0 Put(1) Put(2) => 1, cell 2
```

Each line is two runs, one of each side, printed as "the questions seen `=>` the value, the cell".

**What breaks without them.** Refactoring. Extracting three lines of a program into a function of its own is
the third law read from right to left; inlining a helper is the same law read the other way; deleting a
`x <- p ; done x` that a code generator left behind is the second. An implementation in which grouping
changed what a handler sees would be one in which no program could be tidied without re-testing it under
every handler.

**Where the law is not trivial.** In chapter 3's forty lines it is nearly a definition. It stops being one as
soon as an implementation is made fast. Stage 4 of chapter 3i does not substitute when it sequences: `flatMap`
builds a `Bind` node that holds the two halves, so that `(p then f) then g` and `p then (f then g)` are
*different values*, and a third one, `Delay`, stands for a program not built yet. The laws are then false of
the nodes and must be true of what every interpreter makes of them. That is what stage 4's one loop, `head`,
is for, and it is why the generated check for stage 4 compares what handlers see and not the nodes.

**Where the law is the host's.** In direct style there is no `<-` to have laws: `done v` is a value in hand
and "then" is the next statement. In those renderings lines 1 to 3 are properties of the language's own `let`
and call, seen through the handlers. They are run all the same. A rendering that broke them would be one whose
handler behaved differently when the same statements were moved into a function, and a handler that inspects
the stack could do that.

## 4.2 A handler is a fold

Chapter 2b said a handler is a fold over the tree. As equations:

```
(done v) under H             =   H's done line, at v
(x <- Op ; k(x)) under H     =   H's line for Op, with rest = (x => k(x) under H)      -- Op is one of H's
(x <- Op ; k(x)) under H     =   x <- Op ; (k(x) under H)                              -- Op is not H's
```

Three cases, and there is no fourth: a program is `done` or a question, and a question is the handler's or it
is not. So these three lines say everything `under` does, for every program. Two things in them are easy to
read past.

The rest that a handler's line is given is **already handled**: it is `k(x) under H`, not `k(x)`. That is
what a *deep* handler is, and it is why `Refusals` can return `refused(why)` for the whole computation.

And the third line is the whole of how handlers of different effects live together: a question that is not
`H`'s goes by, still asked, and `H` goes on standing behind it.

One consequence is used more than the equations themselves. Handling one program after another:

```
(x <- p ; f(x)) under H      =   p under H',   where H' is H with its done line replaced by
                                               done(v) => f(v) under H
```

Handle the first part; where it is done, go on with the second, handled. For a handler that carries
something, this says the something is passed on at the seam:

```
(x <- p ; f(x)) under Cell(s)    =   (a, s1) <- p under Cell(s)
                                     f(a) under Cell(s1)
```

```
4 handling done: 7, cell 3 | paid(7)
5 handling a sequence: 1, cell 2 = 1, cell 2
```

Line 4 is `done 7` under `Cell(3)` and under `Refusals`: each handler's `done` line, and nothing else. Line 5
is `bump` twice in sequence under `Cell(0)`, handled whole and handled in two pieces with the cell carried
across by hand.

**What breaks without it.** Everything that stops a program in the middle. Chapter 11 keeps the rest of a
program and resumes it tomorrow; that is the second equation above with a night between its two sides. A
handler whose result for a whole program were not determined by its results for the parts could not be
stopped at a seam and picked up again. The same law is what lets a test run the first half of a long program
once and try several second halves from the state it left.

**The shallow handler.** A handler whose line is given the rest *unhandled* — `k(x)`, not `k(x) under H` —
is called shallow. It is a legitimate design (it is how one writes a handler that changes its behaviour after
the first question), and it is a different one: the second equation becomes "`H`'s line for `Op`, with rest =
`k`", and the fold is gone. A library should say which it has. Every rendering here behaves as a deep one.

## 4.3 Its own questions, every one, and no other's

Read as a statement about a run and not about a tree, section 4.2 says:

> A handler is offered every question of its effect that the program under it asks, in the order asked,
> unless a handler further in answered it first. It is offered no question of another effect.

```
stopped  =  Put(1) ; x <- Get ; Refuse("stop at {x}") ; Put(9) ; done 9

6 its own and no other's: Put(1) Get=1 => refused(stop at 1), cell 1
```

That is `stopped under Refusals under Log under Cell(0)`. The log has the two questions of the state that
were asked, with their answers, in order. It does not have the `Refuse`, which is not its effect and went by
on its way to `Refusals`. It does not have `Put(9)`, which was never asked.

**What breaks without it.** Chapter 10. "A program cannot go around the journal" is this law. An
implementation in which some path from a program to the world did not pass the handler — a method called on
`this`, a reference kept from an earlier scope, a question answered by a default that nobody installed —
would have an audit log that is true of what was routed through it.

**How strong it is depends on the rendering, and here the honest answer is not the same everywhere.** Where a
program is data, or a generator that can only yield, the law holds by construction: there is no other way out
(section 2.5 said so). In `code/scala/laws/capabilities.scala` it holds for the program as written, because
`stopped` uses the `State` it is given; nothing in the language stops another program from keeping the
`State` of an outer block in a variable and writing to it past the `Log`. Chapter 3c says so of that
technique ("an interceptor is a wrapper, with a wrapper's blind side"). Line 6 is the same text in both
renderings; what stands behind it is not.

## 4.4 A question nobody answers

> A program with a question left has no value. Running it does not run past the question: nothing after it
> happens. Where the row is in the type, such a program does not compile.

```
timed  =  t <- Now ; Put(t) ; x <- Get ; done x + t

7 nobody answers: Now                                                    -- timed under Cell(0), run
```

Twelve renderings print that line: the run stops and names the question. Four print another one, beginning
`7 nobody answers: does not compile:`, and so does stage 4 of the path, because in them `timed under Cell(0)`
with no clock is not a program that can be built and run:

| rendering | what refuses it | how that is checked |
| --- | --- | --- |
| `scala/laws/capabilities.scala` | no given `Clock` in scope | `typeCheckErrors`, inside the main |
| `scala/path/laws4.scala` | `run` takes the empty row | `typeCheckErrors`, inside the main |
| `haskell/TaglessLaws.hs` | no instance of `Clock` for the monad | `TaglessUnanswered.hs` must fail |
| `kotlin/laws.kt` | no context argument for `Clock` | `unanswered.kt` must fail |
| `koka/laws.kk` | `main` is left with the effect `clock` | `unanswered.kk` must fail |

A printed line that says "does not compile" is a constant, so it is evidence of nothing by itself. The two
Scala files ask their own compiler, while they are being compiled, whether the line type-checks, and assert
that it does not and that the same line with its clock does. The other three have beside them the smallest
file that is the program with its handler missing; `run-all.sh` compiles each, requires that it fail, and
requires the compiler's message to be the one about the missing handler.

The five refusals are of two kinds, and the difference is worth a sentence. Where the row is a real type
that a program carries — stage 4 and Koka — `timed` under the cell alone is a well-typed program with the
clock in its row, and what is refused is *running* it: `run` takes the empty row, and Koka refuses a `main`
that is left with an effect. Where the row is the set of things in scope — context parameters in Scala and
Kotlin, constraints in Haskell — the program cannot be mentioned where no clock is given. The first kind
lets a half-handled program be passed around as a value with its debts in its type, which is section 2.6's
"each handler removes its effect from the row".

**What breaks without it.** The claim in this book's title for Part VI: what is not answered does not happen.
The usual way to break it is kindness: a default handler, an ambient instance, a global fallback that answers
when nobody else did. Then "nobody answers" silently becomes "something answers that nobody chose", and the
row, where there is one, no longer says who.

## 4.5 Two handlers, two orders

Handlers of different effects can be applied in either order, and the types allow both. Whether the order
*matters* is the question people most often get wrong in both directions.

**It matters when one of the handlers does something other than answer once.** The standard case is state
and failure:

```
lost  =  Put(1) ; Refuse("no")

8 state then failure: refused(no) | failure then state: refused(no), cell 1
```

`lost under Cell(0) under Refusals` handles the state first, inside. `Cell` turns the program into one that
ends in a pair, and then `Refusals`, outside, drops the rest at the refusal — with the pair that was never
made. The result is `refused(no)` and nothing else. `lost under Refusals under Cell(0)` handles the failure
first; the refusal is now an ordinary value inside the program that `Cell` goes on handling, and the result
is `(refused(no), 1)`.

Said as an equation, which is the precise form of "the order matters":

```
Put(n) ; Refuse(why)   =   Refuse(why)          -- true under Cell inside Refusals; false the other way round
```

With the cell inside, a write before a refusal cannot be observed: the refusal takes it. With the cell
outside, it can. Neither is the right one. The first is a transaction that rolls back; the second is a
counter that survives the failure, which is what an audit log, a rate limit and a retry budget must be. The
program does not choose. Whoever writes the one line that runs it does, and that is the point of chapter 7.

The two results are related exactly, for every program and not only this one:

```
p under Cell(s) under Refusals   =   (r, s1) <- p under Refusals under Cell(s)
                                     done (r with s1 beside its value if r is paid; r if it is refused)
```

The cell handled inside is the cell handled outside with its content forgotten wherever the program was
refused. It is a theorem in the Lean file (`refusals_cell`) and one of the generated checks.

**It does not matter when both only answer.** A handler that calls `rest` exactly once, as the last thing
it does, with nothing of its own wrapped around the result, cannot tell what stands outside or inside it:

```
9 two that only answer: 14, cell 7 = 14, cell 7
```

`timed under FixedClock(7) under Cell(0)` and `timed under Cell(0) under FixedClock(7)`. For these two
handlers it is proved for every program (`cell_fixedClock`). I have not proved the general statement — "any
two handlers that only answer commute" — and the chapter does not claim it as more than what the examples
and the shape of the fold suggest. It is in the backlog.

**A caution about handler objects.** In the renderings whose handlers are objects with fields, line 8
is true of the *result*, and the handler object is still there afterwards with `1` in it. The law is about
what the handled program returns. Anything read off a handler object after the run is outside it.

**And about three.** In `java/LoomLaws.java`, `go/laws.go` and `csharp/laws.cs`, "the order of handlers" is a
position in a list that one driver walks, innermost first. Lines 8 and 9 hold there because that driver was
written to fold the handlers' `done` lines from the inside out and to let a refusal skip the ones inside it.
Nothing is nested. The law is the driver's to keep, and a second driver would have to be checked again.

## 4.6 Zero times, once, twice: the rest is a value

Section 2.4 gave a handler three things to do with the rest. Each has its law.

**Not at all.** Nothing after the question happens. Line 6 already showed it: `Put(9)` is not in the log.
(Section 4.8 says what this law does *not* promise: that what the rest was holding is released.)

**Once, at once.** A line `Op, rest => rest(v)` is the same as answering in place. This sounds like nothing
and is the law that every fast path depends on: stage 4 of chapter 3i has two kinds of handler for a state,
one that is given `k` and resumes it and one that returns the answer and is given nothing, and they have to
be the same handler. The generated check asks that of a thousand programs, on one path each, and it holds.

**Twice.** The rest is a value, and calling a value twice with the same argument gives the same thing twice:

```
rest(v) ; rest(v)       -- the second call starts where the first one started, not where it stopped
```

That is the whole law, and what follows from it is the rule section 6.8 called positional. A handler
standing *inside* the one that resumes twice is part of the rest: it is resumed twice with it, and each time
from what it held at the question. A handler standing *outside* is not part of the rest: it sees two
programs run one after the other.

```
forked  =  Put(1) ; x <- Choose([10, 20]) ; y <- Get ; Put(y + x) ; z <- Get ; done z

10 twice, state inside: 11, cell 11 | 21, cell 21             -- forked under Cell(0) under AllChoices
11 twice, state outside: 11 | 31, cell 31                     -- forked under AllChoices under Cell(0)
```

Inside, each path has its own cell: both start from the `1` that was put before the choice. Outside, there
is one cell, and the second path reads the `11` the first one left.

**What a handler's own state must therefore obey.** It belongs to *one application* of the handler to one
program, and at every question it is part of what the rest closes over. Two things follow. Applying the same
handler to two programs gives two states. And a rest resumed twice resumes the handler's state twice from
the same value. A handler that keeps its state in a variable of its own breaks the second, and here is the
finding of this chapter.

**The finding: a cell in a variable.** `code/scala/laws/generated.scala` has a third state handler,
`Variable`, written the way chapter 3's `InMemory` and most of the renderings' handler objects are written:
the content is a `var` of the handler. On a single path it is indistinguishable from `Cell`. Put inside a
handler that resumes twice, it prints for line 10

```
11, cell 11 | 31, cell 31
```

which is line 11's answer. The handler stands inside the choice and behaves as if it stood outside: for a
handler with a variable, *the order of handlers no longer decides* whether a state is each path's own or
shared, and section 4.5's promise that the one line chooses is void for it. Asked of a thousand generated
programs, "the rest called twice with one answer ends twice the same" fails for 12 with the variable and for
none with the cell passed as a value; stage 4's in-place handler with a variable differs from its `k`
handler on every path of a choice for 18 of the same thousand.

This is not news to chapters 3b and 6, which name the hazard, or to chapter 3i, which grows the handler with
a state of its own to remove it. Chapter 3b says that neither behaviour is wrong and that a design must say
which it has, and that stands: a real account is one world whoever forks, and a handler for it should be
shared. What the law adds is the statement of what such a handler has given up: not "mutable state is
dangerous" but "this handler's state is not part of the rest", so that it has made the choice once, for
every line that will ever run it, and the line can no longer make it. It also says which renderings are
exposed.
Those that cannot resume twice cannot break this law; a variable is safe in them, and most of their law
files use one. Of those that can: the Haskell and Lean trees have no variables; the Rust and Zig law files keep a
handler's state as a field of the handled machine, so that copying the machine copies it; the tagless
rendering's state is its monad's, passed along by the bind; and the Scala and Java trees are safe only
because their law files were written with the state passed along, which the mutable `Accounts` of their
bank files is not. (The scenario's line 6 avoids the question by resuming under a bank that moves nothing.)

Koka is the instructive exception. Its `cell` in `code/koka/laws.kk` is written with a local `var`, and line
10 comes out right: each path has its own cell. I observed that; I did not know it beforehand, and the
backlog has it as a thing to read up in Koka's documentation. A variable there is evidently not what a
variable is on the JVM.

## 4.7 What one handler owes its own operations

Everything so far holds of any handler. This section is about one. The theory of state is `Get` and `Put`
*with equations*, and `Cell` is correct only because it satisfies them:

```
Put(n) ; x <- Get ; k(x)          =   Put(n) ; k(n)                -- you get what was put
Put(n) ; Put(m) ; k               =   Put(m) ; k                   -- the last put wins
x <- Get ; y <- Get ; k(x, y)     =   x <- Get ; k(x, x)           -- asking twice changes nothing
x <- Get ; Put(x) ; k             =   k                            -- putting back what was there is nothing
```

Each `=` here means: *under* `Cell(s)`, for every `s` and every rest `k`. They are not true of the programs,
which are four pairs of different trees.

```
12 the cell's own: 3 4 0=0 cell 5
```

`Put(3) ; Get` gives 3. `Put(3) ; Put(4) ; Get` gives 4. Two `Get`s in a row give `0=0`. `x <- Get ; Put(x)`
under `Cell(5)` leaves 5.

Chapter 2b explained why the tree does not have these equations built in, and the generated check makes the
reason a line of output: under `Log`, `Put(1) ; Put(2)` and `Put(2)` *differ*, because the observer wrote two
lines or one. An observer is a handler that refuses an equation the answering handler satisfies. A tree with
the equations built in could not be observed.

So for each effect of a system there is a short list like this one, and it is the specification of every
handler that claims to answer that effect: the in-memory one, the database one, the recorded one. A test
double that fails "you get what was put" is not a double of the store. Chapter 8 has such a list for each
standard effect, and runs this one against a stub that fails it (section 8.1). The other handlers of this
chapter owe little: `FixedClock(t)` owes that `Now` is `t`; `Refusals` owes
`Refuse(why) ; k = Refuse(why)`, which is section 4.6's "not at all".

`Log` owes one thing, and it is the law of every interceptor that claims only to watch:

```
(p under Log) with its lines thrown away   =   p
```

An observer changes nothing but its own record. Proved for `Log` over `Cell` (`log_unseen`), and checked on
generated programs in both Scala kernels; on stage 4 also in the stronger form "an interceptor that asks
every question again and does nothing else is no interceptor".

## 4.8 What is not a law

Each of these is something I have seen assumed, or assumed myself.

**That handlers commute.** Section 4.5. They commute when both only answer. A type that lets two handlers be
applied in either order is not a promise that the orders agree.

**That operations satisfy equations.** `Move(a, b, 10) ; Move(a, b, 20)` is not `Move(a, b, 30)`. A given
handler may make them indistinguishable; the tree does not, and an audit depends on its not doing so.

**That a `done` line runs once.** It runs once for each path that reaches the end: twice under
`AllChoices` on line 10, never under `Refusals` on line 8. A `done` line that commits, closes or sends is
written on an assumption about every handler outside it.

**That dropping the rest cleans up.** "Nothing after the question happens" is a law. "What the rest was
holding is released" is not: section 6.8 lists which ways of leaving run a finalizer in the libraries I have
read, and a rest that is kept and never called releases nothing in any of them. None of the law files has a
finalizer, so nothing here tests either half. Section 8.6 does, with a scope written on the forty lines: a
rest dropped from outside leaves it unreleased, and a rest resumed twice releases it twice.

**That a program is a value until it is run.** In a tree it is: `p under h` builds a tree and nothing
happens. In direct style `p under h` is a call, and it happens now. Chapter 2 said "it is built without
anything happening", and that is true of the idea and of the renderings whose program is a tree; the twelve
lines cannot tell, because every one of them runs what it builds at once. I would not rely on it across
renderings.

**That an interceptor sees inside everything.** It sees every *question*. A construct that holds a
sub-program — the bracket of section 7.6 — is not a question in the sense of section 4.1 (chapter 2b showed
that it fails the fourth equation), and what an interceptor sees of its body depends on how it was built.

**That two renderings that agree here agree everywhere.** The twelve lines are twelve programs. The
generated checks are a thousand, in one language. What carries the laws to the other fifteen renderings is
that each was written to be a fold, and twelve lines that would catch the mistakes this chapter names.

## 4.9 Proof beside test

`code/lean/Laws.lean` is the tree of chapter 3b in Lean, over *any* signature, with twenty-four theorems
that Lean checks when it reads the file. There is no `sorry`; the ones proved by induction depend on Lean's
own axioms `propext` and `Quot.sound` (I asked it of five), and none on an axiom of mine.

| theorem | what it says | section |
| --- | --- | --- |
| `done_bind`, `bind_done`, `bind_assoc` | the three laws of sequencing | 4.1 |
| `ask_bind` | a question commutes with sequencing; by definition | 4.1 |
| `fold_done`, `fold_ask` | what a fold makes of `done` and of a question | 4.2 |
| `fold_bind` | handling a sequence: the first part, then the second handled | 4.2 |
| `fold_unique` | anything that obeys the two clauses *is* the fold | 4.2 |
| `bind_fold` | sequencing is itself a fold | 4.1, 4.2 |
| `handle_mine`, `handle_other` | its own question; another's passes | 4.2, 4.3 |
| `handle_nothing` | a handler that takes nothing is none | 4.2 |
| `run_ask` | a question nobody answered has no value | 4.4 |
| `put_then_refuse_inside`, `…_outside`, `refuse_outside` | the equation of 4.5, true in one order | 4.5 |
| `refusals_cell` | the two orders related, for every program | 4.5 |
| `cell_fixedClock` | the cell and the fixed clock commute, for every program | 4.5 |
| `cell_bind` | handling a sequence under the cell, the content passed on | 4.2 |
| `get_after_put`, `put_after_put`, `get_after_get`, `put_what_was_got` | the four equations of a state | 4.7 |
| `log_unseen` | the observer changes nothing but its lines | 4.7 |

The first thirteen are about every signature and every handler. The rest are about this chapter's `cell`,
`refusals`, `fixedClock` and `log`.

**What a proof gives that the tests do not.** Three things, and the third is the one that matters.

It is about every program. The generated checks try a thousand terms of a small language, each a handful
of questions; `bind_assoc` is about trees of any depth and any width, over operations I have not thought of.

It is about every *handler*. `fold_bind` has the handler's two clauses as variables. No test can range over
handlers.

And it can say that something is the *only* one. `fold_unique` states that a function which treats `done`
as the first clause says and a question as the second clause says, given its own result on the rest, is the
fold and no other function. That is the precise sense in which section 4.2's three equations "say everything
`under` does", and it is not a statement a test can be written for, because it quantifies over all possible
implementations of `under`.

**What the proofs do not give.** They are about the Lean tree, which is one of sixteen renderings. They say
nothing of the Scala kernel, whose `handle` I believe to be the same function and have not proved to be;
nothing of a generator or a goroutine; and nothing of two laws that are not about a tree at all: that a
variable in a handler breaks section 4.6 (Lean has no variables, so the law cannot fail there, which is a
different thing from having been proved), and that a compiler refuses line 7. A proof is deep and narrow; the
twelve lines are shallow and reach every rendering. The chapter needed both.

What is proved and what is only tested, then: sections 4.1, 4.2, 4.3 as equations and 4.4 at run time are
proved for the tree; 4.5 and 4.7 are proved for these four handlers; 4.6 is tested and not proved, in the
renderings that can state it; "does not compile" is checked by running five compilers and is not a theorem
anywhere.

My own Lean port proves more of the same kind about a larger kernel, with rows, an interceptor and an audit
(chapter 3g names it). Nothing here depends on it, and nothing here was copied from it; the file under
`code/` was written for this chapter on the tree of `code/lean/Bank.lean`.

## 4.10 The laws, by rendering

What each rendering was made to show, and by what. **run**: the line is printed from two runs and compared.
**host**: run, and what it tests is the host language's own sequencing, since the rendering has no `<-` of
its own. **driver**: run, and true because of how the one loop that drives the handlers was written, not by
nesting. **compiler**: the program is refused, and the refusal is checked. **cannot**: the technique cannot
state it, and the line says why. **gen**: also asked of the thousand generated programs. **proved**: also a
theorem. No cell is empty: every rendering prints something for every line.

| rendering | 1–3 sequencing | 4–5 fold | 6 its own | 7 nobody | 8–9 orders | 10–11 twice | 12 cell |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Scala, the forty lines | run, gen | run, gen | run, gen | run, gen | run, gen | run, gen | run, gen |
| Scala, stage 4 | run, gen | run, gen | run, gen | compiler | run, gen | run | run |
| Lean, a tree | run, proved | run, proved | run, proved | run, proved | run, proved | run | run, proved |
| Java, a tree | run | run | run | run | run | run | run |
| Haskell, a tree | run | run | run | run | run | run | run |
| Haskell, tagless | run | run | run | compiler | run | run | run |
| Scala, capabilities | host | run | run | compiler | run | cannot | run |
| TypeScript, generators | run | run | run | run | run | cannot | run |
| Python, generators | run | run | run | run | run | cannot | run |
| Java, virtual threads | host | driver | run | run | driver | cannot | run |
| Go, goroutines | host | driver | run | run | driver | cannot | run |
| C#, an iterator | run | driver | run | run | driver | cannot | run |
| Kotlin, `suspend` | host | run | run | compiler | run | cannot | run |
| Rust, a state machine | run | run | run | run | run | run | run |
| Zig, a state machine | run | run | run | run | run | run | run |
| OCaml 5 | host | run | run | run | run | cannot | run |
| Koka | host | run | run | compiler | run | run | run |

Notes that the cells cannot hold:

- **Tagless final.** "A handler" is the choice of a monad, and the laws of sequencing are the laws of that
  monad's bind: the rendering inherits them from whoever wrote the monad. The list monad handles a choice only
  as the innermost layer, so line 11 needed a layer written by hand, a small continuation monad whose
  `choose` calls the rest once for each option. Without it line 11 would be a "cannot".
- **Rust and Zig.** Lines 1 to 3 compare one combinator, `Then` (run a machine to its end, then start
  another), against machines written by hand. They are laws of that combinator. They do not check that a
  hand-written machine is the program it claims to translate; nothing does. The bank files' handlers are a
  flat list consulted by one loop, which cannot give a result whose shape depends on the order, so the law
  files add a machine `Under(handler, machine)` and the order is its nesting.
- **C#.** The law file adds `Done` and `Then` as iterators, so lines 1 to 3 are compositions of state
  machines there and not the host's statements.
- **Kotlin.** `bank.kt` gives a program one receiver with every effect in it. The law file names each
  effect as a context parameter instead, because one receiver cannot express a different stack for each line,
  and line 7 would then not be a fact about the program.
- **The eight that say "cannot" for lines 10 and 11** are the eight that said it for line 6 of the scenario.
  Those whose bank files make the attempt make it here too, and print the line after the second resumption
  failed: OCaml raises `Continuation_already_resumed`; the generators are refused by `handle`; Kotlin's
  second `resume` re-enters the state machine at its last state and fails on this JVM a step later.
- **Not covered at all:** stages 1, 2 and 3 of the path. Stage 3 could print the twelve lines as stage 4
  does; I did not write it. The generated checks exist in Scala only, and compare trees only for the forty
  lines.

## 4.11 The laws in one place

**Of the idea.**

1. `done` is a unit of sequencing, on both sides; grouping does not matter.
2. A question commutes with sequencing. (What does not is not an operation: chapter 2b.)
3. A handler is a fold: its `done` line at the end; its line for a question of its own, given the rest
   already handled; any other question passed on with the handler behind it. Hence: handling a sequence is
   handling the first part and then the second.
4. A handler is offered every question of its effect asked under it, in order, and no other's.
5. A program with a question left has no value and does not run past it; with rows, it does not compile.
6. The rest is a value. Not called, nothing after the question happens. Called once at once, it is an answer
   in place. Called twice with one answer, it ends twice the same.
7. A handler's state belongs to one application and is part of the rest. So a state handled inside a handler
   that resumes twice is each path's own, and outside it is shared.

**Of particular handlers.** The four equations of a state. That an observer changes nothing but its record.
That `Put(n) ; Refuse(why) = Refuse(why)` under the cell inside the refusal, and not outside. That the cell
and a fixed clock commute.

**Not laws.** That handlers commute; that operations satisfy equations; that `done` runs once; that a
dropped rest is cleaned up; that a program is a value until run; that an interceptor sees inside a bracket.

## What was run

Everything in this chapter is under `sh code/run-all.sh`, which exits 0 on the machine this was written on
with every toolchain of `code/README.md` installed.

| claim | what runs it | `run-all.sh` says |
| --- | --- | --- |
| the twelve lines, §4.1 to §4.7 | a law file beside each rendering; `scala/path/laws4.scala` | `laws`, 17 times |
| §4.4, "does not compile", in Scala | `typeCheckErrors` inside the two mains | part of their `laws` line |
| §4.4, the same elsewhere | `TaglessUnanswered.hs`, `unanswered.kt`, `unanswered.kk` | `refused`, 3 times |
| §4.1 to §4.7, generated | `scala/laws/generated.scala`, main `book.laws.generated` | `holds` |
| the same on stage 4 | `scala/path/laws4.scala`, main `book.path.stage4.generated` | `holds` |
| §4.9 | `lean code/lean/Laws.lean`: no message means every proof checked | `proved`, with the count |

The law files are `laws.*` beside each `bank.*` (`TreeLaws.java`, `LoomLaws.java`, `Laws.hs`,
`TaglessLaws.hs`, `Laws.lean` where the bank file is capitalised; `scala/laws/laws.scala` and
`capabilities.scala` for the two Scala renderings); `code/README.md` has the command for each. A law file
prints the twelve lines of `code/laws-expected.txt`, or in place of a line one that begins with the line's
name and `cannot:`, or `7 nobody answers: does not compile:`; anything else fails the script. The two
generated mains end `12 laws hold on 1000 generated programs each` and `10 laws hold …`, and print the
finding of section 4.6 on the way: `FAILS for 12 of 1000`, `FAILS for 18 of 1000`, and line 10 with a
variable.

The programs are generated by `code/scala/laws/terms.scala` from a fixed seed, so the figures 12 and 18
are the same on every run; they are counts over those thousand terms and say nothing about how often the
failure would be met in practice. The same file has the *reference*: what a term means on one path, computed
by plain recursion with no tree and no handler. Both kernels are compared with it — questions seen, cell,
result — and that is the check that would catch a kernel that was a fold of the wrong thing.

What the tests' equality is: for the forty lines, the same tree, followed twelve questions deep with two
answers tried at each `Get`, the options at each choice and one answer elsewhere; for stage 4, the same
outcome on every path under one stack of handlers from two contents of the cell. Both are weaker than "no
handler can tell them apart", and section 4.9 says what closes the gap and for which rendering.

What rests on argument and not on a run: that the two definitions of "the same" in section 4.0 agree; that
any two handlers which only answer commute (two are proved to); the paragraph on shallow handlers, of which
no rendering here is an example; and that the Scala kernel's `handle` is the function the Lean theorems are
about. What rests on one observation: Koka's local variable behaving as each path's own, and the way
Kotlin's second resumption fails.
