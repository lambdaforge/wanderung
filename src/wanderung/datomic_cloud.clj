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

(defn create-schema-mapping [conn]
  (let [db (d/db conn)
        query '[:find ?e ?ident
                :where
                [?e :db/ident ?ident]]]
    (into {} (d/q query db))))

(defn extract-datomic-cloud-data
  "Extracts all transactions from Datomic with keyword attributes given a Datomic connection.
  Internally Datomic uses the first transactions to initialize the system schema and identifiers
  which are Datomic specific and not relevant for import.
  Currently, it takes 5 transactions, so the 6th is the first user specific one."
  [conn]
  (let [system-attributes #{:db.install/valueType :db/valueType :db/cardinality :db/unique}
        start-tx 6                                          ;; first user transaction
        schema-mapping (create-schema-mapping conn)
        map-db-ident (map (fn [[e a v tx added]]
                            (let [new-a (schema-mapping a)]
                              (assert new-a)
                              [e new-a (if (system-attributes new-a)
                                         (schema-mapping v)
                                         v) tx added])))
        data-extract (mapcat (fn [{:keys [data]}]
                               (into [] map-db-ident data)))
        tx-data (d/tx-range conn {:start start-tx :limit -1})]
    (into [] data-extract tx-data)))