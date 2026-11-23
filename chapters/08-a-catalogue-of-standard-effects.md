# 8 · A catalogue of standard effects

Every system built this way ends up with the same dozen effects under its own ones: a value that changes, a
value that is only read, a log, a failure, a choice, something that must be released, the clock, chance,
things that happen beside each other, things that come one at a time. This chapter goes through ten of them.
For each: its operations, what its handlers do with the rest of the program, the handlers one usually wants,
what each of those owes, and what goes wrong.

The second item is the spine of the chapter. Chapter 2b said the idea is two halves, operations as data and
the rest of the program as a value, and that a mechanism with only the first half answers once, in place. So
for each effect I ask: **would the first half do?** The count is worth having before the entries. Six of
the ten never need the rest: state, reader, writer, time, randomness, and the operation that
registers a resource. An interpreter of command objects answers them. Four need it, each for a different
line of chapter 2b's table: failure drops it, choice calls it many times, async and streams call it once,
later. And everything that is hard in this chapter is in neither list. It is in the constructs that act on a
*stretch* of a program — a changed setting, a catch, a scope, a deadline, a fork with a body — which chapter
2b said are not operations at all.

The listings are in the notation of section 2.0. Each is checked by code that runs: every program, handler
and result below is in `code/scala/catalogue.scala`, on the forty lines of chapter 3, with the results as
assertions. `scala-cli run code/scala --main-class book.catalogue` prints `catalogue examples hold`, and
`sh code/run-all.sh` runs it. Section 8.n is block n of that file and of its `main`. Where the notation says
`paid(a)` and `refused(why)`, as chapter 2 did, the file says `Right(a)` and `Left(why)`.

The chapter builds on chapter 4 and does not repeat it. The state is chapter 4's `Get`, `Put` and `Cell`;
its four equations are section 4.7's; that the order of a state and a failure is a decision is section 4.5,
and that the order of a state and a choice is one is section 4.6. What is new here is the same questions
asked of the other nine effects, and several answers chapter 4 could only state: section 4.8 listed "a
`done` line runs once" and "dropping the rest cleans up" among the things that are not laws, and said
nothing there tested either. Section 8.6 tests both.

## 8.0 How an entry reads

**What the handler does with the rest.** Chapter 2b's table had four lines. This chapter uses five words for
them:

| the handler | this chapter says | which half it needs |
| --- | --- | --- |
| calls the rest once, at once, as the last thing it does | *in place* | data alone (section 4.6, "once, at once") |
| does not call it | *dropped* | the rest |
| calls it once, but not now | *later* | the rest |
| calls it more than once | *many times* | the rest, and one that survives being used |
| does something on its `done` line | *at the end* | that the rest ends exactly once: see 8.6 |

The last line is not on chapter 2b's table, and it is where this chapter's trouble comes from. A handler
whose `done` line acts — releases, commits, flushes — needs no power over the rest. It needs a promise about
it, and the promise is not its own to keep.

**What a handler carries.** Most handlers of this chapter remember something: a content, a log, a clock, a
queue. Chapter 4 found what happens when that is a variable of the handler (section 4.6: it stops being part
of the rest, and the order of handlers stops deciding anything). So here it is passed along. In a listing, a
handler's parameter is what it carries, and a comment says with what it goes on:

```
handler Cell(s) : A ! (State + R)  =>  (A, Number) ! R          -- chapter 4's
  done(a)        => done (a, s)
  Get, rest      => rest(s)                                      -- carrying s
  Put(n), rest   => rest(unit)                                   -- carrying n
```

The forty lines have no such handler; stage 4 of chapter 3i grows one. Chapter 4 got one for `Cell` by
making the handled program a function of the content. The file writes that trick once, as `carry` (block
0), and every handler below that carries something is a few lines on top of it. Three handlers of
the chapter keep a variable instead, on purpose, because what they stand for is a world that really is
shared and really does not take anything back: a console (8.3), the machine's clock (8.7), an event loop
(8.9). Each says so where it appears.

**What it owes.** Section 4.7 said that the specification of a handler is the list of equations it makes
true, and that this chapter owed such a list for each standard effect. Each entry has one, run where it can
be, and says what is *not* owed, which is as often the useful half.

## 8.1 State

```
effect State
  Get      -> Number
  Put(n)   -> Unit
```

In place. No handler of a state needs the rest for anything but going on.

Three handlers, and they are three places a content can live. In memory: `Cell(s)`, above. In a store: the
handler keeps nothing and turns each question into a question of a lower vocabulary, which is section 7.2's
"a handler for one effect may be written in terms of other effects" at its smallest:

```
effect Store
  Load(key)          -> Number, or nothing
  Save(key, value)   -> Unit

handler Stored(key, otherwise) : A ! (State + R)  =>  A ! (Store + R)
  done(a)        => done a
  Get, rest      => found <- Load(key)
                    rest(found, or otherwise if there is nothing)
  Put(n), rest   => Save(key, n)
                    rest(unit)

handler Table(rows) : A ! (Store + R)  =>  (A, rows) ! R         -- the store itself, in memory
  Load(key), rest         => rest(rows[key], or nothing)          -- carrying rows
  Save(key, value), rest  => rest(unit)                           -- carrying rows with key := value
```

Journalled: nothing is overwritten, a `Put` adds a line and a `Get` reads the last one.

```
handler Journalled(first, puts) : A ! (State + R)  =>  (A, List(Number)) ! R
  done(a)        => done (a, puts)
  Get, rest      => rest(the last of puts, or first if there is none)      -- carrying puts
  Put(n), rest   => rest(unit)                                             -- carrying puts ++ [n]
```

```
tick   =  n <- Get ; Put(n + 1)
twice  =  tick ; tick

run( twice under Cell(0) )                                =  (unit, 2)
run( twice under Stored("visits", 0) under Table({}) )    =  (unit, {visits: 2})
run( twice under Journalled(0, []) )                      =  (unit, [1, 2])
```

**What it owes.** The four equations of section 4.7: you get what was put; the last put wins; asking twice
changes nothing; putting back what was there is nothing. The file has them as a function, `stateOwes`, that
takes anything claiming to answer a state and runs both sides of each equation under it, from two contents,
with a rest that reads the state once more. All four hold for the three handlers above.

It also has a fourth handler, the test double as it is often written: `Get` is always what the test said,
`Put` is accepted and ignored.

```
stateOwes(Stub)  =  [fails, holds, holds, holds]
```

It fails "you get what was put" and passes the other three, because nothing it holds ever changes. A stub of
this kind is a `Reader` (8.2) answering to the name of a state, and a program tested under it has been tested
for the case where its writes are lost.

**What goes wrong.**

