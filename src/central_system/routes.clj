(ns central-system.routes
  (:require [central-system.sessions :as ssn]
            [central-system.ocpp-responses :as ocpp]
            [central-system.ocpp-commands :as ocpp-remote]
            [ring.util.http-response :refer [ok]]
            [compojure.api.sweet :refer :all]
            [central-system.wamp-ws :as comm]
            [camel-snake-kebab.core :refer [->snake_case_string]]
            [onelog.core :as log]))

(defn get-summary []
  (ok {:total-connected-evses (comm/total)}))

(defn get-by-id [evse-id]
  (ok {:uid        evse-id
       :connection (if (comm/connected? evse-id) "connected" "disconnected")
       :connectors (ocpp/get-status evse-id)}))

(def options {:format
              {:response-opts
               {:json-kw
                {:key-fn ->snake_case_string}}}})

(defapi api-routes options
        (GET "/ws/:tenant-id/:evse-id" [tenant-id evse-id :as req]
             (comm/handler req tenant-id evse-id))
        (GET "/summary" [] (get-summary))
        (GET "/sessions" [] (ok (ssn/all)))
        (GET "/connections" [] (ok (comm/get-connections)))
        (GET "/evses/:id" [id] (get-by-id id))
        (GET "/charge-boxes/:serial/sessions" [serial]
             (ok (ssn/get-session-for-box serial)))
        ocpp-remote/api-routes)
