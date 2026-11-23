// The scenario of code/expected.txt in TypeScript, in DIRECT STYLE ON GENERATORS (chapter 3d): the program is
// a generator function, asking is `yield`, and the rest of the program is the generator object itself, stopped
// at that yield. A generator object can be resumed, or closed; it cannot be copied. One resumption.
// `node code/typescript/bank.ts` (Node 24 or later runs TypeScript whose types can be erased)

type Op =
  | { tag: "Balance"; account: string }
  | { tag: "Move"; from: string; to: string; amount: number }
  | { tag: "Now" }
  | { tag: "Refuse"; why: string };

/** a program: a generator that yields questions and is sent their answers */
type Prog<A> = Generator<Op, A, any>;

const balance = (account: string): Op => ({ tag: "Balance", account });
const move = (from: string, to: string, amount: number): Op => ({ tag: "Move", from, to, amount });
const now: Op = { tag: "Now" };
const refuse = (why: string): Op => ({ tag: "Refuse", why });

/** the program: ordinary control flow, `yield` where it asks */
function* pay(from: string, to: string, amount: number): Prog<string> {
  const have: number = yield balance(from);
  if (have < amount) yield refuse(`${from} has ${have}, needs ${amount}`);
  yield move(from, to, amount);
  const at: number = yield now;
  return `moved ${amount} from ${from} to ${to} at ${at}`;
}

/** a handler: `on` is given the question and the rest; undefined means "not mine". What `on` returns is itself
 * a program, so a handler may ask whoever is outside */
interface Handler<A, B> {
  done(a: A): B;
  on(op: Op, rest: (answer?: unknown) => Prog<B>): Prog<B> | undefined;
}

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

function run<A>(p: Prog<A>): A {
  const step = p.next();
  if (!step.done) throw new Error(`nobody answers ${show(step.value)}`);
  return step.value;
}

type Result = { paid: string } | { refused: string };

class Accounts<A> implements Handler<A, A> {
  balances: Record<string, number>;
  constructor(ann: number) {
    this.balances = { ann, bob: 0 };
  }
  done(a: A) {
    return a;
  }
  on(op: Op, rest: (answer?: unknown) => Prog<A>) {
    if (op.tag === "Balance") return rest(this.balances[op.account] ?? 0);
    if (op.tag === "Move") {
      this.balances[op.from] -= op.amount;
      this.balances[op.to] = (this.balances[op.to] ?? 0) + op.amount;
      return rest();
    }
    return undefined;
  }
  toString() {
    return `ann=${this.balances.ann} bob=${this.balances.bob}`;
  }
}

const fixedClock = <A>(at: number): Handler<A, A> => ({
  done: (a) => a,
  on: (op, rest) => (op.tag === "Now" ? rest(at) : undefined),
});

/** zero resumptions: `rest` is not called, and `handle` closes the body */
const refusals: Handler<string, Result> = {
  done: (a) => ({ paid: a }),
  on: (op) => (op.tag === "Refuse" ? done({ refused: op.why }) : undefined),
};
function* done<A>(a: A): Prog<A> {
  return a;
}

/** an interceptor: the question is asked again for whoever is outside, and the answer written down */
class Journal<A> implements Handler<A, A> {
  lines: string[] = [];
  done(a: A) {
    return a;
  }
  on(op: Op, rest: (answer?: unknown) => Prog<A>) {
    if (op.tag !== "Balance" && op.tag !== "Move") return undefined;
    const lines = this.lines;
    return (function* (): Prog<A> {
      const answer = yield op;
      lines.push(`${show(op)} -> ${answer === undefined ? "()" : answer}`);
      return yield* rest(answer);
    })();
  }
}

const limit = <A>(max: number): Handler<A, A> => ({
  done: (a) => a,
  on: (op) =>
    op.tag === "Move" && op.amount > max
      ? (function* (): Prog<A> {
          return yield refuse(`a move of ${op.amount} is over the limit of ${max}`);
        })()
      : undefined,
});

function show(op: Op): string {
  switch (op.tag) {
    case "Balance":
      return `Balance(${op.account})`;
    case "Move":
      return `Move(${op.from},${op.to},${op.amount})`;
    case "Now":
      return "Now";
    case "Refuse":
      return `Refuse(${op.why})`;
  }
}
const shown = (r: Result) => ("paid" in r ? `paid: ${r.paid}` : `refused: ${r.refused}`);
const edge = (bank: Accounts<Result>, p: Prog<Result>) => run(handle(bank, handle(fixedClock<Result>(42), p)));

const rich = new Accounts<Result>(100);
console.log("1 " + shown(edge(rich, handle(refusals, pay("ann", "bob", 30)))));
console.log(`1 balances: ${rich}`);

const poor = new Accounts<Result>(10);
console.log("2 " + shown(edge(poor, handle(refusals, pay("ann", "bob", 30)))));
console.log(`2 balances: ${poor}`);

const journal = new Journal<string>();
edge(new Accounts<Result>(100), handle(refusals, handle(journal, pay("ann", "bob", 30))));
for (const line of journal.lines) console.log("3 journal: " + line);

const limited = new Accounts<Result>(100);
console.log("4 " + shown(edge(limited, handle(refusals, handle(limit<string>(50), pay("ann", "bob", 80))))));
console.log(`4 balances: ${limited}`);

const seen = new Journal<string>();
edge(new Accounts<Result>(10), handle(refusals, handle(seen, pay("ann", "bob", 30))));
console.log(`5 left early: the journal has ${seen.lines.length} line, ${seen.lines.join("; ")}`);

// 6. the attempt, made: a handler that calls the rest a second time is refused by `handle`
const twice: Handler<Result, Result> = {
  done: (a) => a,
  on: (op, rest) =>
    op.tag === "Balance"
      ? (function* () {
          yield* rest(100);
          return yield* rest(10);
        })()
      : undefined,
};
try {
  const dry: Handler<Result, Result> = { done: (a) => a, on: (op, rest) => (op.tag === "Move" ? rest() : undefined) };
  run(handle(twice, handle(dry, handle(fixedClock<Result>(42), handle(refusals, pay("ann", "bob", 30))))));
  console.log("6 twice: the second resumption was not refused");
} catch (e) {
  console.log("6 twice: cannot: the rest is a generator object, which can be resumed or closed but not copied");
}