- *The content in a variable.* Section 4.6. And note which of the three handlers is exposed in production:
  with a real database in the place of `Table`, the content is one world whoever forks or retries, whatever
  the order of handlers says. That is right for a database. It means that for `Stored`, the entries below
  that say "the state inside" do not apply, and a transaction (section 7.6) is what stands in for them.
- *Two states.* A program that needs two has one effect and two meanings. On the forty lines the handler
  further in answers every `Get` (section 4.3: a handler is offered every question of its effect), and
  nothing distinguishes the two. A library with rows gives each instance a name or a type. That is said from
  the law and from my own library's page on the state, which says a row holds one state; it is not run here.
- *Its order with a failure and with a choice.* Sections 4.5 and 4.6, used again in 8.4 and 8.5.

## 8.2 Reader

```
effect Reader
  Env -> Text

handler Given(e) : A ! (Reader + R)  =>  A ! R
  done(a)     => done a
  Env, rest   => rest(e)

greeting  =  e <- Env ; done "hello, {e}"

run( greeting under Given("ann") )  =  "hello, ann"
```

In place, and carrying nothing: the cheapest handler there is. It is dependency injection for a value.

The reader has one classic construct beside its operation: run a *part* of the program with the environment
changed. The usual name is `local`. It acts on a stretch, so by chapter 2b it is a handler or a scoped
operation and not a plain one. As a handler it is three lines: around the part, answer `Env` with what `f`
makes of the answer from outside.

```
handler Local(f) : A ! (Reader + R)  =>  A ! (Reader + R)
  done(a)     => done a
  Env, rest   => e <- Env                    -- asked again, of whoever is outside
                 rest(f(e))

both  =  a <- (greeting under Local(upper)) ; b <- greeting ; done (a, b)

run( both under Given("ann") )  =  ("hello, ANN", "hello, ann")
```

Chapter 2b argued that such a thing cannot be an operation because sequencing must not move into it. Here is
that argument as a run. The second greeting, outside the changed part and inside it:

```
run( ((greeting under Local(upper)) then greeting)  under Given("ann") )  =  ("hello, ANN", "hello, ann")
run( ((greeting then greeting) under Local(upper))  under Given("ann") )  =  ("hello, ANN", "hello, ANN")
```

("then" keeps both results.) An algebraic operation satisfies `(Op ; k) then f = Op ; (k then f)`, section
4.1's fourth equation. `Local` does not, and must not.

**What it owes.** Asking twice changes nothing, and asking without using the answer is nothing:

```
x <- Env ; y <- Env ; k(x, y)   =   x <- Env ; k(x, x)
Env ; k                         =   k
```

Both under `Given`, both run. They are the third and fourth equations of a state with `Put` taken away,
which is what a reader is. Under an observer the second is false, as section 4.7 said of every such equation:
a journal has a line or it has not.

**What goes wrong.** Little, which is why it is the first effect people write. The one trap is `local` built
the other way, as an operation `Local(f, body)` that holds its body. Then it has the price section 7.6 found
for `Atomic`: a handler standing between the program and the reader sees one node go by and nothing inside
it. This chapter builds one construct that way, the fork of 8.9, and meets that price there.

## 8.3 Writer

```
effect Writer
  Tell(line) -> Unit

handler Told(lines) : A ! (Writer + R)  =>  (List(Text), A) ! R
  done(a)            => done (lines, a)
  Tell(line), rest   => rest(unit)                               -- carrying lines ++ [line]

handler Counted(n) : A ! (Writer + R)  =>  (Number, A) ! R        -- folded as it is told
  done(a)            => done (n, a)
  Tell(line), rest   => rest(unit)                               -- carrying n + 1

handler Printed(console) : A ! (Writer + R)  =>  A ! R            -- a real console: a shared world
  done(a)            => done a
  Tell(line), rest   => console.print(line)
                        rest(unit)

handler Censored(f) : A ! (Writer + R)  =>  A ! (Writer + R)      -- a part's lines, rewritten on the way out
  done(a)            => done a
  Tell(line), rest   => Tell(f(line))
                        rest(unit)
```

```
steps  =  Tell("parse") ; Tell("check") ; done 42

run( steps under Told([]) )                          =  (["parse", "check"], 42)
run( steps under Counted(0) )                        =  (2, 42)
run( steps under Printed(console) )                  =  42               -- console: parse, check
run( steps under Censored(reverse) under Told([]) )  =  (["esrap", "kcehc"], 42)
```

In place, all four. `Printed` is the first handler of the chapter with a variable, and it is one because a
console is one: what was printed stays printed.

`Censored` is the writer's scoped construct, built as a handler around a part, like `Local`. The other
classic, "listen" (what did this part tell?), is `Told` applied to the part.

**What it owes.** One thing: what a sequence tells is what its parts tell, in order.

```
the lines of (p then q)   =   the lines of p  ++  the lines of q
```

Not owed: any equation that merges two `Tell`s into one. `Told` could not tell `Tell("a") ; Tell("b")` from
`Tell("a b")` if it joined lines with a space; `Counted` can, and `Printed` flushes twice.

**What goes wrong: where the lines of a failed run go.** The log one most wants is the log of the run that
failed, and the order of two handlers decides whether it exists.

```
failing  =  Tell("parse") ; Refuse("bad input") ; Tell("check") ; done 42

run( failing under Refusals under Told([]) )          =  (["parse"], refused("bad input"))
run( failing under Told([]) under Refusals )          =  refused("bad input")              -- and no lines
run( failing under Printed(console) under Refusals )  =  refused("bad input")              -- console: parse
```

The first two are section 4.5 with a log for a cell: collected outside the refusal, the lines outlive it;
collected inside, they were part of the rest and went with it. The third line is the same order as the
second, and the line is on the console all the same. That is section 4.6's finding seen from failure
instead of from choice: a handler with a world behind it is outside every other handler, wherever the line
that runs the program puts it. For a console that is what one wants. The point is that it was not chosen.

## 8.4 Failure

```
effect Failure
  Refuse(why) -> never
```

Dropped. This is the first effect of the catalogue that the data half cannot answer: an interpreter of
commands returns to its caller, and the whole of failure is not returning (section 6.2).

Four things to do with a refusal. Three are handlers, and the fourth is not a handler of this effect at all.

*As a result.* Chapter 2's `Refusals`: the computation ends in `paid(a)` or `refused(why)`.

*As a default.*

```
handler OrElse(default) : A ! (Failure + R)  =>  A ! R
  done(a)             => done a
  Refuse(why), rest   => done default

run( failing under OrElse(0) under Told([]) )  =  (["parse"], 0)
```

*Caught.* Catching acts on a part, so it is `Refusals` applied to the part, and a program for the refusal:

```
recover(p, h)  =  r <- p under Refusals
                  if r is refused(why) then h(why) else done (what r was paid)
```

