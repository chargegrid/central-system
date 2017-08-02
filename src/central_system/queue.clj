(ns central-system.queue
  (:require [langohr.core :as rmq]
            [langohr.channel :as lch]
            [langohr.basic :as lb]
            [central-system.settings :refer [config]]
            [central-system.json :as json]
            [clj-time.core :refer [now]]
            [clojure.tools.logging :refer [info]]))

(defn- shutdown [conn & channels]
  (info "Closing RabbitMQ channel & connection")
  (doseq [ch channels]
    (rmq/close ch))
  (rmq/close conn))

(let [ssn-channel (atom nil)
      ocpp-channel (atom nil)
      connections-channel (atom nil)]
  ;; Create a connection, open a channel
  (defn setup []
    (let [conf (:amqp config)
          uri (:url conf)
          connection (rmq/connect {:uri uri})
          ssn-ch (lch/open connection)
          ocpp-ch (lch/open connection)
          cx-ch (lch/open connection)]
      (reset! ssn-channel ssn-ch)
      (reset! ocpp-channel ocpp-ch)
      (reset! connections-channel cx-ch)
      (.addShutdownHook (Runtime/getRuntime)
                        (Thread. #(shutdown connection ssn-ch ocpp-ch)))))

  ;; Publish a session
  (defn publish-session [ssn]
    (let [conf (:amqp config)
          payload (json/snake ssn)
          exchange (:sessions-exchange conf)
          routing-key (:raw-sessions-routing-key conf)]
      (lb/publish @ssn-channel exchange routing-key payload {:content-type "application/json" :persistent true})))

  ;; Publish an occp log msg
  (defn publish-ocpp-msg! [direction response-to tenant-id box-serial ocpp-message]
    (let [rmq-message (json/snake {:tenant-id         tenant-id
                                   :charge-box-serial box-serial
                                   :ocpp-message      ocpp-message
                                   :response-to       response-to
                                   :occurred-at       (now)
                                   :direction         direction})
          conf (:amqp config)
          exchange (:ocpp-exchange conf)
          routing-key (:ocpp-msg-routing-key conf)]
      (info "OCPP log" rmq-message)
      (lb/publish @ocpp-channel exchange routing-key rmq-message {:content-type "application/json" :persistent true})))



  ;; Publish (dis)connection event
  (defn publish-connection-event
    ([event tenant-id box-serial ip]
     (publish-connection-event event tenant-id box-serial ip nil))
    ([event tenant-id box-serial ip status]
     (let [conf (:amqp config)
           conn-event {:event-type        event
                       :occurred-at       (now)
                       :tenant-id         tenant-id
                       :charge-box-serial box-serial
                       :ip                ip
                       :status            status}
           conn-event-json (json/snake conn-event)
           exchange (:ocpp-exchange conf)
           routing-key (:connection-events-routing-key conf)]
       (lb/publish @connections-channel exchange routing-key conn-event-json {:content-type "application/json" :persistent true})))))
