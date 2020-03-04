(ns user
  (:require [wanderung.core :as w]
            [datahike.api :as d]))

;; assuming Datomic already exists
(def datomic-cfg {:server-type :ion
                  :region "<your AWS Region>" ;; e.g. us-east-1
                  :system "<system-name>"
                  :creds-profile "<your_aws_profile_if_not_using_the_default>"
                  :endpoint "http://entry.<system-name>.<region>.datomic.net:8182/"
                  :proxy-port 8182})

(def datomic-name "taxonomy")

;; create datahike dev instance
(def datahike-uri "datahike:file:///tmp/migration")

(d/create-database datahike-uri)

(w/datomic-cloud->datahike datomic-cfg datomic-name datahike-uri)
