(ns wanderung.core
  (:require [datahike.api :as d]
            [datomic.api :as dt]
            [wanderung.datomic :as wd]))

(defn datomic->datahike [datomic-config datahike-config]
  (let [datomic-conn (dt/connect datomic-config)
        datomic-data (wd/extract-datomic-data datomic-conn)
        datahike-conn (d/connect datahike-config)]
    @(d/migrate datahike-conn datomic-data)))

