(ns wanderung.core
  (:require [datahike.api :as d]
            [datomic.client.api :as dt]
            [datomic.api :as da]
            [wanderung.datomic-cloud :as wdc]
            [wanderung.datomic-free :as wdf]
            [wanderung.sql :as ws]))

(defn datomic-free->datahike [datomic-config datahike-config]
  (let [datomic-conn (da/connect datomic-config)
        datomic-data (wdf/extract-datomic-free-data datomic-conn)
        datahike-conn (d/connect datahike-config)]
    @(d/migrate datahike-conn datomic-data)))

(defn datomic-cloud->datahike [datomic-config datomic-name datahike-config]
  (let [datomic-conn (dt/connect (dt/client datomic-config) {:db-name datomic-name})
        datomic-data (wdc/extract-datomic-cloud-data datomic-conn)
        datahike-conn (d/connect datahike-config)]
    @(d/migrate datahike-conn datomic-data)))

(defn sql->datahike [sql-config datahike-config]
  (comment
    {:subprotocol "h2"
     :subname (str (System/getProperty "java.io.tmpdir")
                   "/princess_bride.h2")}
    )
  (let [datahike-conn (d/connect datahike-config)]
    (ws/migrate sql-config datahike-conn)))