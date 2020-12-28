# sweet-array

Array manipulation library for Clojure with sweet (type) syntax and fewer pitfalls

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
