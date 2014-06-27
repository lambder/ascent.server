(ns leiningen.ascent
  (:require 
    [leiningen.core.main :refer [debug info abort]]
    [ascent.server :as server]))

(defn prepare-options [options]
  (apply hash-map 
    (map 
      #(if (.startsWith % ":") 
         (keyword (.substring % 1)) %) 
      options)))

(defn extract-watch-path [project build-id]
  (let [all-builds      (get-in project [:cljsbuild :builds]) 
        default-build   (first all-builds)
        filtered-build  (first (filter #(= (:id %) build-id) all-builds))
        selected-build  (or filtered-build default-build)]
    (or
      (get-in selected-build [:compiler :output-dir])
      (do 
        (info "cannot select build configuration, namespace update notification will be disabled") nil))))

(defn ascent [project & options]
  (let [{:keys [port ns-updates] :as options} (prepare-options options)
        watch-path (if ns-updates (extract-watch-path project "dev"))]
    (if port 
      (do
        (server/run { :port (Integer. port)
                      :watch-path watch-path })
        (info "ascent server started at port:" port "watching:" watch-path)

        (loop []
          (java.lang.Thread/sleep Long/MAX_VALUE) (recur)))
      
      (abort "required :port parameter is not specified"))))