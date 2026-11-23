// The laws of code/laws-expected.txt in Go, in DIRECT STYLE ON GOROUTINES: the mechanism of bank.go
// (chapter 3d), and twelve observations on it (chapter 4). A program is plain code, so `done` is `return` and
// "then" is the next statement. A handler answers by returning, keeps what it remembers in a field, and says
// in Done what it makes of the result. The rest is a blocked goroutine: lines 10 and 11 cannot be run.
// `go run code/go/laws.go`
package main

import (
	"fmt"
	"slices"
	"strings"
)

type Get struct{}
type Put struct{ N int64 }
type Refuse struct{ Why string }
type Now struct{}

func (p Put) String() string { return fmt.Sprintf("Put(%d)", p.N) }
func (Now) String() string   { return "Now" }

// Run is what a body holds: where its questions go, where its answers come from. Go has no implicit
// parameters, so the body is handed it.
type Run struct {
	toHandler chan any
	toBody    chan reply
}
type reply struct {
	answer    any
	abandoned bool
}
type finished struct{ value any }
type abandoned struct{}

// Ask blocks the body until a handler answers. If the rest was dropped it panics, so the body's deferred
// calls run on the way out.
func (r *Run) Ask(op any) any {
	r.toHandler <- op
	got := <-r.toBody
	if got.abandoned {
		panic(abandoned{})
	}
	return got.answer
}

// A Handler answers by returning (answer, true); (nil, false) is "not mine". It may ask whoever is outside.
// It leaves by panicking with a leave, which says what the program under it has come to. Done is what it
// makes of a result that reaches it.
type Handler interface {
	On(op any, outer func(any) any) (any, bool)
	Done(a any) any
}
type leave struct {
	by     Handler
	result any
}

// handlers are listed innermost first
func answer(handlers []Handler, op any) any {
	for i, h := range handlers {
		rest := handlers[i+1:]
		if x, mine := h.On(op, func(o any) any { return answer(rest, o) }); mine {
			return x
		}
	}
	panic(fmt.Sprintf("nobody answers %v", op))
}

// a result on its way out: what each of these handlers, innermost first, makes of it
func ended(handlers []Handler, a any) any {
	for _, h := range handlers {
		a = h.Done(a)
	}
	return a
}

// run is `body under handlers[0] under handlers[1] ...`. The value passes every Done; the result of a handler
// that left passes only those outside it.
func run(handlers []Handler, body func(*Run) any) (result any) {
	r := &Run{make(chan any), make(chan reply)}
	gone := make(chan struct{})
	go func() {
		defer close(gone)
		defer func() {
			if p := recover(); p != nil {
				if _, dropped := p.(abandoned); !dropped {
					panic(p)
				}
			}
		}()
		r.toHandler <- finished{body(r)}
	}()
	defer func() { // a handler left, or nobody answered: drop the rest, wait for the body to unwind
		if p := recover(); p != nil {
			r.toBody <- reply{abandoned: true}
			<-gone
			l, left := p.(leave)
			if !left {
				panic(p)
			}
			result = ended(handlers[slices.Index(handlers, l.by)+1:], l.result)
		}
	}()
	for {
		switch m := (<-r.toHandler).(type) {
		case finished:
			return ended(handlers, m.value)
		default:
			r.toBody <- reply{answer: answer(handlers, m)} // the one resumption
		}
	}
}

// ---- the handlers of the chapter

// held is a value with the cell's content after it
type held struct {
	value any
	cell  int64
}

func (h held) String() string { return fmt.Sprintf("%v, cell %d", h.value, h.cell) }

type Cell struct{ s int64 }

func (c *Cell) On(op any, _ func(any) any) (any, bool) {
	switch o := op.(type) {
	case Get:
		return c.s, true
	case Put:
		c.s = o.N
		return nil, true
	}
	return nil, false
}
func (c *Cell) Done(a any) any { return held{a, c.s} }

type paid struct{ value any }
type refused struct{ why string }

func (p paid) String() string    { return fmt.Sprintf("paid(%v)", p.value) }
func (r refused) String() string { return "refused(" + r.why + ")" }

// zero resumptions: it leaves, and the rest is dropped
type Refusals struct{}

func (h *Refusals) On(op any, _ func(any) any) (any, bool) {
	if r, ok := op.(Refuse); ok {
		panic(leave{h, refused{r.Why}})
	}
	return nil, false
}
func (*Refusals) Done(a any) any { return paid{a} }

type FixedClock struct{ now int64 }

func (c *FixedClock) On(op any, _ func(any) any) (any, bool) {
	if _, ok := op.(Now); ok {
		return c.now, true
	}
	return nil, false
}
func (*FixedClock) Done(a any) any { return a }

// logged is a value with the questions of the state that were asked on the way to it
type logged struct {
	lines []string
	value any
}

func (l logged) String() string { return fmt.Sprintf("%s => %v", strings.Join(l.lines, " "), l.value) }

// the observer, an interceptor: every question of the state asked again outward, written down with its answer
type Log struct{ lines []string }

