(ns simple.build
  "Some build utilities"
  (:require [clojure.tools.build.api :as b]
            [org.corfield.build :as bb]
            [deps-deploy.deps-deploy :as dd]))

(defn default-target
  [opts]
  (:target opts "target"))

(defn default-basis
  [opts]
  (b/create-basis {:project "deps.edn"}))

(defn default-class-dir
  [opts]
  (if (empty? (default-target opts))
    "classes"
    (str (default-target opts) "/classes")))

(defn default-name
  [opts]
  (assert (:lib opts) "lib is required!")
  (name (:lib opts)))

(defn default-version
  [opts]
  (:version opts (format "1.0.%s" (b/git-count-revs nil))))

(defn default-jar-file [opts]
  (:jar-file opts
             (format "%s/%s-%s.jar" (default-target opts) (default-name opts) (default-version opts))))

(defn default-uber-file [opts]
  (:uber-file opts
              (format "%s/%s-%s-standalone.jar" (default-target opts) (default-name opts) (default-version opts))))

(defn default-opts
  [opts]
  {:lib (:lib opts)
   :version (default-version opts)
   :target (default-target opts)
   :basis (default-basis opts)
   :src-dirs (:src-dirs opts ["src"])
   :tag (:tag opts (str "v" (default-version opts)))
   :class-dir (default-class-dir opts)
   :jar-file (default-jar-file opts)
   :uber-file (default-uber-file opts)})

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

(defn javac
  [{:keys [src-dirs class-dir basis target] :as opts}]
  (println "Compiling java...")
  (b/javac {:src-dirs src-dirs
            :class-dir class-dir
	    :basis basis
	    :javac-opts ["-source" "8" "-target" "8"]}))

(defn uberjar
  [{:keys [lib version basis target src-dirs class-dir uber-file] :as opts}]
  (assert (and lib version) "lib and version are required for install")
  (println "Copying src...")
  (b/copy-dir {:src-dirs   src-dirs
               :target-dir class-dir})
  (println "Compiling clj...")
  (b/compile-clj opts)
  (println (str "Building uberjar " uber-file "..."))
  (b/uber opts))

(defn install
  "Install jar file to local maven repository ~/.m2, Depend on existing built jar file."
  [{:keys [lib version basis target class-dir jar-file] :as opts}]
  (assert (and lib version) "lib and version are required for install")
  (printf "Install jar file(%s) to local maven repository(~/.m2) with version(%s \"%s\")%n" jar-file lib version)
  (bb/clean opts)
  (bb/jar opts)
  (b/install))

(defn clojars
  "Deploy the JAR to Clojars. Requires: lib, version. Depend on existing built jar file"
  [{:keys [lib version target class-dir jar-file] :as opts}]
  (assert (and lib version) "lib and version are required for deploy")
  (dd/deploy (merge {:installer :remote :artifact jar-file
                     :pom-file (b/pom-path {:lib lib :class-dir class-dir})}
                    opts)))

(defn git-tag-version
  "Shells out to git and tag current commit using version:
    git tag <version>
  Options:
    :dir - dir to invoke this command from, by default current directory
    :path - path to count commits for relative to dir"
  [{:keys [dir path version tag] :or {dir "."} :as opts}]
  (assert (and version tag) "version and tag is required")
  (printf "git tag version(%s) with tag(%s)." version tag)
  (-> {:command-args (cond-> ["git" "tag" tag]
                       path (conj "--" path))
       :dir (.getPath (b/resolve-path dir))
       :out :capture}
      b/process
      :out))

(defn tag
  [opts]
  (no-local-change opts)
  (git-tag-version opts))

(defn update-lein-version
  [{:keys [dir file lib version] :or {dir "." file "README.md"} :as opts}]
  (let [lib-name (clojure.string/replace (str lib) "/" "\\/")
        replace (format "s/\\(%s\\) \".*\"/\\1 \"%s\"/g" lib-name version)]
    (printf "Update version number to %s in file %s%n" version file)
    (-> {:command-args ["sed" "-i" "" replace file]
         :dir (.getPath (b/resolve-path dir))
         :out :capture}
        b/process
        :out)))

(defn update-deps-version
  [{:keys [dir file lib version] :or {dir "." file "README.md"} :as opts}]
  (let [lib-name (clojure.string/replace (str lib) "/" "\\/")
        replace (format "s/\\(%s\\) {:mvn\\/version \".*\"}/\\1 {:mvn\\/version \"%s\"}/g" lib-name version)]
    (printf "Update version number to %s in file %s%n" version file)
    (-> {:command-args ["sed" "-i" "" replace file]
         :dir (.getPath (b/resolve-path dir))
         :out :capture}
        b/process
        :out)))

(defn release
  [opts]
  (no-local-change opts)
  (bb/clean opts)
  (bb/jar opts)
  (clojars opts)
  (git-tag-version opts)
  (update-lein-version opts)
  (update-deps-version opts))
