(ns wanderung.datomic-cloud
  (:require [datomic.client.api :as d]))

(defn extract-datomic-cloud-data [conn]
  (let [db (d/db conn)
        txs (sort-by first (d/q '[:find ?tx ?inst
                                  :in $ ?bf
                                  :where
                                  [?tx :db/txInstant ?inst]
                                  [(< ?bf ?inst)]]
                                db
                                (java.util.Date. 70)))
        schema-attrs #{:db/cardinality :db/valueType :db/unique}
        id->ident (->> (d/q '[:find ?e ?id :where [?e :db/ident ?id]] db)
                       (into {}))]
    (apply concat (for [[tid tinst] txs]
                    (->> (d/q '[:find ?e ?at ?v ?t ?added
                                :in $ ?t
                                :where
                                [?e ?a ?v ?t ?added]
                                [?a :db/ident ?at]
                                (not [?a :db/ident :db/txInstant])]
                              (d/history db) tid)
                         (map (fn [[_ a _ _ _ :as entity]] (if (schema-attrs a) (update entity 2 id->ident) entity)))
                         (sort-by first)
                         (into [[tid :db/txInstant tinst tid true]]))))))