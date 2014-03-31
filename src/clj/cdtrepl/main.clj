(ns cdtrepl.main
  (:require [taoensso.timbre :as timbre])
    
	(:require [cljs.repl :as repl0])
	(:require [cljs.repl.rhino :as rhino])

  (:require [clojure.pprint :as pp])

	(:require 
		      	[clojure.string :as string]
            [clojure.java.io :as io]
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

(timbre/refer-timbre)

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
  (if-let [sns (request "ns")]
    (symbol sns) 'cljs.user))

(defn- compile-handler [request]
  (->       
    (let [request-json (json/read (InputStreamReader. (:body request))) ns (get-namespace request)]
      (if-let [expression (request-json "clj-statement")]
        (do
          (info "request:" request-json)
          (let [compiled (compile-expression expression ns)]
            (if (error? compiled)   
              (warn "response:" compiled)
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

(compojure/defroutes app-routes
  (compojure/POST "/compile" [:as request] (compile-handler request))
  (compojure/GET "/status" [:as request] (response/response "OK"))
  (route/not-found "Not Found"))
 
(def app
  (-> (handler/site app-routes) 
    (cors-preflight)))

(defn --main []
  (println (str (compile-expression  "(ns a.b.c)" "cljs.user")))
  (println (str (compile-expression  "(def a 1)" "cljs.user"))))

(defn -main [port]
  (http/run-server app {:port (Integer. port) :join? false})
  (info "http server started on port" port))

(timbre/set-config! [:appenders :standard-out] 
  {
    :min-level nil 
    :enabled? true 
    :async? false :rate-limit nil
    :fn  
      (fn [{:keys [output]}] 
        (binding [*out* *err*] 
          (timbre/str-println output)))})

(timbre/set-config! [:fmt-output-fn]
  (fn [{:keys [level throwable message timestamp hostname ns]}]
    (format "[%s] %s - %s" ns (string/upper-case (name level)) message)))







