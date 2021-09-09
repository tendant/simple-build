(ns build
  (:require [clojure.tools.build.api :as b]
            [org.corfield.build :as bb]
            [simple.build :as sb]))

(def lib 'org.clojars.wang/simple-build)

;; if you want a version of MAJOR.MINOR.COMMITS:
(def version (format "0.0.%s" (b/git-count-revs nil)))

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

(defn deploy
  [opts]
  (-> opts
      (assoc :lib lib :version version)
      (sb/no-local-change)
      (sb/git-tag-version)
      (bb/clean)
      (bb/jar)
      (sb/clojars)
      (sb/update-lein-version)
      (sb/update-deps-version)))