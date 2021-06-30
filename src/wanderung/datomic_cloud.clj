(ns wanderung.datomic-cloud
  (:require [datomic.client.api :as d]
            [wanderung.datom :as datom]
            [clojure.spec.alpha :as spec]))

(defn- temp-id [eid]
  (str "tmp-" eid))

(defn- map-value-ref
  "Maps the value part of a datom to either a temp id or a true entity id"
  [transaction-context value]
  (let [{:keys [tempids datom eid-tid-map]} transaction-context]
    (if (keyword? value)
      value
      (or (get tempids (temp-id value))
          (let [this-tid (datom/datom-tid datom)
                value-tid (get eid-tid-map value)]
            (assert (number? this-tid))
            (assert (number? value-tid))
            (if (= this-tid value-tid)
              (temp-id value)
              (throw (ex-info "Failed to map value ref"
                              {:value value
                               :tempids tempids
                               :datom datom
                               :referred-entity (get (:entity-map transaction-context) value)}))))))))

(def transaction-temp-id "datomic.tx")

(defn- datom->list-form
  "Transforms a datom to list-form to be part of a transaction. Entity ids will be mapped."
  [transaction-context [eid attrib value tid add? :as datom]]
  (let [tempids (:tempids transaction-context)
        transaction-context (assoc transaction-context :datom datom)
        ref-attribs (:ref-attribs transaction-context)
        _ (assert ref-attribs)
        temp-eid (temp-id eid)]
    [ ;; Whether to add or retract
     (if add? :db/add :db/retract)

     ;; The entity id: either it is a temp id for a new entity,
     ;; or a true entity id. In the special case that the entity id is *this* transaction,
     ;; the special tempid "datomic.tx" is used.
     (if (= eid tid) ;; 
       transaction-temp-id
       (get tempids temp-eid temp-eid))

     ;; The attribute
     attrib

     ;; The value: If it is a reference to another entity,
     ;; the correct entity id needs to be figured out.
     (if (ref-attribs attrib)
       (if (= value tid)
         transaction-temp-id
         (map-value-ref transaction-context value))
       value)]))

(defn- tx-data-from-datoms [transaction-context datoms]
  "Map datoms to list-forms to be the tx-data of a transaction"
  (into []
        (comp (remove #(#{:db.install/attribute} (datom/datom-attribute %)))
              (map (partial datom->list-form transaction-context)))
        datoms))

(defn- perform-transaction
  "Perform a transaction with the datoms and return an updated transaction-context"
  [conn transaction-context datoms]
  (let [tid (-> datoms first datom/datom-tid)
        tx-data (tx-data-from-datoms transaction-context datoms)
        result (d/transact
                conn
                {:tx-data tx-data})
        new-tempids (:tempids result)]
    (update transaction-context
            :tempids
            #(-> %
                 (merge new-tempids)
                 (assoc (temp-id tid) (get new-tempids transaction-temp-id))))))

(defn transact-datoms-to-datomic "Transact datoms to datomic. This function does the opposite of what the function `extract-datomic-cloud-data` does."
  [conn datoms]
  {:pre [(spec/valid? :datom/tuples datoms)]}
  (let [entity-map (datom/datoms->entity-attribute-map datoms)
        ref-attribs (datom/ref-attrib-set entity-map)
        transaction-groups (datom/transaction-groups datoms)]
    (reduce (partial perform-transaction conn)
            {:ref-attribs ref-attribs
             :entity-map entity-map
             :eid-tid-map (datom/datoms->entity-transaction-map datoms)
             :datom nil
             :tempids {}}
            transaction-groups)))



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
                       (into {}))
        query {:query '[:find ?e ?at ?v ?t ?added
                        :in $ ?t
                        :where
                        [?e ?a ?v ?t ?added]
                        [?a :db/ident ?at]
                        (not [?a :db/ident :db/txInstant])]
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
