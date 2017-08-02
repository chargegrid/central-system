(defproject central-system "0.1"
  :description "Central System"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 ;; web (TODO: is all the ring stuff still needed? Or is it included in compojure?)
                 [ring/ring-core "1.4.0"]
                 [ring/ring-devel "1.4.0"]
                 [ring/ring-defaults "0.1.5"]
                 [ring/ring-json "0.4.0"]
                 [ring-cors "0.1.7"]
                 [metosin/ring-http-response "0.6.5"]
                 [metosin/compojure-api "1.0.1"]
                 [compojure "1.4.0"]
                 [http-kit "2.1.18"]
                 [javax.servlet/servlet-api "2.5"]
                 [prone "1.0.0"]
                 ;; logging
                 [org.clojure/tools.logging "0.3.1"]
                 [ch.qos.logback/logback-classic "1.1.6"]
                 [ring.middleware.logger "0.5.0" :exclusions [org.slf4j/slf4j-log4j12]]
                 ;; misc
                 [org.clojure/data.json "0.2.6"]
                 [clj-time "0.11.0"]
                 [com.novemberain/langohr "3.5.0"]
                 [cprop "0.1.9"]
                 [mixradio/faraday-atom "0.3.1"]
                 [clojurewerkz/support "1.1.0"]
                 [camel-snake-kebab "0.3.2"]
                 [funcool/promesa "1.1.0"]]

  :profiles {:uberjar {:aot :all}}

  :target-path "target/%s/"

  :main central-system.core
  :uberjar-name "central-system.jar")
