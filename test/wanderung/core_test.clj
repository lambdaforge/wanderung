(ns wanderung.core-test
  (:require [clojure.test :refer :all]
            [wanderung.core :as wanderung]
            [datahike.api :as dh]
            [datomic.client.api :as dt]
            [clojure.edn :as edn]
            [taoensso.timbre :as timbre]
            [clojure.java.io :as io]
            [datahike.api :as d])
  (:import [java.io File]))

(timbre/set-level! :warn)

(def testdata (-> "testdatoms.edn"
                  io/resource
                  slurp
                  edn/read-string))

(defn datomic-cfg [db-name]
  {:wanderung/type :datomic
   :server-type :dev-local
   :storage-dir :mem
   :name db-name
   :system "CI"})

(defn setup-datomic-conn
  "Given a database name creates a new datomic in-memory database and returns a connection."
  [db-name]
  (let [client-cfg (datomic-cfg db-name)
        db-cfg {:db-name db-name}
        dt-client (dt/client client-cfg)
        _ (dt/delete-database dt-client db-cfg)
        _ (dt/create-database dt-client db-cfg)]
    (dt/connect dt-client db-cfg)))

(defn datahike-cfg [db-name]
  {:wanderung/type :datahike
   :store {:backend :mem
           :id db-name}})

(defn setup-datahike-conn
  "Given a database name creates a new datahike in-memory database and returns a connection."
  [db-name]
  (let [cfg (datahike-cfg db-name)]
    (dh/delete-database cfg)
    (dh/create-database cfg)
    (dh/connect cfg)))

(defn setup-data [tx-fn conn]
  (tx-fn conn {:tx-data [{:db/ident :person/name
                                   :db/valueType :db.type/string
                                   :db/unique :db.unique/identity
                                   :db/cardinality :db.cardinality/one}
                                  {:db/ident :person/age
                                   :db/valueType :db.type/long
                                   :db/cardinality :db.cardinality/one}
                                  {:db/ident :person/siblings
                                   :db/valueType :db.type/ref
                                   :db/cardinality :db.cardinality/many}]})
  (tx-fn conn {:tx-data [{:db/id -1
                                   :person/name "Alice"
                                   :person/age 25}
                                  {:db/id -2
                                   :person/name "Bob"
                                   :person/age 35}
                                  {:person/name "Charlie"
                                   :person/age 45
                                   :person/siblings [-1 -2]}]}))

(deftest test-nippy-migration
  (let [a {:wanderung/type :nippy
           :filename (str (File/createTempFile "a_file" ".nippy"))}
        b {:wanderung/type :nippy
           :filename (str (File/createTempFile "b_file" ".nippy"))}]
    (wanderung/datoms-to-storage a testdata)
    (wanderung/migrate a b)
    (is (= testdata (wanderung/datoms-from-storage b)))))

(deftest test-datomic->datahike-basic
  (let [db-name "dt->dh-test-basic"
        dt-conn (setup-datomic-conn db-name)]
    (setup-datahike-conn db-name)
    (setup-data dt/transact dt-conn)
    (wanderung/migrate (datomic-cfg db-name) (datahike-cfg db-name))
    (testing "test basic data and query"
      (letfn [(coerce-result [result]
                (->> result
                     (map #(update (first %) :person/siblings set))
                     set))]
        (let [dh-conn (dh/connect (datahike-cfg db-name))
              query '[:find (pull ?e [:person/name :person/age {:person/siblings [:person/name]}])
                      :where [?e :person/name _]]
              dt-result (->> (dt/db dt-conn)
                             (dt/q query)
                             coerce-result)
              dh-result (->> (dh/db dh-conn)
                             (dh/q query)
                             coerce-result)]
          (is (= dt-result
                 dh-result)))))))

