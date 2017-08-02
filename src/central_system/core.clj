(ns central-system.core
  (:require [central-system.queue :as queue]
            [central-system.sessions :refer [initialize-session-store]]
            [central-system.routes :refer [api-routes]]
            [central-system.settings :refer [config]]
            [central-system.dynamo :refer [create-table]]
            [org.httpkit.server :refer [run-server]]
            [clojure.tools.logging :refer [info]]
            [ring.middleware
             [reload :refer [wrap-reload]]
             [logger :as logger]
             [cors :refer [wrap-cors]]]
            [prone.middleware :refer [wrap-exceptions]]
            [compojure.response :refer [Renderable render]])
  (:import (java.util.concurrent CompletableFuture))
  (:gen-class))

(def app
  (-> #'api-routes
      logger/wrap-with-logger
      wrap-exceptions                                       ; TODO: skip for production
      wrap-reload                                           ; TODO: skip for production
      (wrap-cors :access-control-allow-origin [#"http://localhost:3449", #"http://docker-local:3449"]
                 :access-control-allow-methods [:get :put :post :delete :options])))

(extend-protocol Renderable
  CompletableFuture
  (render [future request] (render (.get future) request)))

(defn -main [& args]
  (queue/setup)
  (create-table)
  (initialize-session-store)
  (let [port (:port config)]
    (info "Central System server running on port" port)
    (run-server app {:port port})))
