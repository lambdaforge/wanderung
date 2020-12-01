(ns wanderung.datahike
  (:require [datahike.api :as d]))


(def find-tx-datoms
  '[:find ?tx ?inst
    :in $ ?bf
    :where
    [?tx :db/txInstant ?inst]
    [(< ?bf ?inst)]])

(def find-datoms-in-tx
  '[:find ?e ?a ?v ?t ?added
    :in $ ?t
    :where
    [?e ?a ?v ?t ?added]
    (not [?e :db/txInstant ?v ?t ?added])])

(def find-datoms-in-tx-ref
  '[:find ?e ?at ?v ?t ?added
    :in $ ?t
    :where
    [?e ?a ?v ?t ?added]
    [?a :db/ident ?at]
    (not [?a :db/ident :db/txInstant])])


(defn extract-datahike-data [conn]
  (let [db (d/db conn)
        txs (sort-by first (d/q find-tx-datoms db (java.util.Date. 70))) ;; TODO: why 70?
        schema-attrs #{:db/cardinality :db/valueType :db/unique}
        id->ident (->> (d/q '[:find ?e ?id :where [?e :db/ident ?id]] db)
                       (into {}))
        query {:query find-datoms-in-tx
               :args [(d/history db)]}]
    (letfn [(update-schema-attr [[_ a _ _ _ :as entity]]
              (if (schema-attrs a)
                (update entity 2 id->ident)
                entity))]
      (mapcat
       (fn [[tid tinst]]
         (->> (d/q (update-in query [:args] conj tid))
              (map update-schema-attr)
              (sort-by first)
              (into [[tid :db/txInstant tinst tid true]])))
       txs))))

