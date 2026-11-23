# 7 · The data model is the effects

So far effects have been a way to write one function well. This chapter makes the larger claim: they are a way
to *structure a system*. The rule I build by fits in one sentence.

> **The data model is the effects; every implementation is a handler; the application runs in one line.**

The rest of the chapter says what each part means, what it removes from a codebase, and what it costs.

Chapter 6 was the second half of the idea, the rest of the program. This chapter and chapter 10 are the first:
**the operations are data**. Everything claimed below follows from a system's reaches being a signature — a
list of named questions that exist as values — and very little of it needs the rest of the program at all.
Sections 7.1 to 7.5 would stand on chapter 5's command objects. Section 7.6 is where the two halves meet again,
and where the word *algebraic* of chapter 2b earns its place in a design review.

The listings are in the notation of section 2.0. The one-line applications of 7.3 and 7.4 show a shape and are
not listings from `code/`; the same shape, for the bank, is the first line every one of the sixteen renderings
runs. Section 7.6 is checked by `code/scala/transaction.scala`. Chapter 9 runs such lines for one small
application, three of them (section 9.4), and builds the screen and the two platforms of 7.5 in miniature.

## 7.1 Start from what is asked

The usual first question of a design is "what are the components?" — services, repositories, clients, queues —
and then "how do they call each other?". The result is a graph of things that hold references to other things.

The first question here is different: **what does the business logic need to say?**

For the bank it was `Balance`, `Move`, `Now`, `Refuse`. For a shop that takes bookings it is something like

```
Slots.free(service, day)       →  the free times
Bookings.reserve(client, slot) →  a reference
Bookings.cancel(reference)     →  done
Mail.send(to, letter)          →  done
Clock.now                      →  the time
Ids.fresh                      →  an identifier
```

That list is the architecture. Not a diagram of it — the thing itself, as types the compiler checks. It says
what the system is *about*, in the system's own words, and it says nothing about PostgreSQL, HTTP, Cloudflare or
SMTP.

Two tests of a good vocabulary:

- **A domain expert could read it.** "Reserve a slot for a client" is their sentence. `executeQuery` is not.
- **It is small.** Business logic asks for few distinct things. If the list is long, operations are leaking the
  implementation ("begin transaction", "set header") and belong inside a handler.

A bad vocabulary is the most expensive mistake available in this style, and the hardest to see early. If the
operations are `Sql.query(text)` and `Http.post(url, body)`, you have named the *transport*, every program still
knows about tables and URLs, and a handler can see that "a query happened" but not that money moved. The effect
must be at the level of the meaning.

## 7.2 Everything that touches the world is a handler

An implementation is a handler: it knows about the driver, the connection pool, the retry policy, the provider's
quirks. And **only** handlers know. That is a rule with teeth:

- Real resources — clients, connections, keys, files, sockets — are created by the host at the entry point and
  live inside handlers. Programs receive plain data, effects and identifiers.
- No ambient effects in business code. The clock, randomness, environment variables, threads and global mutable
  state are effects like any other. `Clock.now` is a question; `System.currentTimeMillis()` is a reach.
- A handler for one effect may be written *in terms of other effects*: the handler of `Bookings` asks `Store`
  and `Clock`. So handlers form layers, each speaking a lower vocabulary, down to the few that touch the platform.

What you get is a codebase with two kinds of code and a visible line between them. Above the line: programs,
which are pure descriptions and can be read, tested and reasoned about with no infrastructure. Below it:
handlers, which are few, listed, and the only place a reviewer needs to look for what the system does to the
world.

## 7.3 One line

At the entry point — `main`, a request handler, a test — the program meets its handlers:

```
run( program under BookingsOnStore under Postgres(pool) under Smtp(config) under SystemClock under Refusals )
```

Read from the left, as in chapter 2: the program; bookings answered in terms of a store; the store answered by
a database; mail, the clock and refusals answered. This line is the composition root, the dependency
injection configuration, the environment selection and the test set-up of a conventional system, in one
expression of ordinary code. In a rendering where applying a handler is a function call it reads from the
inside out instead (`handle(h)(p)` in chapter 3), and in direct style it is a nest of blocks; it is one
expression in all of them.

Three properties are worth stating.

**It is checked.** If `program` asks an effect that the line does not handle, the row is not empty and `run`
does not compile. There is no start-up failure because a bean was missing.

**It is the only place that decides.** Which database, which mail provider, whether time is real — all here. To
know how production differs from the test, compare two lines.

**Run once.** A program never calls `run` inside itself. To give part of a program its own handler, the handler
is applied to that part — which yields a program with a smaller row, still a value, still not run. This keeps
the picture simple: descriptions all the way down, one execution at the edge.

