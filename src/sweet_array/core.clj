(ns sweet-array.core
  (:refer-clojure :exclude [aclone aget aset cast instance? into-array type])
  (:require [clojure.core :as c])
  (:import [clojure.lang Compiler$LocalBinding]))

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

(defn tag-fn [desc]
  (letfn [(step [desc]
            (if (vector? desc)
              (str \[ (tag-fn (first desc)))
              (case desc
                boolean "Z" byte "B" char "C" short "S"
                int "I" long "J" float "F" double "D"
                booleans "[Z" bytes "[B" chars "[C" shorts "[S"
                ints "[I" longs "[J" floats "[F" doubles "[D"
                objects "[Ljava.lang.Object;"
                (some-> ^Class (resolve desc)
                        (.getName)
                        ((fn [name] (str \L name \;)))))))]
    (step desc)))

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

(defmacro aget [arr idx & more]
  (let [m (-> (meta &form)
              (assoc ::form &form))]
    (if (and (symbol? arr) (nil? (:tag (meta arr))))
      (with-meta `(aget* ~arr ~idx ~@more) m)
      (let [asym (gensym 'arr)]
        `(let [~asym ~arr]
           ~(with-meta
              `(aget* ~asym ~idx ~@more)
              m))))))

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
                       `(c/aget ~arr ~(first idx))
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
   Byte/TYPE `byte
   Character/TYPE `char
   Short/TYPE `short
   Integer/TYPE `int
   Long/TYPE `long
   Float/TYPE `float
   Double/TYPE `double})

(defmacro aset [arr idx & idxv]
  (let [m (-> (meta &form)
              (assoc ::form &form))]
    (if (and (symbol? arr) (nil? (:tag (meta arr))))
      (with-meta `(aset* ~arr ~idx ~@idxv) m)
      (let [asym (gensym 'arr)]
        `(let [~asym ~arr]
           ~(with-meta
              `(aset* ~asym ~idx ~@idxv)
              m))))))

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
          `(c/aset (aget ~arr ~idx ~@(butlast more)) ~(last more) ~expr)
          `(c/aset ~arr ~idx ~expr))))
    (do
      (when (and *warn-on-reflection* (> (count idxv) 1))
        (let [{:keys [line column]} (meta &form)]
          (warn "Reflection warning, %s:%d:%d - type of first argument for aset cannot be inferred"
                *file* line column)))
      `(c/aset ~arr ~idx ~@idxv))))

(defn- expand-inits [^Class t inits]
  (if (.isArray t)
    (if (vector? inits)
      (let [asym (gensym 'arr)
            ctype (.getComponentType t)]
        `(let [~asym ~(with-meta
                        `(make-array ~ctype ~(count inits))
                        {:tag (type->tag t)})]
           ~@(map-indexed
              (fn [i init] `(c/aset ~asym ~i ~(expand-inits ctype init)))
              inits)
           ~asym))
      (throw
       (ex-info (str (.getName t) " expected, but got " (pr-str inits))
                {:type t :init inits})))
    (if-let [f (primitive-coerce-fns t)]
      `(~f ~inits)
      inits)))

(defmacro new [type-desc & args]
  (let [t (type-fn type-desc)]
    (if (some-> (first args) vector?)
      (expand-inits t (first args))
      (loop [t' t args' args]
        (if (seq args')
          (if (.isArray t')
            (recur (.getComponentType t') (rest args'))
            (throw
             (ex-info (str (.getName t) " can't take more than "
                           (count args) " index(es)")
                      {})))
          (with-meta
            `(make-array ~t' ~@args)
            {:tag (type-fn type-desc)}))))))

(defmacro aclone [arr]
  (let [m (-> (meta &form)
              (assoc ::form &form))]
    (if (and (symbol? arr) (nil? (:tag (meta arr))))
      (with-meta `(aclone* ~arr) m)
      (let [asym (gensym 'arr)]
        `(let [~asym ~arr]
           ~(with-meta
              `(aclone* ~asym)
              m))))))

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

(defn- expand-into-array [^Class type coll]
  (if (.isArray type)
    (let [ctype (.getComponentType type)
          coll-sym (gensym 'coll)]
      `(c/into-array ~ctype
                     (for [~coll-sym ~coll]
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
         coll (cond->> coll xform (list `eduction xform))]
     (with-meta
       (if (.isArray ctype)
         (expand-into-array t coll)
         `(c/into-array ~ctype ~coll))
       {:tag (tag-fn type-desc)}))))
