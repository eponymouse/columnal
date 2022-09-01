{-# LANGUAGE GADTs #-}
import Data.List

-- To make this haskell, we use (Integer, Unit u) for Number, String for Text
-- We leave dates out, but should be fairly straightforward using same as Boolean

data Unit

data Type a where
  Number :: Unit -> Type (Integer, Unit)
  Boolean :: Type Bool
  Text :: Type String
  List :: Type a -> Type [a]
  Pair :: (Type a, Type b) -> Type (a, b)
  Tagged :: [Tag a] -> Type a
  
data Tag a where
  Tag :: (String, (a -> Maybe (b, Type b))) -> Tag a
{-
data ValueAndType where
  VT :: (a, Type a) -> ValueAndType

anyToString :: ValueAndType -> String
anyToString (VT (x, t)) = case x of
  Boolean -> show x
  Text -> show x
  List l -> show $ map (\y -> anyToString (y, l)) x
  Pair (a, b) -> show (anyToString (fst x, a), anyToString (snd x, b))
  Tagged tags -> head $ [anyToString tag | tag <- tags, Just (ty, y) <- [snd tag x]]
-}
anyToString :: (a, Type a) -> String
anyToString (x, t) = case t of
  Boolean -> show x
  Text -> show x
  List l -> intercalate "," $ map (\y -> anyToString (y, l)) x
  Pair (a, b) -> "(" ++ anyToString (fst x, a) ++ ", " ++ anyToString (snd x, b) ++ ")"
  Tagged tags -> head $ [fst tag ++ " " ++ anyToString (y, ty) | Tag tag <- tags, Just (y, ty) <- [snd tag x]] 


example :: ([(Bool, String)], Type [(Bool, String)])
example = ([(True, "A"), (False, "B")], List $ Pair (Boolean, Text)) 