*Retried.* And here the catalogue's first distinction that is easy to get wrong:

```
retry(n, p)  =  r <- p under Refusals
                if r is refused(why) and n > 1 then retry(n - 1, p) else done r
```

A refusal has no rest to call again: it answers `never`. What is retried is the *part*, handled a second
time, which is possible because the part is a value (section 7.6 retried a transaction's body the same
way). Section 6.5's `TryEach` was the other thing: the rest of the caller, called again with another answer.
The two are confused under the one word "retry", and they need different things of a technique: this one
needs the part as something that can be run twice, which direct style gets by taking a function; that one
needs the rest twice, which half the renderings cannot give.

**State and retry, in two orders.** Section 4.5's law, with a consequence that is easy to miss until it is
an assertion:

```
flaky  =  n <- Get ; Put(n + 1)
          if n < 2 then Refuse("attempt {n} failed")
          done "done at attempt {n}"

run( retry(3, flaky) under Cell(0) )     =  (paid("done at attempt 2"), 3)
run( retry(3, flaky under Cell(0)) )     =  refused("attempt 0 failed")
```

With the cell outside, an attempt sees what the attempts before it wrote, and the third gets through. With
the cell inside the retried part, every attempt starts from the same content and fails the same way. Neither
is a bug of a handler. The second is a transaction that rolls back, retried: correct, and useless for any
program whose progress is in the state that is rolled back. A retry budget, a back-off counter, an
idempotency key must be answered *outside* the retry, and the thing retried *inside*, and one program may
need both.

**What it owes.** `Refuse(why) ; k = Refuse(why)`, which is section 4.6's "not at all". And catching owes two:

```
recover(Refuse(why), h)   =   h(why)
recover(done v, h)        =   done v
```

Both run. Not owed, and run as a failure: that catching guards what comes after it.
`recover(done 1, h) then Refuse("later")` under `Refusals` is `refused("later")`. Catching is not an
operation, for the reason `Local` is not.

**What goes wrong.** A refusal drops the rest *up to its handler*, with everything that rest was carrying:
the lines of 8.3, the cell of section 4.5, the scope of 8.6, every fibre of 8.9. Each of those entries is
this one seen from the other side. Two more, from section 6.4, stand: an exception of the host language is
not a `Refuse` and passes no handler; and a failure the program should be able to survive is not this
effect but section 6.6's, a question with a verdict for an answer.

## 8.5 Choice

```
effect Choice
  Choose(options) -> one of the options

fail  =  Choose([])                          -- a choice among nothing: no path goes on
```

Many times, or in place: the one effect whose handlers differ in which half they need.

```
handler First : A ! (Choice + R)  =>  (some(A) | none) ! R             -- in place
  done(a)                 => done some(a)
  Choose(options), rest   => if options is empty then done none
                             else rest(the first of options)

handler FirstSuccess : A ! (Choice + R)  =>  (some(A) | none) ! R      -- many times, until one path ends
  done(a)                 => done some(a)
  Choose(options), rest   => for each o in options:
                               found <- rest(o)
                               if found is some(_) then done found
                             done none

handler ByDraw : A ! (Choice + R)  =>  A ! (Random + R)                -- in place, by asking chance (8.8)
  done(a)                 => done a
  Choose(options), rest   => i <- Draw(the number of options)
                             rest(options[i])
```

and chapter 2's `AllChoices`, which calls the rest for every option and collects.

```
pair  =  a <- Choose([1, 2, 3, 4]) ; tick
         b <- Choose([1, 2, 3, 4]) ; tick
         if not (a < b and a + b = 6) then fail
         done (a, b)

run( pair under First        under Cell(0) )   =  (none, 2)               -- took 1 and 1; failed for good
run( pair under FirstSuccess under Cell(0) )   =  (some((2, 4)), 10)
run( pair under AllChoices   under Cell(0) )   =  ([(2, 4)], 20)
run( pair under Cell(0) under AllChoices )     =  [((2, 4), 2)]
```

`First` and `FirstSuccess` have the same type and are different handlers. `First` commits: it needs no rest,
any technique has it, and a `fail` further down is final. `FirstSuccess` is backtracking, and it is
backtracking because it holds the rest and can call it again with the next option after the first came back
empty. Eight of the sixteen renderings can write the first and not the second (section 2.4).

The last two lines are section 4.6's law, put to work. One cell outside the search counts what the whole
search did: twenty numbers tried. A cell inside is each path's own: the one path that got through tried
two. "How much work did the search do" and "how long is this solution" are the same program and the same
handlers in two orders.

`ByDraw` turns one effect into another. `split` is chapter 2's program:

```
run( split under ByDraw under Seeded(7) )  =  (10, 2)                    -- one of AllChoices' four
```

**What it owes.** Every handler of a choice: a choice among one is no choice, `Choose([x]) = done x`. Run
for the three. `AllChoices` owes more: the paths of `Choose(a ++ b)` are the paths of `Choose(a)` and then
those of `Choose(b)`. `FirstSuccess` owes that its answer is the first of `AllChoices`' answers; I did not
write that as an assertion beyond the one program above.

**What goes wrong.**

- *What is after the choice runs once for each path.* Section 6.8. Every handler inside the choice is
  resumed with it: 8.6 does that to a scope, 8.8 to a generator of random numbers.
- *Depth first.* `FirstSuccess` goes down the first option until it ends. A program whose first option never
  ends has no answer under it, though one lies down the second. A search that takes turns between branches
  is a different handler, with a queue where this one has the host's stack. I name it and do not build it:
  my library's page on choice has one (a fair "or", from the logic-programming literature it cites), and I
  have not rebuilt it here.
- *The stack.* Each option taken is a nested call on the forty lines.

## 8.6 Resources

```
effect Scope
  Defer(finalizer) -> Unit            -- finalizer is a program: run it when the scope ends

bracket(acquire, release, use)  =  r <- acquire ; Defer(release(r)) ; use(r)

handler Scoped(finalizers) : A ! (Scope + R)  =>  A ! R
  done(a)                  => run each of finalizers, the last deferred first
                              done a
  Defer(finalizer), rest   => rest(unit)                          -- carrying [finalizer] ++ finalizers

file(name, use)  =  bracket( Tell("open {name}") ; done name,   n => Tell("close {n}"),   use )
```

`Defer` is an ordinary algebraic operation, answered in place. Everything else about a resource is in one
line of the handler: the `done` line. **A scope is a handler whose `done` line releases.** So it works
exactly when the rest of the scoped part reaches `done`, once. That is the fifth word of section 8.0, *at
the end*, and it is a promise about every handler outside the scope.

When the promise is kept:

