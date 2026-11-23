# 1b · Five programs that are hard to write

Chapter 1 was about what a function hides. This chapter comes at the same subject from the other side: five
ordinary requirements that most languages will not let you write in a straight line.

None of them is exotic. Each is something a working team has been asked for. Each has an obvious first attempt,
and each first attempt fails — not through any fault of its author, but because the language has no way to say
what was meant. So each has a standard replacement, built by competent people, and each replacement leaves one
particular bug open that the replacement itself cannot rule out.

The code in this chapter is illustrative pseudocode, in no particular language. Much of it is the code somebody
writes first, which is to say code that does not work. Nothing here is a solution; those begin in chapter 2.

## One · Leave early, with an answer, from deep inside

You are validating an invoice. It has lines; lines have allocations; allocations name a cost centre. The rule:
if any cost centre is closed, the whole invoice is rejected, with that centre's name.

```
check(invoice):
  for line in invoice.lines:
    for alloc in line.allocations:
      if closed(alloc.centre):
        ???                        // the answer must get out of here
```

Whatever replaces `???` has to travel from the middle of two nested loops out to the caller, past code that has
no opinion about cost centres and should never have heard of them.

**Why it does not work.** A function can return a value to its immediate caller. It cannot say "abandon
everything between here and that point up there, and let the answer be this".

**What people do.** Three things.

*Throw an exception.* It is the closest thing most languages have, and it works. But the signature of `check`
does not mention it, nothing makes the caller handle it, and the `catch` will also catch what you did not mean
— a null dereference three layers down arrives at the same place as your business rule.

*Return a sentinel and thread it.* `check` returns "maybe a rejection", and so does every function between the
loops and the caller, because each must look at what the one below returned and decide whether to stop.
Functions whose subject is the structure of an invoice now contain the word "rejected". Add a second way to fail
next quarter and each of them is edited again.

*Use a combinator.* `find`, `exists`, a traversal that short-circuits. For this example one fits, and you should
use it. It stops fitting when the walk is a recursive descent over a tree with four kinds of node, or when the
exit must happen inside a callback you do not own, or when two different things can end the walk with different
values.

**The bug left open: the layer that forgets.** One function in the chain receives the failure and does not pass
it on. It logs it, or maps it to a default, or matches only the success case because when it was written there
was only one. The types are satisfied; every function returns the right shape. The program simply continues past
a decision that should have ended it, and each function looks reasonable when reviewed alone.

## Two · A producer that pushes, read as something that pulls

You have a parser. It works by callback: you hand it a function, it hands you tokens.

```
parse(document, onToken)           // calls onToken(t) for every token, then returns
```

Now you need the first ten tokens. Or the tokens up to one that means "stop". Or to read two documents in step,
one token from each.

**Why it does not work.** The producer holds the loop. You hold a function that is called from inside it. You
take what you are given, when it is given; you cannot say "enough", and you cannot say "wait". Your own state
has to live in variables outside the callback, because the callback returns after every token.

**What people do.**

*Collect everything first.* Put every token in a list, then work with the list. Correct, and it reads the whole
document to answer a question about its beginning. On a small input nobody notices. On a two-gigabyte log it is
an outage, and it becomes one on the day the input grew.

*Run the producer on another thread, with a queue between.* This is the industrial answer and it is widely
used. It turns a question about tokens into a question about concurrency: a bounded queue, a termination signal,
a shutdown path, a rule for moving an exception from the producer's thread to the consumer's, and tests that
depend on timing. Where the producer really is concurrent — it reads a socket and should run ahead of you — a
thread and a queue are the design. Here nothing is concurrent; the thread is being used as a way to pause.

**The bug left open: the path that never ends.** The consumer stops early, the producer blocks for ever on a
full queue, and a thread leaks. Or the producer fails and the consumer waits for an item that will not come.

## Three · Stop in the middle, and continue tomorrow

A purchase over ten thousand needs a director's approval. The business logic is four lines.

```
order = place(request)
ok    = approval(order)            // a person, some time this week
if ok: ship(order)
else:  refund(order)
```

**Why it does not work.** The second line waits for a person — not milliseconds, days. The process will be
restarted for a deploy before the answer arrives, and whatever was holding those four lines in memory will be
gone. A running function cannot be put down and picked up by a different process.

**What people do.** Almost always the same thing: the program is turned inside out into a state machine, with
its position kept as a row in a table.

```
states:  Placed, AwaitingApproval, Approved, Rejected, Shipped, Refunded

onEvent(state, event):
  (Placed, Submitted)             -> AwaitingApproval
  (AwaitingApproval, Approved)    -> Approved
  (Approved, Shipped)             -> Shipped
  ...                              // one line for every pair that can occur
```

This is a standard, teachable, honest design, and a good team writes it well. Where the states themselves are
the requirement — a payment lifecycle that a regulation enumerates — it is the right model. But look at what it
did to the four lines.

- The business rule is no longer in one place. "If approved, ship; otherwise refund" is now two transitions and
  a diagram on a wiki.
- The line that was the requirement — wait for a director — has disappeared. In its place is machinery about
  *how* waiting is done.
- Two things must agree for ever: the code and the stored states. Deploy a version that renames a state and
  every run sitting in it is stranded. Nothing fails at build time.

