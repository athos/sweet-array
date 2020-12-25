(ns sweet-array.core
  (:refer-clojure :exclude [aclone aget aset cast into-array type])
  (:require [clojure.core :as c])
  (:import [clojure.lang Compiler$LocalBinding]))

(defn- type->tag [^Class type]
  (.getName type))

(defn- tag->type [tag]
  (Class/forName tag))

(defn- infer-type [&env x]
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

(defn- warn [fmt & vals]
  (binding [*out* *err*]
    (apply printf fmt vals)
    (newline)))

(defmacro aget [arr & idx]
  (with-meta
    (if (symbol? arr)
      `(aget* ~arr ~@idx)
      `(let [arr# ~arr]
         (aget* arr# ~@idx)))
    (meta &form)))

(defmacro aget* [arr & idx]
  (if-let [^Class t (infer-type &env arr)]
    (loop [t t arr arr idx idx]
      (if (seq idx)
        (let [ctype (.getComponentType t)]
          (recur ctype
                 (with-meta
                   `(c/aget ~arr ~(first idx))
                   {:tag (type->tag ctype)})
                 (rest idx)))
        arr))
    (do
      (when (and *warn-on-reflection* (> (count idx) 1))
        (let [{:keys [line column]} (meta &form)]
          (warn "Reflection warning, %s:%d:%d - type of first argument for aget cannot be inferred"
                *file* line column)))
      `(c/aget ~arr ~@idx))))

(def ^:private primitive-coerce-fns
  {Boolean/TYPE 'boolean
   Byte/TYPE 'byte
   Character/TYPE 'char
   Short/TYPE 'short
   Integer/TYPE 'int
   Long/TYPE 'long
   Float/TYPE 'float
   Double/TYPE 'double})

(defmacro aset [arr idx & idxv]
  (with-meta
    (if (symbol? arr)
      `(aset* ~arr ~idx ~@idxv)
      `(let [arr# ~arr]
         (aset* arr# ~idx ~@idxv)))
    (meta &form)))

(defmacro aset* [arr idx & idxv]
  (if-let [^Class t (infer-type &env arr)]
    (let [[more v] ((juxt butlast last) idxv)
          vtype (loop [t (.getComponentType t) more more]
                  (if (empty? more)
                    t
                    (recur (.getComponentType t) (rest more))))
          f (primitive-coerce-fns vtype)
          expr (cond->> v f (list f))]
      (if (seq more)
        `(c/aset (aget ~arr ~idx ~@(butlast more)) ~(last more) ~expr)
        `(c/aset ~arr ~idx ~expr)))
    (do
      (when (and *warn-on-reflection* (> (count idxv) 1))
        (let [{:keys [line column]} (meta &form)]
          (warn "Reflection warning, %s:%d:%d - type of first argument for aset cannot be inferred"
                *file* line column)))
      `(c/aset ~arr ~idx ~@idxv))))

(defn- expand-inits [arr inits]
  (letfn [(rec [idx inits]
            (if (vector? inits)
              (for [[i inits] (map-indexed vector inits)
                    expr (rec (conj idx i) inits)]
                expr)
              `((aset ~arr ~@idx ~inits))))]
    (rec [] inits)))

(defmacro new [type-desc & args]
  (let [t (type-fn type-desc)
        [comp-type _] (loop [t t l 0]
                        (if (.isArray t)
                          (recur (.getComponentType t) (inc l))
                          [t l]))]
    (with-meta
      (if (some-> (first args) vector?)
        (let [arr (with-meta (gensym 'arr) {:tag (tag-fn type-desc)})
              dims (loop [inits (first args) dims []]
                     (if (vector? inits)
                       (recur (first inits) (conj dims (count inits)))
                       dims))]
          `(let [~arr (make-array ~comp-type ~@dims)]
             ~@(expand-inits arr (first args))
             ~arr))
        `(make-array ~comp-type ~@args))
      {:tag (tag-fn type-desc)})))

(defmacro aclone [arr]
  (if (symbol? arr)
    `(aclone* ~arr)
    `(let [arr# ~arr]
       (aclone* arr#))))

(defmacro aclone* [arr]
  (if-let [t (infer-type &env arr)]
    (with-meta `(c/aclone ~arr) {:tag (type->tag t)})
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

(defmacro into-array [type-desc coll]
  (let [t (type-fn type-desc)
        ctype (.getComponentType t)]
    (with-meta
      (if (.isArray ctype)
        (expand-into-array t coll)
        `(c/into-array ~ctype ~coll))
      {:tag (tag-fn type-desc)})))
