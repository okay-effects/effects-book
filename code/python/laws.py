# THE LAWS, AS LINES (chapter 4) in Python, ON GENERATORS as code/python/bank.py is (chapter 3d): the twelve
# observations of code/laws-expected.txt. A program is a generator, `then` is `yield from`, a handler is given
# the rest of the program. The rest is the generator object, so lines 10 and 11, which resume it twice, are
# attempted and print `cannot`. Handlers that remember do it in a field, and give it back with the result.
# `python3 -I code/python/laws.py`

from dataclasses import dataclass


@dataclass(frozen=True)
class Get:
    pass


@dataclass(frozen=True)
class Put:
    n: int


@dataclass(frozen=True)
class Refuse:
    why: str


@dataclass(frozen=True)
class Now:
    pass


@dataclass(frozen=True)
class Choose:
    options: tuple


def ask(op):
    """the program that asks one question and is done with its answer"""
    return (yield op)


def done(value):
    return value
    yield  # makes this a program that asks nothing


def then(program, f):
    """one program after another: what `a = yield from p; return (yield from f(a))` is, as a function"""
    return (yield from f((yield from program)))


def handle(handler, body):
    """a handler is a function (op, rest) -> a program, or None for "not mine"; `done` wraps the result"""
    on, done = handler

    def resume(answer=None):
        try:
            op = body.send(answer)
        except StopIteration as finished:
            return done(finished.value)
        used = False

        def rest(x=None):
            nonlocal used
            if used:
                raise RuntimeError("resumed twice: a generator's rest is not a copy")
            used = True
            return resume(x)

        mine = on(op, rest)
        if mine is not None:
            return (yield from mine)
        return (yield from rest((yield op)))  # not mine: ask it outward, go on with the answer

    try:
        return (yield from resume())
    finally:
        body.close()  # if the rest was dropped, the body's `finally` blocks run now


def run(program):
    try:
        op = program.send(None)
    except StopIteration as finished:
        return finished.value
    raise RuntimeError(f"nobody answers {type(op).__name__}")


# ---- the handlers of the chapter


class Cell:
    """the state: the cell is a field, and `done` gives its content back beside the value"""

    def __init__(self, s):
        self.s = s

    def on(self, op, rest):
        match op:
            case Get():
                return rest(self.s)
            case Put(n):
                self.s = n
                return rest()

    def done(self, value):
        return value, self.s


def cell(s, program):
    """`p under Cell(s)`"""
    state = Cell(s)
    return handle((state.on, state.done), program)


def refusals(program):
    """zero resumptions: `rest` is not called, and `handle` closes the body"""
    on = lambda op, rest: done(("refused", op.why)) if isinstance(op, Refuse) else None
    return handle((on, lambda value: ("paid", value)), program)


def fixed_clock(at, program):
    return handle((lambda op, rest: rest(at) if isinstance(op, Now) else None, lambda value: value), program)


def all_choices(program):
    """every option taken: the rest once for each. This is the handler that a generator's rest refuses"""

    def on(op, rest):
        if isinstance(op, Choose):

            def every():
                paths = []
                for option in op.options:
                    paths += yield from rest(option)
                return paths

            return every()

    return handle((on, lambda value: [value]), program)


class Log:
    """the observer, an interceptor: every question of the state asked again outward, written down with its
    answer"""

    def __init__(self):
        self.lines = []

    def on(self, op, rest):
        if not isinstance(op, (Get, Put)):
            return None

        def passed_on():
            answer = yield op
            self.lines.append(f"Put({op.n})" if isinstance(op, Put) else f"Get={answer}")
            return (yield from rest(answer))

        return passed_on()

    def done(self, value):
        return self.lines, value


def log(program):
    observer = Log()
    return handle((observer.on, observer.done), program)


# ---- the programs of the twelve lines: ordinary control flow, `yield` where they ask


def put(n):
    yield Put(n)
    return n


def bump():
    x = yield Get()
    yield Put(x + 1)
    return x


def stopped():
    yield Put(1)
    x = yield Get()
    yield Refuse(f"stop at {x}")
    yield Put(9)
    return 9


