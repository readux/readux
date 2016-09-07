(set-env!
  :resource-paths #{"src/cljs" "src/cljc"}
  :dependencies '[[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.227"]
                 [reagent "0.6.0-rc"]
                 [adzerk/boot-cljs "1.7.228-1" :scope "test"]
                 [pandeiro/boot-http "0.7.3" :scope "test"]
                 [adzerk/boot-reload "0.4.12" :scope "test"]
                 [adzerk/boot-cljs-repl "0.3.3" :scope "test"]
                 [com.cemerick/piggieback "0.2.1" :scope "test"]
                 [weasel "0.7.0" :scope "test"]
                 [org.clojure/tools.nrepl "0.2.12" :scope "test"]]
  :project 'readux
  :version "0.1.2-SNAPSHOT")

(task-options!
  pom {:project (get-env :project)
       :version (get-env :version)
       :license {"MIT" "https://mit-license.org/"}
       :url "https://readux.github.io"
       :description "ClojureScript library for managing state in reagent-based SPA's. Inspired by Redux"}
  jar {:main 'readux.core
       :file (format "%s-%s-standalone.jar" (get-env :project) (get-env :version))})

(require '[adzerk.boot-cljs :refer [cljs]]
         '[pandeiro.boot-http :refer [serve]]
         '[adzerk.boot-reload :refer [reload]]
         '[adzerk.boot-cljs-repl :refer [cljs-repl start-repl]])



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
  (comp (pom)
        (jar)
        (install)))

(deftask checkout-dep
    "Watch for changes - rebuild & redeploy locally as needed.
    
    NOTE: 
    use this in conjunction with 'checkout -d <dir>' on a dependent project"
    []
    (println "watching for changes & rebuilding local jar as needed.")
    (println "use -d <path-to-this-project> to rebuild dependent projects in response")
    (comp (watch)
          (local)))

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
  (comp (pom)
        (jar)
        (push :repo "deploy-clojars")))