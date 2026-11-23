// The scenario of code/expected.txt in Go, in DIRECT STYLE ON GOROUTINES (chapter 3d): the program is plain
// code on its own goroutine, asking is a send and a receive on two channels, and the rest of the program is
// that goroutine, blocked. A goroutine cannot be copied. One resumption.
// `go run code/go/bank.go`
package main

import (
	"fmt"
	"strings"
)

type Balance struct{ Account string }
type Move struct {
	From, To string
	Amount   int64
}
type Now struct{}
type Refuse struct{ Why string }

func (b Balance) String() string { return "Balance(" + b.Account + ")" }
func (m Move) String() string    { return fmt.Sprintf("Move(%s,%s,%d)", m.From, m.To, m.Amount) }

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
type finished struct{ value string }
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

// the program: ordinary Go, top to bottom. The answers are `any`: Go cannot tie an answer's type to its question
func pay(r *Run, from, to string, amount int64) string {
	have := r.Ask(Balance{from}).(int64)
	if have < amount {
		r.Ask(Refuse{fmt.Sprintf("%s has %d, needs %d", from, have, amount)})
	}
	r.Ask(Move{from, to, amount})
	at := r.Ask(Now{}).(int64)
	return fmt.Sprintf("moved %d from %s to %s at %d", amount, from, to, at)
}

// A Handler answers by returning (answer, true); (nil, false) is "not mine". It may ask whoever is outside.
// It leaves by panicking with a leave.
type Handler func(op any, outer func(any) any) (any, bool)
type leave struct{ result string }

// handlers are listed innermost first
func answer(handlers []Handler, op any) any {
	for i, h := range handlers {
		rest := handlers[i+1:]
		if x, mine := h(op, func(o any) any { return answer(rest, o) }); mine {
			return x
		}
	}
	panic(fmt.Sprintf("nobody answers %v", op))
}

func run(handlers []Handler, body func(*Run) string) (result string) {
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
	defer func() { // a handler left: drop the rest, wait for the body to unwind
		if p := recover(); p != nil {
			l, left := p.(leave)
			if !left {
				panic(p)
			}
			r.toBody <- reply{abandoned: true}
			<-gone
			result = "refused: " + l.result
		}
	}()
	for {
		switch m := (<-r.toHandler).(type) {
		case finished:
			return "paid: " + m.value
		default:
			r.toBody <- reply{answer: answer(handlers, m)} // the one resumption
		}
	}
}

type Accounts map[string]int64

func (b Accounts) Handle(op any, _ func(any) any) (any, bool) {
	switch o := op.(type) {
	case Balance:
		return b[o.Account], true
	case Move:
		b[o.From] -= o.Amount
		b[o.To] += o.Amount
		return nil, true
	}
	return nil, false
}
func (b Accounts) String() string { return fmt.Sprintf("ann=%d bob=%d", b["ann"], b["bob"]) }

func clock(op any, _ func(any) any) (any, bool) {
	if _, ok := op.(Now); ok {
		return int64(42), true
	}
	return nil, false
}

func refusals(op any, _ func(any) any) (any, bool) {
	if r, ok := op.(Refuse); ok {
		panic(leave{r.Why})
	}
	return nil, false
}

type Journal struct{ lines []string }

func (j *Journal) Handle(op any, outer func(any) any) (any, bool) {
	switch op.(type) {
	case Balance, Move:
		x := outer(op)
		shown := "()"
		if x != nil {
			shown = fmt.Sprint(x)
		}
		j.lines = append(j.lines, fmt.Sprintf("%v -> %s", op, shown))
		return x, true
	}
	return nil, false
}

func limit(most int64) Handler {
	return func(op any, outer func(any) any) (any, bool) {
		if m, ok := op.(Move); ok && m.Amount > most {
			return outer(Refuse{fmt.Sprintf("a move of %d is over the limit of %d", m.Amount, most)}), true
		}
		return nil, false
	}
}

func main() {
	payment := func(amount int64) func(*Run) string {
		return func(r *Run) string { return pay(r, "ann", "bob", amount) }
	}

	rich := Accounts{"ann": 100}
	fmt.Println("1 " + run([]Handler{refusals, clock, rich.Handle}, payment(30)))
	fmt.Printf("1 balances: %v\n", rich)

	poor := Accounts{"ann": 10}
	fmt.Println("2 " + run([]Handler{refusals, clock, poor.Handle}, payment(30)))
	fmt.Printf("2 balances: %v\n", poor)

	journal := &Journal{}
	run([]Handler{journal.Handle, refusals, clock, Accounts{"ann": 100}.Handle}, payment(30))
	for _, line := range journal.lines {
		fmt.Println("3 journal: " + line)
	}

	limited := Accounts{"ann": 100}
	fmt.Println("4 " + run([]Handler{limit(50), refusals, clock, limited.Handle}, payment(80)))
	fmt.Printf("4 balances: %v\n", limited)

	seen := &Journal{}
	run([]Handler{seen.Handle, refusals, clock, Accounts{"ann": 10}.Handle}, payment(30))
	fmt.Printf("5 left early: the journal has %d line, %s\n", len(seen.lines), strings.Join(seen.lines, "; "))

	fmt.Println("6 twice: cannot: the rest is a blocked goroutine; a goroutine cannot be copied, " +
		"so it is resumed once")
}
