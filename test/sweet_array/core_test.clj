(ns sweet-array.core-test
  (:require [clojure.test :refer [deftest is are]]
            [sweet-array.core :as sa]
            [type-infer.core :refer [infer]]))

(deftest type-test
  (is (= (type (boolean-array 0))
         (sa/type [boolean])
         (sa/type [:boolean])
         (sa/type booleans)
         (sa/type :booleans)))
  (is (= (type (byte-array 0))
         (sa/type [byte])
         (sa/type [:byte])
         (sa/type bytes)
         (sa/type :bytes)))
  (is (= (type (char-array 0))
         (sa/type [char])
         (sa/type [:char])
         (sa/type chars)
         (sa/type :chars)))
  (is (= (type (short-array 0))
         (sa/type [short])
         (sa/type [:short])
         (sa/type shorts)
         (sa/type :shorts)))
  (is (= (type (int-array 0))
         (sa/type [int])
         (sa/type [:int])
         (sa/type ints)
         (sa/type :ints)))
  (is (= (type (long-array 0))
         (sa/type [long])
         (sa/type [:long])
         (sa/type longs)
         (sa/type :longs)))
  (is (= (type (float-array 0))
         (sa/type [float])
         (sa/type [:float])
         (sa/type floats)
         (sa/type :floats)))
  (is (= (type (double-array 0))
         (sa/type [double])
         (sa/type [:double])
         (sa/type doubles)
         (sa/type :doubles)))
  (is (= (type (object-array 0))
         (sa/type [Object])
         (sa/type objects)
         (sa/type :objects)))
  (is (= (type (make-array String 0))
         (sa/type [String])))
  (is (= (type (make-array Integer/TYPE 0 0))
         (sa/type [[int]])
         (sa/type [[:int]])
         (sa/type [ints])
         (sa/type [:ints])))
  (is (= (type (make-array Double/TYPE 0 0 0))
         (sa/type [[[double]]])
         (sa/type [[[:double]]])
         (sa/type [[doubles]])
         (sa/type [[:doubles]])))
  (are [desc] (thrown? Throwable (macroexpand `(sa/type ~'desc)))
    42
    int
    :long
    "String"
    ["double"]
    [int int]
    [UnknownClass]))

(deftest instance?-test
  (is (sa/instance? [int] (int-array [1 2 3])))
  (is (sa/instance? [String] (make-array String 0)) )
  (is (sa/instance? [[double]] (make-array Double/TYPE 0 0)))
  (is (not (sa/instance? [boolean] true)))
  (is (not (sa/instance? [int] (short-array [1 2 3]))))
  (is (not (sa/instance? [[long]] (long-array [1 2 3]))))
  (is (not (sa/instance? [double] (make-array Double/TYPE 0 0)))))

(deftest array-type?-test
  (is (sa/array-type? (sa/type [String])))
  (is (not (sa/array-type? String)))
  (is (not (sa/array-type? nil))))

(deftest array?-test
  (is (sa/array? (sa/new [int] [1 2 3])))
  (is (not (sa/array? 42)))
  (is (not (sa/array? nil))))

(deftest cast-test
  (let [arr (make-array Integer/TYPE 0)
        arr' (sa/cast [int] arr)]
    (is (sa/instance? [int] arr))
    (is (nil? (infer arr)))
    (is (sa/instance? [int] arr))
    (is (= (sa/type [int]) (infer arr'))))
  (let [arr (make-array String 0)
        arr' (sa/cast [String] arr)]
    (is (sa/instance? [String] arr))
    (is (nil? (infer arr)))
    (is (sa/instance? [String] arr'))
    (is (= (sa/type [String]) (infer arr'))))
  (let [arr (make-array Double/TYPE 0 0)
        arr' (sa/cast [[double]] arr)]
    (is (sa/instance? [[double]] arr))
    (is (nil? (infer arr)))
    (is (sa/instance? [[double]] arr'))
    (is (= (sa/type [[double]]) (infer arr')))))

(deftest new-test
  (let [arr (sa/new [byte])]
    (is (sa/instance? [byte] arr))
    (is (= (sa/type [byte]) (infer arr)))
    (is (= 0 (alength arr))))
  (let [arr (sa/new [int] 5)]
    (is (sa/instance? [int] arr))
    (is (= (sa/type [int]) (infer arr)))
    (is (= 5 (alength arr)))
    (is (= [0 0 0 0 0] (seq arr))))
  (let [arr (sa/new [String] 3)]
    (is (sa/instance? [String] arr))
    (is (= (sa/type [String]) (infer arr)))
    (is (= 3 (alength arr)))
    (is (= [nil nil nil] (seq arr))))
  (let [arr (sa/new [[long]] 2 3)]
    (is (sa/instance? [[long]] arr))
    (is (= (sa/type [[long]]) (infer arr)))
    (is (= 2 (alength arr)))
    (is (= 3 (alength (aget arr 0)) (alength (aget arr 1))))
    (is (= [0 0 0] (seq (aget arr 0)) (seq (aget arr 1)))))
  (let [arr (sa/new [[[double]]] 2 3 4)]
    (is (sa/instance? [[[double]]] arr))
    (is (= (sa/type [[[double]]]) (infer arr)))
    (is (= 2 (alength arr)))
    (is (= 3 (alength (aget arr 0)) (alength (aget arr 1))))
    (is (= 4
           (alength (aget arr 0 0))
           (alength (aget arr 0 1))
           (alength (aget arr 0 2))
           (alength (aget arr 1 0))
           (alength (aget arr 1 1))
           (alength (aget arr 1 2))))
    (is (= [0.0 0.0 0.0 0.0]
           (seq (aget arr 0 0))
           (seq (aget arr 0 1))
           (seq (aget arr 0 2))
           (seq (aget arr 1 0))
           (seq (aget arr 1 1))
           (seq (aget arr 1 2)))))
  (let [arr (sa/new [[int]] 3)]
    (is (sa/instance? [[int]] arr))
    (is (= (sa/type [[int]]) (infer arr)))
    (is (= 3 (alength arr)))
    (is (= [nil nil nil] (seq arr))))
  (let [arr (sa/new [boolean] [true false true])]
    (is (sa/instance? [boolean] arr))
    (is (= (sa/type [boolean]) (infer arr)))
    (is (= 3 (alength arr)))
    (is (= [true false true] (seq arr))))
  (let [arr (sa/new [String] ["foo" "bar" "baz"])]
    (is (sa/instance? [String] arr))
    (is (= (sa/type [String]) (infer arr)))
    (is (= 3 (alength arr)))
    (is (= ["foo" "bar" "baz"] (seq arr))))
  (let [arr (sa/new [[byte]] [[0 1 2] [3 4 5]])]
    (is (sa/instance? [[byte]] arr))
    (is (= (sa/type [[byte]]) (infer arr)))
    (is (= 2 (alength arr)))
    (is (= 3 (alength (aget arr 0)) (alength (aget arr 1))))
    (is (= [0 1 2] (seq (aget arr 0))))
    (is (= [3 4 5] (seq (aget arr 1)))))
  (let [arr (sa/new [[[char]]] [[[\a \b \c]] [[\d \e] [\f]]])]
    (is (sa/instance? [[[char]]] arr))
    (is (= (sa/type [[[char]]]) (infer arr)))
    (is (= 2 (alength arr)))
    (is (= 1 (alength (aget arr 0))))
    (is (= 2 (alength (aget arr 1))))
    (is (= 3 (alength (aget arr 0 0))))
    (is (= 2 (alength (aget arr 1 0))))
    (is (= 1 (alength (aget arr 1 1))))
    (is (= [\a \b \c] (seq (aget arr 0 0))))
    (is (= [\d \e] (seq (aget arr 1 0))))
    (is (= [\f] (seq (aget arr 1 1)))))
  (let [xs (sa/new [int] [0 1 2])
        ys (sa/new [int] [3 4])
        arr (sa/new [[int]] [xs ys])]
    (is (sa/instance? [[int]] arr))
    (is (= (sa/type [[int]]) (infer arr)))
    (is (= 2 (alength arr)))
    (is (= 3 (alength (aget arr 0))))
    (is (= 2 (alength (aget arr 1))))
    (is (= [0 1 2] (seq (aget arr 0))))
    (is (= [3 4] (seq (aget arr 1)))))
  (is (thrown? Throwable (macroexpand `(sa/new [~'int] 2 3)))))

(def ^String s "foobar")

(deftest aclone-test
  (let [arr (int-array [1 2 3])
        arr' (sa/aclone arr)]
    (is (sa/instance? [int] arr'))
    (is (= (sa/type [int]) (infer arr')))
    (is (not (identical? arr arr')))
    (is (= [1 2 3] (seq arr'))))
  (let [arr (sa/new [[boolean]] [[true false] [false true]])
        arr' (sa/aclone arr)]
    (is (sa/instance? [[boolean]] arr'))
    (is (= (sa/type [[boolean]]) (infer arr')))
    (is (not (identical? arr arr')))
    (is (identical? (aget arr 0) (aget arr' 0)))
    (is (identical? (aget arr 1) (aget arr' 1))))
  (is (thrown? Throwable (macroexpand `(sa/aclone s)))))

(deftest into-array-test
  (let [arr (sa/into-array [boolean] (map even? (range 3)))]
    (is (sa/instance? [boolean] arr))
    (is (= (sa/type [boolean]) (infer arr)))
    (is (= 3 (alength arr)))
    (is (= [true false true] (seq arr))))
  (let [arr (sa/into-array [String] (map str (range 3)))]
    (is (sa/instance? [String] arr))
    (is (= (sa/type [String]) (infer arr)))
    (is (= 3 (alength arr)))
    (is (= ["0" "1" "2"] (seq arr))))
  (let [arr (sa/into-array [[double]] (partition 2 (range 0.0 2.0 0.5)))]
    (is (sa/instance? [[double]] arr))
    (is (= (sa/type [[double]]) (infer arr)))
    (is (= 2
           (alength arr)
           (alength (aget arr 0))
           (alength (aget arr 1))))
    (is (= [0.0 0.5] (seq (aget arr 0))))
    (is (= [1.0 1.5] (seq (aget arr 1)))))
  (let [arr (sa/into-array [[String]] [["foo" "bar"] ["baz"]])]
    (is (sa/instance? [[String]] arr))
    (is (= (sa/type [[String]]) (infer arr)))
    (is (= 2 (alength arr) (alength (aget arr 0))))
    (is (= 1 (alength (aget arr 1))))
    (is (= ["foo" "bar"] (seq (aget arr 0))))
    (is (= ["baz"] (seq (aget arr 1)))))
  (let [arr (sa/into-array [int] (map inc) (range 3))]
    (is (sa/instance? [int] arr))
    (is (= (sa/type [int]) (infer arr)))
    (is (= 3 (alength arr)))
    (is (= [1 2 3] (seq arr))))
  (let [arr (sa/into-array [String] (map str) (range 3))]
    (is (sa/instance? [String] arr))
    (is (= (sa/type [String]) (infer arr)))
    (is (= 3 (alength arr)))
    (is (= ["0" "1" "2"] (seq arr))))
  (let [arr (sa/into-array [[long]] (partition-all 2) (range 6))]
    (is (sa/instance? [[long]] arr))
    (is (= (sa/type [[long]]) (infer arr)))
    (is (= 3 (alength arr)))
    (is (= 2
           (alength (aget arr 0))
           (alength (aget arr 1))
           (alength (aget arr 2))))
    (is (= [0 1] (seq (aget arr 0))))
    (is (= [2 3] (seq (aget arr 1))))
    (is (= [4 5] (seq (aget arr 2)))))
  (let [arr (sa/into-array [char] cat [[\a \b \c] [\d \e] [\f]])]
    (is (sa/instance? [char] arr))
    (is (= (sa/type [char]) (infer arr)))
    (is (= 6 (alength arr)))
    (is (= [\a \b \c \d \e \f] (seq arr)))))

(def ^ints arr-with-type-hint
  (sa/new [int] 1))

(def arr-without-type-hint
  (sa/new [[int]] 1 1))

(deftest aget-test
  (let [arr (sa/new [int] [100 101 102])
        res (sa/aget arr 1)]
    (is (= Integer/TYPE (infer res)))
    (is (= 101 res)))
  (let [arr (sa/new [String] ["foo" "bar" "baz"])
        res (sa/aget arr 0)]
    (is (= String (infer res)))
    (is (= "foo" res)))
  (let [arr (sa/new [[boolean]] [[true false] [false true]])
        res1 (sa/aget arr 0)
        res2 (sa/aget arr 0 1)]
    (is (= (sa/type [boolean]) (infer res1)))
    (is (= [true false] (seq res1)))
    (is (= Boolean/TYPE (infer res2)))
    (is (= false res2)))
  (let [arr (sa/new [[[float]]]
                    [[[1.0 0.0] [0.0 1.0]]
                     [[2.0 0.0] [0.0 2.0]]
                     [[3.0 0.0] [0.0 3.0]]])
        res1 (sa/aget arr 0)
        res2 (sa/aget arr 0 0)
        res3 (sa/aget arr 0 0 0)]
    (is (= (sa/type [[float]]) (infer res1)))
    (is (= [[1.0 0.0] [0.0 1.0]] (map seq res1)))
    (is (= (sa/type [float]) (infer res2)))
    (is (= [1.0 0.0] (seq res2)))
    (is (= Float/TYPE (infer res3)))
    (is (= 1.0 res3)))
  (is (thrown? Throwable (macroexpand `(sa/aget s 0))))
  (is (thrown? Throwable (macroexpand `(sa/aget arr-with-type-hint 0 0))))
  (is (re-find #"^Reflection warning"
               (with-out-str
                 (binding [*warn-on-reflection* true
                           *err* *out*]
                   (macroexpand
                    `(sa/aget arr-without-type-hint 0 0)))))))

(deftest aset-test
  (let [arr (sa/new [int] [1 2 3])]
    (sa/aset arr 1 101)
    (is (= [1 101 3] (seq arr))))
  (let [arr (sa/new [[boolean]] [[true false] [false true]])]
    (sa/aset arr 0 1 true)
    (is (= [true true] (seq (aget arr 0))))
    (is (= [false true] (seq (aget arr 1)))))
  (let [arr (sa/new [[[char]]] [[[\a \b \c]] [[\d \e] [\f]]])]
    (sa/aset arr 1 0 1 \z)
    (is (= [\a \b \c] (seq (aget arr 0 0))))
    (is (= [\d \z] (seq (aget arr 1 0))))
    (is (= [\f] (seq (aget arr 1 1)))))
  (is (thrown? Throwable (macroexpand `(sa/aset s 0 "foo"))))
  (is (thrown? Throwable (macroexpand `(sa/aset arr-with-type-hint 0 0 42))))
  (is (re-find #"^Reflection warning"
               (with-out-str
                 (binding [*warn-on-reflection* true
                           *err* *out*]
                   (macroexpand
                    `(sa/aset arr-without-type-hint 0 0 42)))))))

(sa/def arr1 (int-array [1 2 3]))
(sa/def arr2 (sa/into-array [[double]] (partition-all 2) (range 4)))
(sa/def arr3 (sa/cast [String] (into-array String ["foo"])))
(sa/def ^ints arr4 (into-array Integer/TYPE [1 2 3]))
(sa/def ^#sweet/tag [CharSequence] arr5 (sa/new [String] ["foo"]))

(deftest def-test
  (is (= (sa/type [int]) (infer arr1)))
  (is (= (sa/type [[double]]) (infer arr2)))
  (is (= (sa/type [String]) (infer arr3)))
  (is (= (sa/type [int]) (infer arr4)))
  (is (= (sa/type [CharSequence]) (infer arr5)))
  (are [form] (thrown? Exception
                       (binding [*ns* (the-ns 'sweet-array.core-test)]
                         (eval form)))
    '(sa/def arr (identity (sa/new [int] [1 2 3])))
    '(sa/def arr 42)
    '(sa/def ^#sweet/tag [String] arr (sa/new [int] [1 2 3]))))
