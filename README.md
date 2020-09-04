# wanderung

Migration tool for Datahike from and to other databases.

## Usage

Make sure your source and target databases exist. You can run the migration on the commandline:

```bash
lein run -s datomic-cloud.edn -t datahike-pg.edn -d datomic-cloud:datahike
```

Use `lein run -- -h` for further instructions. See the `*-example.edn` files for `dataomic-cloud` and `datahike-pg` example configurations.

Alternatively open your Clojure project, add `io.lambdaforge/wanderung` to your dependencies, and start a REPL:

```clojure
(require '[wanderung.core :as w])

(def datomic-cfg {:name "your-database"
                  :server-type :ion
                  :region      "eu-west-1"
                  :system      "your-system"
                  :endpoint    "http://entry.your-system.eu-west-1.datomic.net:8182/"
                  :proxy-port  8182})

(def datahike-cfg {:store {:backend :file
                           :path "/your-data-path"}
                   :name "from-datomic"
                   :schema-flexibility :write
                   :keep-history? true}) 
;; if the database doesn't exist, wanderung will create a Datahike database
                           
(w/migrate [:datomic-cloud :datahike] datomic-cfg datahike-cfg)
```

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
