(defproject io.lambdaforge/wanderung "0.1.1-SNAPSHOT"
  :description "Data migration tool for Datahike"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [io.replikativ/datahike-jdbc "0.1.2-SNAPSHOT"]
                 [io.replikativ/datahike "0.3.3-SNAPSHOT" :exclusions [io.replikativ/konserve]]
                 [com.datomic/client-cloud "0.8.78" :scope "provided"]
                 [com.datomic/client-pro "0.9.63" :scope "provided"]
                 [com.datomic/dev-local "0.9.232"]
                 [com.cognitect/transit-clj "0.8.313"]
                 [com.taoensso/nippy "3.1.1"]
                 [org.clojure/tools.cli "1.0.206"]]
  :resource-paths ["resources"]
  :profiles {:dev {:dependencies [[org.clojure/test.check "0.9.0"]]}
             :test {:dependencies [[lambdaisland/kaocha "1.0.632"]]}}
  :main wanderung.core
  :repl-options {:init-ns wanderung.core})
