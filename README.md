# simple-build
[![Clojars Project](https://img.shields.io/clojars/v/org.clojars.wang/simple-build.svg)](https://clojars.org/org.clojars.wang/simple-build)

A Clojure library with some built utilities for [tools.build](https://github.com/clojure/tools.build), inspired by [build-clj](https://github.com/seancorfield/build-clj)

```clj
org.clojars.wang/simple-build {:mvn/version "0.0.18"}
```

## Usage

1. Add alias to deps.clj file
```clj
  :build {:deps {org.clojars.wang/simple-build {:mvn/version "0.0.18"}}
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
      (sb/install)))
      
(defn release
  [opts]
  (-> opts
      (assoc :lib lib :version version)
      (sb/release)))
```

3. Build jar file

```shell
clj -T:build jar
```

3. Install project to local maven repository

```shell
clj -T:build install
```

4. Release clojure project to clojars

```shell
clj -T:build release
```

## Development

1. Install to local ~/.m2 maven repository
```clj
    clj -T:build install
```
    
2. Deploy to clojars
```clj
    clj -T:build release
```
## License

Copyright © 2021 Lei

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
