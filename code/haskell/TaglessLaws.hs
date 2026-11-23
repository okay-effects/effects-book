{-# LANGUAGE FlexibleInstances #-}
{-# LANGUAGE GeneralizedNewtypeDeriving #-}
-- The laws of code/laws-expected.txt in Haskell, in the FINAL ENCODING (chapter 4, by the technique of chapter
-- 3c): there is no tree, as in Tagless.hs. An effect is a type class over an abstract monad `m`; a program is
-- polymorphic in `m`; a handler is an instance, so "p under h" is p read at a monad that has h's instance, and
-- THE ORDER OF HANDLERS IS THE ORDER OF THE LAYERS of that monad: the handler nearest the program is the
-- outermost layer of the type and is taken off first. `cell 0 (refusals p)` is p under Refusals under Cell(0),
-- read at `ExceptT String (StateT Integer Identity)`. Each layer must also say how every other effect it is used
-- with passes through it.
-- `runghc code/haskell/TaglessLaws.hs`
module Main (main) where

import Control.Monad.Except (ExceptT, runExceptT, throwError)
import Control.Monad.Reader (ReaderT, ask, runReaderT)
import Control.Monad.State (StateT (..))
import Control.Monad.Trans (lift)
import Control.Monad.Writer (WriterT, runWriterT, tell)
import Data.Functor.Identity (Identity, runIdentity)
import Data.List (intercalate)

-- ---- the vocabulary of the chapter: a state, a failure, a clock, a choice

class Monad m => State m where
  get :: m Integer
  put :: Integer -> m ()

class Monad m => Failure m where
  refuse :: String -> m a

class Monad m => Clock m where
  now :: m Integer

class Monad m => Choice m where
  choose :: [Integer] -> m Integer

-- ---- Cell(s): a layer of state

cell :: Integer -> StateT Integer m a -> m (a, Integer)
cell s p = runStateT p s

instance Monad m => State (StateT Integer m) where
  get = StateT (\s -> pure (s, s))
  put n = StateT (\_ -> pure ((), n))

instance Failure m => Failure (StateT s m) where
  refuse = lift . refuse

instance Clock m => Clock (StateT s m) where
  now = lift now

instance Choice m => Choice (StateT s m) where
  choose = lift . choose

-- ---- Refusals: a layer of Either

refusals :: ExceptT String m a -> m (Either String a)
refusals = runExceptT

instance Monad m => Failure (ExceptT String m) where
  refuse = throwError

instance State m => State (ExceptT e m) where
  get = lift get
  put = lift . put

-- ---- FixedClock(t): a layer that carries t

fixedClock :: Integer -> ReaderT Integer m a -> m a
fixedClock at p = runReaderT p at

instance Monad m => Clock (ReaderT Integer m) where
  now = ask

instance State m => State (ReaderT r m) where
  get = lift get
  put = lift . put

-- ---- AllChoices: the list monad is a handler of Choice, but only as the innermost layer (the libraries have
-- no lawful list transformer). So that the choice can also be handled INSIDE a state, here is a layer written
-- by hand, and it is the notation's handler word for word: a computation is given the rest, `k`, and `choose`
-- calls it once per option, in order.

newtype Choices r m a = Choices ((a -> m [r]) -> m [r])

instance Functor (Choices r m) where
  fmap f (Choices c) = Choices (\k -> c (k . f))

instance Applicative (Choices r m) where
  pure a = Choices (\k -> k a)
  cf <*> ca = cf >>= \f -> fmap f ca

instance Monad (Choices r m) where
  Choices c >>= f = Choices (\k -> c (\a -> let Choices d = f a in d k))

instance Monad m => Choice (Choices r m) where
  choose options = Choices (\k -> concat <$> mapM k options)

instance State m => State (Choices r m) where
  get = Choices (get >>=)
  put n = Choices (put n >>=)

allChoices :: Monad m => Choices a m a -> m [a]
allChoices (Choices c) = c (\a -> pure [a])

-- ---- Log, an interceptor: a monad that wraps a monad, asks the wrapped one, and writes the answer down

newtype Logged m a = Logged (WriterT [String] m a)
  deriving (Functor, Applicative, Monad)

instance State m => State (Logged m) where
  get = Logged (lift get >>= \x -> x <$ tell ["Get=" ++ show x])
  put n = Logged (lift (put n) >> tell ["Put(" ++ show n ++ ")"])

logged :: Logged m a -> m (a, [String])
logged (Logged body) = runWriterT body

-- | running: only a program with no layer left has a value
run :: Identity a -> a
run = runIdentity

-- ---- the programs of the twelve lines: each names in its type the effects it asks, and no monad

store :: State m => Integer -> m Integer
store n = n <$ put n

bump :: State m => m Integer
bump = do x <- get; put (x + 1); pure x

stopped, lost :: (State m, Failure m) => m Integer
stopped = do put 1; x <- get; _ <- refuse ("stop at " ++ show x); store 9
lost = put 1 >> refuse "no"

timed :: (State m, Clock m) => m Integer
timed = do t <- now; put t; x <- get; pure (x + t)

forked :: (State m, Choice m) => m Integer
forked = do put 1; x <- choose [10, 20]; y <- get; put (y + x); get

held :: (Integer, Integer) -> String
held (a, s) = show a ++ ", cell " ++ show s

outcome :: Either String String -> String
outcome = either (\why -> "refused(" ++ why ++ ")") (\a -> "paid(" ++ a ++ ")")

-- | what a program over the state is seen to do: its questions with their answers, its value, the cell after
seen :: Logged (StateT Integer Identity) Integer -> String
seen p = unwords written ++ " => " ++ held (a, s)
  where
    ((a, written), s) = run (cell 0 (logged p))

main :: IO ()
main = do
  -- 1-3. sequencing: these are the monad laws of the monad chosen, here Logged over a state
  putStrLn ("1 done then f: " ++ seen (pure 5 >>= store) ++ " = " ++ seen (store 5))
  putStrLn ("2 p then done: " ++ seen (bump >>= pure) ++ " = " ++ seen bump)
  let f x = store (x + 1)
      g y = y <$ put (y * 2)
  putStrLn ("3 grouping: " ++ seen ((get >>= f) >>= g) ++ " = " ++ seen (get >>= \x -> f x >>= g))

  -- 4-5. what a handler makes of `pure`, and of one program after another
  putStrLn ("4 handling done: " ++ held (run (cell 3 (pure 7))) ++ " | "
    ++ outcome (show <$> run (refusals (pure (7 :: Integer)))))
  let whole = cell 0 (do a <- bump; b <- bump; pure (a + b))
      pieces = do (a, s) <- cell 0 bump; (b, s2) <- cell s bump; pure (a + b, s2)
  putStrLn ("5 handling a sequence: " ++ held (run whole) ++ " = " ++ held (run pieces))

  -- 6. stopped under Refusals under Log under Cell(0): the refusal never reaches the two layers below
  let ((r, written), s) = run (cell 0 (logged (refusals stopped)))
  putStrLn ("6 its own and no other's: " ++ unwords written ++ " => " ++ outcome (show <$> r) ++ ", cell " ++ show s)

  -- 7. a question nobody answers is a constraint no layer satisfies: `run (cell 0 timed)` wants an instance of
  -- Clock for Identity and there is none. TaglessUnanswered.hs is that program, and GHC refuses it.
  putStrLn "7 nobody answers: does not compile: no instance of Clock for the monad chosen"

  -- 8. two effects whose order is the meaning: is the cell part of what a refusal throws away?
  let inside = outcome (held <$> run (refusals (cell 0 lost)))
      (refused, after) = run (cell 0 (refusals lost))
  putStrLn ("8 state then failure: " ++ inside ++ " | failure then state: " ++ outcome (show <$> refused)
    ++ ", cell " ++ show after)

  -- 9. two layers that only answer: either order
  putStrLn ("9 two that only answer: " ++ held (run (cell 0 (fixedClock 7 timed))) ++ " = "
    ++ held (run (fixedClock 7 (cell 0 timed))))

  -- 10-11. the rest resumed twice: a state layer above the choice is each path's own, below it is one
  putStrLn ("10 twice, state inside: " ++ intercalate " | " (map held (run (allChoices (cell 0 forked)))))
  let (paths, final) = run (cell 0 (allChoices forked))
  putStrLn ("11 twice, state outside: " ++ intercalate " | " (map show paths) ++ ", cell " ++ show final)

  -- 12. what this handler owes its own operations: the four equations of a state
  let afterPut = fst (run (cell 0 (put 3 >> get)))
      lastPut = fst (run (cell 0 (put 3 >> put 4 >> get)))
      (x, y) = fst (run (cell 0 (do x' <- get; y' <- get; pure (x', y'))))
      putBack = snd (run (cell 5 (get >>= put)))
  putStrLn ("12 the cell's own: " ++ show afterPut ++ " " ++ show lastPut ++ " " ++ show x ++ "=" ++ show y
    ++ " cell " ++ show putBack)
