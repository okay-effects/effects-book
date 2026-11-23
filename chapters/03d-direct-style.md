# 3d · Direct style on a stack the runtime keeps

The fourth family turns the first one inside out. The program is not a data structure that an interpreter
walks. It is ordinary code that *runs*, and stops where it asks. The pair of chapter 2 — a question, and the
rest — still exists, but nobody built it: the question is what the code handed over when it stopped, and the
rest is the stopped code itself, kept by the language's runtime.

Four renderings run here: generators in `code/typescript/bank.ts` and `code/python/bank.py`, a virtual thread
in `code/java/Loom.java`, and a goroutine in `code/go/bank.go`. All four print lines 1 to 5 and, for line 6,
the reason they cannot. A fifth, OCaml 5, belongs to this family by its runtime and has the handler in the
language; it is in chapter 3f.

## Generators

A generator function is a function that can stop at `yield`, hand a value out, and be resumed with a value
sent in. That is asking.

```ts
function* pay(from: string, to: string, amount: number): Prog<string> {
  const have: number = yield balance(from);
  if (have < amount) yield refuse(`${from} has ${have}, needs ${amount}`);
  yield move(from, to, amount);
  const at: number = yield now;
  return `moved ${amount} from ${from} to ${to} at ${at}`;
}
```

The operations are plain objects. Calling `pay("ann", "bob", 30)` runs nothing and returns a generator object;
`next(answer)` runs it to its next `yield`. A handler drives it, and is itself a generator, so that a question
it does not answer is yielded on to whoever is outside:

```ts
function* handle<A, B>(h: Handler<A, B>, body: Prog<A>): Prog<B> {
  function* resume(answer?: unknown): Prog<B> {
    const step = body.next(answer);
    if (step.done) return h.done(step.value);
    let used = false; // the body has moved on after one resumption: a second would continue from elsewhere
    const rest = (x?: unknown) => {
      if (used) throw new Error("resumed twice: a generator's rest is not a copy");
      used = true;
      return resume(x);
    };
    const mine = h.on(step.value, rest);
    if (mine) return yield* mine;
    return yield* rest(yield step.value); // not mine: ask it outward, go on with the answer
  }
  try {
    return yield* resume();
  } finally {
    body.return(undefined as A); // if the rest was dropped, the body's `finally` blocks run now
  }
}
```

That is the whole mechanism, and it is the same shape as chapter 3's `handle`: offer the question to the
handler with the rest; if it is not the handler's, pass it out with the handled rest behind it. The Python file
is the same function in Python.

### What generators give

**The program is the language.** `if`, `for`, `while`, `try`/`finally`, early `return`, local variables — all
of it is available inside `pay`, because `pay` is a function. Nothing needs `flatMap`.

**No tree, no closure per step.** A step that asks costs a `yield`. A step that does not ask costs nothing.

**Leaving early is built in.** A generator can be closed: `body.return()` in JavaScript, `body.close()` in
Python. The generator's pending `finally` blocks run. `handle` above closes the body in its own `finally`, so a
handler that does not call `rest` unwinds the program it dropped. This is axis (f) answered by the host.

**The program still cannot go around.** A generator's only channel to its driver is `yield`. An interceptor
that wraps `handle` around it sees every question, as with a tree. (The body can, as in any language, call a
library directly instead of asking; no technique in this part prevents that, and chapter 10 is about catching
it.)

### What generators cost

**One resumption.** The rest of the program is the generator object, and it is mutable: `next` advances *it*.
After `rest(100)` has run the body to its end there is nothing left for `rest(10)` to continue. Neither
JavaScript nor Python offers a way to copy a generator object. `bank.ts` makes the attempt with a handler that
calls `rest` twice, and `handle` refuses it; that is where line 6 comes from. The honest type for such a
program says so. In my TypeScript library a program written as a generator has a different type from one built
as data, and a handler that resumes many times accepts only the second: "a generator given to it is a type
error" (`okay-ts/README.md`).

