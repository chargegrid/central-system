(ns central-system.dynamo
  (:require [faraday-atom.core :as dyn]
            [central-system.settings :refer [config]]))

(def client-opts
  {;;; For DDB Local just use some random strings here, otherwise include your
   ;;; production IAM keys:
   :access-key (-> config :aws :access-key)
   :secret-key (-> config :aws :secret-key)
   :endpoint   (-> config :aws :dyn-endpoint)})

   ;;; You may optionally override the default endpoint if you'd like to use DDB
   ;;; Local or a different AWS Region (Ref. http://goo.gl/YmV80o), etc.:
  ;  :endpoint "http://localhost:8000"                   ; For DDB Local
   ;; :endpoint "http://dynamodb.eu-west-1.amazonaws.com" ; For EU West 1 AWS region


(def sessions-table
  (dyn/table-client client-opts
                    ;;the name of the table as a keyword
                    :sessions-atom-table
                    ;;this is the key we will use to store atom identity in the table.
                    :atom/id))

;; Creates the table with a default read/write throughput of 8/8.
(defn create-table []
  (dyn/ensure-table! sessions-table))