(deftest test-datomic->datahike-history
  (let [db-name "dt->dh-test-history"
        dt-conn (setup-datomic-conn db-name)]
    (setup-datahike-conn db-name)
    (setup-data dt/transact dt-conn)
    (dt/transact dt-conn {:tx-data [[:db/retractEntity [:person/name "Alice"]]]})
    (wanderung/migrate (datomic-cfg db-name) (datahike-cfg db-name))
    (let [dh-conn (dh/connect (datahike-cfg db-name))
          dh-db (dh/db dh-conn)
          dt-db (dt/db dt-conn)]
      (testing "current snapshot without retracted data"
        (letfn [(coerce-result [result]
                  (->> result
                       (map #(update (first %) :person/siblings set))
                       set))]
          (let [query '[:find (pull ?e [:person/name :person/age {:person/siblings [:person/name]}])
                        :where [?e :person/name _]]
                dt-result (->> dt-db
                               (dt/q query)
                               coerce-result)
                dh-result (->> dh-db
                               (dh/q query)
                               coerce-result)]
            (is (= dt-result
                   dh-result)))))
      (testing "history snapshot with retraction time"
        (let [query '[:find ?n ?d ?op
                      :where
                      [?e :person/name ?n ?t ?op]
                      [?t :db/txInstant ?d]]
              dt-result (->> dt-db
                             dt/history
                             (dt/q query)
                             set)
              dh-result (->> dh-db
                             dh/history
                             (dh/q query)
                             set)]
          (is (= dt-result
                 dh-result))))
      (testing "schema"
        (let [query '[:find (pull ?e [*])
                      :in $ [?attrs ...]
                      :where
                      [?e :db/ident ?attrs]]
              attributes [:person/name :person/age :person/siblings]
              dt-result (->> (dt/q query
                                   dt-db
                                   attributes)
                             (map first)
                             (map (fn [{:keys [db/unique] :as attr}]
                                    (cond-> attr
                                            true (dissoc :db/id)
                                            true (update :db/valueType :db/ident)
                                            true (update :db/cardinality :db/ident)
                                            (some? unique) (update :db/unique :db/ident))))
                             set)
              dh-result (->> (dh/q query
                                   dh-db
                                   attributes)
                             (map (comp #(dissoc % :db/id) first))
                             set)]
          (is (= dt-result
                 dh-result)))))))

(deftest test-datahike->datomic-basic
  (let [db-name "dh->dt-test-basic"
        dh-conn (setup-datahike-conn db-name)]
    (setup-datomic-conn db-name)
    (setup-data dh/transact dh-conn)
    (wanderung/migrate (datahike-cfg db-name) (datomic-cfg db-name))
    (testing "test basic data and query"
      (letfn [(coerce-result [result]
                (->> result
                     (map #(update (first %) :person/siblings set))
                     set))]
        (let [dt-conn (dt/connect (dt/client (datomic-cfg db-name)) {:db-name db-name})
              query '[:find (pull ?e [:person/name :person/age {:person/siblings [:person/name]}])
                      :where [?e :person/name _]]
              dh-result (->> (dh/db dh-conn)
                             (dh/q query)
                             coerce-result)
              dt-result (->> (dt/db dt-conn)
                             (dt/q query)
                             coerce-result)]
          (is (= dh-result
                 dt-result)))))))

(deftest test-datahike->datomic-history
  (let [db-name "dh->dt-test-history"
        dh-conn (setup-datahike-conn db-name)]
    (setup-datomic-conn db-name)
    (setup-data dh/transact dh-conn)
    (dh/transact dh-conn {:tx-data [[:db/retractEntity [:person/name "Alice"]]]})
    (wanderung/migrate (datahike-cfg db-name) (datomic-cfg db-name))
    (let [dt-conn (dt/connect (dt/client (datomic-cfg db-name)) {:db-name db-name})
          dt-db (dt/db dt-conn)
          dh-db (dh/db dh-conn)]
      (testing "current snapshot without retracted data"
        (letfn [(coerce-result [result]
                  (->> result
                       (map #(update (first %) :person/siblings set))
                       set))]
          (let [query '[:find (pull ?e [:person/name :person/age {:person/siblings [:person/name]}])
                        :where [?e :person/name _]]
                dh-result (->> dh-db
                               (dh/q query)
                               coerce-result)
                dt-result (->> dt-db
                               (dt/q query)
                               coerce-result)]
            (is (= dh-result
                   dt-result)))))
      (testing "history snapshot with retraction time"
        (let [query '[:find ?n ?d ?op
                      :where
                      [?e :person/name ?n ?t ?op]
                      [?t :db/txInstant ?d]]
              dh-result (->> dh-db
                             dh/history
                             (dh/q query)
                             set)
              dt-result (->> dt-db
                             dt/history
                             (dt/q query)
                             set)]
          (is (= dh-result
                 dt-result))))
      (testing "schema"
        (let [query '[:find (pull ?e [*])
                      :in $ [?attrs ...]
                      :where
                      [?e :db/ident ?attrs]]
              attributes [:person/name :person/age :person/siblings]
              dh-result (->> (dh/q query
                                   dh-db
                                   attributes)
                             (map (comp #(dissoc % :db/id) first))
                             set)
              dt-result (->> (dt/q query
                                   dt-db
                                   attributes)
                             (map first)
                             (map (fn [{:keys [db/unique] :as attr}]
                                    (cond-> attr
                                            true (dissoc :db/id)
                                            true (update :db/valueType :db/ident)
                                            true (update :db/cardinality :db/ident)
                                            (some? unique) (update :db/unique :db/ident))))
                             set)]
          (is (= dh-result
                 dt-result)))))))