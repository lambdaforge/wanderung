(ns wanderung.sql
  (:require [hugsql.core :as h]
            [datahike.api :as d]
            [clj-time.coerce :as tc])
  (:import [java.sql Timestamp]))

(h/def-db-fns "sql/inspect.sql")
(h/def-sqlvec-fns "sql/inspect.sql")


(defn- sql-type->datahike-type [t]
  (case t
    "FOO" :db.type/foo
    "INTEGER" :db.type/long
    "TIMESTAMP" :db.type/instant
    "VARCHAR" :db.type/string
    :db.type/string))

(defn sql-schema->datahike-schema [db]
  (mapcat
    (fn [{:keys [table_name]}]
      (map
        (fn [{:keys [type_name column_name]}]
          {:db/id          (d/tempid :db.part/db)
           :db/ident       (-> column_name clojure.string/lower-case keyword)
           :db/valueType   (sql-type->datahike-type type_name)
           :db/cardinality :db.cardinality/one})
        (all-schema db {:table_name table_name})))
    (all-tables db)))


(defn sql-data->datahike-data [db]
  (mapcat
    (fn [{:keys [table_name]}]
      (map
        (fn [c] (->> (dissoc c :id)
                     (reduce-kv
                       (fn [m k v]
                         (assoc m k (if (= (type v) Timestamp)
                                      (tc/to-date (tc/from-sql-time v))
                                      v)))
                       {})))
        (snip-query db {:select (select-snip {:cols ["*"]})
                        :from   (from-snip {:tables [table_name]})})))
    (all-tables db)))

(defn migrate [from to]
  (doall (for [tx (partition 1000 1000 nil (sql-schema->datahike-schema from))]
           (d/transact to (vec tx))))
  (doall
    (for [tx (partition 1000 1000 nil (sql-data->datahike-data from))]
      (d/transact to (vec tx)))))