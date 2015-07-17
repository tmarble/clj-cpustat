(defproject net.info9/clj-cpustat "0.2.0"
  :description "Clojure CPU statistics library"
  :url "https://github.com/tmarble/clj-cpustat"
  :scm {:url "https://github.com/tmarble/clj-cpustat.git"}
  :pom-addition [:developers [:developer [:name "Tom Marble"]]]
  :license {:name "MIT"
            :url "http://opensource.org/licenses/MIT"}

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [environ "1.0.0"]]

  :plugins [[codox "0.8.12"
             :exclusions [org.clojure/clojure]]]

  :source-paths ["src/main/clj"]
  :codox {:sources ["src/main/clj"]
          :src-dir-uri "https://github.com/tmarble/clj-cpustat/blob/master/"
          :src-linenum-anchor-prefix "L"}

  :aliases {"clean-test" ^{:doc "Clean and run all tests."}
            ["do" "clean" ["test"]]}

  :main cpustat.core ;; for debugging

  :profiles
  {:dev {:test-paths ["src/test/clj"]}})
