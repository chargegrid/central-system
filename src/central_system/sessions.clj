(ns central-system.sessions
  (:require [clj-time.core :refer [now]]
            [clojure.tools.logging :refer [info infof warnf]]
            [clj-time.format :as f]
            [central-system.dynamo :refer [sessions-table]]
            [faraday-atom.core :as dyn]))

;;; TODO: List of generic improvements
;; - Add UUIDs for REST-API
;; - Add persistance
;; - Store dates as joda, not strings

; We have to transform the Joda DateTime's because faraday-atom doesn't support storing DateTime
(defn date->str [date]
  (f/unparse (f/formatters :date-time-no-ms) date))

(def ssn-no (dyn/item-atom sessions-table :ssn-no))
(defn next-ssn-no [] (swap! ssn-no inc))

(def sessions (dyn/item-atom sessions-table :sessions))

(defn initialize-session-store []
  (swap! sessions #(or % {}))
  (swap! ssn-no #(or % 0)))


(defn get-sessions
  ([box-serial]
   (->> @sessions
        (filter #(-> % second :charge-box-serial (= box-serial)))
        (into {})))
  ([box-serial connector-id]
   (->> (get-sessions box-serial)
        (filter #(-> % second :connector-id (= connector-id))))))

(defn get-session-for-box [box-serial]
  (->> (get-sessions box-serial)
       (seq)
       (map (fn [s] (assoc (second s) :id (first s))))))

(defn session-exists? [box-serial session-id]
  (-> (get-sessions box-serial)
      (get session-id)))


(defn start [{:keys [token-id connector-id] :as tx-details}]
  (let [seq-no (next-ssn-no)]
    (swap! sessions
           assoc seq-no
           (assoc tx-details :server-timestamp-start (date->str (now))
                             :status "started"))
    (infof "Session %d started, idTag = %s, connectorId = %s" seq-no token-id connector-id)
    seq-no))


(defn stop [id box-serial {:keys [meter-stop client-timestamp-stop]}]
  (swap! sessions update id
         (fn [stored]
           (if (= box-serial (:charge-box-serial stored))
             (assoc stored :status "finished"
                           :server-timestamp-stop (date->str (now))
                           :client-timestamp-stop client-timestamp-stop
                           :meter-stop meter-stop)
             (do (warnf "Transaction %d tries to finish, but with wrong charge box serial" id)
                 stored))))
  (info "Transaction finished, ID = " id))

(defn rm [id]
  (swap! sessions dissoc id))

(defn ssn-to-payload [id]
  (let [{:keys [tenant-id charge-box-serial connector-id client-timestamp-start client-timestamp-stop meter-stop meter-start token-id] :as session}
        (get @sessions id)]
    {:tenant-id         tenant-id
     :charge-box-serial charge-box-serial
     :connector-id      connector-id
     :started-at        client-timestamp-start
     :ended-at          client-timestamp-stop
     :volume            (/ (- meter-stop meter-start) 1000)
     :user-id           token-id}))

(defn all []
  (or @sessions
      {}))