**The colour.** A function that asks must be a generator function, and its caller must delegate to it with
`yield*`, and so must *its* caller. The mark runs up the call graph exactly as `async` does.

**The types of answers.** A TypeScript generator has one type for everything sent into it, whatever was
yielded. So `const have: number = yield balance(from)` is annotated by hand in the rendering, and the compiler
would accept `string` there. The known way round is to make each operation a tiny generator of its own and ask
with `yield*`, whose result type is that generator's return type; libraries built on generators do this.

**Depth.** The rendering above nests one `yield*` per question, so a program of many steps grows the chain. A
library drives the body from a loop. It is the same debt as the tree's recursion, and paid the same way.

## Threads and virtual threads

Where a language has cheap threads, the same thing can be done with no marking of functions at all. The body
runs on its own thread; asking is a blocking call that hands the question to the thread that handles and waits
for the answer.

```java
static String pay(String from, String to, long amount) {
  long have = ask(new Balance(from));
  if (have < amount) ask(new Refuse(from + " has " + have + ", needs " + amount));
  ask(new Move(from, to, amount));
  long at = ask(new Now());
  return "moved " + amount + " from " + from + " to " + to + " at " + at;
}
```

This is a Java method like any other. `ask` is a static method. There is no `function*`, no `suspend`, no
`async`: a method that asks has the same signature as one that does not, and can be called from a lambda, a
stream, a library callback. `code/java/Loom.java` implements `ask` with two hand-over queues and a scoped
value that tells the body which run it belongs to; the rest of the program is the body's thread, parked.

### What threads give

**No colour.** This is the entire reason to prefer a thread to a generator, and it is a large one: existing
code can ask without being rewritten, and so can code called through libraries that know nothing of effects.

**The host's debugger and stack traces**, complete, because the stack is a real stack.

**Leaving early by unwinding.** When a handler drops the rest, `Loom.java` answers the parked `ask` with a
signal instead of a value; `ask` throws; the body's `finally` blocks run; the thread ends. One of my libraries
does the same on real continuations and on plain threads: "`Abandoned` is thrown at the `perform` the rest
waits in, so `finally` blocks run" (`okay-direct/docs/design.md`). Note what this implies for user code: a
`catch (Throwable t)` in the body that swallows the signal defeats it.

### What threads cost

**One resumption.** A parked thread can be unparked once. Of the JDK's own continuation my library's notes
say: "Loom's continuations cannot be copied; a second resumption throws" (`okay-direct/docs/
implementations.md`).

**A hand-over for an answer that needs none.** In the rendering every question crosses two queues, even
`Balance`, whose answer was already in memory. A serious implementation answers in place on the body's own
stack and switches only when a handler really takes the rest. On my libraries, with JMH (two forks of five
one-second iterations, JDK 25, the median of three runs): a state loop answered in place cost 5.2 ns per
operation in direct style on Loom against 15.9 ns on a machine over a tree — and a handler that takes the rest
and resumes it once cost 110 ns on Loom against 20.1 ns on the machine (`okay-java/resumable/README.md`,
"Measured"). The first number is why one wants direct style. The second is what a stack switch costs when it
does happen.

**In these two renderings, a handler that only answers.** This is a limit of `code/java/Loom.java` and
`code/go/bank.go` as written, not of threads, and it should be stated exactly. Their handlers have the shape
"given a question and a way to ask the handlers outside, return the answer, or leave by throwing". The handler
runs on the handling thread while the body is parked, and when it returns, the loop hands the answer over. So
a handler there can answer, refuse, ask something else instead, and see an answer that came from outside on
its way back to the body (the journal does). What it is never handed is the rest: it has no clause for "the
body finished, with this", so it cannot continue the body and then act on how the body *ended* — chapter 6's
compensation (6.4) — and it cannot put the body aside to be continued by someone else later (6.3, 11.1),
though the parked thread is sitting there and a fuller library would give it to the handler as an object with
one `resume`. Generators do not have this limit in `code/`: `handle` in `bank.ts` passes the handler a `rest`
it can call and get the outcome from. The table in section 6.8 has all sixteen side by side.

