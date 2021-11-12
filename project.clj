(defproject sweet-array "0.1.0-SNAPSHOT"
  :description "Array manipulation library for Clojure with \"sweet\" type syntax with fewer pitfalls"
  :url "https://github.com/athos/sweet-array"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[type-infer "0.1.1"]]
  :repl-options {:init-ns sweet-array.core}
  :profiles {:provided {:dependencies [[org.clojure/clojure "1.10.3"]]}})
