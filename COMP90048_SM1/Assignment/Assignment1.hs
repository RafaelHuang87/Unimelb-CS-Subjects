module Assignment1 (subst, interleave, unroll) where

subst :: (Eq t) => t -> t -> [t] -> [t]
subst a b [] = []
subst a b (x:xs)
    | x == a = b : (subst a b xs)
    | otherwise = x : (subst a b xs)

interleave :: [t] -> [t] -> [t]
interleave [] [] = []
interleave x [] = x
interleave [] y = y
interleave (x:xs) (y:ys) = x : y : interleave xs ys

unroll :: Int -> [a] -> [a]
unroll b (x : xs)
    | b <= 0 = []
    | otherwise = take b (cycle(x : xs))

