# simple-build

A Clojure library with some built utilities for [tools.build](https://github.com/clojure/tools.build), inspired by [build-clj](https://github.com/seancorfield/build-clj)

```clj
org.clojars.wang/simple-build {:mvn/version "0.0.0"}
```

## Usage

1. Add alias to deps.clj file
```clj
  :build {:deps {org.clojars.wang/simple-build {:mvn/version "0.0.0"}}
          :ns-default build}
```

2. Create build.clj file

```clj
(ns build
  (:require [clojure.tools.build.api :as b]
            [org.corfield.build :as bb]
            [simple.build :as sb]))

(def lib '<group-id>/<artifact-id>)

;; if you want a version of MAJOR.MINOR.COMMITS:
(def version (format "1.0.%s" (b/git-count-revs nil)))

(defn jar
  [opts]
  (-> opts
      (assoc :lib lib :version version)
      (bb/clean)
      (bb/jar)))

(defn install
  [opts]
  (-> opts
      (assoc :lib lib :version version)
      (bb/clean)
      (sb/jar)
      (sb/install)))
      
(defn deploy
  [opts]
  (-> opts
      (assoc :lib lib :version version)
      (sb/no-local-change)
      (sb/git-tag-version)
      (bb/clean)
      (sb/jar)
      (sb/clojars)
      (sb/update-lein-version)
      (sb/update-deps-version)))
```

3. Install project to local maven repository

```shell
clj -T:build install
```

4. Release clojure project to clojars

```shell
clj -T:build deploy
```

## Development

1. Install to local ~/.m2 maven repository
```clj
    clj -T:build install
```
    
2. Deploy to clojars
```clj
    clj -T:build deploy
```
## License

Copyright © 2021 Lei

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
