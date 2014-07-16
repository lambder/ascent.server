(ns ascent.server
	(:require [cljs.repl :as repl0])
	(:require [cljs.repl.rhino :as rhino])

  (:require [clojure.pprint :as pp])
  
  (:require [ascent.nswatch :as nswatch])
  
  (:require [leiningen.core.main :refer [debug info]])

	(:require 
		      	[clojure.string :as string]
            [clojure.java.io :as io]
            [clojure.core.async :as async :refer [go alt! go-loop]]
            [chord.http-kit :as chord]
            [cljs.compiler :as comp]
            [cljs.analyzer :as ana]
            [cljs.tagged-literals :as tags]
            [cljs.env :as env]
            [clojure.tools.reader :as reader])

  (:require [compojure.core :as compojure]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [ring.util.response :as response]
            [org.httpkit.server :as http])

  (:require [clojure.data.json :as json])

  (:import [java.io InputStreamReader]))


(defn error [& params]
  (apply info params))

(def warn error)

(defn read-expr [expr]
  (try
    (reader/read-string expr)
    (catch Exception e
        {
          :compile-status "error"
          :compile-message (.getMessage e)
        })))

(defn compile-form [form]
  (let [env {:context :expr :locals {} :ns {:name ana/*cljs-ns*}}]
    (let [ast (ana/analyze env form)]
        {
          :compile-status "ok"
          :js-statement   (comp/emit-str ast)
          :response-ns    ana/*cljs-ns*
          :ns-change      (= (:op ast) :ns)
        })))

(def warnings 
  { :undeclared-var false
    :undeclared-ns false
    :undeclared-ns-form false})


(defn error? [m]
  (= (:compile-status m) "error"))

(defn- compile-expression [expr ns]
  (env/with-compiler-env (env/default-compiler-env)     
    (binding [ana/*cljs-ns* ns ana/*cljs-warnings* (merge ana/*cljs-warnings* warnings)]           
    	(let [form (read-expr expr)]
        (if-not (error? form)
          (compile-form form) form)))))

(defn- get-namespace [request]
  (symbol (or (request "ns") (request :ns) "cljs.user")))
    

(defn- compile-handler [request]
  (->       
    (let [request-json (json/read (InputStreamReader. (:body request))) ns (get-namespace request)]
      (if-let [expression (request-json "clj-statement")]
        (do
          (info "request:" request-json)
          (let [compiled (compile-expression expression ns)]
            (if (error? compiled)   
              (info "response:" compiled)
              (info "response:" compiled))
            
            compiled
          ))
        (do
          (error "invalid request (no statement):" request)
          {
            :compile-status  "error"
            :compile-message "missing CLJS expression"   
          })))
   
    (json/write-str)
    (response/response)
    (response/content-type "application/json")))

(defn- cors-headers [response]
  (let [headers "Content-Type"]
    (-> response
      (response/header "Access-Control-Allow-Methods", "POST")
      (response/header "Access-Control-Allow-Headers", headers)
      (response/header "Access-Control-Allow-Origin", "*")
      (response/header "Access-Control-Expose-Headers", headers)
      (response/header "Access-Control-Request-Headers", headers))))

(defn cors-preflight [handler]
  (fn [request]
    (if  (= (:request-method request) :options)
      (cors-headers (response/response ""))
      (cors-headers (handler request)))))



(defmulti handle :type)

(defmethod handle "compile" [message]
  (let [result (compile-expression (:clj-statement message) (get-namespace message))]   
    (info "message to client:" result)
    (assoc (merge message result) :type "compile-result")))

(defmethod handle nil [message]
  (error "no handler for message" message)
  {:error "no handler for message" :message message})

(defn ws-handler [request]
  (chord/with-channel request channel
    (let [
      watch-path (get-in request [:ascent-options :watch-path])
      watch (if watch-path (nswatch/watch watch-path channel))]
        (go-loop [] 
          (if-let [message (<! channel)] 
            (do
              (info "request:" message)    
              
              (let [result (handle (:message message))]
                (info "response:" result)
                (>! channel result))
              
              (recur)))
            (when watch-path
              (nswatch/close! watch))))))

(compojure/defroutes app-routes
  (compojure/GET  "/ws" [] ws-handler)
  (compojure/POST "/compile" [:as request] (compile-handler request))
  (compojure/GET "/status" [:as request] (response/response "OK"))
  (route/not-found "Not Found"))
 
(defn assoc-options [handler options]
  (fn [request]
    (handler (assoc request :ascent-options options)))) 
 
(defn app [options]
  (-> 
    (handler/site app-routes) 
    (assoc-options options)
    (cors-preflight)))

(defn --main []
  (println (str (compile-expression  "(ns a.b.c)" "cljs.user")))
  (println (str (compile-expression  "(def a 1)" "cljs.user"))))

(defn run [{:keys [port watch-path] :as options}]
  (http/run-server 
    (app 
      {:watch-path watch-path})
      {:port port}))

(defn -main [port]
  (run  {:port (Integer. port)})
  (info "http server started on port" port))







