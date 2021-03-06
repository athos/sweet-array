# sweet-array
[![Clojars Project](https://img.shields.io/clojars/v/sweet-array.svg)](https://clojars.org/sweet-array)
![build](https://github.com/athos/sweet-array/workflows/build/badge.svg)
[![codecov](https://codecov.io/gh/athos/sweet-array/branch/main/graph/badge.svg?token=phoLI2vS9n)](https://codecov.io/gh/athos/sweet-array)

Array manipulation library for Clojure with "sweet" type syntax and fewer pitfalls

## Table of Contents

- [Rationale](#rationale)
- [Installation](#installation)
- [Usage](#usage)
  - [Array creation](#array-creation)
  - [Array indexing](#array-indexing)
  - [Type-related utilities](#type-related-utilities)
- [Type syntax](#type-syntax)
- [Benchmarks](#benchmarks)

## Rationale

Using Clojure's array functions, you can write code like the following:

```clojure
(defn array-mul [a b]
  (let [nrows (alength a)
        ncols (alength (aget b 0))
        n (alength b)
        c (make-array Double/TYPE nrows ncols)]
    (dotimes [i nrows]
      (dotimes [j ncols]
        (dotimes [k n]
          (aset c i j
                (+ (* (aget a i k)
                      (aget b k j))
                   (aget c i j))))))
    c))
```

Seems good? Unfortunately, the performance of this code is not as good as it looks.
That’s because it contains a lot of reflective calls, some of which will be caught
by the compiler as reflection warnings, the others will not!!
To get rid of those problematic reflections, you need to add type hints here and there:

```clojure
(defn ^"[[D" array-mul [^"[[D" a ^"[[D" b]
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
    c))
```

Knowing why and where to put type hints requires an extensive understanding of
how the Clojure compiler works and how array functions are implemented.
But roughly speaking, Clojure's array functions are not very good at handling
multi-demensional arrays (This issue has been reported as
[CLJ-1289](https://clojure.atlassian.net/browse/CLJ-1289)).

Using `sweet-array`, you can write code that is almost as concise as how you would
write it straightforwardly, while it runs as fast as the optimized one shown above:

```clojure
(require '[sweet-array.core :as sa])

(defn ^#sweet/tag [[double]] array-mul [a b]
  (let [a (sa/cast [[double]] a)
        b (sa/cast [[double]] b)
        nrows (alength a)
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
    c))
```

`sweet-array` leverages the static types inferred by the Clojure compiler
to inserts necessary type hints implicitly, so you don't have to add
type hints yourself in most cases.

Also, `sweet-array` adopts "sweet" type syntax (e.g. `[[double]]` and `[String]`),
so you don't have to be bothered with cryptic array type hints (e.g. `^"[[D"` and
`^"[Ljava.lang.String;"`) any longer.

## Installation

Add the following to your project dependencies:

[![Clojars Project](https://clojars.org/sweet-array/latest-version.svg)](https://clojars.org/sweet-array)

## Usage

### Array creation

#### `(new [T] n1 n2 ... nk)`

The simplest way to create an array using this library is to use
the `sweet-array.core/new` macro. The `new` macro is a generic array constructor
that can be used to create both primitive and reference type arrays:

```clojure
(require '[sweet-array.core :as sa])

(def xs (sa/new [int] 3))
(class xs) ;=> [I
(alength xs) ;=> 3

(def ys (sa/new [String] 5))
(class ys) ;=> [Ljava.lang.String;
(alength ys) ;=> 5
```

The first argument of the `new` macro is what we call a *type descriptor*.
See the [Type syntax](#type-syntax) section for more details, but roughly speaking,
a type descriptor `[T]` denotes an array type whose component type is `T`
(e.g. `[int]` denotes int array type and `[String]` denotes String array type).

The `new` macro can also be used to create multi-dimensional arrays.
The following example creates a two-dimensional int array:

```clojure
(def arr (sa/new [[int]] 2 3))
(class arr) ;=> [[I
(alength arr) ;=> 2
(alength (aget arr 0)) ;=> 3
```

In general, `(sa/new [[T]] n1 n2)` creates a 2-d array of the type `T` with the size
of `n1`x`n2` and `(sa/new [[[T]]] n1 n2 n3)` creates a 3-d array of the type `T` 
with the size of `n1`x`n2`x`n3`, and so on.

#### `(new [T] [elem1 elem2 ... elemk])`

The `new` macro provides another syntax to create an array enumerating 
the initial elements. `(sa/new [T] [elem1 elem2 ... elemk])` creates an array
initialized with the elements `elem1`, `elem2`, ..., `elemk`:

```clojure
(def arr (sa/new [int] [1 2 3]))
(alength arr) ;=> 3
[(aget arr 0) (aget arr 1) (aget arr 2)] ;=> [1 2 3]
```

In general, `(sa/new [T] [elem1 elem2 ... elemk])` is equivalent to:

```clojure
(doto (sa/new [T] k)
  (aset 0 elem1)
  (aset 1 elem2)
  ...
  (aset (- k 1) elemk))
```

This form can be used to initialize arbitrarily nested arrays:

```clojure
;; 2-d double array
(sa/new [[double]] [[1.0 2.0] [3.0 4.0]])
;; 3-d boolean array
(sa/new [[[boolean]]]
        [[[true false] [false true]]
         [[false true] [true false]]]
```

When initializing multi-dimensional arrays, the init expression for each element
may itself be an array or an expression that generates an array:

```clojure
(def xs (sa/new [double] [1.0 2.0]))
(def ys (sa/new [double] [3.0 4.0]))
(sa/new [[double]] [xs ys])
```

#### `(into-array [T] coll)`

Another way to create an array is the `sweet-array.core/into-array` macro:

```clojure
(require '[sweet-array.core :as sa])

(def arr (sa/into-array [int] (range 10)))
(class arr) ;=> [I
(alength arr) ;=> 10
```

Like `clojure.core/into-array`, `sa/into-array` converts an existing collection
(Seqable) into an array. Unlike `clojure.core/into-array`, the resulting array
type is specified with the [type descriptor](#type-syntax) as the first argument.

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
the dimension of an array:

```clojure
;; 1-d to 2-d conversion
(sa/into-array [[int]] (partition-all 2) (sa/new [int] [1 2 3 4]))

;; 2-d to 1-d conversion
(sa/into-array [double] cat (sa/new [[double]] [[1.0 2.0] [3.0 4.0]]))
```

### Array indexing

#### `(aget array idx1 idx2 ... idxk)`
#### `(aset array idx1 idx2 ... idxk val)`

`sweet-array` provides its own version of `aget` / `aset` for indexing arrays.
They work almost the same way as `aget` / `aset` defined in `clojure.core`:

```clojure
(require '[sweet-array.core :as sa])

(def ^"[I" arr (sa/new [int] [1 2 3 4 5]))

(sa/aget arr 2) ;=> 3
(sa/aset arr 2 42)
(sa/aget arr 2) ;=> 42
```

Of course, they can also be used for multi-dimensional arrays as 
`c.c/aget` & `aset`:

```clojure
(def ^"[D" arr (sa/new [double] [[1.0 2.0] [3.0 4.0]]))

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
  - If more indices are passed to them than the number of dimensions of the inferred array type, then they will raise a compile-time error
    ```clojure
    (sa/aget (sa/new [int] 3) 0 1 2)
    ;; Syntax error macroexpanding sweet-array.core/aget* at ...
    ;; Can't apply aget to (sa/new [int] 3) with more than 1 index(es)
    ```
- Faster access to multi-dimensional arrays by automatic type hint insertion
  - `sa/aget` & `sa/aset` know that indexing `[T]` once results in the type `T`, and automatically insert obvious type hints to the expanded form, which reduces cases where one has to add type hints manually
    ```clojure
    (require '[criterium.core :as cr])
    
    (def ^"[[I" arr
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

The `sweet-array.core/type` macro is convenient to reify an array type represented
with a [type descriptor](#type-syntax):

```clojure
(require '[sweet-array.core :as sa])

(sa/type [int]) ;=> [I
(sa/type [String]) ;=> [Ljava.lang.String;
(sa/type [[double]]) ;=> [[D
```

Each form is more concise and straightforward than the corresponding idiomatic code:

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

## Type syntax

`power-dot` adopts what we call *type descriptors* to denote array types
throughout the library. Following is the syntax definition of `power-dot`'s 
type descriptors:

```
    <type descriptor> ::= '[' <component type> ']'
                        | <array type alias>

     <component type> ::= <primitive type name>
                        | <reference type name>
                        | <type descriptor>

<primitive type name> ::= 'boolean'
                        | 'byte'
                        | 'char'
                        | 'short'
                        | 'int'
                        | 'long'
                        | 'float'
                        | 'double'

<reference type name> ::= any valid class or interface name

   <array type alias> ::= 'booleans'
                        | 'bytes'
                        | 'shorts'
                        | 'ints'
                        | 'longs'
                        | 'floats'
                        | 'doubles'
                        | 'objects'
```

A type descriptor `[T]` denotes an array whose component type is `T`.
The component type itself can be an array type. For example, `[[T]]` denotes
two-dimensional array type of `T`, `[[[T]]]` denotes three-dimensional array type
of `T` and so on.

Array type aliases, such as `ints` and `doubles`, can also be used as type
descriptors. They are completely interchangeable with their corresponding type 
descriptor notation: `ints` is equivalent to `[int]` and `[doubles]` is equivalent
to `[[double]]`, for instance.

## Benchmarks

## License

Copyright © 2020 Shogo Ohta

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
