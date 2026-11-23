{-# LANGUAGE FlexibleInstances #-}
{-# LANGUAGE GeneralizedNewtypeDeriving #-}
-- The scenario of code/expected.txt in Haskell, in the FINAL ENCODING (tagless final, chapter 3c): there is no
-- tree. An effect is a type class over an abstract monad `m`; the program is a function polymorphic in `m`; a
-- handler is an instance. The rest of the program is whatever `m`'s bind makes of it: once for a state monad,
-- several times for the list monad.
-- `runghc code/haskell/Tagless.hs`
module Main (main) where

import Control.Monad (when)
import Control.Monad.Except (ExceptT, runExceptT, throwError)
import Control.Monad.Reader (ReaderT, ask, runReaderT)
import Control.Monad.State (State, gets, modify, runState)
import Control.Monad.Trans (lift)
import Data.List (intercalate)
import qualified Data.Map as Map

class Monad m => Bank m where
  balance :: String -> m Integer
  move :: String -> String -> Integer -> m ()

class Monad m => Clock m where
  now :: m Integer

class Monad m => Failure m where
  refuse :: String -> m a

-- | the program: compiled against whichever `m` is chosen
pay :: (Bank m, Clock m, Failure m) => String -> String -> Integer -> m String
pay from to amount = do
  have <- balance from
  when (have < amount) (refuse (from ++ " has " ++ show have ++ ", needs " ++ show amount))
  move from to amount
  at <- now
  pure ("moved " ++ show amount ++ " from " ++ from ++ " to " ++ to ++ " at " ++ show at)

-- ---- a test's world: balances and a trail in a state, failure ABOVE the state, so both survive a refusal.
-- That order is a decision taken here, in the type, once for every program run in `Test`.

data World = World {balances :: Map.Map String Integer, trail :: [String]}

type Test = ExceptT String (State World)

instance Bank Test where
  balance account = gets (Map.findWithDefault 0 account . balances)
  move from to n =
    modify (\w -> w {balances = Map.insertWith (+) to n (Map.insertWith (+) from (negate n) (balances w))})

instance Clock Test where
  now = pure 42

instance Failure Test where
  refuse = throwError

-- ---- interceptors are monads that wrap a monad. Each must say how EVERY other effect passes through it.

class Monad m => Trail m where
  note :: String -> m ()

instance Trail Test where
  note line = modify (\w -> w {trail = trail w ++ [line]})

newtype Journaled m a = Journaled {journaled :: m a}
  deriving (Functor, Applicative, Monad, Clock, Failure)

instance (Bank m, Trail m) => Bank (Journaled m) where
  balance account = Journaled $ do
    n <- balance account
    note ("Balance(" ++ account ++ ") -> " ++ show n)
    pure n
  move from to n = Journaled $ do
    move from to n
    note ("Move(" ++ from ++ "," ++ to ++ "," ++ show n ++ ") -> ()")

newtype Limited m a = Limited (ReaderT Integer m a)
  deriving (Functor, Applicative, Monad)

instance (Bank m, Failure m) => Bank (Limited m) where
  balance = Limited . lift . balance
  move from to n = Limited $ do
    most <- ask
    if n > most
      then lift (refuse ("a move of " ++ show n ++ " is over the limit of " ++ show most))
      else lift (move from to n)

instance Clock m => Clock (Limited m) where
  now = Limited (lift now)

instance Failure m => Failure (Limited m) where
  refuse = Limited . lift . refuse

limited :: Integer -> Limited m a -> m a
limited most (Limited body) = runReaderT body most

-- ---- many resumptions: a monad whose bind runs the rest once per element. Nothing in `pay` changes.

type WhatIf = ExceptT String []

instance Bank WhatIf where
  balance _ = lift [100, 10]
  move _ _ _ = pure ()

instance Clock WhatIf where
  now = pure 42

instance Failure WhatIf where
  refuse = throwError

test :: Integer -> Test a -> (Either String a, World)
test ann body = runState (runExceptT body) (World (Map.fromList [("ann", ann)]) [])

outcome :: Either String String -> String
outcome = either ("refused: " ++) ("paid: " ++)

shown :: World -> String
shown w = "ann=" ++ count "ann" ++ " bob=" ++ count "bob"
  where
    count who = show (Map.findWithDefault 0 who (balances w))

main :: IO ()
main = do
  let (paid, rich) = test 100 (pay "ann" "bob" 30)
  putStrLn ("1 " ++ outcome paid)
  putStrLn ("1 balances: " ++ shown rich)

  let (refused, poor) = test 10 (pay "ann" "bob" 30)
  putStrLn ("2 " ++ outcome refused)
  putStrLn ("2 balances: " ++ shown poor)

  let (_, written) = test 100 (journaled (pay "ann" "bob" 30))
  mapM_ (putStrLn . ("3 journal: " ++)) (trail written)

  let (over, kept) = test 100 (limited 50 (pay "ann" "bob" 80))
  putStrLn ("4 " ++ outcome over)
  putStrLn ("4 balances: " ++ shown kept)

  let (_, seen) = test 10 (journaled (pay "ann" "bob" 30))
  let kept5 = trail seen
  putStrLn ("5 left early: the journal has " ++ show (length kept5) ++ " line, " ++ intercalate "; " kept5)

  let outcomes = zip [100 :: Integer, 10] (runExceptT (pay "ann" "bob" 30) :: [Either String String])
  putStrLn ("6 twice: " ++ intercalate " | " [show s ++ " -> " ++ either ("refused: " ++) id r | (s, r) <- outcomes])
