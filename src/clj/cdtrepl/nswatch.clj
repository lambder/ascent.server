(ns cdtrepl.nswatch
  (:require 
    [taoensso.timbre :as timbre]
    [clojure.core.async :as async :refer [go alt! go-loop]])

  (:import 
    (java.nio.file WatchService Paths FileSystems StandardWatchEventKinds)))

(timbre/refer-timbre)

(defprotocol IWatcher
  (close! [_]))

(defn recursive-paths [base]
  (filter #(.isDirectory %)
    (file-seq  (clojure.java.io/file base))))
  
  
(defn get-ns [file]
  (let [line 
        (with-open [reader (clojure.java.io/reader file)]
          (second (line-seq reader)))]
    (second (first (re-seq #"^goog\.provide\('(.*)'\);$" line)))))
  
  
  (System/getProperty "os.name")

(defn modifiers []
  (if (.contains (System/getProperty "os.name") "OS X")
    (do
      (info "using OS X high watch sensivity")
      [com.sun.nio.file.SensitivityWatchEventModifier/HIGH]) []))
  
(defn watch [base ch]
  (info "recursively watching " base)
  
  (let [watch-service (.. FileSystems getDefault newWatchService) mods (into-array (modifiers))
        watch-keys 
          (doall
            (map 
              #(let [path (.toPath %)]
                (.register path watch-service 
                  (into-array [StandardWatchEventKinds/ENTRY_MODIFY])
                  mods)) 
              (recursive-paths base)))]
      
      (async/thread
        (loop []
          (let [key (.take watch-service)]
            (doseq [event (.pollEvents key)]
              (let [path (.resolve (.watchable key) (.context event)) file (.toFile path) file-name (str file)]
                (when (.endsWith file-name ".js")
                  (when-let [ns (get-ns file)]
                    (info "namespace updated: " ns)
                    (async/put! ch {:type "ns-change" :ns ns})
                    
                    ))))  

            (.reset key))
          (recur)))
  
      (reify
        IWatcher
          (close! [_] 
            (debug "watching stopped")
            (.close watch-service)))))0