```
nested  =  file("a", a => file("b", b => Tell("use {a} {b}")))

the lines of ( nested under Scoped )  =  [open a, open b, use a b, close b, close a]
```

(Every run of this section is under `Told`, outermost, and shows what was told.)

**The rest dropped.** Section 4.8 said "dropping the rest cleans up" is not a law and that nothing in chapter
4 tested it. Here it is tested, on the forty lines:

```
leaves  =  file("a", a => Tell("use {a}") ; Refuse("no"))

leaves under Refusals under Scoped    :   [open a, use a, close a]    refused(no)
leaves under Scoped under Refusals    :   [open a, use a]             refused(no)      -- never closed
```

Answered inside the scope, a refusal is a value the scoped part ends in, the `done` line runs, the file is
closed. Answered outside, the refusal drops the rest of the scope with its `done` line in it. Nothing
failed. The file is open.

**The rest resumed twice.** And "a `done` line runs once" is not a law either:

```
forks  =  file("a", a => x <- Choose([1, 2]) ; Tell("use {a} for {x}") ; done x)

forks under Scoped under AllChoices   :   [open a, use a for 1, close a, use a for 2, close a]
forks under AllChoices under Scoped   :   [open a, use a for 1, use a for 2, close a]
```

A choice outside the scope resumes the rest of the scope twice. The file was opened once, above the choice;
it is closed at the end of the first path, *used by the second path after it was closed*, and closed again.
The finalizers are carried, not in a variable, and that is exactly why both paths have the list: section
4.6 working as designed, on a thing that exists once. With the choice inside the scope there is one resource
for every branch and one release.

So the rule for a scope written this way: **it must stand outside whatever drops the rest, and outside
whatever repeats it.** The page of my simplest kernel on its resource module says the same in its own
words ("handle errors inside"; "scopes are for programs that resume once"); I read that page and did not run
that module here. Section 9.2 meets the same `done` line doing good: a transaction that writes on it is
rolled back by a rest that is dropped.

**One clause that helps, and its limit.** A scope can watch a question go by. `Refuse` answers `never`, so
whoever handles it will not call the rest; a scope that sees one pass can release first:

```
handler Guarded(finalizers)       -- Scoped, and one more line:
  Refuse(why), rest   => run each of finalizers, the last deferred first
                         Refuse(why)                              -- asked again; nobody will call rest

leaves under Guarded under Refusals   :   [open a, use a, close a]    refused(no)
```

This is sound for an operation whose type says it has no rest, and for no other. A question with an answer
may be resumed tomorrow, or twice, or never, and the scope cannot tell which while it goes by. Section
8.10 has the case that matters most: a consumer that stops reading. It is not a refusal, `Guarded` sees
nothing, and the producer's file stays open.

**What a library adds here**, named and not built. Section 6.8 has the rule and its three cases: a handler
that will not continue must *say* so, and the saying unwinds what it leaves, running the finalizers on the
way; a rest that is kept and never called releases nothing in any library I have read. For a resource
shared by the branches of a search, the page of my library on resources describes counting the holders of
each acquisition, so that it is released when the last branch that can use it is done; I cite the page and
have not rebuilt the mechanism. And cancellation of a fibre (8.9) is a third road by which a scope is left.

**The other way to build it.** `bracket` could be a node that holds its body, as section 7.6's `Atomic` is.
Its handler walks the body, so it sees how the body ended whatever the body did *as a value*, and it has
section 7.6's price: an interceptor between sees the node and not the inside. This file does not build it;
`code/scala/transaction.scala` is that construction.

## 8.7 Time

```
effect Clock
  Now             -> Number
  Sleep(millis)   -> Unit

handler Virtual(t) : A ! (Clock + R)  =>  (A, Number) ! R
  done(a)           => done (a, t)
  Now, rest         => rest(t)                                    -- carrying t
  Sleep(d), rest    => rest(unit)                                 -- carrying t + d

handler Wall : A ! (Clock + R)  =>  A ! R                         -- the machine's clock: a shared world
  done(a)           => done a
  Now, rest         => rest(what the machine's clock says)
  Sleep(d), rest    => park this thread for d
                       rest(unit)
```

and chapter 2's `FixedClock(t)`, which answers `Now` with `t`.

In place, all three. Under `Virtual` a sleep is an addition:

```
patient  =  t <- Now ; Sleep(3600000) ; u <- Now ; done u - t

run( patient under Virtual(0) )  =  (3600000, 3600000)            -- an hour, in no time
```

**What each owes**, and they differ, which is the lesson of this entry.

- `FixedClock`: asking twice changes nothing. It is a reader.
- `Virtual`: a reading after a sleep is the reading before it plus the sleep; and
  `Sleep(a) ; Sleep(b) = Sleep(a + b)`. Run.
- `Wall`: that time does not go backwards. Nothing else. The file asserts that a `Now`, a `Sleep(2)` and a
  `Now` differ by at least 1, and that is all it can assert.

A program that reads the clock twice and relies on the two readings being equal is correct under
`FixedClock` and wrong in production, and no test under `FixedClock` can find it, because the test's handler
satisfies an equation the real one does not. Section 4.7 said a test double that fails an equation of the
real handler is not a double. The converse costs as much: a double that satisfies *more* than the real one
hides bugs. `Virtual` is the better double for anything that waits.

**A deadline.** It acts on a part, so it is a handler around the part. It lets sleeps go by while there is
time left, and cuts short the one that would pass the limit:

```
handler Budget(left) : A ! (Clock + Failure + R)  =>  A ! (Clock + Failure + R)
  done(a)           => done a
  Sleep(d), rest    => if d <= left then
                         Sleep(d)                                 -- asked again, of the clock outside
                         rest(unit)                               -- carrying left - d
                       else
                         Sleep(left)
                         Refuse("out of time")

slow  =  Sleep(40) ; Tell("half way") ; Sleep(40) ; done "finished"

run( slow under Budget(60)  under Refusals under Told([]) under Virtual(0) )
  =  ((["half way"], refused("out of time")), 60)
run( slow under Budget(100) under Refusals under Told([]) under Virtual(0) )
  =  ((["half way"], paid("finished")), 80)
```

The clock stands at 60 when the first run is refused: the test of a timeout took no time and is exact.

**What goes wrong.** A handler acts when a question reaches it, and at no other time.
`done "asked nothing" under Budget(0)` is `paid("asked nothing")`, and a part that computes for a minute
without asking is not cut short by any handler: there is no point at which one runs. Stopping code that
does not ask is pre-emption, and it belongs to the platform, a thread interrupted or a task cancelled. A
library's timeout is built on that, and on cancellation's finalizers (8.6). The short handler above is a
deadline for programs that wait, which is most of what a backend's deadlines are for.

