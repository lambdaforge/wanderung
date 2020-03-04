# wanderung

Migration tool for Datahike from and to other databases.

## Usage

Make sure your source and target databases exist.

## From Datomic
```clojure
(require '[wanderung.core :as w])

(def datomic-cfg {:server-type :ion
                  :region "eu-west-1"
                  :system "my-source"
                  :endpoint "http://entry.my-source.eu-west-1.datomic.net:8182/"
                  :proxy-port 8182})

(def datomic-name "source-name")

(def datahike-cfg {:backend :file
                   :path "/tmp/my-target"})

(w/datomic-cloud->datahike datomic-cfg datomic-name datahike-cfg)
```

## License

Copyright © 2019 lambdaforge UG (haftungsbeschränkt)

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
