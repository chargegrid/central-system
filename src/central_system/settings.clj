(ns central-system.settings
  (:require [cprop.core :refer [load-config]]))

(def config (load-config))