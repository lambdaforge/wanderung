(ns wanderung.sql
  (:require [hugsql.core :as h]
            [datahike.api :as d]
            [camel-snake-kebab.core :as csk]
            [clj-time.coerce :as tc]))

(def db
  {:subprotocol "h2"
   :subname (str (System/getProperty "java.io.tmpdir")
                 "/princess_bride.h2")})

(h/def-db-fns "sql/example.sql")
(h/def-sqlvec-fns "sql/example.sql")

(drop-characters-table db)

(create-characters-table db)

(insert-character db {:name "Westley" :specialty "love"})
(insert-character db {:name "Buttercup" :specialty "beauty"})
(insert-characters db {:characters [["Vizzini" "intelligence"]
                                    ["Fezzik" "strength"]
                                    ["Inigo Montoya" "swordmanship"]]})



(character-by-name db {:name "buttercup"})

(characters-by-ids-specify-cols db {:ids [1 2] :cols ["name" "specialty"]})

(defn sql-type->datahike-type [t]
  (case t
    "FOO" :db.type/foo
    "INTEGER" :db.type/long
    "TIMESTAMP" :db.type/instant
    "VARCHAR" :db.type/string
    :db.type/string))

(defn sql-schema->datahike-schema [db]
  (->> (all-tables db)
       (mapcat (fn [{:keys [table_name]}]
                 (->> (all-schema db {:table_name table_name})
                      (map (fn [{:keys [type_name column_name]}] {:db/id (d/tempid :db.part/db)
                                                                  :db/ident (-> column_name clojure.string/lower-case keyword)
                                                                  :db/valueType (sql-type->datahike-type type_name)
                                                                  :db/cardinality :db.cardinality/one})))))
       vec))


(defn sql-data->datahike-data [db]
  (->> (snip-query db {:select (select-snip {:cols ["*"]})
                       :from (select-snip {:tables ["CHARACTERS"]})})
       (mapv #(dissoc % :id))))


(def uri "datahike:mem://sql-clone")

(d/delete-database uri)

(d/create-database uri)

(def conn (d/connect uri))

(d/transact conn (sql-schema->datahike-schema db))

(d/transact conn (sql-data->datahike-data db))


(class #inst "2020-01-17T16:06:53.028000000-00:00")

(d/transact conn [{:db/ident :foo
                   :db/valueType :db.type/instant
                   :db/cardinality :db.cardinality/one}])

(d/transact conn [{:foo #inst "2020-01-17T16:44:45.148-00:00"}])
