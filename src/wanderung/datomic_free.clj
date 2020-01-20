(ns wanderung.datomic-free
  (:require [datomic.api :as d]))

(defn extract-datomic-free-data [conn]
  (let [db (d/db conn)
        txs (sort-by first (d/q '[:find ?tx ?inst
                                  :in $ ?bf
                                  :where
                                  [?tx :db/txInstant ?inst]
                                  [(< ?bf ?inst)]]
                                db
                                (java.util.Date. 70)))]
    (apply concat (for [[tid tinst] txs]
                    (into [[tid :db/txInstant tinst tid true]]
                          (sort-by
                            first
                            (d/q '[:find ?e ?at ?v ?t ?added
                                   :in $ ?t
                                   :where
                                   [?e ?a ?v ?t ?added]
                                   [?a :db/ident ?at]
                                   (not [?a :db/ident :db/txInstant])]
                                 (d/history db) tid)))))))
