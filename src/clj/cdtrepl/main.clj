(ns cdtrepl.main
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
            [ring.adapter.jetty :as jetty])

  (:require [clojure.data.json :as json])

  (:import [java.io InputStreamReader])
)


(defn- add-ns [nss ns-name]
  (assoc nss ns-name {:name ns-name}))

(defn- ensure-ns [compiler ns-name]
  (when-not (get-in @compiler [::namespaces ns-name])
    (swap! compiler update-in [::ana/namespaces] add-ns ns-name)
  )
)

(defn- compiler-env [ns-name]
  (ensure-ns
    (env/default-compiler-env) ns-name)
)

(defn- compile-statement [expr & {:keys [analyze-path verbose warn-on-undeclared special-fns static-fns]}]
  (let  [
          env {:context :expr :locals {}}
      		read-error (Object.)
        ]

		(let [form 
    				(try
            	(binding  [
                          *ns* (create-ns ana/*cljs-ns*)
                       		reader/*data-readers* tags/*cljs-data-readers*
                       		reader/*alias-map*
                       		  (apply merge
                            	((juxt :requires :require-macros)
                             		(ana/get-namespace ana/*cljs-ns*)))
                        ]


                 			(reader/read-string expr))
              (catch Exception e
                {
                  :compile-status "error"
                  :compile-message (.getMessage e)
                }
              )
            )
    		 ]

         (if-not (= (:compile-status form) "error")
            (let [ env (assoc env :ns (ana/get-namespace ana/*cljs-ns*))
                   ast (ana/analyze env form)]
            
              (let [isns (= (:op ast) :ns)]
                (merge
                  {
                     :compile-status "ok"
                     :js-statement   (comp/emit-str ast)
                     :response-ns ana/*cljs-ns*
                  }

                  (if isns 
                     {
                       :ns-change true
                     }
                  )
                )
              )
            )

            form
         )
    )
  )      
)

(defn- get-namespace-from-request [request]
  (if-let [sns (request "ns")]
    (symbol sns)
    'cljs.user))

(defn- get-statement-from-request [request]
  (request "clj-statement"))

(defn session-compile [request]
 	(let [warn-on-undeclared false static-fns nil ns (get-namespace-from-request request)]
    (env/with-compiler-env (compiler-env ns)
  			(binding [  ana/*cljs-ns* ns
              		  ana/*cljs-warnings* 
                        (assoc ana/*cljs-warnings*
                            :undeclared-var warn-on-undeclared
                            :undeclared-ns warn-on-undeclared
                            :undeclared-ns-form warn-on-undeclared)
              		  ana/*cljs-static-fns* static-fns]

          (ensure-ns env/*compiler* ana/*cljs-ns*)

          (if-let [statement (get-statement-from-request request)]
            (compile-statement statement)

            {
              :compile-status  "error"
              :compile-message "missing CLJS statement"   
            }
          )          
    		)
  	)
  )		
)

(def __ns "cljs.user")


(defn --main []
  (println (str "js: \n---\n" (session-compile  __ns "(ns a.b.c)") "\n---\n"))
  (println (str "js: \n---\n" (session-compile  __ns "(def a 1)") "\n---\n"))
  (println (str "js: \n---\n" (session-compile  __ns "(def c 1)") "\n---\n"))


  (println (str "js: \n---\n" (session-compile __ns  "(+ a a)") "\n---\n"))
  (println (str "js: \n---\n" (session-compile __ns  "(def b 2)") "\n---\n"))
)


(defn- compile-handler [request]
  (let [request (json/read (InputStreamReader. (:body request)))]
      
   ; (println (str "statement: " statement " ns: " ns))
    (->

      (let [response (json/write-str (session-compile request))]
        (println "response: " response)
        (response/response response)
      )

      
      (response/content-type "application/json")
    )
  )
)


(defn- cors-headers [response]
  (let [headers "Content-Type"]
    (-> response
      (response/header "Access-Control-Allow-Methods", "POST")
      (response/header "Access-Control-Allow-Headers", headers)
      (response/header "Access-Control-Allow-Origin", "*")
      (response/header "Access-Control-Expose-Headers", headers)
      (response/header "Access-Control-Request-Headers", headers)
    )
  )
)

(defn cors-preflight [handler]
  (fn [request]
    (if  (= (:request-method request) :options)
      (cors-headers (response/response ""))
      (cors-headers (handler request))
    )
  )
)

(compojure/defroutes app-routes
  (compojure/POST "/compile" [:as request] (compile-handler request))
  (compojure/GET "/status" [:as request] (response/response "OK"))
  (route/not-found "Not Found"))
 
(def app
  (-> (handler/site app-routes) 
      (cors-preflight)
  )
)

(defn -main [port]
  (jetty/run-jetty app {:port (Integer. port) :join? false}))





