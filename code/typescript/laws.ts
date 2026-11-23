// THE LAWS, AS LINES (chapter 4) in TypeScript, ON GENERATORS as code/typescript/bank.ts is (chapter 3d): the
// twelve observations of code/laws-expected.txt. A program is a generator, `then` is `yield*`, a handler is
// given the rest of the program. The rest is the generator object, so lines 10 and 11, which resume it twice,
// are attempted and print `cannot`. Handlers that remember do it in a field, and give it back with the result.
// `node code/typescript/laws.ts` (Node 24 or later runs TypeScript whose types can be erased)

type Op =
  | { tag: "Get" }
  | { tag: "Put"; n: number }
  | { tag: "Refuse"; why: string }
  | { tag: "Now" }
  | { tag: "Choose"; options: number[] };

/** a program: a generator that yields questions and is sent their answers */
type Prog<A> = Generator<Op, A, any>;

const Get: Op = { tag: "Get" };
const Put = (n: number): Op => ({ tag: "Put", n });
const Refuse = (why: string): Op => ({ tag: "Refuse", why });
const Now: Op = { tag: "Now" };
const Choose = (options: number[]): Op => ({ tag: "Choose", options });

/** the program that asks one question and is done with its answer */
function* ask(op: Op): Prog<number> {
  return yield op;
}
function* done<A>(a: A): Prog<A> {
  return a;
}
/** one program after another: what `const a = yield* p; return yield* f(a)` is, as a function */
function* then<A, B>(p: Prog<A>, f: (a: A) => Prog<B>): Prog<B> {
  return yield* f(yield* p);
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
  if (!step.done) throw new Error(`nobody answers ${step.value.tag}`);
  return step.value;
}

// ---- the handlers of the chapter

/** the state: the cell is a field, and `done` gives its content back beside the value */
class Cell<A> implements Handler<A, [A, number]> {
  s: number;
  constructor(s: number) {
    this.s = s;
  }
  done(a: A): [A, number] {
    return [a, this.s];
  }
  on(op: Op, rest: (answer?: unknown) => Prog<[A, number]>) {
    if (op.tag === "Get") return rest(this.s);
    if (op.tag === "Put") {
      this.s = op.n;
      return rest();
    }
    return undefined;
  }
}
/** `p under Cell(s)` */
const cell = <A>(s: number, p: Prog<A>) => handle(new Cell<A>(s), p);

type Result<A> = { paid: A } | { refused: string };

/** zero resumptions: `rest` is not called, and `handle` closes the body */
const refusals = <A>(p: Prog<A>) =>
  handle<A, Result<A>>(
    { done: (a) => ({ paid: a }), on: (op) => (op.tag === "Refuse" ? done({ refused: op.why }) : undefined) },
    p,
  );

const fixedClock = <A>(at: number, p: Prog<A>) =>
  handle<A, A>({ done: (a) => a, on: (op, rest) => (op.tag === "Now" ? rest(at) : undefined) }, p);

/** every option taken: the rest once for each. This is the handler that a generator's rest refuses */
const allChoices = <A>(p: Prog<A>) =>
  handle<A, A[]>(
    {
      done: (a) => [a],
      on: (op, rest) =>
        op.tag === "Choose"
          ? (function* (): Prog<A[]> {
              const all: A[] = [];
              for (const option of op.options) all.push(...(yield* rest(option)));
              return all;
            })()
          : undefined,
    },
    p,
  );

/** the observer, an interceptor: every question of the state asked again outward, written down with its answer */
class Log<A> implements Handler<A, [string[], A]> {
  lines: string[] = [];
  done(a: A): [string[], A] {
    return [this.lines, a];
  }
  on(op: Op, rest: (answer?: unknown) => Prog<[string[], A]>) {
    if (op.tag !== "Get" && op.tag !== "Put") return undefined;
    const lines = this.lines;
    return (function* (): Prog<[string[], A]> {
      const answer = yield op;
      lines.push(op.tag === "Put" ? `Put(${op.n})` : `Get=${answer}`);
      return yield* rest(answer);
    })();
  }
}
const log = <A>(p: Prog<A>) => handle(new Log<A>(), p);

// ---- the programs of the twelve lines: ordinary control flow, `yield` where they ask

