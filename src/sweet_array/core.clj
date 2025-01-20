(ns sweet-array.core
  (:refer-clojure :exclude [aclone aget aset cast instance? into-array type])
  (:require [clojure.core :as c]
            [type-infer.core :as ty])
  (:import [clojure.lang RT]
           [java.lang.reflect Array]))

(def array-class-syntax-supported?
  (try
    (and (read-string "int/1") true)
    (catch Exception _ false)))

(defn- type->tag [^Class type]
  (.getName type))

(defn- tag->type [tag]
  (Class/forName tag))

(def ^:private array-type-tags
  {"booleans" "[Z", "bytes" "[B", "chars" "[C"
   "shorts" "[S", "ints" "[I", "longs" "[J"
   "floats" "[F", "doubles" "[D"
   "objects" "[Ljava.lang.Object;"})

(defn tag-fn [type-desc]
  (letfn [(error! []
            (throw
             (ex-info (str "Invalid array type descriptor: " (pr-str type-desc))
                      {:descriptor type-desc})))
          (step [desc toplevel?]
            (cond (vector? desc)
                  (if (= (count desc) 1)
                    (str \[ (step (first desc) false))
                    (error!))

                  (ident? desc)
                  (or (when (nil? (namespace desc))
                        (let [desc' (name desc)
                              t (case desc'
                                  "boolean" "Z" "byte" "B" "char" "C" "short" "S"
                                  "int" "I" "long" "J" "float" "F" "double" "D"
                                  nil)]
                          (if t
                            (if toplevel?
                              (error!)
                              t)
                            (array-type-tags desc'))))
                      (when-let [ret (and (symbol? desc) (resolve desc))]
                        (when (class? ret)
                          (if (.isArray ^Class ret)
                            (.getName ^Class ret)
                            (when-not toplevel?
                              (str \L (.getName ^Class ret) \;)))))
                      (error!))

                  :else (error!)))]
    (step type-desc true)))

(defmacro tag [desc]
  (tag-fn desc))

(defn type-fn ^Class [desc]
  (tag->type (tag-fn desc)))

(defmacro type
  "Returns the class object that represents the array type denoted by type-desc."
  [desc]
  (type-fn desc))

(defmacro instance?
  "Evaluates x and tests if it is an instance of the array type denoted by
  type-desc."
  [type-desc x]
  `(c/instance? ~(type-fn type-desc) ~x))

(defn array-type?
  "Returns true if and only if the given type is an array type."
  {:added "0.2.0"}
  [^Class t]
  (and (not (nil? t)) (.isArray t)))

(defn array?
  "Returns true if and only if the given object is an array."
  {:added "0.2.0"}
  [x]
  (array-type? (class x)))

(defn- type->str [^Class type]
  (if (and array-class-syntax-supported? (array-type? type))
    (pr-str type)
    (.getName type)))

(defn- warn [fmt & vals]
  (binding [*out* *err*]
    (apply printf fmt vals)
    (newline)))

(defn- rt-aget [arr idx]
  `(RT/aget ~arr (unchecked-int ~idx)))

(defn- rt-aset [arr idx expr]
  `(RT/aset ~arr (unchecked-int ~idx) ~expr))

(defn- expand-to-macro* [macro* &form arr & args]
  (let [m (-> (meta &form)
              (assoc ::form &form))]
    (if (and (symbol? arr) (nil? (:tag (meta arr))))
      (with-meta `(~macro* ~arr ~@args) m)
      (let [asym (gensym 'arr)]
        `(let [~asym ~arr]
           ~(with-meta
              `(~macro* ~asym ~@args)
              m))))))

(defmacro aget
  "Works just like clojure.core/aget, but with static checking of the array type
  to detect type errors at compile time."
  [arr idx & more]
  (apply expand-to-macro* `aget* &form arr idx more))

(defmacro aget* [arr idx & more]
  (if-let [t (ty/infer-type &env arr)]
    (if (not (array-type? t))
      (let [form (::form (meta &form))
            msg (str "Can't apply aget to "
                     (pr-str (second form))
                     ", which is " (type->str t) ", not array")]
        (throw (ex-info msg {:form form})))
      (loop [t t, arr arr, idx (cons idx more), n 0]
        (if (seq idx)
          (if (array-type? t)
            (let [ctype (.getComponentType t)]
              (recur ctype
                     (with-meta
                       (rt-aget arr (first idx))
                       {:tag (type->tag ctype)})
                     (rest idx)
                     (inc n)))
            (let [form (::form (meta &form))
                  msg (str "Can't apply aget to "
                           (pr-str (second form))
                           " with more than " n " index(es)")]
              (throw (ex-info msg {:form form}))))
          arr)))
    (do
      (when (and *warn-on-reflection* (seq more))
        (let [{:keys [line column]} (meta &form)]
          (warn "Reflection warning, %s:%d:%d - type of first argument for aget cannot be inferred"
                *file* line column)))
      `(c/aget ~arr ~idx ~@more))))

(def ^:private primitive-coerce-fns
  {Boolean/TYPE `boolean
   Byte/TYPE `unchecked-byte
   Character/TYPE `unchecked-char
   Short/TYPE `unchecked-short
   Integer/TYPE `unchecked-int
   Long/TYPE `unchecked-long
   Float/TYPE `unchecked-float
   Double/TYPE `unchecked-double})

(defmacro aset
  "Works just like clojure.core/aset, but with static checking of the array type
  to detect type errors at compile time."
  [arr idx & idxv]
  (apply expand-to-macro* `aset* &form arr idx idxv))

(defmacro aset* [arr idx & idxv]
  (if-let [t (ty/infer-type &env arr)]
    (if (not (array-type? t))
      (let [form (::form (meta &form))
            msg (str "Can't apply aset to "
                     (pr-str (second form))
                     ", which is " (type->str t) ", not array")]
        (throw (ex-info msg {:form form})))
      (let [[more v] ((juxt butlast last) idxv)
            vtype (loop [t (.getComponentType t), more more, n 1]
                    (cond (empty? more) t

                          (not (.isArray t))
                          (let [form (::form (meta &form))
                                msg (str "Can't apply aset to "
                                         (pr-str (second form))
                                         " with more than " n " index(es)")]
                            (throw (ex-info msg {:form form})))

                          :else (recur (.getComponentType t) (rest more) (inc n))))
            f (primitive-coerce-fns vtype)
            expr (cond->> v f (list f))]
        (if (seq more)
          (rt-aset `(aget ~arr ~idx ~@(butlast more)) (last more) expr)
          (rt-aset arr idx expr))))
    (do
      (when (and *warn-on-reflection* (> (count idxv) 1))
        (let [{:keys [line column]} (meta &form)]
          (warn "Reflection warning, %s:%d:%d - type of first argument for aset cannot be inferred"
                *file* line column)))
      `(c/aset ~arr ~idx ~@idxv))))

(def ^:private array-ctor-fns
  {Boolean/TYPE `boolean-array
   Byte/TYPE `byte-array
   Character/TYPE `char-array
   Short/TYPE `short-array
   Integer/TYPE `int-array
   Long/TYPE `long-array
   Float/TYPE `float-array
   Double/TYPE `double-array})

(defn- array-ctor-form [t size]
  (if-let [f (array-ctor-fns t)]
    `(~f (unchecked-int ~size))
    `(Array/newInstance ~t (unchecked-int ~size))))

(defn- expand-inits [^Class t inits]
  (if (array-type? t)
    (if (vector? inits)
      (let [asym (gensym 'arr)
            ctype (.getComponentType t)]
        `(let [~asym ~(with-meta
                        (array-ctor-form ctype (count inits))
                        {:tag (type->tag t)})]
           ~@(map-indexed
              (fn [i init]
                (rt-aset asym i (expand-inits ctype init)))
              inits)
           ~asym))
      inits)
    (if-let [f (primitive-coerce-fns t)]
      `(~f ~inits)
      inits)))

(defmacro new
  "Creates an array of the type denoted by type-desc.

  The macro has two forms:
  - (new [T] n): produce an array of type T of size n
  - (new [T] [e_1 ... e_n]): produce an array of type T of size n initialized
    with elements e_1, ..., e_n."
  [type-desc & args]
  (let [t (type-fn type-desc)]
    (cond (empty? args)
          `(sweet-array.core/new ~type-desc 0)

          (vector? (first args))
          (expand-inits t (first args))

          :else
          (loop [t' t args' args n 0]
            (if (seq args')
              (if (array-type? t')
                (recur (.getComponentType t') (rest args') (inc n))
                (throw
                 (ex-info (str type-desc " can't take more than "
                               (count args) " index(es)")
                          {})))
              (with-meta
                (if (> n 1)
                  `(Array/newInstance ~t' (sweet-array.core/new [~'int] [~@args]))
                  (array-ctor-form t' (first args)))
                {:tag (type->tag t)}))))))

(defmacro aclone [arr]
  (expand-to-macro* `aclone* &form arr))

(defmacro aclone* [arr]
  (if-let [t (ty/infer-type &env arr)]
    (if (array-type? t)
      (with-meta `(c/aclone ~arr) {:tag (type->tag t)})
      (let [form (::form (meta &form))
            msg (str "Can't apply aclone to "
                     (pr-str (second form))
                     ", which is " (type->str t) ", not array")]
        (throw (ex-info msg {:form form}))))
    `(c/aclone ~arr)))

(defmacro cast
  "Casts the given expression expr to the array type denoted by type-desc.

  This macro only has the compile-time effect and does nothing at runtime."
  [type-desc expr]
  (with-meta expr {:tag (tag-fn type-desc)}))

(defn- into-array-form [t coll]
  (if-let [f (array-ctor-fns t)]
    `(~f ~coll)
    `(c/into-array ~t ~coll)))

(defn- expand-into-array [^Class type coll]
  (if (array-type? type)
    (let [ctype (.getComponentType type)
          coll-sym (gensym 'coll)]
      (into-array-form ctype
                       `(for [~coll-sym ~coll]
                          ~(expand-into-array ctype coll-sym))))
    (if-let [f (primitive-coerce-fns type)]
      `(~f ~coll)
      coll)))

(defmacro into-array
  "Converts the given collection (seqable) to an array of the type denoted by
  type-desc. A transducer may be supplied."
  ([type-desc coll]
   `(into-array ~type-desc nil ~coll))
  ([type-desc xform coll]
   (let [t (type-fn type-desc)
         ctype (.getComponentType t)
         coll (cond->> coll xform (list `sequence xform))]
     (with-meta
       (if (array-type? ctype)
         (expand-into-array t coll)
         (into-array-form ctype coll))
       {:tag (tag-fn type-desc)}))))

(defmacro def
  "The macro version of def dedicated to arrays.
  This macro can be used as a drop-in replacement for Clojure's def. Unlike
  the ordinary def form, (sweet-array.core/def <var> <init>) infers the static
  type of <init> and implicitly adds the inferred type as the type hint for <var>.
  Throws an error at macro expansion time if the type of <init> cannot be statically
  inferred or if the inferred type is not an array type."
  {:added "0.2.0"
   :clj-kondo/lint-as 'clj-kondo.lint-as/def-catch-all}
  ([name arr]
   (with-meta `(sweet-array.core/def ~name nil ~arr) (meta &form)))
  ([name docstr arr]
   (if (symbol? arr)
     (with-meta `(def* ~name ~arr) (meta &form))
     (let [asym (gensym 'init)]
       `(let [~asym ~arr]
          ~(with-meta
             `(def* ~name ~asym ~docstr ~arr)
             (meta &form)))))))

(defmacro def* [name sym docstr expr]
  (let [inferred-type (ty/infer-type &env sym)
        tag (:tag (meta name))
        ^Class hinted-type (some-> tag ty/resolve-tag)]
    (cond (and tag (nil? hinted-type))
          (let [msg (format "Unable to resolve tag: %s in this context"
                            (pr-str tag))]
            (throw (ex-info msg {:hinted-tag tag})))

          (and (nil? inferred-type) (nil? hinted-type))
          (let [msg (str "Can't infer the static type of " (pr-str expr) ". "
                         "Use `sweet-array.core/cast` to explicitly specify "
                         "the array type or use `def` instead.")]
            (throw (ex-info msg {})))

          (and inferred-type (not (array-type? inferred-type)))
          (let [msg (str "Can't use sweet-array.core/def for " (pr-str expr)
                         ", which is " (type->str inferred-type) ", not array")]
            (throw (ex-info msg {:inferred-type inferred-type})))

          (and hinted-type (not (array-type? hinted-type)))
          (let [msg (format "Hinted type (%s) is not an array type"
                            (type->str hinted-type))]
            (throw (ex-info msg {:hinted-type hinted-type})))

          (and hinted-type
               inferred-type
               (not (.isAssignableFrom hinted-type inferred-type)))
          (let [msg (format "Inferred type (%s) is not compatible with hinted type (%s)"
                            (type->str inferred-type)
                            (type->str hinted-type))]
            (throw (ex-info msg {:hinted-type hinted-type
                                 :inferred-type inferred-type})))

          :else
          `(def ~(vary-meta name assoc :tag (or hinted-type inferred-type))
             ~@(when docstr [docstr])
             ~sym))))