The other standard answer is to adopt a workflow engine. The good ones are good: retries, timers, visibility, an
operations story that somebody else maintains. The price is a new service in the path of your most important
work, and code that takes the engine's shape. Many such engines resume a workflow by running it again from the
beginning and feeding back the recorded results, which obliges workflow code never to read a clock, a random
number or a service directly. In most of them that is a documented rule; the compiler does not know it. Chapter
11 is about this choice.

**The bug left open: the transition nobody wrote.** A state and an event that can occur together, and no case
for the pair. At nine states and seven events there are sixty-three pairs, most of them impossible, and the ones
you decided were impossible are where this bug lives. It shows up as a run that quietly never finishes.

## Four · Do something on the way back

The smallest of the five, and the one most often written subtly wrong. Every request should be timed, and the
timing recorded with the outcome, including when the outcome is a failure.

```
handle(request):
  started  = now()
  response = route(request)
  record(now() - started, response.status)     // does not run if route throws
  return response
```

So you reach for `finally`:

```
try:     return route(request)
finally: record(now() - started, ???)          // and what is the status here?
```

**Why it does not work.** `finally` runs on every path and cannot see the result. A line after the call sees
the result and runs on one path only. "On the way back out, with whatever is coming back" is split across two
constructs.

**What people do.** Write it twice, once on the success path and once on the failure path.

**The bug left open: the third exit.** Six months later somebody adds an early return or a new `catch` and
updates one of the two copies. Some requests are now timed and some are not. Nobody notices, because the tests
are not about the instrumentation.

## Five · A decision that is not yours to make

An importer is reading a file of fifty thousand rows. At row forty thousand it meets one that will not parse.
What should it do?

- A nightly batch wants to skip the row, count it, and go on.
- An interactive tool wants to ask the operator.
- A compliance run wants to stop at once, because a partial import is worse than none.
- A migration wants to put in a default and mark the row.

The importer cannot know which. It is a library; the policy belongs to whoever called it. And the decision must
be taken *at row forty thousand, with the file still open*, because the remaining ten thousand rows are the
reason anybody is running it.

```
load(file, strict)                              // the first attempt: a flag
```

**Why it does not work.** The knowledge is in the caller and the moment is in the callee. A call can pass
knowledge down in advance, as parameters; it cannot pass a question up and wait for the answer while the callee
stays standing.

**What people do.**

*Flags and policy objects.* `strict`, then a callback, then the callback needs the line number, then somebody
wants "skip the rest of this file but keep what you have". The library accumulates the vocabulary of every
caller it ever had.

*Throw, and let the caller catch.* By the time the `catch` runs, the importer's frame is gone: the open file,
the position, the ability to carry on. The caller can decide what to do *instead of* the import, which is not
the question it was asked.

*A callback that returns an instruction* — skip, use this default, abort. This is the closest anyone gets by
hand, and it works. The set of instructions is now a small protocol between two pieces of code that never see
each other; the importer must implement every one, and a new one changes both sides.

**The bug left open: the policy that cannot be said.** A caller needs a combination the parameters do not
express, and either the library grows a fifth parameter or the caller pre-processes the file to get around the
library. It arrives as a feature request, once a quarter, for years.

## What the five have in common

Each replacement above encodes a decision about control flow as data — a sentinel, a queue, a state table, a
duplicated line, a flag — and nothing then checks that the data and the intention still agree. That is the cost.
It is small on the first day and it compounds in exactly the systems that live long and have several authors.

Now read the five again as a group. The same noun is missing from each.

| the program | what it needed to get hold of |
| --- | --- |
| leave early | the rest of the walk, to drop it |
| a pushing producer | the rest of the producer, to make it wait |
| continue tomorrow | the rest of the program, to keep it |
| on the way back | the rest of the caller, to stand around it |
| someone else's decision | the rest of the import, to hand it out and get it back |

Every programmer reasons easily about what a function returns. There is a second half to every call that is just
as real and has no name in most languages: what the caller will do with the result. You cannot refer to it,
store it, drop it or run it twice. All five programs needed to.

Chapter 1 arrived at the same place from its own direction. A program that can only ask stops at each question,
and between the question and its answer the rest of the program is a thing that someone holds. Whoever holds it
may decline to continue it (one), continue it when they are ready for the next item (two), keep it until
tomorrow (three), continue it and look at what comes back (four), or take a decision and hand it back (five).

So the five are one mechanism used five ways. Chapter 2 states the mechanism — a program as a value that asks,
a handler that is given the rest — and chapter 3 builds it in forty lines. Chapter 6 then takes these shapes one
at a time, as code that runs. Chapter 11 is the third program at full length.

The two opening chapters have each found one half of what chapter 2b will put together. Chapter 1 wanted the
reach to be a *question, as data*, so that it could be listed, seen and refused. This chapter wanted *the rest
of the program* in somebody's hand. Either can be had without the other, and chapter 5 is a tour of the tools
that have one of them.

One caution to carry forward. That the replacements leave bugs open does not make them mistakes; I said where
each is the right choice, and for one or two layers, one call site, two exits, it usually is. The argument is
about what happens when a system contains several of these at once, each worked around separately.
