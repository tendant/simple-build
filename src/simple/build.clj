(ns simple.build
  "Some build utilities"
  (:require [clojure.tools.build.api :as b]
            [org.corfield.build :as bb]
            [deps-deploy.deps-deploy :as dd]))

(def ^:private default-target "target")

(def ^:private default-basis (b/create-basis {:project "deps.edn"}))

(defn- default-class-dir [target]
  (str target "/classes"))

(defn- default-jar-file [target lib version]
  (format "%s/%s-%s.jar" target (name lib) version))

(defn- default-uber-file [target lib version]
  (format "%s/%s-%s-standalone.jar" target (name lib) version))

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

(defn jar
  "Build the library JAR file.
  Requires: lib, version"
  [{:keys [target class-dir lib version basis scm src-dirs tag jar-file] :as opts}]
  (assert (and lib version) "lib and version are required for jar")
  (let [target    (or target default-target)
        class-dir (or class-dir (default-class-dir target))
        basis     (or basis default-basis)
        src-dirs  (or src-dirs ["src"])
        tag       (or tag (str "v" version))
        jar-file  (or jar-file (default-jar-file target lib version))]
    (println "\nWriting pom.xml...")
    (println "deps:" (:deps basis))
    (b/write-pom {:class-dir class-dir
                  :lib       lib
                  :version   version
                  :scm       (cond-> (or scm {})
                               tag (assoc :tag tag))
                  :basis     basis
                  :src-dirs  src-dirs})
    (println "Copying src...")
    (b/copy-dir {:src-dirs   src-dirs
                 :target-dir class-dir})
    (println (str "Building jar " jar-file "..."))
    (b/jar {:class-dir class-dir
            :jar-file  jar-file}))
  opts)

(defn javac
  [{:keys [src-dirs class-dir basis target] :as opts}]
  (let [target (or target default-target)
        src-dirs (or src-dirs ["java"])
        class-dir (or class-dir (default-class-dir target))
        basis (or basis default-basis)]
    (println "Compiling java...")
    (b/javac {:src-dirs src-dirs
              :class-dir class-dir
	      :basis basis
	      :javac-opts ["-source" "8" "-target" "8"]}))
  opts)

(defn uber
  "Build the library Uber JAR file. Requires: lib, version"
  [{:keys [target class-dir lib version basis scm src-dirs tag uber-file] :as opts}]
  (assert (and lib version) "lib and version are required for uber")
  (let [target    (or target default-target)
        class-dir (or class-dir (default-class-dir target))
        basis     (or basis default-basis)
        src-dirs  (or src-dirs ["src"])
        tag       (or tag (str "v" version))
        uber-file  (or uber-file (default-uber-file target lib version))]
    (println "Copying src...")
    (b/copy-dir {:src-dirs   src-dirs
                 :target-dir class-dir})
    (println (str "Building uberjar " uber-file "..."))
    (b/uber {:class-dir class-dir
             :uber-file uber-file
             :basis basis}))
  opts)

(defn compile-clj
  [{:keys [target class-dir basis src-dirs] :as opts}]
  (let [target (or target (default-target))
        class-dir (or class-dir (default-class-dir target))
        basis (or basis (default-basis))
        src-dirs (or src-dirs ["src"])])
  (println "Compiling clj...")
  (b/compile-clj {:basis basis
                  :src-dirs src-dirs
                  :class-dir class-dir})
  opts)

(defn uberjar
  [{:keys [lib version basis target class-dir uber-file] :as opts}]
  (assert (and lib version) "lib and version are required for install")
  (let [basis (or basis default-basis)
        target (or target default-target)
        class-dir (or class-dir (default-class-dir target))
        uber-file  (or uber-file (default-uber-file target lib version))]
    (-> opts
        (assoc :lib lib
               :version version
               :basis basis
               :target target
               :class-dir class-dir
               :uber-file uber-file)
        (compile-clj)
        (uber))
    opts))

(defn install
  "Install jar file to local maven repository ~/.m2, Depend on existing built jar file."
  [{:keys [lib version basis target class-dir jar-file] :as opts}]
  (assert (and lib version) "lib and version are required for install")
  (let [basis (or basis default-basis)
        target (or target default-target)
        class-dir (or class-dir (default-class-dir target))
        jar-file  (or jar-file (default-jar-file target lib version))]
    (printf "Install jar file(%s) to local maven repository(~/.m2) with version(%s \"%s\")%n" jar-file lib version)
    (-> opts
        (assoc :lib lib
               :version version
               :basis basis
               :target target
               :class-dir class-dir
               :jar-file jar-file)
        (bb/clean)
        (bb/jar)
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
  (let [tag (str "v" version)]
    (printf "git tag version(%s) with tag(%s)." version tag)
    (-> {:command-args (cond-> ["git" "tag" tag]
                         path (conj "--" path))
         :dir (.getPath (b/resolve-path dir))
         :out :capture}
        b/process
        :out))
  opts)

(defn tag
  [{:keys [dir path version] :as opts}]
  (-> opts
      (no-local-change)
      (git-tag-version)))

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

(defn release
  [opts]
  (-> opts
      (no-local-change)
      (bb/clean)
      (bb/jar)
      (clojars)
      (git-tag-version)
      (update-lein-version)
      (update-deps-version)))
