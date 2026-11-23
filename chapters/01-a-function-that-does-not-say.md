# 1 · A function that does not say what it does

## One line of code

Here is a function, in no particular language.

```
receipt = pay(from, to, amount)
```

Its signature says it takes two accounts and an amount and returns a receipt. Now some questions that anyone
responsible for this line will be asked sooner or later.

- Does it read a database? Which one?
- Does it call another company's service? What happens if that service is slow?
- Does it read the clock? If I run it twice, do I get the same receipt?
- Does it write a log line? An audit record? Does it send an email?
- Does it start a thread, open a file, read an environment variable?
- Does it do anything else?

The signature answers none of them. To find out, you read the body, and the bodies of everything it calls, and
the libraries those call. In a codebase of ordinary size nobody has done that. The honest answer to "does it do
anything else" is: we believe not.

This is the starting point of the whole book. **A function's type describes what goes in and what comes out,
and is silent about what the function reaches for on the way.** Everything that follows is a consequence of
taking that silence seriously.

## The reaches are where the trouble is

Consider what a typical defect report says. It is rarely "the sum was computed wrongly". It is:

- the test passed, but the test did not use the real clock;
- it worked until the provider started answering in three seconds;
- we cannot reproduce it, because we do not know what the database returned at that moment;
- the retry sent the payment twice;
- a library we did not know we depended on opened a connection at start-up.

Each of these is about a reach: a moment when the code went outside itself. The pure part of a program — the
arithmetic, the decisions made from values already in hand — is the part that is easy to test, easy to read, and
almost never the cause.

So the hard part of software is concentrated in a small number of moments, and those are exactly the moments the
type does not mention.

## What we do about it today

Engineers are not naive about this. There is a set of practices, each of which patches the silence from a
different side.

**Pass the dependency in.** Instead of `pay` reaching for a global database, give it a `Accounts` interface as an
argument. Now the signature mentions accounts. This is dependency injection, and it is a real improvement.
But it lists what the function was *given*, not what it *does*: nothing stops the body from also calling
`System.currentTimeMillis()` or opening a socket. And every function between the entry point and `pay` must now
carry the argument, which is why injection frameworks exist.

**Replace the dependency in tests.** With an interface you can pass a fake. With a mocking library you can
replace even what was not passed. Now the test controls the reach — one test at a time, by listing in advance the
calls it expects. The fake and the real implementation drift apart, and the drift is found in production.

**Wrap the call.** Put logging, timing, retry and authorisation around the method: a decorator, an aspect, a
middleware. This works for the calls you remembered to wrap and for the layers that have a wrapping point.

**Watch from outside.** Tracing agents, network policies, sandboxes. They see what actually happened, which is
valuable, and they see it after the code is written, at a level where a "transfer" has become bytes on a socket.

**Agree to be careful.** Code review, architecture rules, a wiki page that says the domain layer must not import
the HTTP client.

None of these is foolish, and a good team uses most of them. Notice what they have in common. Each one treats a
reach as an *action the code performs*, and then tries to get around the action: put something in front of it,
replace what it lands on, observe its traces. The action itself stays where it was — a call, in the middle of a
function, that just happens.

## The other possibility

Suppose the function could not act at all. Suppose the only thing it could do, when it needs a balance, is
produce a value that says "I need the balance of this account" — and stop, until someone hands it an answer.

Then several things become true at once, and not by discipline.

*What it does is what it asks.* The list of questions a function can ask is its complete description. If
"what time is it" is not among them, it does not read the clock; there is no call for it to make.

*Whoever answers decides.* The same function runs against a real database, an in-memory map, a recording of last
Tuesday, or another process, depending only on who is answering. The function does not change and is not told.

*Every question passes one point.* If something stands between the function and the answerer, it sees each
question and each answer — all of them, including the ones added next year by someone who never heard of it.

*A question can go unanswered.* Then the thing simply does not happen. Not "is blocked", not "raises an
exception from inside the driver": the program stopped at the question and was not continued.

*The stop is a real place.* Between the question and its answer, the rest of the program is sitting there as a
thing you can hold. You can keep it until a person approves. You can keep it until tomorrow. You can continue it
twice with two different answers and compare.

Those five paragraphs are the five questions of the preface, answered. The price is one change of view: a reach
stops being an action and becomes a **question, as data**. The thing that answers is called a **handler**. The
pair is what the literature calls an *effect*.

## Is this not just an interface?

It is the natural objection, and chapter 5 takes it in full. The short version: an interface is the first half.
When you call a method of an interface, the method runs and returns; you have named the reach, and that is good.
What you have not got is the *stop*. The call went into the implementation and came back; nothing stood at the
boundary holding the rest of your program in its hand.

Most of what is new in this book comes from that held remainder. It is why a handler can refuse, retry, ask
twice, wait a week, or run every alternative — and why a wrapper around a method cannot.

## A promise that must be checked

There is an honest objection to all of this, and it is better met now than in chapter 10.

If I write the type of `pay` as "a receipt, asking accounts and payments", that type lists the questions the
function *declares*. On most platforms nothing stops its body from opening a socket as well. Java lets you. Rust
has `unsafe`. Haskell has `unsafePerformIO`. A type cannot prevent what the language permits behind its back.

So the claim worth making is narrower than "the type lists everything this code may do". It is this: **all
access to the outside world sits in a small, listed set of handlers, and every build checks that nothing else
reaches past them.**

That check can be built. On the JVM, for example, one can read the compiled bytecode of every business module and
its third-party jars and fail the build if any of it refers to the network, the file system, SQL, processes,
threads, the system clock, randomness, reflection, class loading or native code. Handlers are not failed; they
are listed, by provider.

The first time I ran such a check over a repository I thought I knew — 24 modules, 53 inputs, 21 seconds — it
found 232 real reaches. Among them: a clock read and a random source inside a module I had thought of as pure
data, and a messaging client's jars reaching native code. Three idioms of the compiler had to be carved out, and
the report prints, in its header, on every run, what it chose not to count.

I mention the number because it is the point of this chapter in miniature. I had written that code. I had a rule
that said the module was pure. The rule was not enough, and I did not know until something *looked*. A function
that does not say what it does cannot be trusted by reading its type, and — it turns out — not by its author's
memory either.

## What the rest of the book does with this

Chapter 2 states the idea exactly, in a notation that belongs to no language: operation, program, handler, the
rest of the program, the row. Chapter 2b says what those amount to — operations as data, and the rest of the
program as a value held by whoever answers — and what the word *algebraic* means. Chapter 3 builds it once, in
forty lines, and runs the examples. Part III lays out the other ways to build it, in thirteen languages, and
how to choose among them. Chapter 5 is the map: everything else engineers use for these problems, placed on
those two halves. Chapter 6 is what the second half can do that nothing without it can. Parts V and VI carry
it up from one function to a system: effects as the architecture, and effects as the means of control. Part
VII is about the newest writer and the newest actor in our systems — the AI agent — and why this shape suits
both of its roles. Part VIII is the bill, Part IX the history, and the last chapter is the picture again.
