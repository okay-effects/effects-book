{-# LANGUAGE GADTs #-}
-- The scenario of code/expected.txt in Haskell, as PROGRAMS AS DATA (chapter 3b): the tree of two cases, with
-- the operations a GADT indexed by the type of the answer. Matching on an operation tells the compiler what its
-- answer is, so there is no cast anywhere; and with no mutable state to lean on, a handler that keeps something
-- returns it beside the result.
-- `runghc code/haskell/Bank.hs`
module Main (main) where

import Data.List (intercalate)
import qualified Data.Map as Map
import Data.Void (Void, absurd)

-- | an operation: a question as data, indexed by the type of its answer
data Op x where
  Balance :: String -> Op Integer
  Move :: String -> String -> Integer -> Op ()
  Now :: Op Integer
  Refuse :: String -> Op Void

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

-- | the program
pay :: String -> String -> Integer -> Prog String
pay from to amount = do
  have <- ask (Balance from)
  if have < amount
    then absurd <$> ask (Refuse (from ++ " has " ++ show have ++ ", needs " ++ show amount))
    else pure ()
  ask (Move from to amount)
  at <- ask Now
  pure ("moved " ++ show amount ++ " from " ++ from ++ " to " ++ to ++ " at " ++ show at)

-- handlers: each takes a program to a program with one effect answered. The last equation of each is "not
-- mine": the question stays a question, with the handled rest behind it.

type Balances = Map.Map String Integer

accounts :: Balances -> Prog a -> Prog (a, Balances)
accounts b (Done a) = Done (a, b)
accounts b (Ask (Balance account) rest) = accounts b (rest (Map.findWithDefault 0 account b))
accounts b (Ask (Move from to n) rest) =
  accounts (Map.insertWith (+) to n (Map.insertWith (+) from (negate n) b)) (rest ())
accounts b (Ask op rest) = Ask op (accounts b . rest)

fixedClock :: Integer -> Prog a -> Prog a
fixedClock _ (Done a) = Done a
fixedClock at (Ask Now rest) = fixedClock at (rest at)
fixedClock at (Ask op rest) = Ask op (fixedClock at . rest)

-- | zero resumptions: the rest is not called
refusals :: Prog a -> Prog (Either String a)
refusals (Done a) = Done (Right a)
refusals (Ask (Refuse why) _) = Done (Left why)
refusals (Ask op rest) = Ask op (refusals . rest)

-- | an interceptor: the question is asked again for whoever is outside, and the answer written down
journal :: Prog a -> Prog (a, [String])
journal (Done a) = Done (a, [])
journal (Ask op@(Balance _) rest) = Ask op (\x -> noted (shown op ++ " -> " ++ show x) (journal (rest x)))
journal (Ask op@Move {} rest) = Ask op (\x -> noted (shown op ++ " -> ()") (journal (rest x)))
journal (Ask op rest) = Ask op (journal . rest)

noted :: String -> Prog (a, [String]) -> Prog (a, [String])
noted line = fmap (\(a, later) -> (a, line : later))

limit :: Integer -> Prog a -> Prog a
limit _ (Done a) = Done a
limit most (Ask (Move _ _ n) _)
  | n > most = absurd <$> ask (Refuse ("a move of " ++ show n ++ " is over the limit of " ++ show most))
limit most (Ask op rest) = Ask op (limit most . rest)

-- | many resumptions: at Balance the rest is called once per supposed answer
whatIf :: [Integer] -> Prog a -> Prog [(Integer, a)]
whatIf _ (Done a) = Done [(0, a)]
whatIf suppose (Ask (Balance _) rest) =
  concat <$> mapM (\s -> map (\(_, a) -> (s, a)) <$> whatIf suppose (rest s)) suppose
whatIf suppose (Ask op rest) = Ask op (whatIf suppose . rest)

-- | a bank that moves nothing
dryRun :: Prog a -> Prog a
dryRun (Done a) = Done a
dryRun (Ask Move {} rest) = dryRun (rest ())
dryRun (Ask op rest) = Ask op (dryRun . rest)

run :: Prog a -> a
run (Done a) = a
run (Ask op _) = error ("nobody answers " ++ shown op)

shown :: Op x -> String
shown (Balance account) = "Balance(" ++ account ++ ")"
shown (Move from to n) = "Move(" ++ from ++ "," ++ to ++ "," ++ show n ++ ")"
shown Now = "Now"
shown (Refuse why) = "Refuse(" ++ why ++ ")"

outcome :: Either String String -> String
outcome = either ("refused: " ++) ("paid: " ++)

balances :: Balances -> String
balances b = "ann=" ++ show (Map.findWithDefault 0 "ann" b) ++ " bob=" ++ show (Map.findWithDefault 0 "bob" b)

edge :: Integer -> Prog a -> (a, Balances)
edge ann = run . accounts (Map.fromList [("ann", ann)]) . fixedClock 42

main :: IO ()
main = do
  let (paid, rich) = edge 100 (refusals (pay "ann" "bob" 30))
  putStrLn ("1 " ++ outcome paid)
  putStrLn ("1 balances: " ++ balances rich)

  let (refused, poor) = edge 10 (refusals (pay "ann" "bob" 30))
  putStrLn ("2 " ++ outcome refused)
  putStrLn ("2 balances: " ++ balances poor)

  let ((_, linesOfThree), _) = edge 100 (journal (refusals (pay "ann" "bob" 30)))
  mapM_ (putStrLn . ("3 journal: " ++)) linesOfThree

  let (over, limited) = edge 100 (refusals (limit 50 (pay "ann" "bob" 80)))
  putStrLn ("4 " ++ outcome over)
  putStrLn ("4 balances: " ++ balances limited)

  let ((_, seen), _) = edge 10 (journal (refusals (pay "ann" "bob" 30)))
  putStrLn ("5 left early: the journal has " ++ show (length seen) ++ " line, " ++ intercalate "; " seen)

  let outcomes = run (whatIf [100, 10] (dryRun (fixedClock 42 (refusals (pay "ann" "bob" 30)))))
  putStrLn ("6 twice: " ++ intercalate " | " [show s ++ " -> " ++ either ("refused: " ++) id r | (s, r) <- outcomes])
