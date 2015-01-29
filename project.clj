(defproject
  ascent/server "0.2.0-SNAPSHOT"
  :description "Tool Server for ascent project"
  :license {:name "EPL" :url  "http://www.eclipse.org/legal/epl-v10.html"}

  :min-lein-version "2.3.0" 
  
  :plugins [
            [lein-ancient "0.5.5"]]
  
  :dependencies [
                 [org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-2234"]
                 [http-kit "2.1.18"]
                 [jarohen/chord "0.3.1"]
                 [org.clojure/core.async "0.1.267.0-0d7780-alpha"]
                 [ring "1.2.2"]
                 [compojure "1.1.6"]
                 [org.clojure/data.json "0.2.4"]
                 ]

  :source-paths ["src/clj"]
  
  :profiles {
             :default {
                       :eval-in-leiningen true
                       }

             ;; used to start on Heroku
             :production {
                 :dependencies [
                                [leiningen "2.4.2"]
                                ]
                          }

             ;; used to start REPL
             :nrepl {
                     :dependencies [
                                    [org.clojure/tools.nrepl "0.2.3"]
                                    [leiningen "2.4.2"]
                                    ]
                     }
             }
  )
