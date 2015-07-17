;; Copyright © 2015 Tom Marble
;;
;; This software is licensed under the terms of the
;; MIT License which can be found in the file LICENSE
;; at the root of this distribution.

(ns cpustat.core
  "The Clojure cpustat library.

   Statistics can be gathered by providing a callback
   function to start-cpustat and/or by dereferencing
   the cpustat atom."
  (:require [clojure.string :as string]
            [clojure.java.io :refer :all] ;; for as-file
            [clojure.java.shell :as shell :refer [sh]]
            [clojure.pprint :as pp] ;; DEBUG
            [environ.core :refer [env]])
  (:import [java.lang
            Process
            ProcessBuilder]
           [java.io
            BufferedReader
            InputStreamReader]))

;; holds a map of cpu stat information, for example
;; {:info
;;  {:os :linux,
;;   :os-arch "amd64",
;;   :os-version "4.0.0-2-amd64",
;;   :fs "/",
;;   :hostname "ficelle",
;;   :cpu
;;   {:name "Intel(R) Core(TM) i5-4200U CPU @ 1.60GHz",
;;    :cores 4,
;;    :speed 2.3186869999999997,
;;    :cache 3.072}},
;;  :running true,
;;  :process
;;  #object[java.lang.UNIXProcess 0x7158da9b "java.lang.UNIXProcess@7158da9b"],
;;  :cpustat
;;  [[98.6 1.0 0.4000000000000057]
;;   [94.78 5.02 0.20000000000000284]
;;   [96.0 3.6 0.4000000000000057]
;;   [95.6 4.0 0.4000000000000057]],
;;  :delay 5}
(defonce cpustat (atom nil))

(defn- hostname
  "Return the system hostname."
  {:added "0.2.0"}
  [os]
  (if (or (= os :linux) (= os :mac))
    (let [cmd (as-file "/bin/hostname")]
      (if-not (.exists cmd)
        "unknown" ;; unexpectedly the hostname command was not found
        (let [result (sh (.getCanonicalPath cmd))
              {:keys [exit out err]} result]
          (if (= exit 0)
            (string/trim-newline out)
            "unknown")))) ;; cmd failed
    ;; NOT implemented yet for Windows (or other platforms)
    "unknown"))

(defn- cpu-unknown
  "Return unknown PU information map"
  {:added "0.2.0"}
  []
  {:name "unknown" :cores 1 :speed 0.0 :cache 0.0})

