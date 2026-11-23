{-# LANGUAGE GADTs #-}
-- The laws of code/laws-expected.txt in Haskell, as PROGRAMS AS DATA (chapter 4, by the technique of chapter
-- 3b): the tree of two cases over a GADT of operations, as in Bank.hs. Each line runs both sides of a law and
-- prints what was seen; a law that failed here would print two different halves. No handler has a variable:
-- what a handler remembers is in what it returns.
-- `runghc code/haskell/Laws.hs`
module Main (main) where

import Control.Exception (ErrorCall (..), evaluate, try)
import Data.List (intercalate, stripPrefix)
import Data.Maybe (fromMaybe)
import Data.Void (Void, absurd)

-- | the vocabulary of the chapter: a state, a failure, a clock, a choice
data Op x where
  Get :: Op Integer
  Put :: Integer -> Op ()
  Refuse :: String -> Op Void
  Now :: Op Integer
  Choose :: [Integer] -> Op Integer

-- | a program: done, or a question with the rest
data Prog a where
  Done :: a -> Prog a
  Ask :: Op x -> (x -> Prog a) -> Prog a

instance Functor Prog where
  fmap f p = p >>= Done . f

instance Applicative Prog where
  pure = Done
  pf <*> pa = pf >>= \f -> fmap f pa

instance Monad Prog where
  Done a >>= f = f a
  Ask op rest >>= f = Ask op (\x -> rest x >>= f)

ask :: Op x -> Prog x
ask op = Ask op Done

-- handlers: each takes a program to a program with one effect answered. `p under h` is `h p`; the last
-- equation of each is "not mine".

-- | the state: the cell's content goes down the tree with the handler and comes back beside the result
cell :: Integer -> Prog a -> Prog (a, Integer)
cell s (Done a) = Done (a, s)
cell s (Ask Get rest) = cell s (rest s)
cell _ (Ask (Put n) rest) = cell n (rest ())
cell s (Ask op rest) = Ask op (cell s . rest)

-- | zero resumptions: the rest is not called
refusals :: Prog a -> Prog (Either String a)
refusals (Done a) = Done (Right a)
refusals (Ask (Refuse why) _) = Done (Left why)
refusals (Ask op rest) = Ask op (refusals . rest)

fixedClock :: Integer -> Prog a -> Prog a
fixedClock _ (Done a) = Done a
fixedClock at (Ask Now rest) = fixedClock at (rest at)
fixedClock at (Ask op rest) = Ask op (fixedClock at . rest)

-- | every option taken: the rest once for each, in order
allChoices :: Prog a -> Prog [a]
allChoices (Done a) = Done [a]
allChoices (Ask (Choose options) rest) = concat <$> mapM (allChoices . rest) options
allChoices (Ask op rest) = Ask op (allChoices . rest)

-- | the observer: every question of the state asked again, and written down with its answer
logged :: Prog a -> Prog ([String], a)
logged (Done a) = Done ([], a)
logged (Ask Get rest) = Ask Get (\x -> noted ("Get=" ++ show x) (logged (rest x)))
logged (Ask (Put n) rest) = Ask (Put n) (\x -> noted ("Put(" ++ show n ++ ")") (logged (rest x)))
logged (Ask op rest) = Ask op (logged . rest)

noted :: String -> Prog ([String], a) -> Prog ([String], a)
noted line = fmap (\(later, a) -> (line : later, a))

run :: Prog a -> a
run (Done a) = a
run (Ask op _) = error ("nobody answers " ++ shown op)

shown :: Op x -> String
shown Get = "Get"
shown (Put n) = "Put(" ++ show n ++ ")"
shown (Refuse why) = "Refuse(" ++ why ++ ")"
shown Now = "Now"
shown (Choose options) = "Choose(" ++ show options ++ ")"

-- ---- the programs of the twelve lines

put :: Integer -> Prog Integer
put n = n <$ ask (Put n)

bump, stopped, lost, timed, forked :: Prog Integer
bump = do x <- ask Get; ask (Put (x + 1)); pure x
stopped = do ask (Put 1); x <- ask Get; _ <- ask (Refuse ("stop at " ++ show x)); put 9
lost = do ask (Put 1); absurd <$> ask (Refuse "no")
timed = do t <- ask Now; ask (Put t); x <- ask Get; pure (x + t)
forked = do ask (Put 1); x <- ask (Choose [10, 20]); y <- ask Get; ask (Put (y + x)); ask Get

held :: (Integer, Integer) -> String
held (a, s) = show a ++ ", cell " ++ show s

outcome :: Either String String -> String
outcome = either (\why -> "refused(" ++ why ++ ")") (\a -> "paid(" ++ a ++ ")")

-- | what a program over the state is seen to do: its questions with their answers, its value, the cell after
seen :: Prog Integer -> String
seen p = unwords written ++ " => " ++ held (a, s)
  where
    ((written, a), s) = run (cell 0 (logged p))

main :: IO ()
main = do
  -- 1-3. sequencing: `pure` is a unit on each side, and grouping does not matter
  putStrLn ("1 done then f: " ++ seen (pure 5 >>= put) ++ " = " ++ seen (put 5))
  putStrLn ("2 p then done: " ++ seen (bump >>= pure) ++ " = " ++ seen bump)
  let f x = put (x + 1)
      g y = y <$ ask (Put (y * 2))
  putStrLn ("3 grouping: " ++ seen ((ask Get >>= f) >>= g) ++ " = " ++ seen (ask Get >>= \x -> f x >>= g))

  -- 4-5. a handler is a fold: what it makes of `Done`, and of one program after another
  putStrLn ("4 handling done: " ++ held (run (cell 3 (pure 7))) ++ " | "
    ++ outcome (show <$> run (refusals (pure (7 :: Integer)))))
  let whole = cell 0 (do a <- bump; b <- bump; pure (a + b))
      pieces = do (a, s) <- cell 0 bump; (b, s2) <- cell s bump; pure (a + b, s2)
  putStrLn ("5 handling a sequence: " ++ held (run whole) ++ " = " ++ held (run pieces))

  -- 6. a handler sees every operation of its effect, in order, and nothing of another's
  let ((written, r), s) = run (cell 0 (logged (refusals stopped)))
  putStrLn ("6 its own and no other's: " ++ unwords written ++ " => " ++ outcome (show <$> r) ++ ", cell " ++ show s)

  -- 7. a question nobody answers: the types let it through (`Prog a` does not say what is asked), `run` stops
  unanswered <- try (evaluate (run (cell 0 timed)))
  let named (ErrorCall message) = fromMaybe message (stripPrefix "nobody answers " message)
  putStrLn ("7 nobody answers: " ++ either named (const "it ran") unanswered)

  -- 8. two effects whose order is the meaning: is the cell part of what a refusal throws away?
  let inside = outcome (held <$> run (refusals (cell 0 lost)))
      (refused, after) = run (cell 0 (refusals lost))
  putStrLn ("8 state then failure: " ++ inside ++ " | failure then state: " ++ outcome (show <$> refused)
    ++ ", cell " ++ show after)

  -- 9. two handlers that only answer: either order
  putStrLn ("9 two that only answer: " ++ held (run (cell 0 (fixedClock 7 timed))) ++ " = "
    ++ held (run (fixedClock 7 (cell 0 timed))))

  -- 10-11. the rest resumed twice: a state handled inside the choice is each path's own, outside it is one
  putStrLn ("10 twice, state inside: " ++ intercalate " | " (map held (run (allChoices (cell 0 forked)))))
  let (paths, final) = run (cell 0 (allChoices forked))
  putStrLn ("11 twice, state outside: " ++ intercalate " | " (map show paths) ++ ", cell " ++ show final)

  -- 12. what this handler owes its own operations: the four equations of a state
  let afterPut = fst (run (cell 0 (ask (Put 3) >> ask Get)))
      lastPut = fst (run (cell 0 (ask (Put 3) >> ask (Put 4) >> ask Get)))
      (x, y) = fst (run (cell 0 (do x' <- ask Get; y' <- ask Get; pure (x', y'))))
      putBack = snd (run (cell 5 (ask Get >>= ask . Put)))
  putStrLn ("12 the cell's own: " ++ show afterPut ++ " " ++ show lastPut ++ " " ++ show x ++ "=" ++ show y
    ++ " cell " ++ show putBack)
