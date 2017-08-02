(ns central-system.ocpp-commands
  (:require [compojure.api.sweet :refer :all]
            [schema.core :as s]
            [clojure.string :refer [capitalize join]]
            [central-system.wamp-ws :as comm]
            [ring.util.http-response :refer [bad-request ok internal-server-error]]
            [promesa.core :as p]
            [org.httpkit.client :as http]
            [central-system.settings :refer [config]]
            [clojure.tools.logging :as log]
            [clojure.data.json :as json]))

(def commands
  {:reset                "Reset"
   :remote-start         "RemoteStartTransaction"
   :remote-stop          "RemoteStopTransaction"
   :get-configuration    "GetConfiguration"
   :clear-cache          "ClearCache"
   :unlock-connector     "UnlockConnector"
   :get-diagnostics      "GetDiagnostics"
   :update-firmware      "UpdateFirmware"
   :change-configuration "ChangeConfiguration"})

(defn exec [tenant-id box-serial cmd-id params]
  (let [cmd (cmd-id commands)]
    (if-let [msg-id (comm/call! tenant-id box-serial cmd params)]
      (ok {:status "Request accepted" :msg-id msg-id})
      (bad-request {:error (str "Charge box with serial '" box-serial "' is not connected")}))))

(defn request [opts]
  (p/promise (fn [resolve _] (http/request opts resolve))))

(defn cbs-url [& paths]
  (join "/" (into [(:charge-box-service-url config)] paths)))

(defn- clean-resp [{:keys [status body headers]}]
  {:status  status
   :body    body
   :headers {"content-type" (:content-type headers)}})

(defn- reject-non-200 [{:keys [status headers body error] :as response}]
  (log/info "Received response " (pr-str response))
  (if (= status 200)
    (json/read-str body :key-fn keyword)
    (throw (ex-info "Unsuccessfull HTTP call!"
                    {:error-response (clean-resp response)}))))

(defn get-resource [name id tenant-id user-id]
  (p/map reject-non-200
         (request {:url     (cbs-url name id)
                   :headers {"x-tenant-id" tenant-id
                             "x-user-id"   user-id}})))

(def get-box (partial get-resource "charge-boxes"))
(def get-evse (partial get-resource "evses"))

(defn- handle-errors [ex]
  (if-let [response (:error-response (ex-data ex))]
    response
    (do (log/error ex)
        (internal-server-error {:error "Unknown error"}))))

(defn with-box-and-evse [evse-id tenant-id user-id fn]
  (p/catch (p/alet [evse (p/await (get-evse evse-id tenant-id user-id))
                    box (p/await (get-box (:charge_box_id evse) tenant-id user-id))]
                   (fn box evse))
           handle-errors))

(defn with-box [box-id tenant-id user-id fn]
  (p/catch (p/alet [box (p/await (get-box box-id tenant-id user-id))]
                   (fn box))
           handle-errors))

(defn remote-cmd [box-id tenant-id user-id cmd-id params]
  (log/info "Calling remote method" cmd-id "on box" (str box-id) "with parameters" params)
  (with-box box-id tenant-id user-id
            (fn [box]
              (exec tenant-id (:serial box) cmd-id params))))

(defn start-session [token-id evse-id tenant-id user-id]
  (with-box-and-evse evse-id tenant-id user-id
                     (fn [box evse]
                       (exec tenant-id (:serial box) :remote-start
                             {:id-tag     token-id
                              :connector-id (:connector_id evse)}))))

(defn stop-session [session-id evse-id tenant-id user-id]
  (with-box-and-evse evse-id tenant-id user-id
                     (fn [box evse]
                       (exec tenant-id (:serial box) :remote-stop
                             {:transaction-id session-id}))))

(defroutes api-routes
           (context "/evses/:evse-id/remote" [evse-id]
                    :path-params [evse-id :- s/Str]
                    :header-params [x-tenant-id :- s/Uuid x-user-id]

                    (POST "/start-session" _
                          :body-params [token_id :- s/Str]
                          (start-session token_id evse-id x-tenant-id x-user-id))
                    (POST "/stop-session" _
                          :body-params [session_id :- Long]
                          (stop-session session_id evse-id x-tenant-id x-user-id)))
           (context "/charge-boxes/:box-id/remote" [box-id]
                    :path-params [box-id :- s/Uuid]
                    :header-params [x-tenant-id :- s/Uuid x-user-id]
                    (POST "/reset" _
                          :body-params [type :- (s/enum "soft" "hard")]
                          (remote-cmd box-id x-tenant-id x-user-id
                                      :reset {:type (capitalize type)}))
                    (POST "/clear-cache" _
                          (remote-cmd box-id x-tenant-id x-user-id
                                      :clear-cache {}))
                    (POST "/unlock-connector" _
                          :body-params [connector_id :- s/Num]
                          (remote-cmd box-id x-tenant-id x-user-id
                                      :unlock-connector {:connectorId connector_id}))
                    (POST "/get-configuration" _
                          (remote-cmd box-id x-tenant-id x-user-id
                                      :get-configuration {}))
                    (POST "/get-diagnostics" _
                          :body-params [location :- s/Str]
                          (remote-cmd box-id x-tenant-id x-user-id
                                      :get-diagnostics {:location location}))
                    (POST "/update-firmware" _
                          :body-params [location :- s/Str
                                        retrieve_date :- s/Str]
                          (remote-cmd box-id x-tenant-id x-user-id
                                      :update-firmware {:location location
                                                        :retrieveDate retrieve_date}))
                    (POST "/change-configuration" _
                          :body-params [key :- s/Str,
                                        value :- s/Str]
                          (remote-cmd box-id x-tenant-id x-user-id
                                      :change-configuration {:key   key
                                                             :value value}))))
