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

(defn extract-datahike-data
  "Given a Datahike connection extracts datoms from indices."
  [conn]
  (let [db (d/db conn)
        txs (sort-by first (d/q find-tx-datoms db (java.util.Date. 70)))
        query {:query find-datoms-in-tx
               :args [(d/history db)]}]
    (mapcat
     (fn [[tid tinst]]
       (->> (d/q (update-in query [:args] conj tid))
            (sort-by first)
            (into [[tid :db/txInstant tinst tid true]])))
     txs)))

