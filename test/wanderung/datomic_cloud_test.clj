(ns wanderung.datomic-cloud-test
  (:require [clojure.test :refer :all]
            [wanderung.datomic-cloud :as datomic-cloud]))

(def sample-datom [119 :person/name "Mjao" nil true])
(def sample-datom-2 [119 :person/mother 120 nil true])

(def datom->list-form #'datomic-cloud/datom->list-form)

(deftest datom->list-form-test
  (is (= [:db/add "tmp-119" :person/name "Mjao"]
         (datom->list-form {:ref-attribs #{} :tempids {}}
                           sample-datom)))
  (is (= [:db/add 49 :person/name "Mjao"]
         (datom->list-form {:ref-attribs #{}
                            :tempids {"tmp-119" 49
                                      "tmp-120" 50}}
                           sample-datom)))
  (is (= [:db/add 17 :person/mother 1024]
         (datom->list-form {:ref-attribs #{:person/mother}
                            :tempids {"tmp-119" 17
                                      "tmp-120" 1024}}
                           sample-datom-2))))
