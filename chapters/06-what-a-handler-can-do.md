# 6 · What a handler can do that a wrapper cannot

Chapter 5 ended on a table whose last column — stop, resume, repeat — only one row filled. This chapter is that
column. In the terms of chapter 2b it is the **second half** of the idea, alone: the rest of the program, up to
the handler, as a value in the handler's hand. It takes the five programs of chapter 1b and writes each one.

The listings are in the notation of section 2.0. Each is checked by code that runs: every program, handler and
result below is in `code/scala/continuation.scala`, on the forty lines of chapter 3 and nothing else, with the
results as assertions. `scala-cli run code/scala --main-class book.continuation` prints `continuation examples
hold`; each section names the block of that file's `main` that asserts it (the blocks are numbered 1 to 6 in
its comments). Where the notation says `paid(a)` and `refused(why)`, as chapter 2 did, the file says `Right(a)`
and `Left(why)`. `Outcome(A)` below is short for `paid(A) | refused(Text)`.

That file is the tree of chapter 3, and what the sections need differs by technique, so each says what it
needs. *Leaving* (6.2), *an instruction* (6.6) and *waiting* (6.3) need a rest that can be dropped or resumed
once: every technique of Part III has that. *Looking at what comes back* (6.4) needs more: the handler must be
handed the rest as something it can call and get a result from. *Again* and *twice* (6.5, 6.7) need a rest that
survives being used, which a generator, a coroutine or a thread does not give (chapter 3d). Section 6.8 says
which of the sixteen renderings under `code/` can do which.

## 6.1 The wrapper, first

A wrapper — a decorator, a proxy, an aspect, a middleware — stands around one call. It runs before, calls
through, runs after. That is a good deal:

- it can time the call, log it, check a permission;
- it can skip the call and return a cached or a default value;
- it can call again when the call fails: retry, with back-off;
- it needs nothing from the language, and every engineer can read it.

For a concern that lives entirely inside one call, a wrapper is the right tool and a handler adds nothing.

Its limit is in the word *around*. The wrapper holds the call. When the call returns, the wrapper returns, and
control goes back to the caller — whom the wrapper has never seen. Whatever the caller does next, on the next
line or three functions later, is out of its reach.

A handler is given the question **and the rest of the caller**, as the function `rest`. So there are things it
can do to that rest. I count six, and each is one of the programs that chapter 1b could not write.

## 6.2 Not continue it: leave from deep inside

The invoice with a closed cost centre. Two nested walks, and an answer that must get out of both:

```
line(allocs, closed) : Number ! Failure =            -- one invoice line: the sum of its allocations
  sum := 0
  for each a in allocs:
    if a.centre in closed then Refuse(a.centre)
    sum := sum + a.amount
  done sum

check(lines, closed) : Number ! Failure =            -- the invoice: the sum of its lines
  total := 0
  for each l in lines:
    part <- line(l, closed)
    total := total + part
  done total

invoice = [ [("north", 10), ("south", 20)], [("old", 5), ("west", 1)] ]

run( check(invoice, closed: {"old"}) under Refusals )  =  refused("old")
run( check(invoice, closed: {})      under Refusals )  =  paid(36)
```

`Refusals` is chapter 2's handler, unchanged: on `Refuse` it does not call `rest`. The outer walk, the inner
walk and the additions waiting in both were the rest, and they are dropped. Neither function returns a "maybe
rejected"; neither inspects what the other returned. The layer that forgets to propagate cannot exist, because
no layer propagates.

A wrapper can decline to call *through*. It cannot decline to return to its caller.

*Checked by* block 1 of `continuation.scala`, where the two walks are a recursive `line` inside `check` and a
fold over the lines. *What it needs:* a rest that can be dropped. Every rendering under `code/` does exactly
this with `Refuse` in line 2 of the scenario (`code/expected.txt`), each by its own means: the tree renderings
do not call a function, the generators are closed, the threads are unwound by an exception thrown at the
parked call, the state machines are dropped (chapter 3a, axis f).

## 6.3 Continue it later: push read as pull

A producer that emits, and knows nothing of who is taking:

```
effect Stream
  Emit(item) -> Unit

words(text) : Unit ! Stream =
  for each w in the words of text:
    produced := produced + 1               -- a counter outside the program: what the producer really did
    Emit(w)
  done unit

numbers(from) : never ! Stream =           -- an endless producer
  Emit("{from}")
  numbers(from + 1)
```

One handler continues it at once and collects everything — the callback style:

