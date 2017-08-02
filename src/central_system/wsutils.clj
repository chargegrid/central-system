(ns central-system.wsutils
  (:require [org.httpkit.server :refer [accept]]
            [clojure.string :refer [split trim lower-case]])
  (:import [org.httpkit.server AsyncChannel]))

; Add support for custom websocket handshake. Take from:
; https://github.com/http-kit/http-kit/issues/64

(defn subprotocol? [proto req]
  (if-let [protocols (get-in req [:headers "sec-websocket-protocol"])]
    (some #{proto}
          (map #(lower-case (trim %))
               (split protocols #",")))))

(defmacro with-subproto-channel
  [request ch-name subproto & body]
  `(let [~ch-name (:async-channel ~request)]
     (if (:websocket? ~request)
       (if-let [key# (get-in ~request [:headers "sec-websocket-key"])]
         (if (subprotocol? ~subproto ~request)
           (do
             (.sendHandshake ~(with-meta ch-name {:tag `AsyncChannel})
                             {"Upgrade"                "websocket"
                              "Connection"             "Upgrade"
                              "Sec-WebSocket-Accept"   (accept key#)
                              "Sec-WebSocket-Protocol" ~subproto})
             ~@body
             {:body ~ch-name})
           {:status 400 :body "missing or bad WebSocket-Protocol"})
         {:status 400 :body "missing or bad WebSocket-Key"})
       {:status 400 :body "not websocket protocol"})))