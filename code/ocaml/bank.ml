(* The scenario of code/expected.txt in OCaml 5, with the language's OWN EFFECT HANDLERS (chapter 3f): nothing
   is encoded. `perform` asks; a handler is a `match` with `effect` cases and is given the rest as `k`. The
   rest is a fibre, a piece of stack, and may be resumed once.
   `ocaml code/ocaml/bank.ml` (OCaml 5.3 or later, for the `effect` patterns) *)

open Effect
open Effect.Deep

type _ Effect.t +=
  | Balance : string -> int Effect.t
  | Move : string * string * int -> unit Effect.t
  | Now : int Effect.t
  | Refuse : string -> 'a Effect.t

(* the program: plain OCaml; its type, string, says nothing of what it asks *)
let pay src dst amount () =
  let have = perform (Balance src) in
  if have < amount then perform (Refuse (Printf.sprintf "%s has %d, needs %d" src have amount));
  perform (Move (src, dst, amount));
  let at = perform Now in
  Printf.sprintf "moved %d from %s to %s at %d" amount src dst at

let of_ balances who = Option.value ~default:0 (Hashtbl.find_opt balances who)

let accounts (type a) (balances : (string, int) Hashtbl.t) (body : unit -> a) : a =
  match body () with
  | result -> result
  | effect Balance account, k -> continue k (of_ balances account)
  | effect Move (src, dst, n), k ->
    Hashtbl.replace balances src (of_ balances src - n);
    Hashtbl.replace balances dst (of_ balances dst + n);
    continue k ()

let fixed_clock (type a) (at : int) (body : unit -> a) : a =
  match body () with
  | result -> result
  | effect Now, k -> continue k at

(* zero resumptions. The rest is not simply forgotten: `discontinue` resumes it with an exception, so that
   whatever it holds is released on the way out *)
exception Left_early

let drop k = try ignore (discontinue k Left_early) with Left_early -> ()

let refusals (type a) (body : unit -> a) : (a, string) result =
  match body () with
  | receipt -> Ok receipt
  | effect Refuse why, k -> drop k; Error why

(* an interceptor: the question is performed again, for whoever is outside, and the answer written down *)
let journal (type a) (lines : string list ref) (body : unit -> a) : a =
  match body () with
  | result -> result
  | effect Balance account, k ->
    let n = perform (Balance account) in
    lines := Printf.sprintf "Balance(%s) -> %d" account n :: !lines;
    continue k n
  | effect Move (src, dst, n), k ->
    perform (Move (src, dst, n));
    lines := Printf.sprintf "Move(%s,%s,%d) -> ()" src dst n :: !lines;
    continue k ()

let limit (type a) (most : int) (body : unit -> a) : a =
  match body () with
  | result -> result
  | effect Move (src, dst, n), k ->
    if n > most then
      try perform (Refuse (Printf.sprintf "a move of %d is over the limit of %d" n most))
      with Left_early -> discontinue k Left_early
    else continue k (perform (Move (src, dst, n)))

let shown = function Ok receipt -> "paid: " ^ receipt | Error why -> "refused: " ^ why
let bank ann = let b = Hashtbl.create 2 in Hashtbl.replace b "ann" ann; b
let balances b = Printf.sprintf "ann=%d bob=%d" (of_ b "ann") (of_ b "bob")
let edge b body = accounts b (fun () -> fixed_clock 42 body)

let () =
  let rich = bank 100 in
  print_endline ("1 " ^ shown (edge rich (fun () -> refusals (pay "ann" "bob" 30))));
  print_endline ("1 balances: " ^ balances rich);

  let poor = bank 10 in
  print_endline ("2 " ^ shown (edge poor (fun () -> refusals (pay "ann" "bob" 30))));
  print_endline ("2 balances: " ^ balances poor);

  let lines = ref [] in
  ignore (edge (bank 100) (fun () -> refusals (fun () -> journal lines (pay "ann" "bob" 30))));
  List.iter (fun l -> print_endline ("3 journal: " ^ l)) (List.rev !lines);

  let limited = bank 100 in
  print_endline ("4 " ^ shown (edge limited (fun () -> refusals (fun () -> limit 50 (pay "ann" "bob" 80)))));
  print_endline ("4 balances: " ^ balances limited);

  let seen = ref [] in
  ignore (edge (bank 10) (fun () -> refusals (fun () -> journal seen (pay "ann" "bob" 30))));
  Printf.printf "5 left early: the journal has %d line, %s\n" (List.length !seen)
    (String.concat "; " (List.rev !seen));

  (* 6. the attempt, made: a handler that continues the rest a second time *)
  let twice (type a) (body : unit -> a) : a =
    match body () with
    | result -> result
    | effect Balance _, k -> ignore (continue k 100); continue k 10
  in
  let dry (type a) (body : unit -> a) : a =
    match body () with result -> result | effect Move _, k -> continue k ()
  in
  match twice (fun () -> dry (fun () -> fixed_clock 42 (fun () -> refusals (pay "ann" "bob" 30)))) with
  | _ -> print_endline "6 twice: the second resumption was not refused"
  | exception Continuation_already_resumed ->
    print_endline "6 twice: cannot: the rest is a fibre, resumed once; a second `continue` raises an exception"