**The platform's rules about when a thread may stop.** On the JVM a native frame between the handler and the
question pins the continuation. My notes also record a crash of HotSpot 25 when a continuation is resumed
while the thread holds a monitor taken outside it (`okay-direct/docs/implementations.md`). The public API is
threads; the continuation underneath is internal to the JDK and needs flags to reach.

## Buying the second resumption back

Direct style is one-shot because the rest is a stack the runtime owns. Three ways exist to get more, and each
is a real technique with a real price.

**Copy the frames.** If the runtime lets frames be read and written, the rest can be duplicated before it is
resumed. In Kotlin a suspended function's continuation is an object with fields; one of my libraries copies it
by reflection on the JVM and by copying the object in JavaScript, and cannot on native code, which "gives no
way to read a frame's fields" (`okay-kotlin/docs/design.md`). Its own benchmark, one JMH run on a laptop:
every Pythagorean triple to 30 by copying frames took 4 752 µs, and the same search with the program as data
on a machine took 287 µs (`okay-kotlin/README.md`, "Measured"). Copying works. It is not the cheap road.

**A runtime whose continuations can be serialised.** Espresso, the JVM written on Truffle, has continuations
that can be serialised, hence copied (`okay-direct/docs/implementations.md`). It means running on that VM.

**Rewrite the code.** Transform each method that may stop so that it can save its frame as data and re-enter
it. That is no longer this chapter; it is the next one.

What is shared by the first two, and easy to miss: a copied frame copies the local variables and *shares the
objects they refer to*. Two resumptions of a body that appends to a list it created before the question both
append to one list.

## Who builds this way

- **Generators.** In TypeScript, Effect offers a generator syntax for its programs (`Effect.gen`, with
  `yield*`). `redux-saga` is the older and widely used case: a saga is a generator that yields descriptions of
  effects, and middleware interprets them — which is this chapter exactly, used for testable side effects in
  user interfaces long before the phrase "algebraic effects" reached that community.
- **Virtual threads and fibres.** On the JVM, Ox is a Scala library for direct-style concurrency on virtual
  threads. Go's goroutines and channels are the same substrate, built into the language: `code/go/bank.go` is
  `Loom.java` with two channels for the two queues, `panic` and `recover` for the unwinding, and the run passed
  to the body as a parameter, since Go has no implicit ones. Its answers are `any` and are asserted where they
  are used (`r.Ask(Balance{from}).(int64)`): Go has no way to tie an answer's type to its question.
- **Coroutines given by the language** — OCaml 5's fibres, and the effect languages — are this family with the
  handler construct built in. They have a chapter of their own, 3f.
- **Mine**: a Java library with three substrates behind one interface (the JDK's continuation, plain threads,
  Espresso), and the generator half of the TypeScript port.

## On the axes

| axis | generators | threads, virtual threads |
| --- | --- | --- |
| b. the rest | the generator object | a parked thread or continuation |
| c. finding the handler | whoever drives the generator; outward by re-yielding | a handler stack held by the run |
| d. the types | the host's, weak on answers | the host's |
| e. resumptions | one | one |
| f. leaving early | close the generator: `finally` runs | throw at the parked call: `finally` runs |
| g. storing the rest | no | no (Espresso excepted) |
| h. cost | a `yield` per question; nothing otherwise | nothing in place; a switch when the rest is taken |
| i. the host | coloured functions; good traces | no colour; the host's own traces; pinning rules |

**Choose it when** one resumption is enough — which is most systems — and the programs should read as the
language reads. Prefer threads to generators where the platform has cheap ones and existing code must be able
to ask. **Do not choose it when** a handler must explore alternatives, or when the rest must outlive the
process.
