module Proj1 (Pitch, toPitch, feedback,
              GameState, initialGuess, nextGuess) where
{- Program Name: COMP90048_Declarative Programming_Proj1
   Author: Junjie Huang
   Login Name: JUNJHUANG
   E-mail: junjhuang@student.unimelb.edu.au
   Program function: This program achieves a simple way to predict pitches
                     (chords) and the average guess time is 4.15
   Finish Time: 04/04/2019 -}
data Note = A | B | C | D | E | F | G deriving (Show, Eq)
data Octave = One | Two | Three deriving Eq
data Pitch = Pitch Note Octave deriving Eq

-- Define the GameState with a list of pitch list
type GameState = ([[Pitch]])
type ScoreL = ((Int, Int, Int), Int)
showOct :: Octave -> String
showOct One = "1"
showOct Two = "2"
showOct Three = "3"
showPitch :: Pitch -> String
showPitch (Pitch note oct) = (show note) ++ (showOct oct)
instance Show Octave where show = showOct
instance Show Pitch where show = showPitch

get_allPitches :: [Note] -> [Octave] -> [Pitch]
get_allPitches a b = [Pitch x y | x <- a, y <- b]

allNotes = [A, B, C, D, E, F, G]
allOctaves = [One, Two, Three]
allPitches = get_allPitches allNotes allOctaves

get_allScores :: [Int] -> [ScoreL]
get_allScores a = [((x, y, z), 0) | x <- a, y <- a, z <- a]

{- Build a scorelist which looks like: [((0, 0, 0), 0), ..., ((3, 3, 3), 0)]
Length of the scoreList is 64, but some of them are useless, like (1, 3, 3),
(2, 2, 2), (3, 0, 1) - (3, 3 ,3) ... -}
allScores = [0, 1, 2, 3]
scoreList = get_allScores allScores

isEqual :: [Pitch] -> String -> Maybe Pitch
isEqual [] s = Nothing
isEqual (x : xs) s
    | show x == s = Just x
    | otherwise = isEqual xs s

toPitch :: String -> Maybe Pitch
toPitch str 
    | (isEqual allPitches str) == Nothing = Nothing
    | otherwise = isEqual allPitches str

{- Function Nme: compareNote
   Function Use: compare the Note part
   Function Input: Pitch, [Pitch]
   Function Output: True or False -}
compareNote :: Pitch -> [Pitch] -> Bool
compareNote x [] = False
compareNote (Pitch a c) ((Pitch b _):xs)
    | a == b = True
    | otherwise = compareNote (Pitch a c) xs

{- Function Nme: compareOct
   Function Use: compare the Octave part
   Function Input: Pitch, [Pitch]
   Function Output: True or False -}
compareOct :: Pitch -> [Pitch] -> Bool
compareOct x [] = False
compareOct (Pitch c a) ((Pitch _ b):xs)
    | a == b = True
    | otherwise = compareOct (Pitch c a) xs

elemIn :: Pitch -> [Pitch] -> Bool
elemIn x [] = False
elemIn v (x:xs)
    | x == v = True
    | otherwise = elemIn v xs

removeElem :: Pitch -> [Pitch] -> [Pitch]
removeElem x [] = []
removeElem v (x:xs)
    | x == v = xs
    | otherwise = x : removeElem v xs

incCount :: (Int, [Pitch], [Pitch]) -> (Int, [Pitch], [Pitch])
incCount (c1, ts, gs) = (c1 + 1, ts, gs)

keep :: Pitch -> (Int, [Pitch], [Pitch]) -> (Int, [Pitch], [Pitch])
keep t (c1, ts, gs) = (c1, t:ts, gs)

{- Function Nme: countPit
   Function Use: count the number of same pitches in the list 
                 and update the list for counting Note and Octave
   Function Input: target, Guess
   Function Output: (Num, update_target, update_Guess) -}
countPit :: [Pitch] -> [Pitch] -> (Int, [Pitch], [Pitch])
countPit [] guess = (0, [], guess)
countPit target [] = (0, target, [])
countPit (t:ts) guess
    | elemIn t guess = incCount (countPit ts (removeElem t guess))
    | otherwise = keep t (countPit ts guess)

removeNote :: Pitch -> [Pitch] -> [Pitch]
removeNote x [] = []
removeNote (Pitch a c) ((Pitch b d):xs)
    | a == b = xs
    | otherwise = (Pitch b d) : removeNote (Pitch a c) xs

countNote :: [Pitch] -> [Pitch] -> (Int, [Pitch], [Pitch])
countNote [] guess = (0, [], guess)
countNote target [] = (0, target, [])
countNote (t:ts) guess
    | compareNote t guess = incCount (countNote ts (removeNote t guess))
    | otherwise = keep t (countNote ts guess)

removeOct :: Pitch -> [Pitch] -> [Pitch]
removeOct x [] = []
removeOct (Pitch a c) ((Pitch b d):xs)
    | c == d = xs
    | otherwise = (Pitch b d) : removeOct (Pitch a c) xs

countOct :: [Pitch] -> [Pitch] -> (Int, [Pitch], [Pitch])
countOct [] guess = (0, [], guess)
countOct target [] = (0, target, [])
countOct (t:ts) guess
    | compareOct t guess = incCount (countOct ts (removeOct t guess))
    | otherwise = keep t (countOct ts guess)

