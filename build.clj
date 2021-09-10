(ns build
  (:require [clojure.tools.build.api :as b]
            [org.corfield.build :as bb]
            [simple.build :as sb]))

(def lib 'org.clojars.wang/simple-build)

;; if you want a version of MAJOR.MINOR.COMMITS:
(def version (format "0.0.%s" (b/git-count-revs nil)))

(def ^:private default-basis (b/create-basis {:project "deps.edn"}))

(def scm {:url "https://github.com/tendant/simple-build"})

(defn jar
  [opts]
  (-> opts
      (assoc :lib lib :version version :scm scm)
      (bb/clean)
      (bb/jar)))

(defn install
  [opts]
  (-> opts
      (assoc :lib lib :version version :scm scm)
      (sb/install)))

(defn tag
  [opts]
  (-> opts
      (assoc :lib lib :version version :scm scm)
      (sb/tag)))

(defn release
  [opts]
  (-> opts
      (assoc :lib lib :version version :scm scm)
      (sb/release)))

(defn uberjar
  [opts]
  (-> opts
      (assoc :lib lib :version version :scm scm)
      (bb/clean)
      (sb/uberjar)))