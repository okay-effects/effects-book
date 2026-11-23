// The laws of code/laws-expected.txt in C#, on the compiler's ITERATOR STATE MACHINES: the mechanism of
// bank.cs (chapter 3e), and twelve observations on it (chapter 4). A program yields its questions and hands
// its value to `done`; one program after another is one iterator stepping two others. A handler answers by
// returning, keeps what it remembers in a field, and says in Done what it makes of the result. The state
// machine cannot be copied: lines 10 and 11 cannot be run.
// `dotnet run code/csharp/laws.cs` (.NET 10 or later runs a single file)

using System;
using System.Collections.Generic;
using System.Linq;

/// an operation: a question as data. Its answer is put into it before the program is stepped again:
/// `yield return` passes values out only, so the answer travels through the question
abstract record Op { public object? Answer; }
record Get : Op;
record Put(long N) : Op { public override string ToString() => $"Put({N})"; }
record Refuse(string Why) : Op;
record Now : Op { public override string ToString() => "Now"; }

/// a program: its questions, one at a time. `done` carries the value out, because an iterator cannot both
/// yield and return a value
delegate IEnumerable<Op> Prog<A>(Action<A> done);

/// a handler answers by returning (true, answer); (false, _) is "not mine". It may ask whoever is outside.
/// It leaves by throwing Leave, with what the program under it has come to. Done is what it makes of a
/// result that reaches it
abstract class Handler
{
    public abstract (bool mine, object? answer) On(Op op, Func<Op, object?> outer);
    public virtual object? Done(object? a) => a;
}
class Leave(Handler by, object result) : Exception
{
    public Handler By = by;
    public object Result = result;
}

/// a value with the cell's content after it
record Held(object? Value, long Cell) { public override string ToString() => $"{Value}, cell {Cell}"; }
class Cell(long s) : Handler
{
    public override (bool, object?) On(Op op, Func<Op, object?> outer)
    {
        if (op is Put put) s = put.N;
        return op is Get ? (true, s) : (op is Put, null);
    }
    public override object? Done(object? a) => new Held(a, s);
}

record Paid(object? Value) { public override string ToString() => $"paid({Value})"; }
record Refused(string Why) { public override string ToString() => $"refused({Why})"; }
/// zero resumptions: it leaves, and the iterator is not stepped again
class Refusals : Handler
{
    public override (bool, object?) On(Op op, Func<Op, object?> outer) =>
        op is Refuse r ? throw new Leave(this, new Refused(r.Why)) : (false, null);
    public override object? Done(object? a) => new Paid(a);
}

class FixedClock(long now) : Handler
{
    public override (bool, object?) On(Op op, Func<Op, object?> outer) => op is Now ? (true, now) : (false, null);
}

/// a value with the questions of the state that were asked on the way to it
record Logged(List<string> Lines, object? Value)
{
    public override string ToString() => $"{string.Join(" ", Lines)} => {Value}";
}
/// the observer, an interceptor: every question of the state asked again outward, written down with its answer
class Log : Handler
{
    readonly List<string> lines = [];
    public override (bool, object?) On(Op op, Func<Op, object?> outer)
    {
        if (op is not (Get or Put)) return (false, null);
        var answer = outer(op);
        lines.Add(op is Get ? $"Get={answer}" : $"{op}");
        return (true, answer);
    }
    public override object? Done(object? a) => new Logged(lines, a);
}

static class Laws
{
    /// handlers are listed innermost first
    static object? Answer(Handler[] handlers, int from, Op op)
    {
        for (var i = from; i < handlers.Length; i++)
        {
            var next = i + 1;
            var (mine, answer) = handlers[i].On(op, o => Answer(handlers, next, o));
            if (mine) return answer;
        }
        throw new InvalidOperationException($"nobody answers {op}");
    }

    /// a result on its way out: what each handler from `from` outward makes of it
    static object? Ended(Handler[] handlers, int from, object? a) =>
        handlers.Skip(from).Aggregate(a, (r, h) => h.Done(r));

    /// `p under handlers[0] under handlers[1] ...`. The value passes every Done; the result of a handler that
    /// left passes only those outside it
    static object? Run<A>(Handler[] handlers, Prog<A> p)
    {
        object? value = null;
        // `using` disposes the iterator when the loop is left: its pending `finally` blocks run
        using var program = p(a => value = a).GetEnumerator();
        try
        {
            while (program.MoveNext()) program.Current.Answer = Answer(handlers, 0, program.Current);
        }
        catch (Leave leave) { return Ended(handlers, Array.IndexOf(handlers, leave.By) + 1, leave.Result); }
        return Ended(handlers, 0, value);
    }

    // ---- one program after another: the two iterators that sequencing is made of

    static Prog<A> Done<A>(A a) => done => Finished(a, done);
    static IEnumerable<Op> Finished<A>(A a, Action<A> done)
    {
        done(a);
        yield break;
    }
    static Prog<B> Then<A, B>(Prog<A> p, Func<A, Prog<B>> f) => done => Both(p, f, done);
    static IEnumerable<Op> Both<A, B>(Prog<A> p, Func<A, Prog<B>> f, Action<B> done)
    {
        A x = default!;
        foreach (var op in p(a => x = a)) yield return op;
        foreach (var op in f(x)(done)) yield return op;
    }

