(ns central-system.wamp-ws
  (:require [central-system.wsutils :as wsutils]
            [central-system.json :as json]
            [clojure.data.json :as j]
            [clojure.tools.logging :as log]
            [org.httpkit.server :as ws]
            [central-system.queue :refer [publish-ocpp-msg! publish-connection-event]]
            [clojure.set :refer [map-invert]]
            [central-system.ocpp-responses :as ocpp]))


;----------------------------------------
; Wamp message schema
;+----------------+---------+-----------+-----------+---------------+
;| Elem #1        | Elem #2 | Elem #3   | Elem #4   | Elem #5       |
;+----------------+---------+-----------+-----------+---------------+
;| 2 (CALL)       |  msgId  | action    | {payload} | -             |
;+----------------+---------+-----------+-----------+---------------+
;| 3 (CALLRESULT) |  msgId  | {payload} | -         | -             |
;+----------------+---------+-----------+-----------+---------------+
;| 4 (CALLERROR)  |  msgId  | err_code  | err_descr | {err_details} |
;+----------------+---------+-----------+-----------+---------------+


;; ----------------------------------------
;; State
;; ----------------------------------------

(def connections (atom {}))
; structure :ip, :async-channel, :rpc-calls
; rpc-calls: {msgId => {:origin, :action}, }

(defn get-connections []
  (into {} (for [[k v] @connections]
             [k (dissoc v :async-channel)])))

(defn connect!
  [tenant-id box-serial {ip :remote-addr, channel :async-channel}]
  (log/infof "Chargepoint %s connected from %s" box-serial ip)
  (publish-connection-event :connected tenant-id box-serial ip)
  (swap! connections
         assoc box-serial {:ip            ip
                           :async-channel channel
                           :tenant-id     tenant-id
                           :rpc-calls     {}}))

(defn connected? [box-serial]
  (contains? @connections box-serial))

(defn total [] (count @connections))

(defn disconnect! [tenant-id box-serial {ip :remote-addr channel :async-channel} status]
  (log/info "Chargepoint" box-serial "disconnected with status" status)
  (publish-connection-event :disconnected tenant-id box-serial ip status)
  (swap! connections dissoc box-serial))

(defn store-ongoing-call! [box-serial msg-id origin action]
  (swap! connections assoc-in [box-serial :rpc-calls msg-id] {:origin origin
                                                              :action action}))

(defn remove-call! [box-serial msg-id]
  (swap! connections update-in [box-serial :rpc-calls] dissoc msg-id))

(defn ongoing-call [box-serial msg-id]
  (get-in @connections [box-serial :rpc-calls msg-id]))


;; ----------------------------------------
;; WAMP
;; ----------------------------------------

(def wamp-type
  {:CALL       2
   :CALLRESULT 3
   :CALLERROR  4})

(defn- wamp-msg-id []
  (->> #(rand-nth "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789")
       repeatedly
       (take 25)
       (apply str)))


;; ----------------------------------------
;; WS handler
;; ----------------------------------------

(defn- firmware-error [box-serial msg-id payload error-message]
  (let [err {:message           error-message
             :msg-id            msg-id
             :charge-box-serial box-serial
             :payload           payload}]
    (log/error "Possible firmware error:\n" (json/pretty err))))

(defn call! [tenant-id box-serial action payload]
  (log/info "Sending" action " to " box-serial "with params" payload)
  (if-let [channel (get-in @connections [box-serial :async-channel])]
    (let [msg-id (wamp-msg-id)
          wamp-call [(:CALL wamp-type) msg-id action payload]
          payload (json/camel wamp-call)]
      (store-ongoing-call! box-serial msg-id :server action)
      (publish-ocpp-msg! :to-box nil tenant-id box-serial payload)
      (ws/send! channel payload)
      msg-id)
    (log/errorf "Charge point %s disconnected, cannot send %s" box-serial action)))


(defn- on-receive-call [channel tenant-id box-serial [wamp-msg-id wamp-action wamp-payload] msg]
  (log/infof "Received %s from %s with payload:\n%s"
             wamp-action box-serial (json/pretty msg))
  (publish-ocpp-msg! :from-box nil tenant-id box-serial msg)
  (store-ongoing-call! box-serial wamp-msg-id :evse nil)
  (let [{:keys [ok-payload err-code err-desc]} (ocpp/get-reply tenant-id box-serial :call wamp-action wamp-payload)
        wamp-response (json/camel (if (some? ok-payload)
                                    [(:CALLRESULT wamp-type) wamp-msg-id ok-payload]
                                    [(:CALLERROR wamp-type) wamp-msg-id err-code err-desc]))]
    (log/infof "Replying to %s from %s with payload:\n%s"
               wamp-action box-serial (json/pretty wamp-response))
    (publish-ocpp-msg! :to-box wamp-action tenant-id box-serial wamp-response)
    (ws/send! channel wamp-response)
    (remove-call! box-serial wamp-msg-id)))


(defn- on-receive-response [type tenant-id box-serial [msg-id payload] msg]
  (if-let [call (ongoing-call box-serial msg-id)]
    (do
      (publish-ocpp-msg! :from-box (:action call) tenant-id box-serial msg)
      (if (= :server (:origin call))
        (let [action (str (:action call) "Res")]
          (log/infof "Received %s from %s with payload:\n%s"
                     action box-serial (json/pretty payload))
          (ocpp/get-reply tenant-id box-serial type action payload)
          (remove-call! box-serial msg-id))
        (firmware-error box-serial msg-id payload
                        "Received wamp-response to a call initiated from the charge-point")))
    (do
      (publish-ocpp-msg! :from-box nil tenant-id box-serial msg)
      (firmware-error box-serial msg-id payload
                      "Received wamp-response for non-existent message-id"))))

(defn- on-receive [channel tenant-id box-serial msg]
  (let [[msg-type-id msg-id x y] (json/read-str msg)
        msg-type (get (map-invert wamp-type) msg-type-id)]
    (case msg-type
      :CALL (on-receive-call channel tenant-id box-serial [msg-id x y] msg)
      :CALLRESULT (on-receive-response :result tenant-id box-serial [msg-id x] msg)
      :CALLERROR (on-receive-response :error tenant-id box-serial [msg-id x] msg)
      (do
        (publish-ocpp-msg! :from-box nil tenant-id box-serial msg)
        (firmware-error box-serial msg-id x
                        (str "Unsupported message-type " msg-type))))))

(defn handler [req tenant-id box-serial]
  (wsutils/with-subproto-channel req channel "ocpp1.5"
                                 (connect! tenant-id box-serial req)
                                 (ws/on-receive channel #(on-receive channel tenant-id box-serial %))
                                 (ws/on-close channel (fn [status]
                                                        (disconnect! tenant-id box-serial req status)))))