def lost():
    yield Put(1)
    return (yield Refuse("no"))


def timed():
    t = yield Now()
    yield Put(t)
    x = yield Get()
    return x + t


def forked():
    yield Put(1)
    x = yield Choose((10, 20))
    y = yield Get()
    yield Put(y + x)
    z = yield Get()
    return z


def held(result, shown=str):
    value, s = result
    return f"{shown(value)}, cell {s}"


def outcome(result, shown=str):
    return f"paid({shown(result[1])})" if result[0] == "paid" else f"refused({result[1]})"


def seen(program):
    """what a program over the state is seen to do: its questions with their answers, its value, the cell after"""
    (lines, value), s = run(cell(0, log(program)))
    return f"{' '.join(lines)} => {value}, cell {s}"


# 1-3. sequencing: `done` is a unit on each side, and grouping does not matter
print(f"1 done then f: {seen(then(done(5), put))} = {seen(put(5))}")
print(f"2 p then done: {seen(then(bump(), done))} = {seen(bump())}")


def f(x):
    return put(x + 1)


def g(y):
    return then(ask(Put(y * 2)), lambda _: done(y))


print(f"3 grouping: {seen(then(then(ask(Get()), f), g))} = {seen(then(ask(Get()), lambda x: then(f(x), g)))}")

# 4-5. a handler is a fold: what it makes of `done`, and of one program after another
print(f"4 handling done: {held(run(cell(3, done(7))))} | {outcome(run(refusals(done(7))))}")
whole = run(cell(0, then(bump(), lambda a: then(bump(), lambda b: done(a + b)))))
a, s1 = run(cell(0, bump()))
b, s2 = run(cell(s1, bump()))
print(f"5 handling a sequence: {held(whole)} = {held((a + b, s2))}")

# 6. a handler sees every operation of its effect, in order, and nothing of another's
(lines, result), s = run(cell(0, log(refusals(stopped()))))
print(f"6 its own and no other's: {' '.join(lines)} => {outcome(result)}, cell {s}")

# 7. a question nobody answers: nothing is typed, so it is found when it is asked; `run` says which question
try:
    run(cell(0, timed()))
    unanswered = "it ran"
except RuntimeError as nobody:
    unanswered = str(nobody).removeprefix("nobody answers ")
print(f"7 nobody answers: {unanswered}")

# 8. two effects whose order is the meaning: is the cell part of what a refusal throws away?
inside = outcome(run(refusals(cell(0, lost()))), held)
after = held(run(cell(0, refusals(lost()))), outcome)
print(f"8 state then failure: {inside} | failure then state: {after}")

# 9. two handlers that only answer: either order
print(f"9 two that only answer: {held(run(cell(0, fixed_clock(7, timed()))))} = "
      f"{held(run(fixed_clock(7, cell(0, timed()))))}")


# 10-11. the rest resumed twice. The attempt, made: `all_choices` calls the rest for the second option, and
# `handle` refuses, in either order of the two handlers
def twice(name, attempt):
    try:
        print(f"{name}: {attempt()}")
    except RuntimeError as refused:
        if not str(refused).startswith("resumed twice"):
            raise
        print(f"{name}: cannot: the rest is a generator object, which can be resumed or closed but not copied")


twice("10 twice, state inside", lambda: " | ".join(held(path) for path in run(all_choices(cell(0, forked())))))
twice(
    "11 twice, state outside",
    lambda: held(run(cell(0, all_choices(forked()))), lambda paths: " | ".join(str(path) for path in paths)),
)

# 12. what this handler owes its own operations: the four equations of a state
after_put, _ = run(cell(0, then(ask(Put(3)), lambda _: ask(Get()))))
last_put, _ = run(cell(0, then(ask(Put(3)), lambda _: then(ask(Put(4)), lambda _: ask(Get())))))
(x, y), _ = run(cell(0, then(ask(Get()), lambda x: then(ask(Get()), lambda y: done((x, y))))))
_, put_back = run(cell(5, then(ask(Get()), lambda x: ask(Put(x)))))
print(f"12 the cell's own: {after_put} {last_put} {x}={y} cell {put_back}")
