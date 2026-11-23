(* The laws of code/laws-expected.txt in OCaml 5, with the language's OWN EFFECT HANDLERS (chapter 4, by the
   technique of chapter 3f), as in bank.ml. A program is a plain function that performs; `p under h` is
   `h (fun () -> p ())`. Sequencing is OCaml's own `let`, so lines 1 to 3 are laws of `let`, seen through a
   handler. No handler here has a variable: the cell's content is passed along and comes back beside the result.
   `ocaml code/ocaml/laws.ml` (OCaml 5.3 or later, for the `effect` patterns) *)

open Effect
open Effect.Deep

(* the vocabulary of the chapter: a state, a failure, a clock, a choice *)
type _ Effect.t +=
  | Get : int Effect.t
  | Put : int -> unit Effect.t
  | Refuse : string -> 'a Effect.t
  | Now : int Effect.t
  | Choose : int list -> int Effect.t

(* the state: a handled program is a function from the cell's content to the result and the content after *)
let cell (type a) (s : int) (body : unit -> a) : a * int =
  let from : int -> a * int =
    match body () with
    | result -> fun s -> (result, s)
    | effect Get, k -> fun s -> continue k s s
    | effect Put n, k -> fun _ -> continue k () n
  in
  from s

(* zero resumptions; `discontinue` lets the rest release what it holds on the way out *)
exception Left_early

let drop k = try ignore (discontinue k Left_early) with Left_early -> ()

let refusals (type a) (body : unit -> a) : (a, string) result =
  match body () with
  | result -> Ok result
  | effect Refuse why, k -> drop k; Error why

let fixed_clock (type a) (at : int) (body : unit -> a) : a =
  match body () with
  | result -> result
  | effect Now, k -> continue k at

(* every option taken: the rest once for each, in order. That is what it asks of `k` *)
let all_choices (type a) (body : unit -> a) : a list =
  match body () with
  | result -> [ result ]
  | effect Choose options, k -> List.concat_map (fun o -> continue k o) options

(* the observer: every question of the state performed again, and written down with its answer *)
let log (type a) (body : unit -> a) : string list * a =
  match body () with
  | result -> ([], result)
  | effect Get, k ->
    let x = perform Get in
    let later, a = continue k x in
    (Printf.sprintf "Get=%d" x :: later, a)
  | effect Put n, k ->
    perform (Put n);
    let later, a = continue k () in
    (Printf.sprintf "Put(%d)" n :: later, a)

(* the programs of the twelve lines *)
let put n () = perform (Put n); n
let bump () = let x = perform Get in perform (Put (x + 1)); x
let stopped () =
  perform (Put 1);
  let x = perform Get in
  (perform (Refuse (Printf.sprintf "stop at %d" x)) : unit);
  put 9 ()
let lost () : int = perform (Put 1); perform (Refuse "no")
let timed () = let t = perform Now in perform (Put t); let x = perform Get in x + t
let forked () =
  perform (Put 1);
  let x = perform (Choose [ 10; 20 ]) in
  let y = perform Get in
  perform (Put (y + x));
  perform Get

let held (a, s) = Printf.sprintf "%d, cell %d" a s
let outcome = function Ok a -> "paid(" ^ a ^ ")" | Error why -> "refused(" ^ why ^ ")"
let number = Result.map string_of_int

(* what a program over the state is seen to do: its questions with their answers, its value, the cell after *)
let seen p =
  let (lines, a), s = cell 0 (fun () -> log p) in
  Printf.sprintf "%s => %s" (String.concat " " lines) (held (a, s))

let cannot = "cannot: the rest is a fibre, resumed once; a second `continue` raises an exception"

let () =
  (* 1-3. sequencing: a value bound and used, a program bound and returned, and grouping *)
  Printf.printf "1 done then f: %s = %s\n" (seen (fun () -> let x = 5 in put x ())) (seen (put 5));
  Printf.printf "2 p then done: %s = %s\n" (seen (fun () -> let x = bump () in x)) (seen bump);
  let f x = put (x + 1) () and g y = perform (Put (y * 2)); y in
  Printf.printf "3 grouping: %s = %s\n"
    (seen (fun () -> let y = (let x = perform Get in f x) in g y))
    (seen (fun () -> let x = perform Get in (let y = f x in g y)));

  (* 4-5. what a handler makes of a program that only returns, and of one program after another *)
  Printf.printf "4 handling done: %s | %s\n" (held (cell 3 (fun () -> 7))) (outcome (number (refusals (fun () -> 7))));
  let whole = cell 0 (fun () -> let a = bump () in let b = bump () in a + b) in
  let pieces = let a, s = cell 0 bump in let b, s2 = cell s bump in (a + b, s2) in
  Printf.printf "5 handling a sequence: %s = %s\n" (held whole) (held pieces);

  (* 6. a handler sees every operation of its effect, in order, and nothing of another's *)
  let (lines, r), s = cell 0 (fun () -> log (fun () -> refusals stopped)) in
  Printf.printf "6 its own and no other's: %s => %s, cell %d\n" (String.concat " " lines) (outcome (number r)) s;

  (* 7. a question nobody answers: the type of `timed`, unit -> int, says nothing of the clock; the runtime
     raises Unhandled with the question in it *)
  let unanswered = match cell 0 timed with _ -> "it ran" | exception Unhandled Now -> "Now" in
  Printf.printf "7 nobody answers: %s\n" unanswered;

  (* 8. two effects whose order is the meaning: is the cell part of what a refusal throws away? *)
  let inside = outcome (Result.map held (refusals (fun () -> cell 0 lost))) in
  let refused, after = cell 0 (fun () -> refusals lost) in
  Printf.printf "8 state then failure: %s | failure then state: %s, cell %d\n" inside (outcome (number refused)) after;

  (* 9. two handlers that only answer: either order *)
  Printf.printf "9 two that only answer: %s = %s\n"
    (held (cell 0 (fun () -> fixed_clock 7 timed)))
    (held (fixed_clock 7 (fun () -> cell 0 timed)));

  (* 10-11. the rest resumed twice. The attempt, made: `all_choices` continues the rest for the second option *)
  (match all_choices (fun () -> cell 0 forked) with
   | paths -> Printf.printf "10 twice, state inside: %s\n" (String.concat " | " (List.map held paths))
   | exception Continuation_already_resumed -> Printf.printf "10 twice, state inside: %s\n" cannot);
  (match cell 0 (fun () -> all_choices forked) with
   | paths, last ->
     Printf.printf "11 twice, state outside: %s, cell %d\n" (String.concat " | " (List.map string_of_int paths)) last
   | exception Continuation_already_resumed -> Printf.printf "11 twice, state outside: %s\n" cannot);

  (* 12. what this handler owes its own operations: the four equations of a state *)
  let after_put = fst (cell 0 (fun () -> perform (Put 3); perform Get)) in
  let last_put = fst (cell 0 (fun () -> perform (Put 3); perform (Put 4); perform Get)) in
  let x, y = fst (cell 0 (fun () -> let x = perform Get in let y = perform Get in (x, y))) in
  let put_back = snd (cell 5 (fun () -> perform (Put (perform Get)))) in
  Printf.printf "12 the cell's own: %d %d %d=%d cell %d\n" after_put last_put x y put_back
