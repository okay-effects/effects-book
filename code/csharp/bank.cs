// The scenario of code/expected.txt in C#, on the compiler's ITERATOR STATE MACHINES (chapter 3e): a method
// with `yield return` is compiled into a class with one state per yield. The program yields its questions; a
// loop steps it and hands each answer back through the question. The state machine is an object the language
// gives no way to copy. One resumption.
// `dotnet run code/csharp/bank.cs` (.NET 10 or later runs a single file)

using System;
using System.Collections.Generic;
using System.Linq;

/// an operation: a question as data. Its answer is put into it before the program is stepped again:
/// `yield return` passes values out only, so the answer travels through the question
abstract record Op { public object? Answer; }
record Balance(string Account) : Op { public override string ToString() => $"Balance({Account})"; }
record Move(string From, string To, long Amount) : Op
{
    public override string ToString() => $"Move({From},{To},{Amount})";
}
record Now : Op;
record Refuse(string Why) : Op;

/// a handler answers by returning (true, answer); (false, _) is "not mine". It may ask whoever is outside.
/// It leaves by throwing Leave
delegate (bool mine, object? answer) Handler(Op op, Func<Op, object?> outer);
class Leave(string result) : Exception { public string Result = result; }

static class Bank
{
    /// the program: ordinary C# with `yield return` where it asks. `receipt` carries the result out,
    /// because an iterator cannot both yield and return a value
    static IEnumerable<Op> Pay(string from, string to, long amount, Action<string> receipt)
    {
        var balance = new Balance(from);
        yield return balance;
        var have = (long)balance.Answer!;
        if (have < amount) yield return new Refuse($"{from} has {have}, needs {amount}");
        yield return new Move(from, to, amount);
        var now = new Now();
        yield return now;
        receipt($"moved {amount} from {from} to {to} at {now.Answer}");
    }

    /// handlers are listed innermost first
    static object? Answer(Handler[] handlers, int from, Op op)
    {
        for (var i = from; i < handlers.Length; i++)
        {
            var next = i + 1;
            var (mine, answer) = handlers[i](op, o => Answer(handlers, next, o));
            if (mine) return answer;
        }
        throw new InvalidOperationException($"nobody answers {op}");
    }

    static string Run(Handler[] handlers, long amount)
    {
        var outcome = "";
        // `using` disposes the iterator when the loop is left: its pending `finally` blocks run
        using var program = Pay("ann", "bob", amount, receipt => outcome = "paid: " + receipt).GetEnumerator();
        try
        {
            while (program.MoveNext()) program.Current.Answer = Answer(handlers, 0, program.Current);
        }
        catch (Leave leave) { outcome = "refused: " + leave.Result; }
        return outcome;
    }

    static Handler Accounts(Dictionary<string, long> b) => (op, _) => op switch
    {
        Balance q => (true, b.GetValueOrDefault(q.Account)),
        Move m => (true, Moved(b, m)),
        _ => (false, null),
    };
    static object? Moved(Dictionary<string, long> b, Move m)
    {
        b[m.From] = b.GetValueOrDefault(m.From) - m.Amount;
        b[m.To] = b.GetValueOrDefault(m.To) + m.Amount;
        return null;
    }
    static readonly Handler Clock = (op, _) => op is Now ? (true, 42L) : (false, null);
    static readonly Handler Refusals = (op, _) => op is Refuse r ? throw new Leave(r.Why) : (false, null);
    static Handler Journal(List<string> lines) => (op, outer) =>
    {
        if (op is not (Balance or Move)) return (false, null);
        var answer = outer(op);
        lines.Add($"{op} -> {answer ?? "()"}");
        return (true, answer);
    };
    static Handler Limit(long most) => (op, outer) => op is Move m && m.Amount > most
        ? (true, outer(new Refuse($"a move of {m.Amount} is over the limit of {most}")))
        : (false, null);

    static Dictionary<string, long> Of(long ann) => new() { ["ann"] = ann, ["bob"] = 0 };
    static string Shown(Dictionary<string, long> b) => $"ann={b["ann"]} bob={b["bob"]}";

    public static void Main()
    {
        var rich = Of(100);
        Console.WriteLine("1 " + Run([Refusals, Clock, Accounts(rich)], 30));
        Console.WriteLine("1 balances: " + Shown(rich));

        var poor = Of(10);
        Console.WriteLine("2 " + Run([Refusals, Clock, Accounts(poor)], 30));
        Console.WriteLine("2 balances: " + Shown(poor));

        var journal = new List<string>();
        Run([Journal(journal), Refusals, Clock, Accounts(Of(100))], 30);
        foreach (var line in journal) Console.WriteLine("3 journal: " + line);

        var limited = Of(100);
        Console.WriteLine("4 " + Run([Limit(50), Refusals, Clock, Accounts(limited)], 80));
        Console.WriteLine("4 balances: " + Shown(limited));

        var seen = new List<string>();
        Run([Journal(seen), Refusals, Clock, Accounts(Of(10))], 30);
        Console.WriteLine($"5 left early: the journal has {seen.Count} line, {string.Join("; ", seen)}");

        Console.WriteLine("6 twice: cannot: the rest is a compiler-made state machine object; "
            + "the language gives no way to copy it");
    }
}
