# wanderung

Migration tool for Datahike from and to other databases.

## Usage

Make sure your source and target databases exist. You can run the migration on the commandline using [clojure CLI](https://clojure.org/reference/deps_and_cli):

```bash
clj -M:run-m -s datomic-cloud.edn -t datahike-pg.edn
```

Use `clj -M:run-m -h` for further instructions. See the `*-example.edn` files for `dataomic-cloud`, `datahike-file` or `nippy` example configurations.

Alternatively open your Clojure project, add `io.lambdaforge/wanderung` to your dependencies, and start a REPL:

```clojure
(require '[wanderung.core :as w])

(def datomic-cfg {:wanderung/type :datomic
                  :name "your-database"
                  :server-type :ion
                  :region      "eu-west-1"
                  :system      "your-system"
                  :endpoint    "http://entry.your-system.eu-west-1.datomic.net:8182/"
                  :proxy-port  8182})

(def datahike-cfg {:wanderung/type :datahike
                   :store {:backend :file
                           :path "/your-data-path"}
                   :name "from-datomic"
                   :schema-flexibility :write
                   :keep-history? true})
;; if the database doesn't exist, wanderung will create a Datahike database

(w/migrate datomic-cfg datahike-cfg)
```
You can use `clj -T:build jar` to create a jar file and `clj -T:build install` to install the library in your local maven repository. 
Take a look at `build.clj` for further commands.

## Tests

Before using Wanderung for performing a migration, you may wish to run tests that to check that Wanderung works correctly. In order to do so, you need to perform the following steps:

1. Install [Datomic dev-local](https://docs.datomic.com/cloud/dev-local.html).
2. Run the tests by calling `clj -T:build test`. With `clj -T:build clean` you can clean up your local build files.

## Contributors
- @perweij
- @vlaaad
- @jonasseglare

## Deprecation notice

Starting from version `0.2.0` wanderung does not support leiningen as build tool anymore. Please adjust accordingly in your project when using wanderung from the commandline. 

## License

Copyright © 2020-2022 lambdaforge UG (haftungsbeschränkt) & Contributors

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
