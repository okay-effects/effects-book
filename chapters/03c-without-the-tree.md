# 3c · Without the tree: final encodings and capabilities

The second and third families share one decision: there is no data structure for the program. The program is
ordinary code that is *given* its handlers — as type-class instances for an abstract monad, or as values in
scope. Both run here: `code/haskell/Tagless.hs` and `code/scala/capabilities.scala`.

They are taken together because they are the same move at two strengths, and because between them they are
what most working codebases already do.

## Tagless final

An effect is a type class over an abstract monad `m`; the program is polymorphic in `m`:

```haskell
class Monad m => Bank m where
  balance :: String -> m Integer
  move    :: String -> String -> Integer -> m ()

class Monad m => Clock m   where now    :: m Integer
class Monad m => Failure m where refuse :: String -> m a

pay :: (Bank m, Clock m, Failure m) => String -> String -> Integer -> m String
pay from to amount = do
  have <- balance from
  when (have < amount) (refuse (from ++ " has " ++ show have ++ ", needs " ++ show amount))
  move from to amount
  at <- now
  pure ("moved " ++ show amount ++ " from " ++ from ++ " to " ++ to ++ " at " ++ show at)
```

A handler is an instance: a choice of `m` and of what each method does in it.

### What it gives

**The row, checked, for nothing.** `(Bank m, Clock m, Failure m)` is a set of effects, unordered, inferred if
you leave the signature out. Run `pay` in a monad with no `Clock` instance and it does not compile. Chapter 2's
row is here without any library.

**No interpreter.** Once `m` is chosen the program is ordinary code in `m`. The compiler can specialise and
inline it; there is no tree to allocate and no loop to dispatch through. For code that must be generic and fast
this is the reason to choose it.

**Many resumptions, if the monad has them.** The rest of the program is not a value anyone holds; it is
whatever `m`'s bind does with the code that follows. Choose a monad whose bind runs the rest once per element,
and the rest runs twice:

```haskell
type WhatIf = ExceptT String []

instance Bank WhatIf where
  balance _  = lift [100, 10]        -- two answers: the rest of `pay` runs for each
  move _ _ _ = pure ()
```

`Tagless.hs` prints line 6 with these lines, two one-line instances for the clock and for failure, and no
change to `pay`. This is worth dwelling on, because the
usual summary — "tagless final has no continuation" — is not quite right. It has exactly the continuation its
monad has. With `IO` or a state monad that is one resumption. With the list monad it is many. With a
continuation monad it is all of chapter 2.

### What it costs

**The order of effects is back in the type.** The test's monad in `Tagless.hs` is

```haskell
type Test = ExceptT String (State World)
```

failure *above* state, so that balances and the journal survive a refusal. Swap the two and a refusal takes
the state with it: by the type alone, there are then no balances to print on line 2 of the scenario and no
journal on line 5. With handlers over a tree that order was chosen
where the program was run, per run. Here it is chosen once, in a type, for every program that runs in `Test`.

**An interceptor is a monad that wraps a monad, and must be taught every other effect.** The journal:

```haskell
newtype Journaled m a = Journaled {journaled :: m a}
  deriving (Functor, Applicative, Monad, Clock, Failure)

instance (Bank m, Trail m) => Bank (Journaled m) where
  balance account = Journaled $ do
    n <- balance account
    note ("Balance(" ++ account ++ ") -> " ++ show n)
    pure n
  …
```

It intercepts `Bank` and must say how `Clock` and `Failure` pass through it. Here a deriving clause says it;
for the limit, which carries a parameter, the file writes those instances by hand. With *n* effects and *m*
wrappers this is *n × m* instances. It is the lifting problem of monad transformers (chapter 5.4), and tagless
final inherits it at exactly the point where one wants to intercept.

**Nothing outside can see the program.** There is no value to walk. To observe every `Bank` call you wrap the
instance, as above; what was not routed through your wrapper is not seen.

**It needs higher-kinded types.** `m` is a type constructor that the program abstracts over. Haskell, Scala,
PureScript, OCaml (through functors) and Lean can say that. Java, Kotlin, C#, Go, Swift, TypeScript and Rust
cannot directly, and the encodings that simulate it are costly enough that this family is, in practice, not
available there.

## Capability passing

Drop the abstract monad. An effect is an interface; the program is plain code; the right to ask is an instance
passed in — implicitly, where the language allows it. `code/scala/capabilities.scala`:

```scala
def pay(from: String, to: String, amount: Long)(using bank: Bank, clock: Clock, failure: Failure): String =
  val have = bank.balance(from)
  if have < amount then failure.refuse(s"$from has $have, needs $amount")
  bank.move(from, to, amount)
  s"moved $amount from $from to $to at ${clock.now()}"
```

This is dependency injection with the compiler doing the wiring, and it is fair to call it by that name.

