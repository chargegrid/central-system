(ns central-system.json
  (:require [clojure.data.json :as json]
            [camel-snake-kebab.core :refer [->camelCase ->snake_case ->kebab-case ->kebab-case-keyword]]
            [camel-snake-kebab.extras :refer [transform-keys]]
            [clojurewerkz.support.json]
            [ring.util.response :refer [content-type]]))

(defn keyword->name [key transform]
  (name (if (keyword? key)
          (transform key)
          key)))

(defn pretty [coll]
  (with-out-str (json/pprint coll)))

(defn camel [coll]
  (json/write-str coll :key-fn #(keyword->name % ->camelCase)))

(defn snake [coll]
  (json/write-str coll :key-fn #(keyword->name % ->snake_case)))

(defn read-str [json]
  (json/read-str json :key-fn ->kebab-case-keyword))