And `Wall`'s `Sleep` parks a thread. That is in place, and it is the wrong handler in a runtime of fibres,
where a sleep is "later": the rest is put aside until a timer fires. I did not build a scheduler that
answers `Sleep`; the backlog has it.

## 8.8 Randomness

```
effect Random
  Draw(bound) -> Number                      -- from 0 up to bound - 1

handler Seeded(s) : A ! (Random + R)  =>  A ! R
  done(a)             => done a
  Draw(bound), rest   => rest(a number below bound, made from s)        -- carrying the next s

handler Taped(tape) : A ! (Random + R)  =>  (List(Number), A) ! (Random + R)
  done(a)             => done (tape, a)
  Draw(bound), rest   => x <- Draw(bound)                               -- asked again, of whoever answers
                         rest(x)                                        -- carrying tape ++ [x]

handler FromTape(tape) : A ! (Random + R)  =>  A ! (Random + R)
  done(a)             => done a
  Draw(bound), rest   => if tape is empty then x <- Draw(bound) ; rest(x)
                         else rest(the first of tape)                   -- carrying the rest of tape
```

In place. `Seeded` is a state whose `Get` moves it on; `Taped` and `FromTape` are chapter 10's journal and
replay for one effect.

```
dice  =  a <- Draw(6) ; b <- Draw(6) ; c <- Draw(6) ; done [a, b, c]

run( dice under Seeded(7) )                =  [2, 5, 3]              -- and [2, 5, 3] again
run( dice under Seeded(8) )                =  [4, 0, 2]
run( dice under Taped([]) under Seeded(7) )  =  ([2, 5, 3], [2, 5, 3])
run( dice under FromTape([2, 5, 3]) )      =  [2, 5, 3]              -- no generator anywhere
```

**What it owes.** No equation between draws. `x <- Draw ; y <- Draw ; k(x, y)` is *not*
`x <- Draw ; k(x, x)`, and the file asserts that the three draws above are not all equal. This is the one
effect of the catalogue whose handlers owe the *absence* of the reader's equation, and a stub that returns a
constant breaks it in the direction no test will notice.

What `Seeded` does owe: the same seed, the same draws. What the pair owes:
`(p under Taped) under FromTape(its tape) = p`, a run reproduced from its recording. That is what turns a
failure seen once into a test, and it needs nothing but the first half.

**Randomness and choice.** Section 4.6 again, and here its two orders are a statistical fact:

```
branchThenDraw  =  side <- Choose(["left", "right"]) ; n <- Draw(1000) ; done (side, n)

run( branchThenDraw under Seeded(7) under AllChoices )  =  [(left, 278), (right, 278)]
run( branchThenDraw under AllChoices under Seeded(7) )  =  [(left, 278), (right, 231)]
```

With the generator inside the choice, each path resumes the generator as it was at the choice, and every
branch draws the same numbers. Nothing is broken: a handler's state is part of the rest. But a simulation
that forks and then samples, written with the generator inside, has perfectly correlated branches, and the
only sign of it is in the results.

## 8.9 Async

Two things go under this name and it helps to take them apart: several programs taking turns, and a program
that waits for an answer from elsewhere without holding a thread. Both are the rest called once, *later*.

### Taking turns: a scheduler is a handler

```
effect Async
  Yield         -> Unit                       -- let the others run
  Fork(body)    -> Unit                       -- start body beside me: an operation that holds a program

handler Scheduler(pick, waiting) : Unit ! (Async + R)  =>  Unit ! R
  done(u)            => go on with the next of waiting, if there is one
  Yield, rest        => put rest with waiting                       -- rest is not called: it is queued
                        go on with the next of waiting
  Fork(body), rest   => put rest with waiting
                        put (body under this handler) with waiting  -- the handler walks the body
                        go on with the next of waiting

go on with the next of waiting  =  i <- pick(the number waiting)
                                   take waiting[i] out, and continue it     -- carrying the others
```

That is a scheduler: a handler that does not call the rest but keeps it in a queue, and a rule for which to
take out. The queue is carried like any other state. `pick` is the whole policy, and since it is a program,
it can ask:

```
roundRobin   =  n => done 0                   -- whoever has waited longest
newestFirst  =  n => done n - 1               -- whoever came last
byDraw       =  n => Draw(n)                  -- whoever answers chance decides
```

```
worker(name)  =  Tell("{name} 1") ; Yield ; Tell("{name} 2")
twoWorkers    =  Fork(worker("a")) ; Fork(worker("b"))

the lines of ( twoWorkers under Scheduler(roundRobin) )                  =  [a 1, b 1, a 2, b 2]
the lines of ( twoWorkers under Scheduler(newestFirst) )                 =  [a 1, a 2, b 1, b 2]
the lines of ( twoWorkers under Scheduler(byDraw) under Seeded(2) )      =  [a 1, b 1, b 2, a 2]
```

One program, three schedulers, three interleavings, and the third is a function of a seed. **A fibre is a
program on a scheduler, and the scheduler can be exchanged**: nothing in `worker` knows which it has.

**What a scheduler owes.** That every fibre runs to its end, and that each fibre's own steps are in its own
order. The file asserts both of the three runs above. Not owed: the interleaving. A program correct under
one `pick` and wrong under another has a race, and here a race can be shown.

**Async and state.** A cell outside the scheduler is shared by the fibres:

```
increment      =  x <- Get ; Yield ; Put(x + 1)
twoIncrements  =  Fork(increment) ; Fork(increment)

run( twoIncrements under Scheduler(roundRobin)  under Cell(0) )              ends with the cell at 1
run( twoIncrements under Scheduler(newestFirst) under Cell(0) )              ends with the cell at 2
run( twoIncrements under Scheduler(byDraw) under Seeded(1) under Cell(0) )   ends with the cell at 1
run( twoIncrements under Scheduler(byDraw) under Seeded(3) under Cell(0) )   ends with the cell at 2
```

The lost update, as an assertion that gives the same answer on every run. Under the drawing scheduler the
seed decides, so a seed is a bug report that reproduces: chapter 10's replay, for concurrency. Nothing here
uses a thread.

**The finding of this section: the body of a fork is not where it was written.** Put the cell between the
forking code and the scheduler:

```
run( twoIncrements under Cell(0) under Scheduler(roundRobin) )      -- fails: nobody answers Get
```

The two answers one would guess are a cell shared by the two fibres, and a cell each. It is neither: the
run fails. `Fork(body)` is a question, and it travels outward past `Cell` to the scheduler with `body`
inside it as a value. The scheduler walks the body, and the scheduler stands outside the cell. So the body's
`Get` is asked from outside the cell, and nobody answers it. `Fork` holds a program, so it is one of chapter
2b's scoped operations, and this is the second half of section 7.6's question about them. There: who can
see into the block? An interceptor between cannot. Here: under whose handlers does the block run? Not those
between.

