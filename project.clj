(defproject cdtrepl.server "0.1.0-SNAPSHOT"
  :description "Web for Service Chrome DevTools CLJS REPL"
  :license {
      :name "Eclipse Public License"
      :url  "http://www.eclipse.org/legal/epl-v10.html"
  }
 
  :min-lein-version "2.0.0" 

  
  :plugins [
    [lein-ancient "0.5.5"]]
  
  :dependencies [
  	[org.clojure/clojure "1.5.1"]
    [org.clojure/clojurescript "0.0-2156"]
    [http-kit "2.1.18"]
    [ring "1.2.2"] 
    [com.taoensso/timbre "3.1.6"] 
    [compojure "1.1.6"]
  	[org.clojure/data.json "0.2.4"]]





  :source-paths ["src/clj"]

  :uberjar-name "cdtrepl-server.jar"
)