```
handler Collected : Unit ! (Stream + R)  =>  List(Text) ! R
  done(u)             => done []
  Emit(item), rest    => more <- rest(unit)
                         done [item] ++ more

run( words("to be or not to be") under Collected )  =  ["to", "be", "or", "not", "to", "be"]      -- produced = 6
```

Another does **not** continue it. It hands the item out together with the rest of the producer:

```
handler Pulled : A ! (Stream + R)  =>  (end | item(Text, next)) ! R
  done(a)             => done end
  Emit(item), rest    => done item(item, next: () => rest(unit))     -- rest is not called; it is handed out
```

Now the consumer holds the loop. It is ordinary code, not a program that asks, and it can stop:

```
take(n, source) =                          -- source is a program; nothing of it runs until run(source)
  if n = 0 then []
  else if run(source) is item(x, next) then [x] ++ take(n - 1, next())
  else []

take(2, words("to be or not to be") under Pulled)  =  ["to", "be"]          -- produced = 2: no third word
take(3, numbers(1) under Pulled)                   =  ["1", "2", "3"]       -- an endless producer
```

Two words were produced, not six. The producer was not filtered afterwards; it was never continued. An endless
producer is no trouble to a consumer that stops, and two producers can be read in step —

```
zip(words("a b c") under Pulled, numbers(1) under Pulled, 5)  =  [("a", "1"), ("b", "2"), ("c", "3")]
```

— with no thread and no queue, because each producer is a value in the consumer's hand.

The first version of `take` written for this chapter was wrong, and the mistake is instructive. It computed the
next step before checking whether it wanted one, and the counter said three. Pull means the consumer decides,
and the consumer must then really not ask. The assertion found it; without the counter the file would have held
a pull that pushed.

This is the generator that some languages build in. Here it is a handler of five lines and a small data type, in a
library, and the producer did not change between the two handlers.

*Checked by* block 2 of `continuation.scala` (`Collected`, `Pulled`, `take`, `zip`, and the counter). *What it
needs:* a rest that can be put down and picked up once, later, by someone other than the handler. Every
technique has it, and for the ones in direct style it is the native case: `code/typescript/bank.ts` and
`code/python/bank.py` are built on the language's own generator, which is this section the other way up — there
the producer's `yield` is the primitive and the handler is what is built from it. The limit is of another kind:
in a rendering whose rest is a generator object or a parked thread, `next` may be called once, and a consumer
that wants to go back to an earlier item and read on from there again needs section 6.7.

## 6.4 Continue it, and look at what comes back

Chapter 1b's fourth program wanted a hook on the way out that sees the outcome. The sharper version of the
same need is compensation: *if what follows goes wrong, undo what I just did.*

```
payAndShip(from, to, amount, inStock) : Text ! (Accounts + Failure) =
  Move(from, to, amount)
  if inStock = 0 then Refuse("nothing left to ship")
  done "shipped, paid {amount}"
```

The money moves; two lines later the caller may fail. The handler passes each `Move` on to the bank, continues
the rest, and looks at how the rest *ended*:

```
handler Compensated : Outcome(A) ! (Accounts + R)  =>  Outcome(A) ! (Accounts + R)
  done(r)                     => done r
  Move(from, to, n), rest     => moved <- Move(from, to, n)        -- asked again, of the bank outside
                                 back  <- rest(moved)              -- the rest of the caller, run to its end
                                 if back is refused(why) then
                                   Move(to, from, n)
                                   done refused("{why}; moved {n} back")
                                 else done back

shipped(inStock) = run( payAndShip("ann", "bob", 30, inStock) under Refusals under Compensated under bank )

shipped(inStock: 0)  =  refused("nothing left to ship; moved 30 back")      -- bank: ann 100, bob 0
shipped(inStock: 1)  =  paid("shipped, paid 30")                            -- bank: ann 70,  bob 30
```