On the forty lines this is a failure at run time, on the first `Get` of the first fibre. With rows it would
be a type: `Fork`'s signature has to say which effects a body may ask, and they can only be those of the
scheduler's own outside. I did not build that on stage 4, whose rows would need an effect that mentions the
row it sits in; the backlog has it. And I argue, without having run it, that the same holds in direct style
for a different reason: a scheduler there starts a new fibre from its own handler, so the handlers installed
between the code that forked and the scheduler are not on the new fibre's stack.

What does work, and is run: a handler *inside* the body is that fibre's own.
`Fork(increment under Cell(0))` twice runs to the end, each fibre with a cell nobody else sees.

**Async and failure.** The same rule from the side of 8.4. `crashing` forks a fibre that tells `a 1` and
refuses, and a `worker("b")` beside it:

```
crashing under Scheduler(roundRobin) under Refusals     :   [a 1]    refused(a broke)
```

A refusal answered outside the scheduler drops the rest up to there, and the queue of waiting fibres is part
of that rest. One fibre failed and all of them are gone; `b` never ran. With the refusal answered inside the
body of the fibre that may fail:

```
the lines of ( supervised under Scheduler(roundRobin) )  =  [a 1, a ended: a broke, b 1, b 2]
```

Supervision is where `Refusals` stands.

### Waiting for an answer: the program returns

```
effect Waiting
  Await(register) -> X          -- register is handed a callback; whoever calls it with an x has answered

start(p, whenDone)  =
  if p is done(a) then whenDone(a)
  if p is Await(register) ; rest then
    register( x => start(rest(x), whenDone) )                    -- and return
```

`start` runs a program as far as its first `Await`, hands the rest of the program to `register` as the
callback, and **returns**. No thread is held. Whoever calls the callback lends its thread for the next
stretch, up to the next `Await`. `start` is not a handler in the sense of this book: it stands where `run`
stands, and it comes back before the program has ended.

With an event loop, which is one thread and a list of things to do later (a real shared world, and so a
variable in the file):

```
sum  =  a <- loop.soon(20) ; b <- loop.soon(22) ; done a + b

start(sum, r => result := r)        -- has returned; result is not set; the rest of sum is in the loop's list
loop.turn()                         -- the first answer: the program is now at its second Await
loop.turn()                         -- result = 42
```

This is how one of my notes describes fibres in my own library (note 04): execution is one
loop over the tree; at an asynchronous operation the loop registers a callback and returns, holding no
thread; the callback re-enters the loop with the rest of the program. And the sentence of that note that
this book is placed to explain: capturing the continuation costs nothing there, because it is already in
the tree. Chapter 5 said a runtime of fibres is the second half with the scheduler holding the rest. Where
the program is data, the rest was never anywhere else, and a fibre is not a kind of thing the runtime has.
It is a program, a callback, and somebody's thread. The note lists what the library runs the same program
on: a pool of workers of its own, virtual threads, a plain thread pool, the event loop on JavaScript. I
report that from the note and ran none of it here. Its last point needs no run: a program that asks nothing
of this effect never meets any of it. On the forty lines that is plain to see, since such a program is run
by `run`, and there is no scheduler anywhere for it to pay for.

**Blocking is a capability.** To give a program's value back to a caller that wants it *now*, something has
to wait for the last callback, and waiting parks a thread. That is possible on a platform with threads to
park and impossible on a single event loop, where parking the one thread stops the loop that would have
called back. So it is a permission, and a type can carry it. This listing stays in Scala, because its point
is the `using`:

```scala
sealed trait CanBlock

def await[A](p: Prog[A])(using CanBlock): A =
  val result = java.util.concurrent.CompletableFuture[A]()
  start(p)(a => result.complete(a))
  result.get()
```

`elsewhere(x)` is an `Await` that another thread answers.

```
sum  =  a <- elsewhere(20) ; b <- elsewhere(22) ; done a + b

await(sum)                               -- does not compile: there is no CanBlock here
await(sum)  =  42                        -- where the evidence of a platform with threads was imported
```

The file asks its own compiler about the first line while it is being compiled, as chapter 4's law files do,
and asserts that the line is refused and that the message names `CanBlock`. The note says that in my library
this evidence does not exist on the JavaScript platform, so that a blocking join there is a compile error
and the same program is driven through the event loop instead. What is run here is the miniature: one JVM,
the evidence in scope or not. No build for a browser is under `code/`.

**What is not here.** The two halves of this section do not meet in the file: there is no `Await` inside a
scheduled fibre, no joining a fibre for its result, no cancelling one, no sleeping one. A library is where
they meet, and 8.12 lists what that takes. What the section claims is the shape: a scheduler is a handler
with a queue of rests, an asynchronous wait is a rest given away as a callback, and neither needed anything
the forty lines do not have. Every technique of Part III can do both, since both call the rest once; I say
that from section 6.3's requirement and have run it on the tree only.

## 8.10 Streams

```
effect Stream
  Emit(item) -> Unit
```

In place, later, or dropped: three handlers, three lines of the table. Section 6.3 built two of them, a
handler that collects and one that hands out each item with the rest of the producer, and turned a producer
that pushes into something a consumer pulls from. This entry adds what a catalogue owes beyond that: stages,
stopping, and pace.

**A stage is an interceptor.** It answers `Emit` by asking `Emit` again, of whoever is further out:

```
handler Mapped(f) : A ! (Stream + R)  =>  A ! (Stream + R)
  done(a)             => done a
  Emit(item), rest    => Emit(f(item))
                         rest(unit)

handler Taking(left) : A ! (Stream + R)  =>  Unit ! (Stream + R)
  done(a)             => done unit
  Emit(item), rest    => if left <= 0 then done unit
                         else if left = 1 then
                           Emit(item)
                           done unit                              -- rest is not called
                         else
                           Emit(item)
                           rest(unit)                             -- carrying left - 1

endless(n)  =  Tell("made {n}") ; Emit("{n}") ; endless(n + 1)

run( endless(1) under Mapped(add "!") under Taking(2) under Collected under Told([]) )
  =  (["made 1", "made 2"], ["1!", "2!"])
```

An endless producer, and two steps of it ran. A pipeline is a stack of handlers, and the stage that stops
does so by dropping the rest of everything inside it. (The middle case of `Taking` is there for the reason
section 6.3 gave for its `take`: a stage that resumes the producer and *then* notices it wanted no more has
made a third item.)

**Back-pressure is when the handler resumes.**

