(ns wanderung.sql-test
  (:require [clojure.test :refer :all]
            [datahike.api :as d]
            [hugsql.core :as h]
            [wanderung.core :refer [sql->datahike]]))

(h/def-db-fns "wanderung/sql/test.sql")

(deftest sql->datahike-test
  (let [sql-db {:subprotocol "h2"
                :subname     (str (System/getProperty "java.io.tmpdir")
                                  "/princess_bride.h2")}
        uri "datahike:mem://sql-test"
        _ (do
            (d/delete-database uri)
            (d/create-database uri))]
    (drop-characters-table sql-db)
    (create-characters-table sql-db)

    (insert-character sql-db {:name "Westley" :specialty "love"})
    (insert-characters sql-db {:characters [["Vizzini" "intelligence"]
                                        ["Fezzik" "strength"]
                                        ["Inigo Montoya" "swordmanship"]]})

    (clojure.java.jdbc/with-db-transaction
      [tx sql-db]
      (insert-character tx {:name "Miracle Max" :specialty "miracles"})
      (insert-character tx {:name "Valerie" :specialty "speech interpreter"}))

    (sql->datahike sql-db uri)

    (let [conn (d/connect uri)]
      (testing "Schema"
        (is (= #{[:id :db.type/long :db.cardinality/one]
                 [:name :db.type/string :db.cardinality/one]
                 [:specialty :db.type/string :db.cardinality/one]
                 [:created_at :db.type/instant :db.cardinality/one]}
               (d/q '[:find ?i ?v ?c :where [?e :db/ident ?i] [?e :db/valueType ?v] [?e :db/cardinality ?c]] @conn))))
      (testing "Data"
        (is (= #{["Fezzik"
                  "strength"]
                 ["Inigo Montoya"
                  "swordmanship"]
                 ["Miracle Max"
                  "miracles"]
                 ["Valerie"
                  "speech interpreter"]
                 ["Vizzini"
                  "intelligence"]
                 ["Westley"
                  "love"]}
               (d/q '[:find ?n ?s :where [?e :name ?n] [?e :specialty ?s]] @conn)))))))