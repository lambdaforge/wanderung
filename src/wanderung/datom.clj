(ns wanderung.datom
  (:require [clojure.spec.alpha :as spec]))

(spec/def :datom/eid number?)
(spec/def :datom/attribute keyword?)
(spec/def :datom/value any?)
(spec/def :datom/transaction-id :datom/eid)
(spec/def :datom/add? boolean?)

(spec/def :datom/tuple (spec/cat :eid :datom/eid
                                 :attribute :datom/attribute
                                 :value :datom/value
                                 :transaction-id :datom/transaction-id
                                 :add? :datom/add?))

(defn datom-eid [[eid _ _ _ _]] eid)
(defn datom-attribute [[_ a _ _ _]] a)
(defn datom-value [[_ _ v _ _]] v)
(defn datom-tid [[_ _ _ tid _]] tid)
(defn datom-add? [[_ _ _ _ add?]] add?)

(spec/def :datom/tuples (spec/coll-of :datom/tuple))

(defn transaction-groups "Return a sequence of sequences of datoms with common transaction id"
  [datoms]
  (partition-by datom-tid datoms))

(defn datoms->entity-attribute-map "Given a list of datoms, create a map of all entities and their attributes after all list-forms have been applied. 

This implementation is only an approximation but good enough for figuring out which entities are part of the schema and used to define attributes whose values should be entity references."
  [datoms]
  (reduce
   (fn [dst [eid attr value tx add?]]
     (if add?
       (assoc-in dst [eid attr] value)
       (update dst eid dissoc attr)))
   {}
   datoms))

(defn datoms->entity-transaction-map
  "Build a map from every entity id to the first transaction id where it occurs"
  [datoms]
  (reduce (fn [dst [e a v t a?]] (update dst e #(or % t)))
          {}
          datoms))

(defn ref-attrib-set "Given an entity-attribute-map, get the set of attributes whose values are refs to other entities according to the schema"
  [entity-attribute-map]
  (into #{:db.install/attribute}
        (comp (map (fn [[k v]] (if (= :db.type/ref (:db/valueType v)) (:db/ident v))))
              (filter some?))
        entity-attribute-map))
