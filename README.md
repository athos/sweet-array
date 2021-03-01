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

### Type-related utilities

#### `(type [T])`

#### `(instance? [T] expr)`

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
