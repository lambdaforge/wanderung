(ns wanderung.core
  (:require [datahike.api :as d]
            [datomic.client.api :as dt]
            [wanderung.datomic :as wd]))

(defn datomic->datahike [datomic-config datomic-name datahike-config]
  (let [datomic-conn (dt/connect (dt/client datomic-config) {:db-name datomic-name})
        datomic-data (wd/extract-datomic-data datomic-conn)
        datahike-conn (d/connect datahike-config)]
    @(d/migrate datahike-conn datomic-data)))

