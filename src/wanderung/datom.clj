(ns wanderung.datom
  (:require [clojure.spec.alpha :as spec]))

(spec/def :datom/eid (spec/or :id number? :tempid string?))
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;
;;;  Datoms similarity: Used for sanity checking
;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- inc-nil [x]
  (inc (or x 0)))

(defn- build-eid-features
  "Constructs a map from each entity id to a feature value that is invariant of entity id assignment"
  [ref-attrib datoms]
  (let []
    (reduce
     (fn [dst [tid transaction-group]]
       (reduce
        (fn [dst [eid attr value _ add?]]
          (let [step (fn [dst id what & data] (update-in dst [id [tid add? attr what data]] inc-nil))]
            (if (ref-attrib attr)
              (if (= eid value)
                (step dst eid :both)
                (-> dst
                    (step eid :eid)
                    (step value :value)))
              (step dst eid :eid value))))
        dst
        transaction-group))
     {}
     (map-indexed vector (transaction-groups datoms)))))

(defn- datoms-feature
  "Compute a 'feature' value of all the datoms that is invariant of the entity id mapping"
  [datoms]
  (let [ref-attrib (-> datoms
                       datoms->entity-attribute-map
                       ref-attrib-set)
        eid-map (build-eid-features ref-attrib datoms)]
    (reduce
     (fn [dst [eid attr value tid add?]]
       (let [key [(eid-map eid) attr (if (ref-attrib attr) (eid-map value) value) (eid-map tid) add?]]
         (update dst key inc-nil)))
     {}
     datoms)))

(defn similar-datoms?
  "Returns true if two sequences of datoms represent the same database. It generally returns false if they don't represent the same database, but can in rare cases return true. This function can nevertheless be used for sanity checking in order to detect common errors."
  [datoms-a datoms-b]
  (= (datoms-feature datoms-a)
     (datoms-feature datoms-b)))