## 7.4 Tests swap handlers

```
run( program under BookingsOnStore under InMemoryStore under Recording(outbox) under FixedClock(t0) under Refusals )
```

The test is the production line with three handlers changed. There are no mocks, no patching, no container
started in a test profile. The in-memory store is a real implementation of the `Store` vocabulary — small,
written once, used by every test in the project.

Consequences that I have come to value more than I expected:

- Tests run the **real program**: the same value production runs, not a copy assembled with test wiring.
- They are **fast and deterministic** because time, identifiers and randomness are answered by the test.
- They do not break when the code is refactored, because they assert on results and on what was asked of the
  outside — not on the order of internal calls.
- A failing production case can become a test by **replaying its journal** (chapter 10), with no reconstruction
  of state by hand.

## 7.5 One program, several worlds

Because the program does not know who answers, the same program runs wherever its effects can be answered. I use
this constantly, and three cases show the range.

**Two platforms.** A backend written once runs on Cloudflare's Workers (JavaScript, durable objects, their
key-value store) and on a plain JVM host. The effects are "a stored value", "an actor addressed by key", "a
letter sent", "an HTTP request made". On one platform their handlers call Cloudflare's bindings; on the other, a
JDK HTTP server and files. The business program is one source file compiled twice. The cloud is the main
platform and the JVM host is the fallback — and it is a fallback I can actually fall back to, because it runs
the same end-to-end tests.

**Screens as data.** A screen of an application is a program over an effect whose operations are "show this
form", "wait for the person's choice". One handler draws it in a browser; another on a phone with native
controls; a third is a test that plays a person — presses buttons by their labels and asserts on what is shown —
against the real program with an in-memory backend. The screen's logic exists once.

**Two languages.** If operations are data, they can cross a wire. A program in one language can have an effect
answered by a handler in another process written in another language: the question is serialised, the answer
comes back, the program continues. The reference kernels I maintain in Scala, Java and Rust are tested for
exactly this — every host of one language with every worker of another — and for giving the same digest on a
thousand random programs, so that "the same model" is a test and not a hope.

None of these required designing for portability. It is what a program that only asks *is*.

## 7.6 Structure that is in the data: a transaction

Chapter 5 said that dependency injection decides who provides and leaves open how a call is run, and that the
usual patch, a proxy and an annotation, has a road around it. The transaction is where that shows most, so it
is the worked case of this chapter. Every listing below is checked by `code/scala/transaction.scala`, on the
tree of chapter 3: `scala-cli run code/scala --main-class book.transaction` prints `transaction examples hold`.
The results are that file's assertions, on connections it names `c1` to `c5`. No other rendering runs this
section, and that matters more here than elsewhere; the end of the section says why.

**What the conventional design does well.** One annotation on a method, and the container begins a transaction
before the call and commits or rolls back after it. The business code has no `begin` and no `commit` in it.
Whether to join the transaction in force, start a new one or take a savepoint is an attribute of the annotation.
This is mature, it works with the databases and transaction managers a team is likely to meet, and for most
methods of most services it is all anybody needs.

**Where it ends.** "The current transaction" is kept beside the code, usually per thread, and found by
intercepting calls. A call that is not intercepted has no bracket: the self-call of chapter 5 is the famous
case. I would put the cause one level below the proxy. A sequence of calls has no brackets in it. `begin` and
`commit` are two calls that somebody must pair, and the annotation asks a framework to pair them around a
method. The pairing itself is nowhere in the program.

So put it in the program. A bracket is an operation that *holds its body*:

```
effect Transaction
  Atomic(body)     -> what body ends in         -- an operation that holds a program
  Rollback(why)    -> never
  Insert(row)      -> Unit
```

`Atomic(body)` is a question like any other — do this as one unit, and give me its result — whose argument
happens to be a program. Three things follow from the shape alone. There is no commit operation, so there is none
to forget: the bracket closes where the body ends. Brackets cannot be mismatched, because a tree cannot be
unbalanced. And `Rollback` answers `never`, as `Refuse` did: nothing written after it runs.

"A question like any other" needs the qualification chapter 2b prepared. `Atomic` is *not* an algebraic
operation: what follows it cannot be moved inside it without changing what is in the transaction. It is a
scoped operation, a node that holds a sub-program, and the last part of this section is the price of that.

```
charge(order, funds, price) : Unit ! Transaction =
  Atomic(
    if funds < price then Rollback("no funds for {order}")
    else Insert("payment {order} {price}") )

place(order, funds, price) : Text ! Transaction =
  Atomic(
    Insert("order {order}")
    charge(order, funds, price)
    done order )
```

