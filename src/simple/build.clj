(ns simple.build
  "Some build utilities"
  (:require [clojure.tools.build.api :as b]
            [deps-deploy.deps-deploy :as dd]))

(def ^:private default-target "target")
(def ^:private default-basis (b/create-basis {:project "deps.edn"}))
(defn- default-class-dir [target] (str target "/classes"))
(defn- default-jar-file [target lib version]
  (format "%s/%s-%s.jar" target (name lib) version))

(defn install
  "Install jar file to local maven repository ~/.m2, Depend on existing built jar file."
  [{:keys [lib version basis target class-dir jar-file] :as opts}]
  (assert (and lib version) "lib and version are required for install")
  (let [basis (or basis (default-basis))
        target (or target default-target)
        class-dir (or class-dir (default-class-dir target))
        jar-file  (or jar-file (default-jar-file target lib version))]
    (-> opts
        (assoc :lib lib
               :version version
               :basis basis
               :target target
               :class-dir class-dir
               :jar-file jar-file)
        (b/install))
    opts))

(defn clojars
  "Deploy the JAR to Clojars. Requires: lib, version. Depend on existing built jar file"
  [{:keys [lib version target class-dir jar-file] :as opts}]
  (assert (and lib version) "lib and version are required for deploy")
  (let [target    (or target default-target)
        class-dir (or class-dir (default-class-dir target))
        jar-file  (or jar-file (default-jar-file target lib version))]
    (dd/deploy (merge {:installer :remote :artifact jar-file
                       :pom-file (b/pom-path {:lib lib :class-dir class-dir})}
                      opts)))
  opts)

(defn git-tag-version
  "Shells out to git and tag current commit using version:
    git tag <version>
  Options:
    :dir - dir to invoke this command from, by default current directory
    :path - path to count commits for relative to dir"
  [{:keys [dir path version] :or {dir "."} :as opts}]
  (println "git tag version:" version)
  (-> {:command-args (cond-> ["git" "tag" version]
                       path (conj "--" path))
       :dir (.getPath (b/resolve-path dir))
       :out :capture}
      b/process
      :out)
  opts)

(defn- local-changes?
  [{:keys [dir path] :or {dir "."} :as opts}]
  (-> {:command-args (cond-> ["git" "status" "--porcelain"]
                       path (conj "--" path))
       :dir (.getPath (b/resolve-path dir))
       :out :capture}
      b/process
      :out))

(defn no-local-change
  [opts]
  (let [changes (local-changes? opts)]
    (if (empty? changes)
      opts
      (throw (ex-info (format "Local change detected, please commit your local change first!%n%s" changes)
                      {:changes changes})))))

(defn update-lein-version
  [{:keys [dir file lib version] :or {dir "." file "README.md"} :as opts}]
  (let [lib-name (clojure.string/replace (str lib) "/" "\\/")
        _ (println "lib-name:" lib-name)
        replace (format "s/\\(%s\\) \".*\"/\\1 \"%s\"/g" lib-name version)]
    (printf "Update version number to %s in file %s%n" version file)
    (-> {:command-args ["sed" "-i" "" replace file]
         :dir (.getPath (b/resolve-path dir))
         :out :capture}
        b/process
        :out)
    opts))

(defn update-deps-version
  [{:keys [dir file lib version] :or {dir "." file "README.md"} :as opts}]
  (let [lib-name (clojure.string/replace (str lib) "/" "\\/")
        _ (println "lib-name:" lib-name)
        replace (format "s/\\(%s\\) {:mvn\\/version \".*\"}/\\1 {:mvn\\/version \"%s\"}/g" lib-name version)]
    (println "replace:" replace)
    (printf "Update version number to %s in file %s%n" version file)
    (-> {:command-args ["sed" "-i" "" replace file]
         :dir (.getPath (b/resolve-path dir))
         :out :capture}
        b/process
        :out)
    opts))