### What it gives

**It reads as the language reads.** No `for`, no `do`, no `flatMap`. Loops are loops; `try` is `try`; a
debugger steps through it; a stack trace is a stack trace. A team needs no new concept to write it.

**The signature lists what is asked**, and a missing capability is a compile error. Context parameters are a
row spelled as a parameter list.

**It costs nothing at run time.** A call through an interface, which the JIT may inline.

**Leaving early is the host's exception**, and so interoperates with every library the host has:

```scala
def refusals[A](body: Failure ?=> A): Either[String, A] =
  boundary[Either[String, A]]:
    Right(body(using new Failure { def refuse(why: String) = boundary.break(Left(why)) }))
```

### What it costs

**The rest of the program is not available.** `bank.balance(from)` runs and returns, once. There is nothing a
handler can keep, call twice or decline to call except by throwing. `capabilities.scala` prints for line 6:
*cannot: a capability's method returns once; the rest of the caller is the host's stack, not a value.* Lines 1
to 5 are the same as everywhere else, which is the honest size of the loss: for answering, refusing and
recording, capabilities are enough.

**An interceptor is a wrapper, with a wrapper's blind side.**

```scala
final class Journal(inner: Bank, var lines: Vector[String] = Vector.empty) extends Bank:
  def balance(a: String) = { val n = inner.balance(a); lines :+= s"Balance($a) -> $n"; n }
  def move(f: String, t: String, n: Long) = { inner.move(f, t, n); lines :+= s"Move($f,$t,$n) -> ()" }
```

It prints the same journal. But it sees what was passed *through it*. A function that was handed the inner
bank, or that captured it earlier, goes around. And a wrapper must implement every method of the interface, so
adding an operation to `Bank` means editing every wrapper; an interceptor over a tree has one default case.

**A capability can escape.** The `Failure` given to `body` is valid only inside `refusals`. Nothing in the
types above stops `body` from storing it in a field and calling `refuse` after `refusals` has returned, when
the `break` has nowhere to go. Keeping capabilities from outliving their handlers is what *capture checking*
is for: the type of a value records which capabilities it may hold. Scala 3 has it as an experimental feature;
Effekt is designed around the same guarantee. Without such a checker it is a convention.

### Where it meets the other families

A capability whose methods merely return gives requirements 1 and 2 and stops. A capability whose methods can
*suspend the caller* gives everything — and then the capability is only the surface, and what is underneath is
one of chapters 3d or 3e. Kotlin is the clearest case: an effect is an interface of `suspend` functions passed
as a context parameter, and because a `suspend` function's continuation is an object, a handler behind that
interface can take the rest. That is how one of my libraries is built (`okay-kotlin/docs/design.md`). Java on
virtual threads is the same shape with a thread underneath.

So "capabilities or handlers" is not a choice between two things. It is the choice of axis (c), how a handler
is found, and it leaves open axis (b), what the rest is made of.

## Who builds this way

- **Tagless final** is the common style of the Cats and Cats Effect ecosystem in Scala (`F[_]: Monad`), and of
  `mtl` in Haskell, whose classes (`MonadState`, `MonadError`, …) are exactly effects as constraints on an
  abstract monad. `fused-effects` is a Haskell library in this tradition: effects are interpreted by
  type-class instances it calls carriers.
- **Capabilities** are the model of Effekt, and the direction of Scala 3's work on capture checking. In Kotlin,
  Arrow's typed errors are a capability: a `Raise<E>` in context gives the right to fail with an `E`. In
  Haskell, `bluefin` passes effect handles as ordinary arguments, scoped by a type parameter so that they
  cannot escape. ZIO's environment type lists the services a computation requires, which is the same idea
  applied to services.
- **Mainstream dependency injection** is this family without the type-level list and with a container in place
  of the compiler.

## On the axes

| axis | tagless final | capabilities |
| --- | --- | --- |
| b. the rest | what the monad's bind makes of it | the host's stack; not a value |
| c. finding the handler | statically, by instance | statically or lexically, by the value in scope |
| d. the types | constraints: an unordered, checked set | context parameters; capture sets where available |
| e. resumptions | what the monad gives: one for `IO`, many for a list or continuation monad | one |
| f. leaving early | what the monad gives | host exceptions, host `finally` |
| g. storing the rest | no | no |
| h. cost | near zero when specialised; dictionary passing when not | an interface call |
| i. the host | as good as the monad's interop | the host itself: its traces, its debugger |

**Choose tagless final when** the language has higher-kinded types, the code is a library that must be generic
over its runtime, and visibility from outside is not a requirement. **Choose capabilities when** the need is
"say what this code reaches for, and let me swap it in a test", the team should write ordinary code, and
nothing has to hold the rest of the program. **Choose neither alone when** something must see every operation
whether or not the code cooperated, or stop a program and continue it later.