`charge` is written on its own and does not know whether it will be called inside `place`. In the value that
`place` builds, the bracket of `charge` is inside the bracket of `place`, whoever called it and by whatever
route. The structure is in the data, not in the call stack.

The handler owns the connection and one bit — open or not — and walks the tree:

```
handler Transactions(c) : A ! (Transaction + R)  =>  (committed(A) | rolledBack(Text)) ! R
  done(a)                 => done committed(a)
  Insert(row), rest       => c.insert(row)                         -- pending, while a transaction is open
                             rest(unit)
  Rollback(why), rest     => done rolledBack(why)                  -- rest is not called
  Atomic(body), rest      => if c.open then                        -- a bracket inside a bracket: walk on
                               r <- body under Transactions(c)
                               if r is committed(x) then rest(x) else done r
                             else
                               c.begin()
                               r <- body under Transactions(c)     -- the body is a value: it is walked here
                               if r is rolledBack(why) then
                                 c.rollback()
                                 done r
                               else if c.commit() then rest(x)     -- r is committed(x)
                               else walk the same body again, from c.begin()
```

The first bracket it meets: begin, walk the body, commit. A bracket inside a bracket: walk on. A rollback
anywhere inside: the rest of the body is dropped, as chapter 2's refusal dropped the rest of `pay`, and the
first bracket rolls back. In the file the two results are `Right(a)` and `Left(why)`, the connection writes
what it was told into `said` and what it kept into `rows`, and the last line is a local function `walk` that
calls itself.

```
run( place("o-1", funds: 100, price: 30) under Transactions(c1) )  =  committed("o-1")
  -- c1.said = ["BEGIN", "INSERT order o-1", "INSERT payment o-1 30", "COMMIT"]

run( charge("o-2", funds: 100, price: 30) under Transactions(c2) )  =  committed(unit)
  -- c2.said = ["BEGIN", "INSERT payment o-2 30", "COMMIT"]

run( place("o-3", funds: 10, price: 30) under Transactions(c3) )  =  rolledBack("no funds for o-3")
  -- c3.said = ["BEGIN", "INSERT order o-3", "ROLLBACK"]
```

Two brackets in the code, one begin and one commit: for the handler the whole transaction is one node with one
subtree. `charge` run alone is its own transaction, with no change to it. And a rollback raised in the inner
function took the order row with it.

**The body is a value.** The handler was given the body, not a call that has already happened. So when the
database refuses a commit, it can walk the same body again:

```
c4 = a connection that refuses its first commit         -- in the file: Connection(conflicts = 1)

run( place("o-4", funds: 100, price: 30) under Transactions(c4) )  =  committed("o-4")
  -- c4.said = ["BEGIN", "INSERT order o-4", "INSERT payment o-4 30", "ROLLBACK",
  --            "BEGIN", "INSERT order o-4", "INSERT payment o-4 30", "COMMIT"]
  -- c4.rows = ["order o-4", "payment o-4 30"]
```

`place` has no retry in it, and no second annotation whose order relative to the first must be right. The rule
of chapter 6 applies in full: what is in the body runs once per walk. The inserts appear twice in what the
connection was told and once in what it kept, and that is safe only because a rollback undoes them. A body that
also sent a letter would send two.

**The attributes become decisions of the handler.** Joining the transaction in force is the walk above. An
independent transaction is a second handler, on a second connection, applied to the inner body. A nested one is
a savepoint taken at the inner bracket. Those two are the design and nothing more: the forty lines run only the
first, and so does my own transaction library, where a bracket inside a bracket joins the outer one and neither
an independent transaction nor a savepoint is built. My JDBC handler goes the other way and refuses a nested
transaction at run time rather than flatten it without saying.

Three limits, plainly.

- The connection here is three vectors in memory. Nothing in this section is evidence about a real database.
- The bracket guarantees the pairing, not isolation. What the database does between begin and commit is still
  the database's business.
- An exception of the host language thrown through the interpreter is not a `Rollback` and is not seen. Chapter
  3 said the forty lines have no finalizer; a library must roll back on every way out that chapter 6 lists.

And a fourth, which the file asserts. An operation that holds a program hides that program from whoever stands
between:

```
handler Watching(saw) : A ! (Transaction + R)  =>  A ! (Transaction + R)       -- answers nothing
  done(a)                    => done a
  op of Transaction, rest    => saw := saw ++ [the name of op]
                                pass op on, unanswered

run( place("o-5", funds: 100, price: 30) under Watching(saw) under Transactions(c5) )  =  committed("o-5")
  -- saw = ["Atomic"]
```