function* put(n: number): Prog<number> {
  yield Put(n);
  return n;
}
function* bump(): Prog<number> {
  const x: number = yield Get;
  yield Put(x + 1);
  return x;
}
function* stopped(): Prog<number> {
  yield Put(1);
  const x: number = yield Get;
  yield Refuse(`stop at ${x}`);
  yield Put(9);
  return 9;
}
function* lost(): Prog<number> {
  yield Put(1);
  return yield Refuse("no");
}
function* timed(): Prog<number> {
  const t: number = yield Now;
  yield Put(t);
  const x: number = yield Get;
  return x + t;
}
function* forked(): Prog<number> {
  yield Put(1);
  const x: number = yield Choose([10, 20]);
  const y: number = yield Get;
  yield Put(y + x);
  const z: number = yield Get;
  return z;
}

const held = <A>([a, s]: [A, number], shown: (a: A) => string = String) => `${shown(a)}, cell ${s}`;
const outcome = <A>(r: Result<A>, shown: (a: A) => string = String) =>
  "paid" in r ? `paid(${shown(r.paid)})` : `refused(${r.refused})`;

/** what a program over the state is seen to do: its questions with their answers, its value, the cell after */
function seen(p: Prog<number>): string {
  const [[lines, a], s] = run(cell(0, log(p)));
  return `${lines.join(" ")} => ${a}, cell ${s}`;
}

// 1-3. sequencing: `done` is a unit on each side, and grouping does not matter
console.log(`1 done then f: ${seen(then(done(5), put))} = ${seen(put(5))}`);
console.log(`2 p then done: ${seen(then(bump(), done))} = ${seen(bump())}`);
const f = (x: number) => put(x + 1);
const g = (y: number) => then(ask(Put(y * 2)), () => done(y));
console.log(`3 grouping: ${seen(then(then(ask(Get), f), g))} = ${seen(then(ask(Get), (x) => then(f(x), g)))}`);

// 4-5. a handler is a fold: what it makes of `done`, and of one program after another
console.log(`4 handling done: ${held(run(cell(3, done(7))))} | ${outcome(run(refusals(done(7))))}`);
const whole = run(cell(0, then(bump(), (a) => then(bump(), (b) => done(a + b)))));
const [a, s1] = run(cell(0, bump()));
const [b, s2] = run(cell(s1, bump()));
console.log(`5 handling a sequence: ${held(whole)} = ${held([a + b, s2])}`);

// 6. a handler sees every operation of its effect, in order, and nothing of another's
const [[lines, r], s] = run(cell(0, log(refusals(stopped()))));
console.log(`6 its own and no other's: ${lines.join(" ")} => ${outcome(r)}, cell ${s}`);

// 7. a question nobody answers: the types do not object (every question is an `Op`); `run` says which question
let unanswered = "it ran";
try {
  run(cell(0, timed()));
} catch (e) {
  unanswered = e instanceof Error ? e.message.replace("nobody answers ", "") : String(e);
}
console.log(`7 nobody answers: ${unanswered}`);

// 8. two effects whose order is the meaning: is the cell part of what a refusal throws away?
const inside = outcome(run(refusals(cell(0, lost()))), held);
const after = held(run(cell(0, refusals(lost()))), outcome);
console.log(`8 state then failure: ${inside} | failure then state: ${after}`);

// 9. two handlers that only answer: either order
const clockInside = held(run(cell(0, fixedClock(7, timed()))));
console.log(`9 two that only answer: ${clockInside} = ${held(run(fixedClock(7, cell(0, timed()))))}`);

// 10-11. the rest resumed twice. The attempt, made: `allChoices` calls the rest for the second option, and
// `handle` refuses, in either order of the two handlers
function twice(name: string, attempt: () => string) {
  try {
    console.log(`${name}: ${attempt()}`);
  } catch (e) {
    if (!(e instanceof Error) || !e.message.startsWith("resumed twice")) throw e;
    console.log(`${name}: cannot: the rest is a generator object, which can be resumed or closed but not copied`);
  }
}
twice("10 twice, state inside", () =>
  run(allChoices(cell(0, forked())))
    .map((path) => held(path))
    .join(" | "),
);
twice("11 twice, state outside", () => held(run(cell(0, allChoices(forked()))), (paths) => paths.join(" | ")));

// 12. what this handler owes its own operations: the four equations of a state
const [afterPut] = run(cell(0, then(ask(Put(3)), () => ask(Get))));
const [lastPut] = run(cell(0, then(ask(Put(3)), () => then(ask(Put(4)), () => ask(Get)))));
const [[x, y]] = run(cell(0, then(ask(Get), (x) => then(ask(Get), (y) => done([x, y])))));
const [, putBack] = run(cell(5, then(ask(Get), (x) => ask(Put(x)))));
console.log(`12 the cell's own: ${afterPut} ${lastPut} ${x}=${y} cell ${putBack}`);