{- Function Name: feedback
   Function Use: Get a target and a guess, return a result
   Function Input: target, Guess
   Function Output: (Int, Int, Int) -}
feedback :: [Pitch] -> [Pitch] -> (Int, Int, Int)
feedback target guess = (a, b, c)
    where (a, d, e) = countPit target guess
          (b, _, _) = countNote d e
          (c, _, _) = countOct d e

get_allLinks :: [Pitch] -> [[Pitch]]
get_allLinks x = [[a, b, c] | a <- x, b <- x, c <- x, a /= b, a /= c, b /= c]

{- Function Name: get_corLinks
   Function Use: Delete some repetitive links from allLinks
   Function Input: allLinks
   Function Output: correct links without repetitive objects -}
get_corLinks :: [[Pitch]] -> [[Pitch]]
get_corLinks [] = []
get_corLinks (x:xs) = x : (get_corLinks temp)
                      where temp = filter (\y -> (feedback x y)/=(3, 0, 0)) xs

allLinks = get_allLinks allPitches
--Get all correct links between pitches
corLinks = get_corLinks allLinks

initialGuess :: ([Pitch], GameState)
initialGuess = (temp, corLinks)
                where (temp, _) = findBest(findAllMax corLinks corLinks)

{- Function Name: deleteLinks
   Function Use: Delete the links which don't have the same feedback 
                 as the guess in this turn
   Function Input: [Pitch], [[Pitch]], (Int, Int, Int)
   Function Output: Updated [[Pitch]] -}
deleteLinks :: [Pitch] -> [[Pitch]] -> (Int, Int, Int) -> [[Pitch]]
deleteLinks temp corp a = [x | x <- corp, (feedback temp x == a)]

{- Function Name: getOnePossible
   Function Use: get all feedbacks for one pitch
   Function Input: [Pitch], correctLinks
   Function Output: Feedbacks for this [Pitch] -}
getOnePossible :: [Pitch] -> [[Pitch]] -> [(Int, Int, Int)]
getOnePossible _ [] = []
getOnePossible a (x:xs) = (feedback a x) : (getOnePossible a xs)

scoreEqual :: (Int, Int, Int) -> (Int, Int, Int) -> Bool
scoreEqual (a, b, c) (d, e, f)
    | (a == d && b == e && c == f) = True
    | otherwise = False

{- Function Name: updOneList
   Function Use: Update one link's tally
   Function Input: [Pitch], feedbacks of [Pitch], the link
   Function Output: ([Pitch], the link) -}
updOneList :: [Pitch] -> [(Int, Int, Int)] -> ((Int, Int, Int), Int) 
              -> ([Pitch], (Int, Int, Int), Int)
updOneList p [] (a, b) = (p, a, b)
updOneList p (x:xs) (a, b)
    | scoreEqual x a = updOneList p xs (a, b + 1)
    | otherwise = updOneList p xs (a, b)

{- Function Name: updScoreList
   Function Use: Update the one [Pitch]'s scorelinks list
   Function Input: the [Pitch], list of feedbacks, old scorelinks list
   Function Output: (the [Pitch], updated scorelinks list) -}
updScoreList :: [Pitch] -> [(Int, Int, Int)] -> [((Int, Int, Int), Int)]
                -> [([Pitch], (Int, Int, Int), Int)]
updScoreList _ a [] = []
updScoreList a b (x:xs) = (updOneList a b x) : (updScoreList a b xs)

{- Function Name: findMax
   Function Use: Find the maximum tally in one [Pitch]'s scorelinks list
   Function Input: (the [Pitch], updated scorelinks list)
   Function Output: (the [Pitch], the maximum tally) -}
findMax :: [([Pitch], (Int, Int, Int), Int)] -> ([Pitch], Int)
findMax [] = ([], 0)
findMax [(a, b, c)] = (a, c)
findMax ((a, b, c):xs)
    | c > maxNum = (a, c)
    | otherwise = (maxPit, maxNum)
    where (maxPit, maxNum) = findMax xs

{- Function Name: findAllMax
   Function Use: find all maximum tallies for every [Pitch]
   Function Input: the list of [Pitch]
   Function Output:the list of (One [Pitch], the maximum tally 
                   for this [Pitch]) -}
findAllMax :: [[Pitch]] -> [[Pitch]] -> [([Pitch], Int)]
findAllMax _ [] = []
findAllMax a (x:xs) = findMax(updScoreList x 
                      (getOnePossible x a) scoreList) : findAllMax a xs

{- Function Name: findBest
   Function Use: Output the minimum one in all maximum tallies
   Function Input: List of pitch list with its maximum tally
   Function Output: The pitch list with the minimum "maximum tally" -}
findBest :: [([Pitch], Int)] -> ([Pitch], Int)
findBest [] = ([], 0)
findBest [(a, b)] = (a, b)
findBest ((a, b):xs)
    | b < min = (a, b)
    | otherwise = (minpit, min)
    where (minpit, min) = findBest xs

nextGuess :: ([Pitch], GameState) -> (Int, Int, Int) -> ([Pitch], GameState)
nextGuess (a, allcorLinks) temp = (fin, upd)
                                  where upd = deleteLinks a allcorLinks temp
                                        (fin,_) = findBest(findAllMax upd upd)