```
handler Each(consume) : A ! (Stream + R)  =>  A ! R
  done(a)             => done a
  Emit(item), rest    => consume(item)                            -- the consumer first
                         rest(unit)                               -- then the producer

handler Batched(size, consume, buffer) : A ! (Stream + R)  =>  A ! R
  done(a)             => if buffer is not empty then consume(buffer)
                         done a
  Emit(item), rest    => if there is room then rest(unit)         -- carrying buffer ++ [item]
                         else
                           consume(buffer ++ [item])
                           rest(unit)                             -- carrying []
```

`made(3)` tells `made 1` before it emits `1`, and so on; `used` is a consumer that tells what it was given.

```
made(3) under Collected, and then used on each   :   [made 1, made 2, made 3, used 1, used 2, used 3]
made(3) under Each(used)                         :   [made 1, used 1, made 2, used 2, made 3, used 3]
made(3) under Batched(2, used, [])               :   [made 1, made 2, used 1+2, made 3, used 3]
```

The first is a buffer without a bound: the producer ran to its end before the consumer saw anything. The
second never has more than one item outstanding. The third lets the producer run ahead by two. There is no
signal from consumer to producer in any of them, no credit and no queue with a high-water mark. The producer
cannot outrun the consumer because between two items it is not running. It is a value in the handler's hand,
and the handler calls it when it is ready.

**What a stage owes.** That it commutes with collecting: the items of `p under Mapped(f)` are `f` of the
items of `p`. And `Taking(n)`: the first `n` items, and the producer made `n`. Both run.

**Streams and resources: the consumer that stops.** The case 8.6 promised; the assertions are in block 6
of the file, and each run is under `Collected` and `Told`.

```
file("a", _ => made(3)) under Taking(1) under Scoped    :   [open a, made 1, close a]
file("a", _ => made(3)) under Scoped under Taking(1)    :   [open a, made 1]             -- never closed
file("a", _ => made(3)) under Guarded under Taking(1)   :   [open a, made 1]             -- nor here
```

The second line is the natural way to write a producer: it opens its own file, so the scope is inside it,
and the consumer is outside. The consumer took one line and stopped. To the producer's scope nothing
happened at all: it is waiting at an `Emit` for an answer that will not come. No refusal went by, so the
guard of 8.6 has nothing to see. This is an old problem and not one of this technique. Python's generators
meet it, and the language's remedy is the one section 6.8 gives, a way to say "I will not continue": a
generator object there has a `close` method that raises an exception at the suspended `yield`, so that the
blocks around it unwind. In a library of handlers the same is the consumer's duty: the
value that section 6.3's `Pulled` hands out needs, beside "the next item", a "no more" that the scope acts
on. On the forty lines there is no such thing, and the first line is the only safe arrangement.

**Not built.** A consumer that is itself a program, asking `Next` for an item, and a handler that runs a
producer and a consumer against each other by holding the rest of each in turn; merging two producers;
items in chunks. All are handlers of the shapes above.

## 8.11 The traps, in one place

**Six constructs of this chapter act on a stretch of a program.** None is an operation in chapter 2b's
sense, and each was built one of that chapter's two ways:

| construct | built here as | what that costs |
| --- | --- | --- |
| a changed environment, `Local` (8.2) | a handler around the part | nothing found |
| rewriting what a part tells, `Censored` (8.3) | a handler around the part | nothing found |
| catching, retrying (8.4) | `Refusals` around the part | what the part carried is lost or kept by position |
| a scope, `Scoped` (8.6) | a handler around the part | released only if the part reaches `done`, once |
| a deadline, `Budget` (8.7) | a handler around the part | sees only what the part asks |
| a fork, `Fork(body)` (8.9) | a node that holds the body | the body runs outside every handler between |

Five of the six are handlers around a part, and that is the reading I would start from: a handler around a
part is an ordinary handler, every law of chapter 4 applies to it, and what it does in combination with
others follows from where it stands. Its cost is that it *is* its position. A scope behaves well or leaks
according to what stands outside it, and its author cannot see that from inside.

The sixth is a node holding a program, and it could not have been a handler: a fork does not delimit its
body, it sends it somewhere else. It has both prices of that construction. Section 7.6 found one, that an
interceptor between does not see inside. Section 8.9 found the other, that handlers between do not apply
inside.

One more price is real and not shown, because the forty lines do not have it. Applying a handler in the
middle of a program is free where there are no rows. Where there are, the handler must be able to take its
effect from wherever it stands in the row and leave the others in order, which is the work of stage 4's
renamed places (section 3i.5). The files of this chapter would need that for `Local`, `Censored`, `Budget`
and the stages of 8.10, each of which asks its own effect again.

**The pairs that matter.** Each row is run, in the block of the section named. "Inside" is the handler
applied first, nearer the program.

| pair | the first inside the second | the first outside the second | section |
| --- | --- | --- | --- |
| state, failure | the content goes with the refusal | the content survives it | 4.5 |
| state, retry | every attempt from the same content | attempts see earlier ones | 8.4 |
| writer, failure | the lines go with the refusal | the lines survive | 8.3 |
| state, choice | each path's own | one, for the whole search | 4.6, 8.5 |
| randomness, choice | every branch draws the same | each branch its own draws | 8.8 |
| scope, failure | not released | released | 8.6 |
| scope, choice | released twice, used after release | released once | 8.6 |
| scope, a consumer that stops | not released | released | 8.10 |
| state, scheduler | not reached by a forked body | shared; `pick` decides the result | 8.9 |
| failure, scheduler | answered in a body: ends that fibre | ends every fibre | 8.9 |

Nine of the ten are one sentence. A handler that drops, repeats or postpones the rest does it to everything
inside it and to nothing outside it. Inside, a carried state is dropped, repeated or postponed with the
rest. That is the right thing for a content, a log or a generator and the wrong thing for a file. The ninth
row is the exception, for the reason given in 8.9.

A handler with a world behind it — `Printed`, `Wall`, a real database under `Stored` — is not in this table.
It is outside every handler, whichever column the line that runs the program puts it in (8.3).

## 8.12 What a library adds to these short handlers

The handlers of this chapter are each the smallest that shows its effect. Here is what stands between them
and a library, named and not built.

- **A constant stack.** On the forty lines a program of 1,000 ticks under `Cell` runs and one of 100,000
  overflows the stack; the file asserts both. Stage 4 of chapter 3i runs that length under a handler that
  resumes (its main `forced`, section 3i.5). A scheduler, a stream and a search are all long programs.
- **Answers in place that allocate nothing.** Six of the ten effects never need the rest, and here each
  still builds a closure for every question. Stage 4's `answer` and its handler with a state are the two
  forms a library gives them; chapter 13 has what the difference costs in my own.
- **Rows.** So that `Stored` is known to leave `Store` to be answered; so that two states can be told
  apart; so that a forked body's questions are checked against what the scheduler can pass on.
