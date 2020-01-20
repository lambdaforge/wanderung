# Wanderung

Migration tool for Datahike from other databases.

## Usage

Make sure [Leiningen](https://leiningen.org/) is installed. Create a project and add 
[![Clojars Project](http://clojars.org/io.lambdaforge/wanderung/latest-version.svg)](http://clojars.org/io.lambdaforge/wanderung) to dependencies as well as the drivers required for your database (e.g. Datomic-Cloud, Postgresql, H2, ...).

### Example SQL Migration with H2
```clojure
(require 'wanderung.core :as w)
(require 'datahike.api :as d)

;; define your h2 config with data in it
(def h2-config {:subprotocol "h2"
                :subname     (str (System/getProperty "java.io.tmpdir") "/example.h2")})

;; make sure datahike already exists
(def uri "datahike:file:///tmp/example")
(d/create-database uri)

;; migrate it
(w/sql->datahike h2-config uri)
(def conn (d/connect uri))

;; query the migrated data
(d/q '[:find (count ?e) :where [?e _ _]] @conn)
```

Have a look at the [sql-test]("test/wanderung/sql_test.clj") namespace for a more elaborated SQL example.

## License

Copyright © 2020 lambdaforge UG (haftungsbeschränkt)

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