func (l *Log) On(op any, outer func(any) any) (any, bool) {
	switch op.(type) {
	case Get:
		x := outer(op)
		l.lines = append(l.lines, fmt.Sprintf("Get=%v", x))
		return x, true
	case Put:
		x := outer(op)
		l.lines = append(l.lines, fmt.Sprint(op))
		return x, true
	}
	return nil, false
}
func (l *Log) Done(a any) any { return logged{l.lines, a} }

// ---- the programs of the twelve lines: ordinary Go, top to bottom. The answers are `any`, as in bank.go

func get(r *Run) int64 { return r.Ask(Get{}).(int64) }
func put(r *Run, n int64) int64 {
	r.Ask(Put{n})
	return n
}
func bump(r *Run) any {
	x := get(r)
	r.Ask(Put{x + 1})
	return x
}
func stopped(r *Run) any {
	r.Ask(Put{1})
	x := get(r)
	r.Ask(Refuse{fmt.Sprintf("stop at %d", x)})
	return put(r, 9)
}
func lost(r *Run) any {
	r.Ask(Put{1})
	r.Ask(Refuse{"no"})
	return int64(0)
}
func timed(r *Run) any {
	t := r.Ask(Now{}).(int64)
	r.Ask(Put{t})
	return get(r) + t
}

// what a program over the state is seen to do: its questions with their answers, its value, the cell after
func seen(p func(*Run) any) any { return run([]Handler{&Log{}, &Cell{0}}, p) }

// `p under Cell(s)`
func cell(s int64, p func(*Run) any) held { return run([]Handler{&Cell{s}}, p).(held) }

func main() {
	// 1-3. sequencing: `done` is a value in hand, "then" is the next statement, grouping is where a call ends
	fmt.Printf("1 done then f: %v = %v\n",
		seen(func(r *Run) any { x := int64(5); return put(r, x) }), seen(func(r *Run) any { return put(r, 5) }))
	fmt.Printf("2 p then done: %v = %v\n", seen(func(r *Run) any { x := bump(r); return x }), seen(bump))
	f := func(r *Run, x int64) int64 { return put(r, x+1) }
	g := func(r *Run, y int64) int64 { r.Ask(Put{y * 2}); return y }
	getThenF := func(r *Run) int64 { return f(r, get(r)) }
	fThenG := func(r *Run, x int64) int64 { return g(r, f(r, x)) }
	fmt.Printf("3 grouping: %v = %v\n", seen(func(r *Run) any { return g(r, getThenF(r)) }),
		seen(func(r *Run) any { x := get(r); return fThenG(r, x) }))

	// 4-5. a handler is a fold: what it makes of a value, and of one program after another
	seven := func(*Run) any { return int64(7) }
	fmt.Printf("4 handling done: %v | %v\n", cell(3, seven), run([]Handler{&Refusals{}}, seven))
	whole := cell(0, func(r *Run) any { return bump(r).(int64) + bump(r).(int64) })
	first := cell(0, bump)
	second := cell(first.cell, bump)
	pieces := held{first.value.(int64) + second.value.(int64), second.cell}
	fmt.Printf("5 handling a sequence: %v = %v\n", whole, pieces)

	// 6. a handler sees every operation of its effect, in order, and nothing of another's
	fmt.Printf("6 its own and no other's: %v\n", run([]Handler{&Refusals{}, &Log{}, &Cell{0}}, stopped))

	// 7. a question nobody answers: the program has no value, and the run says which question
	unanswered := func() (said string) {
		defer func() {
			if p := recover(); p != nil {
				said = strings.TrimPrefix(fmt.Sprint(p), "nobody answers ")
			}
		}()
		return fmt.Sprintf("it ran: %v", cell(0, timed))
	}()
	fmt.Printf("7 nobody answers: %s\n", unanswered)

	// 8. two effects whose order is the meaning: is the cell part of what a refusal throws away?
	fmt.Printf("8 state then failure: %v | failure then state: %v\n",
		run([]Handler{&Cell{0}, &Refusals{}}, lost), run([]Handler{&Refusals{}, &Cell{0}}, lost))

	// 9. two handlers that only answer: either order
	fmt.Printf("9 two that only answer: %v = %v\n",
		run([]Handler{&FixedClock{7}, &Cell{0}}, timed), run([]Handler{&Cell{0}, &FixedClock{7}}, timed))

	// 10-11. the rest resumed twice: a handler here returns one answer, and there is one goroutine to give it to
	once := "cannot: the rest is a blocked goroutine; a goroutine cannot be copied, so it is resumed once"
	fmt.Println("10 twice, state inside: " + once)
	fmt.Println("11 twice, state outside: " + once)

	// 12. what this handler owes its own operations: the four equations of a state
	afterPut := cell(0, func(r *Run) any { r.Ask(Put{3}); return get(r) })
	lastPut := cell(0, func(r *Run) any { r.Ask(Put{3}); r.Ask(Put{4}); return get(r) })
	twice := cell(0, func(r *Run) any { x, y := get(r), get(r); return fmt.Sprintf("%d=%d", x, y) })
	putBack := cell(5, func(r *Run) any { return put(r, get(r)) })
	fmt.Printf("12 the cell's own: %v %v %v cell %d\n", afterPut.value, lastPut.value, twice.value, putBack.cell)
}