    // ---- the programs of the twelve lines: ordinary C# with `yield return` where it asks

    static IEnumerable<Op> Get(Action<long> done)
    {
        var get = new Get();
        yield return get;
        done((long)get.Answer!);
    }
    static Prog<long> Put(long n) => done => Putting(n, done);
    static IEnumerable<Op> Putting(long n, Action<long> done)
    {
        yield return new Put(n);
        done(n);
    }
    static IEnumerable<Op> Bump(Action<long> done)
    {
        var get = new Get();
        yield return get;
        var x = (long)get.Answer!;
        yield return new Put(x + 1);
        done(x);
    }
    static IEnumerable<Op> Stopped(Action<long> done)
    {
        yield return new Put(1);
        var get = new Get();
        yield return get;
        yield return new Refuse($"stop at {get.Answer}");
        foreach (var op in Put(9)(done)) yield return op;
    }
    static IEnumerable<Op> Lost(Action<long> done)
    {
        yield return new Put(1);
        yield return new Refuse("no");
    }
    static IEnumerable<Op> Timed(Action<long> done)
    {
        var now = new Now();
        yield return now;
        var t = (long)now.Answer!;
        yield return new Put(t);
        var get = new Get();
        yield return get;
        done((long)get.Answer! + t);
    }

    /// what a program over the state is seen to do: its questions with their answers, its value, the cell after
    static object? Seen(Prog<long> p) => Run([new Log(), new Cell(0)], p);
    /// `p under Cell(s)`
    static Held UnderCell<A>(long s, Prog<A> p) => (Held)Run([new Cell(s)], p)!;

    public static void Main()
    {
        // 1-3. sequencing: `done` is a unit on each side, and grouping does not matter
        Console.WriteLine($"1 done then f: {Seen(Then(Done(5L), Put))} = {Seen(Put(5))}");
        Console.WriteLine($"2 p then done: {Seen(Then<long, long>(Bump, Done))} = {Seen(Bump)}");
        Func<long, Prog<long>> f = x => Put(x + 1), g = y => Then(Put(y * 2), _ => Done(y));
        Prog<long> get = Get;
        Console.WriteLine($"3 grouping: {Seen(Then(Then(get, f), g))} = {Seen(Then(get, x => Then(f(x), g)))}");

        // 4-5. a handler is a fold: what it makes of `done`, and of one program after another
        Console.WriteLine($"4 handling done: {UnderCell(3, Done(7L))} | {Run([new Refusals()], Done(7L))}");
        var whole = UnderCell(0, Then<long, long>(Bump, a => Then<long, long>(Bump, b => Done(a + b))));
        var first = UnderCell<long>(0, Bump);
        var second = UnderCell<long>(first.Cell, Bump);
        var pieces = new Held((long)first.Value! + (long)second.Value!, second.Cell);
        Console.WriteLine($"5 handling a sequence: {whole} = {pieces}");

        // 6. a handler sees every operation of its effect, in order, and nothing of another's
        Console.WriteLine($"6 its own and no other's: {Run<long>([new Refusals(), new Log(), new Cell(0)], Stopped)}");

        // 7. a question nobody answers: the program has no value, and the run says which question
        string unanswered;
        try { unanswered = $"it ran: {UnderCell<long>(0, Timed)}"; }
        catch (InvalidOperationException e) { unanswered = e.Message.Replace("nobody answers ", ""); }
        Console.WriteLine($"7 nobody answers: {unanswered}");

        // 8. two effects whose order is the meaning: is the cell part of what a refusal throws away?
        Console.WriteLine($"8 state then failure: {Run<long>([new Cell(0), new Refusals()], Lost)}"
            + $" | failure then state: {Run<long>([new Refusals(), new Cell(0)], Lost)}");

        // 9. two handlers that only answer: either order
        Console.WriteLine($"9 two that only answer: {Run<long>([new FixedClock(7), new Cell(0)], Timed)}"
            + $" = {Run<long>([new Cell(0), new FixedClock(7)], Timed)}");

        // 10-11. the rest resumed twice: a handler here returns one answer, and there is one iterator to step
        var once = "cannot: the rest is a compiler-made state machine object; the language gives no way to copy it";
        Console.WriteLine("10 twice, state inside: " + once);
        Console.WriteLine("11 twice, state outside: " + once);

        // 12. what this handler owes its own operations: the four equations of a state
        var afterPut = UnderCell(0, Then(Put(3), _ => get));
        var lastPut = UnderCell(0, Then(Put(3), _ => Then(Put(4), _ => get)));
        var twice = UnderCell(0, Then(get, x => Then(get, y => Done($"{x}={y}"))));
        var putBack = UnderCell(5, Then(get, Put));
        Console.WriteLine($"12 the cell's own: {afterPut.Value} {lastPut.Value} {twice.Value} cell {putBack.Cell}");
    }
}