(`bank` is chapter 2's `InMemory`, starting from `{ann: 100}`, the same handler for both runs.)

`payAndShip` has no `try`, no flag and no refund branch. The compensation is attached at the point where the
thing was done, and every way the caller can end passes it. A wrapper around `move` returns as soon as the move
is made; the failure it would need to see has not happened yet.

Two limits, plainly. This sees outcomes that are *values* — here a refusal, which `Refusals` inside it turned
into `refused(why)`. An exception thrown by the host language through the interpreter is not a value passing a
handler, and is not seen. And a compensating move is itself a move that can fail; nothing here makes it a
transaction.

*Checked by* block 3 of `continuation.scala`. *What it needs* is the thing chapter 2b called *delimited*: the
line `back <- rest(moved)` is a call that returns, with what the caller ended in. That is a stronger demand
than it looks. The handler must be handed the rest as something it can call, and must still be standing when
the rest has finished. The tree renderings (Scala, Java, Haskell, Lean), the generators (TypeScript, Python),
OCaml and Koka give a handler that; in OCaml it is the ordinary shape, `match continue k x with …`. (Of the
others: a capability has no rest; tagless final has what its monad has; and Kotlin's `resume` returns nothing
to its caller, the body's result going to a completion object instead.) Five renderings under `code/` do not,
as they are written, and the reason is worth a paragraph because it is not a weakness of their techniques.

In `code/rust/bank.rs`, `code/zig/bank.zig`, `code/csharp/bank.cs`, `code/go/bank.go` and `code/java/Loom.java`
a handler *returns a reply* — pass, resume with this, leave with that, ask this instead — and a loop acts on
the reply. The handler never holds the rest. It can refuse, answer, substitute a question, and (as the journal
does) see an answer on its way back to the program. It cannot see how the *program* ends after that answer,
because by then its call has returned. Nothing prevents it in principle: in the two state machines the rest is
a value the loop is holding and could pass in, and in the three on a stack the handler could be called again
when the body finishes. Those renderings chose the cheaper interface, the one that answers in place and
captures nothing (chapter 3e, "The handler, in this family"), and this section is what that interface gives up.
A library offers both.

## 6.5 Continue it again: retry the rest of the caller

A wrapper retries a call. This retries what comes *after* the call:

```
effect Offer
  Provider(candidates)       -> one of the candidates
effect Fees
  Quote(provider, amount)    -> Number                 -- the fee
  Charge(provider, amount)   -> Text                   -- a reference

checkout(amount) : Text ! (Offer + Fees + Failure) =
  p   <- Provider(["fast", "steady"])
  fee <- Quote(p, amount)
  if fee > 5 then Refuse("{p} asks a fee of {fee}")
  ref <- Charge(p, amount + fee)
  done ref
```

The caller is given a provider, asks it for a quote, and may refuse — three lines after the question was
answered. The handler continues the rest with the first candidate, and if the rest ends refused, continues the
same rest with the next:

```
handler TryEach : Outcome(A) ! (Offer + R)  =>  Outcome(A) ! R
  done(r)                        => done r
  Provider(candidates), rest     => last := refused("no provider")
                                    for each c in candidates:
                                      last <- rest(c)                -- the same rest, with the next candidate
                                      if last is paid(_) then done last
                                    done last

run( checkout(100) under Refusals under TryEach under Providers({fast: 9, steady: 2}) )  =  paid("steady-102")
  -- quoted  = ["fast", "steady"]     what is after the question ran once per resumption
  -- charged = ["steady"]

run( checkout(100) under Refusals under TryEach under Providers({fast: 9, steady: 7}) )
  =  refused("steady asks a fee of 7")                               -- charged = []
```

(`Providers(fees)` answers `Quote` with the provider's fee and `Charge` with "provider-amount", and keeps what
it was asked in `quoted` and `charged`.)

`checkout` contains no loop and no fallback. The line about `quoted` is the one to read twice: `Quote` was asked
twice, because it is below the question and the rest ran twice. Section 6.8 is about that.

*Checked by* block 4 of `continuation.scala`. *What it needs:* the same rest, called a second time. Here the
neutral notation hides a real difference between techniques, so it should be said in a real language. In the
tree `rest` is a function, and calling a function twice is nothing. In a state machine it is a value, and the
second call is a copy — in `code/rust/bank.rs`, for the scenario's own line 6:

```rust
let copy = rest.clone().step(Answer::Number(supposed));
```

In a rendering whose rest is a generator, a coroutine or a thread there is no second call: the first one moved
the only copy on. `code/typescript/bank.ts` makes the attempt, and its `handle` refuses with "resumed twice: a
generator's rest is not a copy". This section has no rendering but the Scala one; the line-6 handler of the
next section is the nearest thing that all sixteen attempt.

## 6.6 Continue it with an instruction

The importer and the row it cannot read. It has no `strict` flag; at the bad row it asks:

```
effect Import
  Malformed(row, text) -> skip | use(Number)            -- the answer is a verdict

total(rows) : Number ! Import =                         -- an importer with no policy parameter
  sum := 0
  for each (row, text) in rows, numbered from 0:
    if text is a number n then sum := sum + n
    else
      verdict <- Malformed(row, text)
      if verdict is use(n) then sum := sum + n          -- skip: nothing is added, and the loop goes on
  done sum

handler Policy(decide) : A ! (Import + R)  =>  Outcome(A) ! R
  done(a)                       => done paid(a)
  Malformed(row, text), rest    => if decide(row, text) is some(verdict) then rest(verdict)
                                   else done refused("row {row}: {text}")        -- rest is not called

rows = ["10", "20", "n/a", "30"]

run( total(rows) under Policy(always skip) )        =  paid(60)                  -- the nightly batch
run( total(rows) under Policy(always use(1)) )      =  paid(61)                  -- the migration
run( total(rows) under Policy(never decides) )      =  refused("row 2: n/a")     -- the compliance run
```

In the file the answer's type is named `Verdict`, with the cases `Skip` and `Use`.

In the first two the importer went on to the row after the bad one, with its running sum intact: it was not
unwound while somebody else decided. An exception could not do that; by the time a `catch` runs, the frame that
failed is gone. This is the oldest use of the whole mechanism — resumable exceptions, or conditions.

The honest cost: `Verdict` is a protocol between two pieces of code that never see each other. It is typed
here, which a callback returning an enum also is. What the handler adds is that the importer takes no parameter
for it, and that the third caller could end the import without the importer offering "abort" as a verdict.

*Checked by* block 5 of `continuation.scala`. *What it needs:* a rest resumed once, with a chosen value, or
dropped. Every technique has it. This is the one section of the chapter that the five reply-returning
renderings of section 6.4 can follow as they stand: "resume with this" and "leave with that" are two of their
replies.

## 6.7 Continue it twice, and compare

What would chapter 2's `pay` do if the balance were 100, or 10? Continue the rest once for each supposition:

```
handler WhatIf(suppose) : A ! (Accounts + R)  =>  List((List(Number), A)) ! (Accounts + R)
  done(a)                   => done [([], a)]
  Balance(account), rest    => all := []
                               for each s in suppose:
                                 outcomes <- rest(s)                -- the same rest, once per supposition
                                 for each (path, a) in outcomes:
                                   all := all ++ [([s] ++ path, a)]
                               done all

handler DryRun(would) : A ! (Accounts + R)  =>  A ! R            -- a bank that moves nothing, and says what
  done(a)                   => done a                             -- it was asked to move
  Move(from, to, n), rest   => would := would ++ ["Move({from},{to},{n})"]
                               rest(unit)

run( pay("ann", "bob", 30) under Refusals under FixedClock(42) under DryRun(would) under WhatIf([100, 10]) )
  =  [ ([100], paid("moved 30 from ann to bob at 42")),
       ([10],  refused("ann has 10, needs 30")) ]                 -- would = ["Move(ann,bob,30)"]
```

One past, two futures, and — because `Move` was answered by a handler that moves nothing — no money anywhere.
The same move forks an agent's run at a tool call to ask what it would have done with a different result.

*Checked by* block 6 of `continuation.scala` — and by line 6 of the scenario in all sixteen renderings, which
is this handler on this program. Eight print the line and eight print why they cannot (`code/README.md` has the
table; chapter 3h, question 1, has the reason for each). The eight that print it are the four trees, tagless
final in the list monad, the two state machines, which copy the rest, and Koka, where `resume` is simply called
twice:

```koka
ctl balance(account) suppose.flatmap( fn(s) resume(s).map( fn(o) s.show ++ " -> " ++ o.strip ) )
```

## 6.8 What holding the rest does to everything else

Each power above has a consequence, and they are the same consequence seen from different sides.

**What is after the question runs once per continuation.** `Quote` was asked twice in 6.5. Had `Charge` been
above the refusal instead of below it, the customer would have been charged twice. The rule is positional: draw
a line at the question; above it, once; below it, once each time the rest is continued.

**Two futures share one world unless the world is answered inside the fork.** The file asserts the hazard:

```
run( pay("ann", "bob", 30) under Refusals under FixedClock(42) under WhatIf([100, 50]) under real )
  -- the hazard: with a real bank outside, both futures act on ONE world — two moves
  -- real, which started from {ann: 100}, ends with ann 40, bob 60
```

The same `WhatIf`, a real bank outside it, suppositions of 100 and 50: both futures moved thirty. Where a handler
stands relative to the one that forks decides whether its state is shared by the branches or separate for each.
In forty lines that is all there is to say. A machine (chapter 3b) gives a handler a state that is part of the
captured rest, so each path has its own, and makes the order of handlers the place where you choose. (Checked by
the last assertion of block 6. Chapter 3b, "Where a handler's state lives", is the same hazard seen from the
side of the implementation: a handler with a mutable field, against state passed as a value.)

**What the rest was holding must still be released.** If `rest` is never called, a file it had opened stays
open. Chapter 3 said the forty lines have no finalizer, and a line of clean-up written after a question is not
one. A library has finalizers, of one of the three kinds in chapter 3a (unwinding, finalizers as data,
destructors), and it is worth being exact about when they run, because "always" is not
true of mine.

- A handler that **declares it will not continue** — an abort, a failure, an early exit — unwinds what it
  leaves, and each finalizer on the way runs.
- A rest that is **cancelled or unwound on purpose** runs its finalizers.
- A handler that receives the rest as a value, **keeps it and simply never calls it**, releases nothing. The
  kernel cannot tell a rest that is forgotten from one that will be called tomorrow.

So the rule for whoever writes a handler: if you will not continue the rest, say so — abort or unwind — and do
not let it fall out of scope. The rule is not special to a library. OCaml 5 has a construct for the first two
cases, `discontinue`, and a rest there that is neither continued nor discontinued is garbage whose resources
are not released (chapter 3f). Section 8.6 runs the smallest scope there is on the forty lines, and shows
what it does when the rest is dropped from outside it and when the rest is resumed twice.

**In my own libraries.** The first case is tested in both of my Scala kernels. The third is pinned by a test in
one of them: neither resumed nor unwound, nothing of it runs. I got this wrong once myself; the release of a
scope was lost when the engine under it changed, and it came back only for the operations that say they are
final. The simplest of my kernels has no finalizers at all, and its resource module says so. (Chapter 3g,
under Scala, names these libraries.)

**Some runtimes give one continuation, once.** Where the rest of a program is a piece of stack — virtual
threads, most coroutines — resuming it consumes it. That is enough for 6.2 to 6.4 and 6.6, and for chapter 11.
It is not enough for 6.5 and 6.7, which need the rest as a value that survives being used.

**So what a technique can do in this chapter is read off what its rest is.** For the sixteen renderings under
`code/`, as they are written:

| the rest is | renderings | 6.2 leave | 6.3 later | 6.4 look back | 6.6 instruct | 6.5, 6.7 again |
| --- | --- | --- | --- | --- | --- | --- |
| a closure in a tree | Scala, Java, Haskell, Lean | yes | yes | yes | yes | yes |
| what the monad's bind makes of it | Haskell, tagless | yes | by the monad | by the monad | yes | list monad |
| the host's stack, not a value | Scala, capabilities | by exception | no | no | yes | no |
| a generator object | TypeScript, Python | yes | yes | yes | yes | no |
| a parked thread, a goroutine, an iterator | Java, Go, C# | yes | not offered | not offered | yes | no |
| the compiler's continuation object | Kotlin | yes | yes | not directly | yes | no |
| a state machine, by hand | Rust, Zig | yes | at the edge | at the edge | yes | yes, by copy |
| a fibre; `resume` | OCaml; Koka | yes | yes | yes | yes | Koka yes; OCaml no |

"Yes" in the first and fourth columns is run: line 2 of the scenario leaves, and every handler of a bank
resumes with a value. The last column is run too: it is line 6. The middle two columns are read from each
rendering's handler interface and not run, because the scenario has no line for them: "not offered" is section
6.4's reply-returning interface, and "at the edge" means that the code which drives the state machine holds the
rest and can do with it what a handler is not handed. The backlog lists the missing line.

## 6.9 When the wrapper is enough

If the concern begins and ends inside one call — a timeout, a cache, a metric of that call, a retry of that
call — wrap the call. Nothing in this chapter improves on it, and more people can read it.

The difference in kind starts where the concern is about *what the caller does next*: leaving it, pausing it,
watching how it ends, running it again, handing it a decision, running it twice. Those are not six features.
They are the six things one can do with a function one has been handed: not call it, call it later, call it and
use the result, call it again, call it with a chosen argument, call it twice.

That is the second half of chapter 2b, exhausted. Nothing in this chapter needed the first half except in one
respect, and it is the one that makes the rest usable: each handler knew *which* question it had been handed
the rest of — a refusal, an item, a move, a provider, a malformed row, a balance — because the question was a
value with a name. Chapter 5's `shift` has every power shown here and cannot say which of them a given program
will meet. Chapters 7 and 10 are about the half that can.
