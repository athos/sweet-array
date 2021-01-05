(ns sweet-array.core
  (:refer-clojure :exclude [aclone aget aset cast instance? into-array type])
  (:require [clojure.core :as c])
  (:import [clojure.lang Compiler$LocalBinding RT]
           [java.lang.reflect Array]))

(defn- type->tag [^Class type]
  (.getName type))

(defn- tag->type [tag]
  (Class/forName tag))

(defn- ^Class infer-type [&env x]
  (if-let [^Compiler$LocalBinding lb (get &env x)]
    (when (.hasJavaClass lb)
      (.getJavaClass lb))
    (when-let [v (resolve x)]
      (when (and (var? v) (not (fn? @v)))
        (let [tag (:tag (meta v))]
          (cond-> tag
            (string? tag)
            tag->type))))))

(defn tag-fn [type-desc]
  (letfn [(error! []
            (throw
             (ex-info (str "Invalid array type descriptor: " (pr-str type-desc))
                      {:descriptor type-desc})))
          (step [desc]
            (cond (vector? desc)
                  (if (= (count desc) 1)
                    (str \[ (step (first desc)))
                    (error!))

                  (symbol? desc)
                  (case desc
                    boolean "Z" byte "B" char "C" short "S"
                    int "I" long "J" float "F" double "D"
                    booleans "[Z" bytes "[B" chars "[C" shorts "[S"
                    ints "[I" longs "[J" floats "[F" doubles "[D"
                    objects "[Ljava.lang.Object;"
                    (or (when-let [ret (resolve desc)]
                          (when (class? ret)
                            (str \L (.getName ^Class ret) \;)))
                        (error!)))

                  :else (error!)))]
    (when-not (vector? type-desc)
      (error!))
    (step type-desc)))

(defmacro tag [desc]
  (tag-fn desc))

(defn ^Class type-fn [desc]
  (tag->type (tag-fn desc)))

(defmacro type [desc]
  (type-fn desc))

(defmacro instance? [type-desc x]
  `(c/instance? ~(type-fn type-desc) ~x))

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

(defmacro aget [arr idx & more]
  (apply expand-to-macro* `aget* &form arr idx more))

(defmacro aget* [arr idx & more]
  (if-let [t (infer-type &env arr)]
    (if (not (.isArray t))
      (let [form (::form (meta &form))
            msg (str "Can't apply aget to "
                     (pr-str (second form))
                     ", which is " (.getName t) ", not array")]
        (throw (ex-info msg {:form form})))
      (loop [t t, arr arr, idx (cons idx more), n 0]
        (if (seq idx)
          (if (.isArray t)
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

(defmacro aset [arr idx & idxv]
  (apply expand-to-macro* `aset* &form arr idx idxv))

(defmacro aset* [arr idx & idxv]
  (if-let [t (infer-type &env arr)]
    (if (not (.isArray t))
      (let [form (::form (meta &form))
            msg (str "Can't apply aset to "
                     (pr-str (second form))
                     ", which is " (.getName t) ", not array")]
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

(def array-ctor-fns
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
  (if (.isArray t)
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

(defmacro new [type-desc & args]
  (let [t (type-fn type-desc)]
    (cond (empty? args)
          `(sweet-array.core/new ~type-desc 0)
          
          (some-> (first args) vector?)
          (expand-inits t (first args))
          
          :else
          (loop [t' t args' args n 0]
            (if (seq args')
              (if (.isArray t')
                (recur (.getComponentType t') (rest args') (inc n))
                (throw
                 (ex-info (str (.getName t) " can't take more than "
                               (count args) " index(es)")
                          {})))
              (with-meta
                (if (> n 1)
                  `(Array/newInstance ~t' (sweet-array.core/new [~'int] [~@args']))
                  (array-ctor-form t' (first args)))
                {:tag (type->tag t)}))))))

(defmacro aclone [arr]
  (expand-to-macro* `aclone* &form arr))

(defmacro aclone* [arr]
  (if-let [t (infer-type &env arr)]
    (if (.isArray t)
      (with-meta `(c/aclone ~arr) {:tag (type->tag t)})
      (let [form (::form (meta &form))
            msg (str "Can't apply aset to "
                     (pr-str (second form))
                     ", which is " (.getName t) ", not array")]
        (throw (ex-info msg {:form form}))))
    `(c/aclone ~arr)))

(defmacro cast [type-desc expr]
  (with-meta expr {:tag (tag-fn type-desc)}))

(defn- into-array-form [t coll]
  (if-let [f (array-ctor-fns t)]
    `(~f ~coll)
    `(c/into-array ~t ~coll)))

(defn- expand-into-array [^Class type coll]
  (if (.isArray type)
    (let [ctype (.getComponentType type)
          coll-sym (gensym 'coll)]
      (into-array-form ctype
                       `(for [~coll-sym ~coll]
                          ~(expand-into-array ctype coll-sym))))
    (if-let [f (primitive-coerce-fns type)]
      `(~f ~coll)
      coll)))

(defmacro into-array
  ([type-desc coll]
   `(into-array ~type-desc nil ~coll))
  ([type-desc xform coll]
   (let [t (type-fn type-desc)
         ctype (.getComponentType t)
         coll (cond->> coll xform (list `sequence xform))]
     (with-meta
       (if (.isArray ctype)
         (expand-into-array t coll)
         (into-array-form ctype coll))
       {:tag (tag-fn type-desc)}))))
