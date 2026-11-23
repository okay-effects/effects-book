# The scenario of code/expected.txt in Python, in DIRECT STYLE ON GENERATORS (chapter 3d): the program is a
# generator function, asking is `yield`, the rest of the program is the generator object stopped at that yield.
# Nothing is typed: a question nobody answers is found when it is asked. One resumption.
# `python3 code/python/bank.py`

from dataclasses import dataclass


@dataclass(frozen=True)
class Balance:
    account: str

    def __str__(self):
        return f"Balance({self.account})"


@dataclass(frozen=True)
class Move:
    source: str
    to: str
    amount: int

    def __str__(self):
        return f"Move({self.source},{self.to},{self.amount})"


@dataclass(frozen=True)
class Now:
    pass


@dataclass(frozen=True)
class Refuse:
    why: str


def pay(source, to, amount):
    """the program: ordinary control flow, `yield` where it asks"""
    have = yield Balance(source)
    if have < amount:
        yield Refuse(f"{source} has {have}, needs {amount}")
    yield Move(source, to, amount)
    at = yield Now()
    return f"moved {amount} from {source} to {to} at {at}"


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
    raise RuntimeError(f"nobody answers {op}")


def same(a):
    return a


def finished(value):
    return value
    yield  # makes this a program that asks nothing


class Accounts:
    def __init__(self, ann):
        self.balances = {"ann": ann, "bob": 0}

    def on(self, op, rest):
        match op:
            case Balance(account):
                return rest(self.balances.get(account, 0))
            case Move(source, to, amount):
                self.balances[source] -= amount
                self.balances[to] = self.balances.get(to, 0) + amount
                return rest()

    def __str__(self):
        return f"ann={self.balances['ann']} bob={self.balances['bob']}"


def fixed_clock(at):
    return (lambda op, rest: rest(at) if isinstance(op, Now) else None), same


# zero resumptions: `rest` is not called, and `handle` closes the body
refusals = (
    lambda op, rest: finished(("refused", op.why)) if isinstance(op, Refuse) else None,
    lambda value: ("paid", value),
)


class Journal:
    """an interceptor: the question is asked again for whoever is outside, and the answer written down"""

    def __init__(self):
        self.lines = []

    def on(self, op, rest):
        if not isinstance(op, (Balance, Move)):
            return None

        def passed_on():
            answer = yield op
            self.lines.append(f"{op} -> {'()' if answer is None else answer}")
            return (yield from rest(answer))

        return passed_on()


def limit(most):
    def on(op, rest):
        if isinstance(op, Move) and op.amount > most:

            def refused():
                return (yield Refuse(f"a move of {op.amount} is over the limit of {most}"))

            return refused()

    return on, same


def shown(result):
    return f"{result[0]}: {result[1]}"


def edge(bank, program):
    return run(handle((bank.on, same), handle(fixed_clock(42), program)))


rich = Accounts(100)
print("1 " + shown(edge(rich, handle(refusals, pay("ann", "bob", 30)))))
print(f"1 balances: {rich}")

poor = Accounts(10)
print("2 " + shown(edge(poor, handle(refusals, pay("ann", "bob", 30)))))
print(f"2 balances: {poor}")

journal = Journal()
edge(Accounts(100), handle(refusals, handle((journal.on, same), pay("ann", "bob", 30))))
for line in journal.lines:
    print("3 journal: " + line)

limited = Accounts(100)
print("4 " + shown(edge(limited, handle(refusals, handle(limit(50), pay("ann", "bob", 80))))))
print(f"4 balances: {limited}")

seen = Journal()
edge(Accounts(10), handle(refusals, handle((seen.on, same), pay("ann", "bob", 30))))
print(f"5 left early: the journal has {len(seen.lines)} line, {'; '.join(seen.lines)}")


# 6. the attempt, made: a handler that calls the rest a second time is refused by `handle`
def twice(op, rest):
    if isinstance(op, Balance):

        def both():
            yield from rest(100)
            return (yield from rest(10))

        return both()


dry = (lambda op, rest: rest() if isinstance(op, Move) else None), same
try:
    run(handle((twice, same), handle(dry, handle(fixed_clock(42), handle(refusals, pay("ann", "bob", 30))))))
    print("6 twice: the second resumption was not refused")
except RuntimeError:
    print("6 twice: cannot: the rest is a generator object, which can be resumed or closed but not copied")
