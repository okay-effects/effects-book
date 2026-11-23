{-# LANGUAGE FlexibleInstances #-}
-- MEANT NOT TO COMPILE (chapter 4, line 7 of code/laws-expected.txt for TaglessLaws.hs): the program `timed`
-- under Cell(0) and no handler for the clock. In the final encoding a question nobody answers is a constraint
-- no layer of the monad satisfies, and GHC says so before anything runs:
--   code/haskell/TaglessUnanswered.hs:39:27: error: [GHC-39999]
--       • No instance for ‘Clock Identity’ arising from a use of ‘timed’
-- With `fixedClock 7` between `cell 0` and `timed`, as on line 9 of TaglessLaws.hs, it compiles.
-- `ghc -fno-code code/haskell/TaglessUnanswered.hs` must fail.
module Main (main) where

import Control.Monad.State (StateT (..))
import Control.Monad.Trans (lift)
import Data.Functor.Identity (Identity, runIdentity)

class Monad m => State m where
  get :: m Integer
  put :: Integer -> m ()

class Monad m => Clock m where
  now :: m Integer

instance Monad m => State (StateT Integer m) where
  get = StateT (\s -> pure (s, s))
  put n = StateT (\_ -> pure ((), n))

instance Clock m => Clock (StateT s m) where
  now = lift now

cell :: Integer -> StateT Integer m a -> m (a, Integer)
cell s p = runStateT p s

run :: Identity a -> a
run = runIdentity

timed :: (State m, Clock m) => m Integer
timed = do t <- now; put t; x <- get; pure (x + t)

main :: IO ()
main = print (run (cell 0 timed))
