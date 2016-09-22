(set-env!
  :resource-paths #{"public" "src/cljs" "src/cljc"}
  :dependencies '[[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.227"]
                 [adzerk/boot-cljs "1.7.228-1" :scope "test"]
                 [pandeiro/boot-http "0.7.3" :scope "test"]
                 [adzerk/boot-reload "0.4.12" :scope "test"]
                 [adzerk/boot-cljs-repl "0.3.3" :scope "test"]
                 [com.cemerick/piggieback "0.2.1" :scope "test"]
                 [weasel "0.7.0" :scope "test"]
                 [org.clojure/tools.nrepl "0.2.12" :scope "test"]
                 [reagent "0.6.0"]
                 [readux "0.1.5-SNAPSHOT"]]
  ;; use if this project should automatically rebuild when some other
  ;; project is changed...
  ;;:checkouts '[[foo-lib "0.1.0-SNAPSHOT"]]
  :project 'counters
  :version "0.1.0-SNAPSHOT")

(require '[adzerk.boot-cljs :refer [cljs]]
         '[pandeiro.boot-http :refer [serve]]
         '[adzerk.boot-reload :refer [reload]]
         '[adzerk.boot-cljs-repl :refer [cljs-repl start-repl]])

(task-options!
  pom {:project (get-env :project)
       :version (get-env :version)
       :license {"MIT" "https://mit-license.org/"}
       :url "<FIXME: URL HERE>"
       :description "middleware functionality for automatic resolution of promesa-promises"}
  jar {:main 'counters.core
       :file (format "%s-%s-standalone.jar" (get-env :project) (get-env :version))}
  cljs-repl {:nrepl-opts {:port 9000}})

(deftask dev
  "Launch immediate feedback development environment"
  []
  (comp (serve :dir "target")
        (watch)
        (reload)
        (cljs-repl)
        (cljs)
        (target :dir #{"target"})))

(deftask local
  "build & install jar into local (~/.m2) Maven repository"
  []
  (comp (pom) (jar) (install)))

(deftask checkout-dep
    "Watch for changes - rebuild & redeploy locally as needed."
    []
    (println "watching for changes & rebuilding local jar as needed.")
    (println "Add: ")
    (println (str "    :checkouts '[[" (get-env :project) " \"" (get-env :version) "\"]]"))
    (println "To any projects depending on this one.")
    (comp (watch) (local)))

;; These tasks are a simplification of what's found in
;; https://github.com/adzerk-oss/bootlaces/
(defn- get-clojars-creds []
  (mapv #(System/getenv %) ["CLOJARS_USER" "CLOJARS_PASS"]))

(deftask ^:private collect-clojars-creds
  []
  (let [[user pass] (get-clojars-creds)
        cred-map (atom {})]
    (if (and user pass)
      (swap! cred-map assoc :username user :password pass)
      (do  (println "CLOJARS_USER/CLOJARS_PASS UNSET")
           (print "Username: ")
           (#(swap! cred-map assoc :username %) (read-line))
           (print "Password: ")
           (#(swap! cred-map assoc :password %)
             (apply str (.readPassword (System/console))))))
    (merge-env! 
      :repositories 
      [["deploy-clojars"
        (merge @cred-map {:url "https://clojars.org/repo"})]])))

(deftask clojars
  "Build & push to library to clojars Maven repository"
  []
  (collect-clojars-creds)
  (comp (pom) (jar) (push :repo "deploy-clojars")))

(defn- generate-lein-project-file! [& {:keys [keep-project] :or {keep-project true}}]
  (require 'clojure.java.io)
  (let [pfile ((resolve 'clojure.java.io/file) "project.clj")
        ; Only works when pom options are set using task-options!
        {:keys [project version]} (:task-options (meta #'boot.task.built-in/pom))
        prop #(when-let [x (get-env %2)] [%1 x])
        head (list* 'defproject (or project 'boot-project) (or version "0.0.0-SNAPSHOT")
               (concat
                 (prop :url :url)
                 (prop :license :license)
                 (prop :description :description)
                 [:dependencies (conj (get-env :dependencies)
                                      ['boot/core "2.6.0" :scope "compile"])
                  :repositories (get-env :repositories)
                  :source-paths (vec (concat (get-env :source-paths)
                                             (get-env :resource-paths)))]))
        proj (pp-str head)]
      (if-not keep-project (.deleteOnExit pfile))
      (spit pfile proj)))

(deftask lein-generate
  "Generate a leiningen `project.clj` file.
   This task generates a leiningen `project.clj` file based on the boot
   environment configuration, including project name and version (generated
   if not present), dependencies, and source paths. Additional keys may be added
   to the generated `project.clj` file by specifying a `:lein` key in the boot
   environment whose value is a map of keys-value pairs to add to `project.clj`."
 []
 (generate-lein-project-file! :keep-project true))
