(ns wanderung.core
  (:require [datahike.api :as d]
            [datomic.client.api :as dt]
            [wanderung.datomic :as wd]))

(defn datomic->datahike [datomic-config datomic-name datahike-config]
  (let [datomic-conn (dt/connect datomic-config)
        datomic-data (wd/extract-datomic-data datomic-conn {:db-name datomic-name})
        datahike-conn (d/connect datahike-config)]
    @(d/migrate datahike-conn datomic-data)))