- **Finalizers that run when a scope is left by any road a handler declares**: an abort, an unwinding, a
  cancellation; and an account of resources shared by the branches of a search (8.6, section 6.8).
- **For async**: fairness when a fibre does not yield; pre-emption or its substitute; joining and
  cancelling; timers, so that a sleep is a rest put aside; `Await` inside a scheduled fibre; work spread
  over several threads, with all that a shared queue then needs.
- **For streams**: a way for a consumer to say it has stopped; chunks; merging; a consumer as a program.
- **For choice**: a search that is fair between branches; a cut.

None of these changes what an effect *is*. The operations, the half each needs and the equations each owes
are the same in a library; the list above is engineering, and the first two items are most of the work.

## 8.13 The catalogue

What each is, and what its handlers do with the rest:

| effect | operations | the handler and the rest | usual handlers |
| --- | --- | --- | --- |
| state | `Get`, `Put` | in place | in memory; in a store; journalled |
| reader | `Env` | in place | given; changed around a part |
| writer | `Tell` | in place | collected; folded; sent to a sink; rewritten around a part |
| failure | `Refuse` | dropped | as a result; as a default; caught; the part retried |
| choice | `Choose` | in place, or many times | the first; all; backtracking; by chance |
| resources | `Defer` | in place; the scope acts at the end | a scope; a scope that watches refusals |
| time | `Now`, `Sleep` | in place | fixed; virtual; real; a deadline around a part |
| randomness | `Draw` | in place | seeded; recorded; replayed |
| async | `Yield`, `Fork`; `Await` | later | a scheduler with its `pick`; callbacks, with or without blocking |
| streams | `Emit` | in place, later, or dropped | collected; pulled (6.3); stages; a consumer at each item; a buffer |

What each owes, and what goes wrong:

| effect | its handlers owe | not owed | what goes wrong |
| --- | --- | --- | --- |
| state | the four equations of 4.7 | — | a stub; a variable (4.6); a shared store |
| reader | asking twice changes nothing | — | a changed part built as a node |
| writer | the lines of a sequence, in order | merging two lines | lines lost with a refusal, or never unsaid |
| failure | nothing after it happens (4.6) | that a catch guards what follows | it takes all it stands outside of |
| choice | a choice among one is none | that first is backtracking | what follows runs once a path |
| resources | each finalizer once, the last first | release if the rest is dropped | a rest dropped, or repeated |
| time | by handler; the real one, only forward | two readings equal | a double that owes too much |
| randomness | the same seed, the same draws | any equation between draws | every branch the same draws |
| async | each fibre ends, in its own order | the interleaving | a forked body; a shared state |
| streams | a stage commutes with collecting | that a stopped producer is told | a producer's scope left open |

Read down the third column of the first table: the first half of the idea would have been enough for six
rows. Read down the last column of the second: in eight rows what goes wrong is about the rest of the
program all the same, because a handler that only answers still stands inside or outside one that does not.

## What was run

Everything in the listings is an assertion of `code/scala/catalogue.scala`, main `book.catalogue`, under
`sh code/run-all.sh` (`holds    scala: chapter 8, the catalogue of standard effects`). The file is on
`code/scala/kernel.scala` and uses six names from `examples.scala` (`Refuse`, `Refusals`, `Now`,
`FixedClock`, `Choose`, `AllChoices`) and two from `continuation.scala` (`Emit`, `Collected`). Its own
definitions are in an object, `std`, because the package already has other chapters' `take` and `Journal`.
It has no cast and no `@unchecked`.

| section | what is asserted |
| --- | --- |
| 8.1 | three results; `stateOwes` for the three handlers and for the stub |
| 8.2 | `Given`, `Local`; the two programs that differ; the two equations |
| 8.3 | four handlers; the lines of a sequence; the three orders with a refusal |
| 8.4 | a default; the two laws of catching; the retry in two orders; what follows a catch |
| 8.5 | `pair` under four stacks; `ByDraw`; one option is none; options concatenate |
| 8.6 | the nested scope; a refusal inside, outside, guarded; a choice outside and inside |
| 8.7 | fixed, virtual, real; two sleeps are one; the deadline twice; a part that asks nothing |
| 8.8 | two seeds; the tape and its replay; a tape that runs out; the generator on each side of a choice |
| 8.9, turns | three schedulers and what each owes; the lost update, four times; the run that fails |
| 8.9, turns | a state inside a body; a refusal answered outside the scheduler, and inside a body |
| 8.9, waiting | callbacks, turn by turn; `await` refused by the compiler, and allowed |
| 8.10 | the pipeline over an endless producer; the three paces; a stage commutes; the three scopes |
| 8.12 | 1,000 ticks run; 100,000 overflow |

The numbers in 8.5 and 8.8 (`(10, 2)`, `[2, 5, 3]`, `[4, 0, 2]`, 278 and 231) and the interleaving and the
cells under `Seeded` in 8.9 are what this file's generator gives for those seeds, asserted as such. They are
the same on every run and mean nothing beyond it.

**What rests on one technique.** All of it: this chapter has one rendering, the Scala tree. Which sections
another technique could follow is read from what each needs, as in chapter 6: everything "in place",
"dropped" and "later" needs what every technique has; `AllChoices`, `FirstSuccess`, and every row of
8.11's table with a choice in it need the rest twice. `retry` needs the part as a value. I did not write a
second rendering of any of it.

**What is argued and not run.** That on the forty lines the inner of two states answers every question
(8.1; from section 4.3). That the body of a fork escapes the handlers between in direct style too (8.9).
That rows would make the failing run of 8.9 a compile error (8.9; not built on stage 4). That a part which
asks nothing cannot be cut short by a handler (8.7; the assertion is the trivial case). That every
technique can write the scheduler and the callbacks (8.9). The cost of applying a handler in the middle of a
row (8.11).

**What is reported from my own documents and not run here.** What my library's fibres are and where they
run, and that blocking is a compile error on its JavaScript platform (note 04, a note whose
publication status I do not know). That my simplest kernel's scope drops its finalizers when an error is
handled outside it and is meant for programs that resume once (its resource module's page). That my larger
library counts the holders of a resource across the branches of a search, and has a fair search (its pages
on resources and on choice). One statement about someone else's language: that a Python generator has a
`close` which raises at the suspended `yield` (8.10), from that language's documentation as I remember it;
the backlog has it to check.

**What is not in the catalogue.** My libraries' own lists are longer: a value that may be absent, errors
that accumulate, a supply of fresh identifiers, a sub-program run at most once, weighted choice with
inference as a handler. Each is one of the shapes above with another name, and I left them out for that
reason and not because they are unimportant; `PLAN.md` asks the author whether any should come in. Chapter
9 takes three effects that are not standard at all, a screen, a server and a store, and builds a system on
them.
