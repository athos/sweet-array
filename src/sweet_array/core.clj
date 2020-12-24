(ns sweet-array.core
  (:refer-clojure :exclude [aclone aget aset cast into-array type])
  (:require [clojure.core :as c])
  (:import [clojure.lang Compiler$LocalBinding]))

(defn- infer-type [&env x]
  (let [^Compiler$LocalBinding lb (get &env x)]
    (when (some-> lb (.hasJavaClass))
      (.getJavaClass lb))))

(defn tag-fn [desc]
  (letfn [(step [desc]
            (if (vector? desc)
              (str \[ (tag-fn (first desc)))
              (case desc
                boolean "Z"
                byte "B"
                char "C"
                short "S"
                int "I"
                long "J"
                float "F"
                double "D"
                (some-> ^Class (resolve desc)
                        (.getName)
                        ((fn [name] (str \L name \;)))))))]
    (step desc)))

(defmacro tag [desc]
  (tag-fn desc))

(defn ^Class type-fn [desc]
  (Class/forName (tag-fn desc)))

(defmacro type [desc]
  (type-fn desc))

(defn- type->tag [^Class type]
  (.getName type))

(defmacro aget [arr & idx]
  `(let [arr# ~arr]
     (aget* arr# ~@idx)))

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
    `(c/aget ~arr ~@idx)))

(defmacro aset [arr & idxv]
  (let [[idx [i v]] (split-at (- (count idxv) 2) idxv)]
    `(c/aset (aget ~arr ~@idx) ~i ~v)))

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
  `(let [arr# ~arr]
     (aclone* arr#)))

(defmacro aclone* [arr]
  (if-let [t (infer-type &env arr)]
    (with-meta `(c/aclone ~arr) {:tag (type->tag t)})
    `(c/aclone ~arr)))

(defmacro cast [type-desc expr]
  (with-meta expr {:tag (tag-fn type-desc)}))

(def ^:private primitive-coerce-fns
  {Boolean/TYPE 'boolean
   Byte/TYPE 'byte
   Character/TYPE 'char
   Short/TYPE 'short
   Integer/TYPE 'int
   Long/TYPE 'long
   Float/TYPE 'float
   Double/TYPE 'double})

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
