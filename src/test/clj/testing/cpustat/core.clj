;; Copyright © 2015 Tom Marble
;;
;; This software is licensed under the terms of the
;; MIT License which can be found in the file LICENSE
;; at the root of this distribution.

(ns testing.cpustat.core
  (:require [clojure.test :refer :all]
            [cpustat.core :refer :all]))

(deftest testing-cpustat-core
  (testing "testing-cpustat-core"
    (let [cpu-info (info)
          {:keys [os os-arch os-version fs hostname cpu]} cpu-info
          {:keys [name cores speed cache]} cpu
          delay 3
          stats (promise)]
      (is (keyword? os))
      (is (pos? cores))
      (is (not (running?)))
      (start-cpustat delay #(deliver stats %))
      (Thread/sleep 1000)
      (is (running?))
      (is (not (nil? (:process @cpustat))))
      (Thread/sleep (* delay 1000))
      (stop-cpustat)
      (is (and (realized? stats) (vector? @stats)))
      (is (not (running?)))
      (shutdown-agents))))
