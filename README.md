# sweet-array
[![Clojars Project](https://img.shields.io/clojars/v/dev.athos/sweet-array.svg)](https://clojars.org/dev.athos/sweet-array)
![build](https://github.com/athos/sweet-array/workflows/build/badge.svg)
[![codecov](https://codecov.io/gh/athos/sweet-array/branch/main/graph/badge.svg?token=phoLI2vS9n)](https://codecov.io/gh/athos/sweet-array)

Array manipulation library for Clojure with "sweet" array type notation and more safety by static types

## Table of Contents

- [Rationale](#rationale)
- [Installation](#installation)
- [Usage](#usage)
  - [Array creation](#array-creation)
  - [Array definition](#array-definition)
  - [Array indexing](#array-indexing)
  - [Type-related utilities](#type-related-utilities)
- [Array type notation](#array-type-notation)

## Rationale

Clojure has built-in support for Java arrays and provides a set of
facilities for manipulating them, including `make-array`, `aget`, `aset` and so on.
However, some of their difficulties like the following tend to lead users to
write verbose or unexpectedly inefficient code:

- Need to use different constructor functions for different types and dimensionalities
- Clojure compiler sometimes does not know the static type of arrays and may emit inefficient bytecode (especially for multi-dimensional arrays)
- Array type hints tend to be cryptic (e.g. `[[D`, `[Ljava.lang.String;`, etc.) and occasionally pretty hard for humans to write manually

These issues have been pointed out by various Clojurians out there in the past:

- [Taming multidim Arrays](http://clj-me.cgrand.net/2009/10/15/multidim-arrays/)  by Christophe Grand (@cgrand)
- [Java arrays and unchecked math](http://clojure-goes-fast.com/blog/java-arrays-and-unchecked-math/) by Clojure Goes Fast
- [CLJ-1289: aset-* and aget perform poorly on multi-dimensional arrays even with type hints](https://clojure.atlassian.net/browse/CLJ-1288)

`sweet-array` aims to provide solutions for them. Contretely:

- It defines *array type descriptors*, a concise and intuitive array type notation, and provides a generic array constructor which can be used for any types and dimensionalities
- The array constructors in the library maintain the static type of arrays, which reduces the cases where users have to add type hints manually
- The array operators in the library automatically infer the resulting array type, so even multi-dimensional arrays can be handled efficiently

As a result, we can write code like the following using `sweet-array`:

```clojure
;; Example of multiplying two arrays as matrices

(require '[sweet-array.core :as sa])

(sa/def a (sa/new [[double]] [[1.0 2.0] [3.0 4.0]]))
(sa/def b (sa/new [[double]] [[5.0 6.0] [7.0 8.0]]))

(let [nrows (alength a)
      ncols (alength (sa/aget b 0))
      n (alength b)
      c (sa/new [[double]] nrows ncols)]
  (dotimes [i nrows]
    (dotimes [j ncols]
      (dotimes [k n]
        (sa/aset c i j
                 (+ (* (sa/aget a i k)
                       (sa/aget b k j))
                   (sa/aget c i j))))))
  c)
```

Instead of:

```clojure
(def ^"[[D" a (into-array [(double-array [1.0 2.0]) (double-array [3.0 4.0])]))
(def ^"[[D" b (into-array [(double-array [5.0 6.0]) (double-array [7.0 8.0])]))

(let [nrows (alength a)
      ncols (alength ^doubles (aget b 0))
      n (alength b)
      ^"[[D" c (make-array Double/TYPE nrows ncols)]
  (dotimes [i nrows]
    (dotimes [j ncols]
      (dotimes [k n]
        (aset ^doubles (aget c i) j
              (+ (* (aget ^doubles (aget a i) k)
                    (aget ^doubles (aget b k) j))
                  (aget ^doubles (aget c i) j))))))
  c)
```

Note that all the type hints in this code are mandatory to make it run as fast as the above one with `sweet-array`.

## Installation

Add the following to your project dependencies:

[![Clojars Project](https://clojars.org/dev.athos/sweet-array/latest-version.svg)](https://clojars.org/dev.athos/sweet-array)

## Usage

### Array creation

#### `(new [T] n1 n2 ... nk)`

The simplest way to create an array using this library is to use
the `sweet-array.core/new` macro. The `new` macro is a generic array constructor
which can be used to create both primitive and reference type arrays:

```clojure
(require '[sweet-array.core :as sa])

(def xs (sa/new [int] 3))
(class xs) ;=> [I, which means int array type
(alength xs) ;=> 3

(def ys (sa/new [String] 5))
(class ys) ;=> [Ljava.lang.String;, which means String array type
(alength ys) ;=> 5
```

The first argument of the `new` macro is what we call an *array type descriptor*.
See the [Array type notation](#array-type-notation) section for more details, but roughly speaking,
an array type descriptor `[T]` denotes an array type whose component type is `T`
(e.g. `[int]` denotes the int array type and `[String]` denotes the String array type).

The `new` macro can also be used to create multi-dimensional arrays.
The following example creates a two-dimensional int array:

```clojure
(def arr (sa/new [[int]] 2 3))
(class arr) ;=> [[I, which means 2-d int array type
(alength arr) ;=> 2
(alength (aget arr 0)) ;=> 3
```

In general, `(sa/new [[T]] n1 n2)` produces a 2-d array of type `T` of size `n1`x`n2`
and `(sa/new [[[T]]] n1 n2 n3)` produces a 3-d array of type `T` of size `n1`x`n2`x`n3`,
and so on.

#### `(new [T] [e1 e2 ... ek])`

The `new` macro provides another syntax to create an array enumerating
the initial elements. `(sa/new [T] [e1 e2 ... ek])` creates an array
initialized with the elements `e1`, `e2`, ..., `ek`:

```clojure
(def arr (sa/new [int] [1 2 3]))
(alength arr) ;=> 3
[(aget arr 0) (aget arr 1) (aget arr 2)] ;=> [1 2 3]
```

In general, `(sa/new [T] [e1 e2 ... ek])` is equivalent to:

```clojure
(doto (sa/new [T] k)
  (aset 0 e1)
  (aset 1 e2)
  ...
  (aset (- k 1) ek))
```

This form can be used to initialize arrays of any dimensionality:

```clojure
;; 2-d double array
(sa/new [[double]] [[1.0 2.0] [3.0 4.0]])
;; 3-d boolean array
(sa/new [[[boolean]]]
        [[[true false] [false true]]
         [[false true] [true false]]]
```

When initializing multi-dimensional arrays, the init expression for each element
may itself be an array or an expression that evaluates to an array:

```clojure
(def xs (sa/new [double] [1.0 2.0]))
(def ys (sa/new [double] [3.0 4.0]))
(sa/new [[double]] [xs ys])
```

#### `(into-array [T] coll)`

Another way to create an array is to use the `sweet-array.core/into-array` macro:

```clojure
(require '[sweet-array.core :as sa])

(def arr (sa/into-array [int] (range 10)))
(class arr) ;=> [I
(alength arr) ;=> 10
```

Like `clojure.core/into-array`, `sa/into-array` converts an existing collection
(Seqable) into an array. Unlike `clojure.core/into-array`, the resulting array
type is specified with the [array type descriptor](#array-type-notation) as the first argument.

`sa/into-array` can also be used to create multi-dimensional arrays:

```clojure
(def arr' (sa/into-array [[int]] (partition 2 (range 10))))
(class arr') ;=> [[I
[(aget arr' 0 0) (aget arr' 0 1) (aget arr' 1 0) (aget arr' 1 1)]
;=> [0 1 2 3]
```

#### `(into-array [T] xform coll)`

The `sa/into-array` macro optionally takes a [transducer](https://clojure.org/reference/transducers).
This form is inspired by and therefore analogous to `(into to xform from)`.
That is, the transducer `xform` as the second argument will be applied
while converting the collection into an array:

```clojure
(def arr (sa/into-array [int] (filter even?) (range 10)))
(alength arr) ;=> 5
[(aget arr 0) (aget arr 1) (aget arr 2)] ;=> [0 2 4]
```

This is especially useful to do transformations that increase or decrease
the dimensionality of an array:

```clojure
;; 1-d to 2-d conversion
(sa/into-array [[int]] (partition-all 2) (sa/new [int] [1 2 3 4]))

;; 2-d to 1-d conversion
(sa/into-array [double] cat (sa/new [[double]] [[1.0 2.0] [3.0 4.0]]))
```

### Array definition

#### `(def name init)`
#### `(def name docstring init)`

Since 0.2.0, `sweet-array` provides its own version of the `def` macro.
It can be used as a drop-in replacement of Clojure's `def`. Unlike the ordinary
`def` form, it infers the static type of `init` and implicitly adds the inferred
Using the `def` macro together with other macros from this library, it's hardly
necessary to add a type hint explicitly:

```clojure
(sa/def arr (sa/new [int] [1 2 3]))

(:tag (meta #arr))
;=> [I
```

Note that the `def` macro will throw an error at expansion time if the type of
the `init` expression cannot be inferred or the inferred type is not an array
type:

```clojure
(sa/def arr (identity (sa/new [int] [1 2 3])))
;; Syntax error macroexpanding sweet-array.core/def* at (REPL:1:1).
;; Can't infer the static type of (identity (sa/new [int] [1 2 3])). Use `sweet-array.core/cast` to explicitly specify the array type or use `def` instead.

(sa/def arr 42)
;; Syntax error macroexpanding sweet-array.core/def* at (REPL:1:1).
;; Can't use sweet-array.core/def for 42, which is long, not array
```

### Array indexing

#### `(aget array idx1 idx2 ... idxk)`
#### `(aset array idx1 idx2 ... idxk val)`

`sweet-array` provides its own version of `aget` / `aset` for indexing arrays.
They work almost the same way as `aget` / `aset` defined in `clojure.core`:

```clojure
(require '[sweet-array.core :as sa])

(sa/def arr (sa/new [int] [1 2 3 4 5]))

(sa/aget arr 2) ;=> 3
(sa/aset arr 2 42)
(sa/aget arr 2) ;=> 42
```

Of course, they can also be used for multi-dimensional arrays as
`c.c/aget` & `aset`:

```clojure
(sa/def arr (sa/new [double] [[1.0 2.0] [3.0 4.0]]))

(sa/aget arr 1 1) ;=> 4.0
(sa/aset arr 1 1 42)
(sa/aget arr 1 1) ;=> 42
```

The difference is that `sa/aget` and `sa/aset` infer the static type of their
first argument and utilize it for several purposes as follows.
In a nutshell, they are safer and faster:

- Static type checking for the array argument
  - If the type inference fails, they will fall back to `c.c/aget` & `aset` and emit an reflection warning
    ```clojure
    (set! *warn-on-reflection* true)

    (fn [arr] (sa/aget arr 0))
    ;; Reflection warning, ... - call to static method aget on clojure.lang.RT can't be resolved (argument types: unknown, int).

    (fn [arr] (sa/aget arr 0 0))
    ;; Reflection warning, ... - type of first argument for aget cannot be inferred
    ```
  - If the type inference succeeds but the inferred type of the first argument is not an array type, then they will raise a compile-time error
    ```clojure
    (sa/aget "I'm a string" 0)
    ;; Syntax error macroexpanding sweet-array.core/aget* at ...
    ;; Can't apply aget to "I'm a string", which is java.lang.String, not array
    ```
  - If more indices are passed to them than the inferred array type expects, then they will raise a compile-time error
    ```clojure
    (sa/aget (sa/new [int] 3) 0 1 2)
    ;; Syntax error macroexpanding sweet-array.core/aget* at ...
    ;; Can't apply aget to (sa/new [int] 3) with more than 1 index(es)
    ```
- Faster access to multi-dimensional arrays by automatic type hint insertion
  - `sa/aget` & `sa/aset` know that indexing `[T]` once results in the type `T`, and automatically insert obvious type hints to the expanded form, which reduces the cases where one has to add type hints manually
    ```clojure
    (require '[criterium.core :as cr])

    (sa/def arr
      (sa/into-array [[int]] (map (fn [i] (map (fn [j] (* i j)) (range 10))) (range 10)))

    (cr/quick-bench (dotimes [i 10] (dotimes [j 10] (aget arr i j))))
    ;; Evaluation count : 792 in 6 samples of 132 calls.
    ;;              Execution time mean : 910.441562 µs
    ;;     Execution time std-deviation : 170.924552 µs
    ;;    Execution time lower quantile : 758.037129 µs ( 2.5%)
    ;;    Execution time upper quantile : 1.151744 ms (97.5%)
    ;;                    Overhead used : 8.143474 ns

    ;; The above result is way too slow due to unrecognizable reflection
    ;; To avoid this slowness, you'll need to add type hints yourself

    (cr/quick-bench (dotimes [i 10] (dotimes [j 10] (aget ^ints (aget arr i) j))))
    ;; Evaluation count : 4122636 in 6 samples of 687106 calls.
    ;;              Execution time mean : 139.098679 ns
    ;;     Execution time std-deviation : 2.387043 ns
    ;;    Execution time lower quantile : 136.235737 ns ( 2.5%)
    ;;    Execution time upper quantile : 142.183007 ns (97.5%)
    ;;                    Overhead used : 8.143474 ns

    ;; Using `sa/aget`, you can simply write as follows:

    (cr/quick-bench (dotimes [i 10] (dotimes [j 10] (sa/aget arr i j))))
    ;; Evaluation count : 5000448 in 6 samples of 833408 calls.
    ;;              Execution time mean : 113.195074 ns
    ;;     Execution time std-deviation : 4.641354 ns
    ;;    Execution time lower quantile : 108.656324 ns ( 2.5%)
    ;;    Execution time upper quantile : 119.427431 ns (97.5%)
    ;;                    Overhead used : 8.143474 ns
    ```


### Type-related utilities

`sweet-array` also provides several utilities that are useful for dealing with
array types.

#### `(type [T])`

The `sweet-array.core/type` macro is convenient to reify an array type object
represented with an [array type descriptor](#array-type-notation):

```clojure
(require '[sweet-array.core :as sa])

(sa/type [int]) ;=> [I
(sa/type [String]) ;=> [Ljava.lang.String;
(sa/type [[double]]) ;=> [[D
```

Each form shown above is more concise and straightforward than the corresponding traditional code:

```clojure
(class (int-array 0)) ;=> [I
(class (make-array String 0)) ;=> [Ljava.lang.String;
(class (make-array Double/TYPE 0 0)) ;=> [[D
```

#### `(instance? [T] expr)`

The `sweet-array.core/instance?` macro is a predicate to check if a given value is
of the specified array type:

```clojure
(sa/instance? [int] (sa/new [int] [1 2 3])) ;=> true
(sa/instance? [Object] (sa/new [int] [1 2 3])) ;=> false
(sa/instance? [String] "foo") ;=> false
```

`(sa/instance? [T] expr)` is just syntactic sugar for `(instance? (sa/type [T]) expr)`.

#### `(cast [T] expr)`

The `sweet-array.core/cast` macro is for coercing an expression to the specified
array type. It's useful for resolving reflection warnings when some expression
cannot be type-inferred:

```clojure
(defn make-array [n] (sa/new [int] n))

(set! *warn-on-reflection* true)

(sa/aget (make-array 3) 0)
;; Reflection warning, ... - call to static method aget on clojure.lang.RT can't be resolved (argument types: unknown, int).
;=> 0

(sa/aget (sa/cast [int] (make-array 3)) 0)
;=> 0
```

Note that `sa/cast` only has the compile-time effect, and does nothing else at runtime.

#### `#sweet/tag [T]`

For those who want to radically eliminate cryptic array type hints (e.g. `^"[I"`
and `^"[Ljava.lang.String;"`) from your code, `sweet-array` provides reader syntax
that can be used as a replacement for them.

By prefixing `#sweet/tag`, you can write an array type descriptor as a type hint:

```clojure
(defn ^#sweet/tag [String] select-randomly [^#sweet/tag [[String]] arr]
  (sa/aget arr (rand-int (alength arr))))
```

This code compiles without any reflection warning, just as with:

```clojure
(defn ^"[Ljava.lang.String;" select-randomly [^"[[Ljava.lang.String;" arr]
  (sa/aget arr (rand-int (alength arr))))
```

## Array type notation

`sweet-array` adopts what we call *array type descriptors* to denote array types
throughout the library. Following is the definition of `sweet-array`'s
array type descriptors:

```
      <array type descriptor> ::= '[' + <component type> + ']'
                                | <array type alias>

             <component type> ::= <primitive type name>
                                | <reference type name>
                                | <array type descriptor>

        <primitive type name> ::= <symbol primitive type name>
                                | <keyword primitive type name>

 <symbol primitive type name> ::= 'boolean'
                                | 'byte'
                                | 'char'
                                | 'short'
                                | 'int'
                                | 'long'
                                | 'float'
                                | 'double'

<keyword primitive type name> ::= ':' + <symbol primitive type name>

        <reference type name> ::= any valid class or interface name

           <array type alias> ::= <symbol array type alias>
                                | <keyword array type alias>

    <symbol array type alias> ::= 'booleans'
                                | 'bytes'
                                | 'shorts'
                                | 'ints'
                                | 'longs'
                                | 'floats'
                                | 'doubles'
                                | 'objects'

   <keyword array type alias> ::= ':' + <symbol array type alias>
```

An array type descriptor `[T]` denotes an array whose component type is `T`.
The component type itself may be an array type. For instance, `[[T]]` denotes
the two-dimensional array type of `T`, `[[[T]]]` denotes the three-dimensional
array type of `T`, and so on.

Array type aliases, such as `ints` and `doubles`, may also be used as array type
descriptors. They are completely interchangeable with their corresponding array type
descriptor: `ints` is equivalent to `[int]` and `[doubles]` is equivalent to `[[double]]`,
and so on.

## License

Copyright © 2021 Shogo Ohta

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
