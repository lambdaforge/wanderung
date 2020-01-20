(defproject io.lambdaforge/wanderung "0.1.0-SNAPSHOT"
  :description "Data migration tool for Datahike"
  :url "https://github.com/lambdaforge/wanderung"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [io.replikativ/datahike "0.2.2-SNAPSHOT"]
                 [clj-time "0.15.2"]
                 [com.datomic/client-cloud "0.8.78" :scope "provided"]
                 [com.layerware/hugsql "0.5.1" :scope "provided"]]
  :profiles {:dev {:dependencies [[org.clojure/test.check "0.9.0"]
                                  [com.datomic/datomic-free "0.9.5697"]
                                  [com.h2database/h2 "1.4.196"]]}}
  :repl-options {:init-ns wanderung.core})