The interceptor saw the bracket go by and none of the inserts: the body is handed whole to the handler of the
bracket. An interceptor that must see inside has to be applied by whoever walks the body. It is a known difficulty
of operations that hold programs, and forty lines do nothing about it. Neither does my real kernel, as far as I
can tell by reading it: the body is a field of the operation and is run by the bracket's own handler, outside
the interceptor's reach. No test of mine puts an interceptor in front of a bracket, so that is read from the
code and not measured. The consequence for chapter 10 is practical: an audit that must see every write goes
*inside* the transaction handler, or the vocabulary keeps writes out of opaque bodies.

This is the place where the technique changes the answer, and where a neutral notation must not pretend
otherwise. Chapter 2b gave the two ways to have a construct with a scope, and they behave differently here.

- *A scoped operation*, as above: the body is a field of a node. An interceptor sees the node and is blind to
  the body, on every technique in which the body is a value handed over whole. That is what the file asserts,
  for the tree.
- *A handler applied to a part.* In direct style a bracket is usually a block: a function `atomic { … }` that
  installs the transaction handler around its body and runs it. Then there is no `Atomic` node at all. The
  inserts are ordinary operations that travel outward from where they are asked, and they meet the transaction
  handler first, because it is the innermost. An interceptor placed *outside* the block sees an insert only if
  the transaction handler passes it on; one placed *inside* the block sees every insert. So the rule "the audit
  goes inside the transaction handler" survives the change of technique, for a different reason.

The second bullet is argued from how handlers nest and is not run here: no rendering under `code/` but the
Scala tree has a transaction. What stands on the evidence this section has — run, for the forty lines; read
from the code, for my kernel — is the general point: a bracket's body is opaque to an interceptor standing
between the program and the bracket's handler, and a design must decide on which side of the bracket its audit
stands. Section 8.9 meets the other price of an operation that holds a program, with a fork: the body runs
under the handlers of whoever walks it, and those that stand between do not apply to it.

What the section claims is narrow: a transaction need not be a per-thread variable that every call is hoped to
pass. It can be a node in a tree, and then the handler that owns the connection walks the tree.

## 7.7 What a team stops building

Seen from the side of a team's backlog, this style removes work.

| conventionally built | here |
| --- | --- |
| a dependency-injection container and its configuration | the one line |
| mock objects and a mocking library | in-memory handlers, written once |
| test profiles and environment switches | a different line |
| decorators for logging, metrics, retries, per service | one interceptor per concern, for all operations of an effect |
| transaction annotations and the proxies behind them | a bracket operation; the handler that owns the connection |
| an audit trail maintained by convention | a journal handler (chapter 10) |
| an inventory of external dependencies, kept in a wiki | the list of handlers, generated at each build |
| a workflow engine for "wait for approval" | a handler that keeps the rest of the program (chapter 11) |
| a sandbox for untrusted or generated code | a row with nothing dangerous in it |

Each item on the left is a real system with its own model. On the right they are uses of the same two things.
The saving is not only the code: it is that there is one idea to teach a new engineer instead of eight.

## 7.8 What it costs

This is the architecture chapter, so the costs here are architectural. The rest are in Part VIII.

**The vocabulary must be designed.** With a container you can start coding and wire later. Here the first act is
naming what is asked, and a wrong vocabulary costs more to change than a wrong class.

**It is unfamiliar.** A new team member has likely seen dependency injection and not this. The idea is small —
chapter 3 is forty lines — but "the function returns a description and nothing has happened yet" takes a few
days to become natural.

**The boundary needs a guard.** The rule "only handlers touch the world" is not enforced by most languages. It
has to be checked by a tool at build time, or it erodes. Chapter 10.

**Stack traces and debuggers show the machine.** When something fails inside a program, the call stack is the
kernel's loop, not your functions. The answer is that the journal is a better debugger than the stack — it shows
what was asked and answered — but it is a different habit.

**Not everything should be an effect.** Parsing, arithmetic, formatting, anything pure: plain functions. An
effect for something used once inside one handler is ceremony. Chapter 14 is about where to stop.

## 7.9 The sentence again

The data model is the effects: name what is asked. Every implementation is a handler: only handlers touch the
world. The application runs in one line: all decisions about the world are in one visible, checked place.

When this holds, the question that opened the book — what does this code actually do? — is answered by reading
two things: the vocabulary, and the line.

Both are the first half of chapter 2b made into a way of working. The vocabulary is the signature. The line is
the choice of folds. The second half was needed once, in 7.6, and for little: a rollback is a rest that is not
continued. Even the retry after a refused commit is the first half: the body is a program, a program is a
value, and a value can be folded twice.
