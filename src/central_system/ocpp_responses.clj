(ns central-system.ocpp-responses
  (:require [central-system.sessions :as ssn]
            [clj-time.core :refer [now]]
            [ring.util.response :refer [response]]
            [clojure.tools.logging :as log]
            [central-system.queue :refer [publish-session]]
            [camel-snake-kebab.core :refer [->kebab-case-keyword]]
            [central-system.settings :refer [config]]))

(let [status (atom {})]
  (defn- update-status! [evse-id connector-id connector-status error-code]
    (swap! status assoc-in [evse-id connector-id]
           {:status     connector-status
            :error-code error-code}))
  (defn get-status [evse-id]
    (get @status evse-id)))


(defmulti get-reply (fn [tenant-id box-serial type action payload] (->kebab-case-keyword action)))

(defmethod get-reply :boot-notification [& _]
  {:ok-payload
   {:status             "Accepted"
    :current-time       (now)
    :heartbeat-interval (:default-heartbeat-interval config)}})

(defmethod get-reply :authorize [& _]
  ; TODO: Authorization webhook
  {:ok-payload
   {:id-tag-info {:status "Accepted"}}})

(defmethod get-reply :heartbeat [& _]
  {:ok-payload
   {:current-time (now)}})

(defmethod get-reply :start-transaction
  [tenant-id box-serial type action {:keys [timestamp connector-id meter-start id-tag]}]
  (let [sessions (ssn/get-sessions box-serial connector-id)]
    (if (empty? sessions)
      (let [id (ssn/start {:tenant-id              tenant-id
                           :charge-box-serial      box-serial
                           :connector-id           connector-id
                           :token-id               id-tag
                           :meter-start            meter-start
                           :client-timestamp-start timestamp})]
           {:ok-payload
            {:transaction-id id
             :id-tag-info    {:status "Accepted"}}})
      {:err-code "GenericError"
       :err-desc (str "Session" (-> sessions first first) " already started on connector " connector-id)})))

(defmethod get-reply :stop-transaction
  [tenant-id box-serial type action {:keys [timestamp transaction-id meter-stop]}]
  (if (ssn/session-exists? box-serial transaction-id)
    (do
      (ssn/stop transaction-id box-serial {:meter-stop            meter-stop
                                           :client-timestamp-stop timestamp})
      (publish-session (ssn/ssn-to-payload transaction-id))
      (ssn/rm transaction-id)
      {:ok-payload
       {}})
    {:err-code "GenericError"
     :err-desc (str "Transaction " transaction-id " not started on charge-box " box-serial)}))



(defmethod get-reply :status-notification
  [tenant-id box-serial type action {:keys [connector-id status error-code]}]
  (update-status! box-serial connector-id status error-code)
  {:ok-payload
   {}})

(defmethod get-reply :default
  [tenant-id box-serial type action payload]
  {:err-code "NotSupported"
   :err-desc (str action " not supported by ChargeGrid")})