(defn- get-field
  "Helper function for cpu-linux: grabs a field value from cpuinfo"
  {:added "0.2.0"}
  [proc-info field default]
  (let [field-val (first
                    (remove nil?
                      (filter #(.contains % field) proc-info)))
        val (if field-val
              (string/trim (second (string/split field-val #":"))))
        val (case field
              "siblings" (Integer. val)
              "Total Number Of Cores" (Integer. val)
              "cpu MHz" (/ (Double. val) 1000.0)
              "Processor Speed" (Double. (first (string/split val #" ")))
              "cache size" (/ (Double. (first (string/split val #" "))) 1000.0)
              "L2 Cache" (Double. (first (string/split val #" ")))
              val)]
    (if val
      val
      default)))

;; NOTE: cpu MHz changes *all the time*
(defn- cpu-linux
  "Return CPU information for Linux."
  {:added "0.2.0"}
  []
  (let [result (sh "/bin/cat" "/proc/cpuinfo")
        {:keys [exit out err]} result]
    (if (= exit 0)
      (let [procs (string/split out #"\n\n")
            proc-info (string/split-lines (last procs))
            cores (get-field proc-info "siblings" 1)
            name (get-field proc-info "model name" "CPU")
            speed (get-field proc-info "cpu MHz" 0.0)
            cache (get-field proc-info "cache size" 0.0)]
        {:name name :cores cores :speed speed :cache cache})
      (cpu-unknown))))

(defn- cpu-mac
  "Return CPU information for Mac OS X."
  {:added "0.2.0"}
  []
  (let [result (sh "/usr/sbin/system_profiler" "SPHardwareDataType")
        {:keys [exit out err]} result]
    (if (= exit 0)
      (let [proc-info (string/split-lines out)
            cores (get-field proc-info "Total Number Of Cores" 1)
            name (get-field proc-info "Processor Name" "CPU")
            speed (get-field proc-info "Processor Speed" 0.0)
            cache (get-field proc-info "L2 Cache" 0.0)]
        {:name name :cores cores :speed speed :cache cache})
      (cpu-unknown))))

(defn- cpu
  "Return CPU information in a map:

  {:name \"Name\" ;; marketing name for the processor
   :cores 1     ;; number of available cores
   :speed 0.0   ;; CPU frequency (in GHz)
   :cache 0.0}  ;; Cache size (in MiB)"
  {:added "0.2.0"}
  [os]
  (case os
    :linux (cpu-linux)
    :mac (cpu-mac)
    (cpu-unknown)))

(defn info
  "Return a map of system information."
  {:added "0.2.0"}
  []
  (when (nil? (:info @cpustat))
    (let [{:keys [os-name os-arch os-version file-separator]} env
          os (cond
               (.startsWith os-name "Linux") :linux
               (.startsWith os-name "Mac") :mac
               (.startsWith os-name "Windows") :windows
               :else :unknown)
          info {:os os
                :os-arch os-arch
                :os-version os-version
                :fs file-separator
                :hostname (hostname os)
                :cpu (cpu os)}]
      (swap! cpustat assoc :info info)))
  (:info @cpustat))

(defn running?
  "Return true if cpustat is running"
  {:added "0.2.0"}
  []
  (true? (:running @cpustat)))

;; FIXME this is really too big :(
(defn- run-cpustat
  "Run cpustat process"
  {:added "0.2.0"}
  [info pb cb]
  (let [process (.start pb)
        pipe (-> process
               (.getInputStream) (InputStreamReader.) (BufferedReader.))
        {:keys [os cpu]} info
        {:keys [cores]} cpu]
    (swap! cpustat assoc :process process)
    (while (and cores (running?))
      (loop [i 0 stats []]
        (if (= i cores)
          (do
            (swap! cpustat assoc :cpustat stats)
            (if cb (cb stats)))
          (let [line (.readLine pipe)
                [nexti nextstats]
                (if line
                  (case os
                    :linux
                    (let [fields (string/split line #"\s+")
                          core (if (= 13 (count fields))
                                 (re-matches #"\d+" (nth fields 2)))
                          core (if core (Integer. core))
                          vals (if core (map #(Double. %) (nthrest fields 3)))
                          usr (first vals)
                          idle (last vals)
                          sys (if (and usr idle) (- 100.0 usr idle))]
                      (if (and core (= i core))
                        [(inc i) (conj stats [idle usr sys])]
                        [i stats]))
                    :mac
                    (let [fields (string/split (string/trim line) #"\s+")
                          core 1 ;; iostat reports only global results :(
                          usr (if (= 6 (count fields))
                                (re-matches #"\d+" (first fields)))
                          usr (if usr (Double. usr))
                          idle (if usr (Double. (nth fields 2)))
                          sys (if (and usr idle) (- 100.0 usr idle))]
                      (if usr ;; simulate each core as the average
                        [cores (vec (repeat cores [idle usr sys]))]
                        [i stats]))
                    [-1 nil])
                  [-1 nil])]
            (if (>= nexti 0)
              (recur nexti nextstats)))))
      ;; This hangs on JDK 6 on Mac (so don't check :( )
      (if (and (not= :mac os) (not (.isAlive process)))
        (swap! cpustat assoc :running false :process nil)))
    (if (.isAlive process)
      (.destroy process))
    (swap! cpustat assoc :process nil)))

(defn start-cpustat
  "Start cpustat and update statistics every delay seconds.
  Optionally supply a callback function which will receive
  each update. Every datapoint is a vector of stats for
  each core. Each core has values for idle, user and system
  time represented as floating point numbers (whose sum is 100.0%)"
  {:added "0.2.0"}
  ([delay] (start-cpustat delay nil))
  ([delay cb]
   (when-not (running?)
     (let [info (info)
           os (:os info)
           cmd-path (case os
                      :linux "/usr/bin/mpstat"
                      :mac "/usr/sbin/iostat")
           cmd (if cmd-path (as-file cmd-path))]
       (if-not (and cmd (.exists cmd))
         (println "Sorry, cannot find stats command for" os "(expected " cmd-path ")")
         (let [delay-str (str delay)
               args (case os
                      :linux ["-P" "ALL" delay-str]
                      :mac ["-n" "0" "-C" "-w" delay-str])
               command (vec (cons (.getPath cmd) args))
               pb (ProcessBuilder. command)]
           (swap! cpustat assoc :running true :delay delay)
           (future (run-cpustat info pb cb))
           ))))))

(defn stop-cpustat
  "Stop cpustat"
  {:added "0.2.0"}
  []
  (when (running?)
    (let [process (:process @cpustat)]
      (if (and process (.isAlive process))
        (.destroy process)))
    (swap! cpustat assoc :running false :process nil)))

(defn -main
  "Demonstrate clj-cpustat: will print cpu information,
   and -- if the first argument is provided -- will repeatedly
   print out statistics at that interval."
  {:added "0.2.0"}
  [& args]
  (try
    (let [delay-str (first args)
          delay (if delay-str (Integer. delay-str) 0)]
      (pp/pprint (info))
      (when (pos? delay)
        (start-cpustat delay
          #(do (pp/pprint %) (flush)))
        (while (running?)
          (Thread/sleep 10000))))
    (catch Exception e
      (println e)
      (flush))
    (finally
      (stop-cpustat)
      (shutdown-agents))))
